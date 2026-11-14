package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.SelfNotice;
import io.github.pinchan4273.reacademycraft.network.VectorAnalysisSync;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.5.2 ベクトル操作の解析度。
 *
 * <p>プレイヤーごとに、受けるダメージの分類ごとの解析度（0〜1）を持つ（{@link PlayerAbilityData#getAnalysis}、保存し、作り直しでも残る）。
 * 偏向・反射で共通。偏向の割合・反射の割合に解析度を掛け、反射の無効・打ち返し・向け直しは解析度100%のときだけ（{@link VecDeviation}・
 * {@link VecReflection}）。
 *
 * <p>分類（上から順に当てはめる。見分けるのはタグと登録の名前空間だけで、特定のMODの名前は書かない）:
 * <ol>
 * <li>未元物質: {@code #academy:ability/dark_matter}の種類と、天界発現の状態異常の損傷（{@link HeavenPhenomena.StatusSource}）。
 *     使い手ごと（キーに使い手のUUID。甲虫は所有者に数える）。</li>
 * <li>Re の能力（未元物質以外）: 能力のタグ{@code #academy:ability/<カテゴリ>}、またはアドオンのカテゴリの技能のダメージ。カテゴリごと。</li>
 * <li>魔法: 名前が{@code magic}・{@code is_magic}（または{@code …/magic}・{@code …/is_magic}）のタグか、バニラの{@code witch_resistant_to}。</li>
 * <li>バニラの物理: バニラの物理のタグ（{@code is_projectile}・{@code is_explosion}・{@code is_fire}・{@code is_fall}・{@code is_drowning}・
 *     {@code is_freezing}・{@code is_lightning}）か、{@code minecraft}の名前空間の種類で無敵を貫かない物（近接・サボテンなど）。いつも100%。</li>
 * <li>防具・防御を無視する物: バニラの{@code bypasses_*}のタグが付き、その種類自身の名前空間のタグが無い物（奈落・{@code /kill}は
 *     無敵を貫くので物理ではなくここ）。</li>
 * <li>そのMODのタグ: 種類と同じ名前空間（{@code minecraft}・{@code forge}・{@code c}・{@code academy}を除く）のタグ。名前順で最初のタグごと。</li>
 * <li>種類ごと: 上のどれにも当たらない種類は、その種類だけで1つの分類。</li>
 * </ol>
 *
 * <p>進み方: 偏向か反射が働いている間に受けた1回ごとに、Re の能力5%（20回）、未知（MODのタグ・種類ごと）4%（25回）、
 * 魔法・防具を無視する物 1/30（30回）、未元物質2%（50回）。同じ攻撃（同じダメージ源の同じtick）は1回だけ数える。
 * 未元物質の解析度は、いつも毎秒0.25%ずつ下がる（戦いの終わりの0への戻しは無い）。下がるのはサーバーにいる間。
 *
 * <p>未元物質に直接触れる攻撃（白翼刺突、甲虫の近接と突進）は、解析度100%でも偏向・反射の軽減を{@link #DIRECT_CONTACT_CAP}までにし、
 * 無効・打ち返しにしない（{@link #directContact}）。未元物質で変化した物の攻撃（未元光線・未元空気砲など）は、ほかの分類と同じ。
 *
 * <p>画面のための入口: 分類と解析度は{@link #classify}・{@link #analysisOf}、表示の名前は{@link #displayName}、
 * 本人のクライアントへの同期は{@link VectorAnalysisSync}（変わったとき・ログイン・リスポーン・ディメンションの移動。下がるだけの変化は1秒ごと）。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class VectorAnalysis {
    private VectorAnalysis() { }

    /** 分類の種類と、100%までに受ける回数。 */
    public enum Kind {
        DARK_MATTER(50), ABILITY(20), MAGIC(30), PHYSICAL(0), BYPASS(30), MOD_TAG(25), TYPE(25);
        private final int hits;
        Kind(int hits) { this.hits = hits; }
        /** 100%までに受ける回数（バニラの物理は0で、最初から100%）。 */
        public int hitsToFull() { return hits; }
        /** 1回で上がる分。 */
        public float gainPerHit() { return hits <= 0 ? 1 : 1f / hits; }
    }
    /** 分類。keyは保存と同期に使う文字列、labelは表示の名前（未元物質の使い手の名前など。無ければnull）。 */
    public record Category(Kind kind, String key, @Nullable String label) { }

    /** 分類のキー（固定の物）と、頭に付く文字列。 */
    public static final String PHYSICAL_KEY = "physical", MAGIC_KEY = "magic", BYPASS_KEY = "bypass";
    public static final String DARK_MATTER_KEY = "dark_matter", ABILITY_PREFIX = "ability/", TAG_PREFIX = "tag/", TYPE_PREFIX = "type/";
    /** 未元物質に直接触れる攻撃の、偏向・反射の軽減の上限。 */
    public static final float DIRECT_CONTACT_CAP = .5f;
    /** 未元物質の解析度が下がる速さ（1秒あたり）。 */
    public static final float DARK_MATTER_DECAY_PER_SECOND = .0025f;

    private static final List<TagKey<DamageType>> PHYSICAL_TAGS = List.of(DamageTypeTags.IS_PROJECTILE, DamageTypeTags.IS_EXPLOSION,
            DamageTypeTags.IS_FIRE, DamageTypeTags.IS_FALL, DamageTypeTags.IS_DROWNING, DamageTypeTags.IS_FREEZING, DamageTypeTags.IS_LIGHTNING);
    private static final List<TagKey<DamageType>> BYPASS_TAGS = List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_SHIELD,
            DamageTypeTags.BYPASSES_INVULNERABILITY, DamageTypeTags.BYPASSES_COOLDOWN, DamageTypeTags.BYPASSES_EFFECTS,
            DamageTypeTags.BYPASSES_RESISTANCE, DamageTypeTags.BYPASSES_ENCHANTMENTS);
    /** 「そのMODのタグ」に数えない名前空間。 */
    private static final Set<String> SHARED_NAMESPACES = Set.of("minecraft", "forge", "c", "academy");

    // ---- 分類 ----
    /** このダメージの分類。 */
    public static Category classify(DamageSource source) {
        // 1. 未元物質（天界発現の状態異常の損傷も、その使い手の未元物質）。
        if (source instanceof HeavenPhenomena.StatusSource || source.is(SkillDamageSource.abilityTag(DarkMatter.CATEGORY))) {
            Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
            UUID owner = null; String name = null;
            if (attacker instanceof DarkMatterBeetle beetle) {
                owner = beetle.ownerId();
                Entity real = source.getEntity() != null && source.getEntity() != beetle ? source.getEntity() : null;
                if (real != null) name = real.getName().getString();
            } else if (attacker != null) { owner = attacker.getUUID(); name = attacker.getName().getString(); }
            return new Category(Kind.DARK_MATTER, darkMatterKey(owner), name);
        }
        // 2. Re の能力（カテゴリごと）。
        var ability = SkillDamageSource.abilityOf(source);
        if (ability == null && source instanceof SkillDamageSource skill) {
            var definition = SkillCatalog.find(skill.skill());
            if (definition != null && definition.category() != null) ability = definition.category();
        }
        if (ability != null) return new Category(Kind.ABILITY, ABILITY_PREFIX + ability, ability.toString());
        var holder = source.typeHolder();
        var typeId = holder.unwrapKey().map(key -> key.location()).orElse(null);
        Set<ResourceLocation> tags = new HashSet<>();
        holder.tags().forEach(tag -> tags.add(tag.location()));
        return classifyType(typeId, tags);
    }
    /**
     * 分類の3〜7（能力のダメージでないもの）: 種類のIDと、その種類に付いているタグのIDだけで分ける（テストからも呼ぶ）。
     * 特定のMODの名前は見ない。
     */
    public static Category classifyType(@Nullable ResourceLocation typeId, Set<ResourceLocation> tags) {
        // 3. 魔法。
        if (tags.contains(DamageTypeTags.WITCH_RESISTANT_TO.location()) || tags.stream().anyMatch(VectorAnalysis::magicTag))
            return new Category(Kind.MAGIC, MAGIC_KEY, null);
        // 4. バニラの物理（いつも100%）。
        for (var tag : PHYSICAL_TAGS) if (tags.contains(tag.location())) return new Category(Kind.PHYSICAL, PHYSICAL_KEY, null);
        if (typeId != null && "minecraft".equals(typeId.getNamespace()) && !tags.contains(DamageTypeTags.BYPASSES_INVULNERABILITY.location()))
            return new Category(Kind.PHYSICAL, PHYSICAL_KEY, null);
        // 6.の候補（種類自身の名前空間のタグ。名前順で最初）。
        ResourceLocation own = null;
        if (typeId != null && !SHARED_NAMESPACES.contains(typeId.getNamespace())) {
            for (var tag : tags) {
                if (!tag.getNamespace().equals(typeId.getNamespace())) continue;
                if (own == null || tag.toString().compareTo(own.toString()) < 0) own = tag;
            }
        }
        // 5. 防具・防御を無視する物（その種類自身のタグが無いもの）。
        if (own == null) for (var tag : BYPASS_TAGS) if (tags.contains(tag.location())) return new Category(Kind.BYPASS, BYPASS_KEY, null);
        // 6. そのMODのタグ。
        if (own != null) return new Category(Kind.MOD_TAG, TAG_PREFIX + own, own.toString());
        // 7. 種類ごと。
        String id = typeId == null ? "unknown" : typeId.toString();
        return new Category(Kind.TYPE, TYPE_PREFIX + id, id);
    }
    /** 魔法を表すタグか（名前空間は問わない。名前の最後の部分が{@code magic}か{@code is_magic}）。 */
    private static boolean magicTag(ResourceLocation tag) {
        String path = tag.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.equals("magic") || last.equals("is_magic");
    }
    /** 未元物質の使い手ごとのキー（使い手が分からなければ{@link #DARK_MATTER_KEY}だけ）。 */
    public static String darkMatterKey(@Nullable UUID owner) { return owner == null ? DARK_MATTER_KEY : DARK_MATTER_KEY + "/" + owner; }

    /**
     * 未元物質に直接触れる攻撃か（白翼刺突、甲虫の近接と突進）。偏向・反射は{@link #DIRECT_CONTACT_CAP}までしか軽減せず、無効・打ち返しにしない。
     * 甲虫の攻撃は、攻撃の側が{@link SkillDamageSource#isDirectContact}でフラグを付ける（空気弾は付けない）。
     */
    public static boolean directContact(DamageSource source) {
        if (!(source instanceof SkillDamageSource skill)) return false;
        return DarkMatter.STAB.equals(skill.skill()) || skill.isDirectContact();
    }

    // ---- 解析度 ----
    private static @Nullable PlayerAbilityData data(ServerPlayer player) { return player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    /** 分類の解析度（バニラの物理はいつも1）。 */
    public static float analysisOf(@Nullable PlayerAbilityData data, Category category) {
        if (category.kind() == Kind.PHYSICAL) return 1;
        return data == null ? 0 : data.getAnalysis(category.key());
    }
    /** このプレイヤーの、このダメージの解析度（0〜1）。 */
    public static float analysisOf(ServerPlayer player, DamageSource source) { return analysisOf(data(player), classify(source)); }
    /** 解析しきったか（100%）。 */
    public static boolean full(float analysis) { return analysis >= 1; }

    /**
     * 向け直しの対象のエンティティの解析度（反射の向け直し）。Re の能力者（の飛ばした物）は、その能力の分類（未元物質なら使い手ごと）の解析度、
     * それ以外の物体は物理として100%。
     */
    public static float analysisOfEntity(ServerPlayer player, Entity entity) {
        Entity owner = entity instanceof TraceableEntity traced ? traced.getOwner() : null;
        if (entity instanceof DarkMatterBeetle beetle) return analysisOf(data(player), new Category(Kind.DARK_MATTER, darkMatterKey(beetle.ownerId()), null));
        if (!(owner instanceof ServerPlayer caster) || caster == player) return 1;
        var casterData = data(caster);
        var category = casterData == null || casterData.isReadOnly() || !casterData.isActive() ? null : casterData.getAbility();
        if (category == null) return 1;
        if (DarkMatter.CATEGORY.equals(category))
            return analysisOf(data(player), new Category(Kind.DARK_MATTER, darkMatterKey(caster.getUUID()), null));
        return analysisOf(data(player), new Category(Kind.ABILITY, ABILITY_PREFIX + category, null));
    }

    /** 同じ攻撃を2度数えないための、プレイヤーごとの最後に数えたダメージ源とそのtick。 */
    private static final Map<UUID, Object[]> LAST_LEARNED = new HashMap<>();
    /** 増えたので、このtickの終わりに送るプレイヤー。 */
    private static final Set<UUID> URGENT = new HashSet<>();

    /**
     * 偏向か反射が働いている間に受けた1回: その分類の解析度を1回分上げる。同じダメージ源の同じtickは1回だけ。
     * 物理は数えない（いつも100%）。熟練度の加算とは別。
     */
    public static void learn(ServerPlayer player, DamageSource source) {
        var data = data(player);
        if (data == null || data.isReadOnly()) return;
        long now = player.level().getGameTime();
        Object[] last = LAST_LEARNED.get(player.getUUID());
        if (last != null && last[0] == source && (long) last[1] == now) return;
        LAST_LEARNED.put(player.getUUID(), new Object[] {source, now});
        var category = classify(source);
        if (category.kind() == Kind.PHYSICAL) return;
        float before = data.getAnalysis(category.key());
        float after = before + category.kind().gainPerHit();
        // 浮動小数の端数で100%に届かないことがないよう、ほぼ100%は100%にする。
        if (after > 1 - 1e-4f) after = 1;
        int version = data.analysisVersion();
        data.setAnalysis(category.key(), after, category.label());
        if (data.analysisVersion() != version) URGENT.add(player.getUUID());
    }

    // ---- 表示の名前（画面が使う。クライアントでも呼べる） ----
    /**
     * 分類のキーと表示の名前から、画面に出す名前。翻訳があればその名前、なければID。
     * <ul>
     * <li>{@code physical}・{@code magic}・{@code bypass}: {@code academy.analysis.category.<キー>}。</li>
     * <li>{@code ability/<カテゴリ>}: 本体のカテゴリは{@code academy.analysis.category.ability.<名前>}、アドオンは
     *     {@code academy.analysis.category.ability}にカテゴリの名前を入れる。</li>
     * <li>{@code dark_matter/<UUID>}: {@code academy.analysis.category.dark_matter}に使い手の名前（無ければUUID）を入れる。</li>
     * <li>{@code tag/<ID>}・{@code type/<ID>}: {@code academy.analysis.category.unknown}に、タグなら{@code tag.damage_type.<名前空間>.<名前>}、
     *     種類なら{@code damage_type.<名前空間>.<名前>}の翻訳（無ければID）を入れる。</li>
     * </ul>
     */
    public static Component displayName(String key, @Nullable String label) {
        switch (key) {
            case PHYSICAL_KEY, MAGIC_KEY, BYPASS_KEY: return Component.translatable("academy.analysis.category." + key);
            default: break;
        }
        if (key.equals(DARK_MATTER_KEY) || key.startsWith(DARK_MATTER_KEY + "/")) {
            String who = label != null ? label : key.equals(DARK_MATTER_KEY) ? "?" : key.substring(DARK_MATTER_KEY.length() + 1);
            return Component.translatable("academy.analysis.category.dark_matter", who);
        }
        if (key.startsWith(ABILITY_PREFIX)) {
            var category = ResourceLocation.tryParse(key.substring(ABILITY_PREFIX.length()));
            if (category != null && SkillDamageSource.TYPED_CATEGORIES.contains(category))
                return Component.translatable("academy.analysis.category.ability." + category.getPath());
            return Component.translatable("academy.analysis.category.ability",
                    category == null ? Component.literal(key) : Component.translatable(io.github.pinchan4273.reacademycraft.ability.RegistryViews.categoryNameKey(category)));
        }
        if (key.startsWith(TAG_PREFIX) || key.startsWith(TYPE_PREFIX)) {
            boolean tag = key.startsWith(TAG_PREFIX);
            String id = key.substring(tag ? TAG_PREFIX.length() : TYPE_PREFIX.length());
            var location = ResourceLocation.tryParse(id);
            Component name = location == null ? Component.literal(id) : Component.translatableWithFallback(
                    (tag ? "tag.damage_type." : "damage_type.") + location.getNamespace() + "." + location.getPath().replace('/', '.'), tag ? "#" + id : id);
            return Component.translatable("academy.analysis.category.unknown", name);
        }
        return Component.literal(key);
    }

    // ---- 同期 ----
    /** プレイヤーごとの、最後に送った解析度の版。 */
    private static final Map<ServerPlayer, Integer> LAST_SENT = new WeakHashMap<>();
    /** 本人のクライアントへ、解析度の一覧を送る（変わっていなければ、forceのときだけ）。 */
    public static void sync(ServerPlayer player, boolean force) {
        var data = data(player);
        if (data == null) return;
        Integer sent = LAST_SENT.get(player);
        if (!force && sent != null && sent == data.analysisVersion()) return;
        LAST_SENT.put(player, data.analysisVersion());
        URGENT.remove(player.getUUID());
        SelfNotice.send(player, snapshot(data));
    }
    /** 送る一覧（覚えた順）。 */
    public static VectorAnalysisSync snapshot(PlayerAbilityData data) {
        List<VectorAnalysisSync.Entry> entries = new ArrayList<>();
        if (!data.isReadOnly()) data.analysisEntries().forEach((key, value) -> {
            String label = data.getAnalysisLabel(key);
            entries.add(new VectorAnalysisSync.Entry(key, value, label == null ? "" : label));
        });
        return new VectorAnalysisSync(entries);
    }

    /** 1tick分: 未元物質の解析度を下げ、増えたら今、下がっただけなら1秒ごとに送る。テスト用の入口も兼ねる。 */
    public static void tick(ServerPlayer player) {
        var data = data(player);
        if (data == null || data.isReadOnly()) return;
        data.decayAnalysis(DARK_MATTER_KEY, DARK_MATTER_DECAY_PER_SECOND / 20);
        if (URGENT.contains(player.getUUID()) || player.tickCount % 20 == 0) sync(player, false);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p, true); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p, true); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p, true); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { LAST_SENT.remove(p); URGENT.remove(p.getUUID()); LAST_LEARNED.remove(p.getUUID()); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { LAST_SENT.clear(); URGENT.clear(); LAST_LEARNED.clear(); }
}
