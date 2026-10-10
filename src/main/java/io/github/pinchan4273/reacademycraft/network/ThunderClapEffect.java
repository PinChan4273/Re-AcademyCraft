package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** サーバーが認めた見た目だけの雷。クライアントの対象・コストは無く、サーバーの天候エンティティも作らない。 */
public record ThunderClapEffect(ResourceLocation dimension, Vec3 position) {
    public ThunderClapEffect {
        Objects.requireNonNull(dimension); Objects.requireNonNull(position);
        if (!Double.isFinite(position.lengthSqr()) || Math.abs(position.x) > 30_000_000
                || Math.abs(position.z) > 30_000_000 || Math.abs(position.y) > 20_000_000)
            throw new IllegalArgumentException("Invalid lightning position");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeResourceLocation(dimension); b.writeDouble(position.x); b.writeDouble(position.y); b.writeDouble(position.z);
    }
    public static ThunderClapEffect decode(FriendlyByteBuf b) {
        return new ThunderClapEffect(b.readResourceLocation(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }
}
