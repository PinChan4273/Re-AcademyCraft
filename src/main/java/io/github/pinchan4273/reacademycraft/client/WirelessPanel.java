package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.WirelessPageReply;
import io.github.pinchan4273.reacademycraft.network.WirelessPageRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 原作WirelessPage（core/client/ui/TechUI.scala、guis/rework/page_wireless.xml）: ブロックからnodeへの接続（userPage）、
 * またはnodeからmatrixへの接続（nodePage）。接続先、接続できる候補、暗号化された候補ごとのパスワード欄、一覧をスクロールする
 * 矢印を表示する。機械のTechUIでは2ページ目に、開発機では自身の上に表示する。座標はページのもの。
 */
public final class WirelessPanel {
    // page_wireless.xml: パネル（165x132.6、中央、17.4下）は一覧の領域を(8, 60)に147.5x115で置き、行の高さは16。
    // 接続中の行は(7.5, 34.6)に150x18。矢印はその右、7下と下端。その上にConnectedとAvailable。
    static final float LIST_X = 8, LIST_Y = 60, ROW_W = 150, ROW_H = 16, LINKED_X = 7.5f, LINKED_Y = 34.62f,
            ARROW_X = 154.5f, UP_Y = 51.62f, DOWN_Y = 161.2f;
    static final int VISIBLE = 7;
    static final ResourceLocation ELEMENT = TechScreen.gui("element/element_background300x32"), MATRIX = TechScreen.gui("icons/icon_matrix"),
            KEY = TechScreen.gui("icons/icon_key"), CONNECTED = TechScreen.gui("icons/icon_connected"), UNCONNECTED = TechScreen.gui("icons/icon_unconnected"),
            UP = TechScreen.gui("button/button_arrowupb"), DOWN = TechScreen.gui("button/button_arrowdownb");

    private final BlockPos pos;
    private final boolean node;
    @javax.annotation.Nullable private WirelessPageReply reply;
    private int listStart = 0, focused = -1;
    private final Map<Integer, String> passwords = new HashMap<>();

    public WirelessPanel(BlockPos pos, boolean node) { this.pos = pos.immutable(); this.node = node; }

    public BlockPos pos() { return pos; }
    @javax.annotation.Nullable public WirelessPageReply reply() { return reply; }
    /** パスワード欄がキーボード入力を持っているか。 */
    public boolean typing() { return focused >= 0; }

    static boolean in(double x, double y, float bx, float by, float w, float h) { return x >= bx && x < bx + w && y >= by && y < by + h; }

    public void render(GuiGraphics g, Font font, float breathe, double mx, double my) {
        var pose = g.pose();
        DeveloperScreen.quad(pose, TechScreen.gui(node ? "icons/icon_tomatrix" : "icons/icon_tonode"), 10, 10, 16, 16, 1, 1, 1, breathe);
        TechScreen.drawText(font, g, Component.translatable("academy.gui.common.pg_wireless.connected").getString(), 13, 28, 9, .8f, 1, 58, 0xffffff);
        TechScreen.drawText(font, g, Component.translatable("academy.gui.common.pg_wireless.available").getString(), 13, 52, 9, .8f, 1, 58, 0xffffff);
        var linked = reply == null ? null : reply.linked();
        DeveloperScreen.quad(pose, ELEMENT, LINKED_X, LINKED_Y, ROW_W, 18, 1, 1, 1, 1);
        float on = linked == null ? .6f : 1;
        DeveloperScreen.quad(pose, MATRIX, LINKED_X + 8, LINKED_Y + 3, 12, 12, 1, 1, 1, on);
        TechScreen.drawText(font, g, linked == null ? Component.translatable("academy.gui.common.pg_wireless.not_connected").getString() : linked.name(),
                LINKED_X + 20, LINKED_Y + 5, 9, 1, on, 80, 0xffffff);
        boolean hoverLinked = linked != null && in(mx, my, LINKED_X + 125, LINKED_Y + 3, 12, 12);
        DeveloperScreen.quad(pose, linked == null ? UNCONNECTED : CONNECTED, LINKED_X + 125, LINKED_Y + 3, 12, 12, 1, 1, 1,
                linked == null ? .6f : hoverLinked ? 1 : .8f);
        var avail = avail();
        for (int i = listStart; i < Math.min(avail.size(), listStart + VISIBLE); i++) {
            var entry = avail.get(i);
            float y = LIST_Y + (i - listStart) * ROW_H;
            DeveloperScreen.quad(pose, ELEMENT, LIST_X, y, ROW_W, ROW_H, 1, 1, 1, 1);
            DeveloperScreen.quad(pose, MATRIX, LIST_X + 8, y + 2, 12, 12, 1, 1, 1, 178 / 255f);
            TechScreen.drawText(font, g, entry.name(), LIST_X + 20, y + 4, 9, 221 / 255f, 1, 40, 0xffffff);
            if (entry.encrypted()) {
                DeveloperScreen.quad(pose, KEY, LIST_X + 60, y + 2, 12, 12, 1, 1, 1, focused == i ? 1 : 178 / 255f);
                DeveloperScreen.rect(pose, LIST_X + 72.5f, y + 3.5f, 48, 9, 34 / 255f, 34 / 255f, 34 / 255f, 170 / 255f);
                String typed = "*".repeat(passwords.getOrDefault(i, "").length()) + (focused == i && (Util.getMillis() / 500) % 2 == 0 ? "_" : "");
                TechScreen.drawText(font, g, typed, LIST_X + 72.5f, y + 3.5f, 10, 1, 1, 48, 0xffffff);
            }
            boolean hover = in(mx, my, LIST_X + 125, y + 2, 12, 12);
            DeveloperScreen.quad(pose, UNCONNECTED, LIST_X + 125, y + 2, 12, 12, 1, 1, 1, hover ? .8f : .6f);
        }
        DeveloperScreen.quad(pose, UP, ARROW_X, UP_Y, 16, 16, 1, 1, 1, in(mx, my, ARROW_X, UP_Y, 16, 16) ? 1 : .8f);
        DeveloperScreen.quad(pose, DOWN, ARROW_X, DOWN_Y, 16, 16, 1, 1, 1, in(mx, my, ARROW_X, DOWN_Y, 16, 16) ? 1 : .8f);
    }
    private List<WirelessPageReply.Entry> avail() { return reply == null ? List.of() : reply.avail(); }

