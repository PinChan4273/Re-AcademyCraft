package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RenderSolarGen（RenderBlockMulti経由）: ブロックの中央（1マスのBlockMultiでは、pivotのoffsetと回転中心の
 * 合計がここになる）で、置いた向きのdrMapで回し、さらに90度回して0.014倍し、原作の太陽光モデルを描く。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SolarGeneratorRenderer extends LegacyBlockEntityRenderer<SolarGeneratorBlockEntity> {
    public static final Identifier MODEL = Identifier.fromNamespaceAndPath("academy", "block/solar_render");
    private static final float SCALE = .014f;
    private static final AtomicLong DRAWN = new AtomicLong();
    public SolarGeneratorRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.SOLAR_ENTITY.get(), SolarGeneratorRenderer::new);
    }
    /** 描画回数（テスト用）。実際の見え方は画像で判定する。 */
    public static long drawn() { return DRAWN.get(); }
    @Override protected void render(SolarGeneratorBlockEntity tile, float partialTick, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        var state = tile.getBlockState();
        if (!state.hasProperty(SolarGeneratorBlock.FACING)) return;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(SolarGeneratorBlock.legacyTurn(state.getValue(SolarGeneratorBlock.FACING))));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.scale(SCALE, SCALE, SCALE);
        ObjModels.draw(MODEL, RenderTypes.entityCutout(ObjModels.atlas()), pose, buffers, light, overlay);pose.popPose();
        DRAWN.incrementAndGet();
    }
}
