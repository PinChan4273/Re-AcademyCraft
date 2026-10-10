package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.terminal.TerminalApp;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * 原作TerminalUI（AcademyCraft commit 7b1401c、guis/terminal.xml）: データ端末は画面ではなく、ゲームの上に独自の
 * 50度の透視で描くパネルで、少しプレイヤーの方を向き、カーソルに合わせて揺れる。開いている間、マウスは視点ではなく
 * そのカーソルを動かし、移動キーは引き続きプレイヤーを動かし、左ボタンは攻撃ではなくカーソルの下のアプリを起動する。
 * アプリは1行3つ、1ページ3行。カーソルを上下の端へ押し付けると1行スクロールする。各アプリは前のものの0.1秒後に
 * フェードインし、カーソルの下のものは明るくなり、そうなったときに原作のterminal.selectを鳴らす。端末キーで開閉する。
 *
 * 原作はゲームのマウス処理を自前のものへ差し替える。ここでは何かが通知される時点でゲームが既にプレイヤーを回しているので、
 * 各フレームの最初にその回転を測って取り消し、原作が読むマウス単位でカーソルへ与える。
 *
 * 独自の画面を開くアプリは、端末を下に開いたままにする。何かの画面が出ている間、端末は描かれず操作もされない。
 * 原作の独自フォントは使わず、ゲームのフォントを原作の大きさで描く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TerminalHud {
    public static final int MAX_MX = 605, MAX_MY = 740;
    static final double BALANCE_SPEED = 3000, SENSITIVITY = .7, SCALE = 1.0 / 310;
    static final float START_X = 65, START_Y = 155, STEP = 180, APP_SIZE = 151, WIDTH = 640, HEIGHT = 785;
    private static final ResourceLocation BACK = tex("back"), APP_BACK = tex("app_back"), APP_BACK_HDR = tex("app_back_highlight"),
            CURSOR = tex("cursor"), LOGO = tex("logo"), ARROW_UP = tex("arrow_up"), ARROW_DOWN = tex("arrow_down");
    private static final SoundEvent SELECT = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "terminal.select"));
    private static boolean open;
    private static float mouseX, mouseY, buffX, buffY;
    private static double createTime, lastFrameTime;
    private static int selection, scroll;
    private static List<TerminalApp> apps = List.of();
    @Nullable private static TerminalApp lastSelected;
    private static float savedYaw, savedPitch;
    private static boolean savedLook;
    private static long frames;
    private TerminalHud() { }
    private static ResourceLocation tex(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/data_terminal/" + name + ".png"); }
    /** 原作App.getIcon: guis/apps/<name>/icon.png。 */
    public static ResourceLocation icon(TerminalApp app) {
        if (app.kind() == TerminalApp.Kind.TUTORIAL) return tutorialIcon;
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/apps/" + app.name() + "/icon.png");
    }
    /**
     * 原作AppTutorial.getIcon（遊び）: 5回に1回icon_0、10回に1回icon_1、それ以外はicon_2。
     * 原作は端末がウィジェットを作るときに選ぶので、ここでは端末を開いたときに選ぶ。
     */
    private static ResourceLocation tutorialIcon = tutorialIcon();
    private static ResourceLocation tutorialIcon() {
        float rand = (float) Math.random();
        int id = rand < .2f ? 0 : rand < .3f ? 1 : 2;
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/apps/tutorial/icon_" + id + ".png");
    }
    private static double seconds() { return Util.getMillis() / 1000.0; }

    public static boolean isOpen() { return open; }
    /** 描いたフレーム数（テスト用）。実際の見え方は画像で判定する。 */
    public static long frames() { return frames; }
    /** 原作のキー処理: 端末が無ければそう伝え、あれば端末を開くか閉じる。 */
    public static void toggle() {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!TerminalState.installed(client.player)) {
            // 原作TerminalUIのキー処理はチャットで伝える。
            client.player.displayClientMessage(Component.translatable("academy.terminal.none"), false);
            return;
        }
        if (open) close(); else open();
    }
    public static void open() {
        var client = Minecraft.getInstance();
        if (client.player == null || !TerminalState.installed(client.player)) return;
        open = true;
        buffX = buffY = mouseX = mouseY = 150;
        scroll = 0; lastSelected = null; savedLook = false;
        createTime = seconds(); lastFrameTime = 0;
        apps = TerminalState.available(client.player);
        tutorialIcon = tutorialIcon();
    }
    public static void close() { open = false; savedLook = false; }
    /** 原作TerminalUIは永続しないので、LambdaLib2のAuxGuiHandlerが切断時に破棄する: 次のワールドは閉じた状態で始まる。 */
    @SubscribeEvent public static void terminalLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { close(); }

    /** 原作の並び順のアプリ: インストール順で、プリインストールのものが先。 */
    public static List<TerminalApp> apps() { return apps; }
    private static int maxScroll() {
        int rows = apps.size() % 3 == 0 ? apps.size() / 3 : apps.size() / 3 + 1;
        return Math.max(0, rows - 3);
    }
    /**
     * カーソルの下のアプリ（あれば）。原作はscroll + selectionで引くため、スクロール1回につき2行ずれる。
     * ここではカーソルの下に描いたセルを返す。
     */
    @Nullable public static TerminalApp selected() {
        int lookup = scroll * 3 + selection;
        return lookup < apps.size() ? apps.get(lookup) : null;
    }
    /** テスト用の入口: カーソルと、遅れて追う写しを、このアプリのセルの中央へ置く。 */
    public static void pointAt(int index) {
        int order = index - scroll * 3;
        mouseX = buffX = (order % 3 + .5f) * MAX_MX / 3;
        mouseY = buffY = (order / 3 + .5f) * MAX_MY / 3;
        selection = order;
    }
    public static float cursorX() { return mouseX; }
    public static float cursorY() { return mouseY; }

    /** 原作のLeftClickHandler: カーソルの下のアプリを起動する。 */
    public static void click() {
        var app = selected();
        if (app != null) start(app);
    }
    /** 原作App.createEnvironment().onStart()に相当: 各アプリがここで開くもの。 */
    public static void start(TerminalApp app) {
        var client = Minecraft.getInstance();
        switch (app.kind()) {
            case SKILL_TREE -> io.github.pinchan4273.reacademycraft.network.AcademyNetwork.CHANNEL.sendToServer(new io.github.pinchan4273.reacademycraft.network.TerminalOpenApp(app.name()));
            case SETTINGS -> client.setScreen(new TerminalSettingsScreen(null));
            case MEDIA_PLAYER -> client.setScreen(new MediaScreen());
            case ABOUT -> client.setScreen(new AboutScreen());
            case TUTORIAL -> client.setScreen(new TutorialScreen());
            // 原作passOn: 送信機の窓が端末の窓に取って代わる。
            case FREQ_TRANSMITTER -> { close(); FreqTransmitterHud.start(); }
        }
    }

    /** このフレームでマウスがプレイヤーを回した。その回転を取り消し、カーソルへ与える。 */
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance(); var player = client.player;
        if (!open || player == null || client.screen != null) { savedLook = false; return; }
        if (!TerminalState.installed(player)) { close(); return; }
        if (savedLook) {
            float yaw = player.getYRot() - savedYaw, pitch = player.getXRot() - savedPitch;
            if (yaw != 0 || pitch != 0) {
                player.setYRot(savedYaw); player.setXRot(savedPitch);
                player.yRotO -= yaw; player.xRotO -= pitch;
                // MouseHandler.turnPlayerはdelta * f^3 * 8 * 0.15だけ回す。f = sensitivity * 0.6 + 0.2。
                double f = client.options.sensitivity().get() * .6 + .2, per = f * f * f * 8 * .15;
                double invert = client.options.invertYMouse().get() ? -1 : 1;
                // 原作: mouseX += dx * 0.7、mouseY -= dy * 0.7（LWJGLのyは上向き）。
                mouseX += (float) (yaw / per * SENSITIVITY);
                mouseY += (float) (pitch * invert / per * SENSITIVITY);
            }
        }
        savedYaw = player.getYRot(); savedPitch = player.getXRot(); savedLook = true;
    }

    /** 原作は端末を開いている間、左ボタンを横取りする: 離したときにアプリを起動する。 */
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        var client = Minecraft.getInstance();
        if (!open || client.screen != null || client.player == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        event.setCanceled(true);
        if (event.getAction() == GLFW.GLFW_RELEASE) click();
    }

    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!open || client.player == null || client.screen != null || client.options.hideGui) return;
        apps = TerminalState.available(client.player);
        double time = seconds();
        if (lastFrameTime == 0) lastFrameTime = time;
        double dt = time - lastFrameTime;
        lastFrameTime = time;
        // 原作のフレーム更新: カーソルを枠内に留め、端でスクロールし、パネルの揺れが追う遅れた写しを毎秒3000で追いつかせる。
        mouseX = Mth.clamp(mouseX, 0, MAX_MX); mouseY = Mth.clamp(mouseY, 0, MAX_MY);
        selection = (int) ((mouseY - .01) / MAX_MY * 3) * 3 + (int) ((mouseX - .01) / MAX_MX * 3);
        if (mouseY == 0) { mouseY = 1; if (scroll > 0) scroll--; }
        if (mouseY == MAX_MY) { mouseY -= 1; if (scroll < maxScroll()) scroll++; }
        scroll = Math.min(scroll, maxScroll());
        buffX = balance(dt, buffX, mouseX); buffY = balance(dt, buffY, mouseY);

        var window = client.getWindow();
        float aspect = (float) window.getWidth() / window.getHeight();
        var savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float) Math.toRadians(50), aspect, 1, 100), VertexSorting.DISTANCE_TO_ORIGIN);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose(); modelView.setIdentity(); RenderSystem.applyModelViewMatrix();
        var g = new GuiGraphics(client, client.renderBuffers().bufferSource());
        var pose = g.pose();
        pose.translate(.35 * aspect, 1.2, -4);
        pose.translate(1, -1.8, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-1.6f));
        pose.mulPose(Axis.YP.rotationDegrees((float) (-18 - 4 * (buffX / MAX_MX - .5) + Math.sin(time / 1000.0))));
        pose.mulPose(Axis.XP.rotationDegrees((float) (7 + 4 * (buffY / MAX_MY - .5))));
        pose.translate(-1, 1.8, 0);
        pose.scale((float) SCALE, (float) -SCALE, (float) SCALE);
        RenderSystem.disableDepthTest(); RenderSystem.disableCull();
        try {
            drawPanel(g, client, time);
        } finally {
            g.flush();
            RenderSystem.enableCull(); RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            modelView.popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
        frames++;
    }
    private static float balance(double dt, float from, float to) {
        double d = to - from;
        return (float) (from + Math.min(BALANCE_SPEED * dt, Math.abs(d)) * Math.signum(d));
    }

    private static void drawPanel(GuiGraphics g, Minecraft client, double time) {
        var pose = g.pose(); var font = client.font;
        DeveloperScreen.quad(pose, BACK, 0, 0, WIDTH, HEIGHT, 1, 1, 1, 1);
        // text_appcount: "n Apps, hh:mm"（ワールドの時刻）、右端から40内側に右揃え。
        int day = (int) (client.level.getDayTime() % 24000);
        String clock = wrap(day / 1000) + ":" + wrap(day % 1000 * 60 / 1000);
        panelText(g, font, Component.translatable("academy.terminal.appcount", apps.size()).getString() + ", " + clock,
                WIDTH - 300 - 40, 84, 300, 30, 30, 2, 1, 153);
        DeveloperScreen.quad(pose, LOGO, 40, 50, 50, 50, 1, 1, 1, 1);
        panelText(g, font, client.player.getName().getString(), WIDTH - 200 - 40, 41.666668f, 200, 30, 40, 2, 0, 204);
        panelText(g, font, "DATA", 98, 42, 100, 30, 40, 0, 1, 170);
        panelText(g, font, "TERMINAL", 98, 74, 160, 30, 40, 0, 1, 170);
        // 矢印（0.8倍、中央揃え）: スクロールできる方向があるときだけ明るい。
        if (scroll > 0) DeveloperScreen.quad(pose, ARROW_UP, (WIDTH - 80) / 2, 133, 80, 20, 1, 1, 1, 1);
        if (scroll < maxScroll()) DeveloperScreen.quad(pose, ARROW_DOWN, (WIDTH - 80) / 2, HEIGHT - 20 - 40, 80, 20, 1, 1, 1, 1);
        var chosen = selected();
        if (chosen != null && chosen != lastSelected)
            client.getSoundManager().play(SimpleSoundInstance.forUI(SELECT, 1, .2f));
        lastSelected = chosen;
        double life = time - createTime;
        // 選択中のアプリは他より手前に出す（原作のzLevel 40と10）: 最後に描く。
        for (int pass = 0; pass < 2; pass++)
            for (int i = scroll * 3; i < scroll * 3 + 9 && i < apps.size(); i++) {
                var app = apps.get(i);
                boolean isSelected = app == chosen;
                if (isSelected != (pass == 1)) continue;
                int order = i - scroll * 3;
                drawApp(g, font, app, i, START_X + STEP * (order % 3), START_Y + STEP * (order / 3), isSelected, life);
            }
        // カーソル: 0.4で加算し、アプリの上では少し大きい。
        double size = (chosen == null ? 1 : 1.3) * (20 + Math.sin(time / 300.0) * 2);
        additive(pose, CURSOR, (float) (buffX - size / 2), (float) (buffY + 120 - size / 2), (float) size, .4f);
    }
    private static void drawApp(GuiGraphics g, net.minecraft.client.gui.Font font, TerminalApp app, int id, float x, float y, boolean selected, double life) {
        var pose = g.pose();
        float alpha = Mth.clamp((float) (life - (id + 1) * .1f) / .4f, 0, 1);
        DeveloperScreen.quad(pose, selected ? APP_BACK_HDR : APP_BACK, x, y, APP_SIZE, APP_SIZE, 1, 1, 1, alpha);
        iconsDepthTested |= org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        DeveloperScreen.quad(pose, icon(app), x + 9, y + 32, 110, 110, 1, 1, 1, (selected ? .8f : .6f) * alpha);
        float textAlpha = selected ? .1f + .72f * alpha : .1f + .1f * alpha;
        panelText(g, font, Component.translatable(app.titleKey()).getString(), x, y + 148, APP_SIZE, 21, 32, 1, 1, (int) (textAlpha * 255));
    }
    private static String wrap(int x) { return x < 10 ? "0" + x : Integer.toString(x); }
    private static boolean iconsDepthTested;
    /** テスト用の入口: 前回の呼び出し以降、深度テストを有効にしたままアプリのアイコンを描いたか。 */
    public static boolean takeIconsDepthTested() { boolean seen = iconsDepthTested; iconsDepthTested = false; return seen; }
    /**
     * 傾いたパネル上のtext()。GuiGraphicsは文字列ごとにflushし、flushの最後に深度テストを有効にする。そのため最初の
     * 文字列より後に描いたもの（背景、アイコン、カーソル。すべてパネルの同じ平面上）が下の背景と深度比較され、パネルが
     * 傾くとちらついた。パネルは深度テスト無しで順に描くので、各文字列の後で元に戻す。
     */
    private static void panelText(GuiGraphics g, net.minecraft.client.gui.Font font, String text, float x, float y, float w, float h,
                                  float size, int alignX, int alignY, int alpha) {
        text(g, font, text, x, y, w, h, size, alignX, alignY, alpha);
        RenderSystem.disableDepthTest();
    }
    /**
     * 原作TextBox: ウィジェット単位の大きさで描く。alignXは0が左、1が中央、2が右。alignYは0が上、1が中央、2が下。
     * 枠より幅の広い文字は縮小して収める。
     */
    static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String text, float x, float y, float w, float h,
                     float size, int alignX, int alignY, int alpha) {
        float s = size / 9f;
        if (font.width(text) * s > w && font.width(text) > 0) s = w / font.width(text);
        float tw = font.width(text) * s, th = 9 * s;
        float dx = alignX == 0 ? 0 : alignX == 1 ? (w - tw) / 2 : w - tw;
        float dy = alignY == 0 ? 0 : alignY == 1 ? (h - th) / 2 : h - th;
        g.pose().pushPose();
        g.pose().translate(x + dx, y + dy, 0); g.pose().scale(s, s, 1);
        g.drawString(font, text, 0, 0, Math.max(4, Math.min(255, alpha)) << 24 | 0xffffff, false);
        g.pose().popPose();
    }
    /** 原作がカーソルを描くのと同じく、加算ブレンド（GL_SRC_ALPHA, GL_ONE）で描く四角形。 */
    private static void additive(PoseStack pose, ResourceLocation texture, float x, float y, float size, float a) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(m, x, y, 0).uv(0, 0).color(1, 1, 1, a).endVertex();
        buffer.vertex(m, x, y + size, 0).uv(0, 1).color(1, 1, 1, a).endVertex();
        buffer.vertex(m, x + size, y + size, 0).uv(1, 1).color(1, 1, 1, a).endVertex();
        buffer.vertex(m, x + size, y, 0).uv(1, 0).color(1, 1, 1, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.defaultBlendFunc();
    }
}
