package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
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
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientStormWing {
    /** 原作ACCEL。 */
    public static final double ACCEL = .16;
    private static int state = -1;
    private static KeyMapping held;
    private static Vec3 heldDirection;
    private static final boolean[] DOWN = new boolean[4];

    private ClientStormWing() { }
    public static int state() { return state; }
    /** 飛んでいる方向のキー。無ければnull。原作の適用中のキーで、KeyHintが光らせる。 */
    @javax.annotation.Nullable public static KeyMapping held() { return held; }
    public static void receive(StormWingState packet) {
        state = packet.state();
        if (state != StormWing.ACTIVE_STATE) { held = null; heldDirection = null; java.util.Arrays.fill(DOWN, false); }
    }

    private static double move(double from, double to, double limit) {
        double delta = to - from;
        return from + Math.min(Math.abs(delta), limit) * Math.signum(delta);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || state < 0) return;
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null) return;
        if (state == StormWing.ACTIVE_STATE && client.screen == null) {
            var options = client.options;
            KeyMapping[] keys = { options.keyUp, options.keyDown, options.keyLeft, options.keyRight };
            Vec3[] local = { new Vec3(0, 0, 1), new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(-1, 0, 0) };
            for (int i = 0; i < 4; i++) {
                boolean down = keys[i].isDown();
                if (down && !DOWN[i]) { held = keys[i]; heldDirection = local[i]; }
                else if (!down && DOWN[i] && held == keys[i]) { held = null; heldDirection = null; }
                DOWN[i] = down;
            }
        }
        if (heldDirection != null) {
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
        state = -1; held = null; heldDirection = null; java.util.Arrays.fill(DOWN, false);
    }
}
