package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作DeveloperUIのMSG_GET_NODE: 開発機が接続している無線nodeの名前。無ければ""で、パネルはN/Aと表示する。
 * 原作と同じく、パネルを開いたときに1回問い合わせる。開発機の位置も運び、パネルの無線ボタンはそれを無線ページへ問い合わせる。
 */
public record DeveloperNode(int containerId, BlockPos developer, String name) {
    public static final int MAX_NAME = 64;
    public void encode(FriendlyByteBuf b) { b.writeVarInt(containerId); b.writeBlockPos(developer); b.writeUtf(name, MAX_NAME); }
    public static DeveloperNode decode(FriendlyByteBuf b) { return new DeveloperNode(b.readVarInt(), b.readBlockPos(), b.readUtf(MAX_NAME)); }
    /** 原作hGetLinkNodeName: WirelessHelper.getNodeConn(tile)のnodeの名前。 */
    public static String nodeName(ServerLevel level, BlockPos developer) {
        var connection = WirelessNetworks.of(level).connectionOf(developer);
        if (connection == null || !(level.getBlockEntity(connection.node()) instanceof WirelessNodeBlockEntity node)) return "";
        var name = node.nodeName();
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }
    public static void send(ServerPlayer player, int containerId, BlockPos developer) {
        if (player.connection != null)
            AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DeveloperNode(containerId, developer.immutable(), nodeName(player.serverLevel(), developer)));
    }
}
