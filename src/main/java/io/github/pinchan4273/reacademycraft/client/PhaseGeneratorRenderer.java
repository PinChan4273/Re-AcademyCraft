package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RenderPhaseGen: 原作のip_genモデルをブロックの中央に、拡大も回転もせず描き、タンクの量に応じた5枚の
 * テクスチャのどれかを貼る。モデルとテクスチャは原作のもので、テクスチャごとにForgeのOBJローダーでbakeする。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PhaseGeneratorRenderer extends LegacyBlockEntityRenderer<PhaseGeneratorBlockEntity> {
    private static final Identifier[] MODELS = new Identifier[5];
    static { for (int i = 0; i < 5; i++) MODELS[i] = Identifier.fromNamespaceAndPath("academy", "block/ip_gen" + i); }
    /** テクスチャごとの描画回数（テスト用）。実際の見え方は画像で判定する。 */
    private static final AtomicLongArray DRAWN = new AtomicLongArray(5);
    public PhaseGeneratorRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.PHASE_GEN_ENTITY.get(), PhaseGeneratorRenderer::new);
    }
    public static long drawn(int level) { return DRAWN.get(level); }
    @Override protected void render(PhaseGeneratorBlockEntity tile, float partialTick, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        int level = PhaseGeneratorBlockEntity.modelLevel(tile.phaseMB());
        pose.pushPose();
        pose.translate(.5, 0, .5);
        ObjModels.draw(MODELS[level], RenderTypes.entityCutout(ObjModels.atlas()), pose, buffers, light, overlay);pose.popPose();
        DRAWN.incrementAndGet(level);
    }
}
