package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.ImagFusorMenu;
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
 * 原作GuiImagFusor（guis/rework/page_imagfusor.xml）: ui_imagfusorを使うTechUIのページ。作業の上に、今のレシピが使う液体、
 * またはIDLEを表示する（text_imagneeded、(68, 12)に44×12、大きさ12、alpha 0.8）。作業のprogress_fusorのバーは右へ伸びる
 * （61×15、中央、46.5下）。InfoAreaには、エネルギーと液体のヒストグラム。状態・タンク・枠についてのこの移植の文言は、
 * ページのボタンと空の枠のtooltipに出す。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ImagFusorScreen extends TechScreen<ImagFusorMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_imagfusor"), PROGRESS = gui("progress/progress_fusor");
    static final float BAR_W = 60.96875f, BAR_H = 15, BAR_X = (PAGE_W - BAR_W) / 2 + .1875f, BAR_Y = 46.5f;
    public ImagFusorScreen(ImagFusorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        enableWireless(menu.pos, false);
        histogram(energy(() -> menu.milliIF() / 1000.0, ImagFusorBlockEntity.MAX_MILLI_IF / 1000.0),
                liquid(menu::phaseMB, ImagFusorBlockEntity.TANK_MB));
    }
    public Component statusText() {
        return Component.translatable("academy.imag_fusor.status." + menu.status().name().toLowerCase(Locale.ROOT));
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(AcademyContent.IMAG_FUSOR_MENU.get(), ImagFusorScreen::new));
    }
    /** 原作getCurrentRecipeのconsumeLiquid（作業中）。それ以外はIDLE。clientは、機械と同じく入力にある物からレシピを探す。 */
    public String needed() {
        if (menu.status() != ImagFusorBlockEntity.Status.WORKING || minecraft == null || minecraft.level == null) return "IDLE";
        var input = menu.getSlot(0).getItem();
        return io.github.pinchan4273.reacademycraft.crafting.ImagFusionRecipe.find(minecraft.level, input)
                .map(r -> String.valueOf(r.liquid())).orElse("IDLE");
    }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        var pose = g.pose();
        DeveloperScreen.quad(pose, BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
        TerminalHud.text(g, font, needed(), 68, 12, 44, 12, 12, 1, 1, 204);
        float p = Math.max(0, Math.min(1, menu.progress() / (float) ImagFusorBlockEntity.WORK_TICKS));
        if (p > 0) DeveloperScreen.quad(pose, PROGRESS, BAR_X, BAR_Y, BAR_W * p, BAR_H, 0, 0, p, 1, 1, 1, 1, 1);
    }
    @Override protected List<Component> pageTooltip() {
        return List.of(title, statusText(), Component.translatable("academy.imag_fusor.tank", menu.phaseMB()));
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < 5)
            g.renderTooltip(font, Component.translatable("academy.imag_fusor.slot." + hoveredSlot.index), mx, my);
    }
}
