package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 原作のmatrixのパネルがサーバーへ送るもの: ネットワークの命名、パスワードの変更、破棄。原作はmatrixを置いたプレイヤー以外に
 * 何も変更させず、ここでも同じ。nodeと機械は原作と同じく周波数送信機アプリ（FreqRequest）を通じて参加する。
 */
public record WirelessConfig(BlockPos pos, int action, String ssid, String password) {
    public static final int CREATE = 0, PASSWORD = 1, RENAME = 2, DISPOSE = 3;
    public WirelessConfig {
        Objects.requireNonNull(pos); Objects.requireNonNull(ssid); Objects.requireNonNull(password);
        if (action < CREATE || action > DISPOSE
                || ssid.length() > WirelessNetworks.MAX_SSID_LENGTH
                || password.length() > WirelessNetworks.MAX_PASSWORD_LENGTH)
            throw new IllegalArgumentException("Invalid wireless config");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(pos); b.writeByte(action);
        b.writeUtf(ssid, WirelessNetworks.MAX_SSID_LENGTH); b.writeUtf(password, WirelessNetworks.MAX_PASSWORD_LENGTH);
    }
    public static WirelessConfig decode(FriendlyByteBuf b) {
        return new WirelessConfig(b.readBlockPos(), b.readUnsignedByte(),
                b.readUtf(WirelessNetworks.MAX_SSID_LENGTH), b.readUtf(WirelessNetworks.MAX_PASSWORD_LENGTH));
    }

    public void handle(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return;
        if (!(level.getBlockEntity(pos) instanceof WirelessMatrixBlockEntity matrix) || matrix.readOnly()) return;
        // 原作: matrixを置いたプレイヤーだけがそのネットワークに触れられる。原作のplacerNameは""で始まるので、どのプレイヤーも
        // 置いていないmatrixは誰のものでもない（画面も既にそう表示する）。
        String owner = matrix.placer();
        if (owner == null || !owner.equals(player.getGameProfile().getName())) {
            player.displayClientMessage(Component.translatable("academy.wireless.not_yours"), true); return;
        }
        var networks = WirelessNetworks.of(level);
        var network = networks.networkAt(pos);
        switch (action) {
            case CREATE -> {
                if (network != null) { player.displayClientMessage(Component.translatable("academy.wireless.exists"), true); return; }
                if (!matrix.working()) { player.displayClientMessage(Component.translatable("academy.wireless.not_working"), true); return; }
                var made = networks.create(level, pos, ssid, password);
                player.displayClientMessage(Component.translatable(made == null
                        ? "academy.wireless.refused" : "academy.wireless.created", ssid), true);
            }
            case PASSWORD -> {
                if (network == null) return;
                player.displayClientMessage(Component.translatable(networks.setPassword(network.ssid(), password)
                        ? "academy.wireless.password_set" : "academy.wireless.refused", network.ssid()), true);
            }
            case RENAME -> {
                if (network == null) return;
                player.displayClientMessage(Component.translatable(networks.rename(network.ssid(), ssid)
                        ? "academy.wireless.renamed" : "academy.wireless.refused", ssid), true);
            }
            case DISPOSE -> {
                if (network == null) return;
                networks.dispose(network.ssid());
                player.displayClientMessage(Component.translatable("academy.wireless.disposed"), true);
            }
            default -> { }
        }
    }
}
