package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作BloodRetrogradeのクライアントへのMSG_PERFORM: 誰が誰を打ったか。各クライアントは対象の周りに血を飛ばし、
 * 術者の視線に合わせて周囲のブロックへ吹き付ける。
 */
public record BloodRetroEffect(int caster, int target) {
    public static final double RANGE = 64;
    public void encode(FriendlyByteBuf b) { b.writeVarInt(caster); b.writeVarInt(target); }
    public static BloodRetroEffect decode(FriendlyByteBuf b) { return new BloodRetroEffect(b.readVarInt(), b.readVarInt()); }
    /** 術者と、その近くのすべてのプレイヤーへ。 */
    public static void send(ServerPlayer caster, Entity target) {
        var packet = new BloodRetroEffect(caster.getId(), target.getId());
        if (caster.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> caster), packet);
        for (var player : caster.serverLevel().players())
            if (player != caster && player.distanceToSqr(caster) <= RANGE * RANGE && player.connection != null)
                AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
