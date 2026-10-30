package io.github.pinchan4273.reacademycraft.skill;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Electron Missileの攻撃対象の4つの分類（F9-02）。分類ごとに、撃ってよいかをプレイヤーがON／OFFで選ぶ。
 * 生き物がどの分類になるかは{@link ElectronMissileTargeting#classify}が決める（重なるときの順もそこに書く）。
 * <p>
 * 既定は敵対だけON（{@link #defaultOn()}）。1.2.0は射程の中のすべての生き物を撃っていたので、選んでいないプレイヤー（既存のセーブを含む）は、
 * 中立・友好の生き物とプレイヤーを撃たなくなる（安全側の既定。採用の根拠は正式RCの証拠に記録する）。
 */
public enum ElectronMissileTargetKind {
    HOSTILE("hostile", true), NEUTRAL("neutral", false), FRIENDLY("friendly", false), PLAYER("player", false);

    private final String id;
    private final boolean defaultOn;
    ElectronMissileTargetKind(String id, boolean defaultOn) { this.id = id; this.defaultOn = defaultOn; }
    /** 保存とコマンドで使う名前。 */
    public String id() { return id; }
    /** 選んでいないときに撃ってよいか。 */
    public boolean defaultOn() { return defaultOn; }
    /** 翻訳キー（チャットでの分類の名前）。 */
    public String translation() { return "academy.electron_missile.target." + id; }

    /** 保存やコマンドの名前から読む。知らない名前はnull。 */
    public static @Nullable ElectronMissileTargetKind parse(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (var kind : values()) if (kind.id.equals(key)) return kind;
        return null;
    }
}
