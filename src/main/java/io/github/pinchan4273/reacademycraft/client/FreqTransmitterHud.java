package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.FreqReply;
import io.github.pinchan4273.reacademycraft.network.FreqRequest;
import io.github.pinchan4273.reacademycraft.terminal.TerminalApps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 原作FreqTransmitterUI（AcademyCraft commit 7b1401c）: 周波数送信機のアプリの窓。端末から切り替わった後、ゲームの上に描く。
 * 開いている間、マウスのボタンはこのアプリが使う: 目から4ブロック以内のブロックを右クリックすると、それを対象にする。
 *
 * - 開始: 「matrixかnodeを右クリック」。matrixなら、そのSSID（無ければe0）を問い合わせ、次にそのネットワークのパスワードを求める。
 *   nodeなら、そのパスワードを求める。それ以外はe4。何も無い所をクリックすると閉じる。
 * - パスワードは入力し（星で表示）、Enterで確定する。違えばe1。
 * - matrixの後: 右クリックした各nodeを、そのネットワークへ繋ぐ（e5、次にe6で戻る。またはe2）。
 * - nodeの後: 右クリックした各機械を、そのnodeへ繋ぐ（e5、次にe6で戻る。またはe3）。
 * - どの状態も20秒で、サーバーの返事を待つ間は3秒で時間切れになる（"st"）。
 *
 * 文言・箱・位置は原作のもの: アプリの箱は(15, 15)でアイコン付き、注記は中央の右下、パスワードの箱は140×40。
 * 原作は、パスワードの入力中、キーをすべて横取りする。ここでは空の画面がキーを受け取り、Escapeで送信機を閉じる。
 * 原作のnodeのパスワードの入力は、案内に"Authorizing..."（s1_1）を出す。そのまま残している。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class FreqTransmitterHud {
    static final long TIMEOUT = 20000, TRANSMIT_TIMEOUT = 3000;
    private static final int BG = 0x77272727, GLOW = 0xaaffffff;
    @Nullable private static State current;
    private static int nextId;
    private static final Map<Integer, Consumer<FreqReply>> WAITING = new HashMap<>();
    private FreqTransmitterHud() { }

    private abstract static class State {
        final boolean handlesKey; final long created = Util.getMillis(); long timeout = TIMEOUT;
        State(boolean handlesKey) { this.handlesKey = handlesKey; }
        abstract void draw(GuiGraphics g, float w, float h);
        void click(@Nullable BlockHitResult hit) { }
        void key(char c) { }
        void enter() { }
        void backspace() { }
        long age() { return Util.getMillis() - created; }
        void startTransmitting() { timeout = TRANSMIT_TIMEOUT; }
    }

    public static boolean isOpen() { return current != null; }
    /** 原作AppFreqTransmitter.onStart。 */
    public static void start() { WAITING.clear(); set(new Start()); }
    public static void close() { set(null); }
    private static void set(@Nullable State next) {
        var client = Minecraft.getInstance();
        boolean wasTyping = client.screen instanceof KeyCapture;
        current = next;
        // 原作は、パスワードの入力中、キーをすべて横取りする。
        if (next != null && next.handlesKey) { if (!wasTyping) client.setScreen(new KeyCapture()); }
        else if (wasTyping) client.setScreen(null);
    }
    private static String local(String key) { return Component.translatable("academy.app.freq_transmitter." + key).getString(); }
    private static void ask(int op, BlockPos a, BlockPos b, String text, Consumer<FreqReply> then) {
        int id = ++nextId;
        WAITING.put(id, then);
        AcademyNetwork.CHANNEL.sendToServer(new FreqRequest(id, op, a, b, text));
    }
    /** サーバーの返事を受け取るclient側の処理。 */
    public static void receive(FreqReply reply) {
        var then = WAITING.remove(reply.id());
        if (then != null) then.accept(reply);
    }
    /** 原作Raytrace.traceLiving(player, 4, nothing): 目から4ブロック以内のブロック。無ければ何も返さない。 */
    @Nullable static BlockHitResult trace() {
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null) return null;
        var eye = player.getEyePosition(); var end = eye.add(player.getViewVector(1).scale(4));
        // filNormalを使う原作traceLiving(player, 4, nothing): 当たり判定の箱があるブロックだけで止まる。
        var hit = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** 原作のアプリはGUIのsessionと一緒に閉じた。ここでは切断で状態と返事の無い問い合わせを捨て、次のワールドへ持ち越さない。 */
    @SubscribeEvent public static void freqTransmitterLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { current = null; WAITING.clear(); }

    // ---- 入力 ----
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        var client = Minecraft.getInstance();
        if (current == null || client.screen != null || client.player == null) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        event.setCanceled(true);
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) click(trace());
    }
    /** テスト用の入口と、右ボタンの処理: 原作handleClicking。 */
    public static void click(@Nullable BlockHitResult hit) { if (current != null) current.click(hit); }
    public static void type(String text) { if (current != null) for (char c : text.toCharArray()) current.key(c); }
    public static void enter() { if (current != null) current.enter(); }
    @Nullable public static String stateName() { return current == null ? null : current.getClass().getSimpleName(); }

    /** ゲームを止めない空の画面。パスワードを入力中の状態へ、すべてのキーを渡す。 */
    static final class KeyCapture extends Screen {
        KeyCapture() { super(Component.empty()); }
        @Override public boolean charTyped(char c, int modifiers) {
            if (current != null && SharedConstants.isAllowedChatCharacter(c)) current.key(c);
            return true;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (current == null) return super.keyPressed(key, scan, modifiers);
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) current.enter();
            else if (key == GLFW.GLFW_KEY_BACKSPACE) current.backspace();
            else if (key == GLFW.GLFW_KEY_ESCAPE) close();
            return true;
        }
        @Override public void renderBackground(GuiGraphics g) { }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void removed() { if (current != null && current.handlesKey) current = null; }
    }

    // ---- 描画 ----
    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (current == null || client.options.hideGui || client.player == null) return;
        if (client.screen != null && !(client.screen instanceof KeyCapture)) return;
        var g = event.getGuiGraphics();
        float w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        // アプリの箱: アイコンと名前を(15, 15)に。
        var pose = g.pose(); var font = client.font;
        String name = local("name");
        float len = font.width(name) * 10 / 9f;
        pose.pushPose(); pose.translate(15, 15, 0);
        box(g, 0, 0, 30 + len, 18);
        DeveloperScreen.quad(pose, TerminalHud.icon(TerminalApps.FREQ_TRANSMITTER), 2, 0, 18, 18, 1, 1, 1, 1);
        text(g, name, 24, 4, 0xffffffff);
        pose.popPose();
        var state = current;
        state.draw(g, w, h);
        if (current == state && state.age() > state.timeout) set(new NotifyAndQuit("st"));
    }
    /** 原作drawBox: 暗い背景と、その光。 */
    private static void box(GuiGraphics g, float x, float y, float w, float h) {
        DeveloperScreen.rect(g.pose(), x, y, w, h, (BG >> 16 & 255) / 255f, (BG >> 8 & 255) / 255f, (BG & 255) / 255f, (BG >>> 24) / 255f);
        KeyHintHud.glow(g.pose(), x, y, w, h, 1, 0xffffff, (GLOW >>> 24) / 255f);
    }
    private static void text(GuiGraphics g, String text, float x, float y, int argb) {
        float s = 10 / 9f;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(s, s, 1);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, argb, false);
        g.pose().popPose();
    }
    /** 原作drawTextBox: 文字を120で折り返し、余白5の箱に入れる。 */
    private static void textBox(GuiGraphics g, String str, float x, float y) {
        var font = Minecraft.getInstance().font; float s = 10 / 9f;
        List<String> lines = TutorialMarkdown.multiline(str, t -> font.width(t) * s, 0, 120);
        float width = 0; for (var line : lines) width = Math.max(width, font.width(line) * s);
        float height = lines.size() * 10, margin = 5;
        box(g, x, y, margin * 2 + width + 25, margin * 2 + height);
        for (int i = 0; i < lines.size(); i++) text(g, lines.get(i), x + margin, y + margin + i * 10, 0xffffffff);
    }

    // ---- 状態 ----
    private static class Notify extends State {
        final String key;
        Notify(String key) { super(false); this.key = key; }
        @Override void draw(GuiGraphics g, float w, float h) { textBox(g, local(key), w / 2 + 10, h / 2 + 10); }
    }
    private static final class NotifyAndQuit extends Notify {
        NotifyAndQuit(String key) { super(key); }
        @Override void draw(GuiGraphics g, float w, float h) { super.draw(g, w, h); if (age() > 1000) close(); }
    }
    private static final class NotifyAndReturn extends Notify {
        final State back;
        NotifyAndReturn(String key, State back) { super(key); this.back = back; }
        @Override void draw(GuiGraphics g, float w, float h) {
            super.draw(g, w, h);
            // 原作は同じ状態へ戻るので、20秒は状態が始まった時点から数え続ける。
            if (age() > 700) set(back);
        }
    }
    private static final class Start extends State {
        boolean started;
        Start() { super(false); }
        @Override void draw(GuiGraphics g, float w, float h) { textBox(g, local("s0_0"), w / 2 + 10, h / 2 + 10); }
        @Override void click(@Nullable BlockHitResult hit) {
            if (hit == null) { close(); return; }
            if (started) return;
            var level = Minecraft.getInstance().level; var pos = hit.getBlockPos();
            if (level.getBlockEntity(pos) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity) {
                started = true; startTransmitting();
                ask(FreqRequest.QUERY_SSID, pos, pos, "", r -> { if (current == this) set(r.ok() ? new AuthorizeNode(pos, r.text()) : new NotifyAndQuit("e4")); });
            } else if (level.getBlockState(pos).getBlock() instanceof io.github.pinchan4273.reacademycraft.world.block.WirelessMatrixBlock) {
                started = true; startTransmitting();
                ask(FreqRequest.QUERY_SSID, pos, pos, "", r -> { if (current == this) set(r.ok() ? new Authorize(pos, r.text()) : new NotifyAndQuit("e0")); });
            } else {
                set(new NotifyAndQuit("e4"));
            }
        }
    }
    /** 原作StateAuthorizeとStateAuthorizeNode: SSIDまたはNAME、星、案内。 */
    private abstract static class Password extends State {
        final BlockPos target; final String label, hint; String pass = "";
        Password(BlockPos target, String label, String hint) { super(true); this.target = target; this.label = label; this.hint = hint; }
        @Override void draw(GuiGraphics g, float w, float h) {
            var pose = g.pose(); pose.pushPose(); pose.translate(w / 2 + 10, h / 2 - 10, 0);
            box(g, 0, 0, 140, 40);
            text(g, label, 10, 5, 0xffbfbfbf);
            text(g, Component.translatable("academy.app.freq_transmitter.password_mask", "*".repeat(pass.length())).getString(), 10, 15, 0xffffffff);
            text(g, local(hint), 10, 25, 0xff30ffff);
            pose.popPose();
        }
        @Override void key(char c) { if (pass.length() < FreqRequest.MAX_TEXT) pass += c; }
        @Override void backspace() { if (!pass.isEmpty()) pass = pass.substring(0, pass.length() - 1); }
    }
    private static final class Authorize extends Password {
        Authorize(BlockPos matrix, String ssid) { super(matrix, "SSID: " + ssid, "s1_0"); }
        @Override void enter() {
            var state = new Notify("s1_1"); set(state); state.startTransmitting();
            String p = pass;
            ask(FreqRequest.AUTH_MATRIX, target, target, p, r -> { if (current == state) set(r.ok() ? new MatrixLink(target, p) : new NotifyAndQuit("e1")); });
        }
    }
    private static final class AuthorizeNode extends Password {
        AuthorizeNode(BlockPos node, String name) { super(node, "NAME: " + name, "s1_1"); }
        @Override void enter() {
            var state = new Notify("s1_1"); set(state); state.startTransmitting();
            String p = pass;
            ask(FreqRequest.AUTH_NODE, target, target, p, r -> { if (current == state) set(r.ok() ? new NodeLink(target, p) : new NotifyAndQuit("e1")); });
        }
    }
    private static final class MatrixLink extends State {
        final BlockPos matrix; final String pass;
        MatrixLink(BlockPos matrix, String pass) { super(false); this.matrix = matrix; this.pass = pass; }
        @Override void draw(GuiGraphics g, float w, float h) { textBox(g, local("s2_0"), w / 2 + 10, h / 2 + 10); }
        @Override void click(@Nullable BlockHitResult hit) {
            var level = Minecraft.getInstance().level;
            if (hit == null || !(level.getBlockEntity(hit.getBlockPos()) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity)) {
                set(new NotifyAndQuit("e4")); return;
            }
            var node = hit.getBlockPos();
            var state = new Notify("e5"); set(state); state.startTransmitting();
            ask(FreqRequest.LINK_NODE, node, matrix, pass, r -> { if (current == state) set(r.ok() ? new NotifyAndReturn("e6", this) : new NotifyAndQuit("e2")); });
        }
    }
    private static final class NodeLink extends State {
        final BlockPos node; final String pass;
        NodeLink(BlockPos node, String pass) { super(false); this.node = node; this.pass = pass; }
        @Override void draw(GuiGraphics g, float w, float h) { textBox(g, local("s3_0"), w / 2 + 10, h / 2 + 10); }
        @Override void click(@Nullable BlockHitResult hit) {
            var level = Minecraft.getInstance().level;
            var tile = hit == null ? null : level.getBlockEntity(hit.getBlockPos());
            if (tile == null) { set(new NotifyAndQuit("e4")); return; }
            // 原作IWirelessUser: 電力を受け取るか渡すもの全部（node自身を除く）。
            if (tile instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity
                    || !tile.getCapability(ForgeCapabilities.ENERGY).isPresent()) { set(new NotifyAndQuit("e4")); return; }
            var user = hit.getBlockPos();
            var state = new Notify("e5"); set(state); state.startTransmitting();
            ask(FreqRequest.LINK_USER, user, node, pass, r -> { if (current == state) set(r.ok() ? new NotifyAndReturn("e6", this) : new NotifyAndQuit("e3")); });
        }
    }
}
