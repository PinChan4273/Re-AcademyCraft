package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.LocationList;
import io.github.pinchan4273.reacademycraft.network.LocationRequest;
import io.github.pinchan4273.reacademycraft.skill.LocationTeleport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 原作LocationTeleport.Gui（guis/loctele_new.xml。画面中央付近に0.24倍で描く）:
 * - "menu": (20, -179)から442x530。暗い(53, 53, 53, 128)に(122, 146, 156, 158)の枠。高さが0.4秒かけて開く。
 *   その中18下に"list"。行は高さ80、間隔2。
 * - 地点ごとに1行: 名前（43、#c1cfd5。使えないときは灰色#a2a2a2）を(45, 16)に、(335, 30)と(375, 30)に
 *   icon_location_onとicon_clear（幅33、#c1cfd5、0.7。マウスの下では1）。使えないときはテレポートボタンが無い。
 *   背景は白0.1、マウスの下では0.4。行は0.06秒ずつずれて現れる: 背景は0.2秒、ボタンは0.03秒と0.05秒後から0.1秒、
 *   名前は0.1秒後から0.1秒。
 * - 最後の行: クリックするまで"Add..."（#a4d4e9、0.4。入力中は0.8）。クリックで16文字まで入力でき、
 *   Enterか(376, 30)のcheck.pngで術者の立っている場所を登録する。
 * - "info": 中央から右へ23（右揃え）、マウスの下の行の高さ。その行の情報を右揃えで並べる（高さ40、間隔42、余白20）:
 *   ディメンション、座標、消費するCP、使えない理由。最後の行では術者のディメンションと座標。
 * リスト自体はサーバーにあり、この画面はサーバーが最後に送った写しを表示する。入りきらない行はマウスホイールでスクロールする。
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "academy", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class LocationTeleportScreen extends Screen {
    static final float SCALE = .24f, MENU_X = 20, MENU_Y = -179.17f, MENU_W = 442, MENU_H = 530, LIST_Y = 18, ROW_H = 80, SPACING = 2,
            TEXT_X = 45.28f, TEXT_Y = 16.13f, BUTTON = 33.33f, TELEPORT_X = 335, REMOVE_X = 375, CONFIRM_X = 375.83f, BUTTON_Y = 29.92f,
            INFO_RIGHT = -23.33f;
    static final double STEP = .06;
    private static final ResourceLocation TELEPORT = icon("icons/icon_location_on"), REMOVE = icon("icons/icon_clear"), CHECK = icon("check");
    private static List<LocationTeleport.Location> cached = List.of();
    private double opened = Util.getMillis() / 1000.0, rowsBuilt = opened;
    private int first;
    @Nullable private TextEdit typing;
    private boolean asked;
    private float mouseX, mouseY;

    public LocationTeleportScreen() { super(Component.translatable("academy.skill.location_teleport")); }
    private static ResourceLocation icon(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png"); }

    /** あらゆる要求へのサーバーの返答。開いているリストはこれから作り直す（そして再びフェードインする）。 */
    public static void receive(LocationList packet) {
        cached = packet.locations();
        if (Minecraft.getInstance().screen instanceof LocationTeleportScreen screen) screen.rowsBuilt = Util.getMillis() / 1000.0;
    }
    public static List<LocationTeleport.Location> locations() { return cached; }
    /**
     * 写しは届いた接続のもの: 別のワールドやサーバーには別のリストがあるので、次のリストは改めて問い合わせる。
     * こちらを表示しながら別のリストの番号で操作することがないようにする。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void loggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { cached = List.of(); }

    @Override protected void init() {
        // 開くたびにサーバーへ改めて問い合わせ、その間は古い写しを表示しない。再接続前の写しや、ログアウト時の消去の後に
        // 届いた返答の番号を使わせないため。
        if (!asked) { asked = true; cached = List.of(); AcademyNetwork.CHANNEL.sendToServer(LocationRequest.query()); }
        first = Math.max(0, Math.min(first, cached.size() - visibleRows()));
    }
    private static double now() { return Util.getMillis() / 1000.0; }
    /** 原作Gui.Blend: offsetまでは0、そこからlengthかけて1まで上がる。 */
    private float blend(double base, double offset, double length) {
        return (float) Math.max(0, Math.min(1, (now() - base - offset) / length));
    }
    private float ox() { return width / 2f; }
    private float oy() { return height / 2f; }
    private float menuX() { return ox() + MENU_X * SCALE; }
    private float menuY() { return oy() + MENU_Y * SCALE; }
    private int visibleRows() { return Math.max(1, (int) ((MENU_H - LIST_Y) / (ROW_H + SPACING)) - 1); }
    /** 画面上の行: 表示中の地点、その後に追加用の行。 */
    private int shownLocations() { return Math.max(0, Math.min(cached.size() - first, visibleRows())); }
    private float rowY(int shown) { return menuY() + (LIST_Y + shown * (ROW_H + SPACING)) * SCALE; }
    private static boolean in(double x, double y, float bx, float by, float w, float h) { return x >= bx && x < bx + w && y >= by && y < by + h; }
    private boolean usable(LocationTeleport.Location location) {
        var player = Minecraft.getInstance().player;
        var data = player == null ? null : player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        return player != null && data != null && LocationTeleport.refusal(player, data, location) == null;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        mouseX = mx; mouseY = my;
        var pose = g.pose();
        // メニューは0.4秒かけて開く。
        float open = blend(opened, 0, .4);
        float mx0 = menuX(), my0 = menuY(), mw = MENU_W * SCALE, mh = MENU_H * SCALE * open;
        box(g, mx0, my0, mw, mh);
        int hovered = -2;
        g.enableScissor((int) mx0, (int) my0, (int) Math.ceil(mx0 + mw), (int) Math.ceil(my0 + mh));
        int shown = shownLocations();
        for (int i = 0; i < shown; i++) {
            var location = cached.get(first + i);
            float y = rowY(i), rh = ROW_H * SCALE;
            boolean hover = in(mx, my, mx0, y, mw, rh);
            if (hover) hovered = first + i;
            background(g, mx0, y, mw, rh, blend(rowsBuilt, i * STEP, .2), hover);
            boolean ok = usable(location);
            text(g, location.name(), mx0 + TEXT_X * SCALE, y + TEXT_Y * SCALE, 43 * SCALE, ok ? 0xc1cfd5 : 0xa2a2a2, blend(rowsBuilt, i * STEP + .1, .1));
            if (ok) button(g, TELEPORT, mx0 + TELEPORT_X * SCALE, y + BUTTON_Y * SCALE, blend(rowsBuilt, i * STEP + .03, .1));
            button(g, REMOVE, mx0 + REMOVE_X * SCALE, y + BUTTON_Y * SCALE, blend(rowsBuilt, i * STEP + .05, .1));
        }
        // 追加用の行。
        float ay = rowY(shown), rh = ROW_H * SCALE;
        boolean addHover = in(mx, my, mx0, ay, mw, rh);
        if (addHover) hovered = -1;
        float addBlend = blend(rowsBuilt, shown * STEP, .2);
        background(g, mx0, ay, mw, rh, addBlend, addHover);
        String shownText = typing == null ? Component.translatable("academy.loctele.add").getString() : typing.shown(false);
        text(g, shownText, mx0 + TEXT_X * SCALE, ay + 19.7f * SCALE, 43 * SCALE, 0xa4d4e9, addBlend * (typing == null ? .4f : .8f));
        button(g, CHECK, mx0 + CONFIRM_X * SCALE, ay + BUTTON_Y * SCALE, blend(rowsBuilt, shown * STEP, .1));
        g.disableScissor();
        // マウスの下の行の情報。
        if (hovered != -2) info(g, hovered == -1 ? ay : rowY(hovered - first), lines(hovered));
    }
    private List<String> lines(int index) {
        var player = Minecraft.getInstance().player;
        var lines = new ArrayList<String>();
        if (player == null) return lines;
        if (index < 0) {
            lines.add(dimension(player.level().dimension().location()));
            lines.add(String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", player.getX(), player.getY(), player.getZ()));
            return lines;
        }
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        var location = cached.get(index);
        lines.add(dimension(location.dimension().location()));
        lines.add(String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", location.x(), location.y(), location.z()));
        if (data != null) {
            lines.add(String.format(Locale.ROOT, "%.0f CP", LocationTeleport.cpCost(player, data, location)));
            var refusal = LocationTeleport.refusal(player, data, location);
            if (refusal != null) lines.add(Component.translatable(refusal).getString());
        }
        return lines;
    }
    /** 原作の"DimensionName (#id)"。移植版のディメンションはキーで呼ぶ。 */
    private static String dimension(ResourceLocation id) { return id.toString(); }
    private void info(GuiGraphics g, float y, List<String> lines) {
        if (lines.isEmpty()) return;
        float size = 40 * SCALE, s = size / 9f;
        float textW = 0;
        for (var line : lines) textW = Math.max(textW, font.width(line) * s);
        float w = textW + 40 * SCALE, h = lines.size() * 42 * SCALE + 40 * SCALE;
        float right = ox() + INFO_RIGHT * SCALE, x = right - w;
        box(g, x, y, w, h);
        for (int i = 0; i < lines.size(); i++)
            text(g, lines.get(i), right - 20 * SCALE - font.width(lines.get(i)) * s, y + (20 + 42 * i) * SCALE, size, 0xc1cfd5, 1);
    }
    /** 原作のDrawTexture(53, 53, 53, 128)の上にOutline(122, 146, 156, 158)。 */
    private static void box(GuiGraphics g, float x, float y, float w, float h) {
        if (h <= 0) return;
        DeveloperScreen.rect(g.pose(), x, y, w, h, 53 / 255f, 53 / 255f, 53 / 255f, 128 / 255f);
        float r = 122 / 255f, gr = 146 / 255f, b = 156 / 255f, a = 158 / 255f, t = .5f;
        DeveloperScreen.rect(g.pose(), x, y, w, t, r, gr, b, a);
        DeveloperScreen.rect(g.pose(), x, y + h - t, w, t, r, gr, b, a);
        DeveloperScreen.rect(g.pose(), x, y, t, h, r, gr, b, a);
        DeveloperScreen.rect(g.pose(), x + w - t, y, t, h, r, gr, b, a);
    }
    private static void background(GuiGraphics g, float x, float y, float w, float h, float blend, boolean hover) {
        DeveloperScreen.rect(g.pose(), x, y, w, h, 1, 1, 1, blend * (hover ? .4f : .1f));
    }
    private void button(GuiGraphics g, ResourceLocation texture, float x, float y, float blend) {
        float a = (in(mouseX, mouseY, x, y, BUTTON * SCALE, BUTTON * SCALE) ? 1 : .7f) * blend;
        DeveloperScreen.quad(g.pose(), texture, x, y, BUTTON * SCALE, BUTTON * SCALE, 0xc1 / 255f, 0xcf / 255f, 0xd5 / 255f, a);
    }
    private void text(GuiGraphics g, String text, float x, float y, float size, int rgb, float alpha) {
        if (alpha <= 0) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float s = size / 9f;
        pose.scale(s, s, 1);
        g.drawString(font, text, 0, 0, Math.max(4, (int) (255 * alpha)) << 24 | rgb, false);
        pose.popPose();
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        float mx0 = menuX(), mw = MENU_W * SCALE;
        int shown = shownLocations();
        for (int i = 0; i < shown; i++) {
            int index = first + i; float y = rowY(i);
            if (usable(cached.get(index)) && in(mx, my, mx0 + TELEPORT_X * SCALE, y + BUTTON_Y * SCALE, BUTTON * SCALE, BUTTON * SCALE)) {
                onClose(); AcademyNetwork.CHANNEL.sendToServer(LocationRequest.perform(index)); return true;
            }
            if (in(mx, my, mx0 + REMOVE_X * SCALE, y + BUTTON_Y * SCALE, BUTTON * SCALE, BUTTON * SCALE)) {
                AcademyNetwork.CHANNEL.sendToServer(LocationRequest.remove(index)); return true;
            }
        }
        float ay = rowY(shown);
        if (typing != null && in(mx, my, mx0 + CONFIRM_X * SCALE, ay + BUTTON_Y * SCALE, BUTTON * SCALE, BUTTON * SCALE)) { add(); return true; }
        if (in(mx, my, mx0, ay, mw, ROW_H * SCALE)) { if (typing == null) typing = new TextEdit("", Math.min(16, LocationTeleport.NAME_LENGTH)); return true; }
        typing = null;
        return super.mouseClicked(mx, my, button);
    }
    /** 原作confirmInput: 入力した名前（16文字まで）で、術者の立っている場所を登録する。 */
    private void add() {
        if (typing == null) return;
        if (cached.size() < LocationTeleport.MAX_LOCATIONS)
            AcademyNetwork.CHANNEL.sendToServer(LocationRequest.add(typing.text()));
        typing = null;
    }
    @Override public boolean charTyped(char c, int modifiers) {
        if (typing != null) {
            typing.type(c);
            return true;
        }
        return super.charTyped(c, modifiers);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (typing != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { add(); return true; }
            if (typing.key(key)) return true;
            if (key != GLFW.GLFW_KEY_ESCAPE) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        first = Math.max(0, Math.min(first - (int) Math.signum(delta), cached.size() - visibleRows()));
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }

    // テスト用の入口。パネル自身の当たり判定を通す。
    /** プレイヤーと同じ操作で、追加用の行をクリックし、名前を入力してEnterを押す。 */
    public void addForTest(String name) {
        float y = rowY(shownLocations()) + ROW_H * SCALE / 2;
        mouseClicked(menuX() + 20 * SCALE, y, 0);
        for (char c : name.toCharArray()) charTyped(c, 0);
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    }
    public int rows() { return shownLocations(); }
    public boolean rowUsable(int shown) { return usable(cached.get(first + shown)); }
    /** 行のテレポートボタンの中心（画面座標）。 */
    public double[] teleportButton(int shown) {
        return new double[]{menuX() + (TELEPORT_X + BUTTON / 2) * SCALE, rowY(shown) + (BUTTON_Y + BUTTON / 2) * SCALE};
    }
}
