package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作RailgunのMSG_CHARGE_EFFECT: コインが上がったとき（術者だけには、鉄の溜めが始まったときも）、原作RailgunHandEffectの
 * 火花がその手で弾けるプレイヤー。
 */
public record RailgunHandEffect(int player) {
    /** 原作TargetPoints.convert(player, 30)。 */
    public static final double RANGE = 30;
    public void encode(FriendlyByteBuf b) { b.writeVarInt(player); }
    public static RailgunHandEffect decode(FriendlyByteBuf b) { return new RailgunHandEffect(b.readVarInt()); }
    /** 術者へ。{@code around}なら、術者から30ブロック以内のすべてのプレイヤーへも。 */
    public static void send(ServerPlayer caster, boolean around) {
        var packet = new RailgunHandEffect(caster.getId());
        if (caster.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> caster), packet);
        if (!around) return;
        for (var player : caster.serverLevel().players())
            if (player != caster && player.distanceToSqr(caster) <= RANGE * RANGE && player.connection != null)
                AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
