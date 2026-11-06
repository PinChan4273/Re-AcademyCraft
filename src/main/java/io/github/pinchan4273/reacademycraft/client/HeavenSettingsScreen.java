package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.HeavenSettingEdit;
import io.github.pinchan4273.reacademycraft.skill.DarkMatter;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Choice;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Effect;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Frequency;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Pair;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Phenomenon;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.PlayerMode;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 天界発現の設定の画面（v1.5.0 §10〜§12）。能力設定の画面の行から開く。窓と行の形は{@link AbilitySettingsScreen}と同じで、すべて「&lt; 値 &gt;」の行にしている。
 * 対象区分と現象は選択の行で1つを選び、その区分・現象の行を出す（6区分×5組と10現象×8項目を全部並べると一覧が読めないため）。
 * 効果と現象の値はサーバーが正本で、押すと変更を送り、返事の値を表示する（断られたら元へ戻る）。表示の項目（発光の強度・リング・境界・環境音）は{@link ClientSettings}で、効果と費用を変えない。
 */
public final class HeavenSettingsScreen extends Screen {
    private static final float W = AbilitySettingsScreen.W, H = AbilitySettingsScreen.H, SCALE = AbilitySettingsScreen.SCALE, TEXT = AbilitySettingsScreen.TEXT;
    private static final float AREA_X = AbilitySettingsScreen.AREA_X, AREA_Y = AbilitySettingsScreen.AREA_Y, AREA_H = AbilitySettingsScreen.AREA_H;
    private static final float ROW_W = AbilitySettingsScreen.ROW_W, ROW_H = AbilitySettingsScreen.ROW_H;
    private static final float STEP_X = AbilitySettingsScreen.STEP_X, ARROW = AbilitySettingsScreen.STEP_ARROW, VALUE = AbilitySettingsScreen.STEP_VALUE;
    private static final float BOX_Y = AbilitySettingsScreen.OPTION_Y, BOX_H = AbilitySettingsScreen.OPTION_H;

    /** 1行。headは見出し（動かせない）。enabledがfalseの行は暗く、押しても動かない。 */
    private record Row(boolean head, Supplier<String> label, Supplier<String> value, IntConsumer step, BooleanSupplier enabled) { }

    private final @Nullable Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private Target target = Target.SELF;
    private Phenomenon phenomenon = Phenomenon.BURN;
    private int first;
    private float x0, y0;

