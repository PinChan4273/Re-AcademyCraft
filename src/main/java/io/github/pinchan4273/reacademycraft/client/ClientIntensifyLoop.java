package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.network.IntensifyLoopEffect;
import io.github.pinchan4273.reacademycraft.skill.BodyIntensify;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
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

/** 原作の、このclientだけで鳴る停止できるループ音。完了の音は、既存のサーバーからの配信の経路で鳴らす。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientIntensifyLoop {
    private static final ChargingLoopLedger LEDGER = new ChargingLoopLedger();
    private static ClientLevel world;
    private static Loop sound;
    private static long ticks;
    private static int reloadGeneration;
    private ClientIntensifyLoop() { }
    private static final class Loop extends AbstractTickableSoundInstance {
        final Player owner;
        Loop(Player owner) {
            super(AcademySounds.EM_INTENSIFY_LOOP.get(), SoundSource.AMBIENT, RandomSource.create());
            this.owner = owner; looping = true; delay = 0; volume = 1; pitch = 1; position();
        }
        void position() { x = owner.getX(); y = owner.getY(); z = owner.getZ(); }
        boolean valid() { var c = Minecraft.getInstance(); return owner == c.player && owner.level() == c.level && owner.isAlive() && !owner.isRemoved(); }
        @Override public void tick() { if (!valid()) stop(); else position(); }
        void finish() { stop(); }
    }
    private static void currentWorld() { var level = Minecraft.getInstance().level; if (level != world) { reset(); world = level; } }
    private static boolean held() {
        var c = Minecraft.getInstance();
        if (c.player == null || c.screen != null || c.isPaused() || !c.isWindowActive()) return false;
        var d = c.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (d == null || !d.isActive() || d.isReadOnly() || d.isOverloadLocked()) return false;
        // Body Intensifyは切り替え型: キーを押しているかに関係なく、サーバーが予備動作中と伝えている間ループする。
        int slot = ClientBodyIntensify.slot();
        return ClientBodyIntensify.warmingUp() && slot >= 0 && BodyIntensify.ID.equals(d.getSlot(d.getCurrentPreset(), slot));
    }
    public static void receive(IntensifyLoopEffect packet) {
        currentWorld(); var c = Minecraft.getInstance();
        // 観測者向けのパケットは、開始・停止・記録の枠の占有・術者の音の置き換えのどれもできない。
        if (c.level == null || c.player == null || !c.level.dimension().location().equals(packet.dimension())
                || !c.player.getUUID().equals(packet.player()) || c.player.getId() != packet.entityId()) return;
        var decision = LEDGER.accept(packet.grant(), ticks);
        if (decision == ChargingLoopLedger.Decision.IGNORE) return;
        if (decision == ChargingLoopLedger.Decision.STOP) { stopSound(); return; }
        if (!held()) { LEDGER.cancel(packet.player()); stopSound(); return; }
        if (c.getOverlay() != null) { stopSound(); return; }
        if (decision == ChargingLoopLedger.Decision.START) stopSound();
        if (sound == null) { sound = new Loop(c.player); c.getSoundManager().play(sound); }
    }
    private static void stopSound() {
        if (sound != null) { sound.finish(); Minecraft.getInstance().getSoundManager().stop(sound); sound = null; }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld(); ticks++; var c = Minecraft.getInstance();
        if (!LEDGER.expire(ticks).isEmpty()) stopSound();
        if (sound == null) return;
        if (!sound.valid() || sound.isStopped() || c.getOverlay() != null) stopSound();
        else if (!held()) { LEDGER.cancel(c.player.getUUID()); stopSound(); }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class IntensifyLoopReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { reset(); reloadGeneration++; });
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void reset() { stopSound(); LEDGER.clear(); world = null; ticks = 0; }
    public static boolean isPlaying() { return sound != null && !sound.isStopped() && Minecraft.getInstance().getSoundManager().isActive(sound); }
    public static net.minecraft.client.resources.sounds.SoundInstance sound() { return sound; }
    public static int activeSounds() { return sound == null ? 0 : 1; }
    public static int reloadGeneration() { return reloadGeneration; }
}
