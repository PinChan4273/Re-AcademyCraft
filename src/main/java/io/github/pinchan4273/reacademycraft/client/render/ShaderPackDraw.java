package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * シェーダーパックの使用中（{@code ShaderPackCompat#inUse()}）だけ使う描き方（NeoForge 1.21.1 版の、パックの間だけ Minecraft のシェーダーで描く
 * RenderType に当たる物）。パックが無い時は、ここで作った物はどれも使われず、描き方は変わらない。
 *
 * <p>Iris 1.11.4 はパックの間、Iris の知らないプログラムの描画を画面へ届かない描画先へ向ける。Iris が知っているのは vanilla の決まった
 * パイプラインと、{@link RenderPipeline#toBuilder()} で作ったその写し。そこでパックの間は、vanilla のパイプラインの写し（シェーダーは vanilla のまま。
 * Iris がパックのプログラムへ置き換える）で、合成・深度・カリングだけを元の演出と同じにして描く（1.21.1 の {@code *_PACK} の RenderType が、
 * Minecraft のシェーダーに元の演出の状態を付けたのと同じ考え）。
 * <ul>
 * <li>テクスチャと色の演出・色だけの演出: vanilla の {@code ENTITY_TRANSLUCENT_EMISSIVE}（光の影響なしの半透明）の写し。頂点は ENTITY の形へ
 *     書き直す（重ね色なし、最大の明るさ、上向きの法線）。色だけの物は白い 1 画素のテクスチャを貼る。三角形は 4 つ目を 3 つ目と同じにした四角形にする。</li>
 * <li>線: vanilla の {@code LINES} の写し（頂点の形は同じ）。</li>
 * <li>甲虫・六翼の光る層（{@link AcademyPipelines#eyesAdditive}）: vanilla の {@code EYES} の写しで、合成を加算にした物（1.21.1 の eyes と同じ合成）。</li>
 * <li>Plasma Cannon の本体: 独自のシェーダーで専用の描画先へ描き、その絵を上の写しで世界へ貼る（1.21.1 の packComposite と同じ考え）。</li>
 * </ul>
 * 特定のパックや MOD の名前では分けない。描画スレッドだけで使う。
 */
public final class ShaderPackDraw {
    /** 元の演出の頂点の形。 */
    private enum Kind { TEXTURED, COLOR, LINES }
    private record Source(Kind kind, @Nullable Identifier texture, @Nullable Supplier<GpuSampler> sampler) { }
    /** パックの間の描き方。convert は頂点を ENTITY の形へ書き直すか、triangles は三角形を四角形にするか。 */
    record Pack(RenderType type, boolean convert, boolean triangles) {
        /** 元の演出の頂点を書く処理を、この描き方の頂点の形で書く処理にする。 */
        SubmitNodeCollector.CustomGeometryRenderer wrap(SubmitNodeCollector.CustomGeometryRenderer geometry) {
            converted++;
            if (!convert) return geometry;
            boolean quads = triangles;
            return (last, buffer) -> {
                var out = new EntityVertices(buffer, quads);
                geometry.render(last, out);
                out.finish();
            };
        }
    }
    private record PipelineKey(RenderPipeline base, @Nullable ColorTargetState color, Optional<com.mojang.blaze3d.pipeline.DepthStencilState> depth,
                               boolean cull, PrimitiveTopology topology) { }

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("academy", "shader_pack/white");
    private static final Identifier PLASMA = Identifier.fromNamespaceAndPath("academy", "shader_pack/plasma_body");
    /** Plasma Cannon の本体を描く専用の描画先の一辺（画素）。本体の四角の全体をこの絵に描く。 */
    private static final int PLASMA_SIZE = 512;

    private static final Map<RenderType, Source> SOURCES = new IdentityHashMap<>();
    private static final Map<RenderType, Pack> PACKS = new IdentityHashMap<>();
    private static final Map<PipelineKey, RenderPipeline> PIPELINES = new java.util.HashMap<>();
    private static final Function<Identifier, RenderType> EYES_ADDITIVE = Util.memoize(texture -> RenderType.create("academy_shader_pack_eyes_additive",
            RenderSetup.builder(RenderPipelines.EYES.toBuilder().withLocation(id("pipeline/shader_pack/eyes_additive"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE)).build())
                    .withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));
    private static boolean white;
    private static @Nullable TextureTarget plasmaTarget;
    private static @Nullable RenderType plasmaBody, plasmaComposite;
    private static long converted;

    private ShaderPackDraw() { }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", path); }

    /**
     * 位置・テクスチャ・色（texture が null なら位置・色）の頂点で描く演出の RenderType を、パックの間の描き方を作れるように覚える。
     * 戻り値は渡した物そのもの（覚えるだけで、パックが無い時の描き方は変わらない）。
     *
     * @param sampler テクスチャの読み方（null ならテクスチャの既定）。元の RenderType と同じ物を渡す。
     */
    public static RenderType register(RenderType type, @Nullable Identifier texture, @Nullable Supplier<GpuSampler> sampler) {
        SOURCES.put(type, new Source(texture == null ? Kind.COLOR : Kind.TEXTURED, texture, sampler));
        return type;
    }
    public static RenderType register(RenderType type, @Nullable Identifier texture) { return register(type, texture, null); }
    /** 線（vanilla の線と同じ頂点の形）で描く演出の RenderType を覚える。 */
    public static RenderType registerLines(RenderType type) {
        SOURCES.put(type, new Source(Kind.LINES, null, null));
        return type;
    }

    /** 試験と記録用: パックの間の描き方へ書き直して提出した数。 */
    public static long converted() { return converted; }

    /**
     * パックの間の描き方。覚えていない RenderType なら null（そのまま提出する）。{@link WorldDraw} が、シェーダーパックの使用中だけ呼ぶ。
     */
    static @Nullable Pack pack(RenderType original) {
        var known = PACKS.get(original);
        if (known != null) return known;
        var source = SOURCES.get(original);
        if (source == null) return null;
        RenderPipeline from = original.pipeline();
        PrimitiveTopology topology = from.getPrimitiveTopology();
        boolean triangles = topology == PrimitiveTopology.TRIANGLES;
        Pack pack;
        if (source.kind() == Kind.LINES) {
            var pipeline = pipeline(RenderPipelines.LINES, from, topology);
            pack = new Pack(RenderType.create("academy_shader_pack_lines", RenderSetup.builder(pipeline).createRenderSetup()), false, false);
        } else {
            var pipeline = pipeline(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, from, PrimitiveTopology.QUADS);
            Identifier texture = source.texture();
            if (texture == null) texture = white();
            var setup = RenderSetup.builder(pipeline).useOverlay();
            if (source.sampler() != null) setup.withTexture("Sampler0", texture, source.sampler());
            else setup.withTexture("Sampler0", texture);
            pack = new Pack(RenderType.create("academy_shader_pack_" + original, setup.createRenderSetup()), true, triangles);
        }
        PACKS.put(original, pack);
        return pack;
    }

    /** vanilla の base の写しに、元の演出の合成・深度・カリング（と形）を付けたパイプライン。Iris は写しを base と同じパックのプログラムで描く。 */
    private static RenderPipeline pipeline(RenderPipeline base, RenderPipeline from, PrimitiveTopology topology) {
        var key = new PipelineKey(base, from.getColorTargetState(), Optional.ofNullable(from.getDepthStencilState()), from.isCull(), topology);
        return PIPELINES.computeIfAbsent(key, k -> {
            var builder = k.base().toBuilder()
                    .withLocation(id("pipeline/shader_pack/" + from.getLocation().getPath().replace("pipeline/", "") + "_" + PIPELINES.size()))
                    .withDepthStencilState(k.depth())
                    .withCull(k.cull())
                    .withPrimitiveTopology(k.topology());
            if (k.color() != null) builder.withColorTargetState(k.color());
            return builder.build();
        });
    }

    /** 色だけの演出に貼る白い 1 画素。 */
    private static Identifier white() {
        if (!white) {
            var image = new NativeImage(1, 1, false);
            image.setPixel(0, 0, 0xFFFFFFFF);
            Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(WHITE::toString, image));
            white = true;
        }
        return WHITE;
    }

    /** 1.21.1 の {@code RenderType.eyes} に当たる光る層の、パックの間の描き方（vanilla の EYES の写し、加算）。 */
    public static RenderType eyesAdditive(Identifier texture) { return EYES_ADDITIVE.apply(texture); }

    /**
     * Plasma Cannon の本体を専用の描画先へ描く RenderType（独自のシェーダー {@code plasma_body} を {@code ACADEMY_FILL_TARGET} で使い、
     * 本体の四角の全体を描画先いっぱいに描く。頂点は位置と、四角の中の位置（UV、0〜1））。描く前に描画先を透明に消す。
     * 描画先は Iris の知らないプログラムの描画先のまま（Iris は、その描画が向かう描画先を変えない）。
     */
    public static RenderType plasmaBody() {
        if (plasmaBody == null) {
            var pipeline = AcademyPipelines.PLASMA_BODY.toBuilder()
                    .withLocation(id("pipeline/shader_pack/plasma_body_fill"))
                    .withShaderDefine("ACADEMY_FILL_TARGET")
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withDepthStencilState(Optional.empty())
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                    .build();
            plasmaBody = RenderType.create("academy_shader_pack_plasma_body",
                    RenderSetup.builder(pipeline).setOutputTarget(new OutputTarget("academy_plasma_body", ShaderPackDraw::plasmaTarget)).createRenderSetup());
        }
        return plasmaBody;
    }

    /** {@link #plasmaBody()} の描画先か。 */
    static boolean isPlasmaTarget(RenderTarget target) { return target == plasmaTarget; }

    private static RenderTarget plasmaTarget() {
        if (plasmaTarget == null) {
            plasmaTarget = new TextureTarget("academy plasma body for shader packs", PLASMA_SIZE, PLASMA_SIZE, false, GpuFormat.RGBA8_UNORM);
            Minecraft.getInstance().getTextureManager().register(PLASMA, new TargetTexture(plasmaTarget));
        }
        return plasmaTarget;
    }

    /**
     * 専用の描画先の絵を、本体の四角と同じ所へ貼る RenderType（vanilla の {@code ENTITY_TRANSLUCENT_EMISSIVE} の写し。半透明、深度は読むだけ、裏も描く。
     * 頂点は ENTITY の形）。絵は本体の四角の中の位置をそのまま UV にしたものなので、四角の各点に、直に描いた時と同じ色が載る。
     */
    public static RenderType plasmaComposite() {
        if (plasmaComposite == null) {
            plasmaTarget();
            var pipeline = RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE.toBuilder()
                    .withLocation(id("pipeline/shader_pack/plasma_composite"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .build();
            plasmaComposite = RenderType.create("academy_shader_pack_plasma_composite", RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", PLASMA, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)).useOverlay().createRenderSetup());
        }
        return plasmaComposite;
    }

    /** ENTITY の形の頂点を 1 つ書く（重ね色なし、最大の明るさ、上向きの法線）。 */
    public static void entityVertex(VertexConsumer out, float x, float y, float z, int argb, float u, float v) {
        out.addVertex(x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    /** 描画先の色のテクスチャを、テクスチャの管理へ見せる（持ち主は描画先なので、ここでは閉じない）。 */
    private static final class TargetTexture extends AbstractTexture {
        TargetTexture(RenderTarget target) {
            texture = target.getColorTexture();
            textureView = target.getColorTextureView();
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
        @Override public void close() { texture = null; textureView = null; }
    }

    /**
     * 位置・テクスチャ・色（または位置・色）で書く演出の頂点を、ENTITY の形へ書き直す。頂点は次の頂点の初めか {@link #finish()} で書く。
     * 三角形の時は、3 つずつを 4 つ目を 3 つ目と同じにした四角形にする。
     */
    private static final class EntityVertices implements VertexConsumer {
        private final VertexConsumer out;
        private final boolean triangles;
        private boolean pending;
        private float x, y, z, u, v;
        private int color = -1;
        private int inTriangle;

        EntityVertices(VertexConsumer out, boolean triangles) { this.out = out; this.triangles = triangles; }

        private void flush() {
            if (!pending) return;
            pending = false;
            entityVertex(out, x, y, z, color, u, v);
            if (triangles && ++inTriangle == 3) {
                entityVertex(out, x, y, z, color, u, v);
                inTriangle = 0;
            }
        }
        void finish() { flush(); }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            pending = true;
            this.x = x; this.y = y; this.z = z; u = 0; v = 0; color = -1;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255; return this; }
        @Override public VertexConsumer setColor(int argb) { color = argb; return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
