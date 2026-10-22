package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.StormWingInput;
import io.github.pinchan4273.reacademycraft.network.StormWingState;
import io.github.pinchan4273.reacademycraft.skill.StormWing;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作StormWingの術者側: 原作のl_tickと同じく、術者自身のclientが術者を動かす。
 *
 * モードがONで方向キーが押されていない間は、空中で静止する: 足元の半ブロック上から0.3下までにブロックがあれば
 * 落下速度を上向き0.1にし、無ければ重力に逆らって0.078を足す。発動すると、移動キーで方向を選ぶ（術者自身の
 * 前後左右で、ピッチを含む）。毎tick、速度の各軸を、その方向に原作の速さを掛けた値へ最大0.16ずつ近づける。
 * 最後に押したキーが優先され、離すと静止に戻る。風の音と粒子は移植していない。
 *
 * <p>推進しているか（方向キーを押しているか）は、変わるたびにサーバーへ伝える（{@link StormWingInput}）。サーバーは推進の費用を
 * 払えないとき滞空だけを許し（stalled）、その間は方向キーで動かさない。画面を開いた・データ端末を開いた・ウィンドウのフォーカスが
 * 外れたとき（{@link SteeringGate}）は、押していたキーを放したものとして推進をやめ、滞空に戻る（モードはONのまま）。戻った後に
 * 押されたままのキーは、その前から押していたものも、その間に押し始めたものも、離されたのを見るまで押下として数えない（{@link Keys}）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientStormWing {
    /** 原作ACCEL。 */
    public static final double ACCEL = .16;
    private static int state = -1, epoch;
    private static boolean stalled, sentThrust;
    /** 前・後・左・右の、術者自身の向きでの方向。 */
    private static final Vec3[] LOCAL = { new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(-1, 0, 0) };
    private static final Keys KEYS = new Keys();

    /**
     * 方向キーの押下の状態機械。最後に押したキーが優先され、そのキーを離すと静止に戻る。
     *
     * <p>操作できない間（画面・データ端末・フォーカスなし）は、押していたキーを放したものとする。戻った後に押されたままと
     * 報告されるキーは、新しい押下として数えず、一度離されたのを見てから数える。フォーカスの外で離したキーの離しが届かず押された
     * ままに見えても、推進を勝手に再開しない。TOGGLE-GUI-001: 操作できない間に押し始めたキーも同じ（画面を閉じるとゲームが
     * 物理的なキーの状態を読み直すため、それを新しい押下にすると、GUIの中で押した方向へ閉じた途端に推進してしまう）。
     * 戻ったときに離されていたキーは、すぐ効く。
     */
    public static final class Keys {
        private final boolean[] down = new boolean[4], stale = new boolean[4];
        private int held = -1;
        /** 推進している方向（0前・1後・2左・3右）。無ければ-1。 */
        public int held() { return held; }
        /** モードの始まりと終わり: すべて離した状態から数え直す（始まったときに押しているキーは、すぐ推進する）。 */
        public void reset() { held = -1; java.util.Arrays.fill(down, false); java.util.Arrays.fill(stale, false); }
        /** 操作できないtick。どのキーも、戻った後に一度離されるまで数えない（離されていたキーは、戻った最初のtickで外れる）。 */
        public void suspend() {
            java.util.Arrays.fill(stale, true); java.util.Arrays.fill(down, false);
            held = -1;
        }
        /** 操作できるtick: 各方向のキーが押されていると報告されているか。 */
        public void read(boolean[] pressed) { read(pressed, new boolean[4]); }
        /**
         * F03: フォーカスの喪失の印（{@link SteeringGate.FocusMark}）のある方向は、離されていると報告されても数えない状態を解かず、
         * 押されていても推進しない（本当の離しを見るまで）。
         */
        public void read(boolean[] pressed, boolean[] focusMarked) {
            for (int i = 0; i < 4; i++) {
                if (focusMarked[i]) stale[i] = true;
                else if (!pressed[i]) stale[i] = false;
                boolean now = pressed[i] && !stale[i];
                if (now && !down[i]) held = i;
                else if (!now && down[i] && held == i) held = -1;
                down[i] = now;
            }
        }
    }

    private ClientStormWing() { }
    public static int state() { return state; }
    /** 飛んでいる方向のキー。無ければnull。原作の適用中のキーで、KeyHintが光らせる。 */
    @javax.annotation.Nullable public static KeyMapping held() {
        var options = Minecraft.getInstance().options;
        return switch (KEYS.held()) { case 0 -> options.keyUp; case 1 -> options.keyDown; case 2 -> options.keyLeft; case 3 -> options.keyRight; default -> null; };
    }
    /** サーバーが伝えた今のモードの番号（推進の入力に付けて返す）。 */
    public static int epoch() { return epoch; }
    /** サーバーが推進を払えず、滞空だけを許しているか。 */
    public static boolean stalled() { return stalled; }
    public static void receive(StormWingState packet) {
        boolean fresh = packet.epoch() != epoch || state != StormWing.ACTIVE_STATE;
        state = packet.state(); epoch = packet.epoch(); stalled = packet.stalled();
        if (state != StormWing.ACTIVE_STATE) { release(); sentThrust = false; }
        else if (fresh) { release(); sentThrust = false; send(); }
    }
    private static void release() { KEYS.reset(); }
    private static boolean thrusting() { return KEYS.held() >= 0; }
    /** 推進の入力が変わったらサーバーへ伝える。 */
    private static void send() {
        if (state != StormWing.ACTIVE_STATE || Minecraft.getInstance().getConnection() == null) return;
        boolean thrust = thrusting();
        AcademyNetwork.CHANNEL.sendToServer(new StormWingInput(epoch, thrust));
        sentThrust = thrust;
    }

    private static double move(double from, double to, double limit) {
        double delta = to - from;
        return from + Math.min(Math.abs(delta), limit) * Math.signum(delta);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance();
        if (state < 0) return;
        var player = client.player;
        if (player == null) return;
        if (state == StormWing.ACTIVE_STATE && SteeringGate.suspended(client.screen != null, TerminalHud.isOpen(), client.isWindowActive()))
            KEYS.suspend();
        else if (state == StormWing.ACTIVE_STATE) {
            var options = client.options;
            KEYS.read(new boolean[] { options.keyUp.isDown(), options.keyDown.isDown(), options.keyLeft.isDown(), options.keyRight.isDown() },
                    new boolean[] { ClientFocusMarks.marked(options.keyUp), ClientFocusMarks.marked(options.keyDown),
                            ClientFocusMarks.marked(options.keyLeft), ClientFocusMarks.marked(options.keyRight) });
        }
        if (state == StormWing.ACTIVE_STATE && thrusting() != sentThrust) send();
        if (thrusting() && !stalled) {
            var heldDirection = LOCAL[KEYS.held()];
            var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
            double speed = StormWing.speed(data == null ? 0 : data.getProficiency(StormWing.ID));
            var expected = StormWing.worldSpace(heldDirection, player.getYHeadRot(), player.getXRot()).scale(speed);
            if (player.isPassenger()) player.stopRiding();
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(move(motion.x, expected.x, ACCEL), move(motion.y, expected.y, ACCEL), move(motion.z, expected.z, ACCEL));
        } else {
            var from = player.position().add(0, .5, 0); var to = player.position().add(0, -.3, 0);
            boolean near = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS;
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, near ? .1 : motion.y + .078, motion.z);
        }
        player.fallDistance = 0;
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        state = -1; stalled = false; sentThrust = false; release();
    }
}
