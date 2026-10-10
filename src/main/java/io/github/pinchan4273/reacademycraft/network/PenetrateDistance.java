package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 原作PTContextのcurDist（クライアントがMSG_EXECUTEと共に送る）: 照準中に術者がマウスホイールで設定した距離
 * （useMouseWheel）。サーバーは技能の射程内に収める。
 */
public record PenetrateDistance(float distance) {
    public PenetrateDistance {
        if (!Float.isFinite(distance)) throw new IllegalArgumentException("Invalid distance");
    }
    public void encode(FriendlyByteBuf b) { b.writeFloat(distance); }
    public static PenetrateDistance decode(FriendlyByteBuf b) { return new PenetrateDistance(b.readFloat()); }
    public void handle(ServerPlayer player) {
        if (player != null) PenetrateTeleport.setDistance(player, distance);
    }
}
