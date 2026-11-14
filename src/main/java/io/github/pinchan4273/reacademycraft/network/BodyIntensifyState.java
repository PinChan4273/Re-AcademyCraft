package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 術者自身のクライアントへ、Body Intensifyの状態（オフ・準備中・有効）とそのスロットを伝える。これにより、画面が開いたら
 * 準備を、ウィンドウがフォーカスを失ったら動作中のものを終えられる。何も与えない: 決めるのはサーバーだけ。
 * 1.5.2（通信の版71）: スロットは0〜3か、パッシブの自動の起動の{@link io.github.pinchan4273.reacademycraft.skill.PassiveSkills#SLOT}（-1）。
 * 符号つきの1バイトで送る（0〜3は前と同じバイト）。
 */
public record BodyIntensifyState(int phase, int slot) {
    public static final int OFF = 0, WARMUP = 1, ACTIVE = 2;
    public BodyIntensifyState {
        if (phase < OFF || phase > ACTIVE || (slot != io.github.pinchan4273.reacademycraft.skill.PassiveSkills.SLOT && (slot < 0 || slot >= 4)))
            throw new IllegalArgumentException("Invalid Body Intensify state");
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(phase); buffer.writeByte(slot); }
    public static BodyIntensifyState decode(FriendlyByteBuf buffer) { return new BodyIntensifyState(buffer.readUnsignedByte(), buffer.readByte()); }
    public static void send(net.minecraft.server.level.ServerPlayer player, int phase, int slot) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new BodyIntensifyState(phase, slot));
    }
}
