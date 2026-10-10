package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 原作MagMovement（WeAthFolD/KSkun）に対する、現行のサーバー側の動きのアダプタ。 */
@Mod.EventBusSubscriber(modid = "academy")
public final class MagneticMovement {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "mag_movement");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final class Session {
        final Level level;
        final Vec3 start;
        final MagneticTargets.Target target;
        final int preset, slot;
        final float proficiency;
        Vec3 motion = Vec3.ZERO, sent;
        long heartbeat, tick = Long.MIN_VALUE, visual;
        Session(ServerPlayer p, PlayerAbilityData d, int slot, MagneticTargets.Target target) {
            level = p.level(); start = p.position(); this.target = target;
            preset = d.getCurrentPreset(); this.slot = slot; proficiency = d.getProficiency(ID); heartbeat = level.getGameTime();
        }
    }
    private MagneticMovement() {}
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer p, PlayerAbilityData d) {
        return d != null && !d.isReadOnly() && p.isAlive() && d.isActive() && !d.isOverloadLocked()
                && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID) && p.containerMenu == p.inventoryMenu;
    }
    public static boolean isMoving(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    public static String start(ServerPlayer p, PlayerAbilityData d, int slot) {
        if (slot < 0 || slot >= 4 || d == null || d.isReadOnly() || !p.isAlive()
                || !ArcGen.CATEGORY.equals(d.getAbility()) || !d.hasLearned(ID)
                || !ID.equals(d.getSlot(d.getCurrentPreset(), slot))) return "academy.cast.unlearned";
        if (!d.isActive()) return "academy.cast.inactive";
        if (d.isOverloadLocked()) return "academy.cast.overload";
        if (p.containerMenu != p.inventoryMenu) return "academy.cast.unlearned";
        if (isMoving(p)) return "";
        if (!d.consume(ID, 0, ArcGen.lerp(60, 30, d.getProficiency(ID)), p.getAbilities().instabuild)) return "academy.cast.cp";
        var target = MagneticTargets.find(p);
        if (target == null) {
            // 原作のコンテキストは、最初の狙いが外れても終了のコールバックを実行する。
            d.addProficiency(ID, .005f); p.fallDistance = 0; return "academy.cast.no_metal";
        }
        var session = new Session(p, d, slot, target);
        ACTIVE.put(p.getUUID(), session);
        // 原作MSG_EFFECT_STARTとMSG_EFFECT_UPDATE: 近くのすべてのクライアントで電弧とem.move_loop。
        session.visual = io.github.pinchan4273.reacademycraft.network.SkillVisual.show(p, ID, 0, () -> ACTIVE.get(p.getUUID()) == session);
        session.sent = target.position();
        io.github.pinchan4273.reacademycraft.network.SkillVisual.move(session.visual, session.sent, 0);
        return "";
    }
    public static void renew(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) s.heartbeat = p.level().getGameTime();
    }
    public static void release(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) stop(p);
    }
    public static void stop(ServerPlayer p) {
        var s = ACTIVE.remove(p.getUUID()); if (s == null) return;
        var d = data(p);
        if (p.level() == s.level && d != null && !d.isReadOnly() && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID)) {
            d.addProficiency(ID, (float) Math.max(.005, .0011 * s.start.distanceTo(p.position())));
            p.fallDistance = 0; AbilitySyncEvents.sync(p, true);
        }
    }
    private static double adjust(double from, double to) { return from + Math.max(-.08, Math.min(.08, to - from)); }
    public static void tick(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID()); if (s == null) return; var d = data(p); long now = p.level().getGameTime();
        if (!allowed(p, d) || p.level() != s.level || now < s.heartbeat || now - s.heartbeat >= 40
                || d.getCurrentPreset() != s.preset || !ID.equals(d.getSlot(s.preset, s.slot)) || !s.target.alive(p)) { stop(p); return; }
        if (s.tick == now) return; s.tick = now;
        if (!d.consume(ID, ArcGen.lerp(15, 8, s.proficiency), 0, p.getAbilities().instabuild)) { stop(p); return; }
        Vec3 delta = s.target.position().subtract(p.position());
        // 原作の距離0での割り算がNaNの動きを生むのを避ける。
        if (delta.lengthSqr() < .0001) { p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true; stop(p); return; }
        Vec3 direction = delta.normalize();
        if (Math.abs(s.motion.lengthSqr() - p.getDeltaMovement().lengthSqr()) > .5) s.motion = p.getDeltaMovement();
        s.motion = new Vec3(adjust(s.motion.x, direction.x), adjust(s.motion.y, direction.y), adjust(s.motion.z, direction.z));
        p.setDeltaMovement(s.motion); p.hurtMarked = true;
        var target = s.target.position();
        if (s.sent == null || s.sent.distanceToSqr(target) > 1e-4) { s.sent = target; io.github.pinchan4273.reacademycraft.network.SkillVisual.move(s.visual, target, 0); }
        AbilitySyncEvents.sync(p, false);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopServer(ServerStoppedEvent e) { ACTIVE.clear(); }
}
