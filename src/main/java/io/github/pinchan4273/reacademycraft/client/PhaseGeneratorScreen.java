package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.PhaseGeneratorMenu;
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
 * 原作GuiPhaseGen: TechUIのインベントリページにui_phasegen。InfoAreaにはエネルギー（ENERGY）とタンクの
 * ヒストグラム。タンクは原作どおり独自の紫（#b983fb）でIFと表示し、mB単位で示す。発電機の状態とスロットの説明は
 * ツールチップで出す: ページボタンと空のスロット。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhaseGeneratorScreen extends TechScreen<PhaseGeneratorMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_phasegen");
    public PhaseGeneratorScreen(PhaseGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        enableWireless(menu.pos, false);
        histogram(energy(() -> menu.milliIF() / 1000.0, PhaseGeneratorBlockEntity.MAX_MILLI_IF / 1000.0),
                new Hist(() -> "IF", 0xffb983fb, () -> menu.phaseMB() / (double) PhaseGeneratorBlockEntity.TANK_MB,
                        () -> String.format(Locale.ROOT, "%d mB", menu.phaseMB())));
    }
    public Component statusText() { return Component.translatable("academy.phase_gen.status." + menu.status().name().toLowerCase(Locale.ROOT)); }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) { e.enqueueWork(() -> MenuScreens.register(AcademyContent.PHASE_GEN_MENU.get(), PhaseGeneratorScreen::new)); }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        DeveloperScreen.quad(g.pose(), BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
    }
    @Override protected List<Component> pageTooltip() {
        return List.of(title, statusText(), Component.translatable("academy.phase_gen.tank", menu.phaseMB()));
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < 3)
            g.renderTooltip(font, Component.translatable("academy.phase_gen.slot." + hoveredSlot.index), mx, my);
    }
}
