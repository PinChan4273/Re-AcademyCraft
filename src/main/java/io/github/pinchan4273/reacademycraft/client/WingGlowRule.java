package io.github.pinchan4273.reacademycraft.client;

import org.jspecify.annotations.Nullable;

/**
 * 未元物質の翼の発光の、描く時の値の決まり（2026-11-05のユーザーの決定。NeoForge 1.21.1 版の 9ab4cb19 と同じ）。
 *
 * <p>シェーダーパックの使用中は、設定が100%なら90%として描く。100%の翼の面は自分で光る描き方（{@code entityTranslucentEmissive}・
 * {@code beaconBeam}）になり、シェーダーパックはそれを光る物のプログラムへ置き換えて強いブルームを掛けるため、翼と羽の球が白く飛ぶ。
 * 90%は周りの明るさを受ける描き方に発光の層を足す形で、シェーダーパックの下でも形が見える（ユーザーが試して決めた値）。
 * <ul>
 * <li>保存された設定（{@link ClientSettings#WING_GLOW}）は書き換えない。シェーダーパックを外せば100%の描き方に戻る。</li>
 * <li>自分で90%以下にした値は、そのまま使う。</li>
 * <li>シェーダーパックの使用中かは{@link ShaderPackCompat#inUse()}（一般のAPI）で見る。特定のシェーダーやMODの名前では分けない。</li>
 * </ul>
 * {@link #drawn(int, boolean)}はクライアントのクラスを使わない純粋な関数なので、専用サーバーで動くGameTestからも確かめる。
 */
public final class WingGlowRule {
    /** シェーダーパックの使用中に描く発光の上限（%）。 */
    public static final int SHADER_PACK_MAX = 90;
    /** 試験だけの差し込み口: nullでなければ、シェーダーパックの使用中かの答えをこの値にする。既定はnull（使わない）。 */
    private static @Nullable Boolean shaderPackForTest;

    private WingGlowRule() { }

    /** 設定の値（%）と、シェーダーパックの使用中かから、描く時の発光（%）を決める。 */
    public static int drawn(int setting, boolean shaderPack) {
        return shaderPack ? Math.min(setting, SHADER_PACK_MAX) : setting;
    }

    /** 今シェーダーパックが使われているか（試験の差し込みがあればその値）。 */
    public static boolean shaderPack() {
        var forTest = shaderPackForTest;
        return forTest != null ? forTest : ShaderPackCompat.inUse();
    }

    /** 試験だけで使う。nullで差し込みを外し、実際の答えに戻す。 */
    public static void setShaderPackForTest(@Nullable Boolean value) { shaderPackForTest = value; }
}
