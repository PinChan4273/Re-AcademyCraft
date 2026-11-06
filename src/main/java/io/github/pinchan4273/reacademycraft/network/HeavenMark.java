package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 怪現象の演出の通知（サーバー→見えている人、v1.5.0 §12）。判定はサーバーだけが持つ。
 * <ul>
 * <li>{@link #TELEGRAPH}: 予兆のリング（ticksの間）。</li>
 * <li>{@link #GLOW}: 発光の輪郭（ticksの間。1秒ごとに送り直す）。{@link #GLOW_END}で消える。</li>
 * <li>{@link #LIGHTNING}・{@link #STATUS}・{@link #MUTATE}: 発生の瞬間の演出。予兆のリングはこれで消える。</li>
 * </ul>
 */
public record HeavenMark(int entityId, int kind, int ticks) implements AcademyPayload {
    public static final int TELEGRAPH = 0, GLOW = 1, GLOW_END = 2, LIGHTNING = 3, STATUS = 4, MUTATE = 5;
    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(entityId); buffer.writeByte(kind); buffer.writeVarInt(ticks); }
    public static HeavenMark decode(FriendlyByteBuf buffer) { return new HeavenMark(buffer.readVarInt(), buffer.readByte(), buffer.readVarInt()); }
}
