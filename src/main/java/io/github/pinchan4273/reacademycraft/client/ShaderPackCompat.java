package io.github.pinchan4273.reacademycraft.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.neoforged.fml.ModList;
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
     * Forge 1.20.1版では、position-colour-texの順（vertex・color・uv）で書く描画を、position-tex-colourの頂点へ並べ替えて渡した。
     * 1.21の頂点の書き込みは形式の中の決まった位置へ書くので順番は関係なく、形式に無い要素（overlay・lightmap・法線）は書かれない。
     * そのため、ここでは全部をそのまま渡す（呼び出し側を変えないために型を残す）。
     */
    public static final class TexColorOrder implements com.mojang.blaze3d.vertex.VertexConsumer {
        private final com.mojang.blaze3d.vertex.VertexConsumer out;
        public TexColorOrder(com.mojang.blaze3d.vertex.VertexConsumer out) { this.out = out; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { out.addVertex(x, y, z); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int red, int green, int blue, int alpha) { out.setColor(red, green, blue, alpha); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { out.setNormal(x, y, z); return this; }
    }

    private static void resolve() {
        resolved = true;
        // NeoForge 1.21.1ではIris本体（mod ID iris）。Forge 1.20.1ではその移植のOculus。公開APIは同じ。
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) return;
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
