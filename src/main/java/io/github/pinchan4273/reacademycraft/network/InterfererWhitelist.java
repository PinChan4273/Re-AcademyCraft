package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 開いているパネルのための、原作AbilityInterfererのset_whitelistとその同期: クライアントは望むリスト全体を送り、サーバーは
 * パネルが最後に持っていたものと違うたびにリストを送り返す。コンテナidがパネルを示すので、リストが届くのはプレイヤー自身の
 * 開いているパネルの背後の機械だけ。
 */
public record InterfererWhitelist(int containerId, List<String> names) {
    public InterfererWhitelist {
        Objects.requireNonNull(names);
        if (names.size() > AbilityInterfererBlockEntity.MAX_NAMES) throw new IllegalArgumentException("Too many names");
        for (var name : names) if (name == null || name.length() > AbilityInterfererBlockEntity.MAX_NAME) throw new IllegalArgumentException("Invalid name");
        names = List.copyOf(names);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(containerId); b.writeVarInt(names.size());
        for (var name : names) b.writeUtf(name, AbilityInterfererBlockEntity.MAX_NAME);
    }
    public static InterfererWhitelist decode(FriendlyByteBuf b) {
        int id = b.readVarInt(), count = b.readVarInt();
        if (count < 0 || count > AbilityInterfererBlockEntity.MAX_NAMES) throw new io.netty.handler.codec.DecoderException("Too many names");
        var names = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) names.add(b.readUtf(AbilityInterfererBlockEntity.MAX_NAME));
        return new InterfererWhitelist(id, names);
    }
    /** サーバー: パネルがこのリストを要求する。 */
    public void handle(ServerPlayer player) {
        if (player == null || !(player.containerMenu instanceof AbilityInterfererMenu menu) || menu.containerId != containerId) return;
        menu.setWhitelist(player, names);
    }
    /** クライアント: 機械の現在のリスト。 */
    public void accept(net.minecraft.world.entity.player.Player player) {
        if (player != null && player.containerMenu instanceof AbilityInterfererMenu menu && menu.containerId == containerId) menu.acceptWhitelist(names);
    }
}