    /** ページ上の左クリック: 矢印、接続中の行の切断、行の接続、またはパスワード欄。 */
    public void click(double x, double y) {
        focused = -1;
        if (in(x, y, ARROW_X, UP_Y, 16, 16)) { listStart = Math.max(0, listStart - 1); return; }
        if (in(x, y, ARROW_X, DOWN_Y, 16, 16)) { listStart = Math.max(0, Math.min(listStart + 1, avail().size() - VISIBLE)); return; }
        if (in(x, y, LINKED_X + 125, LINKED_Y + 3, 12, 12)) { disconnect(); return; }
        var avail = avail();
        for (int i = listStart; i < Math.min(avail.size(), listStart + VISIBLE); i++) {
            float ry = LIST_Y + (i - listStart) * ROW_H;
            if (in(x, y, LIST_X + 125, ry + 2, 12, 12)) { connect(i); return; }
            if (avail.get(i).encrypted() && in(x, y, LIST_X + 72.5f, ry + 3.5f, 48, 9)) { focused = i; return; }
        }
    }
    /** 入力された文字を、フォーカス中のパスワード欄へ入れる。キーボード入力を持つ欄が無ければfalse。 */
    public boolean charTyped(char c) {
        if (focused < 0) return false;
        String typed = passwords.getOrDefault(focused, "");
        if (c >= ' ' && c != 127 && typed.length() < WirelessPageRequest.MAX_PASSWORD) passwords.put(focused, typed + c);
        return true;
    }
    /** フォーカス中のパスワード欄のBackspaceとEnter。Escapeと、フォーカスが無い場合はページの担当外。 */
    public boolean keyPressed(int key) {
        if (focused < 0) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            String typed = passwords.getOrDefault(focused, "");
            if (!typed.isEmpty()) passwords.put(focused, typed.substring(0, typed.length() - 1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { connect(focused); focused = -1; return true; }
        return key != GLFW.GLFW_KEY_ESCAPE;
    }

    /** このブロックについてサーバーが言うこと。別のブロックへの返答は扱わない。 */
    public boolean accept(WirelessPageReply reply) {
        if (!pos.equals(reply.self())) return false;
        this.reply = reply;
        listStart = Math.max(0, Math.min(listStart, reply.avail().size() - VISIBLE));
        passwords.clear(); focused = -1;
        return true;
    }
    public void refresh() { ask(WirelessPageRequest.LIST, pos, ""); }
    /** 原作のconfirm: 行のパスワードをサーバーへ送り、入力欄を空にする。 */
    public void connect(int index) {
        if (reply == null || index < 0 || index >= reply.avail().size()) return;
        ask(WirelessPageRequest.CONNECT, reply.avail().get(index).pos(), passwords.getOrDefault(index, ""));
        passwords.remove(index);
    }
    public void disconnect() {
        if (reply != null && reply.linked() != null) ask(WirelessPageRequest.DISCONNECT, pos, "");
    }
    public void typePassword(int index, String value) { passwords.put(index, value); }
    private void ask(int op, BlockPos target, String password) {
        AcademyNetwork.CHANNEL.sendToServer(new WirelessPageRequest(op, pos, target, password));
    }
}
