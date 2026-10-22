package io.github.pinchan4273.reacademycraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/**
 * F4（DebugConsole）の2ページ目の行の並べ方（QAの所見FQ-03、2026-10-17の開発司令室の指示）。
 *
 * 画面の幅を超える行は、空白の位置で折り返し、続きは字下げして次の行に置く（文字は削らない）。
 * 行がハート・空腹度・ホットバーの上に収まらなければ、ページに分ける。各ページの1行目は見出し（"AcademyCraft developer info"）、
 * 2ページ以上のときは最後の行にページ番号と操作方法を出す。折り返した1つの行は、同じページに収まるならページの境目で分けない。
 * 描画とは切り離した純粋な関数なので、文字の幅を渡せばサーバー（GameTest）でも確かめられる。
 *
 * WORK-045（FQ-03の残り）から、F4より後に描かれるHUD（能力ONのCPバー・キーのヒント、教程の通知）の描画範囲を「避ける領域」として
 * 受け取れる。避ける領域と縦に重なる行は、その左端より手前で折り返す（本文の幅を狭める）。行の左端から塞がれている行、または狭すぎる行
 * には何も描かず（空の文字列）、次の行へ進む。行が減った分はページを増やす。文字は削らず、小さくもしない。
 * 避ける領域が無いときは、従来と同じ並べ方（{@link #pages(List, int, int, ToIntFunction, BiFunction)}）。
 *
 * 避けて並べられない画面（見出しの下に、本文の1文字とページの行を置ける所が無い。極小の画面や、HUDを動かして左端を塞いだ場合）では、
 * 止まらずに失敗を返し、文字を隠さないよう避けない並べ方に戻す。そのことは{@link Layout#avoided()}がfalseになることで分かる
 * （HUDと重なりうるという、明示した制約）。
 */
public final class DebugConsoleLayout {
    /** 描画の左上（GUI座標）と行の間隔。文字は10/9倍で描く（DebugConsole.draw）。 */
    public static final int TOP = 10, LEFT = 10, LINE = 10;
    /** 画面の下からこれだけ空ける: ハート・空腹度の行（下から39）と、防具・空気の行（下から49）より上。 */
    public static final int BOTTOM_MARGIN = 52;
    /** 折り返した続きの行の字下げ。 */
    public static final String INDENT = "    ";
    /** 避ける領域との間に空ける幅（GUI座標）。 */
    public static final float GAP = 2;
    /** 避ける領域で狭められた行に、これより狭い幅（フォントの画素）しか残らなければ、その行には描かない（1語ずつの細切れにしない）。 */
    public static final int MIN_ROW_WIDTH = 60;

    /** GUI座標の四角（左・上・右・下）。F4より後に描かれるHUDの部品の範囲、または描いた行の範囲。 */
    public record Area(float left, float top, float right, float bottom) {
        /** 2つの四角が重なるか（辺が接するだけなら重ならない）。 */
        public boolean overlaps(Area o) { return o.left < right && o.right > left && o.top < bottom && o.bottom > top; }
    }
    /**
     * 避ける領域つきの並べ方の結果。avoidedがfalseなら、避けて並べられない画面のため避けない並べ方に戻した（文字は欠けないが、
     * HUDと重なりうる）。避ける領域が無いとき、または避けて並べられたときはtrue。
     */
    public record Layout(List<List<String>> pages, boolean avoided) { }
    private DebugConsoleLayout() { }

