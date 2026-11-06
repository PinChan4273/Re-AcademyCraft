package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * この MOD の描画のパイプライン。OpenGL と Vulkan の違いはここより下（vanilla と GLSL の変換）が持つ。
 * 26.3 で {@code com.mojang.blaze3d} のパッケージが動くので、vanilla の描画の API への依存はこの {@code client/render} に集める。
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AcademyPipelines {
    /** 深度は読むだけ（1.21.1 の LEQUAL と深度マスク OFF。26.2 は深度が反転なので GREATER_THAN_OR_EQUAL）。 */
    private static final DepthStencilState READ_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    /** {@code academy_ui.vsh} の、頂点ごとの値の倍率（UV1 の x は符号付きの 16 ビットで届く）。 */
    public static final float EXTRA_SCALE = 30000f;
    /** 位置・UV・色に、頂点ごとの値（UV1）を足した形。 */
    public static final VertexFormat POSITION_TEX_COLOR_EXTRA = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .build();

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", path); }

    private static RenderPipeline.Builder world(String location) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(id("pipeline/" + location))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(READ_ONLY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    /**
     * テクスチャと色、半透明、深度は読むだけ、裏面も描く。シェーダーは vanilla（完全に透明な画素だけを捨てる）。
     * 1.21.1 の position_tex（Md の球と光の帯）と position_tex_color_keep（竜巻など）に当たる。
     */
    public static final RenderPipeline TEX_COLOR_KEEP = world("tex_color_keep")
            .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0).withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).build();

    /** 上と同じで、1.21.1 の vanilla の position_tex_color と同じくアルファ 0.1 未満を捨てる（電弧）。 */
    public static final RenderPipeline TEX_COLOR_CUT = world("tex_color_cut")
            .withVertexShader("core/position_tex_color").withFragmentShader(id("core/position_tex_color_cut"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0).withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).build();

    /** Md の円柱: 色だけ、半透明、深度は読むだけ、表面だけ（光線の中にいる術者に内側が見えないように）。 */
    public static final RenderPipeline COLOR_CULLED = world("color_culled")
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR).build();

    /** Plasma Cannon の本体の uniform の block の名前（{@code plasma_body.fsh}）。 */
    public static final String PLASMA_BLOCK = "PlasmaBody";
    /** Plasma Cannon の本体: 球の配列を std140 の block で渡す metaball。 */
    public static final RenderPipeline PLASMA_BODY = world("plasma_body")
            .withVertexShader(id("core/plasma_body")).withFragmentShader(id("core/plasma_body"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform(PLASMA_BLOCK, UniformType.UNIFORM_BUFFER).build())
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION).build();

    /** 粒子: vanilla の particle で、完全に透明な画素だけを捨てる（原作の煙など）。 */
    public static final RenderPipeline PARTICLE_KEEP = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(id("pipeline/particle_keep"))
            .withFragmentShader(id("core/particle_keep"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    /**
     * 1.21.1 の {@code RenderType.eyes} と同じ描き方: 絵を陰影なし・最大の明るさで、加算（ONE, ONE）で足す。深度は読むだけ、表面だけ。
     * 26.2 の vanilla の eyes は半透明（SRC_ALPHA, ONE_MINUS_SRC_ALPHA）で混ぜるので、白い絵を薄い色で足す層（甲虫・六翼の発光）に使うと、
     * 下の面をその薄い灰色で塗りつぶす。頂点シェーダーは vanilla の core/entity、霧は 1.21.1 と同じく遠いほど薄める。
     */
    public static final RenderPipeline EYES_ADDITIVE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(id("pipeline/eyes_additive"))
            .withVertexShader("core/entity").withFragmentShader(id("core/eyes_additive"))
            .withShaderDefine("EMISSIVE").withShaderDefine("NO_OVERLAY").withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(READ_ONLY)
            .build();
    private static final Function<Identifier, RenderType> EYES_ADDITIVE_TYPES = Util.memoize(texture -> RenderType.create("academy_eyes_additive",
            RenderSetup.builder(EYES_ADDITIVE).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));

    /**
     * 1.21.1 の {@code RenderType.eyes(texture)} に当たる描き方（{@link #EYES_ADDITIVE}）。
     * シェーダーパックの使用中だけ、vanilla の EYES の写しで加算にした物（{@link ShaderPackDraw#eyesAdditive}。Iris が画面へ届ける）を返す。
     */
    public static RenderType eyesAdditive(Identifier texture) {
        return io.github.pinchan4273.reacademycraft.client.ShaderPackCompat.inUse() ? ShaderPackDraw.eyesAdditive(texture) : EYES_ADDITIVE_TYPES.apply(texture);
    }

    private static RenderPipeline.Builder gui(String location) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(id("pipeline/" + location))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS);
    }

    /** 未習得の技能を灰色で描く（LambdaLib2 の ShaderMono）。 */
    public static final RenderPipeline GUI_MONO = gui("gui_mono")
            .withVertexShader("core/position_tex_color").withFragmentShader(id("core/mono"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).build();

    /** CP の棒（系統のアイコンの形を抜く）。Sampler1 がアイコン。 */
    public static final RenderPipeline GUI_CPBAR_CP = gui("gui_cpbar_cp")
            .withVertexShader("core/position_tex_color").withFragmentShader(id("core/cpbar_cp"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR).build();

    /** 過負荷の棒。頂点ごとの値がテクスチャのずれ。Sampler1 がマスク。 */
    public static final RenderPipeline GUI_CPBAR_OVERLOAD = gui("gui_cpbar_overload")
            .withVertexShader(id("core/academy_ui")).withFragmentShader(id("core/cpbar_overload"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withVertexBinding(0, POSITION_TEX_COLOR_EXTRA).build();

    /** 開発機の技能の環。頂点ごとの値が進み具合。Sampler1 が放射のマスク。 */
    public static final RenderPipeline GUI_SKILL_PROGBAR = gui("gui_skill_progbar")
            .withVertexShader(id("core/academy_ui")).withFragmentShader(id("core/skill_progbar"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withVertexBinding(0, POSITION_TEX_COLOR_EXTRA).build();

    private AcademyPipelines() { }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(TEX_COLOR_KEEP);
        event.registerPipeline(TEX_COLOR_CUT);
        event.registerPipeline(COLOR_CULLED);
        event.registerPipeline(PLASMA_BODY);
        event.registerPipeline(PARTICLE_KEEP);
        event.registerPipeline(EYES_ADDITIVE);
        event.registerPipeline(GUI_MONO);
        event.registerPipeline(GUI_CPBAR_CP);
        event.registerPipeline(GUI_CPBAR_OVERLOAD);
        event.registerPipeline(GUI_SKILL_PROGBAR);
    }
}
