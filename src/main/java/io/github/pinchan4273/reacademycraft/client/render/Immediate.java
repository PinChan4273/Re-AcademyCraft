package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 の {@code RenderSystem} の状態と {@code Tesselator} で書いた演出を、書き方を保ったまま 26.2 の提出へつなぐ層。
 * GL の状態を変えるのではなく、ここへ覚えた値から {@link RenderPipeline} と {@link RenderType} を選び、頂点を {@link WorldDraw} へ提出する。
 * 描画スレッドの提出の間（{@link WorldSubmits}）だけ使う。バックエンドに依存しない。
 */
public final class Immediate {
    /** 1.21.1 のシェーダーに当たる物。 */
    public enum Shader {
        /** 1.21.1 の vanilla の position_tex_color（アルファ 0.1 未満を捨てる）。 */
        TEX_COLOR_CUT,
        /** アルファ 0 だけを捨てる（1.21.1 の position_tex と position_tex_color_keep）。 */
        TEX_COLOR_KEEP,
        /** 1.21.1 の position_color。 */
        COLOR
    }

    private enum Depth { READ_WRITE, READ, NONE }
    private record PipelineKey(Shader shader, boolean blend, boolean additive, Depth depth, boolean cull, PrimitiveTopology topology) { }
    private record TypeKey(PipelineKey pipeline, @Nullable Identifier texture, boolean linearClamp) { }

    private static final Map<PipelineKey, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<TypeKey, RenderType> TYPES = new HashMap<>();
    private static final PoseStack IDENTITY = new PoseStack();

    private static Shader shader = Shader.TEX_COLOR_CUT;
    private static @Nullable Identifier texture;
    /** 試験の記録の間だけの提出先（{@link #record}）。既定は null で、提出は今のフレームの {@link WorldSubmits#current()} へ行く。 */
    private static @Nullable WorldDraw recording;
    private static boolean blend, additive, cull = true, depthTest = true, depthMask = true, linearClamp;
    private static float red = 1, green = 1, blue = 1, alpha = 1;

    private Immediate() { }

    /** 演出ごとの提出の前に、1.21.1 でワールドの描画へ入ってきた時の状態へ戻す。 */
    static void reset() {
        shader = Shader.TEX_COLOR_CUT; texture = null; blend = false; additive = false; cull = true; depthTest = true; depthMask = true; linearClamp = false;
        red = green = blue = alpha = 1;
    }

    /**
     * 試験用: body の間、この層の提出を draw（{@link WorldDraw#recording} で作った物）へ送る。
     * 1.21.1 でワールドの描画へ入ってきた時の状態から始め、終わったらその状態へ戻す。academy.renderProbe が ON の時だけ使える。
     */
    public static void record(WorldDraw draw, Runnable body) {
        WorldDraw.requireProbe();
        reset();
        recording = draw;
        try {
            body.run();
        } finally {
            recording = null;
            reset();
        }
    }

    public static void setShader(Shader value) { shader = value; }
    public static void setShaderTexture(int unit, Identifier value) { if (unit == 0) texture = value; }
    public static void enableBlend() { blend = true; }
    public static void disableBlend() { blend = false; }
    public static void defaultBlendFunc() { additive = false; }
    /** 1.21.1 の {@code blendFunc(SRC_ALPHA, ONE)}（加算。v1.5.0 の未元光線の帯）。{@link #defaultBlendFunc()} で半透明へ戻る。 */
    public static void additiveBlendFunc() { additive = true; }
    public static void enableCull() { cull = true; }
    public static void disableCull() { cull = false; }
    public static void enableDepthTest() { depthTest = true; }
    public static void disableDepthTest() { depthTest = false; }
    public static void depthMask(boolean value) { depthMask = value; }
    public static void setShaderColor(float r, float g, float b, float a) { red = r; green = g; blue = b; alpha = a; }
    /** 1.21.1 の {@code texParameter} の代わり: 次の頂点の列のテクスチャを、線形補間と端の固定で読む。 */
    public static void linearClamp(boolean value) { linearClamp = value; }

    public static Vertices begin(PrimitiveTopology topology, @SuppressWarnings("unused") VertexFormat format) { return new Vertices(topology); }
    public static Vertices begin(PrimitiveTopology topology) { return new Vertices(topology); }

