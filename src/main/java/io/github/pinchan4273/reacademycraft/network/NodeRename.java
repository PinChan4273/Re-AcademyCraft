package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 原作NodeNetworkProxy.MSG_RENAME: nodeのパネルの名前欄。原作は送り主の名前がnodeの設置者の名前のときだけ名前を変え、
 * ここでも同じ。
 */
public record NodeRename(BlockPos pos, String name) {
    public static final int MAX_LENGTH = 32;
    public NodeRename {
        Objects.requireNonNull(pos); Objects.requireNonNull(name);
        if (name.length() > MAX_LENGTH) throw new IllegalArgumentException("Node name too long");
    }
    public void encode(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeUtf(name, MAX_LENGTH); }
    public static NodeRename decode(FriendlyByteBuf b) { return new NodeRename(b.readBlockPos(), b.readUtf(MAX_LENGTH)); }
    /** このプレイヤーがそのnodeの名前を変えられるか: 原作のplayer.getName == node.getPlacerName。 */
    public static boolean mayRename(ServerPlayer player, WirelessNodeBlockEntity node) {
        return node.placer() != null && node.placer().equals(player.getGameProfile().getName());
    }
    public void handle(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return;
        if (!(level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity node) || node.readOnly()) return;
        if (!mayRename(player, node)) {
            player.displayClientMessage(Component.translatable("academy.node.not_yours"), true); return;
        }
        String trimmed = name.trim();
        if (node.rename(trimmed)) player.displayClientMessage(Component.translatable("academy.node.renamed", node.nodeName()), true);
    }
}
