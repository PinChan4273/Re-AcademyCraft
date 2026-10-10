package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * WeAthFolDによる原作のテクスチャ付きの弧の四角（7b1401c）を、Forgeで描くためのadapter。
 * 色だけを書き、深度は書かない（囲みの描画の前提）。弧の配置とアニメーションは呼び出し側が行う
 * （ClientChargingSurround・ClientChargingArc・ClientMagneticBlockArcsなど）。
 */
public final class LegacyArcRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/arc/line_segment.png");
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(LegacyArcMesh.MAX_VERTICES * 24));

    private static final class Types extends RenderType {
        private Types() { super("academy_unused_arc", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 256, false, false, () -> { }, () -> { }); }
        private static final RenderType ARC = create("academy_legacy_arc", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, LegacyArcMesh.MAX_VERTICES * 24, false, false,
                CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }

    private LegacyArcRenderer() { }

    /** 渡されたposeを通してmodel空間の形を描く。プレイヤーやワールドの状態は読まない。 */
    public static void draw(PoseStack pose, LegacyArcMesh.Mesh mesh, float opacity) {
        RenderSystem.assertOnRenderThread();
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1
                || mesh.quads().size() > LegacyArcMesh.MAX_VERTICES / 4)
            throw new IllegalArgumentException("Invalid arc draw bounds/opacity");
        if (opacity == 0 || mesh.quads().isEmpty()) return;
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int function = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            var buffer = BUFFERS.getBuffer(Types.ARC);
            Matrix4f matrix = pose.last().pose();
            for (var quad : mesh.quads()) {
                float alpha = quad.alpha() * opacity;
                vertex(buffer, matrix, quad.first(), alpha); vertex(buffer, matrix, quad.second(), alpha);
                vertex(buffer, matrix, quad.third(), alpha); vertex(buffer, matrix, quad.fourth(), alpha);
            }
            BUFFERS.endBatch(Types.ARC);
        } finally {
            // RenderType.clearRenderStateはバニラの既定へ戻すもので、入ってきたときの任意の状態へは戻さない。
            RenderSystem.depthFunc(function); RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> shader); RenderSystem.setShaderTexture(0, texture);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, LegacyArcMesh.Vertex vertex, float alpha) {
        var p = vertex.position();
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(1f, 1f, 1f, alpha).uv(vertex.u(), vertex.v()).endVertex();
    }
}
