package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Electron Bombのオーブの不変のスナップショット。オーブは術者に付いて行くので、ワールドの位置ではなく決めたときのずれを
 * 運ぶ。当たり・ダメージ・資源の決定権は持たない。
 */
public record MdBallEffect(ResourceLocation dimension, UUID player, int entityId, long session,
                           double offsetX, double offsetY, double offsetZ, int lifeTicks) {
    public MdBallEffect {
        Objects.requireNonNull(dimension); Objects.requireNonNull(player);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH || entityId < 0 || session <= 0
                || !bounded(offsetX) || !bounded(offsetY) || !bounded(offsetZ)
                || lifeTicks <= 0 || lifeTicks > 1200) throw new IllegalArgumentException("Invalid Electron Bomb orb");
    }
    /** オーブは術者から水平に半径1.3、下へ1.2の範囲で決める。 */
    private static boolean bounded(double value) { return Double.isFinite(value) && Math.abs(value) <= 2; }
    public void encode(FriendlyByteBuf b) {
        b.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH); b.writeUUID(player);
        b.writeVarInt(entityId); b.writeLong(session);
        b.writeDouble(offsetX); b.writeDouble(offsetY); b.writeDouble(offsetZ); b.writeVarInt(lifeTicks);
    }
    public static MdBallEffect decode(FriendlyByteBuf b) {
        return new MdBallEffect(ResourceLocation.parse(b.readUtf(AbilitySnapshot.MAX_ID_LENGTH)), b.readUUID(),
                b.readVarInt(), b.readLong(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt());
    }
}
