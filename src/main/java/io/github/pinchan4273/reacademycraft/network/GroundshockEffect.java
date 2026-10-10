package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作GroundshockのクライアントへのMSG_PERFORM: 誰が踏み鳴らしたかと、衝撃が通ったブロック（dejavu_blocks）。
 * 各クライアントはそこに破片と煙を散らし、術者自身の視点は下へ振り下ろす。
 */
public record GroundshockEffect(int caster, List<BlockPos> blocks) {
    public static final int MAX_BLOCKS = 512;
    /**
     * 原作のコンテキストは、技能の開始時に術者から25ブロック以内のプレイヤーへ届く（ContextManager.hBeginLink）。
     * ここではそれを含む64ブロック以内の全員へ、より遠くまで送る。
     */
    public static final double RANGE = 64;
    public GroundshockEffect {
        Objects.requireNonNull(blocks);
        if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Too many blocks");
        blocks = List.copyOf(blocks);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(caster); b.writeVarInt(blocks.size());
        for (var pos : blocks) b.writeBlockPos(pos);
    }
    public static GroundshockEffect decode(FriendlyByteBuf b) {
        int caster = b.readVarInt(), count = b.readVarInt();
        if (count < 0 || count > MAX_BLOCKS) throw new io.netty.handler.codec.DecoderException("Too many blocks");
        var blocks = new ArrayList<BlockPos>(count);
        for (int i = 0; i < count; i++) blocks.add(b.readBlockPos());
        return new GroundshockEffect(caster, blocks);
    }
    /** 術者と、その近くのすべてのプレイヤーへ。 */
    public static void send(ServerPlayer caster, java.util.Collection<BlockPos> blocks) {
        var list = new ArrayList<BlockPos>(Math.min(blocks.size(), MAX_BLOCKS));
        for (var pos : blocks) { if (list.size() >= MAX_BLOCKS) break; list.add(pos.immutable()); }
        var packet = new GroundshockEffect(caster.getId(), list);
        for (var player : caster.serverLevel().players())
            if ((player == caster || player.distanceToSqr(caster) <= RANGE * RANGE) && player.connection != null)
                AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        if (!caster.serverLevel().players().contains(caster) && caster.connection != null)
            AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> caster), packet);
    }
}
