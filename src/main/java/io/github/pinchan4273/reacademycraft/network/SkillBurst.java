package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

/**
 * 何かの上での1回限りの表示: 技能が当たった瞬間に、原作のクライアントのコンテキストが対象にいくつかのエンティティや粒子を
 * 出すもので、保持も終了も無い。何が誰に起きたかを示し、何をいくつ描くかはクライアントが決める（原作も各クライアントで
 * 自分で数を選んでいた）。
 *
 * 誰に伝えるかは原作と同じく種類で異なる: Flesh Rippingの血は術者の近くの全員へ、テレポーターのクリティカルは術者だけへ。
 */
public record SkillBurst(ResourceLocation dimension, int entityId, ResourceLocation kind, int value) {
    /** Flesh Rippingが引き裂いたものの位置のEntityBloodSplash。 */
    public static final ResourceLocation BLOOD = ResourceLocation.fromNamespaceAndPath("academy", "blood_splash");
    /** テレポーターの技能がクリティカルで当たった対象の周りの、CriticalHitEffectの式。 */
    public static final ResourceLocation CRITICAL = ResourceLocation.fromNamespaceAndPath("academy", "teleporter_critical");
    public static final double RANGE = 50;

    public SkillBurst {
        Objects.requireNonNull(dimension); Objects.requireNonNull(kind);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH
                || kind.toString().length() > AbilitySnapshot.MAX_ID_LENGTH
                || entityId < 0 || value < 0 || value > 255)
            throw new IllegalArgumentException("Invalid skill burst");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH);
        b.writeVarInt(entityId); b.writeResourceLocation(kind); b.writeByte(value);
    }
    public static SkillBurst decode(FriendlyByteBuf b) {
        return new SkillBurst(ResourceLocation.parse(b.readUtf(AbilitySnapshot.MAX_ID_LENGTH)),
                b.readVarInt(), b.readResourceLocation(), b.readUnsignedByte());
    }

    /** 術者と範囲内の全員へ伝える。テストの治具プレイヤーはlevelのプレイヤー一覧に居ないので、術者には明示的に送る。 */
    public static void showNear(ServerPlayer caster, Entity target, ResourceLocation kind, int value) {
        var burst = new SkillBurst(caster.level().dimension().location(), target.getId(), kind, value);
        send(caster, burst);
        for (var observer : caster.serverLevel().players())
            if (observer != caster && observer.distanceToSqr(caster) <= RANGE * RANGE) send(observer, burst);
    }
    /** 術者だけへ伝える。原作もクリティカルではそうする。 */
    public static void showTo(ServerPlayer caster, Entity target, ResourceLocation kind, int value) {
        send(caster, new SkillBurst(caster.level().dimension().location(), target.getId(), kind, value));
    }
    private static void send(ServerPlayer player, SkillBurst burst) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), burst);
    }
}
