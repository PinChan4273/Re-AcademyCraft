package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * 原作MarkdownParserとGLMarkdownRenderer（AcademyCraft commit 7b1401c内のlambdalib2の写し）。一度レイアウトし、毎フレーム描く。
 * 方言と癖は原作のもの:
 *
 * - 1〜6個の'#'で始まる行は見出し（大きさはfontSizeの1.6、1.4、1.2、1.0倍でレベル1〜4以上。原作の逆順の表による。
 *   レベル3までは太字）。'* 'は点付きの箇条、'> 'はその色の参照。'__x__'は強調（strong）、'**x**'は強調（emphasis）。
 * - '![hover](src)'は画像、'![name a=b]'はタグで、意味は記事が決める。
 * - 普通の行は行を終えない: 空行・見出し・箇条が来るまで文は続き、空行は行間を加える。
 * - 文は幅で折り返すが、最初の断片だけは幅の1.2倍まで伸ばせる。
 * - 行の残りより幅の広い画像は縮めて収め、その行の前の文を画像の下端まで持ち上げる。
 *
 * 原作のTrueTypeフォントは使わず、ゲームのフォントを原作の大きさで描き、太字と斜体はスタイルで表す。
 */
public final class TutorialMarkdown {
    private static final float[] HEADER = {1.8f, 1.6f, 1.4f, 1.2f, 1.0f};
    private static final int TEXT = 0xffffffff, REFERENCE = 0xffe1c385;
    private sealed interface Op permits Text, Dot, Image { }
    private static final class Text implements Op {
        final String text; final float x, size; float y; final boolean bold, italic; final int color;
        Text(String text, float x, float y, float size, boolean bold, boolean italic, int color) {
            this.text = text; this.x = x; this.y = y; this.size = size; this.bold = bold; this.italic = italic; this.color = color;
        }
    }
    private record Dot(float x, float y, float size) implements Op { }
    private record Image(ResourceLocation src, float x, float y, float w, float h) implements Op { }
    private enum Attr { LIST, HEADER, EMPHASIZE, STRONG, REFERENCE }

    private final Font font;
    private final float fontSize, widthLimit;
    private final float lineSpacing = 4;
    private final java.util.function.BiFunction<String, Map<String, String>, String> tags;
    private final List<Op> ops = new ArrayList<>();
    private float x, y, lastSize;
    private boolean lineBegin = true;

    /** @param tags タグの名前ごとの文字列。nullなら何も描かない（原作onTag） */
    private TutorialMarkdown(Font font, float fontSize, float widthLimit, java.util.function.BiFunction<String, Map<String, String>, String> tags) {
        this.font = font; this.fontSize = fontSize; this.widthLimit = widthLimit; this.tags = tags;
    }
    public static TutorialMarkdown layout(String content, Font font, float fontSize, float widthLimit, java.util.function.BiFunction<String, Map<String, String>, String> tags) {
        var md = new TutorialMarkdown(font, fontSize, widthLimit, tags);
        for (var line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) md.parseLine(line);
        return md;
    }
    public float maxHeight() { return y + fontSize; }

