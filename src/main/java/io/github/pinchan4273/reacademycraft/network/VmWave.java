package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * 術者の近くの全員への原作WaveEffect: 爆発や反射が起きた所で広がり前へ漂う、ベクトル操作のglow_circleの輪。原作は
 * コンテキストのメッセージから各クライアントで生成し、輪と揺れは各クライアントの乱数だったので、パケットはサーバーが決める
 * ものだけを運び、残りは各クライアントが決める。
 */
public record VmWave(ResourceLocation dimension, Vec3 position, float yaw, float pitch, float yawJitter, float pitchJitter,
                     int rings, float size) {
    /** 原作のコンテキストは、術者から50ブロック以内のクライアントへ届いた。 */
    public static final double RANGE = 50;
    public VmWave {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z) || !Float.isFinite(yaw) || !Float.isFinite(pitch) || rings < 0 || rings > 8
                || !(size > 0 && size < 8) || !(yawJitter >= 0 && yawJitter <= 90) || !(pitchJitter >= 0 && pitchJitter <= 90))
            throw new IllegalArgumentException("Invalid wave effect");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeResourceLocation(dimension); b.writeDouble(position.x); b.writeDouble(position.y); b.writeDouble(position.z);
        b.writeFloat(yaw); b.writeFloat(pitch); b.writeFloat(yawJitter); b.writeFloat(pitchJitter); b.writeVarInt(rings); b.writeFloat(size);
    }
    public static VmWave decode(FriendlyByteBuf b) {
        return new VmWave(b.readResourceLocation(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readFloat());
    }
    /** 術者と、術者から範囲内のすべてのプレイヤーへ波を送る。 */
    public static void send(ServerPlayer caster, VmWave wave) {
        send(caster, caster, wave);
        for (var player : caster.serverLevel().players())
            if (player != caster && player.distanceToSqr(caster) <= RANGE * RANGE) send(player, caster, wave);
    }
    private static void send(ServerPlayer player, ServerPlayer caster, VmWave wave) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), wave);
    }
}
