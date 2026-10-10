package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 原作CustomizeUI（guis/ui_edit.xml）。設定アプリの"Customize UI"から開く: 原作ACHudのすべてのHUDを、それぞれの位置に
 * プレビューとして表示し、その上の窓に一覧を出す。1つをクリックすると、そのプレビューを枠で囲み、横にXとYの入力欄を開く。
 * 値はEnterで確定し、欄が灰色に戻る。-512〜512の数でなければ受け付けず、欄が赤になる。
 * 位置はHudLayoutのもので、各HUDがそれを読む。原作の設定と同じく、すぐに保存する。
 */
public final class CustomizeUI extends Screen {
    static final ResourceLocation WINDOW = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/window_ui_resize.png");
    static final ResourceLocation CPBAR = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/edit_preview/cpbar.png");
    static final ResourceLocation KEY_HINT = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/edit_preview/key_hint.png");
    // ui_edit.xml: mainは(94, 104)、半分の倍率で144×203。見出しは高さ28で8下、一覧は36下。どちらも幅128で中央。
    // 各要素は高さ24。入力欄は90×16で、要素の5右。
    static final float MAIN_X = 94, MAIN_Y = 104, MAIN_SCALE = .5f, MAIN_W = 144, MAIN_H = 203;
    static final float LIST_X = 8, LIST_Y = 36, ELEM_W = 128, ELEM_H = 24;
    static final float EDIT_W = 90, EDIT_H = 16;

    private final HudLayout.Node[] nodes = HudLayout.Node.values();
    @Nullable private HudLayout.Node focus;
    private float editX, editY;
    private final String[] text = {"", ""};
    private final boolean[] bad = new boolean[2];
    private int field = -1;

    public CustomizeUI() { super(Component.translatable("academy.settings.prop.edit_ui")); }

