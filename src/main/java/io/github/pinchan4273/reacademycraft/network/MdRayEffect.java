package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * ビームの不変のスナップショット: 始点、狙う先、原作のどの光線か（Electron Bomb・Scatter Bomb・Electron Missileが撃つ
 * 小さな光線、Meltdowner自身のもの、Ray Barrageの対、Railgunのもの）。
 * 当たり・ダメージ・資源の決定権は持たない。それらはサーバーが既に解決している。
 */
public record MdRayEffect(ResourceLocation dimension, UUID player, int entityId, long session,
                          Vec3 origin, Vec3 target, int kind) {
    public static final int SMALL = 0, MELTDOWNER = 1;
    /**
     * Ray Barrageの前段の光線（Silbarnに当たった場合と当たらない場合）と、散った光線そのもの。
     * 散る一斉射は始点と術者の視線の向きを運び、各クライアントがその周りに自身の光線を広げる（原作のEntityMdRayBarrageと同じ）。
     */
    public static final int BARRAGE_PRE = 2, BARRAGE_PRE_HIT = 3, BARRAGE = 4;
    /** EntityRailgunFX。長さ45ブロックなので、下の範囲は広い。 */
    public static final int RAILGUN = 5;
    public MdRayEffect {
        Objects.requireNonNull(dimension); Objects.requireNonNull(player);
        Objects.requireNonNull(origin); Objects.requireNonNull(target);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH || entityId < 0 || session <= 0
                || !bounded(origin) || !bounded(target) || origin.distanceToSqr(target) > 64 * 64
                || kind < SMALL || kind > RAILGUN)
            throw new IllegalArgumentException("Invalid meltdowner beam");
    }
    /** 小さな光線。Meltdownerが独自のビームを持つ前は、すべての呼び出し元がこれを指していた。 */
    public MdRayEffect(ResourceLocation dimension, UUID player, int entityId, long session, Vec3 origin, Vec3 target) {
        this(dimension, player, entityId, session, origin, target, SMALL);
    }
    private static boolean bounded(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                && Math.abs(value.x) <= 33_554_431 && Math.abs(value.y) <= 33_554_431 && Math.abs(value.z) <= 33_554_431;
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH); b.writeUUID(player);
        b.writeVarInt(entityId); b.writeLong(session);
        b.writeDouble(origin.x); b.writeDouble(origin.y); b.writeDouble(origin.z);
        b.writeDouble(target.x); b.writeDouble(target.y); b.writeDouble(target.z);
        b.writeByte(kind);
    }
    public static MdRayEffect decode(FriendlyByteBuf b) {
        return new MdRayEffect(ResourceLocation.parse(b.readUtf(AbilitySnapshot.MAX_ID_LENGTH)), b.readUUID(),
                b.readVarInt(), b.readLong(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readUnsignedByte());
    }
}
