package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.media.MediaAcquired;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作MediaAcquireDataは所有者へ同期される。これも同じこと（mod自身のメディアのうちどれを持っているか）を伝える。
 * ログイン時、戻ってきたとき、変わったときに送る。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public record MediaSnapshot(List<String> ids) implements AcademyPayload {
    public static final int MAX_NAME = 32;
    public MediaSnapshot {
        Objects.requireNonNull(ids);
        if (ids.size() > MediaAcquired.INTERNAL.size()) throw new IllegalArgumentException("Too many media");
        for (var id : ids) if (id == null || id.isEmpty() || id.length() > MAX_NAME) throw new IllegalArgumentException("Invalid media id");
        ids = List.copyOf(ids);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(ids.size());
        for (var id : ids) b.writeUtf(id, MAX_NAME);
    }
    public static MediaSnapshot decode(FriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > MediaAcquired.INTERNAL.size()) throw new io.netty.handler.codec.DecoderException("Too many media");
        var ids = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) ids.add(b.readUtf(MAX_NAME));
        return new MediaSnapshot(ids);
    }
    public static void send(ServerPlayer player) {
        if (player == null || player.connection == null) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new MediaSnapshot(List.copyOf(MediaAcquired.installed(player))));
    }
    public void handle(net.minecraft.world.entity.player.Player player) { MediaAcquired.accept(player, ids); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    /** 原作のデータ部分は死亡とエンドからの帰還を越えて残るので、リストを引き継ぐ。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        // 原作LambdaLib2のEntityData.onPlayerCloneは、死亡かどうかを問わずすべてのデータ部分を写すので、エンドからの帰還でも残る
        // （Forge自身はPlayerPersistedしか引き継がない）。
        var old = event.getOriginal().getPersistentData();
        if (old.contains(MediaAcquired.key())) event.getEntity().getPersistentData().put(MediaAcquired.key(), old.get(MediaAcquired.key()).copy());
    }
}
