package io.github.pinchan4273.reacademycraft.network;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * 原作のFollowEntitySoundと同じく、クライアントでエンティティに付いて行く音を開始・停止する。
 * 開始は音の名前を示し、停止は終える音のトークンだけを運ぶ。
 */
public record FollowSound(ResourceLocation dimension, int entityId, long token, @Nullable ResourceLocation sound,
                          SoundSource source, float volume, boolean loop) {
    public FollowSound {
        if (!Float.isFinite(volume) || volume < 0 || volume > 4) throw new IllegalArgumentException("Invalid follow sound volume");
    }
    public static FollowSound stop(ResourceLocation dimension, int entityId, long token) {
        return new FollowSound(dimension, entityId, token, null, SoundSource.AMBIENT, 0, false);
    }
    public boolean starts() { return sound != null; }
    public void encode(FriendlyByteBuf b) {
        b.writeResourceLocation(dimension); b.writeVarInt(entityId); b.writeLong(token);
        b.writeBoolean(sound != null);
        if (sound != null) { b.writeResourceLocation(sound); b.writeEnum(source); b.writeFloat(volume); b.writeBoolean(loop); }
    }
    public static FollowSound decode(FriendlyByteBuf b) {
        var dimension = b.readResourceLocation(); int entity = b.readVarInt(); long token = b.readLong();
        if (!b.readBoolean()) return stop(dimension, entity, token);
        return new FollowSound(dimension, entity, token, b.readResourceLocation(), b.readEnum(SoundSource.class), b.readFloat(), b.readBoolean());
    }
}
