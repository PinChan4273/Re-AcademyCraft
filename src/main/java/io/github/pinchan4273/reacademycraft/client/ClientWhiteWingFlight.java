package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.WhiteWingFlightInput;
import io.github.pinchan4273.reacademycraft.network.WhiteWingFlightState;
import io.github.pinchan4273.reacademycraft.skill.WhiteWingFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 白翼飛行の術者側（§5）。通常は直立のクリエイティブ飛行型（前後左右、ジャンプで上昇、スニークで下降、無入力で減速して滞空。斜めの速さは
 * 正規化する）。高速はエリトラ型で視線で操縦する: 翼が推し続け、約1秒で巡航へ加速し、下を向くと巡航の1.5倍まで増え、上を向くと遅くなるが失速しない。
 * 水平に戻しても速さはすぐに失わず、巡航へ徐々に戻る。速度の向きはR06の上限で視線を追う。通常への復帰は約0.5秒で減速する。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientWhiteWingFlight {
    private static boolean on, fast, safetyHover, sentMoving, wasSprint;
    private static int epoch;
    /** 起動直後に少し浮く間のtick数と、その間の上昇の速さ（ブロック/tick。10tickで約0.6ブロック）。 */
    private static final int LIFT_TICKS = 10;
    private static final double LIFT_SPEED = .06;
    private static int lift;
    /** 長押し設定で離陸に要るジャンプの長押し（tick。0.3秒）。タップは普通のジャンプのまま。 */
    public static final int HOLD_TICKS = 6;
    /** ジャンプを押し続けているtick数と、この押下で飛び立ちを頼んだか（離すまで1回だけ頼む）。 */
    private static int jumpHeld;
    private static boolean takeoffSent;
    /** 2回連打とみなす間隔（tick。バニラのクリエイティブ飛行の切り替えと同じ7）。 */
    public static final int DOUBLE_TAP_TICKS = 7;
    /** ジャンプ・スニークを前に押し始めてからのtick数、前のtickで押していたか、このtickで2回連打になったか。 */
    private static int sinceJump = 100, sinceSneak = 100;
    private static boolean wasJump, wasSneak, jumpDouble, sneakDouble;
    /** 飛び立ってからのtick数と、この飛行で停止を頼んだか（返事が来るまで1回だけ頼み、自前の移動をしない）。 */
    private static int flightAge;
    /** 前のtickの高さ（着地の判定の「上昇していない」に使う）。 */
    private static double lastY;
    private static boolean stopSent;
    /** 高速の今の速さ（ブロック/tick）。 */
    private static double speed;
    private ClientWhiteWingFlight() { }

    public static boolean active() { return on; }
    public static boolean isFast() { return on && fast; }
    public static void receive(WhiteWingFlightState packet) {
        boolean fresh = packet.epoch() != epoch || !on;
        on = packet.on(); epoch = packet.epoch(); fast = packet.fast(); safetyHover = packet.safetyHover();
        // 終わった時にジャンプを押したままなら、一度離すまで次の飛び立ちを頼まない。
        var options = Minecraft.getInstance().options;
        // 飛行の始まりと終わりでは、それまでの押下を連打に数えない。
        sinceJump = sinceSneak = 100; wasJump = options.keyJump.isDown(); wasSneak = options.keyShift.isDown(); stopSent = false;
        if (!on) { sentMoving = false; speed = 0; lift = 0; jumpHeld = 0; takeoffSent = options.keyJump.isDown(); }
        else if (fresh) {
            sentMoving = false; speed = 0; lift = LIFT_TICKS; flightAge = 0; wasSprint = options.keySprint.isDown();
            var player = Minecraft.getInstance().player;
            lastY = player == null ? 0 : player.getY();
        }
    }
    private static void send(boolean moving, boolean fastPressed) {
        if (Minecraft.getInstance().getConnection() != null) AcademyNetwork.CHANNEL.sendToServer(new WhiteWingFlightInput(epoch, moving, fastPressed));
        sentMoving = moving;
    }
    /** 能力設定の「離陸・飛行停止の操作」が2回押しか（既定は長押し。値がまだ届いていなければ長押し）。 */
    private static boolean doubleTapMode() {
        return io.github.pinchan4273.reacademycraft.skill.AbilitySettings.TAKEOFF_DOUBLE.equals(
                ClientSkillSettings.value(io.github.pinchan4273.reacademycraft.skill.AbilitySettings.FLIGHT_TAKEOFF));
    }
    /** 押し始めを数え、このtickでジャンプ・スニークが2回連打になったかを決める（連打に使った押下は次の連打に数えない）。 */
    private static void watchTaps(boolean jump, boolean sneak) {
        jumpDouble = false; sneakDouble = false;
        if (jump && !wasJump) { jumpDouble = sinceJump <= DOUBLE_TAP_TICKS; sinceJump = jumpDouble ? 100 : 0; }
        else if (sinceJump < 100) sinceJump++;
        if (sneak && !wasSneak) { sneakDouble = sinceSneak <= DOUBLE_TAP_TICKS; sinceSneak = sneakDouble ? 100 : 0; }
        else if (sinceSneak < 100) sinceSneak++;
        wasJump = jump; wasSneak = sneak;
    }
    /** 停止を頼む（1回だけ）。可否の条件は無く、サーバーは自分の飛行を終える。 */
    /** 最後に停止を頼んだ理由（調査と試験のため）。 */
    private static String lastRequest = "";
    public static String lastStopRequest() { return lastRequest; }
    private static void requestStop(String reason) {
        if (stopSent) return;
        stopSent = true;
        lastRequest = reason;
        if (Minecraft.getInstance().getConnection() != null) AcademyNetwork.CHANNEL.sendToServer(new WhiteWingFlightInput(WhiteWingFlight.STOP, false, false));
    }
    private static double approach(double from, double to, double limit) {
        double delta = to - from;
        return from + Math.min(Math.abs(delta), limit) * Math.signum(delta);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance(); var player = client.player;
        if (!on) { if (player != null) watchTakeoff(client, player); return; }
        if (player == null) return;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        float p = data == null ? 0 : data.getProficiency(WhiteWingFlight.ID);
        var options = client.options;
        boolean control = !SteeringGate.suspended(client.screen != null, TerminalHud.isOpen(), client.isWindowActive());
        watchTaps(control && options.keyJump.isDown(), control && options.keyShift.isDown());
        flightAge++;
        // 空中でやめる（2026-11-03のユーザーの決定: 長押しの設定ではスニーク2回、連打の設定ではジャンプ2回）。着地もここで見て、
        // サーバーの判定より先に終える（地面に触れた次のtickに、下の滞空の処理が体を浮かせ直すことがあるため）。
        // 接地の値ではなく、足の真下に当たる物があり上昇していないかで見る（サーバーと同じ。壁や地面すれすれで終わらないように）。
        // 2026-11-04（ユーザーの提案）: バニラのクリエイティブ飛行と同じく、下向きに動いて地面にぶつかったとき（前のtickの移動の縦の衝突が下側）
        // だけを着地にする。地面すれすれを横に飛ぶ間や、横の壁に触れている間は、下側の衝突が起きないので止まらない。
        boolean landed = flightAge > WhiteWingFlight.LAND_GRACE_TICKS && lift <= 0 && player.verticalCollisionBelow;
        if (doubleTapMode() ? jumpDouble : sneakDouble) requestStop(doubleTapMode() ? "double jump" : "double sneak");
        else if (landed) requestStop("landed (client)");
        // 停止を頼んだ後は、返事が来るまで自前の移動をしない（普通の物理で落ちる）。バニラの2回ジャンプのクリエイティブ飛行には入れない。
        if (stopSent) { player.getAbilities().flying = false; return; }
        float forward = control ? (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0) : 0;
        float strafe = control ? (options.keyLeft.isDown() ? 1 : 0) - (options.keyRight.isDown() ? 1 : 0) : 0;
        float vertical = control ? (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0) : 0;
        boolean sprint = control && options.keySprint.isDown();
        // 新規の押下だけで切り替える（押したままでは連続で切り替えない）。
        boolean fastPressed = sprint && !wasSprint;
        wasSprint = sprint;
        boolean moving = fast || forward != 0 || strafe != 0 || vertical != 0;
        if (moving != sentMoving || fastPressed) send(moving, fastPressed);

        if (player.isPassenger()) player.stopRiding();
        player.getAbilities().flying = false;
        player.fallDistance = 0;
        var motion = player.getDeltaMovement();
        if (fast && !safetyHover) {
            flyFast(player, p, motion);
            return;
        }
        speed = 0;
        Vec3 expected;
        if (safetyHover) expected = Vec3.ZERO;
        else {
            double h = WhiteWingFlight.horizontalSpeed(p) / 20, v = WhiteWingFlight.verticalSpeed(p) / 20;
            double length = Math.sqrt(forward * forward + strafe * strafe);
            double fx = length > 0 ? forward / length : 0, sx = length > 0 ? strafe / length : 0;
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
            expected = new Vec3((sx * cos - fx * sin) * h, vertical * v, (fx * cos + sx * sin) * h);
        }
        // 起動直後だけ少し浮く（2026-11-03のユーザーの指示: 浮くのはよいが、その後は上がり続けない）。
        boolean lifting = lift > 0 && !safetyHover;
        if (lift > 0) lift--;
        if (lifting) expected = new Vec3(expected.x, Math.max(expected.y, LIFT_SPEED), expected.z);
        // セーフティ滞空は減速を待たない。無入力の減速と通常への復帰は約0.5秒。
        double limit = safetyHover ? 10 : Math.max(.09, motion.length() / 10);
        if (!lifting && player.onGround() && vertical <= 0 && forward == 0 && strafe == 0) return;
        // 縦は、前のtickで実際に動いた速さ（重力と空気抵抗を戻したもの）から目標へ近づけ、その値をそのまま移動に使わせる。
        // 以前は物理の後の値に重力の分を足していたため、無入力でも毎tick約0.08ブロック上がり続けていた。
        player.setDeltaMovement(approach(motion.x, expected.x, limit), approach(placed(player, motion).y, expected.y, limit), approach(motion.z, expected.z, limit));
    }

    /**
     * 飛行していない間: 翼が出ていて（未元物質・能力使用モード・白翼飛行を習得済み）、空中でジャンプを{@link #HOLD_TICKS}押し続けたら
     * （連打の設定ではジャンプを2回連打したら）、サーバーへ飛び立ちを頼む（2026-11-03のユーザーの決定: タップは普通のジャンプ）。可否と費用はサーバーが決める。
     * 押し続けた時間は地上で押し始めた分も数えるので、地上から長押しすると跳んだ後にそのまま飛び立つ。
     */
    private static void watchTakeoff(Minecraft client, net.minecraft.client.player.LocalPlayer player) {
        boolean control = !SteeringGate.suspended(client.screen != null, TerminalHud.isOpen(), client.isWindowActive());
        boolean jump = control && client.options.keyJump.isDown();
        watchTaps(jump, control && client.options.keyShift.isDown());
        if (doubleTapMode()) {
            // 連打の設定: 空中でジャンプを2回連打したら頼む（地上からなら、1回目で跳び、2回目で飛び立つ）。
            jumpHeld = 0;
            if (!jumpDouble) return;
        } else {
            if (!jump) { jumpHeld = 0; takeoffSent = false; return; }
            jumpHeld++;
            if (takeoffSent || jumpHeld < HOLD_TICKS) return;
        }
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.isActive() || !io.github.pinchan4273.reacademycraft.skill.DarkMatter.CATEGORY.equals(data.getAbility())
                || !data.hasLearned(WhiteWingFlight.ID)) return;
        // 地上・水中・エリトラ・クリエイティブの飛行・騎乗・観戦では頼まない（普通の動きを優先する）。
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.getAbilities().flying
                || player.isPassenger() || player.isSpectator()) return;
        takeoffSent = true;
        if (client.getConnection() != null)
            AcademyNetwork.CHANNEL.sendToServer(new WhiteWingFlightInput(WhiteWingFlight.TAKEOFF, true, false));
    }

    /**
     * 前のtickで移動に使われた速さ。空中の物理は、移動の後に縦へ重力を引いて0.98倍、横を0.91倍するので、それを戻す。
     * 縦に何かへ当たったときは移動の値が0になるので、戻した値も0になる。
     */
    private static Vec3 placed(net.minecraft.client.player.LocalPlayer player, Vec3 motion) {
        double gravity = player.isNoGravity() ? 0 : player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());
        return new Vec3(motion.x / .91, motion.y / .98 + gravity, motion.z / .91);
    }

    private static void flyFast(net.minecraft.client.player.LocalPlayer player, float p, Vec3 motion) {
        double cruise = WhiteWingFlight.cruiseSpeed(p) / 20;
        var look = player.getLookAngle();
        // 下向きで巡航の1.5倍まで、上向きで巡航の半分まで。
        double target = look.y < 0 ? cruise * (1 + .5 * -look.y) : cruise * (1 - .5 * look.y);
        // 向きと速さは、前のtickで実際に動いた速さから読む（物理の後の値は重力で下へずれている）。
        motion = placed(player, motion);
        if (speed <= 0) speed = Math.max(motion.length(), .05);
        // 加速は約1秒、減速は緩やかに巡航へ戻る。
        speed = approach(speed, target, speed < target ? cruise / 20 : cruise / 50);
        var direction = motion.lengthSqr() > 1e-5 ? motion.normalize() : look;
        double limit = WhiteWingFlight.turnLimit(speed * 20, cruise * 20) / 20 * Mth.DEG_TO_RAD;
        double angle = Math.acos(Mth.clamp(direction.dot(look), -1, 1));
        Vec3 next;
        if (angle <= limit || angle < 1e-4) next = look;
        else {
            double t = limit / angle, sin = Math.sin(angle);
            next = sin < 1e-4 ? direction : direction.scale(Math.sin((1 - t) * angle) / sin).add(look.scale(Math.sin(t * angle) / sin)).normalize();
        }
        var velocity = next.scale(speed);
        // 置いた値がそのまま移動に使われる（重力の分は足さない）。
        player.setDeltaMovement(velocity);
    }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { on = false; fast = false; safetyHover = false; sentMoving = false; }
}
