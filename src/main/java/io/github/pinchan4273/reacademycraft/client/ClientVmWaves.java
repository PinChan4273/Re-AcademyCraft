package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.VmWave;
import io.github.pinchan4273.reacademycraft.visual.LegacyCubicCurve;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作WaveEffectとWaveEffectRenderer: 術者の視線の方向へ向いたglow_circleの輪を、15tickのあいだ1tickに1/40ずつ前へ漂わせる。
 * 各輪は、自身の寿命（8〜11tick）、開始（2tickおき、±1）、視線の方向の距離（1.5おき、±0.3）、大きさ（波の大きさの0.8〜1.2倍）を持ち、
 * 原作と同じくこのclientで乱数で決める。alphaと大きさは原作の3次曲線に従い、壁越し・両面で描く。
 * 原作はcreateBillboard(-.5, -.5, 1, 1)で四角を作るが、後ろの2つの引数は大きさではなく角の座標なので、
 * 四角は-0.5から1までになる。原作のまま残している。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientVmWaves {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_circle.png");
    static final int LIFE = 15;
    static final LegacyCubicCurve ALPHA = new LegacyCubicCurve().add(0, 0).add(.2, 1).add(.5, 1).add(.8, 1).add(1, 0);
    static final LegacyCubicCurve SIZE = new LegacyCubicCurve().add(0, .4).add(.2, .8).add(2.5, 1.5);

    record Ring(int life, double offset, double size, int timeOffset) { }
    static final class Wave {
        final Vec3 position; final float yaw, pitch; final List<Ring> rings = new ArrayList<>(); int ticks;
        Wave(VmWave packet, RandomSource random) {
            position = packet.position();
            yaw = packet.yaw() + (packet.yawJitter() == 0 ? 0 : -packet.yawJitter() + random.nextFloat() * 2 * packet.yawJitter());
            pitch = packet.pitch() + (packet.pitchJitter() == 0 ? 0 : -packet.pitchJitter() + random.nextFloat() * 2 * packet.pitchJitter());
            for (int i = 0; i < packet.rings(); i++)
                rings.add(new Ring(8 + random.nextInt(4), i * 1.5 + (-.3 + random.nextDouble() * .6),
                        packet.size() * (.8 + random.nextDouble() * .4), i * 2 + (-1 + random.nextInt(2))));
        }
    }
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static long received, drawnRings;
    private ClientVmWaves() { }

    public static void receive(VmWave packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { WAVES.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        WAVES.add(new Wave(packet, RANDOM)); received++;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (level != world) { WAVES.clear(); world = level; return; }
        if (Minecraft.getInstance().isPaused()) return;
        // 原作onUpdate: ticksExistedを数え上げ、寿命で波を消す。
        WAVES.removeIf(wave -> ++wave.ticks >= LIFE);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WAVES.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.disableDepthTest();
        try {
            for (var wave : WAVES) {
                double maxAlpha = Mth.clamp(ALPHA.valueAt(wave.ticks / (double) LIFE), 0, 1);
                var at = wave.position.subtract(camera);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(-wave.yaw));
                pose.mulPose(Axis.XP.rotationDegrees(wave.pitch));
                pose.translate(0, 0, wave.ticks / 40.0);
                double sizeScale = SIZE.valueAt(Mth.clamp(wave.ticks / 20.0, 0, 1.62));
                for (var ring : wave.rings) {
                    float alpha = (float) Math.min(maxAlpha, Mth.clamp(ALPHA.valueAt((wave.ticks - ring.timeOffset()) / (double) ring.life()), 0, 1));
                    if (alpha <= 0) continue;
                    pose.pushPose();
                    pose.translate(0, 0, ring.offset());
                    float scale = (float) (ring.size() * sizeScale);
                    pose.scale(scale, scale, 1);
                    var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                    float a = alpha * .7f;
                    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    buffer.vertex(m, -.5f, -.5f, 0).uv(0, 0).color(1, 1, 1, a).endVertex();
                    buffer.vertex(m, 1, -.5f, 0).uv(1, 0).color(1, 1, 1, a).endVertex();
                    buffer.vertex(m, 1, 1, 0).uv(1, 1).color(1, 1, 1, a).endVertex();
                    buffer.vertex(m, -.5f, 1, 0).uv(0, 1).color(1, 1, 1, a).endVertex();
                    BufferUploader.drawWithShader(buffer.end());
                    pose.popPose();
                    drawnRings++;
                }
                pose.popPose();
            }
        } finally { RenderSystem.enableDepthTest(); RenderSystem.enableCull(); }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { WAVES.clear(); world = null; }
    /** テスト用の入口。 */
    public static long received() { return received; }
    public static long drawnRings() { return drawnRings; }
    public static int live() { return WAVES.size(); }
}
