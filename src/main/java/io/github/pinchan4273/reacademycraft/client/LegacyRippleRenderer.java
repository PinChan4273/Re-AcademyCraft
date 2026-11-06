package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.Immediate;
import io.github.pinchan4273.reacademycraft.visual.LegacyRippleAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.resources.Identifier;

/** 原作の波紋の四角形・UV・色を、状態を保つForgeのワールド描画アダプタ経由で描く。 */
public final class LegacyRippleRenderer {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "academy", "textures/effects/ripple.png");
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
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255 || baseAlpha < 0 || baseAlpha > 255)
            throw new IllegalArgumentException("Invalid ripple colour");
        if (rings == null || rings.size() > 3) throw new IllegalArgumentException("Invalid ripple ring count");
        int drawn = 0;
        {
            // 1.21.1 と同じ状態: 深度テストなし、深度は書かない、両面、半透明。呼ぶ側の状態を変えない。
            Immediate.setShader(Immediate.Shader.TEX_COLOR_KEEP); Immediate.setShaderTexture(0, TEXTURE);
            Immediate.enableBlend(); Immediate.disableCull(); Immediate.disableDepthTest(); Immediate.depthMask(false);
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
                    Immediate.setShaderColor(red / 255f, green / 255f, blue / 255f, alpha);
                    var buffer = Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS); var matrix = pose.last().pose();
                    buffer.addVertex(matrix, -.5f, 0, -.5f).setUv(0, 0);
                    buffer.addVertex(matrix, .5f, 0, -.5f).setUv(0, 1);
                    buffer.addVertex(matrix, .5f, 0, .5f).setUv(1, 1);
                    buffer.addVertex(matrix, -.5f, 0, .5f).setUv(1, 0);
                    buffer.end(); drawn++;
                } finally { pose.popPose(); }
            }
            Immediate.setShaderColor(1, 1, 1, 1); Immediate.enableDepthTest(); Immediate.enableCull(); Immediate.depthMask(true);
        }
        return drawn;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