    private static RenderPipeline pipeline(PipelineKey key) {
        return PIPELINES.computeIfAbsent(key, k -> {
            var builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                    .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/immediate_" + k.shader().name().toLowerCase(java.util.Locale.ROOT)
                            + (k.blend() ? k.additive() ? "_additive" : "_blend" : "") + "_" + k.depth().name().toLowerCase(java.util.Locale.ROOT) + (k.cull() ? "_cull" : "")
                            + "_" + k.topology().name().toLowerCase(java.util.Locale.ROOT)))
                    .withColorTargetState(k.blend() ? new ColorTargetState(k.additive() ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
                    .withCull(k.cull())
                    .withPrimitiveTopology(k.topology());
            switch (k.depth()) {
                case READ_WRITE -> builder.withDepthStencilState(DepthStencilState.DEFAULT);
                case READ -> builder.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
                case NONE -> { }
            }
            if (k.shader() == Shader.COLOR) {
                builder.withVertexShader("core/position_color").withFragmentShader("core/position_color")
                        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR);
            } else {
                builder.withVertexShader("core/position_tex_color")
                        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR);
                if (k.shader() == Shader.TEX_COLOR_CUT) builder.withFragmentShader(Identifier.fromNamespaceAndPath("academy", "core/position_tex_color_cut"));
                else builder.withFragmentShader("core/position_tex_color");
            }
            return builder.build();
        });
    }

    private static RenderType type(PrimitiveTopology topology) {
        var pipelineKey = new PipelineKey(shader, blend, blend && additive, !depthTest ? Depth.NONE : depthMask ? Depth.READ_WRITE : Depth.READ, cull, topology);
        var key = new TypeKey(pipelineKey, shader == Shader.COLOR ? null : texture, linearClamp);
        return TYPES.computeIfAbsent(key, k -> {
            var setup = RenderSetup.builder(pipeline(k.pipeline()));
            java.util.function.Supplier<com.mojang.blaze3d.textures.GpuSampler> sampler = k.texture() != null && k.linearClamp()
                    ? () -> com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR) : null;
            if (k.texture() != null) {
                if (sampler != null) setup.withTexture("Sampler0", k.texture(), sampler);
                else setup.withTexture("Sampler0", k.texture());
            }
            // シェーダーパックの間の描き方（ShaderPackDraw）を作れるように、テクスチャと読み方を覚える。パックが無い時の描き方は変わらない。
            return ShaderPackDraw.register(RenderType.create("academy_immediate_" + TYPES.size(), setup.createRenderSetup()), k.texture(), sampler);
        });
    }

    /** 1.21.1 の {@code BufferBuilder} の使う所だけ。頂点は変換してから覚え、{@link #end()} で提出する。 */
    public static final class Vertices {
        private final PrimitiveTopology topology;
        private float[] data = new float[9 * 16];
        private int size = -9;
        private final Vector3f at = new Vector3f();

        private Vertices(PrimitiveTopology topology) { this.topology = topology; }

        public Vertices addVertex(Matrix4fc matrix, float x, float y, float z) {
            matrix.transformPosition(x, y, z, at);
            return addVertex(at.x, at.y, at.z);
        }
        public Vertices addVertex(float x, float y, float z) {
            size += 9;
            if (size + 9 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[size] = x; data[size + 1] = y; data[size + 2] = z;
            data[size + 3] = 0; data[size + 4] = 0;
            data[size + 5] = 1; data[size + 6] = 1; data[size + 7] = 1; data[size + 8] = 1;
            return this;
        }
        public Vertices setUv(float u, float v) { data[size + 3] = u; data[size + 4] = v; return this; }
        public Vertices setColor(float r, float g, float b, float a) {
            data[size + 5] = r; data[size + 6] = g; data[size + 7] = b; data[size + 8] = a;
            return this;
        }
        public Vertices setColor(int r, int g, int b, int a) { return setColor(r / 255f, g / 255f, b / 255f, a / 255f); }
        public Vertices setColor(int argb) { return setColor(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24); }

        /** 1.21.1 の {@code ClientDraw.drawWithShader}。頂点が無ければ何もしない。今のシェーダーの色の掛け算もここで当てる。 */
        public void end() {
            if (size < 0) return;
            int count = size + 9;
            float[] vertices = data;
            float mr = red, mg = green, mb = blue, ma = alpha;
            boolean textured = shader != Shader.COLOR;
            (recording != null ? recording : WorldSubmits.current()).geometry(IDENTITY, type(topology), (last, buffer) -> {
                for (int i = 0; i < count; i += 9) {
                    buffer.addVertex(vertices[i], vertices[i + 1], vertices[i + 2]);
                    if (textured) buffer.setUv(vertices[i + 3], vertices[i + 4]);
                    buffer.setColor(vertices[i + 5] * mr, vertices[i + 6] * mg, vertices[i + 7] * mb, vertices[i + 8] * ma);
                }
            });
        }
    }
}
