package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.skill.LightShield;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Light Shieldのcontextを持つすべてのclientでの、原作EntityMdShieldとRenderMdShield: 術者の1ブロック前、1.1上の
 * 正方形にmdshield.pngを、視線の方向へ向けて描く。15tickで0.2から1.8へ大きくなり、6tickでフェードインし、
 * 自身の軸の周りにlerp(0.8, 2, ticks / 30)の速さで回る（原作の単位で毎秒何度なので、ほとんど回らない）。
 * さらに30%のtickで、前方の点から半ブロック以内にmdの粒子を出す。盾が無くなると同時に消える（原作setDead）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientLightShieldEffect {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdshield.png");
    static final float SIZE = 1.8f;
    static final class Shield {
        final int entityId; int ticks; float rotation; double lastRender;
        @Nullable Vec3 position, previous; float yaw, pitch, previousYaw, previousPitch;
        Shield(int entityId) { this.entityId = entityId; }
    }
    private static final Map<Long, Shield> SHIELDS = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static long drawn;
    private ClientLightShieldEffect() { }

    public static void receive(SkillVisual packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { SHIELDS.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) { SHIELDS.remove(packet.token()); return; }
        if (LightShield.ID.equals(packet.kind())) {
            var shield = new Shield(packet.entityId());
            var entity = level.getEntity(packet.entityId());
            if (entity != null) place(shield, entity);
            SHIELDS.put(packet.token(), shield);
        }
    }
    /** 原作updatePos: 術者の足元に視線の方向を足し、1.1上げる。頭のyawとピッチを使う。 */
    static void place(Shield shield, Entity entity) {
        shield.previous = shield.position; shield.previousYaw = shield.yaw; shield.previousPitch = shield.pitch;
        shield.position = entity.position().add(entity.getLookAngle()).add(0, 1.1, 0);
        shield.yaw = entity.getYHeadRot(); shield.pitch = entity.getXRot();
        if (shield.previous == null) { shield.previous = shield.position; shield.previousYaw = shield.yaw; shield.previousPitch = shield.pitch; }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { SHIELDS.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        SHIELDS.values().removeIf(shield -> {
            var entity = level.getEntity(shield.entityId);
            if (entity == null || entity.isRemoved()) return true;
            shield.ticks++;
            place(shield, entity);
            // 原作c_update: 30%のtickで、目の1ブロック前の点の周りにmdの粒子を出す。
            if (RANDOM.nextFloat() < .3f) {
                var at = entity.getEyePosition().add(entity.getLookAngle());
                level.addParticle(AcademyParticles.MD.get(), at.x + range(-.5, .5), at.y + range(-.5, .5), at.z + range(-.5, .5),
                        range(-.02, .02), range(-.01, .05), range(-.02, .02));
            }
            return false;
        });
    }
    static double range(double min, double max) { return min + RANDOM.nextDouble() * (max - min); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHIELDS.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        float partial = event.getPartialTick();
        double time = Util.getMillis() / 1000.0;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        try {
            for (var shield : SHIELDS.values()) {
                if (shield.position == null) continue;
                // 原作: 回転は、速さに前のフレームからの秒数を掛けて進める。
                double dt = shield.lastRender == 0 ? 0 : time - shield.lastRender;
                float speed = Mth.lerp(Math.min(shield.ticks / 30f, 1f), .8f, 2f);
                shield.rotation += speed * dt;
                if (shield.rotation >= 360) shield.rotation -= 360;
                shield.lastRender = time;
                var at = shield.previous.lerp(shield.position, partial).subtract(camera);
                float size = SIZE * Mth.lerp(Math.min(shield.ticks / 15f, 1f), .2f, 1f);
                float alpha = Math.min(shield.ticks / 6f, 1f);
                if (alpha <= 0) continue;
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, shield.previousYaw, shield.yaw)));
                pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, shield.previousPitch, shield.pitch)));
                pose.mulPose(Axis.ZP.rotationDegrees(shield.rotation));
                pose.scale(size, size, 1);
                var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                buffer.vertex(m, -.5f, -.5f, 0).uv(0, 0).color(1, 1, 1, alpha).endVertex();
                buffer.vertex(m, .5f, -.5f, 0).uv(1, 0).color(1, 1, 1, alpha).endVertex();
                buffer.vertex(m, .5f, .5f, 0).uv(1, 1).color(1, 1, 1, alpha).endVertex();
                buffer.vertex(m, -.5f, .5f, 0).uv(0, 1).color(1, 1, 1, alpha).endVertex();
                BufferUploader.drawWithShader(buffer.end());
                pose.popPose();
                drawn++;
            }
        } finally { RenderSystem.enableCull(); }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { SHIELDS.clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawn() { return drawn; }
    public static boolean showing(int entityId) { return SHIELDS.values().stream().anyMatch(s -> s.entityId == entityId); }
}
