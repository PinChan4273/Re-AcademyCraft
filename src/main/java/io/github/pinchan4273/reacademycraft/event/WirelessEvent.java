package io.github.pinchan4273.reacademycraft.event;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 原作の無線イベント（cn.academy.event.energy）。WirelessNetworksが動作する直前にMinecraftForge.EVENT_BUSへ送る。
 * 原作と同じく、何かを求めるもの（ネットワークの作成、パスワードの変更、nodeや機械の接続）は取り消し可能で、他のmodが
 * 拒否できる。終わりを知らせるものは取り消せない。ブロックはイベントの対象: ネットワークならmatrix、node、または機械。
 * Levelがnullになるのは、levelに結び付かないデータの場合だけ。
 */
public abstract class WirelessEvent extends Event {
    @Nullable private final Level level;
    private final BlockPos pos;
    protected WirelessEvent(@Nullable Level level, BlockPos pos) { this.level = level; this.pos = pos.immutable(); }
    @Nullable public Level level() { return level; }
    public BlockPos pos() { return pos; }

    /** 原作CreateNetworkEvent: matrixがネットワークを始める。 */
    @Cancelable public static final class CreateNetwork extends WirelessEvent {
        private final String ssid, password;
        public CreateNetwork(@Nullable Level level, BlockPos matrix, String ssid, String password) { super(level, matrix); this.ssid = ssid; this.password = password; }
        public String ssid() { return ssid; }
        public String password() { return password; }
    }
    /** 原作DestroyNetworkEvent: ネットワークが無くなった（matrixが撤去された、またはネットワークが破棄された）。 */
    public static final class DestroyNetwork extends WirelessEvent {
        private final String ssid;
        public DestroyNetwork(@Nullable Level level, BlockPos matrix, String ssid) { super(level, matrix); this.ssid = ssid; }
        public String ssid() { return ssid; }
    }
    /** 原作ChangePassEvent: ネットワークのパスワードを変更する。 */
    @Cancelable public static final class ChangePass extends WirelessEvent {
        private final String ssid, password;
        public ChangePass(@Nullable Level level, BlockPos matrix, String ssid, String password) { super(level, matrix); this.ssid = ssid; this.password = password; }
        public String ssid() { return ssid; }
        public String password() { return password; }
    }
    /** 原作LinkNodeEvent: nodeがネットワークへ参加する（パスワードは確認済み）。 */
    @Cancelable public static final class LinkNode extends WirelessEvent {
        private final BlockPos matrix;
        private final String ssid;
        public LinkNode(@Nullable Level level, BlockPos node, BlockPos matrix, String ssid) { super(level, node); this.matrix = matrix.immutable(); this.ssid = ssid; }
        public BlockPos matrix() { return matrix; }
        public String ssid() { return ssid; }
    }
    /** 原作UnlinkNodeEvent: nodeがネットワークを離れる。 */
    public static final class UnlinkNode extends WirelessEvent {
        private final String ssid;
        public UnlinkNode(@Nullable Level level, BlockPos node, String ssid) { super(level, node); this.ssid = ssid; }
        public String ssid() { return ssid; }
    }
    /** 原作LinkUserEvent: 機械がnodeへ参加する。nodeのパスワードを求める場合、それは確認済み。 */
    @Cancelable public static final class LinkUser extends WirelessEvent {
        private final BlockPos node;
        public LinkUser(@Nullable Level level, BlockPos user, BlockPos node) { super(level, user); this.node = node.immutable(); }
        public BlockPos node() { return node; }
    }
    /** 原作UnlinkUserEvent: 機械がnodeを離れる。 */
    public static final class UnlinkUser extends WirelessEvent {
        private final BlockPos node;
        public UnlinkUser(@Nullable Level level, BlockPos user, BlockPos node) { super(level, user); this.node = node.immutable(); }
        public BlockPos node() { return node; }
    }
}
