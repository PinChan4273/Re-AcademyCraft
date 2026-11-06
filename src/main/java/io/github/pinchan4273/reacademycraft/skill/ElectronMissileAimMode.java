package io.github.pinchan4273.reacademycraft.skill;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Electron Missileの照準の選び方（F9-02、2026-10-23の開発司令室9の訂正）。生成・発射の間隔（8tick）、オーブの上限（5個）、射程、
 * 1発の費用はどちらも同じ。撃ってよい相手は、どちらのモードでも攻撃対象のフィルター（{@link ElectronMissileTargetKind}）で決まる。
 * <ul>
 * <li>{@link #AUTO}: 撃ってよい相手のうち、最も近いもの（1.2.0の自動射撃と同じ選び方）。</li>
 * <li>{@link #SEMI}: 撃ってよい相手のうち、視線の円錐（半角{@link ElectronMissileTargeting#SEMI_CONE_DEGREES}度）の中にいるものを優先する
 * （視線に近いものから）。円錐の中にいなければ、AUTOと同じく最も近いものへ戻る。</li>
 * </ul>
 * 保存する値は{@link #id()}の小文字の名前。設定が無い（新しいプレイヤー、F9-02より前のセーブ）ときは{@link #DEFAULT}（AUTO）。
 */
public enum ElectronMissileAimMode {
    AUTO("auto"), SEMI("semi");

    /** 設定が無いときのモード。 */
    public static final ElectronMissileAimMode DEFAULT = AUTO;
    private final String id;
    ElectronMissileAimMode(String id) { this.id = id; }
    /** 保存とコマンドで使う名前。 */
    public String id() { return id; }
    /** 翻訳キー（チャットでのモードの名前）。 */
    public String translation() { return "academy.electron_missile.mode." + id; }

    /** 保存やコマンドの名前から読む。知らない名前はnull（呼び出し元が既定を使い、保存された値はそのまま残す）。 */
    public static @Nullable ElectronMissileAimMode parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (var mode : values()) if (mode.id.equals(key)) return mode;
        return null;
    }
}
