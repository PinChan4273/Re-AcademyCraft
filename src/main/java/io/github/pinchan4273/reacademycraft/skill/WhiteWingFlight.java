package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import io.github.pinchan4273.reacademycraft.network.SelfNotice;
import io.github.pinchan4273.reacademycraft.network.WhiteWingFlightState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質Lv1 白翼飛行。術者自身のクライアントが動かし（{@code client/ClientWhiteWingFlight}）、
 * 費用・許可・状態はサーバーが決める。
 * <p>
 * パッシブの技能で、キーに割り当てない。能力使用モードで翼が出ている間、ジャンプを
 * 長押しすると飛び立つ（タップは普通のジャンプ）。クライアントが長押しを見て{@link #takeoff}を頼み、地面に着くと終わる。
 * 能力設定の「飛び立ちと停止の操作」を連打にすると、ジャンプの2回連打で飛び立つ。空中では、長押しの設定ならスニークの
 * 2回連打、連打の設定ならジャンプの2回連打で、クライアントが停止（{@link #STOP}）を頼む。着地はクライアントも見て停止を頼む
 * （地面すれすれの移動では、クライアントの接地が1tickしか続かず、サーバーが見落とすことがあったため）。
 * <p>
 * 1.5.2から、費用は飛行の部類の基準（{@link FlightCost}）そのもの: 離陸ごとに CP lerp(40, 20) と過負荷 lerp(10, 0)、
 * 状態ごとの総額を毎tick払う。総額は 1.5.1 と同じ: 滞空 G0 × lerp(1.0, 50%)、移動中は G0 の全額 ＋ 毎秒の純減
 * （通常 lerp(40, 20)、高速 lerp(80, 40)）。G0 は待ちの無いときの回復で、回復待ちの間も同じ額（1.5.1は離陸20CP＋過負荷10）。
 * 1.5.2: 翼の防御が成立すると、高速は通常の移動へ戻る（落ちない）。最後の防御から{@link #GUARD_LOCK_TICKS}tick（羽の防御の動きの長さ）が
 * 過ぎるまで、高速へは戻せない。戻すのは本人の操作だけ。
 * 維持の過負荷は無く、飛行の維持は回復待ちを作らない。自力で移動している間だけ1秒＋0.1%（通常／高速共通）。
 * <p>
 * 着地、能力OFF（翼の収納）・干渉・能力使用不可・過負荷で終了する（着地以外は落下する）。プリセットの変更では続く。CPが足りない・CPセーフティに
 * 掛かるときは、精算が段を下げる（1.5.2: 高速 → 通常 → 滞空 → 止めて落ちる。滞空へ移ったら再度の移動には新しい入力が要る）。高速で壁に当たるとエリトラを基準にした衝突ダメージを
 * 計算し、通常滞空へ移る（自動で高速へ戻らない）。飛行中は落下距離を貯めない。
 * <p>
 * 1.5.2: 飛行の許可（mayfly）は{@link JetEngine}と同じく、持っていないときだけ貸して「貸した」ことだけを覚え、
 * 終わるときはクリエイティブ・スペクテイターなら触らない（飛んでいる途中のゲームモードの変更で、サバイバルで飛べたり、クリエイティブで
 * 飛べなくなったりしない）。プレイヤーの作り直し（Clone）では、止めるときと同じ本人への終了の通知を送り、記録が無いのに停止の依頼や
 * 入力が届いたら終了を返す（どの経路で食い違っても、自然に元へ戻る）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class WhiteWingFlight {
    public static final ResourceLocation ID = DarkMatter.FLIGHT;
    /** 移動1tickの熟練度（1秒＋0.1%）。 */
    public static final float GAIN_PER_TICK = .001f / 20;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static int epochs;

    private static final class Session {
        final int epoch = ++epochs; final net.minecraft.world.level.Level level;
        boolean moving, fast, safetyHover; PlayerAbilityData.Upkeep upkeep;
        /** この飛行が術者にmayflyを貸したか（そのとき術者は自分のものを持っていなかった）。 */
        boolean lent;
        double lastSpeed; Vec3 lastPos;
        /** 1.5.2: 最後に翼の防御が成立したゲームの時刻（無ければ十分に昔）。ここから{@link #GUARD_LOCK_TICKS}tickは高速へ戻せない。 */
        long guardedAt = Long.MIN_VALUE / 2;
        /** 足の下に地面があり上昇していない状態が続いたtick数（サーバーの念のための着地の判定）。 */
        int groundTicks;
        int age;
        Session(ServerPlayer player) { level = player.level(); lastPos = player.position(); }
    }

    private WhiteWingFlight() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static double horizontalSpeed(float p) { return ArcGen.lerp(6, 12, p); }
    public static double verticalSpeed(float p) { return ArcGen.lerp(4, 8, p); }
    /** 高速の巡航（ブロック/秒）。16〜32（熟練度最大でエリトラ＋花火の水平飛行とほぼ同じ）。 */
    public static double cruiseSpeed(float p) { return ArcGen.lerp(16, 32, p); }
    public static double diveSpeed(float p) { return cruiseSpeed(p) * 1.5; }
    /** 1.5.2: 羽の防御の動きの長さ（tick。クライアントの描画 {@code ClientDarkWings} と同じ18で、防ぐたびに最初から）。 */
    public static final int GUARD_LOCK_TICKS = 18;
    /** 移動中に、滞空の総額へ足すCP（毎秒、技能の倍率の前）。通常は moveDue − hoverDue、高速は fastDue − hoverDue（{@link FlightCost}）。 */
    public static float netLoss(float p, boolean fast, float g0) {
        return ((fast ? FlightCost.fastDue(p, g0) : FlightCost.moveDue(p, g0)) - FlightCost.hoverDue(p, g0)) * 20;
    }
    /** 浮く分の、G0 に対する比率（100% → 75% → 50%。{@link FlightCost#hoverShare}）。 */
    public static float hoverShare(float p) { return FlightCost.hoverShare(p); }
    /** 状態ごとの1tickの総額（技能の倍率の前）。 */
    public static float due(float p, float g0, boolean moving, boolean fast) {
        return !moving ? FlightCost.hoverDue(p, g0) : fast ? FlightCost.fastDue(p, g0) : FlightCost.moveDue(p, g0);
    }
    /** 離陸1回のCP（1.5.2: lerp(40, 20)）。 */
    public static float takeoffCp(float p) { return FlightCost.takeoffCp(p); }
    /** 離陸1回の過負荷（1.5.2: lerp(10, 0)）。 */
    public static float takeoffOverload(float p) { return FlightCost.takeoffOverload(p); }
    /** 速度ベクトルの追従の上限（度/秒）: 巡航で120、急降下の最高速で80、その間は速度で線形補間。全熟練度で共通。 */
    public static double turnLimit(double speed, double cruise) {
        double k = Math.max(0, Math.min(1, (speed - cruise) / (cruise * .5)));
        return 120 + (80 - 120) * k;
    }

    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static boolean fast(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.fast; }
    public static boolean moving(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.moving && !s.safetyHover; }
    public static boolean safetyHover(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.safetyHover; }
    public static int epoch(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.epoch; }
    /** 術者の入力の、飛び立ちの依頼を表すモードのID（本物のモードのIDは1から増える）。 */
    public static final int TAKEOFF = -1;
    /** 停止の依頼のモードID（空中でやめる操作と、クライアントが見た着地）。自分の飛行を終えるだけなので、条件は要らない。 */
    public static final int STOP = -2;
    /** 飛び立ってから、着地で終える判定を始めるまでのtick（起動直後に地面から離れる間）。 */
    public static final int LAND_GRACE_TICKS = 10;
    /** 着地とみなす、足の真下の隙間（ブロック）。 */
    public static final double LAND_GAP = .01;
    /** サーバーの念のための着地: 地面の上がこれだけ続いたら止める（普段はクライアントの判定が先に止める）。 */
    public static final int LAND_SERVER_TICKS = 20;
    /**
     * 着地したか（サーバーとクライアントで同じ判定。壁に触れたり、地面すれすれを飛んだりすると、
     * 触れていないのに終わった）。接地の値（onGround）は使わない。足の真下{@link #LAND_GAP}以内に当たる物があり、上昇していないときだけ。
     * 横の壁は足の下に入らないので数えない。riseはこのtickの上への移動（ブロック）。
     */
    public static boolean landed(net.minecraft.world.entity.Entity entity, double rise) {
        // 横はわずかに縮め、触れているだけの横の壁を確実に外す。
        return rise <= 1e-3 && !entity.level().noCollision(entity, entity.getBoundingBox().deflate(1e-3, 0, 1e-3).move(0, -LAND_GAP, 0));
    }

    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.canUseAbility(ID)
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
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        boolean creative = player.getAbilities().instabuild;
        float start = data.getProficiency(ID);
        // 離陸は技能の発動の支払い（ほかの技能と同じくCPの回復待ちが始まる）。過負荷は熟練度100%で0。
        if (!data.consume(ID, takeoffCp(start), takeoffOverload(start), creative)) return "academy.cast.cp";
        var session = new Session(player);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        if (session.upkeep == null) { ACTIVE.remove(player.getUUID()); return "academy.cast.unlearned"; }
        // 1.5.2: 払えない・セーフティに掛かるときは、精算が段を下げる（高速 → 通常 → 滞空 → 止めて落ちる）。
        session.upkeep.stepDown(() -> stepDown(player, session)).custom(g0 -> {
            float p = data.getProficiency(ID);
            // 1.5.2: 状態ごとの総額（滞空・通常の移動・高速）。G0 から決めるので、回復待ちの間も同じ額。
            boolean moving = session.moving && !session.safetyHover;
            return due(p, (float) g0, moving, session.fast);
        });
        lendFlight(player, session);
        send(player, session, true);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        giveBackFlight(player, session);
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        // 落下の距離は終了の後から数える。
        player.fallDistance = 0;
        DarkWingAction.send(player, DarkWingAction.FLIGHT, DarkWingAction.FLIGHT_OFF, 0);
        notifyOff(player, session.epoch);
    }
    /**
     * 本人のクライアントへの終了の通知（止めるとき・作り直しのとき・記録が無いのに依頼が来たときで同じ）。エンティティIDを含まないので、
     * 作り直しの最中の新しいプレイヤーへも送れる。見ている人への翼の状態（{@link DarkWingAction}、IDを含む）はここに入れない。
     */
    private static void notifyOff(ServerPlayer player, int epoch) {
        SelfNotice.send(player, new WhiteWingFlightState(false, epoch, false, false));
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
    }
    /**
     * 浮いているプレイヤーに対するサーバーの確認のために、術者が持っていないときだけmayflyを貸し、貸したことを覚える（{@link JetEngine}と同じ。
     * 飛んでいる途中で失ったとき、つまりサバイバルへの変更などでも、次のtickで貸し直す）。
     */
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
    /** 見ている人にも伝える飛行の状態。 */
    public static int mode(ServerPlayer player) {
        var s = ACTIVE.get(player.getUUID());
        return s == null ? DarkWingAction.FLIGHT_OFF : s.fast ? DarkWingAction.FLIGHT_FAST : DarkWingAction.FLIGHT_NORMAL;
    }
    private static void send(ServerPlayer player, Session session, boolean on) {
        DarkWingAction.send(player, DarkWingAction.FLIGHT, !on ? DarkWingAction.FLIGHT_OFF : session.fast ? DarkWingAction.FLIGHT_FAST : DarkWingAction.FLIGHT_NORMAL, 0);
        SelfNotice.send(player, new WhiteWingFlightState(on, session.epoch, session.fast, session.safetyHover));
    }

    /** 術者のクライアントの入力。古いモードのIDは捨てる。セーフティ滞空からは、一度入力を離して回復していないと移動へ戻らない。 */
    public static void input(ServerPlayer player, int epoch, boolean moving, boolean fastPressed) {
        if (epoch == STOP) {
            // 記録が無いのに停止の依頼が来たら、クライアントが飛んでいるつもりのまま食い違っているので、終了を返す。
            if (ACTIVE.containsKey(player.getUUID())) { lastStop = "client request"; stop(player); } else notifyOff(player, 0);
            return;
        }
        if (epoch == TAKEOFF) {
            var data = data(player);
            if (data != null) takeoff(player, data);
            return;
        }
        var session = ACTIVE.get(player.getUUID());
        // 記録が無いのに飛行中の入力が来たときも終了を返す。別のモードのIDの入力（新しい飛行の始まりと行き違った古い入力）は捨てるだけにする。
        if (session == null) { notifyOff(player, epoch); return; }
        if (session.epoch != epoch) return;
        var data = data(player);
        if (data == null) return;
        session.moving = moving;
        if (session.safetyHover) {
            if (!moving && data.safetyWouldPermit(ID, netLoss(data.getProficiency(ID), false, data.g0PerTick()) / 20f)) { session.safetyHover = false; send(player, session, true); }
            return;
        }
        if (fastPressed) {
            // 1.5.2: 翼の防御の動きの間（最後の防御から GUARD_LOCK_TICKS）は、高速へは戻せない（通常へ戻すのはいつでもよい）。断ったら今の状態を送り直す。
            if (!session.fast && guardLocked(player, session)) { send(player, session, true); return; }
            session.fast = !session.fast; send(player, session, true);
        }
    }

    /** 最後に飛行が終わった理由（調査とテストのため）。 */
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
        // 飛んでいる途中でmayflyを失ったら（サバイバルへの変更など）貸し直す。
        lendFlight(player, session);
        player.fallDistance = 0;
        // 地面に着いたら終える（パッシブになってからの終わり方。起動直後に浮く間は見ない）。普段はクライアントが、下向きに動いて地面にぶつかった
        // ときに停止を頼む（バニラのクリエイティブ飛行が接地で止まるのと同じ考え）。サーバーは念のため、
        // 足の下に地面があり上昇していない状態（{@link #landed}）が{@link #LAND_SERVER_TICKS}続いたときだけ止める（地面すれすれの移動では止めない）。
        if (session.age > LAND_GRACE_TICKS && landed(player, player.getY() - session.lastPos.y)) {
            if (++session.groundTicks >= LAND_SERVER_TICKS) { lastStop = "landed (server)"; stop(player); return; }
        } else session.groundTicks = 0;
        float p = data.getProficiency(ID);
        var delta = player.position().subtract(session.lastPos);
        session.lastPos = player.position();
        // サーバーの速さを実際の動き（この1tickの位置の変化）にそろえる。飛行の動きはクライアントが
        // 決めるので、そのままだとサーバーは重力の分をため続け、被弾のノックバック（空中では縦の速さを保つ）がその大きな下向きの速さを
        // クライアントへ送り、急降下して地面に着き、飛行が終わっていた。ジェットエンジンと同じく、被弾しても横に押されるだけで飛び続ける。
        player.setDeltaMovement(delta);
        double speed = delta.horizontalDistance();
        if (session.fast && player.horizontalCollision) {
            // エリトラと同じ式（失った速さ×10−3）。軽減は通常のダメージ処理と防御の技能が扱う。
            double lost = session.lastSpeed - speed;
            float damage = (float) (lost * 10 - 3);
            if (damage > 0) player.hurt(player.damageSources().flyIntoWall(), damage);
            session.fast = false;
            send(player, session, true);
            if (ACTIVE.get(player.getUUID()) != session) return;
        }
        session.lastSpeed = speed;
        // 1.5.2: CPセーフティの段の下げ（高速 → 通常 → 滞空 → 止める）は、tick の始めの精算（{@link #stepDown}）だけが行う。
        // ここで2度目の判定をすると、通常の段で足りるのに滞空まで飛ばすことがあった。
        if (session.moving && !session.safetyHover && delta.lengthSqr() > 1e-6) data.addProficiency(ID, GAIN_PER_TICK);
        AbilitySyncEvents.sync(player, false);
    }
    /** テスト用の入口。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /** 1.5.2: 精算が呼ぶ段の下げ。高速なら通常の移動へ、移動中なら滞空へ。滞空なら下げられない（false）。 */
    private static boolean stepDown(ServerPlayer player, Session session) {
        if (ACTIVE.get(player.getUUID()) != session) return false;
        if (session.fast) { session.fast = false; send(player, session, true); return true; }
        if (session.moving && !session.safetyHover) { session.safetyHover = true; send(player, session, true); return true; }
        return false;
    }
    private static boolean guardLocked(ServerPlayer player, Session session) {
        return player.level().getGameTime() - session.guardedAt < GUARD_LOCK_TICKS;
    }
    /** 1.5.2: 最後の翼の防御から{@link #GUARD_LOCK_TICKS}tickの間か（飛んでいなければ false）。 */
    public static boolean guardLocked(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && guardLocked(player, s); }
    /**
     * 1.5.2: 翼の防御が成立した（{@link DarkDefense}が呼ぶ）。高速なら通常の移動へ戻し（落ちない）、ここから
     * {@link #GUARD_LOCK_TICKS}tickは高速へ戻せないようにする。飛んでいなければ何もしない。
     */
    public static void guarded(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.guardedAt = player.level().getGameTime();
        if (session.fast) { session.fast = false; send(player, session, true); }
    }

    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /**
     * 死亡またはエンドからの帰還（作り直し）: 新しいプレイヤーは飛行も、それが貸した飛行の許可も持たない。止めるときと同じ本人への終了の通知を送る
     * （すぐの作り直しでは古いプレイヤーの処理が動かず、クライアントが飛行中のまま残ったため）。新しいプレイヤーのエンティティIDは
     * この時点ではまだ仮なので、IDを含む通知は送らない。
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
