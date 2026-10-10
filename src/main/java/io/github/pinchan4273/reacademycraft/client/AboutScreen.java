package io.github.pinchan4273.reacademycraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 原作AppAboutのAboutUI（guis/about.xml、config/about.conf）: Aboutの窓を、742×923の4分の1の大きさで中央に置く。
 * スクロールする領域の上にCreditsとDonateのタブがある。Creditsは原作の見出し・スタッフ・寄付者・謝辞を流し、
 * ホイールか右のバーでスクロールする。Donateは、プレイヤーの言語（無ければ英語）の原作の文とリンクを表示する。
 *
 * 原作との違い: 原作は寄付者をWebサイトから取得し、失敗したらabout.confの一覧を使う。ここではサイトから取得せず、
 * 常にabout.confの一覧を使う（原作と同じく、開くたびに並びを混ぜる）。原作はリンクを直接ブラウザで開くが、
 * ここではゲーム自身のリンクの確認を先に出す。原作は深度マスクで文字を切り取るが、ここではscissorで切り取る。
 * 文字はゲームのフォントで、行の幅が原作と同じになるよう原作より少し小さくする（TEXT_UNITS）。
 * それでも領域に収まらない行は、小さくして収める。
 */
public final class AboutScreen extends Screen {
    static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/about/bg.png");
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/about/button_glow.png");
    static final ResourceLocation CONFIG = ResourceLocation.fromNamespaceAndPath("academy", "config/about.json");
    static final float W = 742, H = 923, SCALE = .25f;
    // guis/about.xml: 領域は(53, 266)。タブ・ドラッグバー・スクロール領域は、領域の単位で書く。
    static final float AREA_X = 53, AREA_Y = 266, AREA_W = 630;
    static final float TAB_W = 315, TAB_H = 58, GLOW_W = 332, GLOW_H = 80;
    static final float BAR_W = 10, BAR_H = 70, BAR_LOWER = 58, BAR_UPPER = 530;
    static final float SCROLL_Y = 58, SCROLL_W = 620, SCROLL_H = 540;
    static final float FONT_SIZE = 30;
    /**
     * ゲームのフォントを原作の大きさで使う。ゲームの字形は原作のTrueTypeフォントより幅が広く、原作の位置
     * （ラベルは-30の右、寄付のページは-280から、寄付者の列は150間隔）は原作の幅に合わせてあるので、
     * スクロール領域のラテン文字は、ゲームの9に対して12の単位で描く（9対9ではない）。
     * ゲームのCJKの字形はTrueTypeと同じく正方形で、これより小さいと読めないので、CJKを含む行は9対9のまま（CJK_UNITS）。
     * 余裕のあるタブのラベルは9のまま。
     */
    static final float TEXT_UNITS = 12, CJK_UNITS = 9;
    private static final int TEXT = 0xffffffff, LINK = 0xff5bb4ff, LINK_HOVER = 0xff8ecbff;

    public enum Tab { CREDITS, DONATE }

    record Item(float x, float y, String text, Align align, boolean bold, float size, String url) { }
    enum Align { LEFT, CENTER, RIGHT }

    private final List<Item> credits = new ArrayList<>(), donate = new ArrayList<>();
    private float creditsMaxY;
    private Tab tab = Tab.CREDITS;
    private float progress;
    private boolean dragging;
    private String hovering;
    private float x0, y0;

    public AboutScreen() {
        super(Component.translatable("academy.app.about.name"));
        initTexts();
    }

