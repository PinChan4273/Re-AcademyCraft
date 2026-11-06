package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * 術者自身のクライアントへ、切り替え型技能のモードがオン・オフになったことを伝える。原作のactivate処理と同じく、
 * 能力キーが能力をオフにする代わりにそのモードを終えられるようにする。
 */
public record SkillModeState(Identifier skill, boolean active) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) { buffer.writeIdentifier(skill); buffer.writeBoolean(active); }
    public static SkillModeState decode(FriendlyByteBuf buffer) { return new SkillModeState(buffer.readIdentifier(), buffer.readBoolean()); }
    public static void send(net.minecraft.server.level.ServerPlayer player, Identifier skill, boolean active) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new SkillModeState(skill, active));
    }
}
