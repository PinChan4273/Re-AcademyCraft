package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.AbilityCategory;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettings;
import io.github.pinchan4273.reacademycraft.skill.ElectronMissile;
import io.github.pinchan4273.reacademycraft.skill.ElectronMissileTargetKind;
import io.github.pinchan4273.reacademycraft.skill.LightShield;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 能力設定の画面（1.4.0のS1、原作に無い）。データ端末の「能力設定」アプリと、能力設定のキー（既定は割り当てなし）で開く。
 * 窓・文字・チェックボックスは、原作のSettingsUIを移した{@link TerminalSettingsScreen}と同じ物を、同じ大きさで使う。
 * <ul>
 * <li>Electron Missile: 照準のモード（AUTO・SEMI-AUTO）と、攻撃対象4つ（敵対・中立・友好・プレイヤー）のON／OFF。</li>
 * <li>Light Shield: モード（AUTO・SEMI-AUTO）。</li>
 * </ul>
 * 値はサーバーが正本で、画面はサーバーから届いた値（{@link ClientSkillSettings}）だけを表示する。押すと変更をサーバーへ送り、
 * 返事が届くまで「送信中」と出す。断られた変更は、返事の値で元の表示へ戻る。書き込みはコマンド（{@code /academy missile}・
 * {@code /academy shield}）と同じ入口を通るので、どちらで変えても同じ値になる。
 * <p>
 * 画面を開いても技能の状態は変えない: 開いている間は技能のキーを送らず（{@link AbilityControls}）、切り替え型の技能（Electron
 * Missile・Light Shieldなど）はONのまま続き、変えた設定は次のtickか次の発射から効く。押し続け型は、ほかの画面と同じく中断される。
 */
public final class AbilitySettingsScreen extends Screen {
    /**
     * 窓の倍率。端末の設定の画面（0.2）より少し大きい0.25にし、文字はすべて{@link #TEXT}の大きさで描く。こうすると、文字の1画素が
     * 画面のGUIの大きさ（1〜4）の整数倍の画素に載り、つぶれない（手動確認 2026-10-25: 0.2と大きさ22・28では、GUIの大きさ2で
     * 文字の画素の行が間引かれ、つぶれて見えた）。窓の高さは923×0.25 = 231で、GUIの大きさ自動の720pでも画面に収まる。
     */
    static final float W = TerminalSettingsScreen.W, H = TerminalSettingsScreen.H, SCALE = .25f;
    /** 文字の大きさ（窓の単位）。36/9×0.25 = 1で、文字の1画素がGUIの1画素になる。 */
    static final float TEXT = 36;
    static final float AREA_X = TerminalSettingsScreen.AREA_X, AREA_Y = TerminalSettingsScreen.AREA_Y, AREA_H = TerminalSettingsScreen.AREA_H;
    static final float ROW_W = TerminalSettingsScreen.ROW_W, ROW_H = TerminalSettingsScreen.ROW_H, GAP = TerminalSettingsScreen.GAP;
    /** モードの2つの選択肢の箱（行の中の位置）。 */
    static final float OPTION_X = 170, OPTION_W = 215, OPTION_GAP = 10, OPTION_Y = 10, OPTION_H = 40;
    /** チェックボックス（{@link TerminalSettingsScreen}と同じ位置）。 */
    static final float CHECK_X = 550, CHECK_Y = 12.5f, CHECK = 35;

    /** 一覧の1要素。settingは設定の名前（見出し・注記・余白ではnull）、skillはその行が属する技能。 */
    public record Row(Kind kind, @Nullable ResourceLocation setting, ResourceLocation skill, String text) { }
    public enum Kind { HEAD, MODE, TARGET, NOTE, GAP, STATUS }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private float x0, y0;