    private void initTexts() {
        JsonObject cfg;
        try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(CONFIG).open()) {
            cfg = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + CONFIG, e);
        }
        var root = cfg.getAsJsonObject("credits");
        float y = 2 * FONT_SIZE;
        for (var s : strings(root.getAsJsonArray("header"))) {
            credits.add(new Item(0, y, s, Align.CENTER, true, FONT_SIZE, null));
            y += FONT_SIZE;
        }
        y += 2 * FONT_SIZE;
        for (var e : root.getAsJsonArray("staff")) {
            var el = strings(e.getAsJsonArray());
            credits.add(new Item(-30, y, el.get(0), Align.RIGHT, true, FONT_SIZE, null));
            for (int i = 1; i < el.size(); ++i) {
                credits.add(new Item(30, y, el.get(i), Align.LEFT, false, FONT_SIZE, null));
                y += FONT_SIZE;
            }
            y += .5f * FONT_SIZE;
        }
        y += FONT_SIZE;
        credits.add(new Item(0, y, Component.translatable("academy.about.donators").getString(), Align.CENTER, true, FONT_SIZE, null));
        y += 1.1f * FONT_SIZE;
        // 原作は翻訳の文字列を、書かれた"\n"で分割する。
        for (var text : Component.translatable("academy.about.donators_info").getString().split("\\\\n|\n")) {
            credits.add(new Item(0, y, text, Align.CENTER, false, FONT_SIZE * .7f, null));
            y += FONT_SIZE * .7f;
        }
        y += 1.5f * FONT_SIZE;
        var donators = new ArrayList<>(strings(root.getAsJsonArray("donators")));
        Collections.shuffle(donators);
        for (int i = 0; i < donators.size(); ++i) {
            float tw = 150, margin = 30;
            float x = margin + (i % 3) * (620 - 2 * margin - tw) / 2 - 310;
            credits.add(new Item(x, y, donators.get(i), Align.LEFT, false, FONT_SIZE * .8f, null));
            if (i % 3 == 2) y += FONT_SIZE * .8f;
        }
        y += FONT_SIZE;
        y += FONT_SIZE;
        credits.add(new Item(0, y, Component.translatable("academy.about.thanks").getString(), Align.CENTER, true, FONT_SIZE, null));
        y += FONT_SIZE;
        creditsMaxY = y + 30;

        var donation = cfg.getAsJsonObject("donation");
        String lang = Minecraft.getInstance().options.languageCode;
        if (!donation.has(lang)) lang = "en_us";
        y = 100;
        float x = -280;
        for (var s : strings(donation.getAsJsonArray(lang))) {
            if (s.startsWith("!!")) {
                int ix = s.indexOf('|');
                y += 10;
                donate.add(new Item(x, y, s.substring(2, ix), Align.LEFT, false, 40, s.substring(ix + 1)));
                y += 50;
            } else {
                boolean right = s.startsWith("]");
                if (right) s = s.substring(1);
                donate.add(right ? new Item(-x, y, s, Align.RIGHT, false, FONT_SIZE, null) : new Item(x, y, s, Align.LEFT, false, FONT_SIZE, null));
                y += 30;
            }
        }
    }
    private static List<String> strings(JsonArray array) {
        var out = new ArrayList<String>();
        for (var e : array) out.add(e.getAsString());
        return out;
    }

    @Override protected void init() {
        super.init();
        x0 = (width - W * SCALE) / 2; y0 = (height - H * SCALE) / 2;
    }
    /** 画面の座標から、領域の単位へ変換する。 */
    private float ax(double mx) { return (float) ((mx - x0) / SCALE - AREA_X); }
    private float ay(double my) { return (float) ((my - y0) / SCALE - AREA_Y); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }
    private float barY() { return BAR_LOWER + progress * (BAR_UPPER - BAR_LOWER); }
    private float yOffset() { return tab == Tab.CREDITS ? progress * (creditsMaxY - SCROLL_H + 50) : 0; }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale(SCALE, SCALE, 1);
        DeveloperScreen.quad(pose, BG, 0, 0, W, H, 1, 1, 1, 1);
        pose.translate(AREA_X, AREA_Y, 0);
        float x = ax(mx), y = ay(my);
        tabButton(g, 0, Tab.CREDITS, "academy.about.credits", 41 / 255f, 132 / 255f, 241 / 255f, x, y);
        tabButton(g, TAB_W, Tab.DONATE, "academy.about.donate", 231 / 255f, 156 / 255f, 1, x, y);
        // スクロール領域自身の色、次にその文字を、領域で切り取って描く。
        DeveloperScreen.rect(pose, 0, SCROLL_Y, SCROLL_W, SCROLL_H, 0, 0, 0, 21 / 255f);
        g.enableScissor((int) Math.floor(x0 + AREA_X * SCALE), (int) Math.floor(y0 + (AREA_Y + SCROLL_Y) * SCALE),
                (int) Math.ceil(x0 + (AREA_X + SCROLL_W) * SCALE), (int) Math.ceil(y0 + (AREA_Y + SCROLL_Y + SCROLL_H) * SCALE));
        float offset = yOffset();
        String hover = null;
        for (var item : tab == Tab.CREDITS ? credits : donate) {
            float iy = item.y - offset;
            if (iy <= -50 || iy >= SCROLL_H + 50) continue;
            var text = Component.literal(item.text).withStyle(Style.EMPTY.withBold(item.bold));
            float s = item.size / (cjk(item.text) ? CJK_UNITS : TEXT_UNITS), tw = font.width(text) * s, anchor = SCROLL_W / 2 + item.x;
            // スクロール領域からはみ出す行は、切らずに小さくして収める。
            float room = switch (item.align) { case LEFT -> SCROLL_W - anchor; case CENTER -> 2 * Math.min(anchor, SCROLL_W - anchor); case RIGHT -> anchor; };
            if (tw > room && room > 0) { s *= room / tw; tw = room; }
            float left = anchor - switch (item.align) { case LEFT -> 0; case CENTER -> tw / 2; case RIGHT -> tw; };
            boolean over = false;
            if (item.url != null && in(x, y - SCROLL_Y, left, iy, tw, item.size)) { hover = item.url; over = true; }
            pose.pushPose();
            pose.translate(left, SCROLL_Y + iy, 0);
            pose.scale(s, s, 1);
            g.drawString(font, text, 0, 0, item.url == null ? TEXT : over ? LINK_HOVER : LINK, false);
            pose.popPose();
        }
        g.disableScissor();
        hovering = hover;
        // DragBar: 白。マウスの下では明るくする。
        boolean barHover = dragging || in(x, y, AREA_W - BAR_W, barY(), BAR_W, BAR_H);
        DeveloperScreen.rect(pose, AREA_W - BAR_W, barY(), BAR_W, BAR_H, 1, 1, 1, (barHover ? 167 : 133) / 255f);
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    static boolean cjk(String text) { return text.codePoints().anyMatch(c -> c >= 0x2e80 && c <= 0x9fff || c >= 0xac00 && c <= 0xd7af || c >= 0xff00 && c <= 0xffef); }
    /** 原作setTabButtonEnableとボタンのTint: 板、選択中の光、ラベル。 */
    private void tabButton(GuiGraphics g, float bx, Tab which, String key, float r, float gr, float b, float x, float y) {
        var pose = g.pose();
        boolean on = tab == which;
        DeveloperScreen.rect(pose, bx, 0, TAB_W, TAB_H, 1, 1, 1, on ? .5f : .2f);
        if (in(x, y, bx, 0, TAB_W, TAB_H)) DeveloperScreen.rect(pose, bx, 0, TAB_W, TAB_H, 1, 1, 1, 51 / 255f);
        if (on) DeveloperScreen.quad(pose, GLOW, bx + (TAB_W - GLOW_W) / 2, (TAB_H - GLOW_H) / 2, GLOW_W, GLOW_H, r, gr, b, 1);
        var label = Component.translatable(key);
        float s = 42 / 9f, tw = font.width(label) * s;
        pose.pushPose();
        pose.translate(bx + (TAB_W - tw) / 2, (TAB_H - 8 * s) / 2, 0);
        pose.scale(s, s, 1);
        g.drawString(font, label, 0, 0, on ? 0xff3d3f4b : 0xffffffff, false);
        pose.popPose();
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        float x = ax(mx), y = ay(my);
        if (in(x, y, 0, 0, TAB_W, TAB_H)) { setTab(Tab.CREDITS); return true; }
        if (in(x, y, TAB_W, 0, TAB_W, TAB_H)) { setTab(Tab.DONATE); return true; }
        if (in(x, y, AREA_W - BAR_W, barY(), BAR_W, BAR_H)) { dragging = true; return true; }
        if (in(x, y, 0, SCROLL_Y, SCROLL_W, SCROLL_H) && hovering != null) {
            ConfirmLinkScreen.confirmLinkNow(hovering, this, false);
            hovering = null;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            progress = Mth.clamp(progress + (float) (dy / SCALE) / (BAR_UPPER - BAR_LOWER), 0, 1);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }
    /** 原作: LWJGLのホイール1段=120に、0.001と0.2を掛ける。 */
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        progress = Mth.clamp(progress - (float) delta * 120 * .001f * .2f, 0, 1);
        return true;
    }
    /** 原作onTabTypeChanged: バーを先頭へ戻す。 */
    public void setTab(Tab type) { tab = type; progress = 0; }
    // 原作の画面はdoesGuiPauseGameを上書きしていないので、1.12の既定でシングルプレイのゲームを止めていた。
    @Override public boolean isPauseScreen() { return true; }

    // テスト用の入口。
    public Tab tab() { return tab; }
    public float progress() { return progress; }
    public void setProgress(float p) { progress = Mth.clamp(p, 0, 1); }
    public float creditsMaxY() { return creditsMaxY; }
    public List<String> texts(Tab which) { return (which == Tab.CREDITS ? credits : donate).stream().map(Item::text).toList(); }
    public List<String> links() { return donate.stream().filter(i -> i.url != null).map(Item::url).toList(); }
}
