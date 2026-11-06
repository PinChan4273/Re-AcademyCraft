package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import io.github.pinchan4273.reacademycraft.network.WhiteWingFlightState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * v1.5.0 未元物質Lv1 白翼飛行（採用仕様・版41の§5、R06）。術者自身のクライアントが動かし（{@code client/ClientWhiteWingFlight}）、
 * 費用・許可・状態はサーバーが決める。
 * <p>
 * 2026-11-03のユーザーの決定で、トグルから受動に変えた（キーに割り当てない）。能力使用モードで翼が出ている間、ジャンプを
 * 長押しすると飛び立つ（タップは普通のジャンプ）。クライアントが長押しを見て{@link #takeoff}を頼み、地面に着くと終わる。
 * 同日の決定で、能力設定の「飛び立ちと停止の操作」を連打にすると、ジャンプの2回連打で飛び立つ。空中では、長押しの設定ならスニークの
 * 2回連打、連打の設定ならジャンプの2回連打で、クライアントが停止（{@link #STOP}）を頼む。着地はクライアントも見て停止を頼む
 * （地面すれすれの移動では、クライアントの接地が1tickしか続かず、サーバーが見落とすことがあったため）。
 * <p>
 * 開始20CP＋過負荷10、維持の過負荷なし。移動中のCP純減は通常lerp(40,20)、高速lerp(80,40)/秒で、自然回復分は翼の維持に充てる
 * （回復待ち中も純減は同じ）。静止滞空の費用は自然回復量R×(1−0.5p)で、回復できるときの差引は0.5pR、回復待ち中は双方0。
 * 飛行の維持は回復待ちを作らない。自力で移動している間だけ1秒＋0.1%（通常／高速共通）。
 * <p>
 * 着地、能力OFF（翼の収納）・干渉・能力使用不可・過負荷で終了する（着地以外は落下する）。プリセットの変更では続く（§13）。CPセーフティだけは飛行を保ち、
 * 通常滞空へ移して移動・推進を止める（再度の移動には新しい入力が要る。§14）。高速で壁に当たるとエリトラを基準にした衝突ダメージを
 * 計算し、通常滞空へ移る（自動で高速へ戻らない）。飛行中は落下距離を貯めない。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class WhiteWingFlight {
    public static final Identifier ID = DarkMatter.FLIGHT;
    public static final float START_CP = 20, START_OVERLOAD = 10;
    /** 移動1tickの熟練度（1秒＋0.1%）。 */
    public static final float GAIN_PER_TICK = .001f / 20;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static int epochs;

    private static final class Session {
        final int epoch = ++epochs; final boolean mayfly; final net.minecraft.world.level.Level level;
        boolean moving, fast, safetyHover; PlayerAbilityData.Upkeep upkeep;
        double lastSpeed; Vec3 lastPos;
        /** 足の下に地面があり上昇していない状態が続いたtick数（サーバーの念のための着地の判定）。 */
        int groundTicks;
        int age;
        Session(ServerPlayer player) { mayfly = player.getAbilities().mayfly; level = player.level(); lastPos = player.position(); }
    }

    private WhiteWingFlight() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static double horizontalSpeed(float p) { return ArcGen.lerp(6, 12, p); }
    public static double verticalSpeed(float p) { return ArcGen.lerp(4, 8, p); }
    /** 高速の巡航（ブロック/秒）。2026-11-03のユーザーの決定で12〜24から16〜32にした（熟練度最大でエリトラ＋花火の水平飛行とほぼ同じ）。 */
    public static double cruiseSpeed(float p) { return ArcGen.lerp(16, 32, p); }
    public static double diveSpeed(float p) { return cruiseSpeed(p) * 1.5; }
    /** 移動中のCP純減（毎秒）。 */
    public static float netLoss(float p, boolean fast) { return fast ? ArcGen.lerp(80, 40, p) : ArcGen.lerp(40, 20, p); }
    /** 滞空の費用の、自然回復量に対する比率（100%→75%→50%）。 */
    public static float hoverShare(float p) { return 1 - .5f * p; }
    /** 速度ベクトルの追従の上限（度/秒。R06）: 巡航で120、急降下の最高速で80、その間は速度で線形補間。全熟練度で共通。 */
    public static double turnLimit(double speed, double cruise) {
        double k = Math.max(0, Math.min(1, (speed - cruise) / (cruise * .5)));
        return 120 + (80 - 120) * k;
    }

    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static boolean fast(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.fast; }
    public static boolean moving(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.moving && !s.safetyHover; }
    public static boolean safetyHover(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.safetyHover; }
    public static int epoch(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.epoch; }
    /** 術者の入力の、飛び立ちの依頼を表すモードの番号（本物のモードの番号は1から増える）。 */
    public static final int TAKEOFF = -1;
    /** 停止の依頼のモード番号（空中でやめる操作と、クライアントが見た着地）。自分の飛行を終えるだけなので、条件は要らない。 */
    public static final int STOP = -2;
    /** 飛び立ってから、着地で終える判定を始めるまでのtick（起動直後に地面から離れる間）。 */
    public static final int LAND_GRACE_TICKS = 10;
    /** 着地とみなす、足の真下の隙間（ブロック）。 */
    public static final double LAND_GAP = .01;
    /** サーバーの念のための着地: 地面の上がこれだけ続いたら止める（普段はクライアントの判定が先に止める）。 */
    public static final int LAND_SERVER_TICKS = 20;
    /**
     * 着地したか（サーバーとクライアントで同じ判定。2026-11-03のユーザーの指摘: 壁に触れたり、地面すれすれを飛んだりすると、
     * 触れていないのに終わった）。接地の値（onGround）は使わない。足の真下{@link #LAND_GAP}以内に当たる物があり、上昇していないときだけ。
     * 横の壁は足の下に入らないので数えない。riseはこのtickの上への移動（ブロック）。
     */
    public static boolean landed(net.minecraft.world.entity.Entity entity, double rise) {
        // 横はわずかに縮め、触れているだけの横の壁を確実に外す。
        return rise <= 1e-3 && !entity.level().noCollision(entity, entity.getBoundingBox().deflate(1e-3, 0, 1e-3).move(0, -LAND_GAP, 0));
    }

    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.canUseAbility()
                && DarkMatter.CATEGORY.equals(data.getAbility()) && data.hasLearned(ID) && !player.isSpectator();
    }

    /**
     * 設定に応じたジャンプ操作（既定は長押し、2回押しも選べる）で離陸する（術者のクライアントの依頼）。断る理由はテストのために返すが、ほかの技能と同じく画面には出さない。
     * 飛行中なら何もしない（終えるのは着地など）。翼が出ている（能力使用モード）ことが条件。
     */
    public static String takeoff(ServerPlayer player, PlayerAbilityData data) {
        if (ACTIVE.containsKey(player.getUUID())) return "";
        if (!player.isAlive() || data.isReadOnly() || player.isSpectator() || player.isPassenger()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        boolean creative = player.getAbilities().instabuild;
        if (!data.consume(ID, START_CP, START_OVERLOAD, creative)) return "academy.cast.cp";
        var session = new Session(player);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        if (session.upkeep == null) { ACTIVE.remove(player.getUUID()); return "academy.cast.unlearned"; }
        session.upkeep.selfSafety().custom(granted -> {
            float p = data.getProficiency(ID);
            // 移動: 回復分を維持に充て、さらに純減を払う。滞空: 回復量の比率だけを払う（回復待ち中はgrantedが0なので双方0）。
            if (session.moving && !session.safetyHover) return granted + netLoss(p, session.fast) / 20.0;
            return granted * hoverShare(p);
        });
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        send(player, session, true);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        player.getAbilities().mayfly = session.mayfly;
        if (!session.mayfly) player.getAbilities().flying = false;
        player.onUpdateAbilities();
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        // 落下の距離は終了の後から数える。
        player.fallDistance = 0;
        send(player, session, false);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
    }
    /** 見ている人にも伝える飛行の状態。 */
    public static int mode(ServerPlayer player) {
        var s = ACTIVE.get(player.getUUID());
        return s == null ? DarkWingAction.FLIGHT_OFF : s.fast ? DarkWingAction.FLIGHT_FAST : DarkWingAction.FLIGHT_NORMAL;
    }
    private static void send(ServerPlayer player, Session session, boolean on) {
        DarkWingAction.send(player, DarkWingAction.FLIGHT, !on ? DarkWingAction.FLIGHT_OFF : session.fast ? DarkWingAction.FLIGHT_FAST : DarkWingAction.FLIGHT_NORMAL, 0);
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new WhiteWingFlightState(on, session.epoch, session.fast, session.safetyHover));
    }

    /** 術者のクライアントの入力。古いモードの番号は捨てる。セーフティ滞空からは、一度入力を離して回復していないと移動へ戻らない。 */
    public static void input(ServerPlayer player, int epoch, boolean moving, boolean fastPressed) {
        if (epoch == STOP) { if (ACTIVE.containsKey(player.getUUID())) lastStop = "client request"; stop(player); return; }
        if (epoch == TAKEOFF) {
            var data = data(player);
            if (data != null) takeoff(player, data);
            return;
        }
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.epoch != epoch) return;
        var data = data(player);
        if (data == null) return;
        session.moving = moving;
        if (session.safetyHover) {
            if (!moving && data.safetyWouldPermit(ID, netLoss(data.getProficiency(ID), false) / 20f)) { session.safetyHover = false; send(player, session, true); }
            return;
        }
        if (fastPressed) { session.fast = !session.fast; send(player, session, true); }
    }

    /** 最後に飛行が終わった理由（調査と試験のため。2026-11-04、被弾や着地の前に終わるというユーザーの指摘）。 */
    private static String lastStop = "";
    public static String lastStopReason() { return lastStop; }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.age++;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || !data.upkeepHeld(session.upkeep)) {
            lastStop = !allowed(player, data) ? "not allowed (active=" + (data != null && data.isActive()) + " overload=" + (data != null && data.isOverloadLocked())
                    + " interfering=" + (data != null && data.isInterfering()) + ")" : session.level != player.level() ? "level" : "upkeep (cp=" + data.getCp() + ")";
            stop(player); return;
        }
        player.fallDistance = 0;
        // 地面に着いたら終える（受動になってからの終わり方。起動直後に浮く間は見ない）。普段はクライアントが、下向きに動いて地面にぶつかった
        // ときに停止を頼む（バニラのクリエイティブ飛行が接地で止まるのと同じ考え。2026-11-04のユーザーの提案）。サーバーは念のため、
        // 足の下に地面があり上昇していない状態（{@link #landed}）が{@link #LAND_SERVER_TICKS}続いたときだけ止める（地面すれすれの移動では止めない）。
        if (session.age > LAND_GRACE_TICKS && landed(player, player.getY() - session.lastPos.y)) {
            if (++session.groundTicks >= LAND_SERVER_TICKS) { lastStop = "landed (server)"; stop(player); return; }
        } else session.groundTicks = 0;
        float p = data.getProficiency(ID);
        var delta = player.position().subtract(session.lastPos);
        session.lastPos = player.position();
        // サーバーの速さを実際の動き（この1tickの位置の変化）にそろえる（2026-11-04のユーザーの指摘: 被弾で落ちる）。飛行の動きはクライアントが
        // 決めるので、そのままだとサーバーは重力の分をため続け、被弾のノックバック（空中では縦の速さを保つ）がその大きな下向きの速さを
        // クライアントへ送り、急降下して地面に着き、飛行が終わっていた。ジェットエンジンと同じく、被弾しても横に押されるだけで飛び続ける。
        player.setDeltaMovement(delta);
        double speed = delta.horizontalDistance();
        if (session.fast && player.horizontalCollision) {
            // エリトラと同じ式（失った速さ×10−3）。軽減は通常のダメージ処理と防御の技能が扱う。
            double lost = session.lastSpeed - speed;
            float damage = (float) (lost * 10 - 3);
            if (damage > 0) player.hurtServer(player.level(), player.damageSources().flyIntoWall(), damage);
            session.fast = false;
            send(player, session, true);
            if (ACTIVE.get(player.getUUID()) != session) return;
        }
        session.lastSpeed = speed;
        if (session.moving && !session.safetyHover) {
            boolean creative = player.getAbilities().instabuild;
            if (!creative && !data.safetyPermits(ID, netLoss(p, session.fast) / 20f, false)) {
                // CPセーフティ: 飛行は保ち、減速を待たず通常滞空へ（§5・§14）。
                session.safetyHover = true; session.fast = false;
                send(player, session, true);
            } else if (delta.lengthSqr() > 1e-6) {
                data.addProficiency(ID, GAIN_PER_TICK);
            }
        }
        AbilitySyncEvents.sync(player, false);
    }
    /** テスト用の入口。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (ACTIVE.remove(e.getEntity().getUUID()) != null && e.getEntity() instanceof ServerPlayer p) io.github.pinchan4273.reacademycraft.network.SkillModeState.send(p, ID, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
