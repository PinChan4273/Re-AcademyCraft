package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.WirelessConfig;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.menu.WirelessMatrixMenu;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
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
 * 原作GuiMatrix2: TechUIのインベントリページにui_matrixと3枚の板・コア。InfoAreaは原作がサーバーの返答（MSG_GATHER_INFO）
 * から組み立て直す: 負荷のヒストグラム、所有者・範囲・帯域、次のいずれか:
 * - ネットワークがあれば、SSIDと（"Change Password"の後に）パスワード。どちらも設置者が編集できる
 *   （MSG_CHANGE_SSID、MSG_CHANGE_PASSWORD）。
 * - 無ければ、設置者には"Wireless Network Initialization"と、入力するSSIDとパスワード、原作のINITボタン（MSG_INIT）。
 *   それ以外の人には"Wireless Network Not Initialized"。
 * 原作と同じく、matrixに無線ページは無い。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WirelessMatrixScreen extends TechScreen<WirelessMatrixMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_matrix");
    @Nullable private String built;
    private boolean lastNetwork;
    private String password;
    public WirelessMatrixScreen(WirelessMatrixMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        password = menu.password();
        watchWireless(menu.pos);
        rebuild();
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(AcademyContent.MATRIX_MENU.get(), WirelessMatrixScreen::new));
    }
    private boolean placer() { var p = Minecraft.getInstance().player; return p != null && menu.isPlacer(p); }
    /** サーバーが最後にこの画面へ伝えたネットワークのSSID。無い、またはまだ伝えられていなければnull。 */
    @Nullable public String ssid() { return wireless() == null || wireless().linked() == null ? null : wireless().linked().name(); }
    private void send(int action, String ssid, String pass) {
        AcademyNetwork.CHANNEL.sendToServer(new WirelessConfig(menu.pos, action, ssid, pass));
        refreshWireless();
    }
    /** 原作rebuildInfo: 依存するものが変わるたびに呼ぶ。 */
    private void rebuild() {
        boolean network = menu.hasNetwork() && ssid() != null;
        String key = network + "|" + ssid() + "|" + placer();
        if (key.equals(built)) return;
        built = key;
        resetInfo();
        histogram(new Hist(hist("capacity"), 0xffff6c00, () -> menu.capacity() == 0 ? 0 : Math.max(0, menu.load()) / (double) menu.capacity(),
                () -> Math.max(0, menu.load()) + "/" + menu.capacity()));
        seplineInfo();
        property("owner", menu::placer);
        property("range", () -> String.format(Locale.ROOT, "%.0f", (double) menu.range()));
        property("bandwidth", () -> menu.bandwidth() + " IF/T");
        if (network) {
            sepline("wireless_info");
            if (placer()) {
                property("ssid", () -> ssid() == null ? "" : ssid(), value -> send(WirelessConfig.RENAME, value.trim(), ""), false);
                sepline("change_pass");
                property("password", () -> password, value -> { password = value; send(WirelessConfig.PASSWORD, "", value); }, true);
            } else {
                property("ssid", () -> ssid() == null ? "" : ssid());
                property("password", () -> "");
            }
        } else if (placer()) {
            sepline("wireless_init");
            property("ssid", () -> "", value -> { }, false);
            property("password", () -> "", value -> { }, true);
            blank(1);
            button("INIT", () -> { password = typed("password"); send(WirelessConfig.CREATE, typed("ssid").trim(), typed("password")); });
        } else {
            sepline("wireless_noinit");
        }
    }
    @Override protected void containerTick() {
        super.containerTick();
        // ネットワークの出現・消滅はメニューが伝える。そのときサーバーへ名前を問い合わせ直す。
        if (menu.hasNetwork() != lastNetwork) { lastNetwork = menu.hasNetwork(); refreshWireless(); }
        rebuild();
    }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        DeveloperScreen.quad(g.pose(), BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
    }
    @Override protected List<Component> pageTooltip() { return List.of(title); }

    // テスト用の入口。
    public void initNetwork(String ssid, String pass) { typeProperty("ssid", ssid); typeProperty("password", pass); pressButton("INIT"); }
    public void renameNetwork(String ssid) { editProperty("ssid", ssid); }
}
