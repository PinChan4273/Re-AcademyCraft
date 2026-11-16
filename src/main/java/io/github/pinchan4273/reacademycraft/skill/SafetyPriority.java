package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 1.5.2: CPセーフティの優先順位（プリセットごとの技能の並び）。
 * <ul>
 * <li>形態の維持は最上位で固定し、並びに入れない（{@link PlayerAbilityData#formDue}。セーフティでは止めない）。</li>
 * <li>並びの候補は、維持費のある本体の技能（{@link #CATALOGUE}）。プレイヤーごと・プリセット4つごとに、上が優先の並びを持つ。</li>
 * <li>保存と同期は、候補のインデックスを1文字ずつ並べた文字列（{@link #encode}。能力設定の値の上限16文字に収まる）。書かれていない候補は、
 *     書かれた物の下へ既定の並び（飛行 ＞ 防御 ＞ 攻撃・補助、同じ部類の中は技能の ID の順）で続く。</li>
 * <li>並びに無い技能（アドオンの維持など）は、すべての候補の下へ、始めた順（新しい物が下）で足す（{@link PlayerAbilityData}の精算が決める）。</li>
 * <li>足りないときは下から止め、パッシブは上から入れ直す（{@link PlayerAbilityData#settleUpkeep}・{@link PassiveSkills}）。</li>
 * <li>過負荷のロック中に回復が始まったら、パッシブをこの並びの上から1つずつ再稼働する（{@link PassiveSkills}）。</li>
 * </ul>
 */
public final class SafetyPriority {
    private SafetyPriority() { }

    /** 部類（既定の並びの大きな順）。飛行を先頭にする（ロックの後に飛行から戻すため）。 */
    public enum Group { FLIGHT, DEFENSE, OTHER }

    /**
     * 並びの候補と、その符号のインデックス（この順で 0, 1, 2 …。保存された文字列の意味が変わらないよう、足すだけで並べ替えない）。
     */
    public static final List<Identifier> CATALOGUE = List.of(VecDeviation.ID, VecReflection.ID, LightShield.ID, DarkMatter.GUARD,
            DarkMatter.FLIGHT, StormWing.ID, JetEngine.ID, BodyIntensify.ID, ElectronMissile.ID, Flashing.ID, DarkMatter.RAY, DarkMatter.HEAVEN);

    /** その技能の部類（候補でなければ{@link Group#OTHER}）。 */
    public static Group group(@Nullable Identifier skill) {
        if (VecDeviation.ID.equals(skill) || VecReflection.ID.equals(skill) || LightShield.ID.equals(skill) || DarkMatter.GUARD.equals(skill)) return Group.DEFENSE;
        if (DarkMatter.FLIGHT.equals(skill) || StormWing.ID.equals(skill) || JetEngine.ID.equals(skill)) return Group.FLIGHT;
        return Group.OTHER;
    }
    /** 既定の並び: 飛行 ＞ 防御 ＞ 攻撃・補助。同じ部類の中は技能の ID の順。 */
    public static final List<Identifier> DEFAULTS = CATALOGUE.stream()
            .sorted(Comparator.comparing((Identifier s) -> group(s).ordinal()).thenComparing(Identifier::toString)).toList();

    /** 候補の1文字の符号（インデックスの36進の1桁）。候補でなければ 0。 */
    public static char code(Identifier skill) {
        int index = CATALOGUE.indexOf(skill);
        return index < 0 ? 0 : Character.forDigit(index, 36);
    }
    /** 並びを文字列にする（候補でない技能は書かない）。 */
    public static String encode(List<Identifier> order) {
        var out = new StringBuilder();
        for (var skill : order) { char c = code(skill); if (c != 0 && out.indexOf(String.valueOf(c)) < 0) out.append(c); }
        return out.toString();
    }
    /** 文字列を並びに読む。知らない文字・重ねて書いた文字があれば null（読めない値は既定に戻す）。空は空の並び。 */
    public static @Nullable List<Identifier> decode(@Nullable String code) {
        if (code == null) return null;
        var out = new ArrayList<Identifier>();
        for (int i = 0; i < code.length(); i++) {
            int index = Character.digit(code.charAt(i), 36);
            if (index < 0 || index >= CATALOGUE.size() || Character.isUpperCase(code.charAt(i))) return null;
            var skill = CATALOGUE.get(index);
            if (out.contains(skill)) return null;
            out.add(skill);
        }
        return out;
    }
    /** 正しい値か（{@link #decode}で読め、能力設定の値の長さに収まる）。 */
    public static boolean valid(@Nullable String code) { return code != null && code.length() <= AbilitySettings.MAX_VALUE && decode(code) != null; }
    /** 保存された文字列から、すべての候補の並び（書かれた物、続けて残りを既定の並びで）。読めなければ既定の並び。 */
    public static List<Identifier> resolve(@Nullable String code) {
        var head = decode(code);
        var out = new ArrayList<Identifier>(head == null ? List.of() : head);
        for (var skill : DEFAULTS) if (!out.contains(skill)) out.add(skill);
        return List.copyOf(out);
    }
    /** その技能の順位（0が最上位）。候補でなければ候補の数（すべての候補より下）。 */
    public static int rank(List<Identifier> resolved, Identifier skill) {
        int index = resolved.indexOf(skill);
        return index < 0 ? resolved.size() : index;
    }
    /**
     * 能力設定の欄に並べる技能（そのプレイヤーの今の系統の、習得済みの候補を、今の並びの順で）。系統が無ければ空。
     */
    public static List<Identifier> candidates(@Nullable Identifier category, Predicate<Identifier> learned, @Nullable String code) {
        var out = new ArrayList<Identifier>();
        if (category == null) return out;
        for (var skill : resolve(code))
            if (category.equals(AbilitySettingsVisibility.categoryOf(skill)) && learned.test(skill)) out.add(skill);
        return out;
    }
    /**
     * 欄の並び（candidates）の index 番目と1つ上（dir ＝ −1）か下（＋1）を入れ替えた、新しい保存の文字列。動かせなければ null。
     * 欄に出ていない候補（ほかの系統の技能）の位置は、今の並びのまま保つ。
     */
    public static @Nullable String move(@Nullable String code, List<Identifier> shown, int index, int dir) {
        int other = index + dir;
        if (index < 0 || index >= shown.size() || other < 0 || other >= shown.size()) return null;
        var all = new ArrayList<>(resolve(code));
        int a = all.indexOf(shown.get(index)), b = all.indexOf(shown.get(other));
        if (a < 0 || b < 0) return null;
        all.set(a, shown.get(other)); all.set(b, shown.get(index));
        // 既定の並びの末尾と同じ所は書かない（短く保つ）。
        int keep = all.size();
        while (keep > 0 && all.subList(keep - 1, all.size()).equals(tailDefaults(all.subList(0, keep - 1)))) keep--;
        return encode(all.subList(0, keep));
    }
    /** head の後に resolve が足す残り（既定の並び）。 */
    private static List<Identifier> tailDefaults(List<Identifier> head) {
        var out = new ArrayList<Identifier>();
        for (var skill : DEFAULTS) if (!head.contains(skill)) out.add(skill);
        return out;
    }
}
