package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 の {@code RenderSystem} の状態と {@code Tesselator} で書いた HUD と画面の描画を、書き方を保ったまま 26.2 の GUI の要素へつなぐ層。
 * 頂点は 2D の変換をしてから覚え、今の GUI の層へ 1 つの要素として足す（描く順序は GUI の層の中で足した順）。
 * 独自のシェーダーの uniform（進み具合、ずれ）は頂点の値（UV1）で渡す。バックエンドに依存しない。
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class GuiImmediate {
    /** 1.21.1 のシェーダーに当たる物。 */
    public enum Shader { TEX_COLOR_CUT, TEX_COLOR_KEEP, COLOR, MONO, CPBAR_CP, CPBAR_OVERLOAD, SKILL_PROGBAR }

    private static RenderPipeline texColor(String location, Identifier fragment, boolean additive) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/" + location))
                .withVertexShader("core/position_tex_color").withFragmentShader(fragment)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(additive ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS).build();
    }
    private static final Identifier CUT_SHADER = Identifier.fromNamespaceAndPath("academy", "core/position_tex_color_cut");
    private static final Identifier KEEP_SHADER = Identifier.withDefaultNamespace("core/position_tex_color");
    /** 1.21.1 の position_tex_color（0.1 未満を捨てる）。 */
    private static final RenderPipeline CUT = texColor("gui_tex_color_cut", CUT_SHADER, false), CUT_ADDITIVE = texColor("gui_tex_color_cut_additive", CUT_SHADER, true);
    /** 1.21.1 の position_tex（0 だけを捨てる）。 */
    private static final RenderPipeline KEEP = texColor("gui_tex_color_keep", KEEP_SHADER, false);
    private static final RenderPipeline COLOR = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/gui_color"))
            .withVertexShader("core/position_color").withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS).build();

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CUT);
        event.registerPipeline(CUT_ADDITIVE);
        event.registerPipeline(KEEP);
        event.registerPipeline(COLOR);
    }

    private static Shader shader = Shader.TEX_COLOR_CUT;
    private static final @Nullable Identifier[] TEXTURES = new Identifier[2];
    private static float red = 1, green = 1, blue = 1, alpha = 1, extra;
    private static boolean additive;

    private GuiImmediate() { }

    /** 試験用: この層の今の状態の写し（1.21.1 の試験が GL から読んだ、描いた後に残る状態に当たる）。読むだけで何も変えない。 */
    public record State(Shader shader, float red, float green, float blue, float alpha, boolean additive, float extra,
                        @Nullable Identifier texture0, @Nullable Identifier texture1) {
        /** テクスチャを除いた写し（テクスチャだけを替えて描いた物を比べる時）。 */
        public State withoutTextures() { return new State(shader, red, green, blue, alpha, additive, extra, null, null); }
    }
    public static State state() { return new State(shader, red, green, blue, alpha, additive, extra, TEXTURES[0], TEXTURES[1]); }

    public static void setShader(Shader value) { shader = value; }
    public static void setShaderTexture(int unit, Identifier value) { TEXTURES[unit] = value; }
    public static void setShaderColor(float r, float g, float b, float a) { red = r; green = g; blue = b; alpha = a; }
    /** 1.21.1 の blendFunc(SRC_ALPHA, ONE)。 */
    public static void additive(boolean value) { additive = value; }
    /** 独自のシェーダーの uniform に当たる値（skill_progbar の Progress、cpbar_overload の TexOffset）。 */
    public static void setExtra(float value) { extra = value; }

    private static RenderPipeline pipeline() {
        return switch (shader) {
            case TEX_COLOR_CUT -> additive ? CUT_ADDITIVE : CUT;
            case TEX_COLOR_KEEP -> KEEP;
            case COLOR -> COLOR;
            case MONO -> AcademyPipelines.GUI_MONO;
            case CPBAR_CP -> AcademyPipelines.GUI_CPBAR_CP;
            case CPBAR_OVERLOAD -> AcademyPipelines.GUI_CPBAR_OVERLOAD;
            case SKILL_PROGBAR -> AcademyPipelines.GUI_SKILL_PROGBAR;
        };
    }

    private static TextureSetup textures() {
        var manager = Minecraft.getInstance().getTextureManager();
        return switch (shader) {
            case COLOR -> TextureSetup.noTexture();
            case CPBAR_CP, CPBAR_OVERLOAD, SKILL_PROGBAR -> {
                var first = manager.getTexture(TEXTURES[0]);
                var second = manager.getTexture(TEXTURES[1]);
                yield TextureSetup.doubleTexture(first.getTextureView(), first.getSampler(), second.getTextureView(), second.getSampler());
            }
            default -> {
                var first = manager.getTexture(TEXTURES[0]);
                yield TextureSetup.singleTexture(first.getTextureView(), first.getSampler());
            }
        };
    }

    /** 画面の座標で切り抜きを始める（1.21.1 の enableScissor。26.2 の enableScissor は今の変換を掛ける）。 */
    public static void absoluteScissor(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.enableScissor(x0, y0, x1, y1);
        graphics.pose().popMatrix();
    }

    public static Vertices begin(GuiGraphicsExtractor graphics) { return new Vertices(graphics); }

    /** 2D の四角形の頂点を覚える。{@link #end()} で今の GUI の層へ足す。 */
    public static final class Vertices {
        private final GuiGraphicsExtractor graphics;
        private float[] data = new float[9 * 8];
        private int size = -9;
        private final Vector2f at = new Vector2f();

        private Vertices(GuiGraphicsExtractor graphics) { this.graphics = graphics; }

        public Vertices addVertex(Matrix3x2fc matrix, float x, float y, float unusedZ) {
            matrix.transformPosition(x, y, at);
            return addVertex(at.x, at.y);
        }
        /** 変換の済んだ位置。 */
        public Vertices addVertex(float x, float y) {
            size += 9;
            if (size + 9 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            data[size] = x; data[size + 1] = y; data[size + 2] = 0; data[size + 3] = 0;
            data[size + 4] = 1; data[size + 5] = 1; data[size + 6] = 1; data[size + 7] = 1; data[size + 8] = extra;
            return this;
        }
        public Vertices setUv(float u, float v) { data[size + 2] = u; data[size + 3] = v; return this; }
        public Vertices setColor(float r, float g, float b, float a) {
            data[size + 4] = r; data[size + 5] = g; data[size + 6] = b; data[size + 7] = a;
            return this;
        }

        public void end() {
            if (size < 0) return;
            int count = size + 9;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < count; i += 9) {
                minX = Math.min(minX, data[i]); maxX = Math.max(maxX, data[i]);
                minY = Math.min(minY, data[i + 1]); maxY = Math.max(maxY, data[i + 1]);
            }
            for (int i = 0; i < count; i += 9) {
                data[i + 4] *= red; data[i + 5] *= green; data[i + 6] *= blue; data[i + 7] *= alpha;
            }
            var scissor = graphics.scissorStack.peek();
            var box = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), Math.max(1, (int) Math.ceil(maxX - minX) + 1), Math.max(1, (int) Math.ceil(maxY - minY) + 1));
            graphics.guiRenderState.addGuiElement(new Element(pipeline(), textures(), java.util.Arrays.copyOf(data, count), shader == Shader.CPBAR_OVERLOAD || shader == Shader.SKILL_PROGBAR,
                    shader != Shader.COLOR, scissor, scissor != null ? scissor.intersection(box) : box));
        }
    }

    /** 今の変換の写し（1.21.1 の {@code graphics.pose().last().pose()} に当たる）。 */
    public static Matrix3x2f matrix(GuiGraphicsExtractor graphics) { return new Matrix3x2f(graphics.pose()); }

    private record Element(RenderPipeline pipeline, TextureSetup textureSetup, float[] vertices, boolean hasExtra, boolean textured,
                           @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < vertices.length; i += 9) {
                consumer.addVertex(vertices[i], vertices[i + 1], 0);
                if (textured) consumer.setUv(vertices[i + 2], vertices[i + 3]);
                consumer.setColor(vertices[i + 4], vertices[i + 5], vertices[i + 6], vertices[i + 7]);
                if (hasExtra) consumer.setUv1((int) (vertices[i + 8] * AcademyPipelines.EXTRA_SCALE), 0);
            }
        }
    }
}