    // ---- MarkdownParser ----
    private record Piece(String text, java.util.Set<Attr> attrs, String tag, Map<String, String> tagAttrs) { }
    private static final Pattern STAR = Pattern.compile("(.*)\\*\\*(.*)"), UNDERSCORE = Pattern.compile("(.*)__(.*)"),
            IMAGE = Pattern.compile("(.*)!\\[([^\\[\\]]*)\\]\\(([^()]+)\\)(.*)"), TAG = Pattern.compile("(.*)!\\[([^\\[\\]]+)\\](.*)"),
            STRING_PROPERTY = Pattern.compile(" *([^=]+)= *\"([^\"]+)\"(.*)"), PROPERTY = Pattern.compile(" *([^=]+)= *([^ =]+)(.*)");
    private void parseLine(String ln) {
        var attrs = java.util.EnumSet.noneOf(Attr.class);
        int level = 0;
        if (ln.startsWith("#")) {
            int sharps = 0; while (sharps < ln.length() && ln.charAt(sharps) == '#') sharps++;
            level = Math.min(6, sharps); attrs.add(Attr.HEADER);
            processSpan(ln.substring(sharps).replaceFirst("^ +", ""), attrs, level);
            newline(false);
        } else if (ln.startsWith("* ")) {
            attrs.add(Attr.LIST);
            processSpan(ln.substring(2), attrs, 0);
            newline(false);
        } else if (ln.startsWith("> ")) {
            attrs.add(Attr.REFERENCE);
            processSpan(ln.substring(2), attrs, 0);
        } else {
            processSpan(ln, attrs, 0);
        }
    }
    private void processSpan(String ln, java.util.Set<Attr> attrs, int level) {
        var pieces = parseSpan(ln, attrs);
        if (pieces.isEmpty()) { newline(false); return; }
        for (var p : pieces) {
            if (p.tag() != null) onTag(p.tag(), p.tagAttrs());
            else onText(p.text(), p.attrs(), level);
        }
    }
    private static List<Piece> parseSpan(String line, java.util.Set<Attr> base) {
        var out = new ArrayList<Piece>();
        Matcher m;
        if ((m = IMAGE.matcher(line)).matches()) {
            out.addAll(parseSpan(m.group(1), base));
            var attrs = new LinkedHashMap<String, String>(); attrs.put("hover", m.group(2)); attrs.put("src", m.group(3));
            out.add(new Piece(null, base, "img", attrs));
            out.addAll(parseSpan(m.group(4), base));
        } else if ((m = TAG.matcher(line)).matches()) {
            out.addAll(parseSpan(m.group(1), base));
            out.add(parseTag(m.group(2), base));
            out.addAll(parseSpan(m.group(3), base));
        } else if ((m = STAR.matcher(line)).matches() && STAR.matcher(m.group(1)).matches()) {
            var inner = STAR.matcher(m.group(1)); inner.matches();
            out.addAll(parseSpan(inner.group(1), base));
            var a = java.util.EnumSet.copyOf(withBase(base)); a.add(Attr.EMPHASIZE);
            out.add(new Piece(inner.group(2), a, null, null));
            out.addAll(parseSpan(m.group(2), base));
        } else if ((m = UNDERSCORE.matcher(line)).matches() && UNDERSCORE.matcher(m.group(1)).matches()) {
            var inner = UNDERSCORE.matcher(m.group(1)); inner.matches();
            out.addAll(parseSpan(inner.group(1), base));
            var a = java.util.EnumSet.copyOf(withBase(base)); a.add(Attr.STRONG);
            out.add(new Piece(inner.group(2), a, null, null));
            out.addAll(parseSpan(m.group(2), base));
        } else if (!line.isEmpty()) {
            out.add(new Piece(line, base, null, null));
        }
        return out;
    }
    private static java.util.Set<Attr> withBase(java.util.Set<Attr> base) { return base.isEmpty() ? java.util.EnumSet.noneOf(Attr.class) : base; }
    private static Piece parseTag(String content, java.util.Set<Attr> base) {
        int ind = content.indexOf(' '); if (ind < 0) ind = content.length();
        var attrs = new LinkedHashMap<String, String>();
        String rest = content.substring(ind);
        while (true) {
            Matcher m;
            if ((m = STRING_PROPERTY.matcher(rest)).matches()) { attrs.put(m.group(1).trim(), m.group(2).trim()); rest = m.group(3); }
            else if ((m = PROPERTY.matcher(rest)).matches()) { attrs.put(m.group(1).trim(), m.group(2).trim()); rest = m.group(3); }
            else break;
        }
        return new Piece(null, base, content.substring(0, ind), attrs);
    }

