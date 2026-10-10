package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 原作TechUIのContainerUI（core/client/ui/TechUI.scala、guis/rework/page_*.xml）: 機械の176x187のページを層で描く。
 * parent_background、次にui_inventoryと機械のui_*（アルファ0.675〜0.85で明滅）。左にページボタン、右にInfoArea:
 * 暗い3x3のblend_quadの枠と線で、中身に合わせて毎秒500で広がり、0.3秒後に中身をフェードインする。InfoAreaには
 * 原作のヒストグラム、区切り線、キーと値の項目が入る。
 *
 * ページは原作の中央揃えTechUIウィジェット（幅172、左へ18）が置く位置にあり、メニューのスロットはその上の原作の座標にある。
 */
public abstract class TechScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    static final ResourceLocation BACKGROUND = gui("parent/parent_background"), INVENTORY = gui("ui/ui_inventory"),
            BLEND_QUAD = gui("blend_quad"), LINE = gui("line"), HISTOGRAM = gui("histogram");
    public static final int PAGE_W = 176, PAGE_H = 187;
    // InfoArea: ページの2内側にある幅172のウィジェットの右7、下5、幅100。
    static final float INFO_X = 181, INFO_Y = 5, INFO_W = 100, KEY_LENGTH = 40;

    static ResourceLocation gui(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png"); }

    /**
     * 原作HistElement: ヒストグラムのバーと、その下の行、ラベル（原作のac.gui.common.histの語、または
     * 位相発電機のIFのような文字そのもの）。
     */
    public record Hist(Supplier<String> label, int color, DoubleSupplier fraction, Supplier<String> value) { }
    static Supplier<String> hist(String key) { return () -> Component.translatable("academy.gui.common.hist." + key).getString(); }
    public static Hist buffer(DoubleSupplier energy, double max) {
        return new Hist(hist("buffer"), 0xff25f7ff, () -> energy.getAsDouble() / max, () -> String.format(java.util.Locale.ROOT, "%.0f IF", energy.getAsDouble()));
    }
    public static Hist energy(DoubleSupplier energy, double max) {
        return new Hist(hist("energy"), 0xff25c4ff, () -> energy.getAsDouble() / max, () -> String.format(java.util.Locale.ROOT, "%.0f IF", energy.getAsDouble()));
    }
    public static Hist liquid(DoubleSupplier amount, double max) {
        return new Hist(hist("liquid"), 0xff7680de, () -> amount.getAsDouble() / max, () -> String.format(java.util.Locale.ROOT, "%.0f mB", amount.getAsDouble()));
    }

    private interface Element { }
    private record HistogramEl(float y, List<Hist> hists) implements Element { }
    private record HistRow(float y, Hist hist) implements Element { }
    private record SepLine(float y, String id) implements Element { }
    private record Button(float y, String name, Runnable action) implements Element { }
    private record Property(float y, String key, Supplier<String> value, @javax.annotation.Nullable java.util.function.Consumer<String> edit,
                            boolean password) implements Element { }

    private final List<Element> elements = new ArrayList<>();
    private final List<String> pages = new ArrayList<>(List.of("inv"));
    private int page;
    // この画面のブロックの無線ページ（原作WirelessPage）。
    @javax.annotation.Nullable private WirelessPanel wirelessPanel;
    private float elemY = 10, expectHeight = 50, infoHeight;
    private double lastFrame, blendStart;
    private float infoAlpha;

    protected TechScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PAGE_W; imageHeight = PAGE_H;
    }

    // 原作InfoAreaの組み立て。
    protected TechScreen<M> histogram(Hist... hists) {
        elemY += -30;
        elements.add(new HistogramEl(elemY, List.of(hists)));
        elemY += 210 * .4f;
        for (var hist : hists) { elements.add(new HistRow(elemY, hist)); elemY += 8; }
        expectHeight = Math.max(50, elemY + 8);
        return this;
    }
    protected TechScreen<M> sepline(String id) {
        elemY += 3;
        elements.add(new SepLine(elemY, id)); elemY += 8;
        expectHeight = Math.max(50, elemY + 8);
        return this;
    }
    protected TechScreen<M> seplineInfo() { return sepline("info"); }
    protected TechScreen<M> property(String key, Supplier<String> value) { return property(key, value, null, false); }
    /** 原作InfoArea.blank。 */
    protected TechScreen<M> blank(float height) { elemY += height; return this; }
    /** 原作InfoArea.button: 名前を9の位置に中央揃え、幅は最低50、マウスの下では明るくする。 */
    protected TechScreen<M> button(String name, Runnable action) {
        elements.add(new Button(elemY, name, action)); elemY += 8;
        expectHeight = Math.max(50, elemY + 8);
        return this;
    }
    /** 原作InfoArea.reset: 全部外して、組み立て直す。 */
    protected void resetInfo() {
        elements.clear(); edits.clear(); changed.clear(); editing = null;
        elemY = 10; expectHeight = 50;
    }
    private float buttonWidth(Button b) { return Math.max(50, font.width(b.name()) + 5); }
    /**
     * editCallbackを持つ原作の項目: 値を括弧で囲み、クリックで入力でき、変更中は青、Enterで送る。
     * パスワードは星で表示する。
     */
    protected TechScreen<M> property(String key, Supplier<String> value, @javax.annotation.Nullable java.util.function.Consumer<String> edit, boolean password) {
        var property = new Property(elemY, key, value, edit, password);
        elements.add(property); elemY += 8;
        if (edit != null) edits.put(property, value.get());
        expectHeight = Math.max(50, elemY + 8);
        return this;
    }
    // 編集できる項目の文字、どれが入力中か、どれが変更済みか。
    private final java.util.Map<Property, String> edits = new java.util.LinkedHashMap<>();
    private final java.util.Set<Property> changed = new java.util.HashSet<>();
    @javax.annotation.Nullable private Property editing;
    @javax.annotation.Nullable private TextEdit editor;

    /** ページを開かずに、無線ページと同じ内容をサーバーへ問い合わせる: matrix自身のネットワーク。 */
    protected void watchWireless(net.minecraft.core.BlockPos pos) {
        wirelessPanel = new WirelessPanel(pos, false);
        wirelessPanel.refresh();
    }
    public void refreshWireless() { if (wirelessPanel != null) wirelessPanel.refresh(); }
    /** 原作WirelessPage.userPage（nodeならnodePage）: このブロックの2ページ目。 */
    protected void enableWireless(net.minecraft.core.BlockPos pos, boolean node) {
        wirelessPanel = new WirelessPanel(pos, node);
        if (!pages.contains("wireless")) pages.add("wireless");
    }
    @Override protected void init() {
        super.init();
        // CGui: 幅172のウィジェットを中央に置いて左へ18。ページはその中で中央揃え。
        leftPos = Math.round((width - PAGE_W) / 2f - 18);
        topPos = Math.round((height - PAGE_H) / 2f);
        lastFrame = blendStart = Util.getMillis() / 1000.0;
    }
    /** 原作TechUI.breatheAlpha。すべてのui_層で共通の時計を使う。 */
    public static float breatheAlpha() {
        double sin = (1 + Math.sin(Util.getMillis() / 1000.0 / .8)) * .5;
        return (float) (.675 + sin * .175);
    }
    /**
     * 原作InventoryPage(name)はpage_inventoryを読み、そのui_inventoryがプレイヤーのスロットを描く。原作へ自前の窓を
     * 渡す機械（InventoryPage(window)）は、その窓が描くものだけになる。
     */
    protected boolean inventoryLayer() { return true; }
    /** 機械自身のui_層と、そのページに描くもの（ページ座標）。 */
    protected abstract void renderPage(GuiGraphics g, float breathe, int mx, int my);

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(leftPos, topPos, 0);
        float breathe = breatheAlpha();
        renderedBreathe = breathe;
        DeveloperScreen.quad(pose, BACKGROUND, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, 1);
        if (page == 0) {
            if (inventoryLayer()) DeveloperScreen.quad(pose, INVENTORY, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
            renderPage(g, breathe, mx - leftPos, my - topPos);
        } else {
            if (wirelessPanel != null) wirelessPanel.render(g, font, breathe, mx - leftPos, my - topPos);
        }
        // ページボタン: 幅24、0.7倍、ウィジェットの(-20, 22 i)から。現在のページは明るい。
        for (int i = 0; i < pages.size(); i++) {
            float bx = -18, by = i * 22, s = 24 * .7f;
            boolean hover = mx - leftPos >= bx && mx - leftPos < bx + s && my - topPos >= by && my - topPos < by + s;
            float lum = i == page ? 1 : .8f, alpha = hover || i == page ? 1 : .8f;
            DeveloperScreen.quad(pose, gui("icons/icon_" + pages.get(i)), bx, by, s, s, lum, lum, lum, alpha);
        }
        renderInfo(g);
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    // 無線ページのテクスチャ。機械自身のページも使う。
    static final ResourceLocation ELEMENT = WirelessPanel.ELEMENT, MATRIX = WirelessPanel.MATRIX, KEY = WirelessPanel.KEY,
            CONNECTED = WirelessPanel.CONNECTED, UNCONNECTED = WirelessPanel.UNCONNECTED, UP = WirelessPanel.UP, DOWN = WirelessPanel.DOWN;
    protected static boolean in(double x, double y, float bx, float by, float w, float h) { return WirelessPanel.in(x, y, bx, by, w, h); }
    /** WirelessPageReplyのクライアント処理: 開いている画面がそのブロックのものなら渡す。 */
    public static void receive(io.github.pinchan4273.reacademycraft.network.WirelessPageReply reply) {
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen instanceof TechScreen<?> tech) tech.acceptWireless(reply);
        else if (screen instanceof DeveloperScreen developer) developer.acceptWireless(reply);
    }
    /** このページのブロックについてサーバーが言うこと。別のブロックへの返答は扱わない。 */
    public void acceptWireless(io.github.pinchan4273.reacademycraft.network.WirelessPageReply reply) {
        if (wirelessPanel != null) wirelessPanel.accept(reply);
    }
    /** 原作のページボタン: そのページを描き、他を隠す。無線ページはサーバーへ改めて問い合わせる。 */
    public void showPage(int index) {
        if (index < 0 || index >= pages.size()) return;
        page = index;
        if ("wireless".equals(pages.get(index))) refreshWireless();
    }
    /** 原作のconfirm: 行のパスワードをサーバーへ送り、入力欄を空にする。 */
    public void connect(int index) { if (wirelessPanel != null) wirelessPanel.connect(index); }
    public void disconnect() { if (wirelessPanel != null) wirelessPanel.disconnect(); }

    private void renderInfo(GuiGraphics g) {
        double now = Util.getMillis() / 1000.0, dt = Math.min(now - lastFrame, .5);
        lastFrame = now;
        float max = (float) dt * 500, delta = expectHeight - infoHeight;
        infoHeight += Math.min(max, Math.abs(delta)) * Math.signum(delta);
        float alpha = (float) Math.max(0, Math.min(1, (now - blendStart - .3) / .3));
        infoAlpha = alpha;
        if (alpha < 1 || Math.abs(infoHeight - expectHeight) >= .01f) renderedTransition = true;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(INFO_X, INFO_Y, 0);
        blendQuad(g, INFO_W, infoHeight, 4);
        if (alpha > 0) for (var element : elements) drawElement(g, element, alpha);
        pose.popPose();
    }
    /** 原作BlendQuad: blend_quadの9マス、半透明の黒、領域の周り4。その上下にline.png。 */
    private static void blendQuad(GuiGraphics g, float w, float h, float margin) {
        var m = g.pose().last().pose();
        float[] xs = {-margin, 0, w, w + margin}, ys = {-margin, 0, h, h + margin};
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BLEND_QUAD);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            float u = i / 3f, v = j / 3f, st = 1 / 3f;
            buffer.vertex(m, xs[i], ys[j], 0).uv(u, v).color(0, 0, 0, .5f).endVertex();
            buffer.vertex(m, xs[i], ys[j + 1], 0).uv(u, v + st).color(0, 0, 0, .5f).endVertex();
            buffer.vertex(m, xs[i + 1], ys[j + 1], 0).uv(u + st, v + st).color(0, 0, 0, .5f).endVertex();
            buffer.vertex(m, xs[i + 1], ys[j], 0).uv(u + st, v).color(0, 0, 0, .5f).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        float mrg = 3.2f;
        DeveloperScreen.quad(g.pose(), LINE, -mrg, -8.6f, w + mrg * 2, 12, 1, 1, 1, 1);
        DeveloperScreen.quad(g.pose(), LINE, -mrg, h - 2, w + mrg * 2, 8, 1, 1, 1, 1);
    }
    private void drawElement(GuiGraphics g, Element element, float alpha) {
        var pose = g.pose();
        if (element instanceof HistogramEl h) {
                pose.pushPose();
                pose.translate(0, h.y(), 0);
                pose.scale(.4f, .4f, 1);
                DeveloperScreen.quad(pose, HISTOGRAM, 0, 0, 210, 210, 1, 1, 1, alpha);
                for (int i = 0; i < h.hists().size(); i++) {
                    var hist = h.hists().get(i);
                    float p = (float) Math.max(.03, Math.min(1, hist.fraction().getAsDouble()));
                    int c = hist.color();
                    // ProgressBar UP: バーの下部、(56 + 40 i, 78)から16x120。
                    DeveloperScreen.rect(pose, 56 + i * 40, 78 + 120 * (1 - p), 16, 120 * p,
                            (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, alpha);
                }
                pose.popPose();
        } else if (element instanceof HistRow r) {
                int c = r.hist().color();
                DeveloperScreen.rect(pose, 3, r.y() + 1.5f, 6, 6, (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, alpha);
                // 原作の枠より長いラベル（日本語のエネルギーバッファ）は、元の大きさの3/4より小さくしない。
                // 代わりにその後ろから始まる値の領域を使い、値のほうを縮める。
                String label = r.hist().label().get();
                float natural = font.width(label) * 8 / 9f, labelRoom = Math.min(60, Math.max(32, natural * .75f));
                float valueX = Math.max(6 + KEY_LENGTH, 10 + labelRoom + 3);
                text(g, label, 10, r.y(), 8, 1, alpha, labelRoom);
                text(g, r.hist().value().get(), valueX, r.y(), 8, 1, alpha, INFO_W - valueX);
        } else if (element instanceof Button b) {
            float w = buttonWidth(b), bx = (INFO_W - w) / 2;
            double mx = lastMouseX - leftPos - INFO_X, my = lastMouseY - topPos - INFO_Y;
            float lum = in(mx, my, bx, b.y(), w, 8) ? 1 : .8f;
            int c = (int) (lum * 255);
            text(g, b.name(), bx + (w - font.width(b.name())) / 2, b.y(), 9, 1, alpha, w, c << 16 | c << 8 | c);
        } else if (element instanceof SepLine s) {
            text(g, Component.translatable("academy.gui.common.sep." + s.id()).getString(), 3, s.y(), 6, .6f, alpha, INFO_W - 3);
        } else if (element instanceof Property p) {
                // 編集できる値の"["は4左に立つので、キーの領域はその分狭い。
                text(g, Component.translatable("academy.gui.common.prop." + p.key()).getString(), 6, p.y(), 8, 1, alpha, KEY_LENGTH - (p.edit() == null ? 2 : 6));
                if (p.edit() == null) {
                    text(g, p.value().get(), 6 + KEY_LENGTH, p.y(), 8, 1, alpha, INFO_W - 6 - KEY_LENGTH);
                } else {
                    String typed = edits.getOrDefault(p, "");
                    String shown = editing == p && editor != null ? editor.shown(p.password()) : p.password() ? "*".repeat(typed.length()) : typed;
                    text(g, "[", 6 + KEY_LENGTH - 4, p.y(), 8, 1, alpha, 10);
                    text(g, shown, 6 + KEY_LENGTH, p.y(), 8, 1, alpha, 40, changed.contains(p) ? 0x2180d8 : 0xffffff);
                    text(g, "]", 6 + KEY_LENGTH + 42, p.y(), 8, 1, alpha, 10);
                }
        }
    }
    /**
     * 原作のフォントを指定の大きさで描く: ゲームのフォントを9の行の高さから拡大縮小し、指定のアルファの白で描く。
     * ゲームの字形は原作より幅が広いので、枠（キーなら原作の40）より長い文字は、隣の値へはみ出さないよう縮小する。
     */
    protected void text(GuiGraphics g, String text, float x, float y, float size, float mono, float alpha, float room) {
        text(g, text, x, y, size, mono, alpha, room, 0xffffff);
    }
    private void text(GuiGraphics g, String text, float x, float y, float size, float mono, float alpha, float room, int rgb) {
        drawText(font, g, text, x, y, size, mono, alpha, room, rgb);
    }
    static void drawText(net.minecraft.client.gui.Font font, GuiGraphics g, String text, float x, float y, float size, float mono, float alpha, float room, int rgb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float s = size / 9f;
        if (font.width(text) * s > room && font.width(text) > 0) s = room / font.width(text);
        pose.translate(0, (size - 9 * s) / 2, 0);
        pose.scale(s, s, 1);
        int a = Math.max(4, (int) (255 * alpha * mono));
        g.drawString(font, text, 0, 0, a << 24 | rgb, false);
        pose.popPose();
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) { }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        double x = mx - leftPos, y = my - topPos;
        editing = null;
        if (button == 0) for (var element : List.copyOf(elements))
            if (element instanceof Button b && in(x, y, INFO_X + (INFO_W - buttonWidth(b)) / 2, INFO_Y + b.y(), buttonWidth(b), 8)) { b.action().run(); return true; }
        if (button == 0) for (var p : edits.keySet())
            if (in(x, y, INFO_X + 6 + KEY_LENGTH - 4, INFO_Y + p.y(), 50, 8)) { editing = p; editor = new TextEdit(edits.getOrDefault(p, ""), 32); return true; }
        float s = 24 * .7f;
        if (button == 0) for (int i = 0; i < pages.size(); i++) if (in(x, y, -18, i * 22, s, s)) { showPage(i); return true; }
        if (page == 0) return super.mouseClicked(mx, my, button);
        if (button == 0 && wirelessPanel != null) wirelessPanel.click(x, y);
        return true;
    }
    @Override public boolean charTyped(char c, int modifiers) {
        if (editing != null) {
            if (editor != null && editor.type(c)) { edits.put(editing, editor.text()); changed.add(editing); }
            return true;
        }
        if (page != 0 && wirelessPanel != null && wirelessPanel.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (editing != null) {
            if (editor != null && editor.key(key)) {
                if (!editor.text().equals(edits.getOrDefault(editing, ""))) { edits.put(editing, editor.text()); changed.add(editing); }
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { confirm(editing); editing = null; return true; }
            if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        }
        if (page != 0 && wirelessPanel != null && wirelessPanel.keyPressed(key)) return true;
        return super.keyPressed(key, scan, modifiers);
    }
    @Override protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        return mx < left - 20 || mx >= left + INFO_X + INFO_W + 4 || my < top || my >= top + PAGE_H;
    }
    /** 原作は何も出さないインベントリページのボタンに、移植版が機械について出す説明: 原作が絵に任せている状態を言葉で示す。 */
    protected List<Component> pageTooltip() { return List.of(); }
    private int lastMouseX, lastMouseY;
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        lastMouseX = mx; lastMouseY = my;
        // 原作isSlotActive: スロットとその中身は、インベントリページでだけ表示する。
        if (page != 0) { renderBackground(g); renderBg(g, partial, mx, my); return; }
        renderBackground(g); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
        float s = 24 * .7f;
        if (mx >= leftPos - 18 && mx < leftPos - 18 + s && my >= topPos && my < topPos + s && !pageTooltip().isEmpty())
            g.renderComponentTooltip(font, pageTooltip(), mx, my);
    }
    // テスト用の入口。
    /** 原作ConfirmInputEvent: 入力した値を項目のコールバックへ渡し、色を戻す。 */
    private void confirm(Property p) {
        changed.remove(p);
        if (p.edit() != null) p.edit().accept(edits.getOrDefault(p, ""));
    }
    /** テスト用の入口: キーで指定した編集可能な項目に入力し、Enterを押す。 */
    public boolean editProperty(String key, String value) {
        for (var p : edits.keySet()) if (p.key().equals(key)) { edits.put(p, value); changed.add(p); confirm(p); return true; }
        return false;
    }
    /** 原作のcontentCell: 編集可能な項目に入力された内容。 */
    protected String typed(String key) {
        for (var e : edits.entrySet()) if (e.getKey().key().equals(key)) return e.getValue();
        return "";
    }
    /** テスト用の入口: 編集可能な項目に入力し、Enterは押さない。 */
    public boolean typeProperty(String key, String value) {
        for (var p : edits.keySet()) if (p.key().equals(key)) { edits.put(p, value); return true; }
        return false;
    }
    /** テスト用の入口: この名前のInfoAreaのボタンを押す。 */
    public boolean pressButton(String name) {
        for (var element : List.copyOf(elements)) if (element instanceof Button b && b.name().equals(name)) { b.action().run(); return true; }
        return false;
    }
    public boolean propertyEditable(String key) { return edits.keySet().stream().anyMatch(p -> p.key().equals(key)); }
    public float infoHeight() { return infoHeight; }
    /** InfoAreaが最大の高さになり、中身がフェードインし終えたか: 撮影の前に待つ条件。 */
    private float renderedBreathe = 1;
    private boolean renderedTransition;
    /** 最後に描いたフレームでのui_層のアルファ。 */
    public float renderedOverlayAlpha() { return renderedBreathe; }
    /** InfoAreaがまだ広がっている、またはフェードイン中にフレームを描いたか。 */
    public boolean renderedInfoTransition() { return renderedTransition; }
    public boolean infoSettled() { return infoAlpha >= 1 && Math.abs(infoHeight - expectHeight) < .01f; }
    public int page() { return page; }
    public List<String> pages() { return List.copyOf(pages); }
    @javax.annotation.Nullable public io.github.pinchan4273.reacademycraft.network.WirelessPageReply wireless() { return wirelessPanel == null ? null : wirelessPanel.reply(); }
    public void typePassword(int index, String value) { if (wirelessPanel != null) wirelessPanel.typePassword(index, value); }
    public float expectedInfoHeight() { return expectHeight; }
}
