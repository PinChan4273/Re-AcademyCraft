package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyMdBallAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyMdRayAnimation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 原作のElectron Bombの球のbillboardとRendererRayCompositeの光線（7b1401c）を、Forgeで描くためのadapter。
 * 渡されたposeを通してmodel空間の形を描き、プレイヤーやワールドの状態は読まない。
 *
 * 原作は、球をShaderSimpleで深度マスクOFFで描き、光線を3つの入れ子の円柱とカメラへ向いた光の帯で描く（どれも深度マスクOFF）。
 */
public final class LegacyMdRenderer {
    public static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/mdball/glow.png");
    /** 光線の光の帯は3つの部分: 端、敷き詰めた中央、端。原作の配置と同じ。 */
    public static final ResourceLocation BEAM_BLEND_IN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/mdray_small/blend_in.png");
    public static final ResourceLocation BEAM_TILE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/mdray_small/tile.png");
    public static final ResourceLocation BEAM_BLEND_OUT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/mdray_small/blend_out.png");
    private static final ResourceLocation[] CORES = new ResourceLocation[LegacyMdBallAnimation.TEXTURE_COUNT];
    static {
        for (int i = 0; i < CORES.length; i++)
            CORES[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdball/" + i + ".png");
    }
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(2048));
    /** 原作RendererRayCylinder.DIV: 円柱1つにつき12面。 */
    private static final int SIDES = 12;

