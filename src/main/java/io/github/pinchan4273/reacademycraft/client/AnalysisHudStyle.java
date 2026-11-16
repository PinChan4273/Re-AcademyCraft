package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.VectorAnalysisSync;
import io.github.pinchan4273.reacademycraft.skill.VectorAnalysis;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * 1.5.2: ベクトル操作の解析度の表示の決まり。Minecraftのクライアントに依存しないので、GameTestのサーバーでも確かめられる
 * （{@link CpHudStyle}と同じ）。値の保存は{@link ClientSettings}、HUDは{@link VectorAnalysisHud}、一覧は能力設定と
 * データ端末（{@link VectorAnalysisScreen}）。
 * <ul>
 * <li>表示の形式: 「選んだ物だけ」（既定）・「すべて」・「出さない」。既定で何も選んでいなければ、HUDには何も出ない
 *     （初めてのときに画面をふさがない。選び方は能力設定の注記で案内する）。</li>
 * <li>並び: 分類の順（未元物質・Re の能力・魔法・防具を無視する物・MODのタグ・種類ごと）、同じ分類の中はキーの順。
 *     サーバーが覚えた順は使わない（新しい分類が増えても、前の項目が動かない）。</li>
 * <li>割合の文字: 切り捨ての整数（%）。100%は解析しきったときだけ（99.9%を100%と見せない。反射の無効は100%だけのため）。</li>
 * <li>選んだ物の保存: キーをカンマでつないだ1行。空白・長すぎるキーは捨て、{@link #MAX_SELECTED}件まで。</li>
 * </ul>
 */
public final class AnalysisHudStyle {
    private AnalysisHudStyle() { }

    /** HUDへの表示の形式。既定は「選んだ物だけ」。 */
    public enum Mode {
        SELECTED("selected"), ALL("all"), OFF("off");
        private final String id;
        Mode(String id) { this.id = id; }
        public String id() { return id; }
        public String translation() { return "academy.ability_settings.analysis.mode." + id; }
        /** 保存された文字列から。知らない値や無い値は既定（手で壊した設定ファイルでも表示を失わない）。 */
        public static Mode parse(@Nullable String value) {
            for (var mode : values()) if (mode.id.equals(value)) return mode;
            return SELECTED;
        }
    }

    /** 保存する選んだ物の上限と、1つのキーの長さの上限（同期のキーの上限と同じ）。 */
    public static final int MAX_SELECTED = 64, MAX_KEY_LENGTH = 256;

    /** 分類の並びの順。知らない形のキーは最後。 */
    public static int kindOrder(String key) {
        if (key.equals(VectorAnalysis.DARK_MATTER_KEY) || key.startsWith(VectorAnalysis.DARK_MATTER_KEY + "/")) return 0;
        if (key.startsWith(VectorAnalysis.ABILITY_PREFIX)) return 1;
        if (key.equals(VectorAnalysis.MAGIC_KEY)) return 2;
        if (key.equals(VectorAnalysis.BYPASS_KEY)) return 3;
        if (key.startsWith(VectorAnalysis.TAG_PREFIX)) return 4;
        if (key.startsWith(VectorAnalysis.TYPE_PREFIX)) return 5;
        return 6;
    }
    /** 画面に出す並び（分類の順、同じ分類の中はキーの順）。元の一覧は変えない。 */
    public static List<VectorAnalysisSync.Entry> sorted(Collection<VectorAnalysisSync.Entry> entries) {
        var out = new ArrayList<>(entries);
        out.sort(Comparator.comparingInt((VectorAnalysisSync.Entry e) -> kindOrder(e.key())).thenComparing(VectorAnalysisSync.Entry::key));
        return out;
    }
    /** HUDに出す項目（並べた後）。「出さない」なら空、「選んだ物だけ」なら選んだキーの項目だけ。 */
    public static List<VectorAnalysisSync.Entry> shown(Mode mode, Collection<VectorAnalysisSync.Entry> entries, Set<String> selected) {
        return switch (mode) {
            case OFF -> List.of();
            case ALL -> sorted(entries);
            case SELECTED -> sorted(entries.stream().filter(e -> selected.contains(e.key())).toList());
        };
    }
    /** 割合の文字（切り捨ての整数。100%は解析しきったときだけ）。 */
    public static String percent(float value) {
        if (!(value > 0)) return "0%";
        if (VectorAnalysis.full(value)) return "100%";
        return Math.min(99, (int) Math.floor(value * 100)) + "%";
    }
    /** バーの長さ（0〜1）。 */
    public static float fill(float value) { return Float.isNaN(value) ? 0 : Math.max(0, Math.min(1, value)); }

    /** 保存された選んだ物（入れた順）。 */
    public static Set<String> decode(@Nullable String saved) {
        var out = new LinkedHashSet<String>();
        if (saved == null) return out;
        for (var part : saved.split(",")) {
            var key = part.trim();
            if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) continue;
            out.add(key);
            if (out.size() >= MAX_SELECTED) break;
        }
        return out;
    }
    /** 選んだ物を保存の形へ（{@link #decode}で同じ物に戻る）。カンマや空白を含むキーは入れない。 */
    public static String encode(Collection<String> keys) {
        var out = new ArrayList<String>();
        for (var key : keys) {
            if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH || key.contains(",") || !key.equals(key.trim())) continue;
            if (out.contains(key)) continue;
            out.add(key);
            if (out.size() >= MAX_SELECTED) break;
        }
        return String.join(",", out);
    }
    /** 選んだ物を1つ切り替えた後の集まり（入っていれば外し、無ければ足す。上限を超えては足さない）。元の集まりは変えない。 */
    public static Set<String> toggle(Set<String> selected, String key) {
        var out = new LinkedHashSet<>(selected);
        if (!out.remove(key) && out.size() < MAX_SELECTED && key != null && !key.isEmpty()) out.add(key);
        return out;
    }

    /**
     * HUDに並べる行の数。total項目を、高さroomに1行lineHeightで並べる。全部入らなければ、最後の1行を「ほかN件」に使う
     * （戻り値は項目の行の数。0なら項目を出さない）。1行しか入らないときは、「ほかN件」の行を出さずに最初の1項目だけを出す。
     */
    public static int linesFor(int total, float room, float lineHeight) {
        if (total <= 0 || !(lineHeight > 0) || !(room >= lineHeight)) return 0;
        int fit = (int) Math.floor(room / lineHeight);
        if (total <= fit) return total;
        return fit <= 1 ? fit : fit - 1;
    }
}
