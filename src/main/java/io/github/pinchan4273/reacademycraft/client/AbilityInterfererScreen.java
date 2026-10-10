package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.InterfererWhitelist;
import io.github.pinchan4273.reacademycraft.network.InterfererWhitelistSync;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.Util;
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
import org.lwjgl.glfw.GLFW;

/**
 * 原作GuiAbilityInterferer（guis/rework/page_interfere.xml）をTechUIのインベントリのページに置いたもの。
 * ui_interfereの絵には、電池の枠とホットバーだけがある。ページの座標で:
 * - panel_config（160×32、中央、25下）: 「Switch:」と、(48, 25)のbutton_switch_onまたは_off
 *   （OFFのときは0.6に暗くする）。「Range:」と、(48, 41)・(108, 41)の矢印（10から100まで10ずつ変える）と、その間の範囲。
 * - panel_whitelist（160×80、中央、中央から28下）: (12, 81.5)と(32, 81.5)に追加と削除、右側に上下。
 *   (3, 94.5)から150×16の行が4つあり、それぞれ名前とicon_whitelist_singleを表示する（選択中以外は0.7）。
 *   追加を押すと(58, 86.5)に40×10の入力欄を開く。Enterで入力した名前を加え、他をクリックすると取り消す。
 *   削除は選択中の名前を外す。
 * 変更はすべてサーバーへ送り、サーバーが決める（スイッチと範囲はmenuのボタン、一覧は原作の
 * set_whitelistと同じく丸ごと）。パネルはサーバーから返ってきた内容を表示する。
 * InfoAreaには原作のエネルギーのヒストグラムを置く。2ページ目は原作の無線のページ（WirelessPage.userPage）。
 * 原作のwhitelistは、誰も干渉から除外しない。原作と同じく、保持・表示・編集だけを行う。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AbilityInterfererScreen extends TechScreen<AbilityInterfererMenu> {
    static final ResourceLocation BLOCK = gui("ui/ui_interfere"), SWITCH_ON = gui("button/button_switch_on"),
            SWITCH_OFF = gui("button/button_switch_off"), ADD = gui("button/button_add"), REMOVE = gui("button/button_remove"),
            LEFT = gui("button/button_arrowlefta"), RIGHT = gui("button/button_arrowrighta"), SINGLE = gui("icons/icon_whitelist_single");
    static final float SWITCH_X = 48, SWITCH_Y = 25, RANGE_Y = 41, SHRINK_X = 48, GROW_X = 108,
            ADD_X = 12, REMOVE_X = 32, BAR_Y = 81.5f, ARROWS_X = 152, UP_Y2 = 94.5f, DOWN_Y2 = 144.5f,
            ROWS_X = 3, ROWS_Y = 94.5f, ROW_W2 = 150, ROW_H2 = 16, BOX_X = 58, BOX_Y = 86.5f, BOX_W = 40, BOX_H = 10;
    public static final int ROWS = 4;
    private int scroll;
    @Nullable private String chosen;
    /** 入力欄を開いている間の文字。開いていないときはnull。 */
    @Nullable private TextEdit typing;

    public AbilityInterfererScreen(AbilityInterfererMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        enableWireless(menu.pos, false);
        histogram(energy(() -> menu.milliIF() / 1000.0, AbilityInterfererBlockEntity.MAX_MILLI_IF / 1000.0));
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(AcademyContent.INTERFERER_MENU.get(), AbilityInterfererScreen::new));
    }
    /** サーバーから送られてくる一覧を受け取るclient側の処理。 */
    public static void receive(InterfererWhitelistSync packet) {
        packet.list().accept(Minecraft.getInstance().player);
    }
    public Component switchText() {
        return Component.translatable(menu.enabled() ? "academy.interferer.on" : "academy.interferer.off");
    }
    private void press(int button) {
        var client = Minecraft.getInstance();
        client.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
    /** 原作の入力欄の確定: 入力した名前を一覧に加える。空なら何もしない。 */
    public void addName(String name) {
        name = name.trim();
        if (name.isEmpty()) return;
        var names = new TreeSet<>(menu.whitelist()); names.add(name);
        send(names);
    }
    /** 原作btn_remove: 選択中の名前を一覧から外す。 */
    public void removeChosen() {
        if (chosen == null) return;
        var names = new TreeSet<>(menu.whitelist()); names.remove(chosen); chosen = null;
        send(names);
    }
    public void choose(@Nullable String name) { chosen = name; }
    @Nullable public String chosen() { return chosen; }
    public boolean typingName() { return typing != null; }
    private void send(Collection<String> names) {
        AcademyNetwork.CHANNEL.sendToServer(new InterfererWhitelist(menu.containerId, List.copyOf(names)));
    }
    private int maxScroll() { return Math.max(0, menu.whitelist().size() - ROWS); }

    @Override protected void containerTick() {
        super.containerTick();
        if (chosen != null && !menu.whitelist().contains(chosen)) chosen = null;
        scroll = Math.min(scroll, maxScroll());
    }
    /** 原作はpage_interfere自身の窓をui_inventory無しで渡す。ホットバーはui_interfereが描く。 */
    @Override protected boolean inventoryLayer() { return false; }
    @Override protected List<Component> pageTooltip() {
        return List.of(title, switchText(), Component.translatable("academy.interferer.range", menu.range()),
                Component.translatable("academy.interferer.cost", menu.range() * menu.range()));
    }
    @Override protected void renderPage(GuiGraphics g, float breathe, int mx, int my) {
        var pose = g.pose();
        DeveloperScreen.quad(pose, BLOCK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, breathe);
        text(g, Component.translatable("academy.interferer.switch").getString(), 8, 27, 10, 1, 1, 40);
        float lum = menu.enabled() ? 1 : .6f;
        DeveloperScreen.quad(pose, menu.enabled() ? SWITCH_ON : SWITCH_OFF, SWITCH_X, SWITCH_Y, 16, 16, lum, lum, lum, 1);
        text(g, Component.translatable("academy.interferer.range_label").getString(), 8, RANGE_Y + 2, 10, 1, 1, 40);
        DeveloperScreen.quad(pose, LEFT, SHRINK_X, RANGE_Y, 16, 16, 1, 1, 1, tint(mx, my, SHRINK_X, RANGE_Y, 16, 16));
        DeveloperScreen.quad(pose, RIGHT, GROW_X, RANGE_Y, 16, 16, 1, 1, 1, tint(mx, my, GROW_X, RANGE_Y, 16, 16));
        // 原作は範囲をDoubleで表示する: "10.0"。
        String range = String.valueOf((double) menu.range());
        text(g, range, 64 + (44 - font.width(range) * 10 / 9f) / 2, RANGE_Y + 3, 10, 1, 1, 44);
        DeveloperScreen.quad(pose, ADD, ADD_X, BAR_Y, 12, 12, 1, 1, 1, tint(mx, my, ADD_X, BAR_Y, 12, 12));
        DeveloperScreen.quad(pose, REMOVE, REMOVE_X, BAR_Y, 12, 12, 1, 1, 1, tint(mx, my, REMOVE_X, BAR_Y, 12, 12));
        DeveloperScreen.quad(pose, UP, ARROWS_X, UP_Y2, 16, 16, 1, 1, 1, tint(mx, my, ARROWS_X, UP_Y2, 16, 16));
        DeveloperScreen.quad(pose, DOWN, ARROWS_X, DOWN_Y2, 16, 16, 1, 1, 1, tint(mx, my, ARROWS_X, DOWN_Y2, 16, 16));
        var names = menu.whitelist();
        for (int i = scroll; i < Math.min(names.size(), scroll + ROWS); i++) {
            float y = ROWS_Y + (i - scroll) * ROW_H2;
            String name = names.get(i);
            DeveloperScreen.quad(pose, ELEMENT, ROWS_X, y, ROW_W2, ROW_H2, 1, 1, 1, name.equals(chosen) ? 1 : .7f);
            DeveloperScreen.quad(pose, SINGLE, ROWS_X + 10, y + 2, 12, 12, 1, 1, 1, 1);
            text(g, name, ROWS_X + 30, y + 3, 10, 1, 1, 110);
        }
        if (typing != null) {
            DeveloperScreen.rect(pose, BOX_X, BOX_Y, BOX_W, BOX_H, 1, 1, 1, 50 / 255f);
            text(g, typing.shown(false), BOX_X, BOX_Y, 10, 1, 1, BOX_W);
        }
    }
    /** 原作Tint: 通常は255のうち178、マウスの下では最大。 */
    private static float tint(int mx, int my, float x, float y, float w, float h) { return in(mx, my, x, y, w, h) ? 1 : 178 / 255f; }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        double x = mx - leftPos, y = my - topPos;
        if (page() != 0 || button != 0) return super.mouseClicked(mx, my, button);
        // 原作LostFocusEvent: 入力欄以外をクリックすると、入力した文字を取り消す。
        boolean inBox = typing != null && in(x, y, BOX_X, BOX_Y, BOX_W, BOX_H);
        if (!inBox) typing = null;
        if (inBox) return true;
        if (in(x, y, SWITCH_X, SWITCH_Y, 16, 16)) { press(AbilityInterfererMenu.BUTTON_TOGGLE); return true; }
        if (in(x, y, SHRINK_X, RANGE_Y, 16, 16)) { press(AbilityInterfererMenu.BUTTON_SHRINK); return true; }
        if (in(x, y, GROW_X, RANGE_Y, 16, 16)) { press(AbilityInterfererMenu.BUTTON_GROW); return true; }
        if (in(x, y, ADD_X, BAR_Y, 12, 12)) { typing = new TextEdit("", AbilityInterfererBlockEntity.MAX_NAME); return true; }
        if (in(x, y, REMOVE_X, BAR_Y, 12, 12)) { removeChosen(); return true; }
        if (in(x, y, ARROWS_X, UP_Y2, 16, 16)) { scroll = Math.max(0, scroll - 1); return true; }
        if (in(x, y, ARROWS_X, DOWN_Y2, 16, 16)) { scroll = Math.min(maxScroll(), scroll + 1); return true; }
        var names = menu.whitelist();
        for (int i = scroll; i < Math.min(names.size(), scroll + ROWS); i++)
            if (in(x, y, ROWS_X, ROWS_Y + (i - scroll) * ROW_H2, ROW_W2, ROW_H2)) { chosen = names.get(i); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean charTyped(char c, int modifiers) {
        if (typing != null) {
            typing.type(c);
            return true;
        }
        return super.charTyped(c, modifiers);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (typing != null) {
            if (typing.key(key)) return true;
            // 原作ConfirmInputEvent: 名前を一覧に加え、入力欄を閉じる。
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { addName(typing.text()); typing = null; return true; }
            // 原作CGuiScreenContainerは、フォーカスのある部品へEscapeを渡す: 名前を取り消し、パネルも閉じる。
            if (key == GLFW.GLFW_KEY_ESCAPE) { typing = null; return super.keyPressed(key, scan, modifiers); }
            // 名前の入力中に、インベントリのキーでパネルが閉じないようにする。
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (page() == 0 && hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index == AbilityInterfererBlockEntity.SLOT_BATTERY)
            g.renderTooltip(font, Component.translatable("academy.interferer.slot.0"), mx, my);
    }

    // テスト用の入口: パネル自身の当たり判定を通して、ページのある点を左クリックする。
    public boolean clickPage(float x, float y) { return mouseClicked(leftPos + x, topPos + y, 0); }
}