    public AbilitySettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("academy.app.ability_settings.name"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        x0 = (width - W * SCALE) / 2; y0 = (height - H * SCALE) / 2;
        rows.clear();
        rows.add(new Row(Kind.HEAD, null, ElectronMissile.ID, "academy.skill.electron_missile"));
        rows.add(new Row(Kind.MODE, AbilitySettings.MISSILE_AIM, ElectronMissile.ID, "academy.ability_settings.aim"));
        for (var kind : ElectronMissileTargetKind.values())
            rows.add(new Row(Kind.TARGET, AbilitySettings.missileTarget(kind), ElectronMissile.ID, kind.translation()));
        rows.add(new Row(Kind.NOTE, null, ElectronMissile.ID, "academy.electron_missile.target.player_rules"));
        rows.add(new Row(Kind.GAP, null, ElectronMissile.ID, ""));
        rows.add(new Row(Kind.HEAD, null, LightShield.ID, "academy.skill.light_shield"));
        rows.add(new Row(Kind.MODE, AbilitySettings.SHIELD_MODE, LightShield.ID, "academy.ability_settings.mode"));
        rows.add(new Row(Kind.GAP, null, LightShield.ID, ""));
        rows.add(new Row(Kind.STATUS, null, LightShield.ID, ""));
    }
    /**
     * 窓の画像（settings.png、742x923）の左上には、原作の「Settings」の文字と歯車の絵が焼き込まれている（40〜88行目）。
     * この画面では、その帯の代わりに、文字の無い同じ並びの行（100〜110行目。帯の外の行と画素が同じことを確かめた）を引き伸ばして描き、
     * その上に自分の見出しを描く。画像は半透明なので、上から塗りつぶすと色が変わるため、帯を描かずに3つに分けて描く。
     */
    static final float TITLE_TOP = 40, TITLE_BOTTOM = 89, CLEAN_TOP = 100, CLEAN_BOTTOM = 110, TITLE_X = 40;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/icons/edit.png");
    private static void window(com.mojang.blaze3d.vertex.PoseStack pose) {
        var tex = TerminalSettingsScreen.WINDOW;
        DeveloperScreen.quad(pose, tex, 0, 0, W, TITLE_TOP, 0, 0, 1, TITLE_TOP / H, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, tex, 0, TITLE_TOP, W, TITLE_BOTTOM - TITLE_TOP, 0, CLEAN_TOP / H, 1, CLEAN_BOTTOM / H, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, tex, 0, TITLE_BOTTOM, W, H - TITLE_BOTTOM, 0, TITLE_BOTTOM / H, 1, 1, 1, 1, 1, 1);
    }
    /** 注記の行の高さ（3行まで。1行の大きさ{@link #NOTE_SIZE}と行の間4）。 */
    static final float NOTE_SIZE = TEXT, NOTE_H = 3 * (NOTE_SIZE + 4) + 8;
    private static float height(Row row) { return row.kind == Kind.GAP ? GAP : row.kind == Kind.NOTE ? NOTE_H : ROW_H; }
    private float ux(double mx) { return (float) ((mx - x0) / SCALE); }
    private float uy(double my) { return (float) ((my - y0) / SCALE); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }
    /** 各行の開始位置（全行が領域に収まる）。 */
    private float rowY(int index) {
        float y = AREA_Y;
        for (int i = 0; i < index; i++) y += height(rows.get(i));
        return y;
    }

