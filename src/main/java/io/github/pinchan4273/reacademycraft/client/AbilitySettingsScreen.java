package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettings;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettingsVisibility;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettingsVisibility.Kind;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettingsVisibility.Row;
import io.github.pinchan4273.reacademycraft.skill.DarkMatterBeetles;
import io.github.pinchan4273.reacademycraft.skill.ElectronMissile;
import io.github.pinchan4273.reacademycraft.skill.ElectronMissileTargetKind;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 能力設定の画面（1.4.0、原作に無い）。データ端末の「能力設定」アプリと、能力設定のキー（既定は割り当てなし）で開く。
 * ウィンドウ・文字・チェックボックスは、原作のSettingsUIを移した{@link TerminalSettingsScreen}と同じ物を、同じ大きさで使う。
 * <ul>
 * <li>Electron Missile: 照準のモード（AUTO・SEMI-AUTO）と、攻撃対象4つ（敵対・中立・友好・プレイヤー）のON／OFF。</li>
 * <li>Light Shield: モード（AUTO・SEMI-AUTO）。</li>
 * <li>v1.5.0（全系統共通で、未習得のラベルを出さない）: CPセーフティのON／OFFと停止ライン（サーバーの値）、CP表示の形式と非使用時の大きさ
 * （表示だけの個人設定で、{@link ClientSettings}に保存する。押すとすぐ変わり、サーバーへは送らない）。</li>
 * <li>1.5.2: パッシブ／アクティブの切り替え。その系統で切り替えられる技能をすべて並べ、切り替えられない行（未習得・熟練度100%未満）は
 * 理由と一緒に灰色で出す（押しても送らない）。値はサーバーが正本で、サーバーも同じ条件を確かめる（{@link io.github.pinchan4273.reacademycraft.skill.PassiveSkills#refusal}）。
 * 同じ欄に、キーの案内へのパッシブの行の出し方（非表示・アイコン・アイコン＋自動、{@link KeyHintPassiveMode}）を左右の矢印で選ぶ行を置く
 * （クライアントの個人設定で、{@link ClientSettings}に保存する）。</li>
 * <li>1.5.2: ベクトル操作の人には、解析度の一覧（割合とバー）と、項目ごとの「HUDに出す」のチェックボックス、HUDの表示の形式
 * （選んだ物だけ・すべて・出さない）。CP表示と同じく{@link ClientSettings}に保存する個人設定。一覧は届いた解析度（{@link ClientVectorAnalysis}）
 * から毎tick作り直す。</li>
 * <li>1.5.2: ベクトル反射の反射方向（攻撃者へ・視線方向・最寄りの敵・視界内の敵を左右の矢印で選ぶ）と、自動の2方式でPvPの相手を
 * 候補に含めるかのチェックボックス。値はサーバーが正本。</li>
 * </ul>
 * v1.5.0: 出す行は{@link AbilitySettingsVisibility}が決める（本人が使える項目だけ。共通の行は系統を持つ人、技能の行は今の系統で習得済みの人）。
 * 画面を開いたまま技能を習得したり系統が変わったりしたら、次のtickに一覧を作り直す。
 * 技能の設定とセーフティの値はサーバーが正本で、画面はサーバーから届いた値（{@link ClientSkillSettings}）だけを表示する。押すと変更をサーバーへ送り、
 * 返事が届くまで「送信中」と出す。断られた変更は、返事の値で元の表示へ戻る。書き込みはコマンド（{@code /academy missile}・
 * {@code /academy shield}）と同じ入口を通るので、どちらで変えても同じ値になる。
 * <p>
 * 画面を開いても技能の状態は変えない: 開いている間は技能のキーを送らず（{@link AbilityControls}）、切り替え型の技能（Electron
 * Missile・Light Shieldなど）はONのまま続き、変えた設定は次のtickか次の発射から効く。押し続け型は、ほかの画面と同じく中断される。
 */
public final class AbilitySettingsScreen extends Screen {
    /**
     * ウィンドウの倍率。端末の設定の画面（0.2）より少し大きい0.25にし、文字はすべて{@link #TEXT}の大きさで描く。こうすると、文字の1画素が
     * 画面のGUIの大きさ（1〜4）の整数倍の画素に載り、つぶれない（手動確認: 0.2と大きさ22・28では、GUIの大きさ2で
     * 文字の画素の行が間引かれ、つぶれて見えた）。ウィンドウの高さは923×0.25 = 231で、GUIの大きさ自動の720pでも画面に収まる。
     */
    static final float W = TerminalSettingsScreen.W, H = TerminalSettingsScreen.H, SCALE = .25f;
    /** 文字の大きさ（ウィンドウの単位）。36/9×0.25 = 1で、文字の1画素がGUIの1画素になる。 */
    static final float TEXT = 36;
    static final float AREA_X = TerminalSettingsScreen.AREA_X, AREA_Y = TerminalSettingsScreen.AREA_Y, AREA_H = TerminalSettingsScreen.AREA_H;
    static final float ROW_W = TerminalSettingsScreen.ROW_W, ROW_H = TerminalSettingsScreen.ROW_H, GAP = TerminalSettingsScreen.GAP;
    /** モードの2つの選択肢のボックス（行の中の位置）。 */
    static final float OPTION_X = 170, OPTION_W = 215, OPTION_GAP = 10, OPTION_Y = 10, OPTION_H = 40;
    /** チェックボックス（{@link TerminalSettingsScreen}と同じ位置）。 */
    static final float CHECK_X = 550, CHECK_Y = 12.5f, CHECK = 35;
    /**
     * 値を「&lt; 値 &gt;」で動かす行の位置（行の中）。値のボックスは、大きさ{@link #TEXT}で6文字（ゲージと数値）が縮まずに入る幅。
     * 3つのボックスを並べると、1つが144幅しかなく文字が縮んで読めないので、モードの行の形にはしない。
     */
    static final float STEP_X = 283, STEP_ARROW = 50, STEP_VALUE = 220;
    /** パッシブ／アクティブの切り替えの行の、2つの選択肢のボックス（行の中の位置）。技能の名前が長いので、モードの行より名前の幅を広く取る。 */
    static final float PASSIVE_X = 290, PASSIVE_W = 155;
    /** 切り替えの選択肢: 0 = アクティブ、1 = パッシブ。 */
    static final String[] PASSIVE_MODES = {io.github.pinchan4273.reacademycraft.skill.PassiveSkills.ACTIVE, io.github.pinchan4273.reacademycraft.skill.PassiveSkills.PASSIVE};
    /** 一覧を動かす範囲の高さ。{@link Kind#STATUS}の行は、動かさずに一番下へ置く（常に見える）。 */
    static final float VIEW_H = AREA_H - ROW_H;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    /** 1.5.2: セーフティの並びを編集するプリセット（0〜3。開いたときの今のプリセット）。画面だけの選択。 */
    private int orderPreset = -1;
    /** セーフティの並びの行の、上・下のボタンと動作中のインジケーターの位置（行の中）。 */
    static final float ORDER_ICON_X = 70, ORDER_NAME_X = 120, ORDER_RUN_X = 450, ORDER_UP_X = 493, ORDER_BUTTON = 50;
    private float x0, y0;
    /** 一覧の動かし具合（0〜1）とつまみのドラッグ（{@link TerminalSettingsScreen}と同じ物）。 */
    private float progressBar;
    private boolean dragging;

    public AbilitySettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("academy.app.ability_settings.name"));
        this.parent = parent;
    }

    @Override protected void init() {
        super.init();
        x0 = (width - W * SCALE) / 2; y0 = (height - H * SCALE) / 2;
        refresh();
    }
    /** 今のプレイヤーのデータ（無ければnull）。 */
    private @Nullable PlayerAbilityData data() {
        var player = minecraft == null ? null : minecraft.player;
        return player == null ? null : AbilityCapabilities.of(player).orElse(null);
    }
    /**
     * 出す行を決め直す（{@link AbilitySettingsVisibility}）。変わったときだけ置き換え、先頭の行の位置を保ったまま、動かせる範囲へ収める。
     * 変わったらtrue。
     */
    private boolean refresh() {
        var next = AbilitySettingsVisibility.rows(data(), analysisKeys());
        if (next.equals(rows)) return false;
        int keep = rows.isEmpty() ? 0 : first();
        rows.clear();
        rows.addAll(next);
        int max = maxFirst();
        progressBar = max == 0 ? 0 : net.minecraft.util.Mth.clamp((float) keep / max, 0, 1);
        return true;
    }
    /** 解析度の一覧に出す分類のキー（{@link AnalysisHudStyle#sorted}の順）。 */
    static List<String> analysisKeys() {
        return AnalysisHudStyle.sorted(ClientVectorAnalysis.entries()).stream().map(io.github.pinchan4273.reacademycraft.network.VectorAnalysisSync.Entry::key).toList();
    }
    /** 技能の習得や系統の変更が、開いている画面にも届くよう、毎tick系統と習得の組を見直す（行の数は30ほどなので、組み立て直して比べる）。 */
    @Override public void tick() {
        super.tick();
        refresh();
    }
    /**
     * ウィンドウの画像（settings.png、742x923）の左上には、原作の「Settings」の文字と歯車の絵が焼き込まれている（40〜88行目）。
     * この画面では、その帯の代わりに、文字の無い同じ並びの行（100〜110行目。帯の外の行と画素が同じことを確かめた）を引き伸ばして描き、
     * その上に自分の見出しを描く。画像は半透明なので、上から塗りつぶすと色が変わるため、帯を描かずに3つに分けて描く。
     */
    static final float TITLE_TOP = 40, TITLE_BOTTOM = 89, CLEAN_TOP = 100, CLEAN_BOTTOM = 110, TITLE_X = 40;
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/icons/edit.png");
    static void window(com.mojang.blaze3d.vertex.PoseStack pose) {
        var tex = TerminalSettingsScreen.WINDOW;
        DeveloperScreen.quad(pose, tex, 0, 0, W, TITLE_TOP, 0, 0, 1, TITLE_TOP / H, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, tex, 0, TITLE_TOP, W, TITLE_BOTTOM - TITLE_TOP, 0, CLEAN_TOP / H, 1, CLEAN_BOTTOM / H, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, tex, 0, TITLE_BOTTOM, W, H - TITLE_BOTTOM, 0, TITLE_BOTTOM / H, 1, 1, 1, 1, 1, 1);
    }
    /** 注記の行の高さ（3行まで。1行の大きさ{@link #NOTE_SIZE}と行の間4）。 */
    static final float NOTE_SIZE = TEXT, NOTE_H = 3 * (NOTE_SIZE + 4) + 8;
    /** 注記の欄に描ける行の数。これを超えた分は描かない（切り捨て）。 */
    public static final int NOTE_MAX_LINES = 3;
    /** 切り捨てを記録した注記（言語と文言のキー。同じ物を何度も記録しない）。 */
    private static final java.util.Set<String> OVERFLOW_REPORTED = new java.util.HashSet<>();
    /**
     * その注記を、欄の幅で折り返したときの行の数（1.5.2。文言が{@link #NOTE_MAX_LINES}行を超えて切れても黙って
     * 通っていた）。行の幅は画面の単位で決まっていて、窓の大きさやGUIスケールでは変わらない。
     */
    public int noteLines(Component note) {
        float scale = NOTE_SIZE / 9f;
        return font.split(note, (int) ((ROW_W - 30) / scale)).size();
    }
    private static float height(Row row) { return row.kind() == Kind.GAP ? GAP : row.kind() == Kind.NOTE ? NOTE_H : ROW_H; }
    private float ux(double mx) { return (float) ((mx - x0) / SCALE); }
    private float uy(double my) { return (float) ((my - y0) / SCALE); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }
    /** 動かす行の数（最後の{@link Kind#STATUS}を除く）。 */
    private int scrolling() { return Math.max(0, rows.size() - 1); }
    /** いちばん下まで動かしたときの先頭の行。全行が収まるなら0。 */
    private int maxFirst() {
        float sum = 0;
        for (int i = scrolling() - 1; i >= 0; i--) {
            sum += height(rows.get(i));
            if (sum > VIEW_H) return i + 1;
        }
        return 0;
    }
    private int first() { return Math.round(progressBar * maxFirst()); }
    private float barY() { return TerminalSettingsScreen.BAR_LOWER + progressBar * (TerminalSettingsScreen.BAR_UPPER - TerminalSettingsScreen.BAR_LOWER); }
    /** 表示する各行の開始位置。範囲の外の行はNaN。 */
    private float rowY(int index) {
        if (rows.get(index).kind() == Kind.STATUS) return AREA_Y + AREA_H - ROW_H;
        int from = first();
        if (index < from) return Float.NaN;
        float y = AREA_Y;
        for (int i = from; i < index; i++) y += height(rows.get(i));
        return y + height(rows.get(index)) > AREA_Y + VIEW_H ? Float.NaN : y;
    }

    /** その技能を、今のクライアントのプレイヤーが使えるか（技能の属する系統で習得済み）。画面はこのときだけ技能の行を出す。 */
    public boolean learned(ResourceLocation skill) {
        var data = data();
        var category = AbilitySettingsVisibility.categoryOf(skill);
        return data != null && category != null && category.equals(data.getAbility()) && data.hasLearned(skill);
    }
    /** その攻撃対象がONか（サーバーの値）。値が届いていなければnull。 */
    public @Nullable Boolean on(ResourceLocation setting) {
        String value = ClientSkillSettings.value(setting);
        return value == null ? null : AbilitySettings.ON.equals(value);
    }
    /** モードの選択肢: 0 = AUTO（"auto"）、1 = SEMI-AUTO（"semi"）。 */
    static final String[] MODES = {"auto", "semi"};
    /** 選択肢のボックスの短い名前（AUTO・SEMI-AUTO）。説明つきの長い名前（コマンドの表示の名前）は、ボックスに収まらないので使わない。 */
    private static String modeLabel(String mode) { return "academy.ability_settings.option." + mode; }
    private boolean allTargetsOff() {
        for (var kind : ElectronMissileTargetKind.values())
            if (!Boolean.FALSE.equals(on(AbilitySettings.missileTarget(kind)))) return false;
        return true;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        // 1.20.1のrenderBackground(g)に当たる物（AboutScreenのlegacyBackgroundと同じ）: ワールドの上の半透明の背景と、そのイベント。
        renderTransparentBackground(g);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.BackgroundRendered(this, g));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0);
        pose.scale(SCALE, SCALE, 1);
        window(pose);
        // 見出し: 原作の編集の印（このアプリのアイコン）と、この画面の名前。
        DeveloperScreen.quad(pose, ICON, TITLE_X, TITLE_TOP + 4, 44, 44, 1, 1, 1, 1);
        TerminalHud.text(g, font, title.getString(), TITLE_X + 56, TITLE_TOP, ROW_W - 80, TITLE_BOTTOM - TITLE_TOP, TEXT, 0, 1, 235);
        float x = ux(mx), y = uy(my);
        for (int i = 0; i < rows.size(); i++) {
            float ry = rowY(i);
            if (!Float.isNaN(ry)) drawRow(g, rows.get(i), AREA_X, ry, x, y);
        }
        if (maxFirst() > 0) {
            DeveloperScreen.quad(pose, TerminalSettingsScreen.ROLLBAR, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H, 1, 1, 1, 1);
            if (dragging || in(x, y, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H))
                DeveloperScreen.rect(pose, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H, 1, 1, 1, 80 / 255f);
        }
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private void drawRow(GuiGraphics g, Row row, float rx, float ry, float mx, float my) {
        var pose = g.pose();
        boolean ready = ClientSkillSettings.readable();
        switch (row.kind()) {
            case HEAD -> {
                // 技能の名前（v1.5.0から習得済みの技能の見出ししか出さないので、未習得のラベルは無くした）。
                TerminalHud.text(g, font, Component.translatable(row.text()).getString(), rx + 8, ry, ROW_W - 8, ROW_H, TEXT, 0, 2, 200);
                DeveloperScreen.rect(pose, rx, ry + ROW_H - 4, ROW_W, 4, 1, 1, 1, 150 / 255f);
            }
            case MODE -> {
                label(g, row.text(), rx, ry, OPTION_X - 20, 255);
                String current = ClientSkillSettings.value(row.setting());
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
                label(g, row.text(), rx, ry, CHECK_X - 15, 255);
                Boolean on = on(row.setting());
                if (on != null) DeveloperScreen.quad(pose, on ? TerminalSettingsScreen.CHECK_TRUE : TerminalSettingsScreen.CHECK_FALSE,
                        rx + CHECK_X, ry + CHECK_Y, CHECK, CHECK, 1, 1, 1, ready ? 1 : .5f);
            }
            case NOTE -> {
                // 注記は長いので、行の幅で3行まで折り返す（1行に縮めると読めない。2行では英語の文が切れた）。
                // 攻撃対象が全部OFFの警告は、Electron Missileの注記の行だけで出す。
                boolean warn = ElectronMissile.ID.equals(row.skill()) && allTargetsOff();
                var note = Component.translatable(warn ? "academy.electron_missile.target.none" : row.text());
                float size = NOTE_SIZE, scale = size / 9f;
                var lines = font.split(note, (int) ((ROW_W - 30) / scale));
                // 欄に入りきらない注記は、切り捨てる前にログへ記録する（言語ごとに1回。client smoke の検査が FAIL にする）。
                if (lines.size() > NOTE_MAX_LINES) {
                    String key = minecraft.options.languageCode + " " + (warn ? "academy.electron_missile.target.none" : row.text());
                    if (OVERFLOW_REPORTED.add(key))
                        com.mojang.logging.LogUtils.getLogger().warn("ACADEMY_SETTINGS_NOTE_OVERFLOW: lang={} key={} lines={} max={} (the rest is cut off)",
                                minecraft.options.languageCode, warn ? "academy.electron_missile.target.none" : row.text(), lines.size(), NOTE_MAX_LINES);
                }
                for (int i = 0; i < Math.min(NOTE_MAX_LINES, lines.size()); i++) {
                    pose.pushPose();
                    pose.translate(rx + 15, ry + 4 + i * (size + 4), 0);
                    pose.scale(scale, scale, 1);
                    g.drawString(font, lines.get(i), 0, 0, ((warn ? 255 : 190) << 24) | 0xffffff, false);
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
            case BEETLE_RECALL -> {
                label(g, row.text(), rx, ry, STEP_X - 5, ready ? 255 : 120);
                float wide = STEP_ARROW * 2 + STEP_VALUE;
                stepBox(g, Component.translatable("academy.ability_settings.beetle.recall.do").getString(), rx + STEP_X, ry, wide, ready && in(mx, my, rx + STEP_X, ry + OPTION_Y, wide, OPTION_H), ready);
            }
            case HEAVEN_OPEN -> {
                label(g, row.text(), rx, ry, STEP_X - 5, 255);
                float wide = STEP_ARROW * 2 + STEP_VALUE;
                stepBox(g, Component.translatable("academy.ability_settings.heaven.open.do").getString(), rx + STEP_X, ry, wide, in(mx, my, rx + STEP_X, ry + OPTION_Y, wide, OPTION_H), true);
            }
            case ANALYSIS -> drawAnalysis(g, row.text(), rx, ry, mx, my);
            case PASSIVE -> drawPassive(g, row, rx, ry, mx, my);
            case SAFETY_FORM -> {
                // 形態の維持は最上位で固定（動かせない）。
                label(g, row.text(), rx, ry, STEP_X - 5, 200);
                TerminalHud.text(g, font, Component.translatable("academy.ability_settings.safety_order.locked").getString(), rx + STEP_X, ry + OPTION_Y,
                        STEP_ARROW * 2 + STEP_VALUE, OPTION_H, TEXT, 1, 1, 160);
            }
            case SAFETY_ORDER -> drawOrder(g, row, rx, ry, mx, my);
            case FORM_RESERVE, SAFETY_PRESET, SAFETY_PERCENT, CP_DISPLAY, CP_IDLE_SHOW, CP_IDLE, CP_IDLE_CUSTOM, BEETLE_CHOICE, BEETLE_FOLLOW, WING_GLOW, ANALYSIS_MODE, KEY_HINT_PASSIVE -> {
                boolean enabled = stepEnabled(row);
                label(g, row.text(), rx, ry, STEP_X - 5, enabled ? 255 : 120);
                stepBox(g, "<", rx + STEP_X, ry, STEP_ARROW, enabled && in(mx, my, rx + STEP_X, ry + OPTION_Y, STEP_ARROW, OPTION_H), enabled);
                TerminalHud.text(g, font, stepText(row), rx + STEP_X + STEP_ARROW, ry + OPTION_Y, STEP_VALUE, OPTION_H, TEXT, 1, 1, enabled ? 255 : 120);
                float next = rx + STEP_X + STEP_ARROW + STEP_VALUE;
                stepBox(g, ">", next, ry, STEP_ARROW, enabled && in(mx, my, next, ry + OPTION_Y, STEP_ARROW, OPTION_H), enabled);
            }
        }
    }
    /** 1.5.2: 編集しているプリセット（0〜3）。 */
    public int orderPreset() {
        if (orderPreset < 0) { var d = data(); orderPreset = d == null ? 0 : d.getCurrentPreset(); }
        return orderPreset;
    }
    /** テスト用: 編集するプリセットを選ぶ。 */
    public void setOrderPreset(int preset) { orderPreset = Math.floorMod(preset, PlayerAbilityData.PRESET_COUNT); }
    /** そのプリセットのサーバーの並びの文字列（届いていない・既定なら空）。 */
    private static String orderCode(int preset) {
        String value = ClientSkillSettings.value(AbilitySettings.safetyOrder(preset));
        return value == null || AbilitySettings.ORDER_DEFAULT.equals(value) ? "" : value;
    }
    /** 欄に並べる技能（今の系統の習得済みの候補を、選んだプリセットの並びの順で）。 */
    public List<ResourceLocation> shownOrder() {
        var d = data();
        return d == null ? List.of() : io.github.pinchan4273.reacademycraft.skill.SafetyPriority.candidates(d.getAbility(), d::hasLearned, orderCode(orderPreset()));
    }
    /** 並びの index 番目を1つ上（dir ＝ −1）か下（＋1）へ動かす（サーバーへ送る。返事を待つ間は送らない）。動かしたら true。 */
    public boolean moveOrder(int index, int dir) {
        var setting = AbilitySettings.safetyOrder(orderPreset());
        if (!ClientSkillSettings.readable() || ClientSkillSettings.value(setting) == null || ClientSkillSettings.pending(setting)) return false;
        String code = io.github.pinchan4273.reacademycraft.skill.SafetyPriority.move(orderCode(orderPreset()), shownOrder(), index, dir);
        if (code == null) return false;
        ClientSkillSettings.request(setting, code.isEmpty() ? AbilitySettings.ORDER_DEFAULT : code);
        return true;
    }
    /** セーフティの並びの1行: 順位・技能のアイコン・名前・動作中のインジケーター・上と下のボタン。 */
    private void drawOrder(GuiGraphics g, Row row, float rx, float ry, float mx, float my) {
        int index;
        try { index = Integer.parseInt(row.text()); } catch (NumberFormatException e) { return; }
        var shown = shownOrder();
        if (index < 0 || index >= shown.size()) return;
        var skill = shown.get(index);
        boolean ready = ClientSkillSettings.readable() && ClientSkillSettings.value(AbilitySettings.safetyOrder(orderPreset())) != null
                && !ClientSkillSettings.pending(AbilitySettings.safetyOrder(orderPreset()));
        TerminalHud.text(g, font, (index + 1) + ".", rx + 15, ry + 10, ORDER_ICON_X - 20, 40, TEXT, 0, 1, 220);
        var icon = io.github.pinchan4273.reacademycraft.skill.SkillTreeLayout.icon(skill);
        if (icon != null) DeveloperScreen.quad(g.pose(), icon, rx + ORDER_ICON_X, ry + 10, 40, 40, 1, 1, 1, 1);
        TerminalHud.text(g, font, Component.translatable("academy.skill." + skill.getPath()).getString(), rx + ORDER_NAME_X, ry + 10,
                ORDER_RUN_X - ORDER_NAME_X - 10, 40, TEXT, 0, 1, 255);
        // 動いている技能は明るい印（サーバーから届いたモードの状態）。
        float c = ClientSkillModes.active(skill) ? 1 : .25f;
        DeveloperScreen.rect(g.pose(), rx + ORDER_RUN_X, ry + 22.5f, 15, 15, c * .55f, c, c * .7f, 1);
        float up = rx + ORDER_UP_X, down = up + ORDER_BUTTON + 10;
        boolean canUp = ready && index > 0, canDown = ready && index < shown.size() - 1;
        stepBox(g, "\u25B2", up, ry, ORDER_BUTTON, canUp && in(mx, my, up, ry + OPTION_Y, ORDER_BUTTON, OPTION_H), canUp);
        stepBox(g, "\u25BC", down, ry, ORDER_BUTTON, canDown && in(mx, my, down, ry + OPTION_Y, ORDER_BUTTON, OPTION_H), canDown);
    }
    /**
     * その切り替えの行を今パッシブにできない理由の翻訳キー（できればnull）。アクティブへ戻すのはいつでもできるので、今パッシブなら理由は出さない。
     * 判定はサーバーと同じ{@link io.github.pinchan4273.reacademycraft.skill.PassiveSkills#refusal}。
     */
    public @Nullable String passiveRefusal(Row row) {
        var skill = io.github.pinchan4273.reacademycraft.skill.PassiveSkills.skillOfSetting(row.setting());
        if (skill == null) return "academy.ability_settings.passive.reason.not_switchable";
        if (io.github.pinchan4273.reacademycraft.skill.PassiveSkills.PASSIVE.equals(ClientSkillSettings.value(row.setting()))) return null;
        var refusal = io.github.pinchan4273.reacademycraft.skill.PassiveSkills.refusal(data(), skill);
        return refusal == null ? null : refusal.translation();
    }
    /**
     * パッシブ／アクティブの切り替えの1行: 技能の名前と、アクティブ・パッシブの2つのボックス（今の値を明るく）。切り替えられないときは、ボックスの代わりに
     * 理由を灰色で出す（例: 「熟練度100%で選べます」）。
     */
    private void drawPassive(GuiGraphics g, Row row, float rx, float ry, float mx, float my) {
        var pose = g.pose();
        boolean ready = ClientSkillSettings.readable();
        String reason = passiveRefusal(row);
        label(g, row.text(), rx, ry, PASSIVE_X - 10, reason == null ? 255 : 120);
        if (reason != null) {
            TerminalHud.text(g, font, Component.translatable(reason).getString(), rx + PASSIVE_X, ry + OPTION_Y, PASSIVE_W * 2 + OPTION_GAP, OPTION_H, TEXT, 1, 1, 120);
            return;
        }
        String current = ClientSkillSettings.value(row.setting());
        for (int i = 0; i < PASSIVE_MODES.length; i++) {
            float ox = rx + PASSIVE_X + i * (PASSIVE_W + OPTION_GAP);
            boolean chosen = PASSIVE_MODES[i].equals(current);
            boolean hover = ready && in(mx, my, ox, ry + OPTION_Y, PASSIVE_W, OPTION_H);
            float c = (chosen ? 140 : hover ? 85 : 51) / 255f;
            DeveloperScreen.rect(pose, ox, ry + OPTION_Y, PASSIVE_W, OPTION_H, c * (chosen ? .55f : 1), c * (chosen ? .85f : 1), c, 1);
            TerminalHud.text(g, font, Component.translatable("academy.ability_settings.passive." + PASSIVE_MODES[i]).getString(), ox, ry + OPTION_Y,
                    PASSIVE_W, OPTION_H, TEXT, 1, 1, ready ? 255 : 120);
        }
    }
    /** 解析度の1項目の行の、割合の文字の右端（チェックボックスの手前）。 */
    static final float ANALYSIS_PERCENT_RIGHT = CHECK_X - 20, ANALYSIS_PERCENT_W = 110;
    /**
     * 解析度の1項目: 分類の名前、その下の細いバー、割合（切り捨ての整数）、「HUDに出す」のチェックボックス。名前は翻訳キーではなく、
     * 届いた一覧の表示の名前（{@link ClientVectorAnalysis#name(String)}）。
     */
    private void drawAnalysis(GuiGraphics g, String key, float rx, float ry, float mx, float my) {
        var pose = g.pose();
        float value = ClientVectorAnalysis.value(key);
        boolean full = io.github.pinchan4273.reacademycraft.skill.VectorAnalysis.full(value);
        float nameRoom = ANALYSIS_PERCENT_RIGHT - ANALYSIS_PERCENT_W - 15;
        TerminalHud.text(g, font, ClientVectorAnalysis.name(key).getString(), rx + 15, ry + 6, nameRoom - 10, 36, TEXT, 0, 1, 255);
        DeveloperScreen.rect(pose, rx + 15, ry + ROW_H - 12, nameRoom - 10, 5, 48 / 255f, 48 / 255f, 48 / 255f, 1);
        float filled = (nameRoom - 10) * AnalysisHudStyle.fill(value);
        if (filled > 0) DeveloperScreen.rect(pose, rx + 15, ry + ROW_H - 12, filled, 5, full ? 1 : .27f, full ? .82f : .70f, full ? .25f : 1, 1);
        TerminalHud.text(g, font, AnalysisHudStyle.percent(value), rx + ANALYSIS_PERCENT_RIGHT - ANALYSIS_PERCENT_W, ry + 10, ANALYSIS_PERCENT_W, 40, TEXT, 2, 1, 255);
        boolean on = ClientSettings.analysisHudSelected().contains(key);
        boolean hover = in(mx, my, rx + CHECK_X, ry + CHECK_Y, CHECK, CHECK);
        DeveloperScreen.quad(pose, on ? TerminalSettingsScreen.CHECK_TRUE : TerminalSettingsScreen.CHECK_FALSE, rx + CHECK_X, ry + CHECK_Y, CHECK, CHECK,
                1, 1, 1, hover ? 1 : .85f);
    }
    private void stepBox(GuiGraphics g, String text, float x, float ry, float w, boolean hover, boolean enabled) {
        float c = (hover ? 85 : 51) / 255f;
        DeveloperScreen.rect(g.pose(), x, ry + OPTION_Y, w, OPTION_H, c, c, c, 1);
        TerminalHud.text(g, font, text, x, ry + OPTION_Y, w, OPTION_H, TEXT, 1, 1, enabled ? 255 : 120);
    }
    /** その行を今動かせるか。セーフティの値はサーバーの返事が要り、任意倍率は「任意」を選んだときだけ効く。 */
    public boolean stepEnabled(Row row) {
        return switch (row.kind()) {
            case SAFETY_PERCENT, BEETLE_CHOICE, BEETLE_FOLLOW -> ClientSkillSettings.readable() && ClientSkillSettings.value(row.setting()) != null;
            // 1.5.2: 形態の予約は、未元物質人間で、セーフティがオンのときだけ動かせる（それ以外は灰色。説明は注記の行）。
            case FORM_RESERVE -> {
                var d = data();
                yield ClientSkillSettings.readable() && ClientSkillSettings.value(row.setting()) != null && d != null && d.isDarkBody() && d.isCpSafety();
            }
            case CP_IDLE_CUSTOM -> ClientSettings.cpIdleSize() == CpHudStyle.IdleSize.CUSTOM;
            default -> true;
        };
    }
    /** 行に出す今の値。 */
    public String stepText(Row row) {
        return switch (row.kind()) {
            case SAFETY_PERCENT, FORM_RESERVE -> {
                String value = ClientSkillSettings.value(row.setting());
                yield value == null ? "-" : value + "%";
            }
            case SAFETY_PRESET -> Component.translatable("academy.ability_settings.safety_order.preset.value", orderPreset() + 1).getString();
            case BEETLE_CHOICE -> {
                String value = ClientSkillSettings.value(row.setting());
                yield value == null ? "-" : Component.translatable(row.text() + "." + value).getString();
            }
            case BEETLE_FOLLOW -> {
                String value = ClientSkillSettings.value(row.setting());
                yield value == null ? "-" : Component.translatable(row.text() + ".value", value).getString();
            }
            case CP_DISPLAY -> Component.translatable(ClientSettings.cpDisplay().translation()).getString();
            case CP_IDLE_SHOW -> Component.translatable(ClientSettings.cpIdleShow().translation()).getString();
            case CP_IDLE -> Component.translatable(ClientSettings.cpIdleSize().translation()).getString();
            case CP_IDLE_CUSTOM -> ClientSettings.cpIdleCustom() + "%";
            case WING_GLOW -> ClientSettings.wingGlow() + "%";
            case ANALYSIS_MODE -> Component.translatable(ClientSettings.analysisHudMode().translation()).getString();
            case KEY_HINT_PASSIVE -> Component.translatable(ClientSettings.keyHintPassive().translation()).getString();
            default -> "";
        };
    }
    /** 行の値を動かす。dirは+1か-1、coarseは停止ラインの10%刻み（Shiftを押しながら）。 */
    public void step(Row row, int dir, boolean coarse) {
        if (!stepEnabled(row)) return;
        switch (row.kind()) {
            case SAFETY_PERCENT -> {
                // 返事を待つ間の連打は、古い値からの計算になるので送らない。
                if (ClientSkillSettings.pending(row.setting())) return;
                int now;
                try { now = Integer.parseInt(ClientSkillSettings.value(row.setting())); } catch (NumberFormatException e) { return; }
                int value = CpHudStyle.stepSafety(now, dir * (coarse ? 10 : 1), PlayerAbilityData.CP_SAFETY_MIN_PERCENT, PlayerAbilityData.CP_SAFETY_MAX_PERCENT);
                if (value != now) ClientSkillSettings.request(row.setting(), Integer.toString(value));
            }
            case FORM_RESERVE -> {
                // 1%刻み（Shiftで5%）。端では止まる。返事を待つ間の連打は送らない。
                if (ClientSkillSettings.pending(row.setting())) return;
                int now;
                try { now = Integer.parseInt(ClientSkillSettings.value(row.setting())); } catch (NumberFormatException e) { return; }
                int value = net.minecraft.util.Mth.clamp(now + dir * (coarse ? 5 : 1), PlayerAbilityData.FORM_RESERVE_MIN, PlayerAbilityData.FORM_RESERVE_MAX);
                if (value != now) ClientSkillSettings.request(row.setting(), Integer.toString(value));
            }
            case SAFETY_PRESET -> setOrderPreset(orderPreset() + dir);
            case BEETLE_CHOICE -> {
                if (ClientSkillSettings.pending(row.setting())) return;
                String[] choices = AbilitySettings.BEETLE_STANCE.equals(row.setting()) ? new String[]{"near", "mid", "far"}
                        : AbilitySettings.DARK_BODY_RESPAWN.equals(row.setting()) ? new String[]{AbilitySettings.RESPAWN_BODY, AbilitySettings.RESPAWN_HUMAN}
                        : AbilitySettings.FLIGHT_TAKEOFF.equals(row.setting()) ? new String[]{AbilitySettings.TAKEOFF_HOLD, AbilitySettings.TAKEOFF_DOUBLE}
                        // 1.5.2 N: ベクトル反射の反射方向（攻撃者へ → 視線方向 → 最寄りの敵 → 視界内の敵 の順に回る）。
                        : AbilitySettings.REFLECTION_DIRECTION.equals(row.setting()) ? io.github.pinchan4273.reacademycraft.skill.ReflectionDirection.ids()
                        : new String[]{"auto", "resummon"};
                ClientSkillSettings.request(row.setting(), CpHudStyle.cycle(choices, ClientSkillSettings.value(row.setting()), dir));
            }
            case BEETLE_FOLLOW -> {
                // 返事を待つ間の連打は、古い値からの計算になるので送らない。端では止まる（回り込まない）。
                if (ClientSkillSettings.pending(row.setting())) return;
                int now;
                try { now = Integer.parseInt(ClientSkillSettings.value(row.setting())); } catch (NumberFormatException e) { return; }
                int value = net.minecraft.util.Mth.clamp(now + dir * DarkMatterBeetles.FOLLOW_STEP, DarkMatterBeetles.FOLLOW_MIN, DarkMatterBeetles.FOLLOW_MAX);
                if (value != now && DarkMatterBeetles.validFollow(value)) ClientSkillSettings.request(row.setting(), Integer.toString(value));
            }
            case CP_DISPLAY -> ClientSettings.setCpDisplay(CpHudStyle.cycle(CpHudStyle.Display.values(), ClientSettings.cpDisplay(), dir));
            case CP_IDLE_SHOW -> ClientSettings.setCpIdleShow(CpHudStyle.cycle(CpHudStyle.IdleShow.values(), ClientSettings.cpIdleShow(), dir));
            case CP_IDLE -> ClientSettings.setCpIdleSize(CpHudStyle.cycle(CpHudStyle.IdleSize.values(), ClientSettings.cpIdleSize(), dir));
            case CP_IDLE_CUSTOM -> ClientSettings.setCpIdleCustom(ClientSettings.cpIdleCustom() + dir * CpHudStyle.CUSTOM_STEP);
            case WING_GLOW -> ClientSettings.setWingGlow(ClientSettings.wingGlow() + dir * ClientSettings.WING_GLOW_STEP);
            case ANALYSIS_MODE -> ClientSettings.setAnalysisHudMode(CpHudStyle.cycle(AnalysisHudStyle.Mode.values(), ClientSettings.analysisHudMode(), dir));
            // キーの案内のパッシブの行は、クライアントの個人設定なので、サーバーの返事を待たずに変える。
            case KEY_HINT_PASSIVE -> ClientSettings.setKeyHintPassive(CpHudStyle.cycle(KeyHintPassiveMode.values(), ClientSettings.keyHintPassive(), dir));
            default -> { }
        }
    }
    /** 行の文字（大きさ{@link #TEXT}、15内側から）。隣のものに届くところでは縮める。 */
    private void label(GuiGraphics g, String key, float rx, float ry, float room, int alpha) {
        TerminalHud.text(g, font, Component.translatable(key).getString(), rx + 15, ry + 10, room - 15, 40, TEXT, 0, 1, alpha);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        float x = ux(mx), y = uy(my);
        if (button == 0 && maxFirst() > 0 && in(x, y, TerminalSettingsScreen.BAR_X, barY(), TerminalSettingsScreen.BAR_W, TerminalSettingsScreen.BAR_H)) {
            dragging = true;
            return true;
        }
        if (button == 0) {
            boolean ready = ClientSkillSettings.readable();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                float ry = rowY(i);
                if (Float.isNaN(ry)) continue;
                if (row.kind() == Kind.SAFETY_ORDER) {
                    int index;
                    try { index = Integer.parseInt(row.text()); } catch (NumberFormatException e) { continue; }
                    if (in(x, y, AREA_X + ORDER_UP_X, ry + OPTION_Y, ORDER_BUTTON, OPTION_H)) { moveOrder(index, -1); return true; }
                    if (in(x, y, AREA_X + ORDER_UP_X + ORDER_BUTTON + 10, ry + OPTION_Y, ORDER_BUTTON, OPTION_H)) { moveOrder(index, 1); return true; }
                }
                if (row.kind() == Kind.FORM_RESERVE || row.kind() == Kind.SAFETY_PRESET || row.kind() == Kind.SAFETY_PERCENT || row.kind() == Kind.CP_DISPLAY || row.kind() == Kind.CP_IDLE_SHOW || row.kind() == Kind.CP_IDLE || row.kind() == Kind.CP_IDLE_CUSTOM
                        || row.kind() == Kind.BEETLE_CHOICE || row.kind() == Kind.BEETLE_FOLLOW || row.kind() == Kind.WING_GLOW || row.kind() == Kind.ANALYSIS_MODE
                        || row.kind() == Kind.KEY_HINT_PASSIVE) {
                    if (in(x, y, AREA_X + STEP_X, ry + OPTION_Y, STEP_ARROW, OPTION_H)) { step(row, -1, hasShiftDown()); return true; }
                    if (in(x, y, AREA_X + STEP_X + STEP_ARROW + STEP_VALUE, ry + OPTION_Y, STEP_ARROW, OPTION_H)) { step(row, 1, hasShiftDown()); return true; }
                }
                if (row.kind() == Kind.HEAVEN_OPEN && in(x, y, AREA_X + STEP_X, ry + OPTION_Y, STEP_ARROW * 2 + STEP_VALUE, OPTION_H)) { minecraft.setScreen(new HeavenSettingsScreen(this)); return true; }
                // 解析度のHUDに出すかは、クライアントの個人設定なので、サーバーの返事を待たずに切り替える。
                if (row.kind() == Kind.ANALYSIS && in(x, y, AREA_X + CHECK_X, ry + CHECK_Y, CHECK, CHECK)) { ClientSettings.toggleAnalysisHudSelected(row.text()); return true; }
                if (!ready) continue;
                if (row.kind() == Kind.BEETLE_RECALL && in(x, y, AREA_X + STEP_X, ry + OPTION_Y, STEP_ARROW * 2 + STEP_VALUE, OPTION_H)) {
                    ClientSkillSettings.request(row.setting(), "recall");
                    return true;
                }
                if (row.kind() == Kind.MODE) {
                    for (int m = 0; m < MODES.length; m++)
                        if (in(x, y, AREA_X + OPTION_X + m * (OPTION_W + OPTION_GAP), ry + OPTION_Y, OPTION_W, OPTION_H)) { choose(row, MODES[m]); return true; }
                }
                if (row.kind() == Kind.TARGET && in(x, y, AREA_X + CHECK_X, ry + CHECK_Y, CHECK, CHECK)) { toggle(row); return true; }
                // 1.5.2 D: 切り替えられない行（理由を出している行）は押しても送らない。サーバーも同じ条件で断る。
                if (row.kind() == Kind.PASSIVE && passiveRefusal(row) == null) {
                    for (int m = 0; m < PASSIVE_MODES.length; m++)
                        if (in(x, y, AREA_X + PASSIVE_X + m * (PASSIVE_W + OPTION_GAP), ry + OPTION_Y, PASSIVE_W, OPTION_H)) { choose(row, PASSIVE_MODES[m]); return true; }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    /** モードを選ぶ（今と同じでも送る。サーバーは何も変えずに値を返す）。 */
    public void choose(Row row, String mode) { ClientSkillSettings.request(row.setting(), mode); }
    /** 攻撃対象を反転する。値がまだ届いていなければ何もしない。 */
    public void toggle(Row row) {
        Boolean on = on(row.setting());
        if (on != null) ClientSkillSettings.request(row.setting(), on ? AbilitySettings.OFF : AbilitySettings.ON);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            progressBar = net.minecraft.util.Mth.clamp(progressBar + (float) (dy / SCALE) / (TerminalSettingsScreen.BAR_UPPER - TerminalSettingsScreen.BAR_LOWER), 0, 1);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }
    /** ホイールで1行ずつ動かす（1.20.2から縦の量は4つ目の引数）。 */
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int max = maxFirst();
        if (max > 0 && delta != 0) progressBar = net.minecraft.util.Mth.clamp((first() - Math.signum((float) delta)) / max, 0, 1);
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    // サーバーが値を切り替え、画面はその返事を表示する。一時停止した統合サーバーは返事をしないので、この画面は一時停止しない。
    @Override public boolean isPauseScreen() { return false; }

    // テスト用の入口。
    public List<Row> rows() { return List.copyOf(rows); }
    public Row row(ResourceLocation setting) { return rows.stream().filter(r -> setting.equals(r.setting())).findFirst().orElseThrow(); }
}
