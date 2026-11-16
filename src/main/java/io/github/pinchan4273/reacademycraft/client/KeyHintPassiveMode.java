package io.github.pinchan4273.reacademycraft.client;

import javax.annotation.Nullable;

/**
 * 1.5.2: キーの案内（{@link KeyHintHud}）に、パッシブにした技能の行をどう出すか。
 * クライアントだけの表示の設定で、{@link ClientSettings}に保存する（解析度のHUDの表示の形式と同じ仕組み）。
 * Minecraftのクライアントに依存しないので、GameTestのサーバーでも確かめられる（{@link AnalysisHudStyle}と同じ）。
 * <ul>
 * <li>非表示: パッシブの行を出さない。</li>
 * <li>アイコン（既定）: キーのラベルを描かず、背景と技能のアイコンだけを出す。起動中はアイコンの周りが光る。</li>
 * <li>アイコン＋自動: キーのラベルに「自動」（{@link KeyHintHud#PASSIVE_LABEL}）を出す（1.5.2の最初の形）。</li>
 * </ul>
 */
public enum KeyHintPassiveMode {
    HIDDEN("hidden"), ICON("icon"), ICON_AUTO("icon_auto");
    private final String id;
    KeyHintPassiveMode(String id) { this.id = id; }
    /** 設定ファイルに書く値。 */
    public String id() { return id; }
    /** 能力設定の画面に出す名前の翻訳キー。 */
    public String translation() { return "academy.key_hint.passive.mode." + id; }
    /** パッシブの行を出すか。 */
    public boolean shown() { return this != HIDDEN; }
    /** キーのラベル（「自動」）を描くか。 */
    public boolean label() { return this == ICON_AUTO; }
    /** 保存された文字列から。知らない値や無い値は既定の「アイコン」（手で壊した設定ファイルでも表示を失わない）。 */
    public static KeyHintPassiveMode parse(@Nullable String value) {
        for (var mode : values()) if (mode.id.equals(value)) return mode;
        return ICON;
    }
}
