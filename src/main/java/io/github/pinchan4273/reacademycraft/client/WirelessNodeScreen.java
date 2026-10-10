package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.NodeRename;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.menu.WirelessNodeMenu;
import java.util.List;
import net.minecraft.Util;
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
 * 原作GuiNode: TechUIのインベントリページにui_node、nodeの2つのエネルギーアイテムスロット、アニメーション
 * （effect_nodeの10フレーム、186x75を半分の倍率で(42, 35.5)に、明滅あり）: ネットワーク上なら8フレームを各0.8秒、
 * そうでなければ2フレームを各3秒。2ページ目は原作のnodePage（参加できるネットワーク）。InfoAreaにはエネルギーと負荷の
 * ヒストグラム、範囲、所有者、名前、パスワード。最後の2つは設置者だけが編集でき、MSG_RENAMEとMSG_CHANGE_PASSを送る
 * （NodeRename、NodePassword）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WirelessNodeScreen extends TechScreen<WirelessNodeMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_node"), EFFECT = gui("effect/effect_node");
    static final int ALL_FRAMES = 10;
    private boolean linked;
    private int frame;
    private long lastChange;
    public WirelessNodeScreen(WirelessNodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        enableWireless(menu.pos, true);
        // nodeの最大値と容量は、画面の生成後にメニューのデータで届く。
        histogram(new Hist(hist("energy"), 0xff25c4ff, () -> menu.storedIF() / (double) menu.maxIF(),
                        () -> String.format(java.util.Locale.ROOT, "%d IF", menu.storedIF())),
                new Hist(hist("capacity"), 0xffff6c00, () -> menu.capacity() == 0 ? 0 : menu.load() / (double) menu.capacity(),
                        () -> menu.load() + "/" + menu.capacity()));
        seplineInfo();
        property("range", () -> String.valueOf((double) menu.range()));
        property("owner", menu::placer);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && menu.mayRename(player)) {
            property("node_name", menu::nodeName, this::rename, false);
            property("password", menu::password, this::changePassword, true);
        } else {
            property("node_name", menu::nodeName);
        }
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(AcademyContent.NODE_MENU.get(), WirelessNodeScreen::new));
    }
    private void rename(String value) { AcademyNetwork.CHANNEL.sendToServer(new NodeRename(menu.pos, value.trim())); }
    private void changePassword(String value) { AcademyNetwork.CHANNEL.sendToServer(new io.github.pinchan4273.reacademycraft.network.NodePassword(menu.pos, value)); }
    // テスト用の入口。
    public void renameTo(String value) { editProperty("node_name", value); }
    public void passwordTo(String value) { editProperty("password", value); }
    public boolean nameEditable() { return propertyEditable("node_name"); }

    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        DeveloperScreen.quad(g.pose(), BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
        // 原作StateContext: 接続時はbegin 0、8フレーム、800ms。未接続時はbegin 8、2フレーム、3000ms。
        boolean now = menu.onNetwork();
        long time = Util.getMillis();
        if (now != linked) { linked = now; frame = 0; lastChange = time; }
        int frames = linked ? 8 : 2, begin = linked ? 0 : 8;
        long frameTime = linked ? 800 : 3000;
        if (time - lastChange >= frameTime) { lastChange = time; frame = (frame + 1) % frames; }
        float v = (begin + frame) / (float) ALL_FRAMES;
        DeveloperScreen.quad(g.pose(), EFFECT, 42, 35.5f, 186 * .5f, 75 * .5f, 0, v, 1, v + 1f / ALL_FRAMES, 1, 1, 1, breathe);
    }
    @Override protected List<Component> pageTooltip() {
        return List.of(title, menu.onNetwork() ? Component.translatable("academy.node.on_network") : Component.translatable("academy.node.off_network"));
    }
}
