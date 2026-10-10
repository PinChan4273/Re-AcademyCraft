package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RenderSolarGen（RenderBlockMulti経由）: ブロックの中央（1マスのBlockMultiでは、pivotのoffsetと回転中心の
 * 合計がここになる）で、置いた向きのdrMapで回し、さらに90度回して0.014倍し、原作の太陽光モデルを描く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SolarGeneratorRenderer implements BlockEntityRenderer<SolarGeneratorBlockEntity> {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("academy", "block/solar_render");
    private static final float SCALE = .014f;
    private static final RandomSource RANDOM = RandomSource.create(42);
    private static final AtomicLong DRAWN = new AtomicLong();
    public SolarGeneratorRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.SOLAR_ENTITY.get(), SolarGeneratorRenderer::new);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(MODEL); }
    /** 描画回数（テスト用）。実際の見え方は画像で判定する。 */
    public static long drawn() { return DRAWN.get(); }
    @Override public void render(SolarGeneratorBlockEntity tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = tile.getBlockState();
        if (!state.hasProperty(SolarGeneratorBlock.FACING)) return;
        var model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(SolarGeneratorBlock.legacyTurn(state.getValue(SolarGeneratorBlock.FACING))));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.scale(SCALE, SCALE, SCALE);
        var consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        for (int side = 0; side <= 6; side++) {
            RANDOM.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], RANDOM, ModelData.EMPTY, null))
                consumer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay, true);
        }
        pose.popPose();
        DRAWN.incrementAndGet();
    }
}
