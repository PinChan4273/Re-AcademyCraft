package io.github.pinchan4273.reacademycraft.energy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 原作WiWorldData、WirelessNet、NodeConn: 1つのワールドの無線ネットワーク。
 *
 * ネットワークはmatrixに属し、SSIDで名付けられる。nodeは、matrixの範囲内にあり、matrixにまだ1つ分の空きがあるとき、
 * パスワードを示して参加する。電力を使うものも同じ方法で、nodeの範囲と容量の内でnodeに参加する。matrixが無くなった
 * ネットワークと、nodeが無くなったnodeの接続は、次に参照したときに外す（原作の検証と同じ）。
 *
 * 原作はこれをWorldSavedDataに保存し、ブロックを位置で参照して、必要なときにそこにあるものを確かめる。ここでも同じで、
 * 読み込まれていないchunkにあるmatrixやnodeは、誰かが問い合わせるまでネットワーク内の位置を保つ。
 */
public final class WirelessNetworks extends SavedData {
    public static final String NAME = "academy_wireless";
    /** 原作WirelessNet.BUFFER_MAX（移植版が数える単位のmilli-IF）。 */
    public static final int BUFFER_MAX_MILLI_IF = 2000000;
    public static final int MAX_SSID_LENGTH = 32, MAX_PASSWORD_LENGTH = 32;

    /** ネットワーク1つ: matrix、名前、パスワード、バッファの量、参加しているもの。 */
    public static final class Network {
        final BlockPos matrix;
        String ssid, password;
        int bufferMilliIF;
        final Set<BlockPos> nodes = new LinkedHashSet<>();
        Network(BlockPos matrix, String ssid, String password) {
            this.matrix = matrix.immutable(); this.ssid = ssid; this.password = password;
        }
        public BlockPos matrix() { return matrix; }
        public String ssid() { return ssid; }
        public boolean passwordIs(String value) { return password.equals(value); }
        /** matrixの設置者のパネル専用。 */
        public String password() { return password; }
        public int bufferMilliIF() { return bufferMilliIF; }
        public Set<BlockPos> nodes() { return Set.copyOf(nodes); }
        public int load() { return nodes.size(); }
    }
    /** node自身の接続1つ: そこに参加しているもの。 */
    public static final class Connection {
        final BlockPos node;
        final Set<BlockPos> users = new LinkedHashSet<>();
        Connection(BlockPos node) { this.node = node.immutable(); }
        public BlockPos node() { return node; }
        public Set<BlockPos> users() { return Set.copyOf(users); }
        public int load() { return users.size(); }
    }

    private final Map<String, Network> networks = new LinkedHashMap<>();
    private final Map<BlockPos, Connection> connections = new LinkedHashMap<>();

    public WirelessNetworks() { }
    /** これらのネットワークがあるlevel（イベント用）。levelから取ったものでないデータではnull。 */
    @Nullable private Level level;

