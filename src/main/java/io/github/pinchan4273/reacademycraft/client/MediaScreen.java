package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.media.Media;
import io.github.pinchan4273.reacademycraft.client.media.MediaLibrary;
import io.github.pinchan4273.reacademycraft.client.media.MediaPlayer;
import io.github.pinchan4273.reacademycraft.client.media.MediaSettings;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * 原作MediaGui（guis/media_player.xml、AcademyCraft commit 7b1401c）: 0.32倍で中央に置いた650x504の窓。
 * 再生/一時停止と停止、音量バー、再生位置、タイトル。その下に、プレイヤーが持つ全メディアの一覧
 * （カバー、名前、説明、長さ）を1ページ5件で並べ、横にスクロールバーを置く。クリックで再生する。
 * 外部メディアには2本の鉛筆があり、名前と説明を書き換えられる。位置・大きさ・色は原作のもので、
 * 単位は原作のウィジェット単位。表示する時間とタイトルは、原作と同じく0.5秒ごとに更新する。
 *
 * 原作は独自のTrueTypeフォントで描くが、ここではゲームのフォントを原作の大きさで使う。
 */
public final class MediaScreen extends Screen {
    static final float SCALE = .32f, WIDTH = 650, HEIGHT = 504;
    private static final ResourceLocation BACK = tex("apps/media_player/back"), PLAY = tex("apps/media_player/play"),
            PAUSE = tex("apps/media_player/pause"), STOP = tex("apps/media_player/stop"),
            VOLUME = tex("icons/volume_overlay"), EDIT = tex("icons/edit");
    // ウィジェット（media_player.xmlより）。
    static final float POP_X = 51.85185f, STOP_X = 114.81481f, BUTTON_Y = 71.79296f, BUTTON_W = 50, BUTTON_H = 42;
    public static final float AREA_X = 51, AREA_Y = 169, AREA_W = 552, AREA_H = 302, ROW_W = 554, ROW_H = 60;
    static final float SCROLL_X = 604, SCROLL_W = 5, SCROLL_H = 55, SCROLL_LOWER = 169, SCROLL_UPPER = 415;
    static final float VOLUME_X = 179.6875f, VOLUME_Y = 77, VOLUME_BAR_W = 9.375f, VOLUME_BAR_H = 30, VOLUME_BAR_Y = 77.875f,
            VOLUME_LOWER = 186, VOLUME_UPPER = 298;
    static final float EDIT_X = 368, EDIT_NAME_Y = 7.125f, EDIT_DESC_Y = 35.25f, EDIT_SIZE = 20;
    /** ElementListは、領域に丸ごと入る行だけを表示する。 */
    static final int PAGE = (int) (AREA_H / ROW_H);

    private final List<Media> installed;
    /** 取得済みだがここに音声が無いmod自身のメディア。再生できるものの後に並べる。 */
    private final List<String> missing;
    private float x0, y0;
    private int scroll;
    private float scrollY = SCROLL_LOWER, volumeX;
    private boolean draggingScroll, draggingVolume;
    private float grab;
    // 表示の内容（最後の更新時点）。
    private String playTime = "00:00", title = "";
    private float progress;
    private boolean showPause;
    private double lastTest;
    // 原作のwrapEdit: 書き換え中のテキストボックス（あれば）。
    @Nullable private Media editing;
    private boolean editingName;
    private TextEdit buffer = new TextEdit("", 64);