    /** この画面で1行に使える文字の幅（フォントの画素。描画は10/9倍、右に4の余白）。 */
    public static int maxTextWidth(int guiWidth) { return Math.max(1, (guiWidth - LEFT - 4) * 9 / 10); }
    /** この画面で、ハート・空腹度に重ならずに描ける行の数（1以上）。 */
    public static int rows(int guiHeight) { return Math.max(1, (guiHeight - BOTTOM_MARGIN - TOP - LINE) / LINE + 1); }
    /** 行の数nを描いたときの、最後の行の下端（GUI座標）。 */
    public static int bottom(int lines) { return TOP + LINE * lines; }
    /** row番目（0から）の行の上端と、文字の下端（10/9倍の文字の高さ10と、半画素ずらした暗い写し）。 */
    public static float rowTop(int row) { return TOP + LINE * row; }
    public static float rowBottom(int row) { return rowTop(row) + LINE + .5f; }
    /** 幅textWidth（フォントの画素）の文字をrow番目の行に描いたときの範囲（GUI座標、暗い写しを含む）。 */
    public static Area rowArea(int row, int textWidth) { return new Area(LEFT, rowTop(row), LEFT + textWidth * 10 / 9f + .5f, rowBottom(row)); }
    /**
     * row番目の行に使える文字の幅（フォントの画素）。縦に重なる避ける領域があれば、その左端（からGAP手前）までに狭める。
     * 左端から塞がれているか、MIN_ROW_WIDTHより狭ければ0（その行には描かない）。重なる領域が無ければmaxTextWidthと同じ。
     */
    public static int rowWidth(int row, int guiWidth, List<Area> avoid) {
        float right = Float.MAX_VALUE;
        for (var a : avoid) {
            if (a.top() >= rowBottom(row) || a.bottom() <= rowTop(row) || a.right() <= LEFT) continue;
            if (a.left() - GAP <= LEFT) return 0;
            right = Math.min(right, a.left() - GAP);
        }
        int full = maxTextWidth(guiWidth);
        if (right == Float.MAX_VALUE) return full;
        int width = Math.min(full, (int) Math.floor((right - LEFT - .5f) * 9 / 10));
        return width < MIN_ROW_WIDTH ? 0 : width;
    }

    /** 1行を、幅maxWidthに収まるように折り返す。続きの行はINDENTで始まる。空白で切れなければ文字の途中で切る。 */
    public static List<String> wrap(String line, int maxWidth, ToIntFunction<String> width) {
        var out = new ArrayList<String>();
        String rest = line;
        boolean first = true;
        while (true) {
            String prefix = first ? "" : INDENT;
            var parts = split(prefix, rest, maxWidth, width);
            out.add(prefix + parts[0]);
            rest = parts[1];
            first = false;
            if (rest.isEmpty()) return out;
        }
    }
    /** restの先頭を、prefixを付けて幅maxWidthに収まるだけ切り出す: {描く部分, 残り}。全部収まれば残りは空。 */
    private static String[] split(String prefix, String rest, int maxWidth, ToIntFunction<String> width) {
        if (width.applyAsInt(prefix + rest) <= maxWidth) return new String[] {rest, ""};
        // 収まる最も長い先頭部分を、空白の位置で探す（名前の後の詰め物の空白も切れ目になりうる）。
        for (int i = rest.length() - 1; i > 0; i--)
            if (rest.charAt(i) == ' ' && width.applyAsInt(prefix + rest.substring(0, i).stripTrailing()) <= maxWidth
                    && !rest.substring(0, i).isBlank()) return new String[] {rest.substring(0, i).stripTrailing(), rest.substring(i).stripLeading()};
        // 空白で切れない: 収まるだけの文字で切る（最低1文字）。
        int n = 1;
        while (n < rest.length() && width.applyAsInt(prefix + rest.substring(0, n + 1)) <= maxWidth) n++;
        return new String[] {rest.substring(0, n), rest.substring(n)};
    }

    /**
     * 行をページに分ける。lines.get(0)は見出しで、各ページの1行目に置く。footer(page, count)は2ページ以上のときの最後の行
     * （pageは1から）。戻り値は、ページごとに描く行。
     */
    public static List<List<String>> pages(List<String> lines, int guiWidth, int guiHeight, ToIntFunction<String> width,
                                           BiFunction<Integer, Integer, String> footer) {
        if (lines.isEmpty()) return List.of(List.of());
        int maxWidth = maxTextWidth(guiWidth), rows = rows(guiHeight);
        var title = wrap(lines.get(0), maxWidth, width);
        var groups = new ArrayList<List<String>>();
        for (var line : lines.subList(1, lines.size())) groups.add(wrap(line, maxWidth, width));
        int total = groups.stream().mapToInt(List::size).sum();
        if (title.size() + total <= rows) {
            var single = new ArrayList<>(title);
            groups.forEach(single::addAll);
            return List.of(single);
        }
        // 見出しとページの行を除いた、本文の行の数（最低1）。
        int capacity = Math.max(1, rows - title.size() - 1);
        var bodies = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        for (var group : groups) {
            if (!current.isEmpty() && current.size() + group.size() > capacity) { bodies.add(current); current = new ArrayList<>(); }
            for (var segment : group) {
                if (current.size() == capacity) { bodies.add(current); current = new ArrayList<>(); }
                current.add(segment);
            }
        }
        if (!current.isEmpty()) bodies.add(current);
        var result = new ArrayList<List<String>>();
        for (int i = 0; i < bodies.size(); i++) {
            var page = new ArrayList<>(title);
            page.addAll(bodies.get(i));
            page.add(footer.apply(i + 1, bodies.size()));
            result.add(page);
        }
        return result;
    }

