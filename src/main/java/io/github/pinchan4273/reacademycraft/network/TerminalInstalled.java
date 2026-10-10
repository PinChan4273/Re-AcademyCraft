package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 原作ItemTerminalInstallerの"install"メッセージ: 端末をインストールしたばかりのプレイヤーに、TerminalInstallEffectを
 * 再生するようサーバーが伝える。中身は何も無い。
 */
public record TerminalInstalled() {
    public void encode(FriendlyByteBuf b) { }
    public static TerminalInstalled decode(FriendlyByteBuf b) { return new TerminalInstalled(); }
}
