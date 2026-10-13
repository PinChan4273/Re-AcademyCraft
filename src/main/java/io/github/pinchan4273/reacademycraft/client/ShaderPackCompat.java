package io.github.pinchan4273.reacademycraft.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Oculus（Irisの移植）がシェーダーパックを使っているか。
 *
 * <p>Oculus 1.8.0は、シェーダーパックでワールドを描く間、次の2つの理由でこのMODの一部の描画を画面から消す（Oculusのクラスの逆アセンブル
 * と、BSL_v10.1.8での描画の比較で確かめる）。
 * <ul>
 * <li>MOD自身のShaderInstanceでワールド（メインの描画先）へ描くと、そのapplyの直後に色と深度の書き込みを止める
 * （MixinShaderInstance → DepthColorStorage.disableDepthColor。メインの描画先が結ばれている間だけ）。独自のシェーダーで描いた効果は何も書かない。</li>
 * <li>getPositionColorTexShaderも、getPositionTexColorShaderと同じposition-tex-colourの頂点の並びのプログラムへ置き換える。
 * position-colour-texの頂点で描くと、テクスチャ座標と色が入れ替わって読まれ、透明なtexelを引いて全部捨てられる。</li>
 * </ul>
 * OculusはこのMODの依存ではないので、その公開API（net.irisshaders.iris.api.v0.IrisApi）はリフレクションで呼ぶ。Oculusが無いとき、
 * またはAPIが見つからないときは、常にfalse（シェーダーパック無しと同じ描き方）。
 */
public final class ShaderPackCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean resolved;
    @Nullable private static Object api;
    @Nullable private static Method inUse;
    private ShaderPackCompat() { }

    /** シェーダーパックが今使われているか。描画のたびに呼んでよい。 */
    public static boolean inUse() {
        if (!resolved) resolve();
        if (inUse == null) return false;
        try { return (boolean) inUse.invoke(api); }
        catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Oculus did not answer whether a shader pack is in use; drawing as without one", e);
            inUse = null; return false;
        }
    }
    /**
     * position-colour-texの順（vertex・color・uv）で書く描画を、position-tex-colourの頂点（vertex・uv・color）へ並べ替えて渡す。
     * シェーダーパックの間だけ、position-tex-colourのRenderTypeと組で使う（overlay・lightmap・法線は、その形式に無いので捨てる）。
     */
    public static final class TexColorOrder implements com.mojang.blaze3d.vertex.VertexConsumer {
        private final com.mojang.blaze3d.vertex.VertexConsumer out;
        private int r = 255, g = 255, b = 255, a = 255;
        public TexColorOrder(com.mojang.blaze3d.vertex.VertexConsumer out) { this.out = out; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) { out.vertex(x, y, z); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer color(int red, int green, int blue, int alpha) { r = red; g = green; b = blue; a = alpha; return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer uv(float u, float v) { out.uv(u, v); out.color(r, g, b, a); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { out.endVertex(); r = g = b = a = 255; }
        @Override public void defaultColor(int red, int green, int blue, int alpha) { out.defaultColor(red, green, blue, alpha); }
        @Override public void unsetDefaultColor() { out.unsetDefaultColor(); }
    }

    private static void resolve() {
        resolved = true;
        if (!ModList.get().isLoaded("oculus")) return;
        try {
            var type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Oculus is loaded but its API was not found; drawing as without a shader pack", e);
            api = null; inUse = null;
        }
    }
}
