package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.tutorial.TutorialState;
import io.github.pinchan4273.reacademycraft.tutorial.Tutorials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 原作GuiTutorial（AcademyCraft commit 7b1401c、guis/tutorial.xml）: ミサカクラウド。427x240の枠を中央に置き、
 * 画面の幅に合わせて拡大縮小する（幅480で1倍）。左に記事の一覧（解放済みは白、それ以外は灰色）。右には、選ぶまでは
 * 原作の4つのロゴ、選んだ後は記事: 中央に本文とスクロールバー（解放済みの記事だけ）、右下にタイトルと概要、右上にプレビュー。
 *
 * セッションで初めて開いたときは、ロゴが順にフェードインし、ミサカのロゴが落ちて収まり、光る線が2本伸び、2秒後に一覧が
 * 現れる。以降は最初から表示される。記事を選ぶとロゴは0.3秒でフェードアウトする。
 *
 * 各記事はacademy:tutorials/(言語)/(id).mdから読み、プレイヤーの言語のものが無ければ英語のものを読む（原作と同じ）。
 * 原作には英語と中国語の記事がある。
 */
public final class TutorialScreen extends Screen {
    static final float FRAME_W = 427, FRAME_H = 240, REF_WIDTH = 480;
    // guis/tutorial.xmlを、その揃え方に従って解決したもの。
    static final float LEFT_X = 7, PART_Y = 9.75f, LEFT_W = 85, PART_H = 220.5f, LIST_X = 6.6f, LIST_Y = 7, LIST_W = 72, ROW_H = 12;
    static final float RIGHT_X = 92, CENTER_W = 172, TEXT_X = 2, TEXT_Y = 5, TEXT_W = 160, TEXT_H = 210.5f;
    static final float SCROLL_X = 162.5f, SCROLL_W = 9.5f, SCROLL_Y = 2, SCROLL_H = 216.5f, THUMB_H = 53, THUMB_LOWER = 2, THUMB_UPPER = 165;
    static final float WINDOW_X = 173.5f, WINDOW_W = 158.5f, INFO_Y = 138.5f, INFO_H = 82;
    // showWindow: 領域、タグの行、2つの矢印（30x130の0.4倍）。
    static final float AREA_X = WINDOW_X + 12.25f, AREA_Y = -1, TAG_X = WINDOW_X + 12, TAG_Y = 120.75f, TAG = 18, TAG_STEP = 17;
    static final float ARROW_LEFT_X = WINDOW_X + 5, ARROW_RIGHT_X = WINDOW_X + 140, ARROW_Y = 41.75f, ARROW_W = 12, ARROW_H = 52;
    private static final ResourceLocation ARROW_LEFT = tex("guis/button/button_left_2"), ARROW_RIGHT = tex("guis/button/button_right_2");
    private static final ResourceLocation WINDOW = tex("guis/window_tutorial_left"), SCROLL_1 = tex("guis/button/widget_scroll_1"),
            SCROLL_2 = tex("guis/button/widget_scroll_2"), LOGO0 = tex("guis/tutorial/logo0"), LOGO1 = tex("guis/tutorial/logo1"),
            LOGO2 = tex("guis/tutorial/logo2"), LOGO3 = tex("guis/tutorial/logo3");
    private static boolean openedThisSession;

    private final List<Tutorials.Tutorial> learned = new ArrayList<>(), unlearned = new ArrayList<>();
    private final boolean firstOpen;
    private final double start;
    @Nullable private Tutorials.Tutorial current;
    private boolean currentLearned;
    private double chosenAt = -1;
    private float scale, x0, y0;
    private float thumbY = THUMB_LOWER;
    private boolean dragging;
    private float grab;
    private final Map<String, Info> cache = new HashMap<>();
    private List<TutorialPreviews.Group> groups = List.of();
    private int previewIndex;
    private int[] viewIndex = new int[0];
    private record Info(String title, TutorialMarkdown brief, TutorialMarkdown content) { }

