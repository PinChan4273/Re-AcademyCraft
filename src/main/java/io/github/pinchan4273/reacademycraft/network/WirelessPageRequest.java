package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作TechUIのWirelessPageのメッセージ（WirelessNetDelegate）: 機械やnodeの無線ページが、それが属するブロックについて
 * サーバーへ問い合わせるもの。それぞれWirelessPageReplyで答える。
 *
 * - LIST: 機械なら、接続中のnodeと、範囲がそれを含む他のnode（原作MSG_FIND_NODES）。nodeなら、そのネットワークと
 *   参加できる他のネットワーク（MSG_FIND_NETWORKS）。
 * - CONNECT（nodeまたはmatrix、パスワード）: 原作のLinkUserEventまたはLinkNodeEvent。
 * - DISCONNECT: 原作のUnlinkUserEventまたはUnlinkNodeEvent。
 *
 * matrixに対するLISTは原作のMSG_GATHER_INFO: ネットワークがあれば、それを接続中の項目とする。
 *
 * 原作はクライアントが送るものを何でも信じる。ここでは問い合わせる者が生きていてブロックから8ブロック以内にあり、
 * ブロックがnodeかエネルギーを持つ機械であることを要する。機械ならnodeのパスワードを、nodeならネットワークのパスワードを確かめる。
 */
