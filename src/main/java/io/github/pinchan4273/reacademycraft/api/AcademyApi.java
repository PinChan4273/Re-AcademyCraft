package io.github.pinchan4273.reacademycraft.api;

/**
 * Re:AcademyCraftの公開APIの版（1.4.0のAPIは実験的で、将来の互換を無条件には保証しない）。
 * 公開の契約は、このパッケージ（{@code io.github.pinchan4273.reacademycraft.api}、ローダーとMinecraftの型に依存しない）と、
 * {@code api.mc}（Minecraftの型を使う実行の契約。Minecraftの版に結びつく）と、{@code api.forge}（Forgeの接続）と、
 * {@code api.client}（クライアント専用の、見た目だけの演出）の型だけ。
 * ほかのパッケージは内部の物で、予告なく変わる。
 * APIの版は、通信の版とは別に数える。非互換の変更は版を上げ、変更の履歴を開発者向けの文書に書く。
 */
public final class AcademyApi {
    private AcademyApi() { }
    /** 公開APIの版。 */
    public static final int VERSION = 1;
    /** 実験的か（1.4.0ではtrue）。 */
    public static final boolean EXPERIMENTAL = true;
}
