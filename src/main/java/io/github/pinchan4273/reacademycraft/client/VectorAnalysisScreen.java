package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.VectorAnalysisSync;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettingsVisibility;
import io.github.pinchan4273.reacademycraft.skill.VectorAnalysis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * データ端末の「ベクトル解析」アプリ（1.5.2、原作に無い）: ベクトル操作の解析度の一覧を見るだけの画面。ウィンドウ・文字の大きさ・
 * 動かし方は能力設定の画面（{@link AbilitySettingsScreen}）と同じ物を使う。並びは{@link AnalysisHudStyle#sorted}（分類の順）、
 * 割合は切り捨ての整数で、解析しきった物は金色。一番下の2行分に、今のHUDの表示の形式と、変える場所（能力設定）を出す。
 * 一覧は届いた解析度（{@link ClientVectorAnalysis}）から毎tick作り直す。いつも100%のバニラの物理は一覧に無い（空のときの案内で伝える）。
 */
public final class VectorAnalysisScreen extends Screen {
    static final float W = AbilitySettingsScreen.W, H = AbilitySettingsScreen.H, SCALE = AbilitySettingsScreen.SCALE, TEXT = AbilitySettingsScreen.TEXT;
    static final float AREA_X = AbilitySettingsScreen.AREA_X, AREA_Y = AbilitySettingsScreen.AREA_Y, AREA_H = AbilitySettingsScreen.AREA_H;
    static final float ROW_W = AbilitySettingsScreen.ROW_W, ROW_H = AbilitySettingsScreen.ROW_H;
    /**
     * 下の案内の高さ（2行分）。案内は1行に収まらない長さなので、文字の大きさ{@link #TEXT}のまま3行まで折り返す
     * （1行の欄では{@link TerminalHud#text}が縮めて描き、GUIスケール2でつぶれて見えた）。
     * 3行は 4 + 2×(TEXT + 4) + TEXT = 120 で、ちょうど2行分に収まる。
     */
    static final float HINT_H = ROW_H * 2;
    /** 一覧を動かす範囲の高さ（この画面だけの値。能力設定の画面の{@link AbilitySettingsScreen#VIEW_H}は変えない）。 */
    static final float VIEW_H = AREA_H - HINT_H;
    /** 割合の文字の幅（行の右端にそろえる）。 */
    static final float PERCENT_W = 130;
    static final Identifier ICON = Identifier.fromNamespaceAndPath("academy", "textures/guis/icons/icon_vecmanip.png");

    private final Screen parent;
    private final List<VectorAnalysisSync.Entry> entries = new ArrayList<>();
    private float x0, y0, progressBar;
    private boolean dragging;

    public VectorAnalysisScreen(@Nullable Screen parent) {
        super(Component.translatable("academy.app.vector_analysis.name"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        x0 = (width - W * SCALE) / 2; y0 = (height - H * SCALE) / 2;
        refresh();
    }
    @Override public void tick() {
        super.tick();
        refresh();
    }
    /** 届いた解析度から並べ直す。先頭の行の位置は保ったまま、動かせる範囲へ収める。 */
    private void refresh() {
        var next = AnalysisHudStyle.sorted(ClientVectorAnalysis.entries());
        int keep = first();
        entries.clear();
        entries.addAll(next);
        int max = maxFirst();
        progressBar = max == 0 ? 0 : Mth.clamp((float) keep / max, 0, 1);
    }
    /** 一度に見える行の数。 */
    private static int visible() { return Math.max(1, (int) (VIEW_H / ROW_H)); }
    private int maxFirst() { return Math.max(0, entries.size() - visible()); }
    private int first() { return Math.round(progressBar * maxFirst()); }
    private float barY() { return TerminalSettingsScreen.BAR_LOWER + progressBar * (TerminalSettingsScreen.BAR_UPPER - TerminalSettingsScreen.BAR_LOWER); }
    private float ux(double mx) { return (float) ((mx - x0) / SCALE); }
    private float uy(double my) { return (float) ((my - y0) / SCALE); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) { }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        // 1.20.1のrenderBackground(g)に当たる物（AbilitySettingsScreenと同じ）: ワールドの上の半透明の背景。
        extractTransparentBackground(g);
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale(SCALE, SCALE);
        AbilitySettingsScreen.window(g);
        DeveloperScreen.quad(g, ICON, AbilitySettingsScreen.TITLE_X, AbilitySettingsScreen.TITLE_TOP + 4, 44, 44, 1, 1, 1, 1);
        TerminalHud.text(g, font, title.getString(), AbilitySettingsScreen.TITLE_X + 56, AbilitySettingsScreen.TITLE_TOP, ROW_W - 80,
                AbilitySettingsScreen.TITLE_BOTTOM - AbilitySettingsScreen.TITLE_TOP, TEXT, 0, 1, 235);
        if (entries.isEmpty()) note(g, Component.translatable(AbilitySettingsVisibility.ANALYSIS_EMPTY), AREA_X, AREA_Y, 190);
        int from = first();
        for (int i = from; i < Math.min(entries.size(), from + visible()); i++) row(g, entries.get(i), AREA_X, AREA_Y + (i - from) * ROW_H);
        // 一番下の2行分: 今のHUDの表示の形式と、変える場所。縮めずに行の幅で折り返す。
        var mode = Component.translatable(ClientSettings.analysisHudMode().translation());
        note(g, Component.translatable("academy.analysis.screen.hint", mode), AREA_X, AREA_Y + VIEW_H, 200);
        float x = ux(mx), y = uy(my);
        if (maxFirst() > 0) {
            DeveloperScreen.quad(g, TerminalSettingsScreen.ROLLBAR, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H, 1, 1, 1, 1);
            if (dragging || in(x, y, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H))
                DeveloperScreen.rect(g, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H, 1, 1, 1, 80 / 255f);
        }
        g.pose().popMatrix();
    }
    /** 1項目: 分類の名前、その下の細いバー、右端に割合。 */
    private void row(GuiGraphicsExtractor g, VectorAnalysisSync.Entry entry, float rx, float ry) {
        float value = entry.value();
        boolean full = VectorAnalysis.full(value);
        float nameRoom = ROW_W - PERCENT_W - 40;
        TerminalHud.text(g, font, ClientVectorAnalysis.name(entry).getString(), rx + 15, ry + 6, nameRoom, 36, TEXT, 0, 1, 255);
        DeveloperScreen.rect(g, rx + 15, ry + ROW_H - 12, nameRoom, 5, 48 / 255f, 48 / 255f, 48 / 255f, 1);
        float filled = nameRoom * AnalysisHudStyle.fill(value);
        if (filled > 0) DeveloperScreen.rect(g, rx + 15, ry + ROW_H - 12, filled, 5, full ? 1 : .27f, full ? .82f : .70f, full ? .25f : 1, 1);
        TerminalHud.text(g, font, AnalysisHudStyle.percent(value), rx + ROW_W - PERCENT_W - 15, ry + 10, PERCENT_W, 40, TEXT, 2, 1, 255);
        DeveloperScreen.rect(g, rx, ry + ROW_H - 2, ROW_W, 2, 1, 1, 1, 40 / 255f);
    }
    /** 長い案内を、文字の大きさ{@link #TEXT}のまま行の幅で3行まで折り返す（能力設定の注記と同じ）。alphaは文字の不透明度（0〜255）。 */
    private void note(GuiGraphicsExtractor g, Component text, float rx, float ry, int alpha) {
        float scale = TEXT / 9f;
        var lines = font.split(text, (int) ((ROW_W - 30) / scale));
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.pose().pushMatrix();
            g.pose().translate(rx + 15, ry + 4 + i * (TEXT + 4));
            g.pose().scale(scale, scale);
            g.text(font, lines.get(i), 0, 0, (alpha << 24) | 0xffffff, false);
            g.pose().popMatrix();
        }
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        float x = ux(mx), y = uy(my);
        if (button == 0 && maxFirst() > 0 && in(x, y, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            progressBar = Mth.clamp(progressBar + (float) (dy / SCALE) / (TerminalSettingsScreen.BAR_UPPER - TerminalSettingsScreen.BAR_LOWER), 0, 1);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }
    /** ホイールで1行ずつ動かす。 */
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int max = maxFirst();
        if (max > 0 && delta != 0) progressBar = Mth.clamp((first() - Math.signum((float) delta)) / max, 0, 1);
        return true;
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    // 見るだけの画面。能力設定と同じく一時停止しない（届いた解析度がそのまま更新される）。
    @Override public boolean isPauseScreen() { return false; }

    /** テスト用: 今並べている項目。 */
    public List<VectorAnalysisSync.Entry> entries() { return List.copyOf(entries); }
    /** テスト用: 一度に見える一覧の行の数。 */
    public static int visibleRows() { return visible(); }
    /** テスト用: 下の案内を今の言語で折り返したときの行の数（3行までなら縮めずに全部描ける）。 */
    public int hintLines() {
        var mode = Component.translatable(ClientSettings.analysisHudMode().translation());
        return font.split(Component.translatable("academy.analysis.screen.hint", mode), (int) ((ROW_W - 30) / (TEXT / 9f))).size();
    }
}
