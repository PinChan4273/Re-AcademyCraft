package io.github.pinchan4273.reacademycraft.skill;

import javax.annotation.Nullable;

/**
 * 1.5.2: ベクトル反射の「反射方向」。プレイヤーごとの設定で、手動・パッシブで共通。
 * 変えたら次に成立した反射から効く（{@link VecReflection}が反射の成立の瞬間に1回だけ読む）。
 * <ul>
 * <li>{@link #ATTACKER}（既定）: 元の射手・殴った相手・光線の発射者へ返す。射手の分からない飛び道具は、飛んできた向きの逆へ返す。</li>
 * <li>{@link #LOOK}: 反射者の照準の方向へ返す。</li>
 * <li>{@link #NEAREST}: 反射者の周り360度で、見通せる対象のうち反射者に最も近い物へ返す。いなければ視線方向。</li>
 * <li>{@link #VISIBLE}: 反射者の前方の視野にいて見通せる対象のうち、反射者に最も近い物へ返す。いなければ視線方向。</li>
 * </ul>
 */
public enum ReflectionDirection {
    ATTACKER("attacker"), LOOK("look"), NEAREST("nearest"), VISIBLE("visible");

    /** 旧セーブ（設定の無いデータ）と、知らない値のときの値。 */
    public static final ReflectionDirection DEFAULT = ATTACKER;
    private final String id;
    ReflectionDirection(String id) { this.id = id; }
    /** 保存と通信に使う名前。 */
    public String id() { return id; }
    /** 自動で対象を選ぶ2つ（最寄り・視界内）か。 */
    public boolean automatic() { return this == NEAREST || this == VISIBLE; }
    /** 能力設定の値の欄に出す短い名前の翻訳キー。 */
    public String translation() { return "academy.ability_settings.reflection.direction." + id; }
    /** 名前から。知らない名前（空を含む）はnull。 */
    public static @Nullable ReflectionDirection parse(@Nullable String id) {
        if (id == null) return null;
        for (var value : values()) if (value.id.equals(id)) return value;
        return null;
    }
    /** 能力設定の左右の矢印で回す順。 */
    public static String[] ids() {
        var out = new String[values().length];
        for (int i = 0; i < out.length; i++) out[i] = values()[i].id;
        return out;
    }
}
