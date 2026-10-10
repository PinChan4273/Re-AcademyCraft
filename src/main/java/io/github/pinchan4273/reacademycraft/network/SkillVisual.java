package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 術者の近くの各クライアントでの技能の持続する表示（原作のクライアントのコンテキストが、そのコンテキストを持つ全員に描いたもの。
 * Storm Wingの竜巻と塵）: 術者と、その時点で50ブロック以内の各プレイヤーで、技能が渡す1つの数（Storm Wingの溜め時間）と共に
 * 開始し、技能自身の条件が成り立たなくなった最初のサーバーtickに、同じクライアントで終える。
 */
@Mod.EventBusSubscriber(modid = "academy")
public record SkillVisual(ResourceLocation dimension, int entityId, long token, @Nullable ResourceLocation kind, int value) {
    public static final double RANGE = 50;
    public boolean starts() { return kind != null; }
    public void encode(FriendlyByteBuf b) {
        b.writeResourceLocation(dimension); b.writeVarInt(entityId); b.writeLong(token); b.writeBoolean(kind != null);
        if (kind != null) { b.writeResourceLocation(kind); b.writeVarInt(value); }
    }
    public static SkillVisual decode(FriendlyByteBuf b) {
        var dimension = b.readResourceLocation(); int entity = b.readVarInt(); long token = b.readLong();
        if (!b.readBoolean()) return new SkillVisual(dimension, entity, token, null, 0);
        return new SkillVisual(dimension, entity, token, b.readResourceLocation(), b.readVarInt());
    }

    private record Showing(ServerPlayer caster, SkillVisual stop, List<ServerPlayer> viewers, BooleanSupplier lasts) { }
    private static final Map<Long, Showing> SHOWING = new LinkedHashMap<>();
    private static long nextToken;

    /** 術者と範囲内の全員で表示を開始する。lastsがfalseになると終わる。 */
    public static long show(ServerPlayer caster, ResourceLocation kind, int value, BooleanSupplier lasts) {
        long token = ++nextToken;
        var dimension = caster.level().dimension().location();
        var start = new SkillVisual(dimension, caster.getId(), token, kind, value);
        var viewers = new ArrayList<ServerPlayer>();
        viewers.add(caster);
        for (var player : caster.serverLevel().players())
            if (player != caster && player.distanceToSqr(caster) <= RANGE * RANGE) viewers.add(player);
        for (var viewer : viewers) send(viewer, start);
        SHOWING.put(token, new Showing(caster, new SkillVisual(dimension, caster.getId(), token, null, 0), viewers, lasts));
        return token;
    }
    /** 表示を開始したすべてのクライアントへ、現在の位置を伝える。 */
    public static void move(long token, net.minecraft.world.phys.Vec3 position, int phase) {
        var showing = SHOWING.get(token);
        if (showing == null) return;
        var packet = new SkillVisualMove(token, position, phase);
        for (var viewer : showing.viewers)
            if (viewer.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }
    private static void send(ServerPlayer player, SkillVisual packet) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    private static void check() {
        for (Iterator<Showing> it = SHOWING.values().iterator(); it.hasNext(); ) {
            var showing = it.next();
            if (showing.caster.isRemoved() || !showing.lasts.getAsBoolean()) {
                it.remove();
                for (var viewer : showing.viewers) send(viewer, showing.stop);
            }
        }
    }
    /** テスト用の入口: サーバー1tick分の確認。 */
    public static void tickForTest() { check(); }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) { if (event.phase == TickEvent.Phase.END) check(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SHOWING.clear(); }
}
