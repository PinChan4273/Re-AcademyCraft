package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * 演出の形を、提出した順のまま描く。連続する同じ RenderType だけを 1 つの draw にまとめる（重ねる順序は変わらない）。
 * 1.21.1 の {@code endBatch} を呼んだ順に画面へ届く前提（Md の光線の順序など）をここで保つ。バックエンドに依存しない API だけを使う。
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AcademyFeatureRenderer implements FeatureRenderer<AcademyFeatureRenderer.Submit> {
    public static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("Academy");

    /** BatchableSubmit にしない（vanilla の段階が RenderType ごとに組み替えないように）。 */
    public record Submit(PoseStack.Pose pose, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer geometry,
                         @Nullable String blockName, @Nullable Function<Matrix4fc, @Nullable GpuBufferSlice> uniforms) implements SubmitNode {
        @Override public FeatureRendererType<Submit> featureType() { return TYPE; }
    }

    private record Draw(PreparedRenderType prepared, StagedVertexBuffer.Draw vertices, @Nullable String blockName, @Nullable GpuBufferSlice block) { }

    private final List<List<Draw>> groups = new ArrayList<>();

    @SubscribeEvent
    public static void register(RegisterFeatureRenderersEvent event) { event.register(TYPE, new AcademyFeatureRenderer()); }

    @Override
    public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
        var staged = context.stagedVertexBuffer();
        var draws = new ArrayList<Draw>();
        RenderType last = null;
        for (Submit submit : submits) {
            RenderType type = submit.renderType();
            boolean own = submit.uniforms() != null;
            if (own || last != type) {
                var prepared = type.prepare();
                var block = own ? submit.uniforms().apply(RenderSystem.getModelViewMatrixCopy()) : null;
                draws.add(new Draw(prepared, staged.appendDraw(type.format(), type.primitiveTopology(), null), submit.blockName(), block));
            }
            last = own ? null : type;
            submit.geometry().render(submit.pose(), staged.getVertexBuilder(draws.getLast().vertices()));
        }
        groups.add(draws);
    }

    @Override
    public void executeGroup(FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
        for (Draw draw : groups.get(groupIndex)) {
            var info = context.stagedVertexBuffer().getExecuteInfo(draw.vertices());
            if (info == null) continue;
            if (draw.block() == null) draw.prepared().drawFromBuffer(info);
            else drawWithBlock(draw, info);
        }
    }

    /** {@code PreparedRenderType#drawFromBuffer} と同じ手順に、独自の uniform の block を足す。 */
    private static void drawWithBlock(Draw draw, StagedVertexBuffer.ExecuteInfo info) {
        PreparedRenderType prepared = draw.prepared();
        RenderTarget target = prepared.outputTarget().getRenderTarget();
        GpuTextureView color, depth;
        if (ShaderPackDraw.isPlasmaTarget(target)) {
            // シェーダーパックの使用中だけ: Plasma Cannon の本体を専用の描画先へ描く。前の本体の絵を透明に消してから描く（深度は無い）。
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), new org.joml.Vector4f(0, 0, 0, 0));
            color = target.getColorTextureView();
            depth = null;
        } else {
            color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            depth = target.useDepth
                    ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView()) : null;
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Academy draw with " + prepared.pipeline(), color, Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(prepared.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", prepared.dynamicTransforms());
            pass.setUniform(draw.blockName(), draw.block());
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            for (PreparedRenderType.Texture texture : prepared.textures()) pass.bindTexture(texture.name(), texture.textureView(), texture.sampler());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    @Override
    public void finishExecute(FeatureFrameContext context) {
        groups.clear();
        PlasmaUniforms.endFrame();
    }

    @Override
    public void close() { PlasmaUniforms.close(); }
}