    public HeavenSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("academy.heaven_settings.title"));
        this.parent = parent;
    }
    private static String tr(String key) { return Component.translatable(key).getString(); }
    private static HeavenSettings settings() { return ClientHeavenSettings.current(); }
    private static <T> T next(T[] all, T now, int dir) {
        int at = 0;
        for (int i = 0; i < all.length; i++) if (all[i] == now) at = i;
        return all[Math.floorMod(at + dir, all.length)];
    }
    private static String onOff(boolean on) { return tr("academy.heaven_settings." + (on ? "on" : "off")); }

    @Override protected void init() {
        super.init();
        x0 = (width - W * SCALE) / 2; y0 = (height - H * SCALE) / 2;
        ClientHeavenSettings.request(HeavenSettingEdit.SYNC, "");
        rows.clear();
        head("academy.heaven_settings.section.control");
        rows.add(new Row(false, () -> tr("academy.heaven_settings.stop"),
                () -> tr(ClientSkillModes.active(DarkMatter.HEAVEN) ? "academy.heaven_settings.stop.do" : "academy.heaven_settings.stop.inactive"),
                dir -> ClientHeavenSettings.request(HeavenSettingEdit.STOP, ""), () -> ClientSkillModes.active(DarkMatter.HEAVEN)));
        head("academy.heaven_settings.section.effects");
        rows.add(new Row(false, () -> tr("academy.heaven_settings.target_select"), () -> tr("academy.heaven.target." + target.id()),
                dir -> target = next(Target.values(), target, dir), () -> true));
        for (var pair : Pair.values()) rows.add(effectRow(pair));
        head("academy.heaven_settings.section.phenomena");
        rows.add(new Row(false, () -> tr("academy.heaven_settings.status_frame"), () -> frequencyName(settings().statusFrameFrequency()),
                dir -> ClientHeavenSettings.request(HeavenSettings.KEY_STATUS_FRAME, next(Frequency.values(), settings().statusFrameFrequency(), dir).id()), ClientHeavenSettings::readable));
        rows.add(new Row(false, () -> tr("academy.heaven_settings.phenomenon_select"), () -> tr("academy.heaven.phenomenon." + phenomenon.id()),
                dir -> phenomenon = next(Phenomenon.values(), phenomenon, dir), () -> true));
        rows.add(new Row(false, () -> tr("academy.heaven_settings.phenomenon_on"), () -> onOff(settings().phenomenonOn(phenomenon)),
                dir -> ClientHeavenSettings.request("phen." + phenomenon.id() + ".on", settings().phenomenonOn(phenomenon) ? "off" : "on"), ClientHeavenSettings::readable));
        rows.add(new Row(false, () -> tr("academy.heaven_settings.frequency"), () -> frequencyName(settings().frequency(phenomenon)),
                dir -> ClientHeavenSettings.request("phen." + phenomenon.id() + ".freq", next(Frequency.values(), settings().frequency(phenomenon), dir).id()), ClientHeavenSettings::readable));
        for (var t : Target.values()) rows.add(new Row(false, () -> tr("academy.heaven.target." + t.id()),
                () -> HeavenSettings.allowed(phenomenon, t) ? onOff(settings().targets(phenomenon, t)) : "-",
                dir -> ClientHeavenSettings.request("phen." + phenomenon.id() + ".target." + t.id(), settings().targets(phenomenon, t) ? "off" : "on"),
                () -> ClientHeavenSettings.readable() && HeavenSettings.allowed(phenomenon, t)));
        head("academy.heaven_settings.section.players");
        playerRows();
        head("academy.heaven_settings.section.display");
        rows.add(new Row(false, () -> tr("academy.heaven_settings.glow"), () -> ClientSettings.heavenGlowPercent() + "%",
                dir -> ClientSettings.setHeavenGlowPercent(ClientSettings.heavenGlowPercent() + dir * 10), () -> true));
        displayToggle("academy.heaven_settings.rings", ClientSettings.HEAVEN_RINGS);
        displayToggle("academy.heaven_settings.border", ClientSettings.HEAVEN_BORDER);
        displayToggle("academy.heaven_settings.sound", ClientSettings.HEAVEN_SOUND);
        first = Math.min(first, maxFirst());
    }
    private void head(String key) { rows.add(new Row(true, () -> tr(key), () -> "", dir -> { }, () -> false)); }
    private void displayToggle(String key, String id) {
        rows.add(new Row(false, () -> tr(key), () -> onOff(ClientSettings.getOn(id)), dir -> ClientSettings.set(id, !ClientSettings.getOn(id)), () -> true));
    }
    private static String frequencyName(Frequency frequency) { return tr("academy.heaven.frequency." + frequency.id()); }
    /** 効果の組の行: OFF／強化／弱体化（自分の甲虫へ害は選べないので弱体化を飛ばす）。今の対象区分を読むので、区分を変えると行の値も変わる。 */
    private Row effectRow(Pair pair) {
        return new Row(false, () -> tr("academy.heaven.pair." + pair.id()), () -> {
            var choice = settings().choice(target, pair);
            return choice == Choice.OFF ? tr("academy.heaven_settings.off") : tr("academy.heaven.effect." + Effect.of(pair, choice).name().toLowerCase(java.util.Locale.ROOT));
        }, dir -> {
            var now = settings().choice(target, pair);
            var choice = next(Choice.values(), now, dir);
            if (!HeavenSettings.allowed(target, pair, choice)) choice = next(Choice.values(), choice, dir);
            ClientHeavenSettings.request(HeavenSettings.KEY_EFFECT + target.id() + "." + pair.id(), choice.id());
        }, ClientHeavenSettings::readable);
    }
    /** 個別プレイヤーの行: 今接続している他のプレイヤーと、登録済みのプレイヤー。 */
    private void playerRows() {
        var names = new LinkedHashMap<UUID, String>();
        settings().players().forEach((id, entry) -> names.put(id, entry.name()));
        if (minecraft != null && minecraft.getConnection() != null) {
            for (var info : minecraft.getConnection().getOnlinePlayers()) {
                var id = info.getProfile().id();
                if (minecraft.player == null || !id.equals(minecraft.player.getUUID())) names.put(id, info.getProfile().name());
            }
        }
        if (names.isEmpty()) rows.add(new Row(false, () -> tr("academy.heaven_settings.no_players"), () -> "-", dir -> { }, () -> false));
        names.forEach((id, name) -> rows.add(new Row(false, () -> name, () -> tr("academy.heaven.player_mode." + settings().playerMode(id).id()),
                dir -> ClientHeavenSettings.request(HeavenSettings.KEY_PLAYER + id, next(PlayerMode.values(), settings().playerMode(id), dir).id()), ClientHeavenSettings::readable)));
    }

    private int visible() { return (int) (AREA_H / ROW_H); }
    private int maxFirst() { return Math.max(0, rows.size() - visible()); }
    private float ux(double mx) { return (float) ((mx - x0) / SCALE); }
    private float uy(double my) { return (float) ((my - y0) / SCALE); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    private static void window(GuiGraphicsExtractor pose) {
        var tex = TerminalSettingsScreen.WINDOW;
        float top = AbilitySettingsScreen.TITLE_TOP, bottom = AbilitySettingsScreen.TITLE_BOTTOM;
        DeveloperScreen.quad(pose, tex, 0, 0, W, top, 0, 0, 1, top / H, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, tex, 0, top, W, bottom - top, 0, AbilitySettingsScreen.CLEAN_TOP / H, 1, AbilitySettingsScreen.CLEAN_BOTTOM / H, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, tex, 0, bottom, W, H - bottom, 0, bottom / H, 1, 1, 1, 1, 1, 1);
    }
    /** 26.2: 背景は{@link #extractRenderState}が描く（1.21.1の{@code renderBackground}と同じ半透明の背景。能力設定の画面と同じ）。 */
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) { }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        extractTransparentBackground(g);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale(SCALE, SCALE);
        window(g);
        TerminalHud.text(g, font, title.getString(), AbilitySettingsScreen.TITLE_X, AbilitySettingsScreen.TITLE_TOP, ROW_W - 40, AbilitySettingsScreen.TITLE_BOTTOM - AbilitySettingsScreen.TITLE_TOP, TEXT, 0, 1, 235);
        float x = ux(mx), y = uy(my);
        int shown = Math.min(rows.size() - first, visible());
        for (int i = 0; i < shown; i++) draw(g, rows.get(first + i), AREA_Y + i * ROW_H, x, y);
        pose.popMatrix();
        io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShaderColor(1, 1, 1, 1);
    }
    private void draw(GuiGraphicsExtractor g, Row row, float ry, float mx, float my) {
        var pose = g;
        if (row.head()) {
            TerminalHud.text(g, font, row.label().get(), AREA_X + 8, ry, ROW_W - 8, ROW_H, TEXT, 0, 2, 200);
            DeveloperScreen.rect(pose, AREA_X, ry + ROW_H - 4, ROW_W, 4, 1, 1, 1, 150 / 255f);
            return;
        }
        boolean enabled = row.enabled().getAsBoolean();
        int alpha = enabled ? 255 : 120;
        TerminalHud.text(g, font, row.label().get(), AREA_X + 15, ry + 10, STEP_X - 20, 40, TEXT, 0, 1, alpha);
        float left = AREA_X + STEP_X, right = left + ARROW + VALUE;
        box(g, "<", left, ry, enabled && in(mx, my, left, ry + BOX_Y, ARROW, BOX_H), alpha);
        TerminalHud.text(g, font, row.value().get(), left + ARROW, ry + BOX_Y, VALUE, BOX_H, TEXT, 1, 1, alpha);
        box(g, ">", right, ry, enabled && in(mx, my, right, ry + BOX_Y, ARROW, BOX_H), alpha);
    }
    private void box(GuiGraphicsExtractor g, String text, float x, float ry, boolean hover, int alpha) {
        float c = (hover ? 85 : 51) / 255f;
        DeveloperScreen.rect(g, x, ry + BOX_Y, ARROW, BOX_H, c, c, c, 1);
        TerminalHud.text(g, font, text, x, ry + BOX_Y, ARROW, BOX_H, TEXT, 1, 1, alpha);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (button == 0) {
            float x = ux(mx), y = uy(my);
            int shown = Math.min(rows.size() - first, visible());
            for (int i = 0; i < shown; i++) {
                var row = rows.get(first + i);
                float ry = AREA_Y + i * ROW_H;
                if (row.head() || !row.enabled().getAsBoolean()) continue;
                if (in(x, y, AREA_X + STEP_X, ry + BOX_Y, ARROW, BOX_H)) { row.step().accept(-1); return true; }
                if (in(x, y, AREA_X + STEP_X + ARROW + VALUE, ry + BOX_Y, ARROW, BOX_H)) { row.step().accept(1); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) { // 1.20.2から縦の量は4つ目の引数
        if (delta != 0) first = net.minecraft.util.Mth.clamp(first - (int) Math.signum(delta), 0, maxFirst());
        return true;
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    // サーバーの返事を表示するので、一時停止しない（能力設定の画面と同じ）。
    @Override public boolean isPauseScreen() { return false; }
}
