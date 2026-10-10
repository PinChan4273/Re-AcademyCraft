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
 * （原作localTick）。閃光の後は、原作のGravityCancellorと同じく、術者が飛行中でも接地中でもない間、
 * 40tickのあいだclientの各tickの初めに落下速度へ0.072を足す。狙いの目印と閃光の音は移植していない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientFlashing {
    /** 原作GravityCancellor(player, 40)と、その0.072。 */
    public static final int GRAVITY_TICKS = 40;
    public static final double GRAVITY_CANCEL = .072;
    private static boolean active;
    private static int aiming = -1, gravityTicks;
    private static final boolean[] DOWN = new boolean[5];

    private ClientFlashing() { }
    public static boolean active() { return active; }
    public static int aiming() { return aiming; }

    public static void receive(FlashingState state) {
        active = state.active();
        if (!active) aiming = -1;
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
        if (!active) { java.util.Arrays.fill(DOWN, false); return; }
        if (client.screen != null) { aiming = -1; java.util.Arrays.fill(DOWN, false); return; }
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        for (int direction = Flashing.LEFT; direction <= Flashing.BACK; direction++) {
            boolean down = key(client, direction).isDown();
            if (down && !DOWN[direction]) aiming = direction;
            else if (!down && DOWN[direction] && aiming == direction) {
                AcademyNetwork.CHANNEL.sendToServer(new FlashingPerform(direction));
                aiming = -1;
            }
            DOWN[direction] = down;
        }
        if (aiming != -1 && data != null && !player.getAbilities().instabuild
                && !data.canConsumeCp(Flashing.consumption(data.getProficiency(Flashing.ID)))) aiming = -1;
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false; aiming = -1; gravityTicks = 0; java.util.Arrays.fill(DOWN, false);
    }
}