    private static final class Types extends RenderType {
        private Types() { super("academy_unused_md", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> { }, () -> { }); }
        private static RenderType sprite(String name, ResourceLocation texture) {
            return create(name, DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder().setShaderState(new ShaderStateShard(
                                    net.minecraft.client.renderer.GameRenderer::getPositionTexShader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
        /**
         * 原作の円柱は、ワールド自身のculling（原作は切らない）で描くので、管の外側しか見えない。
         * 両面で描くと、目が管の中にある人には内側が見える。光線は術者自身の頭から出るので、術者には視界全体に緑の多角形が見えてしまう。
         */
        private static final RenderType BEAM = create("academy_legacy_md_beam", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        private static final RenderType GLOW_SPRITE = sprite("academy_legacy_md_glow", GLOW);
        /** 光線の種類ごと: blend in・tile・blend out。それぞれの種類のテクスチャのフォルダから。 */
        private static final RenderType[][] BEAM_GLOW = new RenderType[LegacyMdRayAnimation.Profile.values().length][];
        static {
            for (var profile : LegacyMdRayAnimation.Profile.values()) {
                String prefix = "academy_legacy_md_beam_" + profile.name().toLowerCase(java.util.Locale.ROOT);
                BEAM_GLOW[profile.ordinal()] = new RenderType[] {
                        sprite(prefix + "_in", rayTexture(profile, "blend_in")),
                        sprite(prefix + "_tile", rayTexture(profile, "tile")),
                        sprite(prefix + "_out", rayTexture(profile, "blend_out")) };
            }
        }
        private static final RenderType[] CORE_SPRITES = new RenderType[CORES.length];
        static {
            for (int i = 0; i < CORES.length; i++) CORE_SPRITES[i] = sprite("academy_legacy_md_core_" + i, CORES[i]);
        }
    }

    private LegacyMdRenderer() { }

    /**
     * テスト用の入口: sinkを設定すると、光の帯・内側の円柱・外側の円柱を描くたびに、"G"・"I"・"O"とその光線の番号を、
     * 画面へ届いた順に追記する。通常のプレイではnull。
     */
    private static StringBuilder trace;
    private static int traceRay;
    public static void trace(StringBuilder sink) { trace = sink; }
    static void traceRay(int ray) { traceRay = ray; }
    static void traceFrame() { if (trace != null) trace.setLength(0); }
    private static void traced(char part) { if (trace != null) trace.append(part).append(traceRay).append(' '); }

    public static ResourceLocation rayTexture(LegacyMdRayAnimation.Profile profile, String part) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + profile.texture + "/" + part + ".png");
    }

    /** poseの原点に、カメラへ向いた2枚のbillboardを描く。原作と同じく、光を芯の後ろに重ねる。 */
    public static int drawOrb(PoseStack pose, LegacyMdBallAnimation.Snapshot orb) {
        RenderSystem.assertOnRenderThread();
        if (orb.texture() < 0 || orb.texture() >= CORES.length) throw new IllegalArgumentException("Invalid orb texture");
        var state = State.capture();
        int drawn = 0;
        try {
            RenderSystem.depthMask(false);
            drawn += billboard(pose, Types.GLOW_SPRITE, orb.glowSize(), orb.glowAlpha());
            drawn += billboard(pose, Types.CORE_SPRITES[orb.texture()], orb.coreSize(), orb.coreAlpha());
        } finally { state.restore(); }
        return drawn;
    }

    private static int billboard(PoseStack pose, RenderType type, float size, float alpha) {
        if (!Float.isFinite(size) || size <= 0 || size > 4 || !Float.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Invalid orb billboard");
        if (alpha <= 0) return 0;
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        // LambdaLib2のRenderIconは、四角を大きさの-0.25から0.75まで画面の上方向に置く: 絵はentityから大きさの4分の1上に来る。
        var buffer = BUFFERS.getBuffer(type); var matrix = pose.last().pose(); float half = size / 2, bottom = -size / 4, top = size * .75f;
        buffer.vertex(matrix, -half, bottom, 0).uv(0, 1).endVertex();
        buffer.vertex(matrix, half, bottom, 0).uv(1, 1).endVertex();
        buffer.vertex(matrix, half, top, 0).uv(1, 0).endVertex();
        buffer.vertex(matrix, -half, top, 0).uv(0, 0).endVertex();
        BUFFERS.endBatch(type);
        return 1;
    }

    /**
     * poseの原点から+X方向の光線を、原作の内側と外側の円柱で描く。呼び出し側がposeの向きを既に決めているので、
     * ここではアニメーションが与える長さだけを使う。
     */
    public static int drawBeam(PoseStack pose, LegacyMdRayAnimation.Snapshot beam, LegacyMdRayAnimation.Profile profile) {
        RenderSystem.assertOnRenderThread();
        // 幅の揺れで、幅は光線ごとの揺れの半径だけ1を超えることがある。EntityRayBaseのままの光線は0.1、Railgunは0.3。
        if (!Double.isFinite(beam.length()) || beam.length() < 0 || beam.length() > 64
                || !Double.isFinite(beam.width()) || beam.width() < 0 || beam.width() > 1 + profile.wiggleRadius
                || !Double.isFinite(beam.alpha()) || beam.alpha() < 0 || beam.alpha() > 1)
            throw new IllegalArgumentException("Invalid beam sample");
        if (beam.length() <= 0 || beam.alpha() <= 0 || beam.width() <= 0) return 0;
        var state = State.capture();
        int drawn = 0;
        try {
            RenderSystem.depthMask(false);
            // 原作RendererRayCompositeは、光の帯・cylinderIn・cylinderOutの順に加え、RendererListはその順に描く。
            // そのため、淡い外側の鞘が明るい内側の芯の上に合成され、芯に色が付く。
            drawn += cylinder(pose, beam, profile.innerWidth, profile.inner[0], profile.inner[1], profile.inner[2], profile.inner[3], .98);
            drawn += cylinder(pose, beam, profile.outerWidth, profile.outer[0], profile.outer[1], profile.outer[2], profile.outer[3], 1);
        } finally { state.restore(); }
        return drawn;
    }

    /**
     * 原作の光は円柱ではなく、カメラへ向いた3つの部分の帯: 設定の幅の端、敷き詰めた中央、端。色は白で、緑は原作の絵そのものにある。
     * そのため、ここで変えるのはalphaだけ: 描画の色のalpha×getAlpha()×getGlowAlpha()。getGlowAlpha()自体は
     * (0.9 + 光の揺れ)×getAlpha()。
     *
     * カメラへ向くべき帯は光線自身の回転からは作れないので、光線の方向と横の軸は、呼び出し側がposeと同じ空間で渡す。
     */
    public static int drawBeamGlow(PoseStack pose, LegacyMdRayAnimation.Snapshot beam, LegacyMdRayAnimation.Profile profile,
                                   org.joml.Vector3f direction, org.joml.Vector3f side) {
        RenderSystem.assertOnRenderThread();
        if (beam.length() <= 0 || beam.alpha() <= 0 || beam.width() <= 0) return 0;
        float cap = (float) profile.glowWidth;
        // 原作RendererRayGlow: width = this.width * ray.getWidth()。drawBoardがそれを半分にしてから、単位の上向きのベクトルの両側に
        // 帯を置く。そのため、帯の幅はglowWidth * widthで、その2倍ではない（長さglowWidthの端は、128×128の絵と同じく正方形）。
        float half = (float) (profile.glowWidth * beam.width() / 2);
        // 原作は、3つの部分を置く前に、帯の両端を光線に沿って光線自身の補正だけずらす。
        // そのため光は、円柱より後ろから始まり、円柱の終わりより先まで続くことがある。
        float start = (float) profile.glowStartFix;
        float end = (float) (beam.length() + profile.glowEndFix);
        float length = end - start;
        if (length <= 0) return 0;
        float middle = Math.max(0, length - 2 * cap);
        if (middle <= 0) { cap = length / 2; middle = 0; }
        var state = State.capture();
        int drawn = 0;
        try {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1, 1, 1, (float) Math.min(1, profile.glowAlpha / 255.0 * beam.alpha() * beam.glow()));
            var parts = Types.BEAM_GLOW[profile.ordinal()];
            drawn += strip(pose, parts[0], direction, side, start, start + cap, half);
            if (middle > 0) drawn += strip(pose, parts[1], direction, side, start + cap, start + cap + middle, half);
            drawn += strip(pose, parts[2], direction, side, end - cap, end, half);
            traced('G');
        } finally { state.restore(); }
        return drawn;
    }

    private static int strip(PoseStack pose, RenderType type, org.joml.Vector3f direction, org.joml.Vector3f side,
                             float from, float to, float half) {
        var matrix = pose.last().pose();
        var buffer = BUFFERS.getBuffer(type);
        float ax = direction.x() * from, ay = direction.y() * from, az = direction.z() * from;
        float bx = direction.x() * to, by = direction.y() * to, bz = direction.z() * to;
        float sx = side.x() * half, sy = side.y() * half, sz = side.z() * half;
        buffer.vertex(matrix, ax - sx, ay - sy, az - sz).uv(0, 1).endVertex();
        buffer.vertex(matrix, bx - sx, by - sy, bz - sz).uv(1, 1).endVertex();
        buffer.vertex(matrix, bx + sx, by + sy, bz + sz).uv(1, 0).endVertex();
        buffer.vertex(matrix, ax + sx, ay + sy, az + sz).uv(0, 0).endVertex();
        BUFFERS.endBatch(type);
        return 1;
    }

    /**
     * 原作RendererRayCylinder: 丸い先端（y = sqrt(x)を4段で、先端の0から幅のところで最大の太さ）、幅から光線の長さまでの管、
     * そして長さから長さ＋幅までの、向きを逆にした同じ先端。RendererRayCompositeは内側の芯の先端を引っ込める（headFix 0.98）:
     * 幅の50分の1だけ先から始まり、その分短い。
     */
    private static int cylinder(PoseStack pose, LegacyMdRayAnimation.Snapshot beam,
                                double width, int red, int green, int blue, int alpha, double headFix) {
        float radius = (float) (width * beam.width()), length = (float) beam.length();
        if (radius <= 0) return 0;
        traced(headFix < 1 ? 'I' : 'O');
        int colour = (int) ((float) (alpha / 255.0 * beam.alpha()) * 255);
        var buffer = BUFFERS.getBuffer(Types.BEAM); var matrix = pose.last().pose();
        float offset = (float) (radius * (1 - headFix)), head = (float) (radius * headFix);
        float[] xs = new float[HEAD_STEPS * 2 + 2], rs = new float[HEAD_STEPS * 2 + 2];
        // 光線に沿った順の輪: 前の先端を上り、管の向こう端、後ろの先端を下る。
        for (int i = 0; i <= HEAD_STEPS; i++) {
            float t = i / (float) HEAD_STEPS;
            xs[i] = offset + t * head; rs[i] = (float) Math.sqrt(t) * radius;
            xs[xs.length - 1 - i] = length + radius - offset - t * head; rs[rs.length - 1 - i] = rs[i];
        }
        int drawn = 0;
        for (int ring = 0; ring + 1 < xs.length; ring++) {
            // 前の先端の根元（半径、幅の位置）から、後ろの先端の根元（長さの位置）まで: 管。
            float xa = ring == HEAD_STEPS ? radius : xs[ring], xb = ring == HEAD_STEPS ? length : xs[ring + 1];
            float ra = rs[ring], rb = rs[ring + 1];
            for (int side = 0; side < SIDES; side++) {
                double from = side * 2 * Math.PI / SIDES, to = (side + 1) * 2 * Math.PI / SIDES;
                float ca = (float) Math.cos(from), sa = (float) Math.sin(from), cb = (float) Math.cos(to), sb = (float) Math.sin(to);
                // 面が外を向くように巻く。原作のcullingで見える側。
                buffer.vertex(matrix, xa, cb * ra, sb * ra).color(red, green, blue, colour).endVertex();
                buffer.vertex(matrix, xb, cb * rb, sb * rb).color(red, green, blue, colour).endVertex();
                buffer.vertex(matrix, xb, ca * rb, sa * rb).color(red, green, blue, colour).endVertex();
                buffer.vertex(matrix, xa, ca * ra, sa * ra).color(red, green, blue, colour).endVertex();
                drawn++;
            }
        }
        BUFFERS.endBatch(Types.BEAM);
        return drawn;
    }
    private static final int HEAD_STEPS = 4;

    /** 原作は、ワールドのGLの状態を受け取ったときのまま残す。ここも同じ。 */
    private record State(boolean depth, boolean mask, boolean cull, boolean blend, int function,
                         int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, float[] color) {
        static State capture() {
            return new State(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE), GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    RenderSystem.getShaderColor().clone());
        }
        void restore() {
            RenderSystem.depthFunc(function); RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }
}
