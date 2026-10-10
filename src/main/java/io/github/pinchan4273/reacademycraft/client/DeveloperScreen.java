package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.develop.DeveloperMenu;
import io.github.pinchan4273.reacademycraft.develop.DeveloperTier;
import io.github.pinchan4273.reacademycraft.develop.DevelopmentSession;
import io.github.pinchan4273.reacademycraft.skill.AbilityCategory;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import io.github.pinchan4273.reacademycraft.skill.SkillTreeLayout;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 原作DeveloperUI（SkillTree.scalaとguis/rework/page_developer.xml）: 400×187のページ。左に系統のパネルと機械の
 * パネル、右に技能の木（視差のある背景、node、親への線、熟練度の輪）。技能（またはレベルアップのボタン）を
 * クリックすると、暗くした覆いの上にその画面を開き、そこで開発できる。系統を持たない場合、木の場所はAcademy OSの
 * コンソールになり、`learn`で系統を得る。学習も開発もすべてmenuのボタンの動作を通し、サーバーで確かめる。
 * 大きさは原作のCGUIの単位で、画面が小さいときはページ全体を縮める。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeveloperScreen extends AbstractContainerScreen<DeveloperMenu> {
    static final float MAIN_W = 400, MAIN_H = 187;
    static final float RIGHT_X = 118, AREA_X = RIGHT_X + 10, AREA_Y = 18, AREA_W = 257, AREA_H = 139;
    static final float LEFT_X = 4, LEFT_W = 108.5f;
    static final float ABILITY_X = LEFT_X + 2, ABILITY_Y = (MAIN_H - 32) / 2 - 10;
    static final float UPGRADE_X = ABILITY_X + 60, UPGRADE_Y = ABILITY_Y + 14.5f, UPGRADE_W = 186.014f * .26f, UPGRADE_H = 59.524f * .26f;
    static final float WIDGET = 16, TOTAL = 23, ICON = 14, PROG = 31;
    static final float PROG_ALIGN = (TOTAL - PROG) / 2, ALIGN = (TOTAL - ICON) / 2, DRAW_ALIGN = (WIDGET - TOTAL) / 2;
    private static ResourceLocation tex(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    static final ResourceLocation PARENT_RIGHT = tex("guis/parent/parent_background_developerright"), UI_RIGHT = tex("guis/ui/ui_developerright"),
            PARENT_LEFT = tex("guis/parent/parent_background_developerleft"), UI_LEFT = tex("guis/ui/ui_developerleft"),
            UI_LEFT_TREE = tex("guis/ui/ui_developerleft_skilltree"),
            PARENT_MACHINE = tex("guis/parent/parent_background_developermachine"), AREA_BACK = tex("guis/effect/effect_developer_background"),
            SKILL_BACK = tex("guis/developer/skill_back"), SKILL_MASK = tex("guis/developer/skill_radial_mask"),
            SKILL_OUTLINE = tex("guis/developer/skill_outline"), LINE = tex("guis/developer/line"),
            VIEW_OUTLINE = tex("guis/developer/skill_view_outline"), VIEW_OUTLINE_GLOW = tex("guis/developer/skill_view_outline_glow"),
            BUTTON_LEARN = tex("guis/button/button_learn"), BUTTON = tex("guis/developer/button"),
            ELEMENT_BACK = tex("guis/element/element_background300x32"), ICON_NODE = tex("guis/icons/icon_node");

    private float scale = 1, ox, oy;
    /** 原作の木の部品のcreationTime: 木を作り（直し）た時刻（秒）。 */
    private double built;
    private final Map<ResourceLocation, double[]> hover = new HashMap<>();
    @Nullable private View view;
    @Nullable private Console console;
    private float mouseX, mouseY;

    @javax.annotation.Nullable private String nodeName;
    @javax.annotation.Nullable private net.minecraft.core.BlockPos developerPos;
    /** 原作のlink_page: Coverの上の無線のページ。nodeのボタンで開く。 */
    @Nullable private LinkPage linkPage;
    /** DeveloperNodeを受け取るclient側の処理: 開いている開発機のパネルが、このmenuのものなら渡す。 */
    public static void receive(io.github.pinchan4273.reacademycraft.network.DeveloperNode packet) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof DeveloperScreen screen && screen.menu.containerId == packet.containerId()) {
            screen.nodeName = packet.name(); screen.developerPos = packet.developer();
        }
    }
    /** linkのページへのWirelessPageReply: ページが開いていて、その開発機への返事なら渡す。 */
    void acceptWireless(io.github.pinchan4273.reacademycraft.network.WirelessPageReply reply) {
        if (linkPage != null) linkPage.panel.accept(reply);
    }
    /** テスト用: パネルが表示しているnodeの名前。サーバーが伝える前はnull。 */
    @javax.annotation.Nullable public String nodeName() { return nodeName; }
    public DeveloperScreen(DeveloperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = (int) MAIN_W; imageHeight = (int) MAIN_H;
    }
    @SubscribeEvent public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(AcademyContent.DEVELOPER_MENU.get(), DeveloperScreen::new));
    }
    static double now() { return Util.getMillis() / 1000.0; }

    @Override protected void init() {
        super.init();
        // 原作CGUIはページを自身の大きさで中央に描く。画面が小さいときは全体を縮める。
        scale = Math.min(1, Math.min((width - 8) / MAIN_W, (height - 8) / MAIN_H));
        ox = (width - MAIN_W * scale) / 2; oy = (height - MAIN_H * scale) / 2;
        if (built == 0) rebuild();
    }
    /** 原作RebuildEvent: ページを作り直し、フェードインをやり直す。 */
    private void rebuild() {
        built = now(); view = null; hover.clear();
        var data = ability();
        String name = minecraft.player.getName().getString();
        // 原作initialize: 系統なしならコンソール、手に磁気コイルがあればリセットのコンソール、それ以外は木。
        console = data == null || !data.hasAbility() ? new Console(name, false)
                : minecraft.player.getMainHandItem().is(AcademyContent.MAGNETIC_COIL.get()) ? new Console(name, true) : null;
    }
    @Nullable private PlayerAbilityData ability() {
        return minecraft == null || minecraft.player == null ? null : minecraft.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { AbilityControls.discardClicks(); super.onClose(); }
    @Override public void removed() { AbilityControls.discardClicks(); super.removed(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int x, int y) { }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) { }
    @Override protected void containerTick() {
        super.containerTick();
        if (console != null) console.tick();
        if (view != null) view.tick();
        // 他の場所で系統を得た（または失った）ときは作り直す。原作のコンソールも、成功したときに作り直す。
        var data = ability();
        if (console != null && !console.emergency && data != null && data.hasAbility() && console.idle()) rebuild();
    }

    // ---- 形 ----
    private float localX(double screenX) { return (float) ((screenX - ox) / scale); }
    private float localY(double screenY) { return (float) ((screenY - oy) / scale); }
    /** 原作dx, dy: 画面全体でのマウスの位置。-0.5から0.5。 */
    private float parallaxX() { return Mth.clamp(mouseX / Math.max(1, width), 0, 1) - .5f; }
    private float parallaxY() { return Mth.clamp(mouseY / Math.max(1, height), 0, 1) - .5f; }
    private List<SkillTreeLayout.Node> nodes() {
        var data = ability();
        if (data == null || !data.hasAbility()) return List.of();
        var result = new ArrayList<SkillTreeLayout.Node>();
        for (var node : SkillTreeLayout.tree(data.getAbility())) {
            var definition = SkillCatalog.find(node.skill());
            // 原作LearningHelper.canBePotentiallyLearnedと_.isEnabled: サーバーの設定でOFFにした技能は表示しない。
            // 使うのはサーバーが送ってきた設定で、このclientのファイルではない。
            if (definition != null && io.github.pinchan4273.reacademycraft.config.SyncedAcademyRules.skillEnabled(node.skill())
                    && (data.getLevel() >= definition.level() || data.hasLearned(node.skill())
                    || node.parent() == null || data.hasLearned(node.parent()))) result.add(node);
        }
        return result;
    }
    /** 部品の左上（ページの単位）: その位置を、マウスの逆へ最大10単位ずらしたもの。 */
    private float nodeX(SkillTreeLayout.Node node) { return AREA_X + node.x() - parallaxX() * 10; }
    private float nodeY(SkillTreeLayout.Node node) { return AREA_Y + node.y() - parallaxY() * 10; }

    // ---- 描画の補助 ----
    static void quad(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        texturedQuad(pose, texture, x, y, w, h, u0, v0, u1, v1, r, g, b, a);
    }
    static void quad(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h, float r, float g, float b, float a) {
        quad(pose, texture, x, y, w, h, 0, 0, 1, 1, r, g, b, a);
    }
    private static void texturedQuad(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h,
                                     float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(m, x, y, 0).uv(u0, v0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x, y + h, 0).uv(u0, v1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x + w, y + h, 0).uv(u1, v1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x + w, y, 0).uv(u1, v0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
    static void rect(PoseStack pose, float x, float y, float w, float h, float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(m, x, y, 0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x, y + h, 0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x + w, y + h, 0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x + w, y, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
    /** アイコンを灰色で描く。まだ習得していない技能をShaderMonoで描くのと同じ。 */
    static void mono(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h, float a) {
        ShaderInstance shader = DeveloperShaders.mono();
        if (shader == null) { quad(pose, texture, x, y, w, h, .6f, .6f, .6f, a); return; }
        RenderSystem.setShader(() -> shader);
        texturedQuad(pose, texture, x, y, w, h, 0, 0, 1, 1, 1, 1, 1, a);
    }
    /** 原作skill_progbar: 円のテクスチャのうち、放射状のマスクが進み具合より下の部分。 */
    static void ring(PoseStack pose, ResourceLocation circle, float x, float y, float size, float progress) {
        ShaderInstance shader = DeveloperShaders.progbar();
        if (shader == null || progress <= 0) return;
        RenderSystem.setShaderTexture(1, SKILL_MASK);
        shader.safeGetUniform("Progress").set(progress);
        RenderSystem.setShader(() -> shader);
        texturedQuad(pose, circle, x, y, size, size, 0, 0, 1, 1, 1, 1, 1, 1);
    }
    enum Align { LEFT, CENTER, RIGHT }
    /** FontOptionを指定した原作Font.draw: 単位での大きさ、xで揃える、影なし。 */
    private void text(GuiGraphics g, String text, float x, float y, float size, Align align, int argb) {
        float s = size / 9f; int w = font.width(text);
        float dx = align == Align.LEFT ? 0 : align == Align.CENTER ? -w / 2f : -w;
        g.pose().pushPose();
        g.pose().translate(x, y, 0); g.pose().scale(s, s, 1); g.pose().translate(dx, 0, 0);
        g.drawString(font, text, 0, 0, argb, false);
        g.pose().popPose();
    }
    /** 原作Font.drawSeperated: 幅で折り返し、中央に揃える。 */
    private void wrapped(GuiGraphics g, String text, float x, float y, float width, float size, int argb) {
        float s = size / 9f; int line = 0;
        for (var part : font.split(Component.literal(text), (int) (width / s))) {
            g.pose().pushPose();
            g.pose().translate(x, y + line * size * 1.1f, 0); g.pose().scale(s, s, 1);
            g.drawString(font, part, -font.width(part) / 2, 0, argb, false);
            g.pose().popPose(); line++;
        }
    }

    @Override public void render(GuiGraphics g, int x, int y, float partialTick) {
        mouseX = x; mouseY = y;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ox, oy, 0); pose.scale(scale, scale, 1);
        drawRight(g);
        drawLeft(g);
        pose.popPose();
        if (view != null) view.draw(g);
        if (linkPage != null) linkPage.draw(g);
    }

    private void drawRight(GuiGraphics g) {
        var pose = g.pose();
        quad(pose, PARENT_RIGHT, RIGHT_X, 0, 278, MAIN_H, 1, 1, 1, 1);
        quad(pose, UI_RIGHT, RIGHT_X, 0, 278, MAIN_H, 1, 1, 1, 1);
        if (console != null) { console.draw(g); return; }
        // 原作: 背景をマウスの逆へ最大1%ずらし、背景の1/1.01の窓から見せる。
        double inv = 1 / 1.01, du = .01;
        float u = (float) ((parallaxX() * du - .5) * inv + .5), v = (float) ((parallaxY() * du - .5) * inv + .5);
        quad(pose, AREA_BACK, AREA_X, AREA_Y, AREA_W, AREA_H, u, v, u + (float) inv, v + (float) inv, 1, 1, 1, 1);
        var data = ability();
        if (data == null) return;
        var nodes = nodes();
        double time = now();
        float localMouseX = localX(mouseX), localMouseY = localY(mouseY);
        for (int idx = 0; idx < nodes.size(); idx++) {
            var node = nodes.get(idx);
            boolean learned = data.hasLearned(node.skill());
            boolean parentLearned = node.parent() == null || data.hasLearned(node.parent());
            double alpha = learned ? 1 : parentLearned ? .7 : .25;
            float wx = nodeX(node), wy = nodeY(node);
            boolean hovering = view == null && localMouseX >= wx && localMouseX < wx + WIDGET && localMouseY >= wy && localMouseY < wy + WIDGET;
            // 原作のhover: 0.1秒で1から1.2へ、また戻る。次の変化は、前の変化が終わってから始める。
            var state = hover.computeIfAbsent(node.skill(), k -> new double[] {0, time - 2});
            double transit = Mth.clamp((time - state[1]) / .1, 0, 1);
            double nodeScale = state[0] == 0 ? Mth.lerp(transit, 1.2, 1) : Mth.lerp(transit, 1, 1.2);
            if (transit == 1) {
                if (state[0] == 0 && hovering) { state[0] = 1; state[1] = time; }
                else if (state[0] == 1 && !hovering) { state[0] = 0; state[1] = time; }
            }
            double dt = Math.max(0, time - built - (idx * .08 + .1));
            float backAlpha = (float) (alpha * Mth.clamp(dt * 10, 0, 1));
            float iconAlpha = (float) (alpha * Mth.clamp((dt - .08) * 10, 0, 1));
            float progressBlend = (float) Mth.clamp((dt - .12) * 2, 0, 1);
            float lineBlend = (float) Mth.clamp(dt * 5, 0, 1);
            pose.pushPose();
            pose.translate(wx + DRAW_ALIGN, wy + DRAW_ALIGN, 0);
            pose.translate(TOTAL / 2, TOTAL / 2, 0); pose.scale((float) nodeScale, (float) nodeScale, 1); pose.translate(-TOTAL / 2, -TOTAL / 2, 0);
            quad(pose, SKILL_BACK, 0, 0, TOTAL, TOTAL, 1, 1, 1, backAlpha);
            quad(pose, SKILL_OUTLINE, PROG_ALIGN, PROG_ALIGN, PROG, PROG, .2f, .2f, .2f, backAlpha * .6f);
            if (learned) quad(pose, node.icon(), ALIGN, ALIGN, ICON, ICON, 1, 1, 1, iconAlpha);
            else mono(pose, node.icon(), ALIGN, ALIGN, ICON, ICON, iconAlpha);
            if (learned) ring(pose, SKILL_OUTLINE, PROG_ALIGN, PROG_ALIGN, PROG, progressBlend * data.getProficiency(node.skill()));
            pose.popPose();
            if (node.parent() != null) {
                var parent = SkillTreeLayout.node(node.parent());
                if (parent != null) line(pose, wx, wy, node, parent, (float) nodeScale,
                        (float) (alpha * (learned ? 1 : .4)), lineBlend);
            }
        }
    }
    /**
     * 原作drawLine: 幅5.5のline.pngを、親の中心から12.2離れた所から、このnodeの中心から12.2離れた所へ、
     * フェードインに合わせて伸ばす。原作は輪の下を深度マスクで隠したが、ここでは2つの輪（それぞれ12.2、hover中のnodeは
     * その倍率を掛ける）の外で始めて終える。
     */
    private void line(PoseStack pose, float wx, float wy, SkillTreeLayout.Node node, SkillTreeLayout.Node parent,
                      float nodeScale, float alpha, float progress) {
        float px = parent.x() - node.x(), py = parent.y() - node.y();
        float norm = (float) Math.sqrt(px * px + py * py);
        if (norm < 1e-3f || progress <= 0) return;
        float ux = px / norm, uy = py / norm;
        float parentScale = hover.containsKey(parent.skill()) && hover.get(parent.skill())[0] == 1 ? 1.2f : 1;
        float x0 = px + WIDGET / 2 - ux * 12.2f * parentScale, y0 = py + WIDGET / 2 - uy * 12.2f * parentScale;
        float x1 = WIDGET / 2 + ux * 12.2f * nodeScale, y1 = WIDGET / 2 + uy * 12.2f * nodeScale;
        float xx = Mth.lerp(progress, x0, x1), yy = Mth.lerp(progress, y0, y1);
        float nx = -(y1 - y0), ny = x1 - x0; float n = (float) Math.sqrt(nx * nx + ny * ny) / (5.5f / 2);
        nx /= n; ny /= n;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, LINE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        pose.pushPose(); pose.translate(wx, wy, 0);
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(m, x0 - nx, y0 - ny, 0).uv(0, 0).color(1, 1, 1, alpha).endVertex();
        buffer.vertex(m, x0 + nx, y0 + ny, 0).uv(0, 1).color(1, 1, 1, alpha).endVertex();
        buffer.vertex(m, xx + nx, yy + ny, 0).uv(1, 1).color(1, 1, 1, alpha).endVertex();
        buffer.vertex(m, xx - nx, yy - ny, 0).uv(1, 0).color(1, 1, 1, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        pose.popPose();
    }

    private void drawLeft(GuiGraphics g) {
        var pose = g.pose();
        var data = ability();
        quad(pose, PARENT_LEFT, LEFT_X, 0, LEFT_W, MAIN_H, 1, 1, 1, 1);
        // 原作: 開発機が無いとき、左側はui_developerleft_skilltreeになり、機械のパネルは描かない。
        quad(pose, menu.viewer() ? UI_LEFT_TREE : UI_LEFT, LEFT_X, 0, LEFT_W, MAIN_H, 1, 1, 1, 1);
        if (!menu.viewer()) drawMachine(g);
        drawAbility(g, data);
    }
    private void drawMachine(GuiGraphics g) {
        var pose = g.pose();
        quad(pose, PARENT_MACHINE, LEFT_X, 0, LEFT_W, MAIN_H, 1, 1, 1, 1);
        float cx = LEFT_X + (LEFT_W - 100) / 2;
        if (menu.tier() != DeveloperTier.PORTABLE) {
            // 原作MSG_GET_NODE: 繋がっているnodeの名前。無ければ（サーバーが伝えるまでも）N/A。
            text(g, I18n.get("academy.developer.machine.current_node"), cx, (MAIN_H - 12) / 2 + 17, 12, Align.LEFT, 0xffffffff);
            boolean over = inside(localX(mouseX), localY(mouseY), cx, (MAIN_H - 16) / 2 + 29, 100, 16);
            quad(pose, ELEMENT_BACK, cx, (MAIN_H - 16) / 2 + 29, 100, 16, 1, 1, 1, over ? 1 : 178 / 255f);
            quad(pose, ICON_NODE, cx + 7, (MAIN_H - 16) / 2 + 29 + 2, 12, 12, 1, 1, 1, 1);
            text(g, nodeName == null || nodeName.isEmpty() ? I18n.get("academy.developer.machine.not_available") : nodeName, cx + 26, (MAIN_H - 16) / 2 + 29 + 2, 12, Align.LEFT, 0xffffffff);
        }
        // 原作のpage_developer.xmlは、これらをどの言語でも英語で書いていた（XMLに翻訳が無かった）。
        // ここでは翻訳のkeyを付け、プレイヤーの言語で表示する。
        text(g, I18n.get("academy.developer.machine.power"), cx, (MAIN_H - 12) / 2 + 43, 12, Align.LEFT, 0xffffffff);
        text(g, I18n.get("academy.developer.machine.sync_rate"), cx, (MAIN_H - 12) / 2 + 66, 12, Align.LEFT, 0xffffffff);
        float barX = LEFT_X + (LEFT_W - 97) / 2;
        float power = menu.tier().capacity == 0 ? 0 : Math.min(1, menu.energy() / (float) menu.tier().capacity);
        rect(pose, barX, (MAIN_H - 8) / 2 + 55.5f, 97 * power, 8, 252 / 255f, 197 / 255f, 50 / 255f, 1);
        rect(pose, barX, (MAIN_H - 8) / 2 + 77.5f, 97 * syncRate(menu.tier()), 8, 50 / 255f, 164 / 255f, 252 / 255f, 1);
    }
    private void drawAbility(GuiGraphics g, @Nullable PlayerAbilityData data) {
        var pose = g.pose();
        // 能力のパネル。
        var category = data == null || !data.hasAbility() ? null : data.getAbility();
        quad(pose, SkillTreeLayout.categoryIcon(category), ABILITY_X, ABILITY_Y, 32, 32, 1, 1, 1, 1);
        text(g, category == null ? I18n.get("academy.developer.machine.not_available") : I18n.get("academy.ability." + category.getPath()), ABILITY_X + 31, ABILITY_Y + 2, 13, Align.LEFT, 0xffffffff);
        float progress = category == null ? 0 : Math.max(.02f, data.getLevelProgress());
        rect(pose, ABILITY_X + 31, ABILITY_Y + (32 - 1.5f) / 2 - 2, 70, 1.5f, 102 / 255f, 102 / 255f, 102 / 255f, 76 / 255f);
        rect(pose, ABILITY_X + 31, ABILITY_Y + (32 - 1.5f) / 2 - 2, 70 * progress, 1.5f, 1, 1, 1, 1);
        text(g, I18n.get("academy.developer.machine.experience", (int) ((category == null ? 0 : data.getLevelProgress()) * 100)), ABILITY_X + 30, ABILITY_Y + 15.5f + 1, 8, Align.LEFT, 0xffffffff);
        if (canUpgrade()) {
            boolean over = view == null && inside(localX(mouseX), localY(mouseY), UPGRADE_X, UPGRADE_Y, UPGRADE_W, UPGRADE_H);
            quad(pose, BUTTON_LEARN, UPGRADE_X, UPGRADE_Y, UPGRADE_W, UPGRADE_H, 1, 1, 1, over ? 1 : 178 / 255f);
        } else if (category != null) {
            float levelX = ABILITY_X + (104 - 41.156f) / 2 + 27.844f;
            text(g, levelName(data.getLevel()), levelX + 41.156f, ABILITY_Y + (32 - 12) / 2 + 6, 9, Align.RIGHT, 0xff1177d6);
        }
    }
    /** 原作AbilityLocalization.levelDesc: レベルごとの名前。 */
    static String levelName(int level) { return I18n.get("academy.developer.ui.level" + Mth.clamp(level, 0, 5)); }
    /** 原作DeveloperType.syncRate。 */
    static float syncRate(DeveloperTier tier) { return tier == DeveloperTier.ADVANCED ? 1f : tier == DeveloperTier.NORMAL ? .7f : .3f; }
    private boolean canUpgrade() {
        var data = ability();
        // 原作は、開発機があるときだけbtn_upgradeを表示する。
        return !menu.viewer() && data != null && data.hasAbility() && data.canLevelUp();
    }
    private static boolean inside(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    // ---- 入力 ----
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return true;
        if (linkPage != null) { linkPage.click(x, y); return true; }
        if (view != null) { view.click((float) x, (float) y); return true; }
        float lx = localX(x), ly = localY(y);
        // 原作button_wireless: この開発機の無線のページを、覆いの上に開く。
        if (nodeButton() && inside(lx, ly, LEFT_X + (LEFT_W - 100) / 2, (MAIN_H - 16) / 2 + 29, 100, 16)) { openLinkPage(); return true; }
        if (console == null) {
            for (var node : nodes())
                if (inside(lx, ly, nodeX(node), nodeY(node), WIDGET, WIDGET)) { view = new View(node, false); return true; }
            if (canUpgrade() && inside(lx, ly, UPGRADE_X, UPGRADE_Y, UPGRADE_W, UPGRADE_H)) { view = new View(null, true); return true; }
        }
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (linkPage != null) { linkPage.panel.keyPressed(key); return true; }
        if (console != null) { console.key(key); return true; }
        // Escだけで閉じる: ここではインベントリのキーは文字の入力になる。原作のページもすべてのキーを受け取っていた。
        return true;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (linkPage != null) { linkPage.panel.charTyped(character); return true; }
        if (console != null && SharedConstants.isAllowedChatCharacter(character)) console.type(character);
        return true;
    }

    /** nodeのボタンは開発機のブロックのときだけある。原作もTileDeveloperのときだけ。 */
    private boolean nodeButton() { return menu.tier() != DeveloperTier.PORTABLE && developerPos != null; }
    /** テスト用の入口とボタンの動作: 無線のページを、新しく問い合わせて開く。 */
    public boolean openLinkPage() {
        if (!nodeButton() || linkPage != null) return false;
        linkPage = new LinkPage(new WirelessPanel(developerPos, false));
        linkPage.panel.refresh();
        return true;
    }
    @Nullable public WirelessPanel linkPanel() { return linkPage == null ? null : linkPage.panel; }
    /** テスト用の入口: ページの外の覆いをクリックする。 */
    public void closeLinkPage() { if (linkPage != null && linkPage.endedAt < 0) linkPage.endedAt = now(); }

    /**
     * 原作blackCoverの中央にWirelessPage.userPageを置く: 覆いは0.7の黒で、0.2秒でフェードイン・アウトする。
     * ページの外をクリックすると終わり、原作のCloseEventがパネルを作り直して、nodeの名前をまた問い合わせる。
     * ここでは、ページが最後に伝えた繋がり先を使う。
     */
    final class LinkPage {
        final WirelessPanel panel;
        final double opened = now(); double endedAt = -1;
        LinkPage(WirelessPanel panel) { this.panel = panel; }
        float pageX() { return (width - TechScreen.PAGE_W * scale) / 2; }
        float pageY() { return (height - TechScreen.PAGE_H * scale) / 2; }
        void click(double x, double y) {
            float px = (float) ((x - pageX()) / scale), py = (float) ((y - pageY()) / scale);
            if (inside(px, py, 0, 0, TechScreen.PAGE_W, TechScreen.PAGE_H)) panel.click(px, py);
            else if (endedAt < 0) endedAt = now();
        }
        void draw(GuiGraphics g) {
            float fade = (float) Mth.clamp((now() - (endedAt < 0 ? opened : endedAt)) / .2, 0, 1);
            float alpha = endedAt < 0 ? fade : 1 - fade;
            if (endedAt >= 0 && alpha <= 0) {
                linkPage = null;
                var reply = panel.reply();
                if (reply != null) nodeName = reply.linked() == null ? "" : reply.linked().name();
                rebuild();
                return;
            }
            var pose = g.pose();
            pose.pushPose(); pose.translate(0, 0, 300);
            rect(pose, 0, 0, width, height, 0, 0, 0, alpha * .7f);
            pose.translate(pageX(), pageY(), 0); pose.scale(scale, scale, 1);
            quad(pose, TechScreen.BACKGROUND, 0, 0, TechScreen.PAGE_W, TechScreen.PAGE_H, 1, 1, 1, 1);
            panel.render(g, font, 1, (mouseX - pageX()) / scale, (mouseY - pageY()) / scale);
            pose.popPose();
        }
    }

    // ---- 技能とレベルアップの画面 ----
    /** Coverの上の原作skillViewAreaとlevelUpArea: 0.7の黒が0.2秒でフェードインし、クリックで閉じる。 */
    final class View {
        @Nullable final SkillTreeLayout.Node node; final boolean level;
        final double opened = now(); double endedAt = -1;
        boolean canClose = true, shouldRebuild, requested, sawDeveloping, buttonVisible;
        int waited; float progress; @Nullable String message;
        View(@Nullable SkillTreeLayout.Node node, boolean level) {
            this.node = node; this.level = level;
            var data = ability();
            buttonVisible = !menu.viewer() && (level || (data != null && node != null && !data.hasLearned(node.skill())));
        }
        int action() {
            return level ? DevelopmentSession.LEVEL_UP : DevelopmentSession.actionFor(node.skill());
        }
        int estimate() {
            var data = ability();
            return level ? menu.tier().cost(5 * (data.getLevel() + 1))
                    : menu.tier().cost(DeveloperTier.skillStimulations(SkillCatalog.find(node.skill()).level()));
        }
        float centreX() { return width / 2f; }
        float centreY() { return height / 2f; }
        /** 原作newButton: 半分の倍率で64×32、文字の領域の(0, 55)を中心にする（レベルアップは40）。 */
        float[] buttonBox() {
            float cy = centreY() + 20 + (level ? 40 : 55) + 5;
            return new float[] {centreX() - 16, cy - 8, 32, 16};
        }
        void tick() {
            if (!requested) return;
            int state = menu.state();
            if (state == DevelopmentSession.DEVELOPING) {
                sawDeveloping = true; progress = menu.total() == 0 ? 0 : menu.elapsed() / (float) menu.total();
                message = level ? I18n.get("academy.developer.ui.dev_developing")
                        : I18n.get("academy.developer.ui.progress") + String.format(" %.0f%%", progress * 100);
            } else if (sawDeveloping && state == DevelopmentSession.DONE) {
                message = I18n.get("academy.developer.ui.dev_successful"); progress = 1; canClose = true; shouldRebuild = true; requested = false;
            } else if (sawDeveloping) {
                message = I18n.get("academy.developer.ui.dev_failed"); canClose = true; requested = false;
            } else if (++waited > 40) {
                // サーバーが開始しなかった: サーバーの判定が、この画面が見ていた状態と食い違った。
                message = I18n.get("academy.developer.ui.condition_fail"); canClose = true; requested = false;
            }
        }
        void click(float x, float y) {
            var box = buttonBox();
            if (buttonVisible && inside(x, y, box[0], box[1], box[2], box[3])) { press(); return; }
            if (!canClose) return;
            if (shouldRebuild) rebuild();
            else if (endedAt < 0) endedAt = now();
        }
        /** 原作のボタン: エネルギー、レベル、その他の条件の順に確かめ、満たしたときだけ開発する。 */
        void press() {
            var data = ability();
            if (data == null) return;
            if (menu.energy() < estimate()) message = I18n.get("academy.developer.ui.noenergy");
            else if (!level && SkillCatalog.find(node.skill()).level() > data.getLevel())
                message = I18n.get("academy.developer.ui.level_fail", SkillCatalog.find(node.skill()).level());
            else if ((menu.availability() & DevelopmentSession.availabilityBit(action())) == 0)
                message = I18n.get("academy.developer.ui.condition_fail");
            else {
                if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action());
                requested = true; canClose = false; waited = 0; sawDeveloping = false;
            }
            buttonVisible = false;
        }
        void draw(GuiGraphics g) {
            double time = now();
            float fade = (float) Mth.clamp((time - (endedAt < 0 ? opened : endedAt)) / .2, 0, 1);
            float alpha = endedAt < 0 ? fade : 1 - fade;
            if (endedAt >= 0 && alpha <= 0) { view = null; return; }
            var pose = g.pose();
            pose.pushPose(); pose.translate(0, 0, 200);
            rect(pose, 0, 0, width, height, 0, 0, 0, alpha * .7f);
            var data = ability();
            float cx = centreX(), cy = centreY();
            if (level) {
                actionIcon(pose, tex("abilities/condition/any" + Math.min(5, data.getLevel() + 1)), cx - 25, cy - 25, progress, progress == 1);
                float ty = cy + 20;
                text(g, I18n.get("academy.developer.ui.uplevel", levelName(data.getLevel() + 1)), cx, ty + 3, 12, Align.CENTER, 0xffffffff);
                text(g, I18n.get("academy.developer.ui.req") + " " + estimate(), cx, ty + 16, 9, Align.CENTER, 0xffffffff);
                text(g, message != null ? message : I18n.get("academy.developer.ui.level_question"), cx, ty + 26, 9, Align.CENTER, 0xffffffff);
            } else {
                boolean learned = data.hasLearned(node.skill());
                var definition = SkillCatalog.find(node.skill());
                float ty = cy + 20;
                String name = I18n.get(definition.translation());
                if (learned) {
                    actionIcon(pose, node.icon(), cx - 25, cy - 25, 0, false);
                    text(g, name, cx, ty + 3, 12, Align.CENTER, 0xffffffff);
                    text(g, I18n.get("academy.developer.ui.skill_exp") + (int) (data.getProficiency(node.skill()) * 100) + "%", cx, ty + 15, 8, Align.CENTER, 0xffa1e1ff);
                    wrapped(g, Component.translatable("academy.developer.desc." + node.skill().getPath()).getString(), cx, ty + 24, 200, 9, 0xffffffff);
                } else {
                    actionIcon(pose, node.icon(), cx - 25, cy - 25, progress, progress == 1);
                    text(g, name + " (LV " + definition.level() + ")", cx, ty + 3, 12, Align.CENTER, 0xffffffff);
                    text(g, I18n.get("academy.developer.ui.skill_not_learned"), cx, ty + 15, 10, Align.CENTER, 0xffff5555);
                    // 原作は、開発機があるときだけ条件・見積もり・ボタンを描く。
                    if (!menu.viewer()) {
                        conditions(g, definition, data, cx, ty);
                        text(g, message != null ? message : I18n.get("academy.developer.ui.learn_question", String.valueOf(estimate())),
                                cx, ty + 40, 10, Align.CENTER, 0xaaffffff);
                    }
                }
            }
            if (buttonVisible) {
                var box = buttonBox();
                boolean over = inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
                // 原作Tint: 通常は0.6の灰色、マウスの下では白。
                float c = over ? 1 : .6f;
                quad(pose, BUTTON, box[0], box[1], box[2], box[3], c, c, c, 1);
            }
            pose.popPose();
        }
        /** 原作skill.getDevConditionsの表示: 開発機の種類、次に各前提。満たしていないものは灰色。 */
        void conditions(GuiGraphics g, SkillCatalog.Definition definition, PlayerAbilityData data, float cx, float ty) {
            var icons = new ArrayList<Object[]>();
            DeveloperTier needed = definition.level() <= DeveloperTier.PORTABLE.maximumSkillLevel ? DeveloperTier.PORTABLE
                    : definition.level() <= DeveloperTier.NORMAL.maximumSkillLevel ? DeveloperTier.NORMAL : DeveloperTier.ADVANCED;
            icons.add(new Object[] {developerIcon(needed), menu.tier().ordinal() >= needed.ordinal(),
                    I18n.get("academy.developer.ui.type_" + needed.name().toLowerCase(java.util.Locale.ROOT))});
            if (definition.passive()) icons.add(new Object[] {tex("abilities/condition/any" + definition.level()),
                    SkillCatalog.IMPLEMENTED.stream().anyMatch(s -> s.level() == definition.level() && s.offeredTo(data.getAbility()) && data.hasLearned(s.id())),
                    I18n.get("academy.developer.ui.anyskill", definition.level())});
            for (var requirement : definition.prerequisites()) {
                var dep = SkillTreeLayout.node(requirement.skill());
                icons.add(new Object[] {dep == null ? SKILL_BACK : dep.icon(),
                        data.hasLearned(requirement.skill()) && data.getProficiency(requirement.skill()) >= requirement.proficiency(),
                        I18n.get(SkillCatalog.find(requirement.skill()).translation()) + String.format(": %.0f%%", requirement.proficiency() * 100)});
            }
            float len = 16 * icons.size();
            text(g, I18n.get("academy.developer.ui.req"), cx - len / 2 - 2, ty + 28, 9, Align.RIGHT, 0xaaffffff);
            for (int i = 0; i < icons.size(); i++) {
                float ix = cx - len / 2 + 16 * i, iy = ty + 25;
                var texture = (ResourceLocation) icons.get(i)[0]; boolean accepted = (Boolean) icons.get(i)[1];
                if (accepted) quad(g.pose(), texture, ix, iy, 14, 14, 1, 1, 1, 1); else mono(g.pose(), texture, ix, iy, 14, 14, 1);
                if (inside(mouseX, mouseY, ix, iy, 14, 14))
                    text(g, "(" + icons.get(i)[2] + ")", cx + len / 2 + 3, ty + 27, 9, Align.LEFT, accepted ? 0xeeffffff : 0xffee5858);
            }
        }
        /** 原作drawActionIcon: 背景50、アイコン27、開発の輪としてview outline。 */
        void actionIcon(PoseStack pose, ResourceLocation icon, float x, float y, float progress, boolean glow) {
            quad(pose, SKILL_BACK, x, y, 50, 50, 1, 1, 1, 1);
            quad(pose, icon, x + 11.5f, y + 11.5f, 27, 27, 1, 1, 1, 1);
            ring(pose, glow ? VIEW_OUTLINE_GLOW : VIEW_OUTLINE, x, y, 50, progress);
        }
    }
    /** 原作DeveloperType.texture: 開発機自身のアイテムまたはブロックの絵。 */
    static ResourceLocation developerIcon(DeveloperTier tier) {
        return switch (tier) {
            case PORTABLE -> tex("item/developer_portable_empty");
            case NORMAL -> tex("blocks/dev_normal");
            case ADVANCED -> tex("blocks/dev_advanced");
        };
    }

    // ---- コンソール ----
    /**
     * 原作Console: Academy OS。10行で、ゆっくり起動し、系統が無いため失敗した後、`learn`のコマンドを受け付ける。
     * これで（インベントリに因子があれば）menuを通して系統を得る。
     */
    final class Console {
        private final Deque<String> outputs = new ArrayDeque<>();
        private final Deque<Task> tasks = new ArrayDeque<>();
        @Nullable private Task current;
        private String input = "";
        private final Task inputTask = new Task() { @Override void begin() { output("OS >"); } @Override boolean finished() { return false; } };
        final boolean emergency;
        Console(String player, boolean emergency) {
            this.emergency = emergency;
            var random = RandomSource.create();
            enqueue(slowPrint(localized("init", player)));
            pause(.4);
            var numbers = new ArrayList<String>();
            for (int i = 1; i <= 6; i++) numbers.add((i * 10 + random.nextInt(6) - 3) + "%");
            numbers.add((64 + random.nextInt(4)) + "%");
            numbers.add(localized("boot_failed"));
            animSequence(.3, numbers);
            // 原作の起動の文: override、またはinvalid_catとlearn_hint（learn_hintは開発機があるときだけ）。
            enqueue(slowPrint(emergency ? localized("override") : menu.viewer() ? localized("invalid_cat") : localized("invalid_cat") + localized("learn_hint")));
        }
        boolean idle() { return current == inputTask && tasks.isEmpty(); }
        void tick() { }
        private void step() {
            if (current != null && current.finished()) { current.finish(); current = null; }
            if (current != null) current.update();
            if (current == null) { current = tasks.isEmpty() ? inputTask : tasks.poll(); current.begin(); }
        }
        void draw(GuiGraphics g) {
            step();
            int y = 5;
            var lines = new ArrayList<>(outputs);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (i == lines.size() - 1 && current == inputTask) line += input + ((Util.getMillis() % 1000) < 500 ? "_" : "");
                // 原作は各行を大きさ8で描き、はみ出してもそのまま描く。ゲームの字形はTrueTypeフォントより幅が広いので、
                // コンソールからはみ出す行は小さくして収める。
                for (var part : line.split("\n", -1)) {
                    float room = AREA_W - 10, size = 8, w = font.width(part) * size / 9f;
                    if (w > room && w > 0) size *= room / w;
                    text(g, part, AREA_X + 5, AREA_Y + y, size, Align.LEFT, 0xffffffff);
                }
                y += 10;
            }
        }
        void key(int key) {
            if (current != inputTask) return;
            if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); }
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { String command = input; input = ""; parse(command); }
        }
        void type(char c) { if (current == inputTask) input += c; }
        private void parse(String command) {
            // 原作は、開発機があるときだけlearnを登録する。
            if (!emergency && !menu.viewer() && command.equals("learn")) develop(DevelopmentSession.ACQUIRE, "dev_begin", "dev_succ", "dev_fail");
            else if (emergency && command.equals("reset")) reset();
            else enqueue(print(localized("invalid_command")));
        }
        /** 原作のresetのコマンド: DevelopActionReset.canResetが成り立つときだけ。それ以外は理由を表示する。 */
        private void reset() {
            if ((menu.availability() & DevelopmentSession.availabilityBit(DevelopmentSession.RESET)) != 0)
                develop(DevelopmentSession.RESET, "reset_begin", "reset_succ", "reset_fail");
            else enqueue(print(localized(menu.tier() != DeveloperTier.ADVANCED ? "reset_fail_dev" : "reset_fail_other")));
        }
        private void develop(int action, String begin, String success, String failure) {
            enqueue(print(localized(begin)));
            enqueue(print(localized("progress", "00")));
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            enqueue(new Task() {
                boolean seen; int waited;
                @Override boolean finished() {
                    int state = menu.state();
                    if (state == DevelopmentSession.DEVELOPING) seen = true;
                    return (seen && state != DevelopmentSession.DEVELOPING) || (!seen && ++waited > 40);
                }
                @Override void update() {
                    int percent = menu.total() == 0 ? 0 : menu.elapsed() * 100 / menu.total();
                    output("\b\b\b" + (percent < 10 ? "0" + percent : String.valueOf(Math.min(99, percent))) + "%");
                }
                @Override void finish() {
                    output("\n");
                    output(seen && menu.state() == DevelopmentSession.DONE ? localized(success) : localized(failure));
                    pause(.5);
                    enqueue(new Task() { @Override void begin() { rebuild(); } @Override boolean finished() { return true; } });
                }
            });
        }
        void enqueue(Task task) {
            tasks.add(task);
            if (current == inputTask) { output(input + "\n"); input = ""; current = null; }
        }
        void pause(double seconds) { enqueue(timed(seconds, "", false)); }
        void animSequence(double seconds, List<String> parts) {
            for (int i = 0; i < parts.size(); i++) enqueue(timed(seconds, parts.get(i), i != parts.size() - 1));
        }
        /** 原作の出力: \bで1文字消し、\nで改行し、最大10行を保つ。 */
        void output(String content) {
            var current = new StringBuilder(outputs.isEmpty() ? "" : outputs.removeLast());
            for (char c : content.toCharArray()) {
                if (c == '\b') current.setLength(Math.max(0, current.length() - 1));
                else if (c == '\n') { outputs.addLast(current.toString()); current = new StringBuilder(); }
                else current.append(c);
            }
            outputs.addLast(current.toString());
            while (outputs.size() > 10) outputs.removeFirst();
        }
        private Task print(String text) { return new Task() { @Override void begin() { output(text); } @Override boolean finished() { return true; } }; }
        /** 原作slowPrintTask: 1文字0.01秒。 */
        private Task slowPrint(String text) {
            return new Task() {
                int idx; double last;
                @Override void begin() { last = now(); }
                @Override void update() {
                    int n = (int) ((now() - last) / .01);
                    if (n > 0) { int end = Math.min(text.length(), idx + n); output(text.substring(idx, end)); last += n * .01; idx = end; }
                }
                @Override boolean finished() { return idx == text.length(); }
            };
        }
        private Task timed(double seconds, String text, boolean erase) {
            return new Task() {
                double start;
                @Override void begin() { start = now(); output(text); }
                @Override boolean finished() { return now() - start >= seconds; }
                @Override void finish() { if (erase) output("\b".repeat(text.length())); }
            };
        }
        private String localized(String key, Object... args) { return I18n.get("academy.developer.console." + key, args).replace("\\n", "\n"); }
    }
    abstract static class Task {
        void begin() { }
        void update() { }
        void finish() { }
        abstract boolean finished();
    }

    // ---- テスト用の入口 ----
    /** 技能のnodeの画面上の位置（実際のクリック用）。木に無いときはnull。 */
    @Nullable public float[] nodeCentre(ResourceLocation skill) {
        for (var node : nodes()) if (node.skill().equals(skill))
            return new float[] {ox + (nodeX(node) + WIDGET / 2) * scale, oy + (nodeY(node) + WIDGET / 2) * scale};
        return null;
    }
    @Nullable public float[] viewButtonCentre() {
        if (view == null || !view.buttonVisible) return null;
        var box = view.buttonBox(); return new float[] {box[0] + box[2] / 2, box[1] + box[3] / 2};
    }
    @Nullable public float[] upgradeCentre() {
        return canUpgrade() ? new float[] {ox + (UPGRADE_X + UPGRADE_W / 2) * scale, oy + (UPGRADE_Y + UPGRADE_H / 2) * scale} : null;
    }
    @Nullable public ResourceLocation viewSkill() { return view == null || view.node == null ? null : view.node.skill(); }
    public boolean viewOpen() { return view != null; }
    public boolean viewLevel() { return view != null && view.level; }
    @Nullable public String viewMessage() { return view == null ? null : view.message; }
    public boolean viewClosable() { return view != null && view.canClose; }
    public boolean consoleReady() { return console != null && console.idle(); }
    public boolean consoleShown() { return console != null; }
    public List<String> consoleLines() { return console == null ? List.of() : List.copyOf(console.outputs); }
    public float pageScale() { return scale; }
}
