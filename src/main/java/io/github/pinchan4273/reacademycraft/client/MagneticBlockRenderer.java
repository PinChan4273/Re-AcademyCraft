package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作RenderEntityBlock: ブロック自身のモデルを、MagManipEntityBlockが毎tick加えるyaw・pitchで回して描く。
 * 周囲の電弧はClientMagneticBlockArcs、音はClientMagneticEffectsが担う。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagneticBlockRenderer extends EntityRenderer<MagneticBlockEntity> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public MagneticBlockRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AcademyContent.MAGNETIC_BLOCK.get(), MagneticBlockRenderer::new);
    }
    @Override public void render(MagneticBlockEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        pose.pushPose();
        // 原作のyawSpeedとpitchSpeedは1tickあたりRandUtils.rangef(1, 3)度で、ブロックごとに1回だけ決める。
        // モデルは中心の周りで回る。
        var speeds = new java.util.Random(entity.getUUID().getLeastSignificantBits());
        float yawSpeed = 1 + 2 * speeds.nextFloat(), pitchSpeed = 1 + 2 * speeds.nextFloat(), age = entity.tickCount + partialTick;
        pose.translate(0, .5, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * yawSpeed));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(age * pitchSpeed));
        pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(entity.blockState(), pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose(); super.render(entity, yaw, partialTick, pose, buffer, light);
    }
    @Override public ResourceLocation getTextureLocation(MagneticBlockEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
