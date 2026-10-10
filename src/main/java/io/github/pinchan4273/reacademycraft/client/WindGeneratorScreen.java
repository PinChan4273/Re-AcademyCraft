package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.WindGeneratorRules;
import io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu;
import io.github.pinchan4273.reacademycraft.world.menu.WindMenuAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
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
 * TechUI上の原作GuiWindGenBaseとGuiWindGenMain。
 * - 土台（guis/rework/page_windbase.xml）: ui_windbaseと、発電機の3つの24四方のアイコン（上から13、31、49の位置に中央揃え。
 *   上部・柱・土台）を、完成度に応じて0.2、0.6、1で描く。InfoAreaには原作のバッファのヒストグラム、情報行、高度。
 *   2ページ目は原作の無線ページ（WirelessPage.userPage）。
 * - 上部（InventoryPage("windmain")）: ui_windmainとファンのスロット。InfoAreaには高度。
 * 原作が絵に任せていること（止まっている理由、柱、上部の高度）は、移植版がインベントリページのボタンで説明する。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WindGeneratorScreen extends TechScreen<WindGeneratorMenu> {
    /** InfoAreaの幅（原作の100）。 */
    public static final int INFO_WIDTH = (int) INFO_W;
    static final ResourceLocation BASE = gui("ui/ui_windbase"), MAIN = gui("ui/ui_windmain"),
            BASE_ICON = gui("icons/icon_wind_base"), PILLAR_ICON = gui("icons/icon_wind_middle"), MAIN_ICON = gui("icons/icon_wind_main");
    public WindGeneratorScreen(WindGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        if (!menu.main) {
            enableWireless(menu.pos, false);
            histogram(buffer(() -> menu.energyUnits() / 12.0, WindGeneratorRules.CAPACITY_UNITS / 12.0));
            seplineInfo();
        }
        property("altitude", () -> Integer.toString(menu.altitude()));
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(AcademyContent.WIND_BASE_MENU.get(), WindGeneratorScreen::new);
            MenuScreens.register(AcademyContent.WIND_MAIN_MENU.get(), WindGeneratorScreen::new);
        });
    }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        var pose = g.pose();
        DeveloperScreen.quad(pose, menu.main ? MAIN : BASE, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
        if (menu.main) return;
        // 原作の完成度: (上部, 柱, 土台)をa0 0.2、a1 0.6、a2 1で。
        float top = .2f, middle = .2f, base = 1;
        switch (menu.status()) {
            case COMPLETE -> { top = 1; middle = 1; }
            case COMPLETE_NOT_WORKING -> { top = .6f; middle = 1; }
            case NO_TOP -> middle = 1;
            case INVALID_BASE, UNAVAILABLE -> base = .2f;
            default -> { }
        }
        float x = (PAGE_W - 24) / 2f;
        DeveloperScreen.quad(pose, MAIN_ICON, x, 13, 24, 24, 1, 1, 1, top);
        DeveloperScreen.quad(pose, PILLAR_ICON, x, 31, 24, 24, 1, 1, 1, middle);
        DeveloperScreen.quad(pose, BASE_ICON, x, 49, 24, 24, 1, 1, 1, base);
    }
    @Override protected List<Component> pageTooltip() { return informationLines(); }

    public int contentLeft() { return leftPos; }
    public int contentTop() { return topPos; }
    /** ページの横に固定幅100で置く原作のInfoAreaが、この画面の大きさで画面内に収まるか。 */
    public boolean infoPanelVisible() { return leftPos + INFO_X + INFO_W + 4 <= width; }
    public boolean infoAnimationComplete() { return infoSettled(); }
    public int informationHeight(Font font) { return Math.round(expectedInfoHeight()); }
    /** 原作のヒストグラムは土台のもので、上部には無い。 */
    public boolean histogramFits(Font font) { return !menu.main; }
    /** インベントリページのボタンに出す、発電機の状態の説明（移植版独自）。 */
    public List<Component> informationLines() {
        var lines = new ArrayList<Component>(); lines.add(title); lines.add(statusText());
        if (!menu.main) lines.add(Component.translatable("academy.wind.energy", String.format(Locale.ROOT, "%.2f", menu.energyUnits() / 12.0)));
        lines.add(Component.translatable("academy.wind.altitude." + (menu.main ? "main" : "base"), menu.altitude()));
        if (!menu.main) {
            String altitude = menu.mainAltitude() == WindMenuAccess.NO_MAIN ? "—" : Integer.toString(menu.mainAltitude());
            lines.add(Component.translatable("academy.wind.generation_altitude", altitude));
            lines.add(Component.translatable("academy.wind.pillars", menu.pillars()));
        }
        lines.add(Component.translatable("academy.wind.slot." + (menu.main ? "fan" : "charge")));
        return lines;
    }
    public Component statusText() {
        var observed = menu.status();
        if (!menu.main && (observed == io.github.pinchan4273.reacademycraft.world.WindStructureProbe.Status.BASE_ONLY
                || observed == io.github.pinchan4273.reacademycraft.world.WindStructureProbe.Status.NO_TOP)
                && !WindGeneratorRules.validPillars(menu.pillars()))
            return Component.translatable("academy.wind.status.pillar_count", menu.pillars(),
                    WindGeneratorRules.MIN_PILLARS, WindGeneratorRules.MAX_PILLARS);
        String status = menu.main ? "fan." + (menu.fanInstalled() ? "installed" : "missing")
                : "status." + menu.status().name().toLowerCase(Locale.ROOT);
        return Component.translatable("academy.wind." + status);
    }
}
