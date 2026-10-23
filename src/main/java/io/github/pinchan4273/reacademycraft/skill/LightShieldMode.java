package io.github.pinchan4273.reacademycraft.skill;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Light Shieldの盾が、どの向きを目指すかの選び方（工程2、2026-10-23の司令室8の指示）。盾の防御・移動・描画・費用はどちらも同じで、
 * 変わるのは標的と目標の向きの選び方だけ。
 * <ul>
 * <li>{@link #AUTO}: 完全自動の防御。視線に依らず、全方向（上下も）から脅威を探し、優先度の高いものへ盾を向ける。</li>
 * <li>{@link #MANUAL}: 利用者向けの名前はSEMI-AUTO（2026-10-23の開発司令室9でモード名を統一）。視線誘導の半自動の防御で、
 * 視線の中心軸に近い有効な脅威へ盾を向け、対象が無ければ視線の方向へ向ける。カメラは動かさない。</li>
 * </ul>
 * 保存する値は{@link #id()}の小文字の名前（MANUALは互換のため"manual"のまま。既存のセーブも古い版も読める）。コマンドの正式な名前は
 * {@link #command()}（"auto"・"semi"）で、"manual"も互換の別名として受け付ける。新しい設定の既定は{@link #DEFAULT}（AUTO）。
 */
public enum LightShieldMode {
    AUTO("auto", "auto"), MANUAL("manual", "semi");

    /** 設定が無い（新しいプレイヤー、工程2より前のセーブ）ときのモード。 */
    public static final LightShieldMode DEFAULT = AUTO;
    private final String id, command;
    LightShieldMode(String id, String command) { this.id = id; this.command = command; }
    /** 保存する名前（MANUALは互換のため"manual"）。 */
    public String id() { return id; }
    /** コマンドの正式な名前（"auto"・"semi"）。 */
    public String command() { return command; }
    /** 翻訳キー（チャットでのモードの名前。AUTO・SEMI-AUTO）。 */
    public String translation() { return "academy.light_shield.mode." + command; }

    /** 保存やコマンドの名前から読む（"auto"、"semi"、互換の"manual"）。知らない名前はnull（呼び出し元が既定を使い、保存された値はそのまま残す）。 */
    public static @Nullable LightShieldMode parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (var mode : values()) if (mode.id.equals(key) || mode.command.equals(key)) return mode;
        return null;
    }
}
