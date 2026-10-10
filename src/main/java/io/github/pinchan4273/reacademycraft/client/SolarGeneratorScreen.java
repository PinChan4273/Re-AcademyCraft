package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.SolarGeneratorMenu;
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
 * 原作GuiSolarGen（guis/rework/page_solar.xml）: TechUIのページで、機械の層はui_windbase（原作自身の選択）。
 * anim_frame（104x70、0.6倍、(56, 23)）には太陽を表すeffect_solarの3分の1を表示する: 上が強、中が停止、下が弱。
 * InfoAreaにはバッファのヒストグラム、情報行、発電速度。
 *
 * 2ページ目は原作の無線ページ（WirelessPage.userPage）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SolarGeneratorScreen extends TechScreen<SolarGeneratorMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_windbase"), EFFECT = gui("effect/effect_solar");
    public SolarGeneratorScreen(SolarGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        enableWireless(menu.pos, false);
        histogram(buffer(() -> menu.milliIF() / 1000.0, SolarGeneratorBlockEntity.MAX_MILLI_IF / 1000.0));
        seplineInfo();
        property("gen_speed", () -> String.format(Locale.ROOT, "%.2fIF/T", generation(menu.status())));
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(() -> MenuScreens.register(AcademyContent.SOLAR_MENU.get(), SolarGeneratorScreen::new)); }
    /** 各状態で1tickあたりにパネルが作る量（SolarGeneratorBlockEntityと同じ）。 */
    public static double generation(int status) { return switch (status) { case 1 -> .6; case 2 -> 3; default -> 0; }; }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        DeveloperScreen.quad(g.pose(), BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
        double v = switch (menu.status()) { case 2 -> 0; case 1 -> 2 / 3.0; default -> 1 / 3.0; };
        DeveloperScreen.quad(g.pose(), EFFECT, 56, 23, 104 * .6f, 70 * .6f, 0, (float) v, 1, (float) (v + 1 / 3.0), 1, 1, 1, 1);
    }
}
