package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.FlashingPerform;
import io.github.pinchan4273.reacademycraft.network.FlashingState;
import io.github.pinchan4273.reacademycraft.skill.Flashing;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作Flashingの術者側。モードがONの間、移動キーで方向を決める（原作のキーグループと同じ）。
 * 原作と同じく、移動キーは術者の移動も兼ねる: キーを押すとその方向を選び、同じキーを離すとサーバーへ
 * 閃光を求める。画面が開くと狙いを取り消し（原作onKeyAbort）、閃光に足りるCPが無くなったときも取り消す
 * （TOGGLE-GUI-001: モードはONのまま。画面・データ端末・フォーカスの喪失の間も取り消し、その間に押されていた方向キーは、
 * 一度離すまで狙いにしない。{@link Aim}）
 * （原作localTick）。閃光の後は、原作のGravityCancellorと同じく、術者が飛行中でも接地中でもない間、
 * 40tickのあいだclientの各tickの初めに落下速度へ0.072を足す。狙いの目印と閃光の音は移植していない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientFlashing {
    /** 原作GravityCancellor(player, 40)と、その0.072。 */
    public static final int GRAVITY_TICKS = 40;
    public static final double GRAVITY_CANCEL = .072;
    private static boolean active;
    private static int gravityTicks;
    private static final Aim AIM = new Aim();

    private ClientFlashing() { }
    public static boolean active() { return active; }
    public static int aiming() { return AIM.aiming(); }

    public static void receive(FlashingState state) {
        active = state.active();
        if (!active) AIM.cancel();
        if (state.flashed()) gravityTicks = GRAVITY_TICKS;
    }
    private static KeyMapping key(Minecraft client, int direction) {
        var options = client.options;
        return switch (direction) {
            case Flashing.LEFT -> options.keyLeft;
            case Flashing.RIGHT -> options.keyRight;
            case Flashing.FORWARD -> options.keyUp;
            default -> options.keyDown;
        };
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null) return;
        if (event.phase == TickEvent.Phase.START) {
            if (gravityTicks > 0) {
                if (!player.getAbilities().flying && !player.onGround())
                    player.setDeltaMovement(player.getDeltaMovement().add(0, GRAVITY_CANCEL, 0));
                gravityTicks--;
            }
            return;
        }
        if (!active) { AIM.reset(); return; }
        if (SteeringGate.suspended(client.screen != null, TerminalHud.isOpen(), client.isWindowActive())) { AIM.suspend(); return; }
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        boolean[] pressed = new boolean[5], marked = new boolean[5];
        for (int direction = Flashing.LEFT; direction <= Flashing.BACK; direction++) {
            pressed[direction] = key(client, direction).isDown();
            marked[direction] = ClientFocusMarks.marked(key(client, direction));
        }
        int flash = AIM.read(pressed, marked);
        if (flash != -1) AcademyNetwork.CHANNEL.sendToServer(new FlashingPerform(flash));
        if (AIM.aiming() != -1 && data != null && !player.getAbilities().instabuild
                && !data.canConsumeCp(Flashing.consumption(data.getProficiency(Flashing.ID)))) AIM.cancel();
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false; gravityTicks = 0; AIM.reset();
    }

    /**
     * 方向の狙いの状態機械（Flashing.LEFT〜BACKの添字）。キーを押すとその方向を狙い、狙っているキーを離すと閃光を求める。
     *
     * <p>操作できない間（画面・データ端末・フォーカスなし、{@link SteeringGate}）は狙いを取り消し（原作onKeyAbort）、閃光を求めない。
     * 戻った後に押されたままのキーは、その前から押していたものも、その間に押し始めたものも、一度離されるまで狙いにしない。
     * 閉じたときの離しを閃光へ化けさせず、GUIの中の入力で狙いを始めない（TOGGLE-GUI-001）。Minecraftの型を使わない。
     */
    public static final class Aim {
        private final boolean[] down = new boolean[5], stale = new boolean[5];
        private int aiming = -1;
        /** 狙っている方向。無ければ-1。 */
        public int aiming() { return aiming; }
        /** モードがOFF・ログアウト: すべて離した状態から数え直す。 */
        public void reset() { aiming = -1; java.util.Arrays.fill(down, false); java.util.Arrays.fill(stale, false); }
        /** 操作できないtick。 */
        public void suspend() { aiming = -1; java.util.Arrays.fill(down, false); java.util.Arrays.fill(stale, true); }
        /** 閃光に足りるCPが無い: 狙いだけ取り消す（キーは押されたまま）。 */
        public void cancel() { aiming = -1; }
        /** 操作できるtick: 各方向のキーが押されていると報告されているか。閃光を求める方向（無ければ-1）。 */
        public int read(boolean[] pressed) { return read(pressed, new boolean[5]); }
        /**
         * F03: フォーカスの喪失の印（{@link SteeringGate.FocusMark}）のある方向は、離されていると報告されても数えない状態を解かず、
         * 押されていても狙わない（本当の離しを見るまで）。
         *
         * <p>F03-F01（WORK-104）: 狙っている方向に印が付いた（操作できないtickを挟まずにフォーカスを失って戻った）ときは、
         * 押されていても離されていても、狙いを取り消すだけで閃光を求めない（{@link #suspend}と同じ扱い）。
         * 閃光にするのは、印の無い本当の離しだけ。原作（LWJGL2）はフォーカスの喪失でキーを全て離した状態にし、Flashingは
         * それをonKeyUpとして閃光を撃っていた。ここは、操作できない間の入力で閃光を撃たない（TOGGLE-GUI-001、司令室7の指示）ために、
         * 原作から意図して変えている。
         */
        public int read(boolean[] pressed, boolean[] focusMarked) {
            int flash = -1;
            for (int direction = Flashing.LEFT; direction <= Flashing.BACK; direction++) {
                if (focusMarked[direction]) stale[direction] = true;
                else if (!pressed[direction]) stale[direction] = false;
                boolean now = pressed[direction] && !stale[direction];
                if (now && !down[direction]) aiming = direction;
                else if (!now && down[direction] && aiming == direction) {
                    if (!focusMarked[direction]) flash = direction;
                    aiming = -1;
                }
                down[direction] = now;
            }
            return flash;
        }
    }
}
