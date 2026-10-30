package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.FollowSound;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * このclientでの原作FollowEntitySound: 各音は、サーバーが終わりを伝えるか、entityが消えるか、
 * このclientがそのワールドを離れるまで、entityに付いて回る。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientFollowSounds {
    private static final Map<Long, Follow> PLAYING = new HashMap<>();
    private static ClientLevel world;
    private ClientFollowSounds() { }

    private static final class Follow extends AbstractTickableSoundInstance {
        final Entity entity; final ResourceLocation id;
        Follow(Entity entity, ResourceLocation id, FollowSound packet) {
            super(SoundEvent.createVariableRangeEvent(id), packet.source(), RandomSource.create());
            this.entity = entity; this.id = id;
            looping = packet.loop(); delay = 0; volume = packet.volume(); pitch = 1; follow();
        }
        void follow() { x = entity.getX(); y = entity.getY(); z = entity.getZ(); }
        @Override public void tick() { if (entity.isRemoved() || entity.level() != Minecraft.getInstance().level) stop(); else follow(); }
        void end() { stop(); }
    }

    public static void receive(FollowSound packet) {
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) { var follow = PLAYING.remove(packet.token()); if (follow != null) stopSound(follow); return; }
        var entity = level.getEntity(packet.entityId());
        if (entity == null) return;
        var follow = new Follow(entity, packet.sound(), packet);
        var previous = PLAYING.put(packet.token(), follow);
        if (previous != null) stopSound(previous);
        client.getSoundManager().play(follow);
    }
    private static void stopSound(Follow follow) { follow.end(); Minecraft.getInstance().getSoundManager().stop(follow); }
    private static void clear() { PLAYING.values().forEach(ClientFollowSounds::stopSound); PLAYING.clear(); }

    /** テスト用の入口: このidの音が、今このclientで何かに付いて回っているか。 */
    public static boolean playing(ResourceLocation sound) {
        return PLAYING.values().stream().anyMatch(f -> f.id.equals(sound) && !f.isStopped());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; return; }
        PLAYING.values().removeIf(follow -> {
            boolean over = follow.isStopped() && !follow.isLooping();
            if (follow.entity.isRemoved()) { stopSound(follow); return true; }
            return over;
        });
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }
}
