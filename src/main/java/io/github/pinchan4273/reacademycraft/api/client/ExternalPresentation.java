package io.github.pinchan4273.reacademycraft.api.client;

import io.github.pinchan4273.reacademycraft.client.ClientExternalBeams;
import io.github.pinchan4273.reacademycraft.visual.ExternalBeamRules;

/**
 * 外部MODのための、見た目だけの演出の入口（公開API、実験的。演出API版1）。
 * <p>
 * <b>クライアント専用。</b>クライアントの主スレッドからだけ呼ぶ。書き込みの入口（beam・end・clear）は、ほかのスレッドからは{@link IllegalStateException}。
 * 読み取りの入口（limit・active・diagnostics）はスレッドを検査せず、ほかのスレッドで読んだ値は保証しない。サーバーや共通のコード（Dedicatedのサーバーで読み込まれうるクラス）から参照しない。アドオンは、自分の通信でサーバーの確定した結果を受け取ったクライアントで呼ぶ。
 * <p>
 * <b>別枠。</b>外部の光線は、持ち主（MOD ID）ごとの一覧・上限・効果ID・寿命で管理し、自然能力の光線・球の一覧とは別。互いの枠を
 * 消費しない。描画の部品・テクスチャ・描画の段階（{@code AFTER_BLOCK_ENTITIES}、自然能力の光線の後）は共用する。
 * <p>
 * <b>見た目だけ。</b>被害・命中・費用・自然能力の習得と技能に副作用を持たない。音も鳴らさない（鳴らすのは呼び出し側）。誰に送るか
 * （配る範囲）は呼び出し側の通信が決め、Re:AcademyCraftは受け付けた光線をその世界で描く（距離では捨てない）。
 * <p>
 * <b>機能の確かめ。</b>{@code AcademyApi.VERSION}は定数でアドオンに埋め込まれるため、実行時の確かめには{@link #apiVersion()}を使う。
 * このクラスが無い版では、呼んだときに{@link LinkageError}（{@link NoClassDefFoundError}など）になる。
 * <p>
 * <b>上限。</b>持ち主ごとの上限はクライアントの設定（{@code config/academy_client.properties}の{@code externalBeamsPerOwner}、1〜256）。
 * 既定の32は、クライアントごと・持ち主のMOD IDごとの同時32本として採用した値。1人あたりや、すべての持ち主の合計の上限ではない。設定で変えられる。
 */
public final class ExternalPresentation {
    private ExternalPresentation() { }

    /** 演出APIの版（この版は1）。 */
    public static final int API_VERSION = 1;

    /** 演出APIの版。実行時の機能の確かめに使う（定数{@link #API_VERSION}はアドオンに埋め込まれる）。 */
    public static int apiVersion() { return API_VERSION; }

    /** この版がその見た目を持つか。 */
    public static boolean supports(BeamStyle style) { return style == BeamStyle.MELTDOWNER_BEAM; }

    /** その見た目の光線の最大の長さ（ブロック）。持たない見た目は0。{@link BeamStyle#MELTDOWNER_BEAM}は96。 */
    public static double maxLength(BeamStyle style) { return supports(style) ? ExternalBeamRules.MAX_LENGTH : 0; }

    /** 持ち主ごとの今の上限（クライアントの設定。既定32は採用済みの値）。 */
    public static int limit(String ownerModId) { return ClientExternalBeams.limit(); }

    /** 光線を1本足す。結果は{@link BeamResult}。{@link BeamResult#ACCEPTED}以外では何も足さない。 */
    public static BeamResult beam(BeamRequest request) { return ClientExternalBeams.beam(request); }

    /** 持ち主のその光線を、寿命の前に終える（すぐ消す）。描いていればtrue。終えた効果IDも、200tickの間は重複として扱う。 */
    public static boolean end(String ownerModId, long effectId) { return ClientExternalBeams.end(ownerModId, effectId); }

    /** 持ち主の光線をすべて終える。終えた本数。 */
    public static int clear(String ownerModId) { return ClientExternalBeams.clear(ownerModId); }

    /** 持ち主の今の本数。 */
    public static int active(String ownerModId) { return ClientExternalBeams.active(ownerModId); }

    /** 計数（負荷の計測と試験のため）。 */
    public static Diagnostics diagnostics(String ownerModId) { return ClientExternalBeams.diagnostics(ownerModId); }

    /**
     * 計数。持ち主ごとの値と、すべての外部の光線を合わせた値。
     *
     * @param active           持ち主の今の本数
     * @param accepted         持ち主の受け付けた数（累計）
     * @param duplicate        {@link BeamResult#DUPLICATE}の数（累計。以下同じ）
     * @param full             {@link BeamResult#FULL}の数
     * @param unsupported      {@link BeamResult#UNSUPPORTED}の数
     * @param invalid          {@link BeamResult#INVALID}の数
     * @param notReady         {@link BeamResult#NOT_READY}の数
     * @param totalActive      すべての持ち主の今の本数
     * @param lastFrameQuads   直前のフレームで、外部の光線が描いた四角形の数（光の帯と円柱）
     * @param lastFrameBatches 直前のフレームで、外部の光線が出した描画の確定（endBatch）の回数
     * @param lastFrameNanos   直前のフレームで、外部の光線を描いた区間のCPU時間（ナノ秒。GPUの時間は含まない）
     * @param totalQuads       外部の光線が描いた四角形の累計
     */
    public record Diagnostics(int active, long accepted, long duplicate, long full, long unsupported, long invalid, long notReady,
                              int totalActive, int lastFrameQuads, int lastFrameBatches, long lastFrameNanos, long totalQuads) { }
}
