package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicLongArray;
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
 * 原作RenderPhaseGen: 原作のip_genモデルをブロックの中央に、拡大も回転もせず描き、タンクの量に応じた5枚の
 * テクスチャのどれかを貼る。モデルとテクスチャは原作のもので、テクスチャごとにForgeのOBJローダーでbakeする。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhaseGeneratorRenderer implements BlockEntityRenderer<PhaseGeneratorBlockEntity> {
    private static final ResourceLocation[] MODELS = new ResourceLocation[5];
    static { for (int i = 0; i < 5; i++) MODELS[i] = ResourceLocation.fromNamespaceAndPath("academy", "block/ip_gen" + i); }
    private static final RandomSource RANDOM = RandomSource.create(42);
    /** テクスチャごとの描画回数（テスト用）。実際の見え方は画像で判定する。 */
    private static final AtomicLongArray DRAWN = new AtomicLongArray(5);
    public PhaseGeneratorRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.PHASE_GEN_ENTITY.get(), PhaseGeneratorRenderer::new);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { for (var model : MODELS) event.register(model); }
    public static long drawn(int level) { return DRAWN.get(level); }
    @Override public void render(PhaseGeneratorBlockEntity tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int level = PhaseGeneratorBlockEntity.modelLevel(tile.phaseMB());
        var model = Minecraft.getInstance().getModelManager().getModel(MODELS[level]);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        var consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        for (int side = 0; side <= 6; side++) {
            RANDOM.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], RANDOM, ModelData.EMPTY, null))
                consumer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay, true);
        }
        pose.popPose();
        DRAWN.incrementAndGet(level);
    }
}
