package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopEffect;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.skill.CurrentCharging;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 原作の、entityに付いて回るAMBIENTのループ音。受理と、どの観測者に鳴らすかはサーバーが決める。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientChargingLoop {
    private static final ChargingLoopLedger LEDGER = new ChargingLoopLedger();
    private static final Map<UUID, Loop> LOOPS = new HashMap<>();
    private static ClientLevel world;
    private static long ticks;
    private static int reloadGeneration;
    private ClientChargingLoop() { }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Player source;
        Loop(Player source) {
            super(AcademySounds.EM_CHARGE_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
            this.source = source; looping = true; delay = 0; volume = .3f; pitch = 1;
            updatePosition();
        }
        private void updatePosition() { x = source.getX(); y = source.getY(); z = source.getZ(); }
        boolean valid() { return source.isAlive() && !source.isRemoved() && source.level() == Minecraft.getInstance().level; }
        @Override public void tick() { if (!valid()) stop(); else updatePosition(); }
        void finish() { stop(); }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    // EventBus6は、入れ子のクラスの単純名からwrapperの名前を作る。package内で重ならない名前にしておく。
    public static final class ChargingLoopReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { reset(); reloadGeneration++; });
        }
    }
    static boolean localHeld() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || client.isPaused() || !client.isWindowActive()) return false;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.isActive() || data.isReadOnly() || data.isOverloadLocked()) return false;
        for (int slot = 0; slot < AbilityControls.SLOTS.length; slot++)
            if (CurrentCharging.ID.equals(data.getSlot(data.getCurrentPreset(), slot)) && AbilityControls.SLOTS[slot].isDown()) return true;
        return false;
    }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
    }
    public static void receive(ChargingLoopEffect packet) {
        var client = Minecraft.getInstance(); currentWorld();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(packet.dimension())) return;
        var decision = LEDGER.accept(packet, ticks);
        if (decision == ChargingLoopLedger.Decision.IGNORE) return;
        if (decision == ChargingLoopLedger.Decision.STOP) { stopSound(packet.player()); return; }
        var entity = client.level.getEntity(packet.entityId());
        if (!(entity instanceof Player source) || !source.getUUID().equals(packet.player()) || !source.isAlive()) {
            stopSound(packet.player()); return;
        }
        if (source == client.player && !localHeld()) { LEDGER.cancel(packet.player()); stopSound(packet.player()); return; }
        if (client.getOverlay() != null) { stopSound(packet.player()); return; }
        if (decision == ChargingLoopLedger.Decision.START) stopSound(packet.player());
        if (!LOOPS.containsKey(packet.player())) {
            var sound = new Loop(source); LOOPS.put(packet.player(), sound); client.getSoundManager().play(sound);
        }
    }
    private static void stopSound(UUID player) {
        var sound = LOOPS.remove(player);
        if (sound != null) { sound.finish(); Minecraft.getInstance().getSoundManager().stop(sound); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld(); ticks++;
        var client = Minecraft.getInstance();
        for (var id : LEDGER.expire(ticks)) stopSound(id);
        for (var id : List.copyOf(LOOPS.keySet())) {
            var sound = LOOPS.get(id);
            if (!sound.valid() || sound.isStopped() || client.getOverlay() != null) stopSound(id);
            else if (client.player != null && id.equals(client.player.getUUID()) && !localHeld()) {
                LEDGER.cancel(id); stopSound(id);
            }
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void reset() {
        for (var id : List.copyOf(LOOPS.keySet())) stopSound(id);
        LEDGER.clear(); world = null; ticks = 0;
    }
    public static boolean isPlaying(UUID player) {
        var sound = LOOPS.get(player);
        return sound != null && !sound.isStopped() && Minecraft.getInstance().getSoundManager().isActive(sound);
    }
    /** native channelの寿命を調べるための、読み取り専用の観測点。 */
    public static net.minecraft.client.resources.sounds.SoundInstance sound(UUID player) { return LOOPS.get(player); }
    public static int activeSounds() { return LOOPS.size(); }
    public static int reloadGeneration() { return reloadGeneration; }
}
