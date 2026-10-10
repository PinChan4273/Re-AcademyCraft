package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.terminal.TerminalApps;
import io.github.pinchan4273.reacademycraft.terminal.TerminalSettings;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * スイッチをクリックしたときに原作の設定アプリが行うこと。原作はシングルプレイでは同じ機械なので、クライアントで設定を
 * 直接書く。ここでは設定はサーバーのものなので、クライアントが要求し、サーバーはシングルプレイのワールドのホストであることを
 * 確かめてから書く。
 */
public record TerminalSetting(String id, boolean on) {
    public TerminalSetting {
        Objects.requireNonNull(id);
        if (id.isEmpty() || id.length() > 32) throw new IllegalArgumentException("Invalid setting");
    }
    public void encode(FriendlyByteBuf b) { b.writeUtf(id, 32); b.writeBoolean(on); }
    public static TerminalSetting decode(FriendlyByteBuf b) { return new TerminalSetting(b.readUtf(32), b.readBoolean()); }

    public void handle(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;
        if (!TerminalState.has(player, TerminalApps.SETTINGS)) return;
        if (!TerminalSettings.mayChange(player.getServer(), player)) return;
        if (TerminalSettings.set(id, on)) TerminalSnapshot.send(player);
    }
}
