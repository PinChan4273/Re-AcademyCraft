package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.network.DirectEffectSync;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 技能由来の直接効果（v1.5.0、採用仕様・版41の§0「既存5技能の直接効果化」とR03・R04）。技能はバニラの状態効果を付けず、ここへ
 * 発生元の技能・発生元の実体・対象・強度・残tickを記録し、サーバーがゲームの処理へ反映する。
 * <ul>
 * <li>移動速度・鈍足・攻撃力: 種類ごとに固定のUUIDの一時的な属性の補正（ポーションの補正とは別のUUIDなので両方が効く）。値はバニラの
 * 同名の効果と同じ（速度+20%/段、鈍足-15%/段、攻撃力+3/段）。</li>
 * <li>跳躍: ジャンプの上向きの速さ+0.1/段と、落下距離-1ブロック/段（バニラのJump Boostと同じ量）。術者自身のクライアントの跳躍は
 * {@link DirectEffectSync}で伝えた値でクライアントが行う。</li>
 * <li>再生: 発生元ごとの周期（50 >> 段 tick）でHPを1回復（最大HPまで）。</li>
 * <li>耐性: 防具などの後の残りの損傷へ、1段20%の軽減を掛ける（バニラの耐性を無視する損傷には掛けない）。</li>
 * <li>盲目・吐き気: 対象のプレイヤーのクライアントへ開始・終了を伝え、クライアントが視界を変える（{@code client/ClientDirectEffects}）。</li>
 * </ul>
 * 牛乳・トーテム・他MODの効果の削除（{@code MobEffectEvent.Remove}）はバニラの状態効果だけに働き、ここの効果は続く（文書版27・28）。
 * 権限のある{@code /effect clear}は、対象の効果（効果名を指定したら、その種類だけ）をここからも外す（{@link #command}）。
 * <p>
 * 保存（R04）: 実体のForgeの永続データに置くので、実体と一緒に保存される。時間制の効果はその実体のtickでだけ減るので、ログアウト・
 * サーバー停止中は止まり、再接続で続きから減る。次元の移動では同じプレイヤーの実体のまま（エンドからの帰還は{@link #clone}で写す）。
 * 真の死亡で外す。トグル中だけ続く効果（{@link #BOUND}、身体強化）は保存しても読み込みで外し、再接続で自動では戻らない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class DirectEffects {
    /** 直接効果の種類。{@code vanilla}は、名前を指定した{@code /effect clear}で一緒に外れるバニラの効果。 */
    public enum Kind {
        SPEED("speed", MobEffects.MOVEMENT_SPEED, false), JUMP("jump_boost", MobEffects.JUMP, true),
        REGENERATION("regeneration", MobEffects.REGENERATION, false), STRENGTH("strength", MobEffects.DAMAGE_BOOST, false),
        RESISTANCE("resistance", MobEffects.DAMAGE_RESISTANCE, false), BLINDNESS("blindness", MobEffects.BLINDNESS, true),
        SLOWNESS("slowness", MobEffects.MOVEMENT_SLOWDOWN, false), NAUSEA("nausea", MobEffects.CONFUSION, true);
        private final String id; private final MobEffect vanilla; private final boolean client;
        Kind(String id, MobEffect vanilla, boolean client) { this.id = id; this.vanilla = vanilla; this.client = client; }
        public String id() { return id; }
        public MobEffect vanilla() { return vanilla; }
        /** 対象のプレイヤーのクライアント自身が反映する（跳躍・視界）。一覧はどの種類も送る（表示と試験のため）。 */
        public boolean client() { return client; }
        public static @Nullable Kind byId(String id) {
            for (var kind : values()) if (kind.id.equals(id)) return kind;
            return null;
        }
    }
    /** 時間でなく、発生元の技能が外すまで続く（身体強化のトグル中）。 */
    public static final int BOUND = -1;
    /** 1つの効果。sourceは発生元の実体（術者）。remainingは残tick、または{@link #BOUND}。clockは再生の周期の数え。 */
    public record Entry(Kind kind, ResourceLocation skill, @Nullable UUID source, int amplifier, int remaining, int clock) {
        public boolean bound() { return remaining == BOUND; }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("kind", kind.id); tag.putString("skill", skill.toString());
            if (source != null) tag.putUUID("source", source);
            tag.putInt("amplifier", amplifier); tag.putInt("remaining", remaining); tag.putInt("clock", clock);
            return tag;
        }
        static @Nullable Entry load(CompoundTag tag) {
            var kind = Kind.byId(tag.getString("kind"));
            var skill = ResourceLocation.tryParse(tag.getString("skill"));
            int remaining = tag.getInt("remaining"), amplifier = tag.getInt("amplifier");
            if (kind == null || skill == null || amplifier < 0 || amplifier > 255 || (remaining <= 0 && remaining != BOUND)) return null;
            return new Entry(kind, skill, tag.hasUUID("source") ? tag.getUUID("source") : null, amplifier, remaining, Math.max(0, tag.getInt("clock")));
        }
    }
    /** 永続データの中のキー（Forgeの{@code PlayerPersisted}の外: 死亡では写されない）。 */
    public static final String KEY = "academy_direct_effects";
    private static final UUID SPEED_ID = UUID.fromString("7c6f2c1e-5d0a-4b65-9a3e-150000000001"),
            SLOWNESS_ID = UUID.fromString("7c6f2c1e-5d0a-4b65-9a3e-150000000002"),
            STRENGTH_ID = UUID.fromString("7c6f2c1e-5d0a-4b65-9a3e-150000000003");

    private DirectEffects() { }

    // ---- 読み書き ----

    /** 対象の直接効果（保存の順）。 */
    public static List<Entry> entries(LivingEntity target) {
        var out = new ArrayList<Entry>();
        var data = target.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_LIST)) return out;
        for (var element : data.getList(KEY, Tag.TAG_COMPOUND)) {
            var entry = Entry.load((CompoundTag) element);
            if (entry != null) out.add(entry);
        }
        return out;
    }
    private static void write(LivingEntity target, List<Entry> entries) {
        var data = target.getPersistentData();
        if (entries.isEmpty()) { data.remove(KEY); return; }
        var list = new ListTag();
        for (var entry : entries) list.add(entry.save());
        data.put(KEY, list);
    }
    /** その種類の最も強い段（無ければ-1）。 */
    public static int amplifier(LivingEntity target, Kind kind) {
        int best = -1;
        for (var entry : entries(target)) if (entry.kind == kind) best = Math.max(best, entry.amplifier);
        return best;
    }
    public static boolean has(LivingEntity target, Kind kind) { return amplifier(target, kind) >= 0; }
    /** その技能が付けたその種類の残tick（無ければ0、トグル中なら{@link #BOUND}）。 */
    public static int remaining(LivingEntity target, Kind kind, ResourceLocation skill) {
        int best = 0;
        for (var entry : entries(target))
            if (entry.kind == kind && entry.skill.equals(skill)) best = entry.bound() ? BOUND : Math.max(best, entry.remaining);
        return best;
    }

    /**
     * 直接効果を付ける（サーバーだけ）。同じ種類・同じ技能・同じ発生元の効果があれば、バニラの効果の重ね方と同じく、強い段を残し、
     * 同じ段なら長い方を残す。ticksは1以上か{@link #BOUND}。
     */
    public static void add(LivingEntity target, Kind kind, int amplifier, int ticks, ResourceLocation skill, @Nullable Entity source) {
        if (target.level().isClientSide || amplifier < 0 || (ticks <= 0 && ticks != BOUND)) return;
        // §15: 未元物質人間は有害な直接効果を受けない。
        if (kind.vanilla.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL && DarkBody.immuneToDebuffs(target)) return;
        var sourceId = source == null ? null : source.getUUID();
        var entries = entries(target);
        boolean merged = false;
        for (int i = 0; i < entries.size(); i++) {
            var old = entries.get(i);
            if (old.kind != kind || !old.skill.equals(skill) || !java.util.Objects.equals(old.source, sourceId)) continue;
            merged = true;
            if (amplifier > old.amplifier || (amplifier == old.amplifier && (ticks == BOUND || (!old.bound() && ticks > old.remaining))))
                entries.set(i, new Entry(kind, skill, sourceId, amplifier, ticks, old.clock));
        }
        if (!merged) entries.add(new Entry(kind, skill, sourceId, amplifier, ticks, 0));
        write(target, entries);
        refresh(target, true);
    }
    /** その技能が付けた効果をすべて外す（トグルの終了など）。外したものがあればtrue。 */
    public static boolean remove(LivingEntity target, ResourceLocation skill) {
        if (target.level().isClientSide) return false;
        var entries = entries(target);
        var kept = new ArrayList<Entry>();
        for (var entry : entries) if (!entry.skill.equals(skill)) kept.add(entry);
        if (kept.size() == entries.size()) return false;
        write(target, kept);
        refresh(target, true);
        return true;
    }
    /** 有害な種類だけを外す（§15: 未元物質人間への変化）。有益な効果は残す。 */
    public static void clearHarmful(LivingEntity target) {
        if (target.level().isClientSide) return;
        var entries = entries(target);
        var kept = new ArrayList<Entry>();
        for (var entry : entries) if (entry.kind.vanilla.getCategory() != net.minecraft.world.effect.MobEffectCategory.HARMFUL) kept.add(entry);
        if (kept.size() == entries.size()) return;
        write(target, kept);
        refresh(target, true);
    }
    /** すべて外す（真の死亡）。 */
    public static void clear(LivingEntity target) {
        if (target.level().isClientSide || !target.getPersistentData().contains(KEY)) return;
        target.getPersistentData().remove(KEY);
        refresh(target, true);
    }

    /**
     * 権限のある{@code /effect clear}（文書版28）: 指定の効果（nullなら全部）に当たる直接効果を外す。身体強化の強化が外れたら、
     * 既存の停止の規則でトグルをOFFにし、継続更新で付け直さない。外したものがあればtrue。
     */
    public static boolean clearByCommand(LivingEntity target, @Nullable MobEffect effect) {
        if (target.level().isClientSide) return false;
        var entries = entries(target);
        var kept = new ArrayList<Entry>();
        boolean body = false;
        for (var entry : entries) {
            if (effect == null || entry.kind.vanilla == effect) {
                body |= entry.skill.equals(BodyIntensify.ID);
            } else kept.add(entry);
        }
        if (kept.size() == entries.size()) return false;
        write(target, kept);
        refresh(target, true);
        if (body && target instanceof ServerPlayer player) BodyIntensify.stopByCommand(player);
        return true;
    }

    // ---- 反映 ----

    /** 属性の補正を今の効果に合わせ、必要なら対象のクライアントへ伝える。 */
    private static void refresh(LivingEntity target, boolean client) {
        // 同じ段の薬と属性値が完全に一致するよう、バニラの定数と同じfloat由来の値を使う。
        modifier(target, Attributes.MOVEMENT_SPEED, SPEED_ID, "academy direct speed", amplifier(target, Kind.SPEED), (double) .2f, AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(target, Attributes.MOVEMENT_SPEED, SLOWNESS_ID, "academy direct slowness", amplifier(target, Kind.SLOWNESS), (double) -.15f, AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(target, Attributes.ATTACK_DAMAGE, STRENGTH_ID, "academy direct strength", amplifier(target, Kind.STRENGTH), 3, AttributeModifier.Operation.ADDITION);
        if (client && target instanceof ServerPlayer player) sync(player);
    }
    private static void modifier(LivingEntity target, Attribute attribute, UUID id, String name, int amplifier, double perLevel, AttributeModifier.Operation operation) {
        var instance = target.getAttribute(attribute);
        if (instance == null) return;
        var current = instance.getModifier(id);
        double amount = perLevel * (amplifier + 1);
        if (amplifier < 0) { if (current != null) instance.removeModifier(id); return; }
        if (current != null && current.getAmount() == amount && current.getOperation() == operation) return;
        if (current != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }
    /** 対象のプレイヤーのクライアントへ、直接効果の今の一覧（全種類）を送る。 */
    public static void sync(ServerPlayer player) {
        var list = new ArrayList<DirectEffectSync.Effect>();
        for (var entry : entries(player))
            list.add(new DirectEffectSync.Effect(entry.kind.ordinal(), entry.amplifier, entry.remaining));
        DirectEffectSync.send(player, list);
    }

    /** 1tick進める（時間制の効果を減らし、再生を行う）。試験からも呼ぶ。 */
    public static void tick(LivingEntity target) {
        if (target.level().isClientSide || !target.getPersistentData().contains(KEY)) return;
        var entries = entries(target);
        var next = new ArrayList<Entry>();
        boolean expired = false;
        float heal = 0;
        for (var entry : entries) {
            int clock = entry.clock + 1;
            if (entry.kind == Kind.REGENERATION && clock % Math.max(1, 50 >> entry.amplifier) == 0) heal += 1;
            if (!entry.bound() && entry.remaining - 1 <= 0) { expired = true; continue; }
            next.add(new Entry(entry.kind, entry.skill, entry.source, entry.amplifier, entry.bound() ? BOUND : entry.remaining - 1, clock));
        }
        write(target, next);
        if (heal > 0 && target.isAlive() && target.getHealth() < target.getMaxHealth()) target.heal(heal);
        // 読み込んだ実体は一時的な補正を持たないので、毎tick確かめて付け直す（同じ値なら何もしない）。
        refresh(target, expired);
    }

    @SubscribeEvent public static void livingTick(LivingEvent.LivingTickEvent event) { tick(event.getEntity()); }

    /** 跳躍（サーバー側。術者自身のクライアントの跳躍は{@code client/ClientDirectEffects}）。 */
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) return;
        int amplifier = amplifier(entity, Kind.JUMP);
        if (amplifier >= 0) entity.setDeltaMovement(jumped(entity.getDeltaMovement(), amplifier));
    }
    /** 跳躍後の速さ。バニラの跳躍はfloatの和をdoubleにするので、同じ段の薬と同じ値になるようfloatで足す（薬とも足し合わさる。R03）。 */
    public static net.minecraft.world.phys.Vec3 jumped(net.minecraft.world.phys.Vec3 motion, int amplifier) {
        return new net.minecraft.world.phys.Vec3(motion.x, (double) ((float) motion.y + .1f * (amplifier + 1)), motion.z);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) return;
        int amplifier = amplifier(entity, Kind.JUMP);
        if (amplifier >= 0) event.setDistance(Math.max(0, event.getDistance() - (amplifier + 1)));
    }
    /** 耐性: 他のリスナーの後に、残りの損傷へ掛ける。バニラの耐性・効果を無視する損傷、防御無視の刺突には掛けない。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        var entity = event.getEntity();
        if (event.isCanceled() || entity.level().isClientSide) return;
        int amplifier = amplifier(entity, Kind.RESISTANCE);
        var source = event.getSource();
        if (amplifier < 0 || source.is(DamageTypeTags.BYPASSES_RESISTANCE) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || SkillDamageSource.ignoresDefenses(source)) return;
        event.setAmount(Math.max(0, event.getAmount() * (1 - .2f * (amplifier + 1))));
    }
    /** 真の死亡で外す（死亡が取り消された場合は残す）。 */
    /** {@link DeathOrder}が、救済・人間化されなかった死亡でだけ呼ぶ。 */
    public static void death(LivingDeathEvent event) { if (!event.isCanceled()) clear(event.getEntity()); }
    /** エンドからの帰還は同じプレイヤーの続き: 効果を写す。死亡からの復活では写さない（Forgeも写さない場所に置いている）。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var player = event.getEntity();
        if (event.isWasDeath()) { player.getPersistentData().remove(KEY); return; }
        var old = event.getOriginal().getPersistentData();
        if (old.contains(KEY, Tag.TAG_LIST)) player.getPersistentData().put(KEY, old.getList(KEY, Tag.TAG_COMPOUND).copy());
        // 新しい実体のトグルは動いていないので、トグル中だけの効果は残さない。
        dropBound(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { refresh(player, true); }
    }
    /** 再接続: トグル中だけの効果は残さない（R04: 再接続で自動では戻らない）。時間制の効果は続きから。 */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { dropBound(player); refresh(player, true); }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player, true);
    }
    private static void dropBound(LivingEntity target) {
        var entries = entries(target);
        var kept = entries.stream().filter(e -> !e.bound()).toList();
        if (kept.size() != entries.size()) write(target, new ArrayList<>(kept));
    }

    /**
     * 権限のある{@code /effect clear [対象] [効果]}を、実行の直前に見つけて直接効果も外す（文書版28）。権限の無い送り手は、構文の
     * 解析で{@code clear}の節まで進めない（節の要求が通らない）ので、ここへ来ない。解析が完全に成功した命令だけを扱い、ほかのリスナーが
     * 取り消した命令は扱わない。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void command(CommandEvent event) {
        if (event.isCanceled()) return;
        var parse = event.getParseResults();
        if (!parse.getExceptions().isEmpty() || parse.getReader().canRead()) return;
        for (var builder = parse.getContext(); builder != null; builder = builder.getChild()) {
            var nodes = builder.getNodes();
            int at = -1;
            for (int i = 0; i + 1 < nodes.size(); i++)
                if ("effect".equals(nodes.get(i).getNode().getName()) && "clear".equals(nodes.get(i + 1).getNode().getName())) { at = i; break; }
            if (at < 0) continue;
            try {
                var context = builder.build(parse.getReader().getString());
                java.util.Collection<? extends Entity> targets = builder.getArguments().containsKey("targets")
                        ? net.minecraft.commands.arguments.EntityArgument.getEntities(context, "targets")
                        : sourceEntity(builder.getSource());
                MobEffect effect = builder.getArguments().containsKey("effect")
                        ? net.minecraft.commands.arguments.ResourceArgument.getMobEffect(context, "effect").value() : null;
                for (var entity : targets) if (entity instanceof LivingEntity living) clearByCommand(living, effect);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException ignored) {
                // 対象が見つからない等は、バニラの命令自身がその誤りを伝える。直接効果は何も変えない。
            }
            return;
        }
    }
    private static java.util.Collection<? extends Entity> sourceEntity(CommandSourceStack source) {
        var entity = source.getEntity();
        return entity == null ? List.of() : List.of(entity);
    }
}
