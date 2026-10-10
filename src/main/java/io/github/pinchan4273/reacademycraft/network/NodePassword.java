package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 原作NodeNetworkProxy.MSG_CHANGE_PASS: nodeのパネルのパスワード欄。原作は送り主の名前がnodeの設置者の名前のときだけ
 * 変更し、ここでも同じ。
 */
public record NodePassword(BlockPos pos, String password) {
    public NodePassword {
        Objects.requireNonNull(pos); Objects.requireNonNull(password);
        if (password.length() > WirelessNodeBlockEntity.MAX_PASSWORD) throw new IllegalArgumentException("Node password too long");
    }
    public void encode(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeUtf(password, WirelessNodeBlockEntity.MAX_PASSWORD); }
    public static NodePassword decode(FriendlyByteBuf b) { return new NodePassword(b.readBlockPos(), b.readUtf(WirelessNodeBlockEntity.MAX_PASSWORD)); }
    public void handle(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return;
        if (!(level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity node) || node.readOnly()) return;
        if (!NodeRename.mayRename(player, node)) {
            player.displayClientMessage(Component.translatable("academy.node.not_yours"), true); return;
        }
        if (node.setPassword(password)) player.displayClientMessage(Component.translatable("academy.node.password_set"), true);
    }
}
