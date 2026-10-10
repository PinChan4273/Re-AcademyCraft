package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** エネルギー移動と同じ光線・capabilityの確認から作る、サーバー専用の表示用スナップショット。 */
public record ChargingArcTarget(ResourceLocation dimension, UUID player, int entityId, long session,
                                long revision, boolean traced, Vec3 endpoint, BlockPos supportedBlock) {
    public ChargingArcTarget {
        Objects.requireNonNull(dimension); Objects.requireNonNull(player); Objects.requireNonNull(endpoint);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH || entityId < 0 || session <= 0 || revision <= 0
                || !bounded(endpoint.x) || !bounded(endpoint.y) || !bounded(endpoint.z))
            throw new IllegalArgumentException("Invalid charging target snapshot");
        if (supportedBlock != null) {
            if (!traced) throw new IllegalArgumentException("Untraced target cannot support charging");
            supportedBlock = supportedBlock.immutable();
            if (!bounded(supportedBlock.getX()) || !bounded(supportedBlock.getY()) || !bounded(supportedBlock.getZ())
                    || endpoint.distanceToSqr(Vec3.atCenterOf(supportedBlock)) > 3)
                throw new IllegalArgumentException("Charging endpoint is not on the supported block");
        }
    }
    private static boolean bounded(double value) { return Double.isFinite(value) && Math.abs(value) <= 33_554_431; }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH); buffer.writeUUID(player);
        buffer.writeVarInt(entityId); buffer.writeLong(session); buffer.writeLong(revision); buffer.writeBoolean(traced);
        buffer.writeDouble(endpoint.x); buffer.writeDouble(endpoint.y); buffer.writeDouble(endpoint.z);
        buffer.writeBoolean(supportedBlock != null);
        // 3つのintで検証済みの座標範囲を保つ（packしたBlockPosはYの範囲が狭い）。
        if (supportedBlock != null) { buffer.writeInt(supportedBlock.getX()); buffer.writeInt(supportedBlock.getY()); buffer.writeInt(supportedBlock.getZ()); }
    }
    public static ChargingArcTarget decode(FriendlyByteBuf buffer) {
        var dimension = ResourceLocation.parse(buffer.readUtf(AbilitySnapshot.MAX_ID_LENGTH)); var player = buffer.readUUID();
        int entity = buffer.readVarInt(); long session = buffer.readLong(), revision = buffer.readLong();
        boolean traced = buffer.readBoolean(); var endpoint = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        var block = buffer.readBoolean() ? new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt()) : null;
        return new ChargingArcTarget(dimension, player, entity, session, revision, traced, endpoint, block);
    }
}
