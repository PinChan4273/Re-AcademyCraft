package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * 甲虫の空気砲撃の通知（サーバー→甲虫を見ている人、v1.5.0 §9）。判定はサーバーだけが持ち、描画はこの通知だけで作れる。
 * <ul>
 * <li>{@link #FIRE}: 発射。posは砲口（muzzle骨）の位置、directionは単位ベクトル、rangeは射程（ブロック）、speedはブロック/tick。</li>
 * <li>{@link #IMPACT}: 着弾または射程切れ。posは止まった点。directionは撃った向きのまま。rangeは使わない（0）。</li>
 * </ul>
 * shotIdは同じ弾のFIREとIMPACTの組（甲虫ごとではなくサーバー全体で増える）。
 */
public record DarkBeetleShot(int beetleId, int shotId, int kind, Vec3 pos, Vec3 direction, float range, float speed) implements AcademyPayload {
    public static final int FIRE = 0, IMPACT = 1;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(beetleId); buffer.writeVarInt(shotId); buffer.writeByte(kind);
        buffer.writeDouble(pos.x); buffer.writeDouble(pos.y); buffer.writeDouble(pos.z);
        buffer.writeFloat((float) direction.x); buffer.writeFloat((float) direction.y); buffer.writeFloat((float) direction.z);
        buffer.writeFloat(range); buffer.writeFloat(speed);
    }
    public static DarkBeetleShot decode(FriendlyByteBuf buffer) {
        int beetle = buffer.readVarInt(), shot = buffer.readVarInt(), kind = buffer.readByte();
        Vec3 pos = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3 direction = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        return new DarkBeetleShot(beetle, shot, kind, pos, direction, buffer.readFloat(), buffer.readFloat());
    }
}
