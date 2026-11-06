package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * 演出の提出先。1.21.1 の {@code endBatch} と同じく、提出した順に画面へ届く（{@link AcademyFeatureRenderer}）。
 * vanilla の {@code submitCustomGeometry} は同じ RenderType をまとめて順序を変え、水より前の段階へしか入れられないので使わない。
 * <p>
 * 試験だけの記録（{@link #recording}、{@link #listen}）は、system property {@code academy.renderProbe} が true の時だけ使える。
 * 既定は OFF で、その時は提出の扱いは何も変わらない。
 */
public final class WorldDraw {
    /** 試験の記録を置けるか（{@code -Dacademy.renderProbe=true}。client smoke の時だけ build.gradle が付ける）。 */
    public static final boolean PROBE = Boolean.getBoolean("academy.renderProbe");
    private static @Nullable BiConsumer<WorldDraw, AcademyFeatureRenderer.Submit> listener;

    private final @Nullable FeatureRenderPhase<SubmitNode> phase;
    private final @Nullable Consumer<AcademyFeatureRenderer.Submit> sink;

    WorldDraw(FeatureRenderPhase<SubmitNode> phase) { this.phase = phase; this.sink = null; }
    private WorldDraw(Consumer<AcademyFeatureRenderer.Submit> sink) { this.phase = null; this.sink = sink; }

    /**
     * 試験用: 画面へ送らず、提出（RenderType、pose、頂点を書く処理、独自の uniform）を提出した順に sink へ渡す提出先。
     * 試験はそれを自分の絵へ同じパイプライン・テクスチャ・サンプラーで描く（1.21.1 の画面外の描画に当たる）。
     */
    public static WorldDraw recording(Consumer<AcademyFeatureRenderer.Submit> sink) {
        requireProbe();
        return new WorldDraw(sink);
    }

    /** 試験用: 実際のフレームの提出を、画面へ描く順のまま、提出先（フレームと段階ごとに別の物）と共に聞く（null で外す）。提出そのものは変えない。 */
    public static void listen(@Nullable BiConsumer<WorldDraw, AcademyFeatureRenderer.Submit> value) {
        if (value != null) requireProbe();
        listener = value;
    }

    static void requireProbe() {
        if (!PROBE) throw new IllegalStateException("The render probe is a test hook: run with -Dacademy.renderProbe=true");
    }

    /**
     * 今の pose で形を提出する。シェーダーパックの使用中だけ、{@link ShaderPackDraw} が覚えている RenderType を、パックの間の描き方
     * （vanilla のパイプラインの写し）へ替えて提出する。パックが無い時は何も替えない。
     */
    public void geometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
        if (io.github.pinchan4273.reacademycraft.client.ShaderPackCompat.inUse()) {
            var pack = ShaderPackDraw.pack(type);
            if (pack != null) {
                type = pack.type();
                geometry = pack.wrap(geometry);
            }
        }
        submit(new AcademyFeatureRenderer.Submit(pose.last().copy(), type, geometry, null, null));
    }

    /**
     * 独自の uniform の block を持つ形を提出する。
     *
     * @param uniforms 準備の段階で 1 回呼ぶ。引数は描く時の model-view。戻り値を block として結ぶ。
     */
    public void geometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry,
                         String blockName, Function<Matrix4fc, @Nullable GpuBufferSlice> uniforms) {
        submit(new AcademyFeatureRenderer.Submit(pose.last().copy(), type, geometry, blockName, uniforms));
    }

    private void submit(AcademyFeatureRenderer.Submit submit) {
        if (sink != null) {
            sink.accept(submit);
            return;
        }
        var heard = listener;
        if (heard != null) heard.accept(this, submit);
        phase.submit(submit);
    }
}