    public MediaScreen() {
        super(Component.translatable("academy.app.media_player.name"));
        var player = Minecraft.getInstance().player;
        installed = MediaLibrary.installed(player);
        missing = MediaLibrary.missing(player);
    }
    private static ResourceLocation tex(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png"); }

    @Override protected void init() {
        super.init();
        x0 = (width - WIDTH * SCALE) / 2; y0 = (height - HEIGHT * SCALE) / 2;
        volumeX = VOLUME_LOWER + MediaPlayer.getVolume() * (VOLUME_UPPER - VOLUME_LOWER);
        updatePlayDisplay();
    }
    private int maxProgress() { return Math.max(0, installed.size() + missing.size() - PAGE); }
    /** 原作のウィジェット単位の点をGUI座標へ変換する。 */
    public float guiX(float x) { return x0 + x * SCALE; }
    public float guiY(float y) { return y0 + y * SCALE; }
    private float lx(double mx) { return (float) ((mx - x0) / SCALE); }
    private float ly(double my) { return (float) ((my - y0) / SCALE); }
    private static boolean in(float x, float y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }
    /** このメディアを表示する行の上端（ウィジェット単位）。スクロールで見えないときはNaN。 */
    public float rowY(int index) {
        int row = index - scroll;
        return row < 0 || row >= PAGE ? Float.NaN : AREA_Y + row * ROW_H;
    }
    @Nullable private Media rowAt(float x, float y) {
        if (!in(x, y, AREA_X, AREA_Y, ROW_W, PAGE * ROW_H)) return null;
        int index = scroll + (int) ((y - AREA_Y) / ROW_H);
        return index < installed.size() ? installed.get(index) : null;
    }

    /** 原作updatePlayDisplay: 再生中のものを、時間・バー・タイトル・ボタンへ反映する。 */
    void updatePlayDisplay() {
        var playing = MediaPlayer.currentPlaying();
        if (playing.isPresent()) {
            var info = playing.get();
            playTime = info.displayTime();
            progress = info.time() / info.media().lengthSecs();
            title = info.media().name();
            showPause = !info.paused();
        } else {
            playTime = "00:00"; progress = 0; title = ""; showPause = false;
        }
        lastTest = Util.getMillis() / 1000.0;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        float x = lx(mx), y = ly(my);
        // 他の場所をクリックすると入力を終え、確定する（原作のLostFocusEvent）。
        if (editing != null) confirmEdit();
        if (in(x, y, POP_X, BUTTON_Y, BUTTON_W, BUTTON_H)) { playOrPause(); return true; }
        if (in(x, y, STOP_X, BUTTON_Y, BUTTON_W, BUTTON_H)) { MediaPlayer.stopCurrent(); updatePlayDisplay(); return true; }
        if (in(x, y, volumeX, VOLUME_BAR_Y, VOLUME_BAR_W, VOLUME_BAR_H)) { draggingVolume = true; grab = x - volumeX; return true; }
        if (in(x, y, SCROLL_X, scrollY, SCROLL_W, SCROLL_H)) { draggingScroll = true; grab = y - scrollY; return true; }
        var media = rowAt(x, y);
        if (media != null) {
            float top = rowY(installed.indexOf(media));
            if (media.external() && in(x - AREA_X, y - top, EDIT_X, EDIT_NAME_Y, EDIT_SIZE, EDIT_SIZE)) { startEdit(media, true); return true; }
            if (media.external() && in(x - AREA_X, y - top, EDIT_X, EDIT_DESC_Y, EDIT_SIZE, EDIT_SIZE)) { startEdit(media, false); return true; }
            MediaPlayer.play(media); updatePlayDisplay();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    /**
     * 原作のpopボタン: 再生中なら一時停止、一時停止中なら再開、そうでなければ最後に再生したメディア、
     * それも無ければ一覧の先頭を再生する。
     */
    public void playOrPause() {
        var playing = MediaPlayer.currentPlaying();
        if (playing.isPresent()) {
            if (playing.get().paused()) MediaPlayer.continueCurrent(); else MediaPlayer.pauseCurrent();
        } else if (MediaPlayer.lastPlayed().isPresent()) {
            MediaPlayer.play(MediaPlayer.lastPlayed().get());
        } else if (!installed.isEmpty()) {
            MediaPlayer.play(installed.get(0));
        }
        updatePlayDisplay();
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingVolume) {
            volumeX = Mth.clamp(lx(mx) - grab, VOLUME_LOWER, VOLUME_UPPER);
            MediaPlayer.setVolume((volumeX - VOLUME_LOWER) / (VOLUME_UPPER - VOLUME_LOWER));
            return true;
        }
        if (draggingScroll) {
            scrollY = Mth.clamp(ly(my) - grab, SCROLL_LOWER, SCROLL_UPPER);
            // 原作: list.setProgress((int) (bar progress * max progress))。
            scroll = (int) ((scrollY - SCROLL_LOWER) / (SCROLL_UPPER - SCROLL_LOWER) * maxProgress());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (draggingVolume) MediaSettings.save();
        draggingVolume = draggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    private void startEdit(Media media, boolean name) {
        editing = media; editingName = name;
        buffer = new TextEdit(name ? media.name() : media.desc(), 64);
    }
    private void confirmEdit() {
        var media = editing; editing = null;
        if (media == null) return;
        if (editingName) MediaSettings.setName(media.id(), buffer.text()); else MediaSettings.setDesc(media.id(), buffer.text());
    }
    @Override public boolean charTyped(char c, int modifiers) {
        if (editing != null) { buffer.type(c); return true; }
        return super.charTyped(c, modifiers);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (editing != null) {
            if (buffer.key(key)) return true;
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirmEdit();
            // 原作wrapEditはEnterか他の場所のクリックでだけ確定する。Escapeは画面を閉じ、入力は失われる。
            else if (key == GLFW.GLFW_KEY_ESCAPE) { editing = null; return super.keyPressed(key, scan, modifiers); }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void removed() {
        // 原作MediaGuiは、閉じるときに確定していない入力を残さない。
        editing = null;
        if (draggingVolume) MediaSettings.save();
        super.removed();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        if (Util.getMillis() / 1000.0 - lastTest > .5) updatePlayDisplay();
        float x = lx(mx), y = ly(my);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0, y0, 0); pose.scale(SCALE, SCALE, 1);
        DeveloperScreen.quad(pose, BACK, 0, 0, WIDTH, HEIGHT, 1, 1, 1, 1);
        text(g, playTime, 403.7037f, 126.76981f, 200, 30, 30, 1, 2, 0xddffffff);
        // 行。
        for (int row = 0; row < PAGE && scroll + row < installed.size() + missing.size(); row++) {
            int index = scroll + row;
            if (index < installed.size()) drawRow(g, installed.get(index), AREA_Y + row * ROW_H, x, y);
            else drawMissing(g, missing.get(index - installed.size()), AREA_Y + row * ROW_H);
        }
        // 何も無い場合: 空の一覧を故障と誤解されないよう、曲を置く場所を示す。
        if (installed.isEmpty() && missing.isEmpty()) {
            text(g, net.minecraft.network.chat.Component.translatable("academy.media.empty").getString(), AREA_X + 10, AREA_Y + 20, ROW_W - 20, 30, 27, 0, 0, 0xbbffffff);
            text(g, net.minecraft.network.chat.Component.translatable("academy.media.missing_hint", MediaLibrary.HOW_TO).getString(), AREA_X + 10, AREA_Y + 60, ROW_W - 20, 30, 24, 0, 0, 0x99ffffff);
        }
        DeveloperScreen.rect(pose, SCROLL_X, scrollY, SCROLL_W, SCROLL_H, 1, 1, 1, 130 / 255f);
        DeveloperScreen.rect(pose, 52, 120, 554 * Mth.clamp(progress, 0, 1), 6, 1, 1, 1, 1);
        // タイトルは右揃え: 幅275.58、右端から50内側。
        text(g, title, WIDTH - 275.5787f - 50, 77.81148f, 275.5787f, 30, 36, 1, 2, 0x99ffffff);
        button(pose, STOP, STOP_X, x, y);
        button(pose, showPause ? PAUSE : PLAY, POP_X, x, y);
        DeveloperScreen.quad(pose, VOLUME, VOLUME_X, VOLUME_Y, 128, 32, 1, 1, 1, 1);
        float barAlpha = (in(x, y, volumeX, VOLUME_BAR_Y, VOLUME_BAR_W, VOLUME_BAR_H) ? 204 : 153) / 255f;
        DeveloperScreen.rect(pose, volumeX, VOLUME_BAR_Y, VOLUME_BAR_W, VOLUME_BAR_H, 1, 1, 1, barAlpha);
        pose.popPose();
        super.render(g, mx, my, partial);
    }
    /** 原作のTint: アルファ200、マウスの下では255。 */
    private void button(com.mojang.blaze3d.vertex.PoseStack pose, ResourceLocation texture, float bx, float x, float y) {
        float a = (in(x, y, bx, BUTTON_Y, BUTTON_W, BUTTON_H) ? 255 : 200) / 255f;
        DeveloperScreen.quad(pose, texture, bx, BUTTON_Y, BUTTON_W, BUTTON_H, 1, 1, 1, a);
    }
    /** 原作のt_one。ElementListが領域の左端に縦へ並べる。 */
    private void drawRow(GuiGraphics g, Media media, float top, float x, float y) {
        var pose = g.pose();
        boolean hover = in(x, y, AREA_X, top, ROW_W, ROW_H);
        DeveloperScreen.rect(pose, AREA_X, top, ROW_W, ROW_H, 1, 1, 1, (hover ? 60 : 20) / 255f);
        DeveloperScreen.quad(pose, media.cover(), AREA_X + 4, top + 5, 50, 50, 1, 1, 1, 1);
        boolean nameEdit = editing == media && editingName, descEdit = editing == media && !editingName;
        // 入力中のボックスには影を付ける（原作は上にアルファ0.2のDrawTexture）。
        if (nameEdit) DeveloperScreen.rect(pose, AREA_X + 65, top + 1, 300, 30, .4f, .4f, .4f, .2f);
        if (descEdit) DeveloperScreen.rect(pose, AREA_X + 66.125f, top + 29, 300, 23, .4f, .4f, .4f, .2f);
        text(g, nameEdit ? buffer.shown(false) : media.name(), AREA_X + 65, top + 1, 300, 30, 35, 0, 1, 0xbbffffff);
        text(g, descEdit ? buffer.shown(false) : media.desc(), AREA_X + 66.125f, top + 29, 300, 23, 27, 0, 0, 0xeeffffff);
        // 時間: 幅70、行の右端から6内側に右揃え、高さは中央。
        text(g, media.displayLength(), AREA_X + ROW_W - 70 - 6, top + (ROW_H - 30) / 2, 70, 30, 28, 2, 0, 0xb9ffffff);
        if (media.external()) {
            for (float ey : new float[]{EDIT_NAME_Y, EDIT_DESC_Y}) {
                float a = (in(x - AREA_X, y - top, EDIT_X, ey, EDIT_SIZE, EDIT_SIZE) ? 255 : 51) / 255f;
                DeveloperScreen.quad(pose, EDIT, AREA_X + EDIT_X, top + ey, EDIT_SIZE, EDIT_SIZE, 1, 1, 1, a);
            }
        }
    }
    /** 原作には無い表示: 取得済みだが音声がここに無い曲。灰色で、追加方法の案内を添える。 */
    private void drawMissing(GuiGraphics g, String id, float top) {
        var pose = g.pose();
        DeveloperScreen.rect(pose, AREA_X, top, ROW_W, ROW_H, 1, 1, 1, 10 / 255f);
        DeveloperScreen.quad(pose, MediaLibrary.NO_COVER, AREA_X + 4, top + 5, 50, 50, 1, 1, 1, .4f);
        text(g, net.minecraft.network.chat.Component.translatable(io.github.pinchan4273.reacademycraft.world.item.MediaItem.nameKey(id)).getString()
                + "  (" + net.minecraft.network.chat.Component.translatable("academy.media.missing").getString() + ")",
                AREA_X + 65, top + 1, 470, 30, 35, 0, 1, 0x88ffffff);
        text(g, net.minecraft.network.chat.Component.translatable("academy.media.missing_hint", MediaLibrary.HOW_TO).getString(),
                AREA_X + 66.125f, top + 29, 470, 23, 27, 0, 0, 0x99ffffff);
        text(g, "--:--", AREA_X + ROW_W - 70 - 6, top + (ROW_H - 30) / 2, 70, 30, 28, 2, 0, 0x66ffffff);
    }
    private void text(GuiGraphics g, String text, float x, float y, float w, float h, float size, int alignX, int alignY, int argb) {
        text(g, font, text, x, y, w, h, size, alignX, alignY, argb);
    }
    /**
     * 原作TextBox: ウィジェット単位の大きさで文字を描く。alignXは0が左、それ以外は右。alignYは0が上、1が中央、2が下。
     * ゲームのフォントは原作より幅が広いので、枠をはみ出す文字は切らずに縮小して収める。
     */
    static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String text, float x, float y, float w, float h,
                     float size, int alignX, int alignY, int argb) {
        float s = size / 9f;
        if (font.width(text) * s > w && font.width(text) > 0) s = w / font.width(text);
        float tw = font.width(text) * s, th = 9 * s;
        float dx = alignX == 0 ? 0 : w - tw;
        float dy = alignY == 0 ? 0 : alignY == 1 ? (h - th) / 2 : h - th;
        g.pose().pushPose();
        g.pose().translate(x + dx, y + dy, 0); g.pose().scale(s, s, 1);
        g.drawString(font, text, 0, 0, argb, false);
        g.pose().popPose();
    }
    @Override public boolean isPauseScreen() { return false; }

    /** テスト用の入口。 */
    public List<Media> installed() { return installed; }
    public List<String> missing() { return missing; }
    public boolean showsPause() { return showPause; }
    public String shownTitle() { return title; }
    public float volumeBarX() { return volumeX; }
    public int scroll() { return scroll; }
    public boolean editing() { return editing != null; }
}
