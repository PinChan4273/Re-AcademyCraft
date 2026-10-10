package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyRippleAnimation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** 原作の波紋の四角形・UV・色を、状態を保つForgeのワールド描画アダプタ経由で描く。 */
public final class LegacyRippleRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/ripple.png");
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1024));

    private static final class Types extends RenderType {
        private Types() { super("academy_unused_ripple", DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 256, false, false, () -> { }, () -> { }); }
        private static final RenderType RIPPLE = create("academy_legacy_ripple", DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder().setShaderState(new ShaderStateShard(
                                net.minecraft.client.renderer.GameRenderer::getPositionTexShader))
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }

    private LegacyRippleRenderer() { }

    /**
     * Thunder Clapの輪: 原作はマークの色を(204, 204, 204, 179)にするが、RippleMarkRenderはそのアルファを各輪自身のもの
     * （material.color.setAlpha(f2i(getAlpha(mod)))）で置き換えるので、179は表に出ない。輪は自身のアルファで描く
     * （179/255を掛けると原作より暗くなる）。
     */
    public static int draw(PoseStack pose, List<LegacyRippleAnimation.Ring> rings) {
        return draw(pose, rings, LegacyRippleAnimation.RED, LegacyRippleAnimation.GREEN, LegacyRippleAnimation.BLUE, 255);
    }

    /**
     * 同じ輪をマーク自身の色で描く。baseAlphaは輪のアルファに掛かり、255が原作どおり
     * （原作の描画はマークのアルファを輪自身のもので置き換える）。
     */
    public static int draw(PoseStack pose, List<LegacyRippleAnimation.Ring> rings, int red, int green, int blue, int baseAlpha) {
        RenderSystem.assertOnRenderThread();
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255 || baseAlpha < 0 || baseAlpha > 255)
            throw new IllegalArgumentException("Invalid ripple colour");
        if (rings == null || rings.size() > 3) throw new IllegalArgumentException("Invalid ripple ring count");
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int function = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader = RenderSystem.getShader(); int texture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone(); int drawn = 0;
        try {
            RenderSystem.disableDepthTest();
            for (var ring : rings) {
                if (ring.index() < 0 || ring.index() >= 3 || !finite(ring.phase()) || !finite(ring.height())
                        || !finite(ring.size()) || ring.size() <= 0 || ring.size() > 2
                        || !finite(ring.alpha()) || ring.alpha() < 0 || ring.alpha() > 1)
                    throw new IllegalArgumentException("Invalid ripple ring bounds");
                float alpha = (float) (ring.alpha() * baseAlpha / 255.0);
                if (alpha <= 0) continue;
                pose.pushPose();
                try {
                    pose.translate(0, ring.height(), 0); pose.scale((float) ring.size(), 1, (float) ring.size());
                    RenderSystem.setShaderColor(red / 255f, green / 255f, blue / 255f, alpha);
                    var buffer = BUFFERS.getBuffer(Types.RIPPLE); var matrix = pose.last().pose();
                    buffer.vertex(matrix, -.5f, 0, -.5f).uv(0, 0).endVertex();
                    buffer.vertex(matrix, .5f, 0, -.5f).uv(0, 1).endVertex();
                    buffer.vertex(matrix, .5f, 0, .5f).uv(1, 1).endVertex();
                    buffer.vertex(matrix, -.5f, 0, .5f).uv(1, 0).endVertex();
                    BUFFERS.endBatch(Types.RIPPLE); drawn++;
                } finally { pose.popPose(); }
            }
        } finally {
            RenderSystem.depthFunc(function); RenderSystem.depthMask(mask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> shader); RenderSystem.setShaderTexture(0, texture);
        }
        return drawn;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