    private static float elemX() { return MAIN_X + LIST_X * MAIN_SCALE; }
    private static float elemY(int i) { return MAIN_Y + (LIST_Y + i * ELEM_H) * MAIN_SCALE; }
    private static boolean in(double x, double y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        // 原作のプレビューは窓より先にguiへ加えるので、窓の下になる。
        for (var node : nodes) preview(g, node);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(MAIN_X, MAIN_Y, 0);
        pose.scale(MAIN_SCALE, MAIN_SCALE, 1);
        DeveloperScreen.quad(pose, WINDOW, 0, 0, MAIN_W, MAIN_H, 1, 1, 1, 1);
        TerminalHud.text(g, font, Component.translatable("academy.uiedit.elements").getString(), LIST_X, 8, ELEM_W, 28, 18, 0, 1, 187);
        for (int i = 0; i < nodes.length; i++) {
            float y = LIST_Y + i * ELEM_H;
            boolean hover = in(mx, my, elemX(), elemY(i), ELEM_W * MAIN_SCALE, ELEM_H * MAIN_SCALE);
            // Tint: 通常は白の25、マウスの下では127。
            DeveloperScreen.rect(pose, LIST_X, y, ELEM_W, ELEM_H, 1, 1, 1, (hover ? 127 : 25) / 255f);
            TerminalHud.text(g, font, Component.translatable(nodes[i].nameKey()).getString(), LIST_X, y, ELEM_W, ELEM_H, 18, 0, 1, 255);
        }
        pose.popPose();
        if (focus != null) {
            // 入力欄: 187の暗さに白い枠。XとYの欄は灰色、受け付けなかったときは赤。
            DeveloperScreen.rect(pose, editX, editY, EDIT_W, EDIT_H, 34 / 255f, 34 / 255f, 34 / 255f, 187 / 255f);
            outline(g, editX, editY, EDIT_W, EDIT_H);
            TerminalHud.text(g, font, "X", editX + 2, editY + 3, 10, 10, 10, 0, 1, 255);
            TerminalHud.text(g, font, "Y", editX + 46, editY + 3, 10, 10, 10, 0, 1, 255);
            for (int f = 0; f < 2; f++) {
                float fx = editX + (f == 0 ? 10 : 53), fy = editY + 3;
                float c = bad[f] ? 0 : 51 / 255f;
                DeveloperScreen.rect(pose, fx, fy, 34, 10, bad[f] ? 0xbb / 255f : c, c, c, 1);
                String shown = text[f] + (field == f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
                TerminalHud.text(g, font, shown, fx, fy, 34, 10, 10, 0, 1, 255);
            }
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** 各nodeのプレビューの部品を、nodeの位置に置く。編集中のものは枠で囲む。 */
    private void preview(GuiGraphics g, HudLayout.Node node) {
        var pose = g.pose();
        // 原作KeyHintUIのプレビューは、案内の倍率の2倍で140×210。
        float scale = node == HudLayout.Node.KEYHINT ? node.scale * 2 : node.scale;
        float w = node.width * scale, h = node.height * scale;
        float x = node == HudLayout.Node.KEYHINT ? width - w + HudLayout.x(node) : HudLayout.left(node, width);
        float y = node == HudLayout.Node.KEYHINT ? (height - h) / 2 + HudLayout.y(node) : HudLayout.top(node, height);
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        switch (node) {
            case CPBAR -> DeveloperScreen.quad(pose, CPBAR, 0, 0, node.width, node.height, 1, 1, 1, 1);
            case KEYHINT -> DeveloperScreen.quad(pose, KEY_HINT, 0, 0, 128, 193, 1, 1, 1, 1);
            case NOTIFICATION -> TutorialNotifyHud.preview(g);
            case MEDIA -> MediaHud.widget(g, "Only My Railgun", .5f, "04:30");
        }
        pose.popPose();
        if (node == focus) outline(g, x, y, w, h);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private static void outline(GuiGraphics g, float x, float y, float w, float h) {
        var pose = g.pose();
        DeveloperScreen.rect(pose, x, y, w, 1, 1, 1, 1, 1);
        DeveloperScreen.rect(pose, x, y + h - 1, w, 1, 1, 1, 1, 1);
        DeveloperScreen.rect(pose, x, y, 1, h, 1, 1, 1, 1);
        DeveloperScreen.rect(pose, x + w - 1, y, 1, h, 1, 1, 1, 1);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (focus != null) {
            for (int f = 0; f < 2; f++) {
                if (in(mx, my, editX + (f == 0 ? 10 : 53), editY + 3, 34, 10)) { field = f; return true; }
            }
        }
        for (int i = 0; i < nodes.length; i++) {
            if (in(mx, my, elemX(), elemY(i), ELEM_W * MAIN_SCALE, ELEM_H * MAIN_SCALE)) { select(nodes[i]); return true; }
        }
        field = -1;
        return super.mouseClicked(mx, my, button);
    }
    /** 原作changeEditFocus: nodeの要素の横に入力欄を開き、その位置を入れる。 */
    public void select(HudLayout.Node node) {
        if (node == focus) return;
        focus = node;
        int i = Arrays.asList(nodes).indexOf(node);
        editX = elemX() + ELEM_W * MAIN_SCALE + 5;
        editY = elemY(i) + ELEM_H * MAIN_SCALE / 2 - EDIT_H / 2;
        text[0] = String.valueOf((double) HudLayout.x(node));
        text[1] = String.valueOf((double) HudLayout.y(node));
        bad[0] = bad[1] = false;
        field = -1;
    }
    @Override public boolean charTyped(char c, int modifiers) {
        if (field < 0) return super.charTyped(c, modifiers);
        if (c >= ' ' && c != 127 && text[field].length() < 16) text[field] += c;
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (field >= 0) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !text[field].isEmpty()) { text[field] = text[field].substring(0, text[field].length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirm(field); return true; }
            if (key != GLFW.GLFW_KEY_ESCAPE) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    /** 原作wrapEditのConfirmInputEvent: 範囲内の数ならnodeを動かし、それ以外は受け付けない。 */
    public boolean confirm(int f) {
        if (focus == null) return false;
        double value;
        try {
            value = Double.parseDouble(text[f]);
            if (!HudLayout.valid(value)) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            bad[f] = true;
            return false;
        }
        bad[f] = false;
        if (f == 0) HudLayout.set(focus, (float) value, HudLayout.y(focus));
        else HudLayout.set(focus, HudLayout.x(focus), (float) value);
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }

    // テスト用の入口。
    @Nullable public HudLayout.Node focus() { return focus; }
    public void type(int f, String value) { field = f; text[f] = value; }
    public boolean refused(int f) { return bad[f]; }
}