    /** 避ける領域つきのページ分けの、ページだけ（{@link #layout}を参照）。 */
    public static List<List<String>> pages(List<String> lines, int guiWidth, int guiHeight, ToIntFunction<String> width,
                                           BiFunction<Integer, Integer, String> footer, List<Area> avoid) {
        return layout(lines, guiWidth, guiHeight, width, footer, avoid).pages();
    }

    /**
     * 避ける領域つきのページ分け。avoidが空なら従来のpagesと同じ。戻り値の各ページは、上の行から順に描く文字列で、空の文字列は
     * 「この行には描かない」（避ける領域に左端から塞がれた行、または狭すぎる行）。見出しは各ページの最初の行（必要なら折り返す）、
     * 2ページ以上のときはページ番号と操作方法を最後に置く。折り返した1つの行は、同じページに収まるならページの境目で分けない。
     * 避けて並べられない画面では、文字を隠さないよう従来の並べ方に戻し、avoided=falseを返す。どの入力でも有限の手数で終わる。
     */
    public static Layout layout(List<String> lines, int guiWidth, int guiHeight, ToIntFunction<String> width,
                                BiFunction<Integer, Integer, String> footer, List<Area> avoid) {
        if (avoid.isEmpty() || lines.isEmpty()) return new Layout(pages(lines, guiWidth, guiHeight, width, footer), true);
        int[] widths = new int[rows(guiHeight)];
        for (int r = 0; r < widths.length; r++) widths[r] = rowWidth(r, guiWidth, avoid);
        var laid = avoiding(lines, widths, width, footer);
        return laid != null ? new Layout(laid, true) : new Layout(pages(lines, guiWidth, guiHeight, width, footer), false);
    }

    /** 1つの行の、まだ置いていない残り。 */
    private static final class Rest {
        String text; boolean first = true;
        Rest(String text) { this.text = text; }
        boolean done() { return !first && text.isEmpty(); }
        Rest copy() { var r = new Rest(text); r.first = first; return r; }
        /** beforeから先へ進んだか（文字を置いたか、空の行を置き終えたか）。 */
        boolean advancedFrom(Rest before) { return text.length() < before.text.length() || (before.first && !first); }
        void set(Rest other) { text = other.text; first = other.first; }
    }
    /**
     * restを、row番目からlimitの手前までの行に置き、outへ足す（塞がれた行には空の文字列）。全部置けたら次の行の番号を返す。
     * whole=trueで置ききれなければ、何も足さず、restも変えずに-1を返す。whole=falseなら置けるだけ置いて（restは残りになる）、
     * 最後に置いた文字の次の行の番号を返す（後ろの空の行は足さない）。足す行の数は、いつも戻り値−row（失敗の-1を除く）。
     */
    private static int place(Rest rest, int row, int limit, int[] widths, ToIntFunction<String> width, List<String> out, boolean whole) {
        var added = new ArrayList<String>();
        String text = rest.text; boolean first = rest.first;
        int r = row;
        while (first || !text.isEmpty()) {
            if (r >= limit) {
                if (whole) return -1;
                break;
            }
            if (widths[r] <= 0) { added.add(""); r++; continue; }
            String prefix = first ? "" : INDENT;
            var parts = split(prefix, text, widths[r], width);
            added.add(prefix + parts[0]);
            text = parts[1]; first = false; r++;
        }
        if (!whole) while (!added.isEmpty() && added.get(added.size() - 1).isEmpty()) { added.remove(added.size() - 1); r--; }
        rest.text = text; rest.first = first;
        out.addAll(added);
        return r;
    }
    private static int place(String line, int row, int limit, int[] widths, ToIntFunction<String> width, List<String> out) {
        return place(new Rest(line), row, limit, widths, width, out, true);
    }
    /** row番目から、ページの行（sample）を置けるか。 */
    private static boolean footerFits(String sample, int row, int[] widths, ToIntFunction<String> width) {
        return place(sample, row, widths.length, widths, width, new ArrayList<>()) >= 0;
    }

