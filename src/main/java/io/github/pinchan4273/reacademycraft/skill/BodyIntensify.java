package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.BodyIntensifyState;
import io.github.pinchan4273.reacademycraft.network.IntensifyBurstEffect;
import io.github.pinchan4273.reacademycraft.network.IntensifyLoopEffect;
import io.github.pinchan4273.reacademycraft.network.SkillModeState;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Body Intensify（電撃使いレベル3）。熟練度で段階の付く切り替え型（原作とは異なる仕様として採用）。原作はキーを押し続けている間
 * 溜め、離すと数秒のランダムな強化効果を与えた。ここでは1回押すとオン、次に押すとオフになる。
 *
 * オン: キーは原作のlerp(200, 120, p)のオーバーロードで受け付けられ、準備が始まる。準備の各tickに原作の溜めの代価lerp(20, 15, p)の
 * CPを払い（熟練度帯により20、15、10、5、2tick。設定body_intensify.warmup_ticks）、オーバーロードはキーの時点のまま保つ。
 * 最後の支払いが済むと強化が始まる: 100%未満では帯が許す数だけ5つの中から重複無しで1回引く。100%では5つ全部で、Speed・
 * Regeneration・StrengthはV、Jump BoostとResistanceはII（どの帯でもIIを超えない）。帯・抽選・準備はキーを受け付けたときの
 * 熟練度のもの。成功で原作の0.01の経験値と音を1回だけ与える。強化が続く間、切り替え型技能の維持コストを現在の熟練度で払い、
 * 原作のHunger IIIの代わりにbody_intensify.extra_exhaustion（0.005）の空腹度消耗を毎tick加える。
 *
 * オフ: 再度のキー、CP切れ、能力やスロットが無くなる、プリセットやカテゴリの変更、妨害、オーバーロード、死亡、ログアウト、
 * ディメンションの移動、サーバーの停止、術者自身のクライアントのフォーカス喪失。準備は画面が開いても終わる。強化の終了でだけ
 * クールダウンfloor(lerp(900, 600, p))を1回設定する。途中で切れた準備は何も返さず、何も与えない。
 *
 * 強化はMinecraft自身の効果で、切れる前に付け直し、外すことはしない: ゲーム自身の統合により、他から来たより強い効果は上に、
 * 弱い効果はそれ自身の時間で隠れて残る。オフになると10tick以内に切れる。Regenerationは自身の回復周期の内で切れ、終了後の回復は
 * 最大でも2tick以内に来る予定だった1回だけ（durationを参照）。牛乳、/effect clear、その他でどれかが外れるとオフになる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class BodyIntensify {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy","body_intensify");
    /** 5つの強化。Jump BoostとResistanceはどの帯でもIIまで。 */
    public enum Buff {
        SPEED(MobEffects.MOVEMENT_SPEED, false), JUMP(MobEffects.JUMP, true), REGENERATION(MobEffects.REGENERATION, false),
        STRENGTH(MobEffects.DAMAGE_BOOST, false), RESISTANCE(MobEffects.DAMAGE_RESISTANCE, true);
        private final MobEffect effect; private final boolean capped;
        Buff(MobEffect effect, boolean capped) { this.effect = effect; this.capped = capped; }
        public MobEffect effect() { return effect; }
    }
    /** 熟練度帯0（25%未満）〜4（100%）: 強化はindex + 1個、増幅値はindex（上限のある2つは最大II）。 */
    public record Band(int index) {
        public int kinds() { return index + 1; }
        public int amplifier(Buff buff) { return buff.capped ? Math.min(index, 1) : index; }
        public int warmup() { return AcademyConfig.bodyWarmupTicks(index); }
    }
    /** 保存された熟練度の帯（そのままの値で判定する）: 99.99%は100%ではない。 */
    public static Band band(float proficiency) {
        return new Band(proficiency >= 1 ? 4 : proficiency >= .75f ? 3 : proficiency >= .5f ? 2 : proficiency >= .25f ? 1 : 0);
    }
    /** 帯の強化: 100%では5つ全部、それ未満ではその数だけを5つから等確率で、重複無しで。 */
    public static List<Buff> choose(Band band, RandomSource random) {
        var all = new ArrayList<>(List.of(Buff.values()));
        if (band.kinds() >= all.size()) return List.copyOf(all);
        var chosen = new ArrayList<Buff>();
        for (int i = 0; i < band.kinds(); i++) chosen.add(all.remove(random.nextInt(all.size())));
        return List.copyOf(chosen);
    }
    /** 各強化を付ける時間。したがってオフ後にどれだけ早く切れるか。Regenerationはrefreshを参照。 */
    public static final int REFRESH = 10;
    /**
     * Regenerationは残り時間が50 >> amplifierの倍数のときに回復する: 1tick長く付け、残り1tickで付け直すと、ちょうどその頻度で回復する。
     * 付け直さなくなると、残り時間がその倍数を通るのは最後の付け直しから2tick以内に最大1回なので、終了後の回復はそのとき予定の1回まで。
     */
    public static int duration(Buff buff, int amplifier) { return buff == Buff.REGENERATION ? (50 >> amplifier) + 1 : REFRESH; }

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static long nextEffectSession;
    private static final class Session {
        final Level level; final int preset, slot; final UUID owner; final int entityId; final long effectSession;
        final Set<UUID> listeners = new LinkedHashSet<>();
        final float warmupCost, overloadFloor; final Band band; final int warmup;
        boolean active; int ticks, age; long lastTick = Long.MIN_VALUE;
        List<Buff> buffs = List.of(); final int[] left = new int[Buff.values().length];
        PlayerAbilityData.Upkeep upkeep; double exhaustion;
        Session(ServerPlayer p, PlayerAbilityData d, int slot, float proficiency) {
            level = p.level(); preset = d.getCurrentPreset(); this.slot = slot;
            owner = p.getUUID(); entityId = p.getId(); effectSession = Math.incrementExact(nextEffectSession); nextEffectSession = effectSession;
            listeners.add(owner);
            for (var observer : level.players()) if (observer.distanceToSqr(p) <= 25 * 25) listeners.add(observer.getUUID());
            band = band(proficiency); warmup = band.warmup();
            warmupCost = ArcGen.lerp(20, 15, proficiency); overloadFloor = d.getOverload();
        }
    }
    private BodyIntensify() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer p, PlayerAbilityData d) {
        return d != null && !d.isReadOnly() && p.isAlive() && d.isActive() && !d.isOverloadLocked() && !d.isInterfering()
                && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID);
    }
    private static boolean valid(ServerPlayer p, PlayerAbilityData d, Session s) {
        return allowed(p, d) && p.level() == s.level && d.getCurrentPreset() == s.preset && ID.equals(d.getSlot(s.preset, s.slot))
                // 準備には術者の手が空いている必要がある。強化はどの画面の間も続く。
                && (s.active || p.containerMenu == p.inventoryMenu);
    }
    /** このセッション自体がまだ術者のものか: 支払いや成長の際に発したイベントが終わらせたかもしれない。 */
    private static boolean running(ServerPlayer p, Session s) { return ACTIVE.get(p.getUUID()) == s; }
    /**
     * このセッション自体が、自身が発したイベントの後も続くか: 別のセッションに置き換えられたものには手を出さない。術者がもう
     * 動かせないもの（未習得、スロット外、オーバーロード、オフ）はここで終える。
     */
    private static boolean carriesOn(ServerPlayer p, Session s) {
        if (!running(p, s)) return false;
        if (valid(p, data(p), s)) return true;
        stop(p);
        return false;
    }

    /** 何らかの形でオン（準備中、または強化中）。 */
    public static boolean isCharging(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    /** 強化がオンか。 */
    public static boolean isActive(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s != null && s.active; }
    /** このセッションが引いた強化。準備中やオフのときは空。 */
    public static List<Buff> buffs(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s == null ? List.of() : s.buffs; }
    /** このセッションを受け付けた帯。オフならnull。 */
    public static Band band(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s == null ? null : s.band; }
    /** このセッションがここまでに加えた空腹度消耗。 */
    public static double exhaustion(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s == null ? 0 : s.exhaustion; }
    /** このスロットからオンにしたセッションが動いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer p, int slot) { var s = ACTIVE.get(p.getUUID()); return s != null && s.slot == slot ? s.age : -1; }

    public static String start(ServerPlayer p, PlayerAbilityData d, int slot) {
        if (slot < 0 || slot >= 4 || d == null || d.isReadOnly() || !p.isAlive()
                || !ArcGen.CATEGORY.equals(d.getAbility()) || !d.hasLearned(ID)
                || !ID.equals(d.getSlot(d.getCurrentPreset(), slot))) return "academy.cast.unlearned";
        if (!d.isActive()) return "academy.cast.inactive";
        if (d.isOverloadLocked()) return "academy.cast.overload";
        if (d.isInterfering()) return "academy.cast.interfered";
        if (p.containerMenu != p.inventoryMenu) return "academy.cast.unlearned";
        if (isCharging(p)) return "";
        if (d.getCooldown(ID) > 0) return "academy.cast.cooldown";
        float proficiency = d.getProficiency(ID);
        if (!d.consume(ID, 0, ArcGen.lerp(200, 120, proficiency), p.getAbilities().instabuild)) return "academy.cast.cp";
        // そのオーバーロードで術者がオーバーロードしたかもしれず、そのイベントはすべてのモードを終える: その場合は何も始めない。
        // 支払いのリスナーが能力やスロットに何かをした後も同じ。
        if (!allowed(p, d) || !ID.equals(d.getSlot(d.getCurrentPreset(), slot)) || p.containerMenu != p.inventoryMenu) {
            AbilitySyncEvents.sync(p, true); return "academy.cast.overload";
        }
        var s = new Session(p, d, slot, proficiency);
        ACTIVE.put(p.getUUID(), s);
        // まず状態を送る: 術者のクライアントは、準備中の間だけ準備のループ音とHUDを再生する。
        BodyIntensifyState.send(p, BodyIntensifyState.WARMUP, slot);
        SkillModeState.send(p, ID, true);
        publish(s, true);
        return "";
    }
    /** 準備中の間の、所有者だけへの準備のループ音。 */
    public static IntensifyLoopEffect effect(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s == null || s.active ? null : effect(s, true); }
    private static IntensifyLoopEffect effect(Session s, boolean playing) {
        return new IntensifyLoopEffect(s.level.dimension().location(), s.owner, s.entityId, s.effectSession, playing);
    }
    private static void publish(Session s, boolean playing) { publish(s, playing, false); }
    private static void publish(Session s, boolean playing, boolean performed) {
        var owner = s.level.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null && owner.level() == s.level) AcademyNetwork.send(owner, new IntensifyLoopEffect(s.level.dimension().location(), s.owner, s.entityId, s.effectSession, playing, performed));
    }
    /** 術者のクライアントからの安全な停止。このスロットのセッションだけ。 */
    public static void cancel(ServerPlayer p, int slot) { var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) stop(p); }
    public static void stop(ServerPlayer p) {
        var s = ACTIVE.remove(p.getUUID());
        if (s == null) return;
        var d = data(p);
        if (d != null) d.endUpkeep(s.upkeep);
        // クールダウンがあるのは強化の終了だけ。途中で切れた準備はループ音とHUDを終え、実行しない。
        if (s.active) { if (d != null && !d.isReadOnly() && d.hasLearned(ID)) d.setCooldown(ID, cooldown(d.getProficiency(ID))); }
        else publish(s, false);
        SkillModeState.send(p, ID, false);
        BodyIntensifyState.send(p, BodyIntensifyState.OFF, s.slot);
        if (d != null) AbilitySyncEvents.sync(p, true);
    }
    public static int cooldown(float proficiency) { return (int) Math.floor(ArcGen.lerp(900, 600, proficiency)); }

    public static void tick(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID()); if (s == null) return; var d = data(p);
        if (!valid(p, d, s)) { stop(p); return; }
        long now = p.level().getGameTime(); if (s.lastTick == now) return; s.lastTick = now;
        s.age++;
        if (!s.active) {
            if (d.getOverload() < s.overloadFloor) d.setOverload(s.overloadFloor);
            if (!d.consume(ID, s.warmupCost, 0, p.getAbilities().instabuild)) { stop(p); return; }
            if (!carriesOn(p, s)) return;
            if (++s.ticks >= s.warmup) { activate(p, d, s); return; }
            if (s.ticks % 10 == 0) publish(s, true);
            AbilitySyncEvents.sync(p, false);
            return;
        }
        // 維持コストはこのtickの始めに精算済み。払えなかった要求は外れている。
        if (!d.upkeepHeld(s.upkeep)) { stop(p); return; }
        s.ticks++;
        float exhaustion = AcademyConfig.bodyExtraExhaustion();
        // Player.causeFoodExhaustionは、すべての消耗と同じく無敵（クリエイティブ、スペクテイター）のプレイヤーを飛ばす。
        if (exhaustion > 0 && !p.getAbilities().invulnerable) { p.causeFoodExhaustion(exhaustion); s.exhaustion += exhaustion; }
        refresh(p, s);
        if (!carriesOn(p, s)) return;
        AbilitySyncEvents.sync(p, false);
    }
    private static void activate(ServerPlayer p, PlayerAbilityData d, Session s) {
        s.buffs = choose(s.band, p.getRandom()); s.active = true; s.ticks = 0;
        s.upkeep = d.startUpkeep(ID, () -> p.getAbilities().instabuild);
        if (s.upkeep == null) { stop(p); return; }
        d.addProficiency(ID, .01f);
        if (!carriesOn(p, s)) return;
        for (var buff : s.buffs) { add(p, s, buff); if (!carriesOn(p, s)) return; }
        p.serverLevel().playSound(null, p, AcademySounds.EM_INTENSIFY_ACTIVATE.get(), SoundSource.AMBIENT, .5f, 1f);
        publish(s, false, true); // 受け付けたHUDの完了は所有者だけが受け取る。
        // 原作のコンテキストは、完了時ではなく開始時に近くの観測者を取り込む。音は別扱い。
        var burst = new IntensifyBurstEffect(s.level.dimension().location(), s.owner, s.entityId, s.effectSession);
        for (var id : s.listeners) {
            var listener = s.level.getServer().getPlayerList().getPlayer(id);
            if (listener != null && listener.level() == s.level) AcademyNetwork.send(listener, burst);
        }
        BodyIntensifyState.send(p, BodyIntensifyState.ACTIVE, s.slot);
        AbilitySyncEvents.sync(p, true);
    }
    private static void add(ServerPlayer p, Session s, Buff buff) {
        int amplifier = s.band.amplifier(buff), duration = duration(buff, amplifier);
        s.left[buff.ordinal()] = duration;
        p.addEffect(new MobEffectInstance(buff.effect, duration, amplifier, false, true, true));
    }
    /**
     * 各強化は、このセッションが与えた時間が残り1tickになったときに付け直す。表示中の効果は他人のより強いものかもしれないので、
     * そこから読まず自身で数える。
     */
    private static void refresh(ServerPlayer p, Session s) {
        for (var buff : s.buffs) {
            if (--s.left[buff.ordinal()] > 1) continue;
            add(p, s, buff);
            if (!carriesOn(p, s)) return;
        }
    }
    /** テスト用の入口: ゲーム時刻に関係なくサーバー1tick分を進める（テストは1回に多数進める）。 */
    public static void tickForTest(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); if (s != null) s.lastTick = Long.MIN_VALUE; tick(p); }

    /**
     * 何か（牛乳、/effect clear、トーテム、他のmod）が強化の1つを外し、誰もそれを止めなかった: 術者が望まなかったので終える。
     * 切れることと付け直しはこれに当たらない。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getEffect() == null) return;
        var s = ACTIVE.get(p.getUUID());
        if (s == null || !s.active) return;
        for (var buff : s.buffs) if (buff.effect == e.getEffect()) { stop(p); return; }
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /** 死亡またはエンドからの帰還: 新しいプレイヤーはセッションを持たない。クールダウンは既存の死亡と帰還の規則に従うので、ここでは設定しない。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.remove(p.getUUID()) != null) {
            SkillModeState.send(p, ID, false);
            BodyIntensifyState.send(p, BodyIntensifyState.OFF, 0);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); nextEffectSession = 0; }
}
