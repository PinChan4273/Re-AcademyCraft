package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RenderEntityBlock: ブロック自身のモデルを、MagManipEntityBlockが毎tick加えるyaw・pitchで回して描く。
 * 周囲の電弧はClientMagneticBlockArcs、音はClientMagneticEffectsが担う。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagneticBlockRenderer extends LegacyEntityRenderer<MagneticBlockEntity> {
    public MagneticBlockRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AcademyContent.MAGNETIC_BLOCK.get(), MagneticBlockRenderer::new);
    }
    @Override protected void render(MagneticBlockEntity entity, float partialTick, PoseStack pose, SubmitNodeCollector buffer, int light) {
        pose.pushPose();
        // 原作のyawSpeedとpitchSpeedは1tickあたりRandUtils.rangef(1, 3)度で、ブロックごとに1回だけ決める。
        // モデルは中心の周りで回る。
        var speeds = new java.util.Random(entity.getUUID().getLeastSignificantBits());
        float yawSpeed = 1 + 2 * speeds.nextFloat(), pitchSpeed = 1 + 2 * speeds.nextFloat(), age = entity.tickCount + partialTick;
        pose.translate(0, .5, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * yawSpeed));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(age * pitchSpeed));
        pose.translate(-.5, -.5, -.5);
        var block = new net.minecraft.client.renderer.block.MovingBlockRenderState();
        block.blockPos = block.randomSeedPos = entity.blockPosition();
        block.blockState = entity.blockState();
        if (entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            block.biome = level.getBiome(block.blockPos);
            block.cardinalLighting = level.cardinalLighting();
            block.lightEngine = level.getLightEngine();
        }
        buffer.submitMovingBlock(pose, block, 0);
        pose.popPose();
    }
}