    /** その技能を、今のクライアントのプレイヤーが使えるか（Meltdownerで習得済み）。使えなくても設定は変えられる（コマンドと同じ）。 */
    public boolean learned(ResourceLocation skill) {
        var player = minecraft == null ? null : minecraft.player;
        var data = player == null ? null : player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        return data != null && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(skill);
    }
    /** その攻撃対象がONか（サーバーの値）。値が届いていなければnull。 */
    public @Nullable Boolean on(ResourceLocation setting) {
        String value = ClientSkillSettings.value(setting);
        return value == null ? null : AbilitySettings.ON.equals(value);
    }
    /** モードの選択肢: 0 = AUTO（"auto"）、1 = SEMI-AUTO（"semi"）。 */
    static final String[] MODES = {"auto", "semi"};
    /** 選択肢の箱の短い名前（AUTO・SEMI-AUTO）。説明つきの長い名前（コマンドの表示の名前）は、箱に収まらないので使わない。 */
    private static String modeLabel(String mode) { return "academy.ability_settings.option." + mode; }
    private boolean allTargetsOff() {
        for (var kind : ElectronMissileTargetKind.values())
            if (!Boolean.FALSE.equals(on(AbilitySettings.missileTarget(kind)))) return false;
        return true;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale(SCALE, SCALE, 1);
        window(pose);
        // 見出し: 原作の編集の印（このアプリのアイコン）と、この画面の名前。
        DeveloperScreen.quad(pose, ICON, TITLE_X, TITLE_TOP + 4, 44, 44, 1, 1, 1, 1);
        TerminalHud.text(g, font, title.getString(), TITLE_X + 56, TITLE_TOP, ROW_W - 80, TITLE_BOTTOM - TITLE_TOP, TEXT, 0, 1, 235);
        float x = ux(mx), y = uy(my);
        for (int i = 0; i < rows.size(); i++) drawRow(g, rows.get(i), AREA_X, rowY(i), x, y);
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private void drawRow(GuiGraphics g, Row row, float rx, float ry, float mx, float my) {
        var pose = g.pose();
        boolean ready = ClientSkillSettings.readable();
        switch (row.kind) {
            case HEAD -> {
                // 技能の名前。未習得なら、右に短い札（長い説明を足すと1行に収めるために縮み、読めなかった）。
                float room = ROW_W - 8;
                if (!learned(row.skill)) {
                    String tag = Component.translatable("academy.ability_settings.not_learned_tag").getString();
                    float tagWidth = font.width(tag) * TEXT / 9;
                    TerminalHud.text(g, font, tag, rx + ROW_W - tagWidth, ry, tagWidth, ROW_H, TEXT, 0, 2, 150);
                    room -= tagWidth + 16;
                }
                TerminalHud.text(g, font, Component.translatable(row.text).getString(), rx + 8, ry, room, ROW_H, TEXT, 0, 2, 200);
                DeveloperScreen.rect(pose, rx, ry + ROW_H - 4, ROW_W, 4, 1, 1, 1, 150 / 255f);
            }
            case MODE -> {
                label(g, row.text, rx, ry, OPTION_X - 20, 255);
                String current = ClientSkillSettings.value(row.setting);
                for (int i = 0; i < MODES.length; i++) {
                    float ox = rx + OPTION_X + i * (OPTION_W + OPTION_GAP);
                    boolean chosen = MODES[i].equals(current);
                    boolean hover = ready && in(mx, my, ox, ry + OPTION_Y, OPTION_W, OPTION_H);
                    float c = (chosen ? 140 : hover ? 85 : 51) / 255f;
                    DeveloperScreen.rect(pose, ox, ry + OPTION_Y, OPTION_W, OPTION_H, c * (chosen ? .55f : 1), c * (chosen ? .85f : 1), c, 1);
                    TerminalHud.text(g, font, Component.translatable(modeLabel(MODES[i])).getString(), ox, ry + OPTION_Y,
                            OPTION_W, OPTION_H, TEXT, 1, 1, ready ? 255 : 120);
                }
            }
            case TARGET -> {
                label(g, row.text, rx, ry, CHECK_X - 15, 255);
                Boolean on = on(row.setting);
                if (on != null) DeveloperScreen.quad(pose, on ? TerminalSettingsScreen.CHECK_TRUE : TerminalSettingsScreen.CHECK_FALSE,
                        rx + CHECK_X, ry + CHECK_Y, CHECK, CHECK, 1, 1, 1, ready ? 1 : .5f);
            }
            case NOTE -> {
                // 注記は長いので、行の幅で3行まで折り返す（1行に縮めると読めない。2行では英語の文が切れた）。
                var note = Component.translatable(allTargetsOff() ? "academy.electron_missile.target.none" : row.text);
                float size = NOTE_SIZE, scale = size / 9f;
                var lines = font.split(note, (int) ((ROW_W - 30) / scale));
                for (int i = 0; i < Math.min(3, lines.size()); i++) {
                    pose.pushPose();
                    pose.translate(rx + 15, ry + 4 + i * (size + 4), 0);
                    pose.scale(scale, scale, 1);
                    g.drawString(font, lines.get(i), 0, 0, ((allTargetsOff() ? 255 : 190) << 24) | 0xffffff, false);
                    pose.popPose();
                }
            }
            case STATUS -> {
                String key = !ClientSkillSettings.received() ? "academy.ability_settings.waiting"
                        : !ClientSkillSettings.readable() ? "academy.ability_settings.unavailable"
                        : ClientSkillSettings.pending() ? "academy.ability_settings.pending" : "academy.ability_settings.synced";
                TerminalHud.text(g, font, Component.translatable(key).getString(), rx + 15, ry + 10, ROW_W - 30, 40, TEXT, 0, 1, 200);
            }
            case GAP -> { }
        }
    }
    /** 行の文字（大きさ{@link #TEXT}、15内側から）。隣のものに届くところでは縮める。 */
    private void label(GuiGraphics g, String key, float rx, float ry, float room, int alpha) {
        TerminalHud.text(g, font, Component.translatable(key).getString(), rx + 15, ry + 10, room - 15, 40, TEXT, 0, 1, alpha);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        float x = ux(mx), y = uy(my);
        if (button == 0 && ClientSkillSettings.readable()) {
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                float ry = rowY(i);
                if (row.kind == Kind.MODE) {
                    for (int m = 0; m < MODES.length; m++)
                        if (in(x, y, AREA_X + OPTION_X + m * (OPTION_W + OPTION_GAP), ry + OPTION_Y, OPTION_W, OPTION_H)) { choose(row, MODES[m]); return true; }
                }
                if (row.kind == Kind.TARGET && in(x, y, AREA_X + CHECK_X, ry + CHECK_Y, CHECK, CHECK)) { toggle(row); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    /** モードを選ぶ（今と同じでも送る。サーバーは何も変えずに値を返す）。 */
    public void choose(Row row, String mode) { ClientSkillSettings.request(row.setting, mode); }
    /** 攻撃対象を反転する。値がまだ届いていなければ何もしない。 */
    public void toggle(Row row) {
        Boolean on = on(row.setting);
        if (on != null) ClientSkillSettings.request(row.setting, on ? AbilitySettings.OFF : AbilitySettings.ON);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    // サーバーが値を切り替え、画面はその返事を表示する。一時停止した統合サーバーは返事をしないので、この画面は一時停止しない。
    @Override public boolean isPauseScreen() { return false; }

    // テスト用の入口。
    public List<Row> rows() { return List.copyOf(rows); }
    public Row row(ResourceLocation setting) { return rows.stream().filter(r -> setting.equals(r.setting)).findFirst().orElseThrow(); }
}
