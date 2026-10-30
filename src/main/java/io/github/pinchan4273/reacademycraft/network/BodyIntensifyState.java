package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 術者自身のクライアントへ、Body Intensifyの状態（オフ・準備中・有効）とそのスロットを伝える。これにより、画面が開いたら
 * 準備を、ウィンドウがフォーカスを失ったら動作中のものを終えられる。何も与えない: 決めるのはサーバーだけ。
 */
public record BodyIntensifyState(int phase, int slot) implements AcademyPayload {
    public static final int OFF = 0, WARMUP = 1, ACTIVE = 2;
    public BodyIntensifyState {
        if (phase < OFF || phase > ACTIVE || slot < 0 || slot >= 4) throw new IllegalArgumentException("Invalid Body Intensify state");
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(phase); buffer.writeByte(slot); }
    public static BodyIntensifyState decode(FriendlyByteBuf buffer) { return new BodyIntensifyState(buffer.readUnsignedByte(), buffer.readUnsignedByte()); }
    public static void send(net.minecraft.server.level.ServerPlayer player, int phase, int slot) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new BodyIntensifyState(phase, slot));
    }
}