    // ---- GLMarkdownRenderer ----
    private float width(String text, float size, boolean bold, boolean italic) {
        return font.width(Component.literal(text).withStyle(Style.EMPTY.withBold(bold).withItalic(italic))) * size / 9f;
    }
    private void onText(String text, java.util.Set<Attr> attrs, int level) {
        float size = fontSize; boolean bold = false, italic = false, list = false; int color = TEXT;
        for (var a : attrs) switch (a) {
            case LIST -> { if (lineBegin) list = true; }
            case HEADER -> { size = fontSize * HEADER[Math.min(level, HEADER.length - 1)]; if (level <= 3) bold = true; }
            case EMPHASIZE -> italic = true;
            case STRONG -> bold = true;
            case REFERENCE -> { color = REFERENCE; if (x == 0) x = size; }
        }
        final float s = size; final boolean b = bold, it = italic;
        var lines = multiline(text, str -> width(str, s, b, it), x, widthLimit);
        if (list) {
            float dot = size * .2f, indent = size * 1.2f;
            x = indent;
            ops.add(new Dot(x, y + size / 2 - dot / 2, dot));
            x += dot * 2;
        }
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            float w = width(ln, size, bold, italic);
            // 原作: "widthLimit * 1.2 is a magic number and this is a temporary hack"。
            if (i != 0 || w + x > widthLimit * 1.2f) newline(true);
            lastSize = size;
            ops.add(new Text(ln, x, y, size, bold, italic, color));
            x += w;
        }
    }
    private void newline(boolean cont) {
        x = 0;
        if (cont) y += lastSize;
        else { y += lastSize + lineSpacing; lastSize = 0; }
        lineBegin = !cont;
    }
    private void onTag(String name, Map<String, String> attrs) {
        if (name.equals("img")) { image(attrs); return; }
        String text = tags.apply(name, attrs);
        // ACMarkdownRenderer: キーの名前を参照として、ミサカの名前を太字で。
        if (text != null) onText(text, java.util.EnumSet.of(name.equals("key") ? Attr.REFERENCE : Attr.STRONG), 0);
    }
    private void image(Map<String, String> attrs) {
        var src = ResourceLocation.tryParse(attrs.getOrDefault("src", ""));
        if (src == null) return;
        float scale = attrs.containsKey("scale") ? Float.parseFloat(attrs.get("scale")) : 1;
        float w, h;
        if (attrs.containsKey("width") && attrs.containsKey("height")) {
            w = Float.parseFloat(attrs.get("width")) * scale; h = Float.parseFloat(attrs.get("height")) * scale;
        } else {
            int[] size = textureSize(src);
            w = size[0] * scale; h = size[1] * scale;
        }
        if (w + x > widthLimit) { float f = (widthLimit - x) / w; w *= f; h *= f; }
        float ix = x, iy = h >= lastSize ? y : y + lastSize - h;
        ops.add(new Image(src, ix, iy, w, h));
        float newY = y + Math.max(0, h - fontSize);
        // 原作は同じ行の前の文を画像の下端まで持ち上げる。
        for (int i = ops.size() - 2; i >= 0 && ops.get(i) instanceof Text t; i--) if (t.y == y) t.y = newY;
        x += w; y = newY; lastSize = fontSize;
    }
    /** PNGのヘッダーから読んだ大きさ。原作がbind後にGLから読み戻すのと同じ値。 */
    private static int[] textureSize(ResourceLocation src) {
        try (var in = Minecraft.getInstance().getResourceManager().open(src)) {
            byte[] head = in.readNBytes(24);
            if (head.length == 24) return new int[]{
                    (head[16] & 255) << 24 | (head[17] & 255) << 16 | (head[18] & 255) << 8 | head[19] & 255,
                    (head[20] & 255) << 24 | (head[21] & 255) << 16 | (head[22] & 255) << 8 | head[23] & 255};
        } catch (IOException ignored) { }
        return new int[]{16, 16};
    }
    /** 原作Fragmentor.toMultiline: 単語とCJKの1文字ずつに分け、最初の行はxから始める。 */
    static List<String> multiline(String text, Function<String, Float> width, float start, float limit) {
        var tokens = new ArrayList<String>();
        var word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean cjk = Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                    || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA || (c >= 0x3000 && c <= 0x303f) || (c >= 0xff00 && c <= 0xffef);
            if (c == ' ' || cjk) {
                if (word.length() > 0) { tokens.add(word.toString()); word.setLength(0); }
                tokens.add(String.valueOf(c));
            } else word.append(c);
        }
        if (word.length() > 0) tokens.add(word.toString());
        var lines = new ArrayList<String>();
        var line = new StringBuilder(); float cap = limit - start, used = 0;
        for (var token : tokens) {
            float tw = width.apply(token);
            if (used + tw > cap && line.length() > 0) {
                lines.add(line.toString()); line.setLength(0); used = 0; cap = limit;
                if (token.equals(" ")) continue;
            } else if (used + tw > cap && start > 0 && lines.isEmpty() && !trailing(token)) {
                // 既に文がある行に収まらない単語は次の行から始める。原作は1.2の余裕まで構わず置いていた。原作のTrueTypeフォントは
                // もっと細かったが、ここでは記事が上限で切られるため、余裕の分だけ単語が切れていた（行末の太字"Solar"）。
                // 句読点は前のものと一緒に置く。
                lines.add(""); used = 0; cap = limit;
                if (token.equals(" ")) continue;
            }
            line.append(token); used += tw;
        }
        if (line.length() > 0 || lines.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static boolean trailing(String token) {
        return !token.isEmpty() && ",.;:!?)]、。，）".indexOf(token.charAt(0)) >= 0;
    }

    public void render(GuiGraphics g) {
        var pose = g.pose();
        for (var op : ops) {
            if (op instanceof Text t) {
                pose.pushPose(); pose.translate(t.x, t.y, 0); pose.scale(t.size / 9f, t.size / 9f, 1);
                g.drawString(font, Component.literal(t.text).withStyle(Style.EMPTY.withBold(t.bold).withItalic(t.italic)), 0, 0, t.color, false);
                pose.popPose();
            } else if (op instanceof Dot d) {
                DeveloperScreen.rect(pose, d.x, d.y, d.size, d.size, 1, 1, 1, 1);
            } else if (op instanceof Image i) {
                DeveloperScreen.quad(pose, i.src, i.x, i.y, i.w, i.h, 1, 1, 1, 1);
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