    public TutorialScreen() {
        super(Component.translatable("item.academy.tutorial"));
        var player = Minecraft.getInstance().player;
        for (var t : Tutorials.all()) (TutorialState.isActivated(player, t) ? learned : unlearned).add(t);
        // 原作は"AC_Tutorial_Open"をクライアントのプレイヤーのentity dataに置き、セッションの間続く。
        firstOpen = !openedThisSession;
        openedThisSession = true;
        start = now();
    }
    public static void open() { Minecraft.getInstance().setScreen(new TutorialScreen()); }
    private static ResourceLocation tex(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    private static double now() { return Util.getMillis() / 1000.0; }

    /** プレイヤーの言語の記事本文。無ければ英語、それも無ければ原作の代わりの文。 */
    static String content(Tutorials.Tutorial tutorial) {
        var manager = Minecraft.getInstance().getResourceManager();
        for (var lang : List.of(Minecraft.getInstance().options.languageCode, "en_us")) {
            var location = ResourceLocation.fromNamespaceAndPath("academy", "tutorials/" + lang + "/" + tutorial.id() + ".md");
            var resource = manager.getResource(location);
            if (resource.isPresent()) try (var in = resource.get().open()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) { }
        }
        return "![title]\nUNKNOWN \n![brief]\n![content]\n ";
    }
    /** 原作ACTutorial.getTitle。 */
    public static String title(Tutorials.Tutorial tutorial) {
        String raw = content(tutorial);
        int i1 = raw.indexOf("![title]"), i2 = raw.indexOf("![brief]");
        return i1 >= 0 && i2 > i1 ? raw.substring(i1 + 8, i2).trim() : tutorial.id();
    }
    private Info info(Tutorials.Tutorial tutorial) {
        return cache.computeIfAbsent(tutorial.id(), id -> {
            String raw = content(tutorial);
            int i1 = raw.indexOf("![title]"), i2 = raw.indexOf("![brief]"), i3 = raw.indexOf("![content]");
            if (!(i1 >= 0 && i1 < i2 && i2 < i3)) throw new IllegalStateException("Malformed tutorial " + id);
            var player = Minecraft.getInstance().player;
            // ACMarkdownRenderer: ![misakaname]はプレイヤーのミサカ番号を太字で。
            java.util.function.BiFunction<String, java.util.Map<String, String>, String> tags = (name, attrs) -> switch (name) {
                case "misakaname" -> Component.translatable("academy.tutorial.misaka", TutorialState.misakaId(player)).getString();
                // ACMarkdownRenderer: そのidに割り当てたキーの名前。知らないidなら???。
                case "key" -> keyName(attrs.getOrDefault("id", ""));
                default -> null;
            };
            // 原作は先頭だけtrimする。後ろの改行を原作のフォントは何も描かないが、ゲームのフォントは描く。
            return new Info(raw.substring(i1 + 8, i2).trim(),
                    TutorialMarkdown.layout(trimHead(raw.substring(i2 + 8, i3)), font, 8, 130, tags),
                    TutorialMarkdown.layout(trimHead(raw.substring(i3 + 10)), font, 8, 150, tags));
        });
    }
    private static String keyName(String id) {
        try { return TerminalSettingsScreen.mapping(id).getTranslatedKeyMessage().getString(); }
        catch (IllegalArgumentException unknown) { return "???"; }
    }
    private static String trimHead(String s) {
        int i = 0; while (i < s.length() && (s.charAt(i) == '\r' || s.charAt(i) == '\n' || s.charAt(i) == ' ')) i++;
        return s.substring(i);
    }

    @Override protected void init() {
        super.init();
        scale = width / REF_WIDTH;
        x0 = (width - FRAME_W * scale) / 2; y0 = (height - FRAME_H * scale) / 2;
    }
    private float fx(double mx) { return (float) ((mx - x0) / scale); }
    private float fy(double my) { return (float) ((my - y0) / scale); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }
    private boolean listShown() { return !firstOpen || now() - start - .4 > 2.0; }
    private List<Tutorials.Tutorial> rows() { var all = new ArrayList<>(learned); all.addAll(unlearned); return all; }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        float x = fx(mx), y = fy(my);
        if (button == 0 && listShown()) {
            float lx = LEFT_X + LIST_X, ly = PART_Y + LIST_Y;
            var rows = rows();
            if (in(x, y, lx, ly, LIST_W, rows.size() * ROW_H)) {
                choose(rows.get((int) ((y - ly) / ROW_H)));
                return true;
            }
        }
        if (button == 0 && current != null) {
            float rx = x - RIGHT_X, ry = y - PART_Y;
            for (int i = 0; i < groups.size(); i++)
                if (in(rx, ry, TAG_X + i * TAG_STEP, TAG_Y, TAG, TAG)) { previewIndex = i; return true; }
            if (arrows() && in(rx, ry, ARROW_LEFT_X, ARROW_Y, ARROW_W, ARROW_H)) { step(-1); return true; }
            if (arrows() && in(rx, ry, ARROW_RIGHT_X, ARROW_Y, ARROW_W, ARROW_H)) { step(1); return true; }
        }
        if (button == 0 && current != null && currentLearned && in(x, y, RIGHT_X + SCROLL_X, PART_Y + thumbY, SCROLL_W, THUMB_H)) {
            dragging = true; grab = y - (PART_Y + thumbY); return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { thumbY = Mth.clamp(fy(my) - grab - PART_Y, THUMB_LOWER, THUMB_UPPER); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) { dragging = false; return super.mouseReleased(mx, my, button); }
    /** 原作の一覧クリック: 最初の選択でロゴをフェードアウトし、窓を表示する。 */
    public void choose(Tutorials.Tutorial tutorial) {
        if (current == null) chosenAt = now();
        if (current == null || current != tutorial) {
            current = tutorial;
            currentLearned = TutorialState.isActivated(Minecraft.getInstance().player, tutorial);
            thumbY = THUMB_LOWER;
            groups = TutorialPreviews.groups(tutorial);
            previewIndex = 0; viewIndex = new int[groups.size()];
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        double t = now();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0); pose.scale(scale, scale, 1);
        float mouseX = fx(mx), mouseY = fy(my);
        // 左: 窓と一覧。
        float leftAlpha = firstOpen ? blend(t, 1.75, .3) : 1;
        DeveloperScreen.quad(pose, WINDOW, LEFT_X, PART_Y, LEFT_W, PART_H, 1, 1, 1, leftAlpha);
        if (listShown()) {
            float lx = LEFT_X + LIST_X, ly = PART_Y + LIST_Y;
            var rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                float ry = ly + i * ROW_H;
                if (in(mouseX, mouseY, lx, ry, LIST_W, ROW_H)) DeveloperScreen.rect(pose, lx, ry, LIST_W, ROW_H, 1, 1, 1, .3f);
                // 原作: 解放済みの記事は白、それ以外は灰色（0.6）。
                int color = i < learned.size() ? 0xffffffff : 0xff999999;
                text(g, info(row).title(), lx + 3, ry + (ROW_H - 10) / 2, 10, LIST_W - 3, color);
            }
        }
        pose.pushPose(); pose.translate(RIGHT_X, PART_Y, 0);
        drawLogos(g, t);
        net.minecraft.world.item.ItemStack hovered = null;
        if (current != null) {
            drawArticle(g, mouseX - RIGHT_X, mouseY - PART_Y);
            hovered = drawPreview(g, mouseX - RIGHT_X, mouseY - PART_Y, t);
        }
        pose.popPose();
        pose.popPose();
        super.render(g, mx, my, partial);
        if (hovered != null) g.renderTooltip(font, hovered, mx, my);
    }
    /** 枠単位の大きさ・指定の色で文字を描き、幅を超えるときは縮める。 */
    private void text(GuiGraphics g, String text, float x, float y, float size, float width, int argb) {
        float s = size / 9f;
        if (font.width(text) * s > width && font.width(text) > 0) s = width / font.width(text);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(s, s, 1);
        g.drawString(font, text, 0, 0, argb, false);
        g.pose().popPose();
    }
    /** 原作のblend: startまでは0、画面を開いてからtin秒かけて1まで上がる。 */
    private float blend(double t, double from, double tin) {
        double d = t - start;
        return (float) Mth.clamp(d < from ? 0 : (d - from < tin ? (d - from) / tin : 1), 0, 1);
    }
    private void drawLogos(GuiGraphics g, double t) {
        float out = chosenAt < 0 ? 1 : (float) (1 - Mth.clamp((t - chosenAt) / .3, 0, 1));
        if (out <= 0) return;
        var pose = g.pose();
        float lw = 899 * .25f, lx = (332 - lw) / 2;
        float y1 = (PART_H - 236 * .25f) / 2 + 59, y0 = (PART_H - 548 * .25f) / 2 - 32.5f;
        float a2 = firstOpen ? blend(t, .65, .3) : 1, a0 = firstOpen ? blend(t, 1.75, .3) : 1, a1 = firstOpen ? blend(t, 1.3, .3) : 1, a3 = firstOpen ? blend(t, .1, .3) : 1;
        DeveloperScreen.quad(pose, LOGO1, lx, y1, lw, 236 * .25f, 1, 1, 1, a1 * out);
        DeveloperScreen.quad(pose, LOGO0, lx, y0, lw, 548 * .25f, 1, 1, 1, a0 * out);
        float s3 = 149 * .25f;
        double lambda = firstOpen ? Mth.clamp((t - start - .7) / .4, 0, 1) : 1;
        float y3 = (PART_H - s3) / 2 + (float) Mth.lerp(lambda, 63, -36);
        DeveloperScreen.quad(pose, LOGO3, (332 - s3) / 2, y3, s3, s3, 1, 1, 1, a3 * out);
        DeveloperScreen.quad(pose, LOGO2, lx, y1, lw, 236 * .25f, 1, 1, 1, a2 * out);
        // 光る線（logo1自身の単位。logo1は0.25倍で描く）: その中央から15下。
        pose.pushPose();
        pose.translate(lx, y1, 0);
        pose.scale(.25f, .25f, 1);
        pose.translate(899 / 2f, 236 / 2f + 15, 0);
        final double ln = 500, ln2 = 300, cl = 50; final float ht = 5;
        if (!firstOpen) {
            lineglow(g, ln - ln2, ln, ht, out); lineglow(g, -ln, -(ln - ln2), ht, out);
        } else {
            double dt = Math.max(0, t - start - .4), b1 = .3, b2 = .2;
            if (dt < b1) {
                if (dt > 0) {
                    double len = Mth.lerp(dt / b1, 0, ln);
                    if (len > cl) { lineglow(g, cl, len, ht, out); lineglow(g, -len, -cl, ht, out); }
                }
            } else {
                double ldt = Math.min(dt - b1, b2), len2 = Mth.lerp(ldt / b2, ln - 2 * cl, ln2);
                lineglow(g, ln - len2, ln, ht, out); lineglow(g, -ln, -(ln - len2), ht, out);
            }
        }
        pose.popPose();
    }
    /** 原作lineglow: 細いバーの周りの光と、白いバー。 */
    private static void lineglow(GuiGraphics g, double x0, double x1, float ht, float alpha) {
        KeyHintHud.glow(g.pose(), (float) x0, -1, (float) (x1 - x0), ht - 2, 5, 0xffffff, alpha);
        DeveloperScreen.rect(g.pose(), (float) x0, -ht / 2, (float) (x1 - x0), ht, 1, 1, 1, alpha);
    }
    private void drawArticle(GuiGraphics g, float mx, float my) {
        var pose = g.pose();
        var info = info(current);
        if (currentLearned) {
            // centerPart: 内容。枠で切り、バーでスクロールする。
            DeveloperScreen.quad(pose, SCROLL_1, SCROLL_X, SCROLL_Y, SCROLL_W, SCROLL_H, 1, 1, 1, 1);
            float thumbAlpha = in(mx, my, SCROLL_X, thumbY, SCROLL_W, THUMB_H) || dragging ? 1 : 204 / 255f;
            DeveloperScreen.quad(pose, SCROLL_2, SCROLL_X, thumbY, SCROLL_W, THUMB_H, 1, 1, 1, thumbAlpha);
            float progress = (thumbY - THUMB_LOWER) / (THUMB_UPPER - THUMB_LOWER);
            float delta = progress * Math.max(0, info.content().maxHeight() - TEXT_H + 10);
            var m = pose.last().pose();
            var topLeft = m.transform(new org.joml.Vector4f(TEXT_X, TEXT_Y, 0, 1));
            var bottomRight = m.transform(new org.joml.Vector4f(TEXT_X + TEXT_W, TEXT_Y + TEXT_H, 0, 1));
            g.enableScissor((int) topLeft.x(), (int) topLeft.y(), (int) Math.ceil(bottomRight.x()), (int) Math.ceil(bottomRight.y()));
            pose.pushPose(); pose.translate(TEXT_X + 3, TEXT_Y + 3 - delta, 0);
            info.content().render(g);
            pose.popPose();
            g.disableScissor();
        }
        // rightWindow: タイトルと概要。
        DeveloperScreen.quad(pose, WINDOW, WINDOW_X, INFO_Y, WINDOW_W, INFO_H, 1, 1, 1, 1);
        float tx = WINDOW_X + (WINDOW_W - 146) / 2, ty = INFO_Y + (INFO_H - 69) / 2 - .25f;
        text(g, info.title(), tx + 3, ty + 3, 10, 140, 0xffffffff);
        pose.pushPose(); pose.translate(tx + 3, ty + 15, 0);
        info.brief().render(g);
        pose.popPose();
    }
    /** 原作btn_leftとbtn_right: 選んだグループに表示が2つ以上あるとき出す。 */
    private boolean arrows() { return !groups.isEmpty() && groups.get(previewIndex).views().size() > 1; }
    public void step(int by) {
        if (groups.isEmpty()) return;
        int n = groups.get(previewIndex).views().size();
        if (n > 0) viewIndex[previewIndex] = Math.floorMod(viewIndex[previewIndex] + by, n);
    }
    /** showWindow: 選んだグループの表示、その下のタグ、横の矢印。 */
    @Nullable private net.minecraft.world.item.ItemStack drawPreview(GuiGraphics g, float mx, float my, double t) {
        var pose = g.pose();
        net.minecraft.world.item.ItemStack hovered = null;
        if (!groups.isEmpty()) {
            var group = groups.get(previewIndex);
            if (!group.views().isEmpty()) {
                pose.pushPose(); pose.translate(AREA_X, AREA_Y, 0);
                hovered = group.views().get(viewIndex[previewIndex]).draw(g, mx - AREA_X, my - AREA_Y, t);
                pose.popPose();
            }
            if (arrows()) {
                DeveloperScreen.quad(pose, ARROW_LEFT, ARROW_LEFT_X, ARROW_Y, ARROW_W, ARROW_H, 1, 1, 1, in(mx, my, ARROW_LEFT_X, ARROW_Y, ARROW_W, ARROW_H) ? 1 : .8f);
                DeveloperScreen.quad(pose, ARROW_RIGHT, ARROW_RIGHT_X, ARROW_Y, ARROW_W, ARROW_H, 1, 1, 1, in(mx, my, ARROW_RIGHT_X, ARROW_Y, ARROW_W, ARROW_H) ? 1 : .8f);
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            float x = TAG_X + i * TAG_STEP;
            boolean hover = in(mx, my, x, TAG_Y, TAG, TAG);
            // 原作: タグは灰色（0.7）のアルファ、マウスの下では白、文字は行の上。
            DeveloperScreen.quad(pose, groups.get(i).tag(), x, TAG_Y, TAG, TAG, 1, 1, 1, hover ? 1 : .7f);
            if (hover) text(g, groups.get(i).text(), TAG_X, TAG_Y - 8, 10, 200, 0xffffffff);
        }
        return hovered;
    }
    // 原作の画面はdoesGuiPauseGameを上書きしていないので、1.12の既定でシングルプレイのゲームを一時停止した。
    @Override public boolean isPauseScreen() { return true; }

    /** テスト用の入口。 */
    @Nullable public Tutorials.Tutorial current() { return current; }
    public List<Tutorials.Tutorial> learned() { return learned; }
    public List<Tutorials.Tutorial> unlearned() { return unlearned; }
    public boolean listVisible() { return listShown(); }
    public float contentHeight() { return current == null ? 0 : info(current).content().maxHeight(); }
    public List<TutorialPreviews.Group> previewGroups() { return groups; }
    public int previewIndex() { return previewIndex; }
    public void showPreview(int index) { if (index >= 0 && index < groups.size()) previewIndex = index; }
    public void scrollTo(float progress) { thumbY = THUMB_LOWER + Mth.clamp(progress, 0, 1) * (THUMB_UPPER - THUMB_LOWER); }
}
