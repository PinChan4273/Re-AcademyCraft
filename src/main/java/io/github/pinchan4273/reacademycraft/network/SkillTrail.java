package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * テレポートの始点から、何かを置いた先までの原作のtp粒子の線: Threatening Teleportのアイテムが地面や犠牲者の頭へ向かう線と、
 * Shift Teleportのブロックが置かれた場所へ向かう線。原作のクライアントのコンテキストと同じく、各クライアントがその技能の
 * 間隔と広がりで線に沿って自身の粒子を並べる。
 */
public record SkillTrail(ResourceLocation dimension, Vec3 from, Vec3 to, ResourceLocation kind) {
    /** 原作の間隔: Threateningは粒子の間を1〜2ブロック、Shiftは0.6〜1。 */
    public static final ResourceLocation THREATENING = ResourceLocation.fromNamespaceAndPath("academy", "threatening_teleport");
    public static final ResourceLocation SHIFT = ResourceLocation.fromNamespaceAndPath("academy", "shift_teleport");
    public static final double RANGE = 50;

    public SkillTrail {
        Objects.requireNonNull(dimension); Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH
                || kind.toString().length() > AbilitySnapshot.MAX_ID_LENGTH
                || !bounded(from) || !bounded(to) || from.distanceToSqr(to) > 64 * 64)
            throw new IllegalArgumentException("Invalid skill trail");
    }
    private static boolean bounded(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                && Math.abs(value.x) <= 33_554_431 && Math.abs(value.y) <= 33_554_431 && Math.abs(value.z) <= 33_554_431;
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH);
        b.writeDouble(from.x); b.writeDouble(from.y); b.writeDouble(from.z);
        b.writeDouble(to.x); b.writeDouble(to.y); b.writeDouble(to.z);
        b.writeResourceLocation(kind);
    }
    public static SkillTrail decode(FriendlyByteBuf b) {
        return new SkillTrail(ResourceLocation.parse(b.readUtf(AbilitySnapshot.MAX_ID_LENGTH)),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readResourceLocation());
    }

    /** 術者と範囲内の全員へ伝える。テストの治具プレイヤーにも伝わるよう、術者には明示的に送る。 */
    public static void show(ServerPlayer caster, Vec3 from, Vec3 to, ResourceLocation kind) {
        if (from.distanceToSqr(to) > 64 * 64) return;
        var trail = new SkillTrail(caster.level().dimension().location(), from, to, kind);
        send(caster, trail);
        for (var observer : caster.serverLevel().players())
            if (observer != caster && observer.distanceToSqr(caster) <= RANGE * RANGE) send(observer, trail);
    }
    private static void send(ServerPlayer player, SkillTrail trail) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), trail);
    }
}
