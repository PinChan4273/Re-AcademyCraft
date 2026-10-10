package io.github.pinchan4273.reacademycraft.client;

import net.minecraft.client.Minecraft;

/** 原作の端末キー。端末自体はゲームの上のパネルであるTerminalHudで、AboutアプリはAboutScreen。 */
public final class TerminalScreen {
    private TerminalScreen() { }
    /** まだ開いていなければ、プレイ中のプレイヤーの端末を開く。 */
    public static void open() {
        if (Minecraft.getInstance().player != null && !TerminalHud.isOpen()) TerminalHud.toggle();
    }
}
