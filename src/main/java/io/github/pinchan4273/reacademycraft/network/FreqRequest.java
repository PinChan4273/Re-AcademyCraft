package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.terminal.TerminalApps;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import io.github.pinchan4273.reacademycraft.world.block.WirelessMatrixBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作FreqTransmitterUI.Syncs: 周波数送信機アプリがサーバーへ問い合わせるもの。それぞれ要求のidを持つFreqReplyで答える。
 *
 * - QUERY_SSID（matrix）: そのネットワークのSSID、または無し（原作e0: 未初期化）。nodeに対してはその名前
 *   （原作のクライアントはnodeの同期済みtileから読む）。
 * - AUTH_MATRIX（matrix、そのネットワークのパスワード）: パスワードが正しいか。
 * - AUTH_NODE（node、そのパスワード）: パスワードが正しいか。
 * - LINK_NODE（node、matrix、ネットワークのパスワード）: 原作のLinkNodeEvent。nodeを参加させる。
 * - LINK_USER（機械、node、nodeのパスワード）: 原作のLinkUserEvent。機械を参加させる。
 *
 * 問い合わせできるのは端末にこのアプリがあるプレイヤーだけで、対象は8ブロック以内のブロックだけ。原作の機械接続の
 * メッセージはパスワードを運ばず、クライアントが先に確認したと信じる。クライアントは何でも送れるので、ここではサーバーが
 * それを確かめ、nodeの場合はネットワークのパスワードも改めて確かめる。matrixのどのセルもmatrixとして答える
 * （原作のBlockMultiの原点の検索と同じ）。
 */
public record FreqRequest(int id, int op, BlockPos a, BlockPos b, String text) {
    public static final int QUERY_SSID = 0, AUTH_MATRIX = 1, AUTH_NODE = 2, LINK_NODE = 3, LINK_USER = 4;
    public static final int MAX_TEXT = 32;
    public FreqRequest {
        Objects.requireNonNull(a); Objects.requireNonNull(b); Objects.requireNonNull(text);
        if (op < QUERY_SSID || op > LINK_USER || text.length() > MAX_TEXT) throw new IllegalArgumentException("Invalid transmitter request");
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(id); buf.writeByte(op); buf.writeBlockPos(a); buf.writeBlockPos(b); buf.writeUtf(text, MAX_TEXT);
    }
    public static FreqRequest decode(FriendlyByteBuf buf) {
        return new FreqRequest(buf.readVarInt(), buf.readUnsignedByte(), buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(MAX_TEXT));
    }
    public void handle(ServerPlayer player) {
        var reply = answer(player);
        if (player != null && player.connection != null)
            AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FreqReply(id, reply.ok(), reply.text()));
    }
    public record Answer(boolean ok, String text) { static final Answer NO = new Answer(false, ""); }
    /** 送らずに返答を求める: GameTestが直接呼ぶ。 */
    public Answer answer(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || !TerminalState.has(player, TerminalApps.FREQ_TRANSMITTER)) return Answer.NO;
        var level = player.serverLevel();
        if (!near(player, a) || (op == LINK_NODE || op == LINK_USER) && !near(player, b)) return Answer.NO;
        var networks = WirelessNetworks.of(level);
        return switch (op) {
            case QUERY_SSID -> {
                if (level.getBlockEntity(a) instanceof WirelessNodeBlockEntity node) yield node.readOnly() ? Answer.NO : new Answer(true, node.nodeName());
                var matrix = matrixOrigin(level, a);
                var network = matrix == null ? null : networks.networkAt(matrix);
                yield network == null ? Answer.NO : new Answer(true, network.ssid());
            }
            case AUTH_MATRIX -> {
                var matrix = matrixOrigin(level, a);
                var network = matrix == null ? null : networks.networkAt(matrix);
                yield new Answer(network != null && network.passwordIs(text), "");
            }
            case AUTH_NODE -> new Answer(level.getBlockEntity(a) instanceof WirelessNodeBlockEntity node && !node.readOnly() && node.passwordIs(text), "");
            case LINK_NODE -> {
                var matrix = matrixOrigin(level, b);
                var network = matrix == null ? null : networks.networkAt(matrix);
                yield new Answer(network != null && level.getBlockEntity(a) instanceof WirelessNodeBlockEntity
                        && networks.linkNode(level, a, network.ssid(), text), "");
            }
            case LINK_USER -> {
                boolean user = level.getBlockEntity(a) != null && level.getBlockEntity(a).getCapability(ForgeCapabilities.ENERGY).isPresent()
                        && !(level.getBlockEntity(a) instanceof WirelessNodeBlockEntity);
                yield new Answer(user && level.getBlockEntity(b) instanceof WirelessNodeBlockEntity node && node.passwordIs(text)
                        && networks.linkUser(level, b, a), "");
            }
            default -> Answer.NO;
        };
    }
    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().hasChunkAt(pos) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    /** matrixのいずれかのセルに対する、matrix自身の位置。そこにmatrixが無ければnull。 */
    @javax.annotation.Nullable static BlockPos matrixOrigin(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WirelessMatrixBlock)) return null;
        var origin = WirelessMatrixBlock.origin(pos, state);
        return WirelessNetworks.matrix(level, origin) == null ? null : origin;
    }
}