    /** 見出しの行と、ページごとの本文の行（各ページの本文の後ろには、ページの行を置ける）。 */
    private record Paged(List<String> title, List<List<String>> bodies) { }

    private static List<List<String>> avoiding(List<String> lines, int[] widths, ToIntFunction<String> width,
                                               BiFunction<Integer, Integer, String> footer) {
        int rows = widths.length;
        // 全部が1ページに収まれば、ページの行は付けない。
        var single = new ArrayList<String>();
        int at = 0;
        for (var line : lines) { if ((at = place(line, at, rows, widths, width, single)) < 0) break; }
        if (at >= 0) return List.of(single);
        // ページの行は、ページ数の桁の文言（99/99）で場所を取っておく（数字の幅はどれも同じなので、実際の文言はこれより広くならない）。
        // ページが99を超えたら、桁を増やして並べ直す（ページの数は文字の数を超えないので、桁はいずれ足りる）。
        for (int most = 99; ; most = most * 10 + 9) {
            var paged = paginate(lines, widths, width, footer.apply(most, most));
            if (paged == null) return null;
            if (paged.bodies().size() > most) continue;
            var result = new ArrayList<List<String>>();
            for (int i = 0; i < paged.bodies().size(); i++) {
                var page = new ArrayList<>(paged.title());
                page.addAll(paged.bodies().get(i));
                if (place(footer.apply(i + 1, paged.bodies().size()), page.size(), rows, widths, width, page) < 0) return null;
                result.add(page);
            }
            return result;
        }
    }

    /**
     * 見出しの下に本文を並べ、ページに分ける。各ページの本文は、その後ろにページの行（sample）を置ける所で終える。
     * 進まない並べ方は採らない: ページを閉じるのは本文があるときだけで、本文の無いページでは、少なくとも1文字（または空の行）を
     * 置ける並べ方だけを採る。それも無ければnull（呼び出し側は避けない並べ方に戻す）。重ねて置かないよう、試しに置いた行は毎回捨てる。
     */
    private static Paged paginate(List<String> lines, int[] widths, ToIntFunction<String> width, String sample) {
        int rows = widths.length;
        var title = new ArrayList<String>();
        int bodyStart = place(lines.get(0), 0, rows, widths, width, title);
        if (bodyStart < 0) return null;
        var bodies = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        int row = bodyStart;
        for (var line : lines.subList(1, lines.size())) {
            var rest = new Rest(line);
            while (!rest.done()) {
                // 残りを丸ごと今のページに置けて、その後にページの行も置けるなら置く。
                var whole = new ArrayList<String>();
                int end = place(rest.copy(), row, rows, widths, width, whole, true);
                if (end >= 0 && footerFits(sample, end, widths, width)) { current.addAll(whole); row = end; break; }
                // 今のページに本文があれば閉じ、次のページで置き直す（閉じたページの後ろにはページの行を置ける）。
                if (!current.isEmpty()) { bodies.add(current); current = new ArrayList<>(); row = bodyStart; continue; }
                // 本文の無いページにも丸ごとは収まらない: 後ろにページの行を置ける範囲で、置けるだけ置く（広い範囲から試す）。
                List<String> part = null;
                Rest after = null;
                int next = -1;
                for (int limit = rows; limit > row && part == null; limit--) {
                    var tryRest = rest.copy();
                    var tryOut = new ArrayList<String>();
                    int n = place(tryRest, row, limit, widths, width, tryOut, false);
                    if (!tryOut.isEmpty() && tryRest.advancedFrom(rest) && footerFits(sample, n, widths, width)) { part = tryOut; after = tryRest; next = n; }
                }
                // このページでは1文字も進められない（見出しの下に、本文とページの行を一緒に置ける所が無い）: 失敗として返す。
                if (part == null) return null;
                current.addAll(part); rest.set(after); row = next;
                if (!rest.done()) { bodies.add(current); current = new ArrayList<>(); row = bodyStart; }
            }
        }
        if (!current.isEmpty()) bodies.add(current);
        return new Paged(title, bodies);
    }
}
