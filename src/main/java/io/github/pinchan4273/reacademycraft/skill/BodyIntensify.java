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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingHurtEvent;

/**
 * Body Intensify（電撃使いレベル3）。熟練度で段階の付く切り替え型（原作とは異なる仕様として採用）。原作はキーを押し続けている間
 * 溜め、離すと数秒のランダムな強化効果を与えた。ここでは1回押すとオン、次に押すとオフになる。
 *
 * オン: キーはlerp(60, 30, p)のオーバーロード（原作はlerp(200, 120, p)）で受け付けられ、準備が始まる。準備の各tickに原作の溜めの代価lerp(20, 15, p)の
 * CPを払う（熟練度帯により20、15、10、5、2tick。設定body_intensify.warmup_ticks）。1.5.2から、準備の間もオーバーロードは回復する
 * （キーの時点の値に保たない）。
 * 最後の支払いが済むと強化が始まる: 100%未満では帯が許す数だけ5つの中から重複無しで1回引く。100%では5つ全部で、Speed・
 * Regeneration・StrengthはV、Jump BoostとResistanceはII（どの帯でもIIを超えない）。帯・抽選・準備はキーを受け付けたときの
 * 熟練度のもの。成功で原作の0.01の経験値と音を1回だけ与える。強化が続く間、部類の外の切り替え型の共通の維持コスト（1.5.2:
 * 回復の lerp(100%, 25%) ＋ toggle_upkeep.body_intensify × (1 − 熟練度)。{@link PlayerAbilityData.Upkeep#recoveryShare}）を現在の熟練度で払い、
 * 原作のHunger IIIの代わりにbody_intensify.extra_exhaustion（0.005）の空腹度消耗を毎tick加える。
 *
 * 1.5.2: 強化を「使った」ときだけ費用がかかる。使ったのは、その強化があるときの、移動（速さ）・ジャンプ（跳躍）・
 * 近接攻撃（筋力）・体力の回復（再生）・被ダメージの軽減（耐性）。1秒ごとに、その秒に移動以外を使っていれば{@link #combatOverload}、
 * 移動だけなら{@link #moveOverload}を「稼働」の過負荷として払う（熟練度100%・最大過負荷1,000で、移動だけは5分、それ以外は90秒でロック）。
 * CPの維持費は、最後に使ってから20tickの間だけ払う（使っていない間は払わない）。
 *
 * オフ: 再度のキー、CP切れ、能力やスロットが無くなる、プリセットやカテゴリの変更、妨害、オーバーロード、死亡、ログアウト、
 * ディメンションの移動、サーバーの停止、術者自身のクライアントのフォーカス喪失。準備は画面が開いても終わる。強化の終了でだけ
 * クールダウンfloor(lerp(900, 600, p))を1回設定する。途中で切れた準備は何も返さず、何も与えない。
 *
 * v1.5.0: 強化はバニラの状態効果ではなく、技能由来の直接効果（{@link DirectEffects}、
 * トグル中だけ続く{@link DirectEffects#BOUND}）。発動で付け、オフ（停止の規則のどれか）で外すので、オフの後に補正は残らない。
 * ポーションの同種の効果とは別に両方が効く。牛乳・他MODの効果の削除では外れず、トグルも続く。権限のある{@code /effect clear}で
 * 外れたらトグルをOFFにし、付け直さない（{@link #stopByCommand}）。熟練度帯・起動時の抽選・費用・クールダウンは変えていない。
 *
 * 1.5.2: 能力設定でパッシブにすると、能力使用モードの間に{@link PassiveSkills}が自動で始める（スロットのインデックスは{@link PassiveSkills#SLOT}）。
 * 準備・費用・抽選・クールダウンはキーで始めるときと同じ。パッシブのセッションはプリセットとスロットを見ず、パッシブのままかを見る。
 * 術者のクライアントのフォーカス喪失の停止は、スロットのキーで始めたものだけ（パッシブは止めてもすぐ起動し直すため）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class BodyIntensify {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("academy","body_intensify");
    /** 5つの強化。Jump BoostとResistanceはどの帯でもIIまで。 */
    public enum Buff {
        SPEED(DirectEffects.Kind.SPEED, false), JUMP(DirectEffects.Kind.JUMP, true), REGENERATION(DirectEffects.Kind.REGENERATION, false),
        STRENGTH(DirectEffects.Kind.STRENGTH, false), RESISTANCE(DirectEffects.Kind.RESISTANCE, true);
        private final DirectEffects.Kind kind; private final boolean capped;
        Buff(DirectEffects.Kind kind, boolean capped) { this.kind = kind; this.capped = capped; }
        /** この強化の直接効果の種類。 */
        public DirectEffects.Kind kind() { return kind; }
        /**
         * 同じ種類のバニラの効果（名前を指定した{@code /effect clear}の対象の照合と、テストの比較のため。強化自身は付けない）。
         * 1.20.5から効果はHolderで扱う（Forge版はMobEffect）。
         */
        public net.minecraft.core.Holder<MobEffect> effect() { return kind.vanilla(); }
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

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static long nextEffectSession;
    private static final class Session {
        final Level level; final int preset; int slot; final UUID owner; final int entityId; final long effectSession;
        final Set<UUID> listeners = new LinkedHashSet<>();
        final float warmupCost; final Band band; final int warmup;
        boolean active; int ticks, age; long lastTick = Long.MIN_VALUE;
        /** 1.5.2: 最後に強化を使ったage、この1秒に移動・それ以外を使ったか、前のtickの位置と体力。 */
        int lastUse = Integer.MIN_VALUE / 2; boolean movedThisSecond, foughtThisSecond;
        @org.jetbrains.annotations.Nullable net.minecraft.world.phys.Vec3 lastPosition; float lastHealth = -1;
        List<Buff> buffs = List.of();
        PlayerAbilityData.Upkeep upkeep; double exhaustion;
        Session(ServerPlayer p, PlayerAbilityData d, int slot, float proficiency) {
            level = p.level(); preset = d.getCurrentPreset(); this.slot = slot;
            owner = p.getUUID(); entityId = p.getId(); effectSession = Math.incrementExact(nextEffectSession); nextEffectSession = effectSession;
            listeners.add(owner);
            for (var observer : level.players()) if (observer.distanceToSqr(p) <= 25 * 25) listeners.add(observer.getUUID());
            band = band(proficiency); warmup = band.warmup();
            warmupCost = ArcGen.lerp(20, 15, proficiency);
        }
    }
    private BodyIntensify() { }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    private static boolean allowed(ServerPlayer p, PlayerAbilityData d) {
        return d != null && !d.isReadOnly() && p.isAlive() && d.isActive() && !d.lockBlocks(ID) && !d.isInterfering()
                && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID);
    }
    private static boolean valid(ServerPlayer p, PlayerAbilityData d, Session s) {
        // 予備動作（WARMUP）も強化（ACTIVE）も、コンテナの画面を開いただけでは止めない。予備動作の
        // 各tickのCPの消費と完了は通常どおり。画面を開いている間の新しい開始は、start()が拒否する。
        // 1.5.2: パッシブで動いているものは、プリセットとスロットを見ず、パッシブのままかを見る。
        return allowed(p, d) && p.level() == s.level && PassiveSkills.sessionMatches(d, ID, s.preset, s.slot);
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
    /** 1.5.2: パッシブ（自動で起動）で動いているか（準備中を含む。スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }
    /**
     * 1.5.2: パッシブにした時に、スロットのキーで始めたセッションが動いていたら、止めずにパッシブのセッションにする
     * （準備・強化・費用・抽選はそのまま。起動の費用を二度払わない）。術者のクライアントへ、今の段階を新しいスロットのインデックスで伝え直す。
     */
    public static void adoptPassive(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID());
        if (s == null || s.slot == PassiveSkills.SLOT) return;
        s.slot = PassiveSkills.SLOT;
        BodyIntensifyState.send(p, s.active ? BodyIntensifyState.ACTIVE : BodyIntensifyState.WARMUP, s.slot);
    }

    public static String start(ServerPlayer p, PlayerAbilityData d, int slot) {
        // 1.5.2: slotが{@link PassiveSkills#SLOT}なら、パッシブの自動の起動（パッシブに切り替えていること）。
        if (d == null || d.isReadOnly() || !p.isAlive()
                || !ArcGen.CATEGORY.equals(d.getAbility()) || !d.hasLearned(ID)
                || !PassiveSkills.slotMatches(d, ID, slot)) return "academy.cast.unlearned";
        if (!d.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (d.lockBlocks(ID)) return "academy.cast.overload";
        if (d.isInterfering()) return "academy.cast.interfered";
        if (p.containerMenu != p.inventoryMenu) return "academy.cast.unlearned";
        if (isCharging(p)) return "";
        if (d.getCooldown(ID) > 0) return "academy.cast.cooldown";
        float proficiency = d.getProficiency(ID);
        if (!d.consume(ID, 0, startOverload(proficiency), p.getAbilities().instabuild)) return "academy.cast.cp";
        // そのオーバーロードで術者がオーバーロードしたかもしれず、そのイベントはすべてのモードを終える: その場合は何も始めない。
        // 支払いのリスナーが能力やスロットに何かをした後も同じ。
        if (!allowed(p, d) || !PassiveSkills.slotMatches(d, ID, slot) || p.containerMenu != p.inventoryMenu) {
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
        return new IntensifyLoopEffect(s.level.dimension().identifier(), s.owner, s.entityId, s.effectSession, playing);
    }
    private static void publish(Session s, boolean playing) { publish(s, playing, false); }
    private static void publish(Session s, boolean playing, boolean performed) {
        var owner = s.level.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null && owner.level() == s.level) AcademyNetwork.send(owner, new IntensifyLoopEffect(s.level.dimension().identifier(), s.owner, s.entityId, s.effectSession, playing, performed));
    }
    /** 術者のクライアントからの安全な停止。このスロットのセッションだけ。 */
    public static void cancel(ServerPlayer p, int slot) { var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) stop(p); }
    public static void stop(ServerPlayer p) {
        var s = ACTIVE.remove(p.getUUID());
        if (s == null) return;
        var d = data(p);
        // 強化の直接効果は、オフになったこのtickで外す（v1.5.0。付け直しの時間を待たない）。
        DirectEffects.remove(p, ID);
        if (d != null) d.endUpkeep(s.upkeep);
        // クールダウンがあるのは強化の終了だけ。途中で切れた準備はループ音とHUDを終え、実行しない。
        if (s.active) { if (d != null && !d.isReadOnly() && d.hasLearned(ID)) d.setCooldown(ID, cooldown(d.getProficiency(ID))); }
        else publish(s, false);
        SkillModeState.send(p, ID, false);
        BodyIntensifyState.send(p, BodyIntensifyState.OFF, s.slot);
        if (d != null) AbilitySyncEvents.sync(p, true);
    }
    /**
     * 権限のある{@code /effect clear}が強化の直接効果を外した（v1.5.0）: 既存の停止の規則でトグルをOFFにし、継続更新で付け直さない。
     */
    public static void stopByCommand(ServerPlayer p) { if (isActive(p)) stop(p); }
    public static int cooldown(float proficiency) { return (int) Math.floor(ArcGen.lerp(900, 600, proficiency)); }
    /** 起動のオーバーロード（原作lerp(200, 120)から）。 */
    public static float startOverload(float proficiency) { return ArcGen.lerp(60, 30, proficiency); }
    /** 1.5.2: 移動だけに強化を使った1秒の過負荷（熟練度100%・最大過負荷1,000で5分）。 */
    public static float moveOverload(float proficiency) { return ArcGen.lerp(1000 / 150f, 1000 / 300f, proficiency); }
    /** 1.5.2: 移動以外（ジャンプ・攻撃・回復・軽減）に強化を使った1秒の過負荷（熟練度100%・最大過負荷1,000で90秒）。移動の分とは重ねない。 */
    public static float combatOverload(float proficiency) { return ArcGen.lerp(1000 / 45f, 1000 / 90f, proficiency); }
    /** 1.5.2: 強化を使っている（最後に使ってから20tick以内）か。CPの維持費はこの間だけ払う。 */
    public static boolean inUse(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); return s != null && s.active && s.age - s.lastUse < 20; }
    /** 強化を使った（1.5.2）。fought: 移動以外の使い方。 */
    private static void used(Session s, boolean fought) {
        s.lastUse = s.age;
        if (fought) s.foughtThisSecond = true; else s.movedThisSecond = true;
    }
    private static @org.jetbrains.annotations.Nullable Session activeWith(net.minecraft.world.entity.Entity entity, Buff buff) {
        if (!(entity instanceof ServerPlayer p)) return null;
        var s = ACTIVE.get(p.getUUID());
        return s != null && s.active && s.buffs.contains(buff) ? s : null;
    }

    public static void tick(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID()); if (s == null) return; var d = data(p);
        if (!valid(p, d, s)) { stop(p); return; }
        long now = p.level().getGameTime(); if (s.lastTick == now) return; s.lastTick = now;
        s.age++;
        if (!s.active) {
            if (!d.consume(ID, s.warmupCost, 0, p.getAbilities().instabuild)) { stop(p); return; }
            if (!carriesOn(p, s)) return;
            if (++s.ticks >= s.warmup) { activate(p, d, s); return; }
            if (s.ticks % 10 == 0) publish(s, true);
            AbilitySyncEvents.sync(p, false);
            return;
        }
        // 維持コストはこのtickの始めに精算済み。払えなかった要求は外れている。
        if (!d.upkeepHeld(s.upkeep)) { stop(p); return; }
        // バフが効いている時間の熟練度（1.1.0）。有効化の瞬間の+0.01は廃止した。
        d.upkeepEarn(s.upkeep);
        // 熟練度のイベントのリスナーが技能を取り上げたら、そこで終える。
        if (!carriesOn(p, s)) return;
        s.ticks++;
        // 1.5.2: 移動（速さ）と体力の回復（再生）は、前のtickとの差で見る。乗り物に乗っている間の移動は数えない。
        var position = p.position();
        if (s.buffs.contains(Buff.SPEED) && s.lastPosition != null && !p.isPassenger()
                && walked(position.subtract(s.lastPosition).horizontalDistanceSqr())) used(s, false);
        s.lastPosition = position;
        if (s.buffs.contains(Buff.REGENERATION) && s.lastHealth >= 0 && p.getHealth() > s.lastHealth) used(s, true);
        s.lastHealth = p.getHealth();
        // 1秒ごとに、その秒の使い方で稼働の過負荷を払う（移動以外があればそちら、移動だけなら移動の分）。次の1秒まで稼働中にする。
        if (s.ticks % 20 == 0) {
            float proficiency = d.getProficiency(ID);
            float overload = s.foughtThisSecond ? combatOverload(proficiency) : s.movedThisSecond ? moveOverload(proficiency) : 0;
            s.foughtThisSecond = false; s.movedThisSecond = false;
            if (overload > 0) {
                d.chargeActionOverload(ID, overload, p.getAbilities().instabuild, 21);
                if (!carriesOn(p, s)) return;
            }
        }
        float exhaustion = AcademyConfig.bodyExtraExhaustion();
        // Player.causeFoodExhaustionは、すべての消耗と同じく無敵（クリエイティブ、スペクテイター）のプレイヤーを飛ばす。
        if (exhaustion > 0 && !p.getAbilities().invulnerable) { p.causeFoodExhaustion(exhaustion); s.exhaustion += exhaustion; }
        AbilitySyncEvents.sync(p, false);
    }
    private static void activate(ServerPlayer p, PlayerAbilityData d, Session s) {
        s.buffs = choose(s.band, p.getRandom()); s.active = true; s.ticks = 0;
        // 1.5.2: CPの維持費は、強化を使っている間（最後に使ってから20tick）だけ払う。
        s.upkeep = d.startUpkeep(ID, () -> p.getAbilities().instabuild || s.age - s.lastUse >= 20);
        if (s.upkeep == null) { stop(p); return; }
        // 維持の熟練度は、待機と同じ0.1パーセントポイント/秒を技能だけに受け取り、能力レベルの経験値は受け取らない。
        // CPは部類の外の切り替え型の共通の形で払う（1.5.2。待機と行動の区別は無い）。
        s.upkeep.alwaysActing().skillOnlyHold().recoveryShare();
        if (!carriesOn(p, s)) return;
        for (var buff : s.buffs) { add(p, s, buff); if (!carriesOn(p, s)) return; }
        p.level().playSound(null, p, AcademySounds.EM_INTENSIFY_ACTIVATE.get(), SoundSource.AMBIENT, .5f, 1f);
        publish(s, false, true); // 受け付けたHUDの完了は所有者だけが受け取る。
        // 原作のコンテキストは、完了時ではなく開始時に近くの観測者を取り込む。音は別扱い。
        var burst = new IntensifyBurstEffect(s.level.dimension().identifier(), s.owner, s.entityId, s.effectSession);
        for (var id : s.listeners) {
            var listener = s.level.getServer().getPlayerList().getPlayer(id);
            if (listener != null && listener.level() == s.level) AcademyNetwork.send(listener, burst);
        }
        BodyIntensifyState.send(p, BodyIntensifyState.ACTIVE, s.slot);
        AbilitySyncEvents.sync(p, true);
    }
    /** 強化の直接効果を、トグルが続く間だけ（{@link DirectEffects#BOUND}）付ける。発生元は術者自身。 */
    private static void add(ServerPlayer p, Session s, Buff buff) {
        DirectEffects.add(p, buff.kind, s.band.amplifier(buff), DirectEffects.BOUND, ID, p);
    }
    /** テスト用の入口: ゲーム時刻に関係なくサーバー1tick分を進める（テストは1回に多数進める）。 */
    public static void tickForTest(ServerPlayer p) { var s = ACTIVE.get(p.getUUID()); if (s != null) s.lastTick = Long.MIN_VALUE; tick(p); }

    // v1.5.0: 牛乳・トーテム・他MODによるバニラの効果の削除（MobEffectEvent.Remove）では、もう止めない。
    // 権限のある/effect clearは、DirectEffects.commandがstopByCommandを呼ぶ。
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) { if (e.getEntity() instanceof ServerPlayer p) tick(p); }
    /** 1.5.2: 跳躍の強化があるときのジャンプは、強化を使ったものとして数える。 */
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent e) {
        var s = activeWith(e.getEntity(), Buff.JUMP);
        if (s != null) used(s, true);
    }
    /**
     * 1.5.2: 歩く・走るとして数える1tickの水平の移動（距離の2乗）。動いていて、1tickに {@link #MAX_WALK_PER_TICK} ブロック以下。
     * それより大きい移動（瞬間移動・押し出しなど）は、強化を使った移動に数えない。
     */
    public static final double MAX_WALK_PER_TICK = 1.5;
    public static boolean walked(double horizontalDistanceSqr) {
        return horizontalDistanceSqr > 1.0E-6 && horizontalDistanceSqr <= MAX_WALK_PER_TICK * MAX_WALK_PER_TICK;
    }
    /** 1.5.2: 筋力の強化を使った攻撃か。プレイヤー自身の素手・武器の近接攻撃（バニラの player_attack）だけ。能力の攻撃は数えない。 */
    public static boolean meleeUse(net.minecraft.world.damagesource.DamageSource source) {
        var attacker = source.getEntity();
        return attacker != null && attacker == source.getDirectEntity()
                && source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK);
    }
    /** 1.5.2: 筋力の強化での近接攻撃と、耐性の強化で軽減した被ダメージは、強化を使ったものとして数える。 */
    @SubscribeEvent public static void hurt(AcademyLivingHurtEvent e) {
        if (e.isCanceled() || e.getAmount() <= 0) return;
        var source = e.getSource();
        var attacker = source.getEntity();
        if (meleeUse(source)) {
            var s = activeWith(attacker, Buff.STRENGTH);
            if (s != null) used(s, true);
        }
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)) {
            var s = activeWith(e.getEntity(), Buff.RESISTANCE);
            if (s != null) used(s, true);
        }
    }
    /** テスト用: 強化を使ったものとして数える（fought: 移動以外）。 */
    public static void useForTest(ServerPlayer p, boolean fought) { var s = ACTIVE.get(p.getUUID()); if (s != null && s.active) used(s, fought); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /** 死亡またはエンドからの帰還: 新しいプレイヤーはセッションを持たない。クールダウンは既存の死亡と帰還の規則に従うので、ここでは設定しない。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.remove(p.getUUID()) != null) {
            DirectEffects.remove(p, ID);
            SkillModeState.send(p, ID, false);
            BodyIntensifyState.send(p, BodyIntensifyState.OFF, 0);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); nextEffectSession = 0; }
}
