package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** サーバーが認めたThunder Clapの不変の表示状態。 */
public record ThunderClapPresentationEffect(ResourceLocation dimension, UUID player, int entityId,
                                             long session, int revision, Action action, Vec3 target) {
    public enum Action { START, UPDATE, STOP }

    public ThunderClapPresentationEffect {
        Objects.requireNonNull(dimension); Objects.requireNonNull(player);
        Objects.requireNonNull(action); Objects.requireNonNull(target);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH || entityId < 0 || session <= 0
                || revision < 0 || action == Action.START && revision != 0
                || action == Action.UPDATE && revision == 0 || !point(target))
            throw new IllegalArgumentException("Invalid Thunder Clap presentation effect");
    }

    private static boolean point(Vec3 value) {
        return bounded(value.x) && bounded(value.y) && bounded(value.z);
    }
    private static boolean bounded(double value) { return Double.isFinite(value) && Math.abs(value) <= 33_554_431; }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH); buffer.writeUUID(player);
        buffer.writeVarInt(entityId); buffer.writeLong(session); buffer.writeVarInt(revision);
        buffer.writeByte(action.ordinal()); buffer.writeDouble(target.x); buffer.writeDouble(target.y); buffer.writeDouble(target.z);
    }

    public static ThunderClapPresentationEffect decode(FriendlyByteBuf buffer) {
        var dimension = ResourceLocation.parse(buffer.readUtf(AbilitySnapshot.MAX_ID_LENGTH));
        var player = buffer.readUUID(); int entity = buffer.readVarInt(); long session = buffer.readLong();
        int revision = buffer.readVarInt(), ordinal = buffer.readUnsignedByte();
        if (ordinal >= Action.values().length) throw new IllegalArgumentException("Invalid Thunder Clap presentation action");
        return new ThunderClapPresentationEffect(dimension, player, entity, session, revision, Action.values()[ordinal],
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }
}
