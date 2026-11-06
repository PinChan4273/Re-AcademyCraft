package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.DarkBeetleShot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 甲虫のクライアント側（v1.5.0 §9）。砲撃の弾を独自に描く（バニラの粒子は使わない）。弾は発射の通知から直進し、着弾の通知で消える。
 * 所有者本人の照準が自分の甲虫を選ばないようにする判定もここに置く（共通のクラスからクライアントのクラスを呼ばないため）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkBeetles {
    /** 通知が来なくても消す上限（tick）。 */
    private static final int MAX_AGE = 60;
    private static final class Shot {
        final Vec3 from, direction; final float speed, range; int age;
        Shot(DarkBeetleShot packet) { from = packet.pos(); direction = packet.direction(); speed = packet.speed(); range = packet.range(); }
        float travelled(float partial) { return Math.min(range, (age + partial) * speed); }
    }
    private static final Map<Integer, Shot> SHOTS = new LinkedHashMap<>();

    private ClientDarkBeetles() { }

    public static void receive(DarkBeetleShot packet) {
        if (packet.kind() == DarkBeetleShot.FIRE) SHOTS.put(packet.shotId(), new Shot(packet));
        else SHOTS.remove(packet.shotId());
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        SHOTS.values().removeIf(shot -> ++shot.age > MAX_AGE || shot.age * shot.speed >= shot.range + shot.speed);
    }
    /** 26.2: 1.21.1のAFTER_PARTICLESの段階は、このMODの演出の提出の同じ名前の段（WorldSubmits）。 */
    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent extract) {
        io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.on(extract, io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.Stage.AFTER_PARTICLES, ClientDarkBeetles::renderFrame);
    }
    private static void renderFrame(io.github.pinchan4273.reacademycraft.client.render.WorldFrame event) {
        if (SHOTS.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().position();
        var matrix = event.getPoseStack().last().pose();
        // 1.21.1のposition_color、blendFunc(SRC_ALPHA, ONE)（加算）、カリングなし、深度は読むが書かない。
        io.github.pinchan4273.reacademycraft.client.render.Immediate.setShader(io.github.pinchan4273.reacademycraft.client.render.Immediate.Shader.COLOR);
        io.github.pinchan4273.reacademycraft.client.render.Immediate.enableBlend(); io.github.pinchan4273.reacademycraft.client.render.Immediate.additiveBlendFunc();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.disableCull(); io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(false);
        var buffer = io.github.pinchan4273.reacademycraft.client.render.Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
        for (var shot : SHOTS.values()) {
            float at = shot.travelled(partial);
            var head = shot.from.add(shot.direction.scale(at)).subtract(camera);
            var tail = shot.from.add(shot.direction.scale(Math.max(0, at - 1.6f))).subtract(camera);
            var side = shot.direction.cross(head);
            if (side.lengthSqr() < 1e-6) continue;
            side = side.normalize();
            for (var layer : new float[][]{{.09f, 1}, {.26f, .35f}}) {
                var s = side.scale(layer[0]);
                float a = layer[1];
                buffer.addVertex(matrix, (float) (tail.x + s.x), (float) (tail.y + s.y), (float) (tail.z + s.z)).setColor(1, 1, 1, 0f);
                buffer.addVertex(matrix, (float) (head.x + s.x), (float) (head.y + s.y), (float) (head.z + s.z)).setColor(.92f, .95f, 1, a);
                buffer.addVertex(matrix, (float) (head.x - s.x), (float) (head.y - s.y), (float) (head.z - s.z)).setColor(.92f, .95f, 1, a);
                buffer.addVertex(matrix, (float) (tail.x - s.x), (float) (tail.y - s.y), (float) (tail.z - s.z)).setColor(1, 1, 1, 0f);
            }
        }
        buffer.end();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(true); io.github.pinchan4273.reacademycraft.client.render.Immediate.enableCull(); io.github.pinchan4273.reacademycraft.client.render.Immediate.defaultBlendFunc();
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { SHOTS.clear(); }

    /** この所有者が、このクライアントのプレイヤー本人か。 */
    public static boolean isLocalPlayer(@Nullable UUID owner) {
        var player = Minecraft.getInstance().player;
        return owner != null && player != null && owner.equals(player.getUUID());
    }
}
