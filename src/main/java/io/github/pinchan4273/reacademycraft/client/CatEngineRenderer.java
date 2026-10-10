package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyCatEnginePose;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RenderCatEngine: 原作の猫のテクスチャの正方形1枚を、カメラへ向け、0.03だけ上下に揺らし、
 * 取り出したエネルギーの量に応じて水平軸の周りに回す。原作の四角はテクスチャのvが下から上へ向くように
 * 描かれていて、ここでも同じにしている。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CatEngineRenderer implements BlockEntityRenderer<CatEngineBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/block/cat_engine.png");
    /** GameTimerの起点: 原作は、タイマーを最初に読んだ時点から揺れを測る。 */
    private static final long START = Util.getMillis();
    private static final AtomicLong DRAWN = new AtomicLong();
    public CatEngineRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.CAT_ENGINE_ENTITY.get(), CatEngineRenderer::new);
    }
    /** 描いた回数（テスト用）。実際に見えているかは画像で判定する。 */
    public static long drawn() { return DRAWN.get(); }

    @Override public void render(CatEngineBlockEntity tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        long time = Util.getMillis();
        if (tile.lastRender != 0) tile.rotation = LegacyCatEnginePose.spin(tile.rotation, time - tile.lastRender, tile.generationIf());
        tile.lastRender = time;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var at = tile.getBlockPos();
        double dx = at.getX() + .5 - camera.x, dz = at.getZ() + .5 - camera.z;
        pose.pushPose();
        pose.translate(.5, LegacyCatEnginePose.bob((time - START) / 1000.0), .5);
        pose.mulPose(Axis.YP.rotationDegrees((float) LegacyCatEnginePose.facing(dx, dz)));
        pose.translate(0, .5, 0);
        pose.mulPose(Axis.XP.rotationDegrees((float) tile.rotation));
        pose.translate(-.5, -.5, 0);
        // 原作はこの正方形の描画でcullingを切るので、どちら側からも見える。
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        var last = pose.last();
        vertex(consumer, last, 0, 0, 0, 0, light); vertex(consumer, last, 1, 0, 1, 0, light);
        vertex(consumer, last, 1, 1, 1, 1, light); vertex(consumer, last, 0, 1, 0, 1, light);
        pose.popPose();
        DRAWN.incrementAndGet();
    }
    private static void vertex(VertexConsumer consumer, PoseStack.Pose last, float x, float y, float u, float v, int light) {
        consumer.vertex(last.pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(last.normal(), 0, 0, 1).endVertex();
    }
    @Override public boolean shouldRenderOffScreen(CatEngineBlockEntity tile) { return false; }
}
