package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 術者自身のクライアントへ、Flashingのモードがオンか（移動キーで狙うため）と、瞬間移動が起きたこと（そのクライアントで
 * 原作の重力打ち消しを始める）を伝える。
 */
public record FlashingState(boolean active, boolean flashed) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(active); buffer.writeBoolean(flashed); }
    public static FlashingState decode(FriendlyByteBuf buffer) { return new FlashingState(buffer.readBoolean(), buffer.readBoolean()); }
}
