package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.MetalFormerMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 原作GuiMetalFormer（guis/rework/page_metalformer.xml）: TechUIのページにui_metalformer。モードのアイコン
 * （幅24、中央、4.5下）を、それを切り替える矢印ボタン（幅16、左右20、8.5下）の間に置く。作業のprogress_metalformerの
 * バーは右へ伸びる（57x15、中央、46.5下）。InfoAreaにはエネルギーのヒストグラム。アイコンにマウスを載せると、
 * その上の原作のテキストボックスにモード名を出す（原作のenum名でなく翻訳した名前）。状態・モード・スロットの説明は
 * ツールチップで出す: ページボタン、アイコン、空のスロット。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MetalFormerScreen extends TechScreen<MetalFormerMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_metalformer"), PROGRESS = gui("progress/progress_metalformer"),
            LEFT = gui("button/button_arrowlefta"), RIGHT = gui("button/button_arrowrighta");
    static final float BAR_W = 57, BAR_H = 15, BAR_X = (PAGE_W - BAR_W) / 2, BAR_Y = 46.5f;
    static final float ICON_X = (PAGE_W - 24) / 2f, ICON_Y = 4.5f, LEFT_X = (PAGE_W - 16) / 2f - 20, RIGHT_X = (PAGE_W - 16) / 2f + 20, ARROW_Y = 8.5f;
    public MetalFormerScreen(MetalFormerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        enableWireless(menu.pos, false);
        histogram(energy(() -> menu.milliIF() / 1000.0, MetalFormerBlockEntity.MAX_MILLI_IF / 1000.0));
    }
    public Component statusText() { return Component.translatable("academy.metal.status." + menu.status().name().toLowerCase(Locale.ROOT)); }
    public Component modeText() { return Component.translatable("academy.metal.mode." + menu.mode().name().toLowerCase(Locale.ROOT)); }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) { e.enqueueWork(() -> MenuScreens.register(AcademyContent.METAL_MENU.get(), MetalFormerScreen::new)); }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        var pose = g.pose();
        DeveloperScreen.quad(pose, BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
        float p = Math.max(0, Math.min(1, menu.progress() / (float) MetalFormerBlockEntity.WORK_TICKS));
        if (p > 0) DeveloperScreen.quad(pose, PROGRESS, BAR_X, BAR_Y, BAR_W * p, BAR_H, 0, 0, p, 1, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, gui("icons/icon_former_" + menu.mode().name().toLowerCase(Locale.ROOT)), ICON_X, ICON_Y, 24, 24, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, LEFT, LEFT_X, ARROW_Y, 16, 16, 1, 1, 1, 1);
        DeveloperScreen.quad(pose, RIGHT, RIGHT_X, ARROW_Y, 16, 16, 1, 1, 1, 1);
        if (in(mx, my, ICON_X, ICON_Y, 24, 24)) textBox(g, modeText().getString(), ICON_X + 6, ICON_Y - 10);
    }
    /** 原作TechUI.drawTextBox（中央揃え）: 背後に半透明の黒、文字は大きさ10で#aaffffff。 */
    private void textBox(GuiGraphics g, String text, float x, float y) {
        float s = 10 / 9f, w = font.width(text) * s, h = 9 * s;
        float left = x - w * .5f;
        DeveloperScreen.rect(g.pose(), left, y, w + 12, h + 4, 0, 0, 0, .5f);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left + 5, y + 2, 0);
        pose.scale(s, s, 1);
        g.drawString(font, text, 0, 0, 0xaaffffff, false);
        pose.popPose();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        double x = mx - leftPos, y = my - topPos;
        if (button == 0 && in(x, y, LEFT_X, ARROW_Y, 16, 16)) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0); return true; }
        if (button == 0 && in(x, y, RIGHT_X, ARROW_Y, 16, 16)) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1); return true; }
        return super.mouseClicked(mx, my, button);
    }
    /** テスト用の入口: 原作のページが置く位置で、左または右の矢印をクリックする。 */
    public boolean clickArrow(boolean right) {
        return mouseClicked(leftPos + (right ? RIGHT_X : LEFT_X) + 8, topPos + ARROW_Y + 8, 0);
    }
    @Override protected List<Component> pageTooltip() { return List.of(title, modeText(), statusText()); }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (in(mx - leftPos, my - topPos, ICON_X, ICON_Y, 24, 24))
            g.renderTooltip(font, Component.translatable("academy.metal.hint." + menu.mode().name().toLowerCase(Locale.ROOT)), mx, my + 20);
        else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < 3)
            g.renderTooltip(font, Component.translatable("academy.metal.slot." + hoveredSlot.index), mx, my);
    }
}
