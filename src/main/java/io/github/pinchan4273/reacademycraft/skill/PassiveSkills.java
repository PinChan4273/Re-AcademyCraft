package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 1.5.2: 技能のパッシブ（能力使用モードで自動で起動）とアクティブ（スロットのキーで起動）の切り替え。
 * <ul>
 * <li>切り替えられるのは11: ストームウィング・ジェット・エンジン・ライト・シールド・ベクトル偏向・ベクトル反射・白翼自動防御と、
 *     切り替え型の5つ（身体強化・エレクトロン・ミサイル・フラッシング・未元光線・天界発現。{@link #TOGGLES}）（{@link #SWITCHABLE}）。
 *     パッシブを選べるのは、その系統で習得済みで、熟練度100%のときだけ（サーバーが確かめる。{@link #refusal}）。</li>
 * <li>パッシブにすると、すべてのプリセットのスロットから外す。パッシブの間はスロットに置けない。アクティブに戻してもスロットは戻さない。</li>
 * <li>計算式はアクティブと同じ（技能の開始の入口に{@link #SLOT}を渡して始める）。パッシブで動いている技能は、プリセットの切り替えでは止まらない。</li>
 * <li>偏向と反射は排他: もう一方がパッシブの間はパッシブにできない（{@link Refusal#EXCLUSIVE}）。古い保存で両方がパッシブでも、
 *     もう一方が動いている間は自動で入れないので、同時には動かない。</li>
 * <li>防御の4つと切り替え型の5つ: 能力使用モードがオンで使える間（{@link PlayerAbilityData#canUseAbility}）、動いていなければ自動で起動する。
 *     過負荷のロック・干渉・再使用の待ち・CPセーフティ・リスポーン・ディメンションの移動・ログインで止まっても、使えるようになったら起動し直す。
 *     起動を断られたら{@link #RETRY_TICKS}tick待ってから試し直す（毎tickの試行を避ける）。</li>
 * <li>切り替え型の5つ: 起動の費用・過負荷・維持費・再使用の待ちは、キーで始めるときと同じ（技能の開始の入口をそのまま使う）。
 *     その技能のセッションが何か動いている間は始めない（二重の起動と、開始の入口の「動いていれば止める」を避ける）。
 *     パッシブにした時にスロットから動いていたセッションは、止めずにパッシブのセッションとして引き継ぐ（{@link #adopt}。開始の費用を二度払わない）。
 *     未元光線は、空気砲と白翼刺突が動いている間は始めない（技能どうしの排他。それらが終わったら始める）。
 *     パッシブで動いていたものが止まったら、{@link #RETRY_TICKS}tick待ってから起動し直す（毎tickの起動と停止の繰り返しを避ける。{@link #noticeStop}）。</li>
 * <li>飛行の2つ（{@link #FLIGHTS}）: モードをオンにしただけでは飛ばない。白翼飛行と同じく、空中でジャンプを長押し（設定が2回押しなら2回押し）すると、
 *     術者のクライアントが離陸を頼む（{@link #takeoff}）。空中でスニーク2回（2回押しの設定ならジャンプ2回）・着地で、停止を頼む（{@link #land}）。
 *     止まった後は離陸できる状態に戻るだけ。ストームウィングの溜めは離陸の特徴として残る。</li>
 * </ul>
 * パッシブで起動している技能の数は、毎tick{@link PlayerAbilityData#setPassiveRunningCount}へ置く（過負荷の回復の倍率）。
 * <p>1.5.2: CPセーフティがオンの間は、自動の起動（入れ直し）を今のプリセットのセーフティの並び（{@link SafetyPriority}）の
 * 上から、1人につき1 tick に1つまで試す。CP が「ライン（＋形態の予約）＋動いている維持の1秒分＋候補の起動費＋候補の維持の1秒分」を上回るときだけ試し、
 * 上の候補が試せない（閾値・再使用の待ち・試し直しの待ち）間は、下を試さない。手動の切り替えと、本人が切った飛行は入れ直さない。
 * 精算が払えない・セーフティのために止めた飛行（白翼飛行・パッシブのストームウィングとジェット・エンジン）は、空中にいれば滞空だけで戻す
 * （ストームウィングは溜めから、ジェット・エンジンは離陸の費用を払う）。着地したら戻すのをやめる。セーフティがオフの間は、今までどおり技能ごとに試す。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class PassiveSkills {
    private PassiveSkills() { }

    /** パッシブで起動するときに、技能の開始の入口へ渡すスロットのインデックス（本物のスロットは0〜3）。 */
    public static final int SLOT = -1;
    /** 切り替えられる技能（能力設定の並びの順）。 */
    public static final List<Identifier> SWITCHABLE = List.of(StormWing.ID, VecDeviation.ID, VecReflection.ID, JetEngine.ID, LightShield.ID, DarkMatter.GUARD,
            BodyIntensify.ID, ElectronMissile.ID, Flashing.ID, DarkMatter.RAY, DarkMatter.HEAVEN);
    /** 切り替えられる技能のうち、部類の外の切り替え型（攻撃・強化）。費用はオンの間ずっと払う。 */
    public static final Set<Identifier> TOGGLES = Set.of(BodyIntensify.ID, ElectronMissile.ID, Flashing.ID, DarkMatter.RAY, DarkMatter.HEAVEN);
    /** 切り替えられる技能のうち、飛行の部類のもの（パッシブでも、ジャンプの操作で離陸する）。 */
    public static final Set<Identifier> FLIGHTS = Set.of(StormWing.ID, JetEngine.ID);
    /** 能力設定の値: アクティブ・パッシブ。 */
    public static final String ACTIVE = "active", PASSIVE = "passive";
    /** 自動の起動を断られた後、次に試すまでのtick。 */
    public static final int RETRY_TICKS = 10;
    /** プレイヤーごと・技能ごとの、次に自動の起動を試してよいゲームの時刻（サーバーだけ。保存しない）。 */
    private static final Map<UUID, Map<Identifier, Long>> RETRY = new HashMap<>();
    /** プレイヤーごとの、前に見たときパッシブで動いていた切り替え型の技能（止まったのを見つけるため。サーバーだけ。保存しない）。 */
    private static final Map<UUID, Set<Identifier>> RUNNING = new HashMap<>();
    /** 1.5.2: 精算が払えない・セーフティのために止めた、空中で戻す飛行（サーバーだけ。保存しない）。 */
    private static final Map<UUID, Set<Identifier>> FALLEN = new HashMap<>();
    /**
     * 1.5.2（ロックからの戻し方）: ロック中の再稼働。過負荷のロック中に回復が始まったら、CPセーフティの優先順位の
     * 上から、パッシブ（白翼飛行を含む）を {@link #RETRY_TICKS} tick ごとに1つずつ再稼働する。再稼働の前に、今の過負荷＋開始の過負荷＋確保が
     * 最大過負荷の {@link #LOCK_RESTART_SHARE} 以下かを確かめ、超えるなら下がるまで待つ（下の技能を先にしない）。
     * 再稼働した技能はロックが解けるまで維持だけ（稼働の払いは {@link PlayerAbilityData#payAction} などが断る）。キーでの開始は今どおり断る。
     */
    public static final float LOCK_RESTART_SHARE = .95f;
    /** 1.5.2: ロック中の次の再稼働を試してよい時刻（サーバーだけ。保存しない）。 */
    private static final Map<UUID, Long> LOCK_NEXT = new HashMap<>();
    /** 1.5.2: このロックで、回復が始まって再稼働を始めたプレイヤー（一度始めたら、その後の開始の過負荷で待ちが延びても続ける）。 */
    private static final Set<UUID> LOCK_BEGUN = new java.util.HashSet<>();
    /** 1.5.2: ロックした瞬間に飛んでいた飛行（順番が来たら、空中なら戻す）。白翼飛行と、パッシブの風の翼・ジェット・エンジン。 */
    private static final Map<UUID, Set<Identifier>> LOCK_FALLEN = new HashMap<>();
    /** セッションの持ち主の名前（{@code SkillDispatch.Owner#name}）→ 技能。プリセットの切り替えで止めないものを決める。 */
    private static final Map<String, Identifier> OWNERS = Map.ofEntries(Map.entry("StormWing", StormWing.ID), Map.entry("VecDeviation", VecDeviation.ID),
            Map.entry("VecReflection", VecReflection.ID), Map.entry("JetEngine", JetEngine.ID), Map.entry("LightShield", LightShield.ID),
            Map.entry("WhiteWingGuard", DarkMatter.GUARD), Map.entry("BodyIntensify", BodyIntensify.ID), Map.entry("ElectronMissile", ElectronMissile.ID),
            Map.entry("Flashing", Flashing.ID), Map.entry("DarkMatterRay", DarkMatter.RAY), Map.entry("HeavenManifest", DarkMatter.HEAVEN));

    private static @Nullable PlayerAbilityData data(ServerPlayer player) { return AbilityCapabilities.of(player).orElse(null); }

    public static boolean switchable(@Nullable Identifier skill) { return skill != null && SWITCHABLE.contains(skill); }
    public static boolean flight(@Nullable Identifier skill) { return skill != null && FLIGHTS.contains(skill); }
    /** 部類の外の切り替え型か。 */
    public static boolean toggle(@Nullable Identifier skill) { return skill != null && TOGGLES.contains(skill); }
    /** その系統で切り替えられる技能（能力設定の並びの順）。系統が無ければ空。 */
    public static List<Identifier> switchableIn(@Nullable Identifier category) {
        var out = new ArrayList<Identifier>();
        if (category != null) for (var skill : SWITCHABLE) if (category.equals(AbilitySettingsVisibility.categoryOf(skill))) out.add(skill);
        return List.copyOf(out);
    }

    /** 能力設定の、その技能の切り替えの設定の名前（例 {@code academy:passive.storm_wing}）。 */
    public static Identifier setting(Identifier skill) { return Identifier.fromNamespaceAndPath("academy", "passive." + skill.getPath()); }
    /** 設定の名前から、その切り替えの技能（切り替えの設定でなければnull）。 */
    public static @Nullable Identifier skillOfSetting(@Nullable Identifier setting) {
        if (setting == null) return null;
        for (var skill : SWITCHABLE) if (setting(skill).equals(setting)) return skill;
        return null;
    }

    /** パッシブにできない理由。 */
    public enum Refusal {
        NOT_SWITCHABLE("not_switchable"), READ_ONLY("read_only"), NOT_LEARNED("unlearned"), PROFICIENCY("proficiency"),
        /** 1.5.2: 偏向と反射は排他なので、もう一方がパッシブの間はパッシブにできない。 */
        EXCLUSIVE("exclusive");
        private final String id;
        Refusal(String id) { this.id = id; }
        /** 能力設定に出す理由の翻訳キー。 */
        public String translation() { return "academy.ability_settings.passive.reason." + id; }
    }
    /**
     * その技能を今パッシブにできるか（できればnull）。サーバーと画面で同じ判定を使う: 切り替えられる技能で、その系統で習得済みで、
     * 熟練度100%（保存された値そのもの）。
     */
    public static @Nullable Refusal refusal(@Nullable PlayerAbilityData data, Identifier skill) {
        if (!switchable(skill)) return Refusal.NOT_SWITCHABLE;
        if (data == null || data.isReadOnly()) return Refusal.READ_ONLY;
        var category = AbilitySettingsVisibility.categoryOf(skill);
        if (category == null || !category.equals(data.getAbility()) || !data.hasLearned(skill)) return Refusal.NOT_LEARNED;
        if (data.getProficiency(skill) < 1) return Refusal.PROFICIENCY;
        var rival = exclusiveRival(skill);
        if (rival != null && data.isPassiveSkill(rival)) return Refusal.EXCLUSIVE;
        return null;
    }
    /** 1.5.2: 同時に動けない相手の技能（偏向と反射。ほかは null）。 */
    public static @Nullable Identifier exclusiveRival(@Nullable Identifier skill) {
        if (VecDeviation.ID.equals(skill)) return VecReflection.ID;
        if (VecReflection.ID.equals(skill)) return VecDeviation.ID;
        return null;
    }

    /** 切り替えの結果。 */
    public enum Result { CHANGED, UNCHANGED, REFUSED, NO_DATA, READ_ONLY }
    /**
     * 本人の切り替えを1つ変える（サーバー。能力設定の画面とテストから）。パッシブにするときは{@link #refusal}で確かめ、すべてのスロットから外す。
     * アクティブに戻すときは、パッシブで動いているその技能を止める（飛んでいれば落ちる）。条件は見ない（いつでも戻せる）。
     */
    public static Result set(ServerPlayer player, Identifier skill, boolean passive) {
        var data = data(player);
        if (data == null) return Result.NO_DATA;
        if (data.isReadOnly()) return Result.READ_ONLY;
        if (!switchable(skill)) return Result.REFUSED;
        if (passive == data.isPassiveSkill(skill)) return Result.UNCHANGED;
        if (passive) {
            if (refusal(data, skill) != null) return Result.REFUSED;
            data.setPassiveSkill(skill, true);
            forgetRetry(player, skill);
            adopt(player, skill);
        } else {
            data.setPassiveSkill(skill, false);
            stopPassive(player, skill);
        }
        AbilitySyncEvents.sync(player, true);
        return Result.CHANGED;
    }

    /**
     * 技能の開始の入口が使う、スロットの確かめ: {@link #SLOT}ならパッシブに切り替えていること、そうでなければ今のプリセットのそのスロットにその技能があること。
     */
    public static boolean slotMatches(PlayerAbilityData data, Identifier skill, int slot) {
        if (slot == SLOT) return data.isPassiveSkill(skill);
        return slot >= 0 && slot < PlayerAbilityData.SLOT_COUNT && skill.equals(data.getSlot(data.getCurrentPreset(), slot));
    }
    /**
     * 動いているセッションを続けてよいかの、スロットの確かめ: パッシブのセッション（{@link #SLOT}）はパッシブのままであること、
     * スロットのセッションは、始めたときのプリセットのままで、そのスロットにその技能があること。
     */
    public static boolean sessionMatches(PlayerAbilityData data, Identifier skill, int preset, int slot) {
        if (slot == SLOT) return data.isPassiveSkill(skill);
        return data.getCurrentPreset() == preset && skill.equals(data.getSlot(preset, slot));
    }

    /** その技能がパッシブで動いているか（飛行は飛んでいる間だけ）。 */
    public static boolean runningPassively(ServerPlayer player, Identifier skill) {
        if (StormWing.ID.equals(skill)) return StormWing.passive(player);
        if (JetEngine.ID.equals(skill)) return JetEngine.passive(player);
        if (LightShield.ID.equals(skill)) return LightShield.passive(player);
        if (VecDeviation.ID.equals(skill)) return VecDeviation.passive(player);
        if (VecReflection.ID.equals(skill)) return VecReflection.passive(player);
        if (DarkMatter.GUARD.equals(skill)) return DarkDefense.passive(player);
        if (BodyIntensify.ID.equals(skill)) return BodyIntensify.passive(player);
        if (ElectronMissile.ID.equals(skill)) return ElectronMissile.passive(player);
        if (Flashing.ID.equals(skill)) return Flashing.passive(player);
        if (DarkMatter.RAY.equals(skill)) return DarkMatterRay.passive(player);
        if (DarkMatter.HEAVEN.equals(skill)) return HeavenManifest.passive(player);
        return false;
    }
    /**
     * 自動の起動を今は試さないか: その技能がパッシブで動いている。切り替え型の5つは、スロットから始めたセッションでも動いていれば試さない
     * （開始の入口は、動いていれば止めるか何もしないため）。未元光線は、空気砲・白翼刺突が動いている間も試さない（技能どうしの排他）。
     */
    private static boolean busy(ServerPlayer player, Identifier skill) {
        if (runningPassively(player, skill)) return true;
        // 1.5.2: 偏向と反射は排他。もう一方が動いている間は自動で入れない（手動で後から入れた方を止めない）。
        if (VecDeviation.ID.equals(skill)) return VecDeviation.active(player) || VecReflection.active(player);
        if (VecReflection.ID.equals(skill)) return VecReflection.active(player) || VecDeviation.active(player);
        if (BodyIntensify.ID.equals(skill)) return BodyIntensify.isCharging(player);
        if (ElectronMissile.ID.equals(skill)) return ElectronMissile.holding(player);
        if (Flashing.ID.equals(skill)) return Flashing.active(player);
        if (DarkMatter.RAY.equals(skill)) return DarkMatterRay.active(player) || DarkMatterAirCannon.active(player) || WhiteWingStab.active(player);
        if (DarkMatter.HEAVEN.equals(skill)) return HeavenManifest.active(player);
        return false;
    }
    /**
     * パッシブにした時に、スロットのキーで始めたその技能のセッションが動いていたら、止めずにパッシブのセッションとして引き継ぐ（切り替え型の5つだけ。
     * 開始の費用を二度払わず、スロットに無いので止められなくなることも無い）。防御・飛行の6つは今までどおり（次のtickに止まり、起動し直す）。
     */
    private static void adopt(ServerPlayer player, Identifier skill) {
        if (BodyIntensify.ID.equals(skill)) BodyIntensify.adoptPassive(player);
        else if (ElectronMissile.ID.equals(skill)) ElectronMissile.adoptPassive(player);
        else if (Flashing.ID.equals(skill)) Flashing.adoptPassive(player);
        else if (DarkMatter.RAY.equals(skill)) DarkMatterRay.adoptPassive(player);
        else if (DarkMatter.HEAVEN.equals(skill)) HeavenManifest.adoptPassive(player);
    }
    /** パッシブで動いている技能の数。 */
    public static int runningCount(ServerPlayer player) {
        int count = 0;
        for (var skill : SWITCHABLE) if (runningPassively(player, skill)) count++;
        return count;
    }
    /** プリセットの切り替えで、その持ち主を止めずにおくか（パッシブで動いている技能の持ち主）。 */
    public static boolean keepsOnPresetChange(ServerPlayer player, String owner) {
        var skill = OWNERS.get(owner);
        return skill != null && runningPassively(player, skill);
    }
    /** パッシブで動いているその技能を止める（動いていなければ何もしない）。 */
    private static void stopPassive(ServerPlayer player, Identifier skill) {
        if (!runningPassively(player, skill)) return;
        if (StormWing.ID.equals(skill)) StormWing.stop(player);
        else if (JetEngine.ID.equals(skill)) JetEngine.stop(player);
        else if (LightShield.ID.equals(skill)) LightShield.stop(player);
        else if (VecDeviation.ID.equals(skill)) VecDeviation.stop(player);
        else if (VecReflection.ID.equals(skill)) VecReflection.stop(player);
        else if (DarkMatter.GUARD.equals(skill)) DarkDefense.stop(player, DarkMatter.GUARD);
        else if (BodyIntensify.ID.equals(skill)) BodyIntensify.stop(player);
        else if (ElectronMissile.ID.equals(skill)) ElectronMissile.stop(player);
        else if (Flashing.ID.equals(skill)) Flashing.stop(player);
        else if (DarkMatter.RAY.equals(skill)) DarkMatterRay.stop(player);
        else if (DarkMatter.HEAVEN.equals(skill)) HeavenManifest.stop(player);
    }

    /**
     * 防御・切り替え型の技能をパッシブで始める。成功なら空、断ったら理由（{@code academy.cast.*}）。切り替え型は、キーで始めるときと同じ入口
     * （同じ条件・起動の費用・過負荷）に{@link #SLOT}を渡す。
     */
    private static String startAuto(ServerPlayer player, PlayerAbilityData data, Identifier skill) {
        if (LightShield.ID.equals(skill)) return LightShield.start(player, data, SLOT);
        if (VecDeviation.ID.equals(skill)) return VecDeviation.toggle(player, data, SLOT);
        if (VecReflection.ID.equals(skill)) return VecReflection.toggle(player, data, SLOT);
        if (DarkMatter.GUARD.equals(skill)) return DarkDefense.startPassive(player, data);
        if (BodyIntensify.ID.equals(skill)) return BodyIntensify.start(player, data, SLOT);
        if (ElectronMissile.ID.equals(skill)) return ElectronMissile.start(player, data, SLOT);
        if (Flashing.ID.equals(skill)) return Flashing.toggle(player, data, SLOT);
        if (DarkMatter.RAY.equals(skill)) return DarkMatterRay.toggle(player, data, SLOT);
        if (DarkMatter.HEAVEN.equals(skill)) return HeavenManifest.toggle(player, data, SLOT);
        return "academy.cast.unlearned";
    }

    /**
     * 自動の起動とパッシブの数の数え直し。毎tick、サーバーのプレイヤーごとに呼ぶ。
     * 防御・切り替え型の技能は、パッシブで、モードがオンで使え（ロック・干渉の外）、CPセーフティが止めておらず、その技能が動いていなければ始める。
     * 飛行の技能はここでは始めない（離陸はジャンプの操作。{@link #takeoff}）。
     */
    static void tick(ServerPlayer player) {
        var data = data(player);
        if (data == null || data.isReadOnly() || !data.hasAbility()) {
            if (data != null && !data.isReadOnly()) data.setPassiveRunningCount(0);
            return;
        }
        long now = player.level().getGameTime();
        // 1.5.2（ロックからの戻し方）: ロック中は、優先順位の順の再稼働だけを行う。
        if (data.isOverloadLocked()) { tickLocked(player, data, now); data.setPassiveRunningCount(runningCount(player)); return; }
        forgetLock(player);
        boolean usable = player.isAlive() && !player.isSpectator() && data.canUseAbility() && !data.safetyBlinking();
        // 1.5.2: 精算が止めた飛行を覚える（本人が止めた物は覚えない）。
        noticeFallen(player, data);
        if (data.isCpSafety()) { tickOrdered(player, data, usable, now); data.setPassiveRunningCount(runningCount(player)); return; }
        for (var skill : SWITCHABLE) {
            if (toggle(skill)) noticeStop(player, skill, now);
            if (!usable || flight(skill) || !data.isPassiveSkill(skill) || busy(player, skill)) continue;
            if (!switchableIn(data.getAbility()).contains(skill)) continue;
            // 再使用の待ちの間は試さない（待ちが終わったtickに起動し直す。試し直しの待ちは掛けない）。
            if (data.getCooldown(skill) > 0) continue;
            var retry = RETRY.get(player.getUUID());
            Long at = retry == null ? null : retry.get(skill);
            if (at != null && now < at) continue;
            startAuto(player, data, skill);
            // 開始のイベントのリスナーが、データを読み替えたかもしれない。
            if (data(player) != data) return;
            if (runningPassively(player, skill)) {
                forgetRetry(player, skill);
                if (toggle(skill)) RUNNING.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(skill);
            } else RETRY.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(skill, now + RETRY_TICKS);
        }
        data.setPassiveRunningCount(runningCount(player));
    }
    /**
     * 1.5.2（ロックからの戻し方）: ロック中の再稼働。回復が始まってから、今のプリセットの優先順位の上から1つずつ、{@link #RETRY_TICKS} tick あけて
     * 再稼働を許し、パッシブを始める（飛行は、ロックした瞬間に飛んでいて今も空中なら、滞空で戻す）。過負荷が
     * {@link #LOCK_RESTART_SHARE} の線を超えるなら、その技能の番で待つ（下を先にしない）。CPや再使用の待ちなど、過負荷でない理由で
     * 始められなかった物は、試し直しの待ちを置いて、下の技能へ進む。
     */
    private static void tickLocked(ServerPlayer player, PlayerAbilityData data, long now) {
        var id = player.getUUID();
        if (!player.isAlive() || player.isSpectator() || !data.isActive() || data.isInterfering()) return;
        if (!LOCK_BEGUN.contains(id)) {
            if (!data.isLockRecovering()) return;
            LOCK_BEGUN.add(id);
        }
        Long next = LOCK_NEXT.get(id);
        if (next != null && now < next) return;
        var category = data.getAbility();
        var fallen = LOCK_FALLEN.get(id);
        for (var skill : SafetyPriority.resolve(data.getSafetyOrder(data.getCurrentPreset()))) {
            if (!category.equals(AbilitySettingsVisibility.categoryOf(skill)) || !data.hasLearned(skill)) continue;
            boolean flight = SafetyPriority.group(skill) == SafetyPriority.Group.FLIGHT;
            // 対象: 白翼飛行（元からのパッシブ）と、パッシブにしている技能。キーで使う技能は再稼働しない。
            if (flight ? !(DarkMatter.FLIGHT.equals(skill) || data.isPassiveSkill(skill)) : !(switchable(skill) && data.isPassiveSkill(skill))) continue;
            boolean allowed = data.allowedDuringLock(skill);
            // 順番が来た飛行・動いている技能は済み。
            if (allowed && (flight || busy(player, skill))) continue;
            // 再使用の待ち・試し直しの待ちの物は、過負荷のせいではないので、下へ進む（順番は許しておく）。
            var retry = RETRY.get(id);
            Long at = retry == null ? null : retry.get(skill);
            if (!flight && (data.getCooldown(skill) > 0 || (at != null && now < at))) { if (!allowed) data.allowDuringLock(skill); continue; }
            if (data.getOverload() + lockRestartOverload(data, skill) > LOCK_RESTART_SHARE * data.getMaxOverload()) return;
            data.allowDuringLock(skill);
            LOCK_NEXT.put(id, now + RETRY_TICKS);
            if (flight) {
                if (fallen != null && fallen.remove(skill) && !player.onGround() && !flyingNow(player, skill)) restoreLockedFlight(player, data, skill);
                return;
            }
            startAuto(player, data, skill);
            if (data(player) != data) return;
            if (runningPassively(player, skill)) {
                forgetRetry(player, skill);
                if (toggle(skill)) RUNNING.computeIfAbsent(id, k -> new java.util.HashSet<>()).add(skill);
                return;
            }
            // 過負荷でない理由で始められなかった: 試し直しの待ちを置き、この tick は下へ進まない（次は RETRY_TICKS 後）。
            RETRY.computeIfAbsent(id, k -> new HashMap<>()).put(skill, now + RETRY_TICKS);
            return;
        }
    }
    /** 1.5.2: ロック中の再稼働で、その技能が上げる過負荷の見積もり（開始の過負荷＋確保。技能の過負荷の倍率込み）。 */
    public static float lockRestartOverload(PlayerAbilityData data, Identifier skill) {
        float p = data.getProficiency(skill);
        float start = 0;
        if (ElectronMissile.ID.equals(skill)) start = ElectronMissile.startOverload(p);
        else if (Flashing.ID.equals(skill)) start = Flashing.startOverload(p);
        else if (BodyIntensify.ID.equals(skill)) start = BodyIntensify.startOverload(p);
        else if (DarkMatter.RAY.equals(skill)) start = DarkMatterRay.START_OVERLOAD;
        else if (DarkMatter.HEAVEN.equals(skill)) start = HeavenManifest.START_OVERLOAD;
        else if (DarkMatter.FLIGHT.equals(skill) || JetEngine.ID.equals(skill)) start = FlightCost.takeoffOverload(p);
        var kind = DefenseCost.kindOf(skill);
        float reserve = kind == null ? 0 : kind.reserve(p);
        return (float) (io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillOverloadSpeed(skill) * (start + reserve));
    }
    /** 1.5.2: ロックが解けたら、ロック中の再稼働のフラグを忘れる。 */
    private static void forgetLock(ServerPlayer player) {
        var id = player.getUUID();
        LOCK_NEXT.remove(id); LOCK_BEGUN.remove(id); LOCK_FALLEN.remove(id);
    }
    /**
     * 1.5.2: ロックした瞬間に飛んでいた飛行を覚える（技能を止める{@link SkillSessions}より先に受け取る）。白翼飛行と、パッシブの風の翼・ジェット・エンジンだけ
     * （キーで始めた飛行は戻さない）。
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void passiveOnOverload(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload event) {
        if (!(event.player() instanceof ServerPlayer player)) return;
        var data = data(player);
        var id = player.getUUID();
        LOCK_NEXT.remove(id); LOCK_BEGUN.remove(id);
        var flying = new java.util.HashSet<Identifier>();
        if (WhiteWingFlight.active(player)) flying.add(DarkMatter.FLIGHT);
        if (data != null && data.isPassiveSkill(StormWing.ID) && StormWing.active(player)) flying.add(StormWing.ID);
        if (data != null && data.isPassiveSkill(JetEngine.ID) && JetEngine.holding(player)) flying.add(JetEngine.ID);
        if (flying.isEmpty()) LOCK_FALLEN.remove(id); else LOCK_FALLEN.put(id, flying);
    }
    /** テスト用: ロックした瞬間に飛んでいた飛行として覚えさせる。 */
    public static void lockFallenForTest(ServerPlayer player, Identifier skill) { LOCK_FALLEN.computeIfAbsent(player.getUUID(), k -> new java.util.HashSet<>()).add(skill); }
    /** テスト用: ロック中の再稼働の待ちを忘れる（次の tick にすぐ次を試す）。 */
    public static void forgetLockNextForTest(ServerPlayer player) { LOCK_NEXT.remove(player.getUUID()); }
    /**
     * 1.5.2: セーフティがオンの間の入れ直し。並びの上から、1人につき1 tick に1つまで。上の候補が試せない間は、下を試さない。
     */
    private static void tickOrdered(ServerPlayer player, PlayerAbilityData data, boolean usable, long now) {
        for (var skill : SWITCHABLE) if (toggle(skill)) noticeStop(player, skill, now);
        if (!usable) return;
        var category = data.getAbility();
        var fallen = FALLEN.get(player.getUUID());
        for (var skill : SafetyPriority.resolve(data.getSafetyOrder(data.getCurrentPreset()))) {
            if (!category.equals(AbilitySettingsVisibility.categoryOf(skill)) || !data.hasLearned(skill)) continue;
            boolean flight = SafetyPriority.group(skill) == SafetyPriority.Group.FLIGHT;
            if (flight) {
                // 止まった飛行は、空中にいる間だけ、滞空で戻す。着地したら戻すのをやめる。
                if (fallen == null || !fallen.contains(skill)) continue;
                if (player.onGround() || flyingNow(player, skill)) { fallen.remove(skill); continue; }
            } else {
                if (!switchable(skill) || !data.isPassiveSkill(skill) || busy(player, skill)) continue;
            }
            // 上の候補が待ちの間は、下を試さない（下を先に立ち上げて、上の分の余力を食わないため）。
            if (data.getCooldown(skill) > 0) return;
            var retry = RETRY.get(player.getUUID());
            Long at = retry == null ? null : retry.get(skill);
            if (at != null && now < at) return;
            if (!restartAffordable(player, data, skill)) return;
            String why = flight ? restoreFlight(player, data, skill) : startAuto(player, data, skill);
            if (data(player) != data) return;
            boolean started = flight ? flyingNow(player, skill) : runningPassively(player, skill);
            if (started) {
                forgetRetry(player, skill);
                if (flight && fallen != null) fallen.remove(skill);
                if (toggle(skill)) RUNNING.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(skill);
            } else RETRY.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(skill, now + RETRY_TICKS);
            // 1 tick に1つまで（始まった物の維持費が次の精算に入ってから、次を判断する）。
            return;
        }
    }
    /** 精算が払えない・セーフティのために止めた飛行を、空中で戻す候補として覚える。 */
    private static void noticeFallen(ServerPlayer player, PlayerAbilityData data) {
        long now = player.level().getGameTime();
        for (var skill : List.of(DarkMatter.FLIGHT, StormWing.ID, JetEngine.ID)) {
            if (!data.takeSafetyEnded(skill)) continue;
            // パッシブのストームウィング・ジェット・エンジンと、白翼飛行（元からのパッシブ）だけ。手動の切り替えは戻さない。
            if (DarkMatter.FLIGHT.equals(skill) || data.isPassiveSkill(skill)) {
                FALLEN.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(skill);
                // ほかの技能と同じく、止まった後は RETRY_TICKS 待ってから戻す（止めてすぐ戻す繰り返しを避ける）。
                RETRY.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(skill, now + RETRY_TICKS);
            }
        }
        // 防御・切り替え型のフラグは使わない（入れ直しは今のパッシブの決まりで試す）。
        for (var skill : SafetyPriority.CATALOGUE) if (SafetyPriority.group(skill) != SafetyPriority.Group.FLIGHT) data.takeSafetyEnded(skill);
    }
    /** その飛行が今飛んでいるか。 */
    private static boolean flyingNow(ServerPlayer player, Identifier skill) {
        if (DarkMatter.FLIGHT.equals(skill)) return WhiteWingFlight.active(player);
        if (StormWing.ID.equals(skill)) return StormWing.active(player);
        return JetEngine.holding(player);
    }
    /**
     * 1.5.2: ロックした瞬間に飛んでいた飛行を、ロック中の再稼働で戻す。風の翼は溜めをやり直さずにすぐ滞空へ戻す（{@link StormWing#resume}）。
     * ほかは{@link #restoreFlight}と同じ。
     */
    private static String restoreLockedFlight(ServerPlayer player, PlayerAbilityData data, Identifier skill) {
        // 風の翼・ジェット・エンジンも特別扱いせず、ふつうの再稼働と同じ決まりで戻す
        // （風の翼は溜めから、ジェット・エンジンは再使用の待ちが明けてから）。
        return restoreFlight(player, data, skill);
    }
    /** 止まった飛行を滞空で戻す（白翼飛行は離陸、ストームウィングは溜めから、ジェット・エンジンは離陸の費用を払って始める）。 */
    private static String restoreFlight(ServerPlayer player, PlayerAbilityData data, Identifier skill) {
        if (DarkMatter.FLIGHT.equals(skill)) return WhiteWingFlight.takeoff(player, data);
        if (!data.isPassiveSkill(skill)) return "academy.cast.unlearned";
        return StormWing.ID.equals(skill) ? StormWing.toggle(player, data, SLOT) : JetEngine.start(player, data, SLOT);
    }
    /**
     * 1.5.2: 入れ直してよいCPがあるか: CP が「ライン（＋形態の予約）＋動いている維持の1秒分＋候補の起動費＋候補の維持の1秒分」を上回る。
     * 値段は技能の倍率を掛けた額。
     */
    public static boolean restartAffordable(ServerPlayer player, PlayerAbilityData data, Identifier skill) {
        if (player.getAbilities().instabuild) return true;
        double speed = io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillCpSpeed(skill);
        // Storm Wing の推進の差額は維持の精算の外で払うので、ここで足す。
        double need = data.safetyLine() + data.upkeepPerSecond() + StormWing.thrustCostPerSecond(player, data)
                + speed * (startCost(data, skill) + upkeepEstimate(data, skill) * 20);
        return data.getCp() > need;
    }
    /** 候補の起動の費用（技能の倍率の前）。起動に CP を払わない技能は0。 */
    public static float startCost(PlayerAbilityData data, Identifier skill) {
        float p = data.getProficiency(skill);
        if (Flashing.ID.equals(skill)) return Flashing.startConsumption(p);
        if (DarkMatter.RAY.equals(skill)) return DarkMatterRay.START_CP;
        if (DarkMatter.HEAVEN.equals(skill)) return HeavenManifest.START_CP;
        if (DarkMatter.FLIGHT.equals(skill) || JetEngine.ID.equals(skill)) return FlightCost.takeoffCp(p);
        return 0;
    }
    /** 候補の維持の1tickの見積もり（技能の倍率の前。G0 から決める額）。 */
    public static float upkeepEstimate(PlayerAbilityData data, Identifier skill) {
        float g0 = data.g0PerTick(), p = data.getProficiency(skill);
        return switch (SafetyPriority.group(skill)) {
            case DEFENSE -> g0 * DefenseCost.upkeepShare(p);
            case FLIGHT -> JetEngine.ID.equals(skill) ? FlightCost.jetDue(p, g0) : FlightCost.hoverDue(p, g0);
            case OTHER -> PlayerAbilityData.recoveryShareDue(skill, p, g0)
                    + (DarkMatter.HEAVEN.equals(skill) ? HeavenManifest.upkeepPerSecond(data.getHeavenSettings(), p) / 20f : 0);
        };
    }
    /** テスト用の入口（FakePlayerはtickの出来事を出さない）。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }
    /** テスト用: 試し直しの待ちを忘れる（すぐに次の自動の起動を試す）。 */
    public static void forgetRetryForTest(ServerPlayer player) { RETRY.remove(player.getUUID()); RUNNING.remove(player.getUUID()); }
    /** テスト用: その技能の試し直しの待ちが残っているか。 */
    public static boolean retryPendingForTest(ServerPlayer player, Identifier skill) {
        var retry = RETRY.get(player.getUUID());
        Long at = retry == null ? null : retry.get(skill);
        return at != null && player.level().getGameTime() < at;
    }
    /** テスト用: 空中で戻す飛行の候補か。 */
    public static boolean fallenForTest(ServerPlayer player, Identifier skill) { var s = FALLEN.get(player.getUUID()); return s != null && s.contains(skill); }
    private static void forgetRetry(ServerPlayer player, Identifier skill) {
        var retry = RETRY.get(player.getUUID());
        if (retry != null) retry.remove(skill);
        var running = RUNNING.get(player.getUUID());
        if (running != null) running.remove(skill);
    }
    /**
     * 切り替え型: 前に見たときパッシブで動いていたのに今は止まっていたら、{@link #RETRY_TICKS}tick待ってから起動し直す。
     * 再使用の待ちの無い止まり方（身体強化の準備の途中のCP不足、白翼自動防御が攻撃を中断した未元光線など）で、毎tick起動と停止を繰り返し、
     * 起動の費用・過負荷を毎tick払うのを避ける。再使用の待ちのある止まり方は、待ちの方が長い。
     */
    private static void noticeStop(ServerPlayer player, Identifier skill, long now) {
        if (runningPassively(player, skill)) { RUNNING.computeIfAbsent(player.getUUID(), id -> new java.util.HashSet<>()).add(skill); return; }
        var running = RUNNING.get(player.getUUID());
        if (running != null && running.remove(skill)) RETRY.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(skill, now + RETRY_TICKS);
    }

    /** そのプレイヤーがパッシブにしている飛行の技能（今の系統で習得済みのもの。無ければnull）。系統は1人1つなので、高々1つ。 */
    public static @Nullable Identifier passiveFlight(@Nullable PlayerAbilityData data) {
        if (data == null || data.isReadOnly()) return null;
        for (var skill : List.of(StormWing.ID, JetEngine.ID))
            if (data.isPassiveSkill(skill) && data.hasLearned(skill) && switchableIn(data.getAbility()).contains(skill)) return skill;
        return null;
    }
    /**
     * パッシブの飛行の離陸（術者のクライアントの依頼）。条件はキーで始めるときと同じ（ストームウィングは溜めから、ジェット・エンジンは離陸の費用を払う）。
     * 飛んでいれば何もしない。断る理由はテストのために返すが、ほかの技能と同じく画面には出さない。
     */
    public static String takeoff(ServerPlayer player) {
        var data = data(player);
        var skill = passiveFlight(data);
        if (skill == null) return "academy.cast.unlearned";
        if (StormWing.ID.equals(skill) ? StormWing.active(player) : JetEngine.holding(player)) return "";
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働で順番が来た飛行は通す。
        if (data.lockBlocks(skill)) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        return StormWing.ID.equals(skill) ? StormWing.toggle(player, data, SLOT) : JetEngine.start(player, data, SLOT);
    }
    /** パッシブの飛行の停止（術者のクライアントの依頼: 空中でやめる操作か着地）。パッシブで飛んでいるときだけ止める。止めたらtrue。 */
    public static boolean land(ServerPlayer player) {
        for (var skill : FLIGHTS) if (runningPassively(player, skill)) { stopPassive(player, skill); return true; }
        return false;
    }

    @SubscribeEvent public static void passiveOnPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void passiveOnLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        RETRY.remove(e.getEntity().getUUID()); RUNNING.remove(e.getEntity().getUUID()); FALLEN.remove(e.getEntity().getUUID());
        LOCK_NEXT.remove(e.getEntity().getUUID()); LOCK_BEGUN.remove(e.getEntity().getUUID()); LOCK_FALLEN.remove(e.getEntity().getUUID());
    }
    @SubscribeEvent public static void passiveOnStopped(ServerStoppedEvent e) { RETRY.clear(); RUNNING.clear(); FALLEN.clear(); LOCK_NEXT.clear(); LOCK_BEGUN.clear(); LOCK_FALLEN.clear(); }
}