    public static WirelessNetworks of(ServerLevel level) {
        var data = level.getDataStorage().computeIfAbsent(WirelessNetworks::load, WirelessNetworks::new, NAME);
        data.level = level;
        return data;
    }
    /** 無線イベントを送る。他のmodが拒否したらtrue。 */
    private static boolean refused(net.minecraftforge.eventbus.api.Event event) { return net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event); }

    // ---- ネットワーク -------------------------------------------------------------------------

    @Nullable public Network network(String ssid) { return networks.get(ssid); }
    public List<String> ssids() { return List.copyOf(networks.keySet()); }
    /** このmatrixまたはnodeが属するネットワーク。無ければnull。 */
    @Nullable public Network networkAt(BlockPos pos) {
        for (var network : networks.values())
            if (network.matrix.equals(pos) || network.nodes.contains(pos)) return network;
        return null;
    }
    /** 原作WirelessSystem.createNetwork: matrix1つにつきネットワーク1つ、名前1つにつきネットワーク1つ。 */
    @Nullable public Network create(Level level, BlockPos matrix, String ssid, String password) {
        if (!valid(ssid, MAX_SSID_LENGTH) || !validPassword(password)) return null;
        if (networks.containsKey(ssid) || networkAt(matrix) != null) return null;
        if (matrix(level, matrix) == null) return null;
        if (refused(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.CreateNetwork(level, matrix, ssid, password))) return null;
        var network = new Network(matrix, ssid, password);
        networks.put(ssid, network); setDirty(); return network;
    }
    public boolean dispose(String ssid) {
        var network = networks.remove(ssid);
        if (network == null) return false;
        setDirty(); net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.DestroyNetwork(level, network.matrix, ssid)); return true;
    }
    /**
     * 原作MSG_CHANGE_SSIDのWirelessNet.setSSID: その名前のネットワークが他に無ければ、matrix・node・バッファを保ったまま
     * 新しい名前にする。
     */
    public boolean rename(String ssid, String newSsid) {
        var network = networks.get(ssid);
        if (network == null || !valid(newSsid, MAX_SSID_LENGTH) || (!ssid.equals(newSsid) && networks.containsKey(newSsid))) return false;
        networks.remove(ssid); network.ssid = newSsid; networks.put(newSsid, network); setDirty(); return true;
    }
    public boolean setPassword(String ssid, String password) {
        var network = networks.get(ssid);
        if (network == null || !validPassword(password)) return false;
        if (refused(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.ChangePass(level, network.matrix, ssid, password))) return false;
        network.password = password; setDirty(); return true;
    }

    /**
     * 原作addNode: パスワード、matrix自身の容量、範囲。別のネットワークにいたnodeは、原作が見つけたネットワークから外すのと同じく、
     * まずそこを離れる。
     */
    public boolean linkNode(Level level, BlockPos nodePos, String ssid, String password) {
        var network = networks.get(ssid);
        if (network == null || !network.passwordIs(password)) return false;
        var matrix = matrix(level, network.matrix);
        if (matrix == null || node(level, nodePos) == null) return false;
        if (network.nodes.contains(nodePos)) return true;
        if (network.load() >= matrix.wirelessCapacity()) return false;
        double range = matrix.wirelessRange();
        if (network.matrix.distSqr(nodePos) > range * range) return false;
        if (refused(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.LinkNode(level, nodePos, network.matrix, ssid))) return false;
        var previous = networkAt(nodePos);
        if (previous != null) previous.nodes.remove(nodePos);
        network.nodes.add(nodePos.immutable()); setDirty(); return true;
    }
    public boolean unlinkNode(BlockPos nodePos) {
        var network = networkAt(nodePos);
        if (network == null || network.matrix.equals(nodePos)) return false;
        network.nodes.remove(nodePos); setDirty();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.UnlinkNode(level, nodePos, network.ssid)); return true;
    }

    // ---- nodeの接続 -----------------------------------------------------------------

    public Connection connection(BlockPos nodePos) {
        return connections.computeIfAbsent(nodePos.immutable(), Connection::new);
    }
    @Nullable public Connection connectionOf(BlockPos userPos) {
        for (var connection : connections.values()) if (connection.users.contains(userPos)) return connection;
        return null;
    }
    /** 原作addReceiver: node自身の容量と範囲。使用者1つにつきnode1つ。 */
    public boolean linkUser(Level level, BlockPos nodePos, BlockPos userPos) {
        var node = node(level, nodePos);
        if (node == null || nodePos.equals(userPos)) return false;
        var connection = connection(nodePos);
        if (connection.users.contains(userPos)) return true;
        if (connection.load() >= node.wirelessCapacity()) return false;
        double range = node.wirelessRange();
        if (nodePos.distSqr(userPos) > range * range) return false;
        if (refused(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.LinkUser(level, userPos, nodePos))) return false;
        var previous = connectionOf(userPos);
        if (previous != null) previous.users.remove(userPos);
        connection.users.add(userPos.immutable()); setDirty(); return true;
    }
    public boolean unlinkUser(BlockPos userPos) {
        var connection = connectionOf(userPos);
        if (connection == null) return false;
        connection.users.remove(userPos); setDirty();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.UnlinkUser(this.level, userPos, connection.node)); return true;
    }

    // ---- エネルギー ---------------------------------------------------------------------------

    /** ネットワークが持つ量。原作と同じ上限で切り、受け取った量を返す。 */
    public int offer(Network network, int milliIF) {
        int taken = Math.max(0, Math.min(milliIF, BUFFER_MAX_MILLI_IF - network.bufferMilliIF));
        if (taken > 0) { network.bufferMilliIF += taken; setDirty(); }
        return taken;
    }
    public int remaining(Network network) { return BUFFER_MAX_MILLI_IF - network.bufferMilliIF; }
    public int draw(Network network, int milliIF) {
        int given = Math.max(0, Math.min(milliIF, network.bufferMilliIF));
        if (given > 0) { network.bufferMilliIF -= given; setDirty(); }
        return given;
    }

    /**
     * 原作WiWorldData.tick（levelのブロックエンティティの後）: 各ネットワークがnodeを均し、次に各nodeの接続が、nodeと
     * そこへ繋がるものの間で電力を動かす。
     *
     * 原作WirelessNet.tick: 読み込まれたnodeを（同じnodeが常に先頭にならないよう）シャッフルし、ネットワークの平均の充填率へ
     * 近づける。各nodeは自身の帯域を超えず、全体でmatrixの帯域を超えない。ネットワークのバッファ（最大2000 IF）はnodeが
     * 得た分を受け取り、失った分を与え、満杯か空になると均しを止める。原作は各移動をnodeとバッファの両方へ加えており、それを保つ。
     */
    public void tick(ServerLevel level) {
        // 原作WirelessNet.tickとNodeConn.tickは、読み込まれたchunkから消えたものを外すところから始める。
        validate(level);
        var random = level.getRandom();
        for (var network : networks.values()) {
            if (!level.hasChunkAt(network.matrix)) continue;
            var matrix = matrix(level, network.matrix);
            if (matrix == null) continue;
            var loaded = new it.unimi.dsi.fastutil.objects.ObjectArrayList<io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity>();
            for (var pos : network.nodes)
                if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity node
                        && node.balanced()) loaded.add(node);
            net.minecraft.Util.shuffle(loaded, random);
            double sum = 0, maxSum = 0;
            for (var node : loaded) { sum += node.milliIF(); maxSum += node.maxMilliIF(); }
            if (maxSum <= 0) continue;
            double percent = sum / maxSum;
            double transferLeft = matrix.wirelessBandwidthMilliIF();
            for (var node : loaded) {
                double cur = node.milliIF(), delta = node.maxMilliIF() * percent - cur;
                delta = Math.signum(delta) * Math.min(Math.abs(delta), Math.min(transferLeft, node.wirelessBandwidthMilliIF()));
                if (network.bufferMilliIF + delta > BUFFER_MAX_MILLI_IF) delta = BUFFER_MAX_MILLI_IF - network.bufferMilliIF;
                else if (network.bufferMilliIF + delta < 0) delta = -network.bufferMilliIF;
                int move = (int) delta;
                transferLeft -= Math.abs(move);
                if (move != 0) { network.bufferMilliIF += move; node.setMilliIF(node.milliIF() + move); setDirty(); }
                if (transferLeft <= 0) break;
            }
        }
        for (var connection : List.copyOf(connections.values())) {
            if (connection.users.isEmpty() || !level.hasChunkAt(connection.node)) continue;
            if (level.getBlockEntity(connection.node) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity node)
                node.connectionTick(level, connection, random);
        }
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "academy")
    public static final class WirelessTicker {
        private WirelessTicker() { }
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onWirelessLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && event.level instanceof ServerLevel level)
                of(level).tick(level);
        }
    }

    // ---- 実際にそこにあるもの ------------------------------------------------------------

    /** 原作WirelessHelper.getNodesInRange: 自身の範囲がこの場所を含むnode。 */
    public static java.util.List<BlockPos> nodesInRange(Level level, BlockPos pos) {
        var found = new java.util.ArrayList<BlockPos>();
        var networks = level instanceof ServerLevel server ? of(server) : null;
        int reach = 20; // 原作で最も広いnodeより少し先まで
        for (int x = -reach; x <= reach; x++) for (int y = -reach; y <= reach; y++) for (int z = -reach; z <= reach; z++) {
            var at = pos.offset(x, y, z);
            var node = node(level, at);
            if (node == null) continue;
            double range = node.wirelessRange();
            // 原作WirelessHelper.getNodesInRange: 範囲内で、まだ1つ分の空きがあるもの。
            var connection = networks == null ? null : networks.connections.get(at);
            if (connection != null && connection.load() >= node.wirelessCapacity()) continue;
            if (at.distSqr(pos) <= range * range) found.add(at.immutable());
        }
        return found;
    }
    @Nullable public static WirelessMatrix matrix(Level level, BlockPos pos) {
        return level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof WirelessMatrix matrix
                && matrix.wirelessCapacity() > 0 ? matrix : null;
    }
    @Nullable public static WirelessNode node(Level level, BlockPos pos) {
        return level != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof WirelessNode node ? node : null;
    }
    /** 原作validate(): matrixが無くなったネットワークを外し、そのnodeの接続も外す。 */
    public void validate(Level level) {
        var gone = new java.util.ArrayList<Network>();
        for (var network : networks.values())
            if (level.hasChunkAt(network.matrix) && matrix(level, network.matrix) == null) gone.add(network);
        boolean changed = networks.values().removeAll(gone);
        // 原作DestroyNetworkEvent: matrixが無くなったネットワーク。
        for (var network : gone) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.WirelessEvent.DestroyNetwork(level, network.matrix, network.ssid));
        for (var network : networks.values())
            changed |= network.nodes.removeIf(pos -> level.hasChunkAt(pos) && node(level, pos) == null);
        changed |= connections.values().removeIf(connection ->
                level.hasChunkAt(connection.node) && node(level, connection.node) == null);
        // 原作NodeConn.tick: （読み込まれたchunkで）ブロックが無くなった発電機や受電機はnodeを離れ、その枠が空く。
        // 読み込まれていないchunkのものは残る。
        for (var connection : connections.values())
            changed |= connection.users.removeIf(pos -> level.hasChunkAt(pos) && !energyUser(level, pos));
        if (changed) setDirty();
    }
    private static boolean energyUser(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity != null && entity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).isPresent();
    }
    /** 原作createNetworkとresetPasswordは空を含むどのパスワードも受け付ける: 空なら公開ネットワーク。 */
    private static boolean validPassword(String value) {
        return value != null && (value.isEmpty() || valid(value, MAX_PASSWORD_LENGTH));
    }
    private static boolean valid(String value, int max) {
        return value != null && !value.isEmpty() && value.length() <= max && value.chars().allMatch(c -> c >= 0x20 && c != 0x7f);
    }

    // ---- 永続化 ------------------------------------------------------------------------

    public static WirelessNetworks load(CompoundTag tag) {
        var data = new WirelessNetworks();
        for (Tag entry : tag.getList("networks", Tag.TAG_COMPOUND)) {
            var networkTag = (CompoundTag) entry;
            var network = new Network(NbtUtils.readBlockPos(networkTag.getCompound("matrix")),
                    networkTag.getString("ssid"), networkTag.getString("password"));
            network.bufferMilliIF = Math.max(0, Math.min(BUFFER_MAX_MILLI_IF, networkTag.getInt("buffer")));
            for (Tag node : networkTag.getList("nodes", Tag.TAG_COMPOUND))
                network.nodes.add(NbtUtils.readBlockPos((CompoundTag) node));
            if (valid(network.ssid, MAX_SSID_LENGTH)) data.networks.put(network.ssid, network);
        }
        for (Tag entry : tag.getList("connections", Tag.TAG_COMPOUND)) {
            var connectionTag = (CompoundTag) entry;
            var connection = new Connection(NbtUtils.readBlockPos(connectionTag.getCompound("node")));
            for (Tag user : connectionTag.getList("users", Tag.TAG_COMPOUND))
                connection.users.add(NbtUtils.readBlockPos((CompoundTag) user));
            data.connections.put(connection.node, connection);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        for (var network : networks.values()) {
            var networkTag = new CompoundTag();
            networkTag.put("matrix", NbtUtils.writeBlockPos(network.matrix));
            networkTag.putString("ssid", network.ssid); networkTag.putString("password", network.password);
            networkTag.putInt("buffer", network.bufferMilliIF);
            var nodes = new ListTag();
            for (var node : network.nodes) nodes.add(NbtUtils.writeBlockPos(node));
            networkTag.put("nodes", nodes);
            list.add(networkTag);
        }
        tag.put("networks", list);
        var connectionList = new ListTag();
        for (var connection : connections.values()) {
            if (connection.users.isEmpty()) continue;
            var connectionTag = new CompoundTag();
            connectionTag.put("node", NbtUtils.writeBlockPos(connection.node));
            var users = new ListTag();
            for (var user : connection.users) users.add(NbtUtils.writeBlockPos(user));
            connectionTag.put("users", users);
            connectionList.add(connectionTag);
        }
        tag.put("connections", connectionList);
        return tag;
    }
    /** テスト用の入口: 保持しているネットワークと接続の数。 */
    public int networkCount() { return networks.size(); }
    public int connectionCount() { return (int) connections.values().stream().filter(c -> !c.users.isEmpty()).count(); }
}
