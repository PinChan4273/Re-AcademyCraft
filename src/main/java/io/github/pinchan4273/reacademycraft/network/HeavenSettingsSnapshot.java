package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** 本人の天界発現の設定の今の値（サーバー→クライアント、v1.5.0）。形は{@link HeavenSettings#save}。readableがfalseなら能力データが読めない。 */
public record HeavenSettingsSnapshot(boolean readable, CompoundTag settings) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(readable); buffer.writeNbt(settings); }
    public static HeavenSettingsSnapshot decode(FriendlyByteBuf buffer) {
        boolean readable = buffer.readBoolean();
        var tag = buffer.readNbt();
        return new HeavenSettingsSnapshot(readable, tag == null ? new CompoundTag() : tag);
    }
    public static HeavenSettingsSnapshot capture(ServerPlayer player) {
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        return data == null || data.isReadOnly() ? new HeavenSettingsSnapshot(false, new CompoundTag()) : new HeavenSettingsSnapshot(true, data.getHeavenSettings().save());
    }
    public static void send(ServerPlayer player) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), capture(player));
    }
}
