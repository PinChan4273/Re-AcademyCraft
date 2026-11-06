package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.pinchan4273.reacademycraft.network.DarkBeetleShot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHOTS.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        var matrix = event.getPoseStack().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend(); RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull(); RenderSystem.depthMask(false);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
        ClientDraw.drawWithShader(buffer);
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc();
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { SHOTS.clear(); }

    /** この所有者が、このクライアントのプレイヤー本人か。 */
    public static boolean isLocalPlayer(@Nullable UUID owner) {
        var player = Minecraft.getInstance().player;
        return owner != null && player != null && owner.equals(player.getUUID());
    }
}
