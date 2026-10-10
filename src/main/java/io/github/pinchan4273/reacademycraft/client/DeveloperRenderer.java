package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.develop.DeveloperTier;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock;
import io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity;
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
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * RenderBlockMultiを通した原作RenderDeveloperNormalとRenderDeveloperAdvanced: 原点のブロックの中央に
 * （回転の中心(0.5, 0, 0.5)はどの向きでもそこに来る）、BlockMultiのdrMapで回し、さらに半回転し、半分の大きさで、
 * 原作の開発機のモデルを描く。原作と同じく、cullingなし・合成ありで描く。原作BlockDeveloperは、
 * どのセルでもブロックの枠線を隠す。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeveloperRenderer implements BlockEntityRenderer<NormalDeveloperBlockEntity> {
    public static final ResourceLocation NORMAL = ResourceLocation.fromNamespaceAndPath("academy", "block/developer_normal_render"),
            ADVANCED = ResourceLocation.fromNamespaceAndPath("academy", "block/developer_advanced_render");
    private static final RandomSource RANDOM = RandomSource.create(42);
    private static final AtomicLong DRAWN = new AtomicLong();
    public DeveloperRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.DEV_NORMAL_ENTITY.get(), DeveloperRenderer::new);
        event.registerBlockEntityRenderer(AcademyContent.DEV_ADVANCED_ENTITY.get(), DeveloperRenderer::new);
        MinecraftForge.EVENT_BUS.addListener(DeveloperRenderer::highlight);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(NORMAL); event.register(ADVANCED); }
    public static long drawn() { return DRAWN.get(); }
    /** BlockDeveloper.EventHandler.onDrawBlockHighlight。 */
    private static void highlight(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof NormalDeveloperBlock) event.setCanceled(true);
    }
    @Override public void render(NormalDeveloperBlockEntity tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = tile.getBlockState();
        if (!(state.getBlock() instanceof NormalDeveloperBlock block) || state.getValue(NormalDeveloperBlock.PART) != 0) return;
        var facing = state.getValue(NormalDeveloperBlock.FACING);
        var model = Minecraft.getInstance().getModelManager().getModel(block.tier() == DeveloperTier.ADVANCED ? ADVANCED : NORMAL);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(SolarGeneratorBlock.legacyTurn(facing)));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(.5f, .5f, .5f);
        var consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        for (int side = 0; side <= 6; side++) {
            RANDOM.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], RANDOM, ModelData.EMPTY, null))
                consumer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay, true);
        }
        pose.popPose();
        DRAWN.incrementAndGet();
    }
    /** 機械は、原点のブロックの外の、3ブロックの奥行きの構造の上に立つ。 */
    @Override public boolean shouldRenderOffScreen(NormalDeveloperBlockEntity tile) { return true; }
    @Override public int getViewDistance() { return 96; }
}