public record WirelessPageRequest(int op, BlockPos self, BlockPos target, String password) {
    public static final int LIST = 0, CONNECT = 1, DISCONNECT = 2;
    public static final int MAX_PASSWORD = 32;
    /**
     * 答えるたびに機械の周りのブロックを走査するので、プレイヤーの要求はtickごとに上限を設ける。ページは1tickにいくつか送る
     * （変更、その更新、ネットワークの到着時にもう1つ）ので、上限はそれより十分大きくしている。
     */
    public static final int MAX_PER_TICK = 8;
    private static final java.util.Map<ServerPlayer, long[]> REQUESTS = new java.util.WeakHashMap<>();
    public WirelessPageRequest {
        Objects.requireNonNull(self); Objects.requireNonNull(target); Objects.requireNonNull(password);
        if (op < LIST || op > DISCONNECT || password.length() > MAX_PASSWORD) throw new IllegalArgumentException("Invalid wireless page request");
    }
    public void encode(FriendlyByteBuf b) { b.writeByte(op); b.writeBlockPos(self); b.writeBlockPos(target); b.writeUtf(password, MAX_PASSWORD); }
    public static WirelessPageRequest decode(FriendlyByteBuf b) {
        return new WirelessPageRequest(b.readUnsignedByte(), b.readBlockPos(), b.readBlockPos(), b.readUtf(MAX_PASSWORD));
    }
    public void handle(ServerPlayer player) {
        if (player == null) return;
        long now = player.serverLevel().getGameTime();
        long[] seen = REQUESTS.computeIfAbsent(player, ignored -> new long[] {Long.MIN_VALUE, 0});
        if (seen[0] != now) { seen[0] = now; seen[1] = 0; }
        if (++seen[1] > MAX_PER_TICK) return;
        var reply = answer(player);
        if (reply != null && player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
    }
    /** 求められたことを行った後の返答。問い合わせる資格が無ければnull。GameTestが呼ぶ。 */
    @Nullable public WirelessPageReply answer(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return null;
        var level = player.serverLevel();
        if (!near(player, self)) return null;
        boolean node = level.getBlockEntity(self) instanceof WirelessNodeBlockEntity n && !n.readOnly();
        var networks = WirelessNetworks.of(level);
        // matrixのパネル向けの原作MSG_GATHER_INFO: ネットワークの名前と、パスワードがあるか。
        if (level.getBlockEntity(self) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity matrix && !matrix.readOnly()) {
            var network = networks.networkAt(self);
            return new WirelessPageReply(self, false, network == null || !network.matrix().equals(self) ? null
                    : new WirelessPageReply.Entry(self, clip(network.ssid()), !network.passwordIs("")), List.of());
        }
        if (!node && !user(level, self)) return null;
        if (op == CONNECT) {
            if (node) {
                var network = networks.networkAt(target);
                if (network != null && network.matrix().equals(target)) networks.linkNode(level, self, network.ssid(), password);
            } else if (level.hasChunkAt(target) && level.getBlockEntity(target) instanceof WirelessNodeBlockEntity n && !n.readOnly() && n.passwordIs(password)) {
                // 対象はクライアントから来るので、読み込まれている場所でだけ読む。その後linkUserがnode自身の範囲内に収める。
                networks.linkUser(level, target, self);
            }
        } else if (op == DISCONNECT) {
            if (node) networks.unlinkNode(self); else networks.unlinkUser(self);
        }
        return node ? nodeList(level, networks, self) : userList(level, networks, self);
    }
    /** 無線の使用者になりうる機械: エネルギーを持つ、node以外のもの。 */
    static boolean user(ServerLevel level, BlockPos pos) {
        var tile = level.getBlockEntity(pos);
        return tile != null && !(tile instanceof WirelessNodeBlockEntity) && tile.getCapability(ForgeCapabilities.ENERGY).isPresent();
    }
    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().hasChunkAt(pos) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    private static WirelessPageReply.Entry nodeEntry(ServerLevel level, BlockPos pos) {
        var node = (WirelessNodeBlockEntity) level.getBlockEntity(pos);
        return new WirelessPageReply.Entry(pos, clip(node.nodeName()), !node.passwordIs(""));
    }
    /** 原作hFindNodes: 接続中のnodeと、範囲内の他のすべてのnode。 */
    static WirelessPageReply userList(ServerLevel level, WirelessNetworks networks, BlockPos user) {
        var connection = networks.connectionOf(user);
        BlockPos linkedPos = connection != null && level.hasChunkAt(connection.node())
                && level.getBlockEntity(connection.node()) instanceof WirelessNodeBlockEntity ? connection.node() : null;
        var avail = new ArrayList<WirelessPageReply.Entry>();
        for (var pos : WirelessNetworks.nodesInRange(level, user)) {
            if (pos.equals(linkedPos) || !(level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity n) || n.readOnly()) continue;
            if (avail.size() < WirelessPageReply.MAX_ENTRIES) avail.add(nodeEntry(level, pos));
        }
        return new WirelessPageReply(user, false, linkedPos == null ? null : nodeEntry(level, linkedPos), avail);
    }
    /**
     * 原作hFindNetworksとWiWorldData.rangeSearch: nodeの範囲内にあるmatrixとnodeのネットワークのうち、matrixがnodeへ届き、
     * 空きがあるもの。最大20。
     */
    static WirelessPageReply nodeList(ServerLevel level, WirelessNetworks networks, BlockPos node) {
        var linked = networks.networkAt(node);
        var self = WirelessNetworks.node(level, node);
        double range = self == null ? 0 : self.wirelessRange();
        var avail = new ArrayList<WirelessPageReply.Entry>();
        for (var ssid : networks.ssids()) {
            var network = networks.network(ssid);
            if (network == null || network == linked) continue;
            var matrix = WirelessNetworks.matrix(level, network.matrix());
            if (matrix == null || network.load() >= matrix.wirelessCapacity()) continue;
            double reach = matrix.wirelessRange();
            if (network.matrix().distSqr(node) > reach * reach) continue;
            boolean near = network.matrix().distSqr(node) <= range * range;
            for (var member : network.nodes()) near |= member.distSqr(node) <= range * range;
            if (!near) continue;
            avail.add(new WirelessPageReply.Entry(network.matrix(), clip(network.ssid()), !network.passwordIs("")));
            if (avail.size() >= 20) break;
        }
        return new WirelessPageReply(node, true,
                linked == null ? null : new WirelessPageReply.Entry(linked.matrix(), clip(linked.ssid()), !linked.passwordIs("")), avail);
    }
    private static String clip(String name) { return name.length() > WirelessPageReply.MAX_NAME ? name.substring(0, WirelessPageReply.MAX_NAME) : name; }
    public static List<WirelessPageReply.Entry> none() { return List.of(); }
}
