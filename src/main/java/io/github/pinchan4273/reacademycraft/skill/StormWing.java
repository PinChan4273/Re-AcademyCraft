package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.SelfNotice;
import io.github.pinchan4273.reacademycraft.network.StormWingState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作StormWing: ベクトル操作のレベル3の飛行。
 *
 * キーでモードをオン・オフする。続く間、術者は飛んでよく（原作はサーバーでallowFlyingを設定した）、落下ダメージを受けない。
 * lerp(70, 30, exp)tick溜め、その後有効になる: 移動キーが術者を動かし、それは原作と同じく術者自身のクライアントの仕事
 * （ClientStormWing）。有効な間は毎tick 0.00005の経験値を与える。
 *
 * <p>資源は1.5.2から飛行の部類の基準（{@link FlightCost}）に従い、滞空と推進を分ける:
 * <ul>
 * <li>溜め（lerp(70, 30)tick）は無料で、技能の特徴として残す（離陸の費用の代わり）。</li>
 * <li>浮いているだけ: 毎tick G0 × lerp(1.0, 50%) を払う（1.5.2。G0 は待ちの無いときの回復で、回復待ちの間も同じ額）。
 * 過負荷はふつうに回復する（1.5.1の「正味で40 × (1 − 熟練度)/秒ずつ減る」と、過負荷の回復を2〜4/秒に置き換える作りは無くした）。</li>
 * <li>推進: 総額が G0 ＋ 設定の推進の純減（thrust_cp_per_second、既定は毎秒 120 → 40。1.5.1 と同じ）になるよう、浮く分に差額
 * （{@link FlightCost#stormPropelExtra}）を足して払う。過負荷の既定は 0（1.5.1は 3 → 1）。推進の支払いは
 * 動いているモードの経路で、CPの回復待ちを作らない。過負荷が増えたときだけ、熟練度で短くなる過負荷の回復待ちが始まる。
 * 払えない（CP不足・CPセーフティ・オーバーロードのロック）ときは推進を止め、クライアントに滞空だけを許す（stalled）。</li>
 * </ul>
 * 推進しているかはクライアントの入力（{@link io.github.pinchan4273.reacademycraft.network.StormWingInput}）で、落下・ノックバック・他の能力による
 * 位置の変化からは推測しない。入力はモードのID（epoch）が今のモードと一致するときだけ受け取る。熟練度15%未満では、風が
 * 術者の周りの柔らかいブロック（硬さ0.3以下）も壊す: 10ブロック以内で1tickに40回のランダムな試行。熟練度がちょうど最大のとき、
 * 有効になると6ブロック以内のすべてのエンティティを吹き飛ばす（術者も真上へ）。モードの終了は理由を問わず、術者自身の飛行の許可を
 * 戻し、クールダウンlerp(30, 10, exp)を設定する。
 *
 * 原作はクライアントが溜めの完了を決めてサーバーへ伝えた。ここではサーバーが数えてクライアントへ伝える。
 *
 * <p>1.5.2: 飛行の許可（mayfly）は{@link JetEngine}と同じく、持っていないときだけ貸して「貸した」ことだけを覚え、
 * 終わるときはクリエイティブ・スペクテイターなら触らない。プレイヤーの作り直し（Clone）では止めるときと同じ終了（state -1）を本人へ送り、
 * 記録が無い・古いIDの入力には、そのIDの終了を返す（クライアントは今のIDと違う終了を捨てるので、新しいモードとは行き違わない）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class StormWing {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "storm_wing");
    public static final int CHARGE = 0, ACTIVE_STATE = 1;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static int epochs;

    private static final class Session {
        final Level level; final int preset, slot, epoch = ++epochs; final float exp; int state = CHARGE, ticks;
        /** クライアントが最後に伝えた推進の入力と、それを払えずに滞空だけを許しているか。 */
        boolean thrustInput, stalled;
        /** このモードが術者にmayflyを貸したか（そのとき術者は自分のものを持っていなかった）。 */
        boolean lent;
        /** 溜めの終わりからの、能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; exp = data.getProficiency(ID);
        }
    }

    private StormWing() { }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    /** 飛び続けてよいか。妨害（能力妨害器）が始まったtickにも終える（1.0.0は妨害で終わらなかった）。 */
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive() && !data.isInterfering()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    public static float chargeTicks(float exp) { return ArcGen.lerp(70, 30, exp); }
    /** 原作の速さ: 熟練度45%未満では遅い。 */
    public static double speed(float exp) { return (exp < .45f ? .7f : 1.2f) * ArcGen.lerp(2, 3, exp); }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(30, 10, exp); }
    /** 術者のクライアント向けの原作worldSpace: 頭のyawから、1.12の回転でrotatePitch(-pitch)、次にrotateYaw(-yaw)。 */
    public static net.minecraft.world.phys.Vec3 worldSpace(net.minecraft.world.phys.Vec3 local, float yawHeadDegrees, float pitchDegrees) {
        float pitch = -pitchDegrees * net.minecraft.util.Mth.DEG_TO_RAD, yaw = -yawHeadDegrees * net.minecraft.util.Mth.DEG_TO_RAD;
        float pc = net.minecraft.util.Mth.cos(pitch), ps = net.minecraft.util.Mth.sin(pitch);
        var v = new net.minecraft.world.phys.Vec3(local.x, local.y * pc + local.z * ps, local.z * pc - local.y * ps);
        float yc = net.minecraft.util.Mth.cos(yaw), ys = net.minecraft.util.Mth.sin(yaw);
        return new net.minecraft.world.phys.Vec3(v.x * yc + v.z * ys, v.y, v.z * yc - v.x * ys);
    }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }
    /** 溜めを終えて飛んでいるか（1.5.2: 形態の維持を払わない間。溜めの間は false）。 */
    public static boolean flying(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.state == ACTIVE_STATE; }
    public static int state(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.state; }

    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        if (!PassiveSkills.slotMatches(data, ID, slot)
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.VECMANIP.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // コンテキストを持つ全員への原作c_makealive: StormWingEffectの竜巻と塵。溜めの間にフェードインし（原作のchargeTime）、
        // このセッションが続く間表示する。
        io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, ID, (int) chargeTicks(session.exp), () -> ACTIVE.get(player.getUUID()) == session);
        // 原作s_makeAlive: モードが続く間allowFlying（持っていないときだけ貸す）。
        lendFlight(player, session);
        send(player, session, CHARGE);
        // 原作c_makealive: 風のループ音がモードの間ずっと術者に付いて行く。
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_STORM_WING.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, true, () -> active(player));
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        giveBackFlight(player, session);
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        // 習得していない技能（止める前にリスナーやカテゴリの変更が外した）にはクールダウンを書かない。
        if (data != null && !data.isReadOnly()) { if (data.hasLearned(ID)) data.setCooldown(ID, cooldown(session.exp)); AbilitySyncEvents.sync(player, true); }
        notifyOff(player, session.epoch);
    }
    private static void send(ServerPlayer player, Session session, int state) {
        SelfNotice.send(player, new StormWingState(state, session.epoch, session.stalled));
    }
    /** 本人のクライアントへの、そのIDのモードの終了（止めるとき・作り直しのとき・記録の無い入力への返事で同じ）。エンティティIDを含まない。 */
    private static void notifyOff(ServerPlayer player, int epoch) {
        SelfNotice.send(player, new StormWingState(-1, epoch, false));
    }
    /** 浮いているプレイヤーに対するサーバーの確認のために、術者が持っていないときだけmayflyを貸し、貸したことを覚える（{@link JetEngine}と同じ）。 */
    private static void lendFlight(ServerPlayer player, Session session) {
        var abilities = player.getAbilities();
        if (abilities.mayfly) return;
        abilities.mayfly = true; session.lent = true;
        player.onUpdateAbilities();
    }
    /** 貸したものだけを、ゲームモード自体が飛行を与えない（クリエイティブ・スペクテイターでない）ときだけ返してもらう。 */
    private static void giveBackFlight(ServerPlayer player, Session session) {
        if (!session.lent || player.isCreative() || player.isSpectator()) return;
        var abilities = player.getAbilities();
        abilities.mayfly = false; abilities.flying = false;
        player.onUpdateAbilities();
    }
    /**
     * 術者のクライアントからの推進の入力。今のモードのIDで、有効（溜め終わり）の間だけ受け取る。溜めの間の入力は捨てる。
     * モードが無いときの入力と古いモードのIDの入力には、そのIDの終了を返す（クライアントだけが飛んでいる食い違いを直す）。
     */
    public static void input(ServerPlayer player, int epoch, boolean thrust) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.epoch != epoch) { notifyOff(player, epoch); return; }
        if (session.state != ACTIVE_STATE) return;
        session.thrustInput = thrust;
    }
    /** テスト用: 今のモードのID（無ければ-1）。 */
    public static int epoch(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.epoch; }
    /** 推進を求めているのに払えず、滞空だけを許しているか。 */
    public static boolean stalled(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.stalled; }
    /**
     * 1.5.2: 今推進しているなら、その差額（滞空の維持に足して技能が払う分、技能の倍率を掛けた1秒分）。推進していなければ0。
     * 入れ直しの閾値の「動いている維持の1秒分」に足す（滞空の分は維持として精算に入っている）。
     */
    public static float thrustCostPerSecond(ServerPlayer player, PlayerAbilityData data) {
        var s = ACTIVE.get(player.getUUID());
        if (s == null || s.state != ACTIVE_STATE || !s.thrustInput || s.stalled || player.getAbilities().instabuild) return 0;
        float p = data.getProficiency(ID);
        return (float) (FlightCost.stormPropelExtra(p, data.g0PerTick(), AcademyConfig.stormWingThrustCp(p)) * 20 * AcademyConfig.skillCpSpeed(ID));
    }

    /** このtickもモードを続けてよいか（tickの始めと、熟練度のイベントの後に確かめる）。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        // 1.5.2: パッシブで飛んでいるものは、プリセットとスロットを見ず、パッシブのままかを見る。
        return data != null && allowed(player, data) && !(session.level != player.level()
                || !PassiveSkills.sessionMatches(data, ID, session.preset, session.slot));
    }
    private static void tick(ServerPlayer player, DoubleSupplier roll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        // モードの途中でmayflyを失ったら（サバイバルへの変更など）貸し直す。
        lendFlight(player, session);
        player.fallDistance = 0;
        if (session.exp < .15f) breakAround(player, roll);
        if (session.state == ACTIVE_STATE) {
            // 滞空のCPは、このtickの始めに精算した維持コスト。払えなかった要求は外れている。
            if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
            // 滞空は待機、推進を払ったtickは行動として、維持の熟練度を得る（1.1.0）。1.0.0の毎tick 0.00005は廃止した。
            data.upkeepEarn(session.upkeep);
            // 熟練度のイベントのリスナーが、モードを終えた・置き換えた・続けられなくした（能力、スロット、プリセット）かもしれない:
            // このtickを古いセッションで続けない。
            if (ACTIVE.get(player.getUUID()) != session) return;
            if (!continues(player, data, session) || !data.upkeepHeld(session.upkeep)) { stop(player); return; }
            boolean stalled = false;
            if (session.thrustInput) {
                // 推進: 浮く分（維持費）に足す費用。動いているモードの支払いなのでCPの回復待ちは作らない。ロック中は払わない。
                // 払えたtickは、次の精算で待機として扱わない（維持の熟練度が行動になる）。
                float proficiency = data.getProficiency(ID);
                // 1.5.2: 推進の総額 G0 ＋ 純減になるよう、滞空の維持との差額を払う。
                boolean paid = !data.lockBlocks(ID) && data.consumeRunning(ID,
                        FlightCost.stormPropelExtra(proficiency, data.g0PerTick(), AcademyConfig.stormWingThrustCp(proficiency)),
                        AcademyConfig.stormWingThrustOverload(proficiency), player.getAbilities().instabuild);
                // オーバーロードはconsumeが戻る前に全セッションを止めることがある。
                if (ACTIVE.get(player.getUUID()) != session) return;
                if (paid) data.upkeepActed(session.upkeep); else stalled = true;
            }
            if (stalled != session.stalled) { session.stalled = stalled; send(player, session, ACTIVE_STATE); }
            AbilitySyncEvents.sync(player, false);
        }
        if (session.state == CHARGE && ++session.ticks > chargeTicks(session.exp)) {
            session.state = ACTIVE_STATE;
            session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
            // 1.5.2: 浮く分は G0 × lerp(1.0, 50%)（推進していてもいなくても同じ）。推進の差額は上で技能が払う。
            if (session.upkeep != null) session.upkeep.custom(g0 -> FlightCost.hoverDue(data.getProficiency(ID), (float) g0));
            if (session.exp == 1) fling(player, roll);
            send(player, session, ACTIVE_STATE);
        }
    }
    /** テスト用の入口: 与えた乱数源から原作の乱数を引いて、モードのサーバー1tick分を進める。 */
    public static void tickForTest(ServerPlayer player, DoubleSupplier roll) { tick(player, roll); }

    /** 熟練度15%未満での原作s_tick: 10ブロック以内で40回のランダムな試行（(int)で切り捨て）を、硬さ0.3以下のブロックに対して行う。 */
    private static void breakAround(ServerPlayer player, DoubleSupplier roll) {
        var level = (ServerLevel) player.level();
        if (!AcademyConfig.canDestroy(level, ID)) return;
        for (int i = 0; i < 40; i++) {
            // 原作.toInt: xとzには原作の(int)キャストを保つ（1.12でも既に負になりえた）。yは切り捨てで、原作が見たすべてのy（0未満は無い）で
            // 同じ結果になり、1.20のy < 0でも術者の下のブロックを保つ。
            var pos = new BlockPos((int) (player.getX() + (roll.getAsDouble() * 20 - 10)),
                    net.minecraft.util.Mth.floor(player.getY() + (roll.getAsDouble() * 20 - 10)), (int) (player.getZ() + (roll.getAsDouble() * 20 - 10)));
            var state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || hardness < 0 || hardness > .3f || !AcademyConfig.canDestroy(player, pos, ID)) continue;
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) continue;
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
    /** 熟練度最大での原作syncState: 術者も含めて6ブロック以内のすべてを、術者の足からそれ自身の頭へ向けてrangef(0.5, 1.0)で飛ばす。 */
    private static void fling(ServerPlayer player, DoubleSupplier roll) {
        var origin = player.position();
        var targets = new java.util.ArrayList<Entity>(player.level().getEntities(player, new AABB(origin, origin).inflate(6),
                e -> e.position().distanceToSqr(origin) <= 36));
        targets.add(player);
        for (var entity : targets) {
            double modifier = .9 + roll.getAsDouble() * .3;
            var delta = entity.getEyePosition().subtract(origin).scale(modifier);
            entity.setDeltaMovement(delta.normalize().scale(.5 + roll.getAsDouble() * .5));
            entity.hurtMarked = true;
        }
    }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            var random = p.getRandom();
            tick(p, random::nextDouble);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /**
     * 死亡またはエンドからの帰還（作り直し）: 新しいプレイヤーはモードも、それが貸した飛行の許可も持たない。止めるときと同じ終了を本人へ送る
     * （すぐの作り直しでは古いプレイヤーの処理が動かず、クライアントだけが浮いて飛べたため）。エンティティIDを含む通知は送らない。
     */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        var session = ACTIVE.remove(e.getEntity().getUUID());
        if (session != null && e.getEntity() instanceof ServerPlayer p) {
            giveBackFlight(p, session);
            notifyOff(p, session.epoch);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
