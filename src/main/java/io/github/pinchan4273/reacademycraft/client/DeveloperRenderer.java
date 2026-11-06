package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import io.github.pinchan4273.reacademycraft.develop.DeveloperTier;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock;
import io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * RenderBlockMultiを通した原作RenderDeveloperNormalとRenderDeveloperAdvanced: 原点のブロックの中央に
 * （回転の中心(0.5, 0, 0.5)はどの向きでもそこに来る）、BlockMultiのdrMapで回し、さらに半回転し、半分の大きさで、
 * 原作の開発機のモデルを描く。原作と同じく、cullingなし・合成ありで描く。原作BlockDeveloperは、
 * どのセルでもブロックの枠線を隠す。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DeveloperRenderer extends LegacyBlockEntityRenderer<NormalDeveloperBlockEntity> {
    public static final Identifier NORMAL = Identifier.fromNamespaceAndPath("academy", "block/developer_normal_render"),
            ADVANCED = Identifier.fromNamespaceAndPath("academy", "block/developer_advanced_render");
    private static final AtomicLong DRAWN = new AtomicLong();
    public DeveloperRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.DEV_NORMAL_ENTITY.get(), DeveloperRenderer::new);
        event.registerBlockEntityRenderer(AcademyContent.DEV_ADVANCED_ENTITY.get(), DeveloperRenderer::new);
        NeoForge.EVENT_BUS.addListener(DeveloperRenderer::highlight);
    }
    public static long drawn() { return DRAWN.get(); }
    /** BlockDeveloper.EventHandler.onDrawBlockHighlight。 */
    private static void highlight(net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent event) {
        if (event.getBlockState().getBlock() instanceof NormalDeveloperBlock) event.setCanceled(true);
    }
    @Override protected void render(NormalDeveloperBlockEntity tile, float partialTick, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        var state = tile.getBlockState();
        if (!(state.getBlock() instanceof NormalDeveloperBlock block) || state.getValue(NormalDeveloperBlock.PART) != 0) return;
        var facing = state.getValue(NormalDeveloperBlock.FACING);
        var model = block.tier() == DeveloperTier.ADVANCED ? ADVANCED : NORMAL;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(SolarGeneratorBlock.legacyTurn(facing)));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(.5f, .5f, .5f);
        ObjModels.draw(model, RenderTypes.entityTranslucent(ObjModels.atlas()), pose, buffers, light, overlay);pose.popPose();
        DRAWN.incrementAndGet();
    }
    /** 機械は、原点のブロックの外の、3ブロックの奥行きの構造の上に立つ。 */
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 96; }
}
