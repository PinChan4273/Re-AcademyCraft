package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.TerminalSetting;
import io.github.pinchan4273.reacademycraft.terminal.TerminalSettings;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * 原作SettingsUI（guis/settings.xml）: settings.pngの窓。742x923を0.2倍で画面中央に置き、右のバーでスクロールする
 * 一覧を持つ。カテゴリは原作の順（そのHashMapの順）: Keys、Generic、Misc。それぞれ線の上の見出し、行、後ろに20の余白。
 *
 * - Keys: 原作のキー項目。ここでは移植版のキー割り当てを原作の名前で示す。クリックするとオレンジでPRESSと表示し、
 *   次のキーかマウスボタンがそれになる。Escapeでは元のまま。
 * - Generic: ワールドのスイッチattackPlayerとdestroyBlocks（原作はシングルプレイでだけ表示）と、このプレイヤー自身の
 *   headsOrTailsとuseMouseWheel（ClientSettings）。それぞれチェックボックス。
 * - Misc: Customize UIと、原作のOKボタン。
 *
 * ワールドのスイッチはサーバーへ問い合わせ、サーバーはスナップショットで答えるので、ボックスはサーバーの持つ値を表示する。
 * 原作の文字は独自のTrueTypeフォント。ここではゲームのフォントを原作の大きさで描き、隣のボックスやキーへ届く行は縮める。
 */
public final class TerminalSettingsScreen extends Screen {
    static final ResourceLocation WINDOW = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/settings.png"),
            CHECK_TRUE = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/check_true.png"),
            CHECK_FALSE = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/check_false.png"),
            ROLLBAR = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/life_record/rollbar.png");
    static final float W = 742, H = 923, SCALE = .2f;
    static final float AREA_X = 60, AREA_Y = 120, AREA_W = 614, AREA_H = 720, ROW_W = 611, ROW_H = 60, GAP = 20;
    static final float BAR_X = 673, BAR_W = 9, BAR_H = 96, BAR_LOWER = 119, BAR_UPPER = 760;

    /** 一覧の1要素: カテゴリの見出し、その行の1つ、またはその後の余白。 */
    public record Row(Kind kind, String id) { }
    public enum Kind { HEAD, KEY, WORLD_SWITCH, CLIENT_SWITCH, CALLBACK, GAP }
    /** 原作のキー項目名と、それぞれに対応する移植版のキー割り当て。 */
    public static final List<String> KEYS = List.of("ability_activation", "ability_0", "ability_1", "ability_2", "ability_3",
            "edit_preset", "switch_preset", "open_data_terminal", "debug_console");

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private float x0, y0, progressBar;
    private boolean dragging;
    @Nullable private String editing;

