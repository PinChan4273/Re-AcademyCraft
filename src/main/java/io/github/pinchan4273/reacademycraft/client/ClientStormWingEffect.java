package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.skill.StormWing;
import io.github.pinchan4273.reacademycraft.visual.LegacyTornado;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 術者の近くのすべてのclientでの、原作StormWingEffectとStormWingContextCのtick:
 * 術者の肩の後ろに、tornado_ringの小さな竜巻を4つ置く（TornadoEffect(2, 0.16, dscale 2)、それぞれ自身のCompTransformで配置）。
 * 体のyawとピッチの5分の1で向きを変え、後ろへ70度傾ける。充電の間にフェードインし、飛んでいる間は0.7を保ち、
 * モードが終わると15tickでフェードアウトする。さらに毎tick、術者の周り3〜8ブロックに土の塵を12個出し、術者の周りを渦巻かせる。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientStormWingEffect {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tornado_ring.png");
    static final int TERMINATE_TICKS = 15;
    /** 原作tornadoListの変換: 平行移動、次にx・y・zの周りの回転（度）。 */
    static final double[][] TRANSFORMS = {{-.1, -.3, .1, 0, 45, 45}, {.1, -.3, .1, 0, -45, -45},
            {-.1, -.5, -.1, 0, -45, 45}, {.1, -.5, -.1, 0, 45, -45}};

    static final class Effect {
        final int entityId, chargeTime; int ticks, terminateTick; boolean terminated;
        final LegacyTornado[] tornadoes = new LegacyTornado[4];
        Effect(int entityId, int chargeTime, Random random) {
            this.entityId = entityId; this.chargeTime = Math.max(1, chargeTime);
            for (int i = 0; i < 4; i++) tornadoes[i] = new LegacyTornado(2, .16, 1, 2, random);
        }
        double alpha() {
            if (terminated) return .7 * (1 - terminateTick / (double) TERMINATE_TICKS);
            return ticks <= chargeTime ? ticks / (double) chargeTime * .7 : .7;
        }
    }
    private static final Map<Long, Effect> EFFECTS = new LinkedHashMap<>();
    private static final Random RANDOM = new Random();
    @Nullable private static ClientLevel world;
    private static long drawnQuads, dust;
    private ClientStormWingEffect() { }

    public static void receive(SkillVisual packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { EFFECTS.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) { var effect = EFFECTS.get(packet.token()); if (effect != null) effect.terminated = true; return; }
        if (StormWing.ID.equals(packet.kind())) EFFECTS.put(packet.token(), new Effect(packet.entityId(), packet.value(), RANDOM));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { EFFECTS.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        EFFECTS.values().removeIf(effect -> {
            var entity = level.getEntity(effect.entityId);
            if (entity == null || entity.isRemoved()) return true;
            if (effect.terminated) return ++effect.terminateTick > TERMINATE_TICKS;
            effect.ticks++;
            // 原作c_tick: 毎tick、術者の周り3〜8ブロックに塵を12個出し、渦巻かせる。
            for (int i = 0; i < 12; i++) {
                double theta = RANDOM.nextDouble() * Math.PI * 2, phi = -Math.PI + RANDOM.nextDouble() * Math.PI * 2;
                double r = 3 + RANDOM.nextDouble() * 5, rzx = r * Math.sin(phi), cth = Math.cos(theta), sth = Math.sin(theta);
                var mote = new TerrainParticle(level, entity.getX() + rzx * cth, entity.getY() + r * Math.cos(phi), entity.getZ() + rzx * sth,
                        0, 0, 0, Blocks.DIRT.defaultBlockState(), entity.blockPosition()) {
                    { gravity = .02f; }
                };
                // 原作ParticleBlockDustは、与えられた速度をそのまま保つ。
                mote.setParticleSpeed(sth * .7, -.01 + RANDOM.nextDouble() * .06, -cth * .7);
                mote.scale(.5f);
                client.particleEngine.add(mote);
                dust++;
            }
            return false;
        });
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = event.getPartialTick();
        double time = Util.getMillis() / 1000.0;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        // 原作はStorm Wingをalphaテストなしで描いた（LegacyAlphaShaders）。
        RenderSystem.setShader(LegacyAlphaShaders::positionTexColor);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        try {
            for (var effect : EFFECTS.values()) {
                Entity entity = level.getEntity(effect.entityId);
                if (entity == null) continue;
                float alpha = (float) (effect.alpha() * .7);
                if (alpha <= 0) continue;
                float yaw = entity instanceof LivingEntity living ? Mth.lerp(partial, living.yBodyRotO, living.yBodyRot) : entity.getYRot();
                float pitch = Mth.lerp(partial, entity.xRotO, entity.getXRot());
                var at = entity.getPosition(partial).add(0, 1.6, 0).subtract(camera);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.mulPose(Axis.XP.rotationDegrees(pitch * .2f));
                pose.mulPose(Axis.XP.rotationDegrees(-70));
                pose.translate(0, .2, -.5);
                for (int i = 0; i < 4; i++) {
                    var t = TRANSFORMS[i];
                    pose.pushPose();
                    pose.translate(t[0], t[1], t[2]);
                    pose.mulPose(Axis.XP.rotationDegrees((float) t[3]));
                    pose.mulPose(Axis.YP.rotationDegrees((float) t[4]));
                    pose.mulPose(Axis.ZP.rotationDegrees((float) t[5]));
                    draw(pose.last().pose(), effect.tornadoes[i].quads(effect.tornadoes[i].time(time)), alpha);
                    pose.popPose();
                }
                pose.popPose();
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
    }
    private static void draw(org.joml.Matrix4f m, List<LegacyTornado.Quad> quads, float alpha) {
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (var q : quads)
            for (int k = 0; k < 4; k++)
                buffer.vertex(m, (float) q.x()[k], (float) q.y()[k], (float) q.z()[k]).uv((float) q.u()[k], (float) q.v()[k]).color(1, 1, 1, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        drawnQuads += quads.size();
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { EFFECTS.clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawnQuads() { return drawnQuads; }
    public static long dust() { return dust; }
    public static boolean showing(int entityId) { return EFFECTS.values().stream().anyMatch(e -> e.entityId == entityId && !e.terminated); }
    public static int live() { return EFFECTS.size(); }
}
