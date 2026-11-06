package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 1.21.1 の {@code MultiBufferSource} の形で書いた描画（{@code ModelPart#render} や頂点を自分で書く物）を、26.2 の提出へつなぐ。
 * 頂点は書いた時点の pose で変換済みの値を覚え（{@code ModelPart} の回転などは描く時ではなく書いた時の値になる）、
 * {@link #submit(SubmitNodeCollector)} で RenderType ごとに、最初に使った順で提出する。描く時の pose は単位行列で、覚えた値をそのまま渡す。
 * <p>
 * 26.2 の提出は同じフレームの後で描かれる。同じ {@code ModelPart} を1つの絵の中で何度も動かして描く物（翼の羽など）を、
 * {@code submitModelPart} のように部品のまま渡すと、描く時の最後の姿勢で全部が描かれるので、ここで頂点にしてから渡す。
 */
public final class CapturedBuffers {
    private static final PoseStack IDENTITY = new PoseStack();
    private final Map<RenderType, Recorder> buffers = new LinkedHashMap<>();

    /** 1.21.1 の {@code MultiBufferSource#getBuffer}。 */
    public VertexConsumer getBuffer(RenderType type) { return buffers.computeIfAbsent(type, t -> new Recorder()); }

    /**
     * 覚えた頂点を、エンティティの提出へ渡す。RenderType ごとに、最初に使った順の番号の order（{@link SubmitNodeCollector#order}）へ入れ、
     * 1.21.1 と同じく書いた順に描かせる（面の後に、同じ絵を足して光らせる層など）。
     * <p>
     * vanilla 26.2 は、同じ order の半透明の {@code submitCustomGeometry} を {@code SubmitNodeCollection#translucentCustomGeometry}
     * （{@code SimpleFeatureRenderPhase}）へ集め、その中で RenderType を鍵にした {@code HashMap}（{@code FeatureSubmits#batches}）で
     * まとめ、その並びの順に描く。RenderType は {@code hashCode} を持たない（同一性のハッシュ）ので、この順は起動ごとに変わる。
     * 同じ order のままでは、足す光（{@code AcademyPipelines#eyesAdditive}）が面より先に描かれる起動があり、後から描いた半透明の面が
     * 光を塗りつぶして翼が灰色に見えた（26.2 だけ）。order は {@code SubmitNodeStorage} が番号の小さい順に描く（vanilla の
     * {@code EyesLayer} も光る層を {@code order(1)} へ入れる）。
     */
    public void submit(SubmitNodeCollector collector) {
        int order = 0;
        for (var entry : buffers.entrySet()) {
            var recorder = entry.getValue();
            if (recorder.count > 0) collector.order(order++).submitCustomGeometry(IDENTITY, entry.getKey(), recorder.replay());
        }
        buffers.clear();
    }

    /** 覚えた頂点を、このMODの演出の提出（{@link WorldDraw}。提出した順に描く）へ渡す。 */
    public void submit(WorldDraw draw) {
        for (var entry : buffers.entrySet()) {
            var recorder = entry.getValue();
            if (recorder.count > 0) draw.geometry(IDENTITY, entry.getKey(), recorder.replay());
        }
        buffers.clear();
    }

    /** 頂点の値を覚える書き手。小数は 位置3・UV2・法線3 の8個、整数は 色・重ね色・明るさ の3個。 */
    private static final class Recorder implements VertexConsumer {
        private float[] floats = new float[8 * 64];
        private int[] ints = new int[3 * 64];
        private int count;
        private int f = -8, i = -3;

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            f += 8; i += 3; count++;
            if (f + 8 > floats.length) { floats = Arrays.copyOf(floats, floats.length * 2); ints = Arrays.copyOf(ints, ints.length * 2); }
            floats[f] = x; floats[f + 1] = y; floats[f + 2] = z; floats[f + 3] = 0; floats[f + 4] = 0;
            floats[f + 5] = 0; floats[f + 6] = 1; floats[f + 7] = 0;
            ints[i] = -1; ints[i + 1] = 0; ints[i + 2] = 0;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { ints[i] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255; return this; }
        @Override public VertexConsumer setColor(int color) { ints[i] = color; return this; }
        @Override public VertexConsumer setUv(float u, float v) { floats[f + 3] = u; floats[f + 4] = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { ints[i + 1] = u & 0xFFFF | v << 16; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { ints[i + 2] = u & 0xFFFF | v << 16; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { floats[f + 5] = x; floats[f + 6] = y; floats[f + 7] = z; return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }

        SubmitNodeCollector.CustomGeometryRenderer replay() {
            float[] fs = Arrays.copyOf(floats, count * 8);
            int[] is = Arrays.copyOf(ints, count * 3);
            int vertexCount = count;
            floats = new float[8 * 64]; ints = new int[3 * 64]; count = 0; f = -8; i = -3;
            return (pose, buffer) -> {
                for (int v = 0; v < vertexCount; v++) {
                    int a = v * 8, b = v * 3;
                    buffer.addVertex(fs[a], fs[a + 1], fs[a + 2], is[b], fs[a + 3], fs[a + 4], is[b + 1], is[b + 2], fs[a + 5], fs[a + 6], fs[a + 7]);
                }
            };
        }
    }

    /** 試験用: 今覚えている RenderType の並び。 */
    public List<RenderType> types() { return new ArrayList<>(buffers.keySet()); }
}