    public TerminalSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("academy.app.settings.name"));
        this.parent = parent;
    }
    public static KeyMapping mapping(String id) {
        return switch (id) {
            case "ability_activation" -> AbilityControls.TOGGLE;
            case "ability_0" -> AbilityControls.SLOTS[0];
            case "ability_1" -> AbilityControls.SLOTS[1];
            case "ability_2" -> AbilityControls.SLOTS[2];
            case "ability_3" -> AbilityControls.SLOTS[3];
            case "edit_preset" -> AbilityControls.EDITOR;
            case "switch_preset" -> AbilityControls.PRESET;
            case "open_data_terminal" -> AbilityControls.TERMINAL;
            case "debug_console" -> DebugConsole.KEY;
            default -> throw new IllegalArgumentException(id);
        };
    }

    @Override protected void init() {
        super.init();
        x0 = (width - W * SCALE) / 2; y0 = (height - H * SCALE) / 2;
        rows.clear();
        rows.add(new Row(Kind.HEAD, "keys"));
        for (var id : KEYS) rows.add(new Row(Kind.KEY, id));
        rows.add(new Row(Kind.GAP, ""));
        rows.add(new Row(Kind.HEAD, "generic"));
        // 原作はワールドのスイッチをシングルプレイでだけ表示する。
        if (minecraft.isSingleplayer()) for (var id : TerminalSettings.ALL) rows.add(new Row(Kind.WORLD_SWITCH, id));
        rows.add(new Row(Kind.CLIENT_SWITCH, ClientSettings.HEADS_OR_TAILS));
        rows.add(new Row(Kind.CLIENT_SWITCH, ClientSettings.USE_MOUSE_WHEEL));
        rows.add(new Row(Kind.GAP, ""));
        rows.add(new Row(Kind.HEAD, "misc"));
        rows.add(new Row(Kind.CALLBACK, "edit_ui"));
        rows.add(new Row(Kind.GAP, ""));
    }
    private static float height(Row row) { return row.kind == Kind.GAP ? GAP : ROW_H; }
    /** ElementList: この行から、領域に丸ごと入るだけの行。 */
    private int first() { return (int) (progressBar * maxProgress()); }
    /** 領域を最後まで埋められる、最後の先頭行。 */
    private int maxProgress() {
        float h = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            h += height(rows.get(i));
            if (h > AREA_H) return i + 1;
        }
        return 0;
    }
    private float ux(double mx) { return (float) ((mx - x0) / SCALE); }
    private float uy(double my) { return (float) ((my - y0) / SCALE); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }
    private float barY() { return BAR_LOWER + progressBar * (BAR_UPPER - BAR_LOWER); }
    /** 表示する各行の開始位置。領域外の行はNaN。 */
    private float rowY(int index) {
        int from = first();
        if (index < from) return Float.NaN;
        float y = AREA_Y;
        for (int i = from; i < index; i++) y += height(rows.get(i));
        return y + height(rows.get(index)) > AREA_Y + AREA_H ? Float.NaN : y;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale(SCALE, SCALE, 1);
        DeveloperScreen.quad(pose, WINDOW, 0, 0, W, H, 1, 1, 1, 1);
        float x = ux(mx), y = uy(my);
        for (int i = 0; i < rows.size(); i++) {
            float ry = rowY(i);
            if (Float.isNaN(ry)) continue;
            drawRow(g, rows.get(i), AREA_X, ry, x, y);
        }
        DeveloperScreen.quad(pose, ROLLBAR, BAR_X, barY(), BAR_W, BAR_H, 1, 1, 1, 1);
        if (dragging || in(x, y, BAR_X, barY(), BAR_W, BAR_H)) DeveloperScreen.rect(pose, BAR_X, barY(), BAR_W, BAR_H, 1, 1, 1, 80 / 255f);
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private void drawRow(GuiGraphics g, Row row, float rx, float ry, float mx, float my) {
        var pose = g.pose();
        switch (row.kind) {
            case HEAD -> {
                // t_cathead: 文字は大きさ42・アルファ170、白い線は150。
                TerminalHud.text(g, font, Component.translatable("academy.settings.cat." + row.id).getString(), rx + 8, ry, ROW_W - 8, ROW_H, 42, 0, 2, 170);
                DeveloperScreen.rect(pose, rx, ry + ROW_H - 4, ROW_W, 4, 1, 1, 1, 150 / 255f);
            }
            case KEY -> {
                label(g, row, rx, ry, 425);
                boolean edit = row.id.equals(editing);
                String name = edit ? "PRESS" : mapping(row.id).getTranslatedKeyMessage().getString();
                int color = edit ? 0xc8fb8525 : 0xc8c8c8c8;
                drawKey(g, name, rx + 440, ry + 10, color);
                if (in(mx, my, rx + 440, ry + 10, 160, 40)) DeveloperScreen.rect(pose, rx + 440, ry + 10, 160, 40, 1, 1, 1, 51 / 255f);
            }
            case WORLD_SWITCH, CLIENT_SWITCH -> {
                label(g, row, rx, ry, 535);
                DeveloperScreen.quad(pose, on(row) ? CHECK_TRUE : CHECK_FALSE, rx + 550, ry + 12.5f, 35, 35, 1, 1, 1, 1);
            }
            case CALLBACK -> {
                label(g, row, rx, ry, 425);
                boolean hover = in(mx, my, rx + 440, ry + 10, 160, 40);
                float c = (hover ? 85 : 51) / 255f;
                DeveloperScreen.rect(pose, rx + 440, ry + 10, 160, 40, c, c, c, 1);
                TerminalHud.text(g, font, "OK", rx + 440, ry + 10, 160, 40, 40, 1, 1, 255);
            }
            case GAP -> { }
        }
    }
    /** 行の文字（大きさ40、15内側から）。隣のものに届くところでは縮める。 */
    private void label(GuiGraphics g, Row row, float rx, float ry, float room) {
        TerminalHud.text(g, font, Component.translatable("academy.settings.prop." + row.id).getString(), rx + 15, ry + 10, room - 15, 40, 40, 0, 1, 255);
    }
    private void drawKey(GuiGraphics g, String name, float x, float y, int argb) {
        var pose = g.pose();
        float s = 40 / 9f;
        if (font.width(name) * s > 160 && font.width(name) > 0) s = 160f / font.width(name);
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(s, s, 1);
        g.drawString(font, name, 0, 0, argb, false);
        pose.popPose();
    }
    public boolean on(Row row) {
        return row.kind == Kind.WORLD_SWITCH ? ClientTerminal.setting(row.id) : ClientSettings.get(row.id);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (editing != null) { endEditing(InputConstants.Type.MOUSE.getOrCreate(button)); return true; }
        float x = ux(mx), y = uy(my);
        if (button == 0 && in(x, y, BAR_X, barY(), BAR_W, BAR_H)) { dragging = true; return true; }
        if (button == 0) {
            for (int i = 0; i < rows.size(); i++) {
                float ry = rowY(i);
                if (Float.isNaN(ry)) continue;
                var row = rows.get(i);
                if ((row.kind == Kind.KEY || row.kind == Kind.CALLBACK) && in(x, y, AREA_X + 440, ry + 10, 160, 40)) { activate(row); return true; }
                if ((row.kind == Kind.WORLD_SWITCH || row.kind == Kind.CLIENT_SWITCH) && in(x, y, AREA_X + 550, ry + 12.5f, 35, 35)) { activate(row); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    /** 行のボックス・キー・ボタンをクリックしたときの動作。 */
    public void activate(Row row) {
        switch (row.kind) {
            case KEY -> editing = row.id;
            case WORLD_SWITCH -> AcademyNetwork.CHANNEL.sendToServer(new TerminalSetting(row.id, !ClientTerminal.setting(row.id)));
            case CLIENT_SWITCH -> ClientSettings.set(row.id, !ClientSettings.get(row.id));
            case CALLBACK -> minecraft.setScreen(new CustomizeUI());
            default -> { }
        }
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            progressBar = Mth.clamp(progressBar + (float) (dy / SCALE) / (BAR_UPPER - BAR_LOWER), 0, 1);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (editing != null) {
            endEditing(key == GLFW.GLFW_KEY_ESCAPE ? null : InputConstants.getKey(key, scan));
            // 原作CGuiScreen.keyTypedはEscapeをまずゲームへ渡し（画面が閉じる）、次にEditKeyへ渡す（キーは保たれる）。両方起きる。
            if (key != GLFW.GLFW_KEY_ESCAPE) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    /** 原作EditKey.endEditing: Escapeならキーを保ち、それ以外はそのキーにする。すぐに保存する。 */
    public void endEditing(@Nullable InputConstants.Key key) {
        if (editing == null) return;
        var mapping = mapping(editing);
        editing = null;
        if (key == null) return;
        minecraft.options.setKey(mapping, key);
        KeyMapping.resetMapping();
        minecraft.options.save();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    // 原作のSettingsUIはシングルプレイのゲームを一時停止した（1.12の既定）が、スイッチを直接ローカルの設定へ書いていた。
    // ここではサーバーが切り替え、画面はその返答を表示する。一時停止した統合サーバーはこれを行えないので、この画面は一時停止しない。
    @Override public boolean isPauseScreen() { return false; }

    // テスト用の入口。
    public List<Row> rows() { return List.copyOf(rows); }
    public Row row(String id) { return rows.stream().filter(r -> r.id.equals(id)).findFirst().orElseThrow(); }
    @Nullable public String editing() { return editing; }
    public boolean editable() { return rows.stream().anyMatch(r -> r.kind == Kind.WORLD_SWITCH); }
    public void scrollTo(float progress) { progressBar = Mth.clamp(progress, 0, 1); }
    public boolean shown(String id) { return !Float.isNaN(rowY(rows.indexOf(row(id)))); }
}
