package io.github.pinchan4273.reacademycraft.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * シェーダーパックが今使われているか（NeoForge 1.21.1 版の同じ名前のクラスと同じ仕組み）。
 *
 * <p>Iris 1.11.4（26.2）は、シェーダーパックの間、描画のパイプラインのプログラムが Iris の物でない描画を、画面へ届かない描画先へ向ける。
 * Iris がパックのプログラムへ対応させるのは、vanilla の決まったパイプライン、{@code RenderPipeline#toBuilder()} で作ったその写し、
 * Iris の API で登録した物だけで、このMODの独自のシェーダーのパイプラインはどれにも当たらない（2026-11-05 に Iris の JAR のクラスで読んだ）。
 * そのため、パックの間だけ、演出を vanilla のシェーダーのパイプラインで描く（{@code client/render/ShaderPackDraw}）。パックが無い時の描き方は変えない。
 *
 * <p>Iris はこのMODの依存ではないので、その公開 API（{@code net.irisshaders.iris.api.v0.IrisApi#isShaderPackInUse}）はリフレクションで呼ぶ。
 * Iris が無い時、または API が見つからない時は、常に false（シェーダーパック無しと同じ描き方）。特定のパックや MOD の名前では分けない。
 */
public final class ShaderPackCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean resolved;
    private static @Nullable Object api;
    private static @Nullable Method inUse;

    private ShaderPackCompat() { }

    /** シェーダーパックが今使われているか。描画のたびに呼んでよい。 */
    public static boolean inUse() {
        if (!resolved) resolve();
        if (inUse == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Iris did not answer whether a shader pack is in use; drawing as without one", e);
            inUse = null;
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        // NeoForge 26.2 では Iris 本体（mod ID iris）。Forge 1.20.1 ではその移植の Oculus。公開 API は同じ。
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) return;
        try {
            var type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            inUse = type.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Iris is loaded but its API was not found; drawing as without a shader pack", e);
            api = null;
            inUse = null;
        }
    }
}
