package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.ThunderClapPresentationEffect;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 現行の雷・エンティティのAPIに、原作ThunderClapContextのタイミングと直線的な範囲攻撃を合わせたもの（WeAthFolD/KSkun）。 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ThunderClap {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "thunder_clap");
    public static final UUID SPEED_MODIFIER = UUID.fromString("38b281b8-9d51-4d72-aa5f-0cab75bead21");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static long nextPresentationSession;
    private static final class Session {
        final ServerPlayer owner; final ServerLevel level; final int preset, slot; final float proficiency;
        final UUID ownerId; final int entityId; final long presentationSession;
        final Set<UUID> listeners = new LinkedHashSet<>();
        int ticks, revision; long heartbeat, lastTick = Long.MIN_VALUE; Vec3 aim;
        Session(ServerPlayer p, PlayerAbilityData d, int slot, Vec3 initialAim) {
            owner = p; level = p.serverLevel(); preset = d.getCurrentPreset(); this.slot = slot;
            proficiency = d.getProficiency(ID); heartbeat = level.getGameTime(); aim = initialAim;
            ownerId = p.getUUID(); entityId = p.getId();
            presentationSession = Math.incrementExact(nextPresentationSession); nextPresentationSession = presentationSession;
            listeners.add(ownerId);
            for (var observer : level.players()) if (observer.distanceToSqr(p) <= 25 * 25) listeners.add(observer.getUUID());
        }
    }
    private ThunderClap() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer p, PlayerAbilityData d) {
        return d != null && !d.isReadOnly() && p.isAlive() && d.isActive() && !d.isOverloadLocked()
                && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID) && p.containerMenu == p.inventoryMenu;
    }
    public static boolean isCharging(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    public static String start(ServerPlayer p, PlayerAbilityData d, int slot) {
        if (slot < 0 || slot >= 4 || d == null || d.isReadOnly() || !p.isAlive()
                || !ArcGen.CATEGORY.equals(d.getAbility()) || !d.hasLearned(ID)
                || !ID.equals(d.getSlot(d.getCurrentPreset(), slot))) return "academy.cast.unlearned";
        if (!d.isActive()) return "academy.cast.inactive";
        if (d.isOverloadLocked()) return "academy.cast.overload";
        if (p.containerMenu != p.inventoryMenu) return "academy.cast.unlearned";
        if (isCharging(p)) return "";
        if (d.getCooldown(ID) > 0) return "academy.cast.cooldown";
        var initialAim = aim(p); if (initialAim == null) return "academy.cast.unloaded";
        if (!d.consume(ID, 0, ArcGen.lerp(390, 252, d.getProficiency(ID)), p.getAbilities().instabuild)) return "academy.cast.cp";
        var session = new Session(p, d, slot, initialAim); ACTIVE.put(p.getUUID(), session);
        publish(session, ThunderClapPresentationEffect.Action.START); return "";
    }
    public static void renew(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.owner == p && s.slot == slot) s.heartbeat = p.level().getGameTime();
    }
    public static void stop(ServerPlayer p) {
        var s = ACTIVE.remove(p.getUUID());
        if (s != null) { publish(s, ThunderClapPresentationEffect.Action.STOP); clearSpeed(s.owner); }
        clearSpeed(p);
    }
    public static void release(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) stop(p); // 原作のKEYUPは終了させるだけで、早く撃つことはない。
    }
    private static void clearSpeed(ServerPlayer p) {
        var attribute = p.getAttribute(Attributes.MOVEMENT_SPEED); if (attribute != null) attribute.removeModifier(SPEED_MODIFIER);
    }
    public static void tick(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID()); if (s == null) return; var d = data(p); long now = p.level().getGameTime();
        if (!allowed(p, d) || s.owner != p || p.level() != s.level || now < s.heartbeat || now - s.heartbeat >= 40
                || s.preset != d.getCurrentPreset() || !ID.equals(d.getSlot(s.preset, s.slot)) || d.getCooldown(ID) > 0) { stop(p); return; }
        if (s.lastTick == now) return; s.lastTick = now;
        s.aim = aim(p); if (s.aim == null) { stop(p); return; }
        // 完全に払ったtickだけを数える。古いコードでは40回目の支払いに失敗しても落雷することがあった。
        if (s.ticks < 40 && !d.consume(ID, ArcGen.lerp(18, 25, s.proficiency), 0, p.getAbilities().instabuild)) { stop(p); return; }
        ++s.ticks;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER);
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, "Academy Thunder Clap charge", -.99 * s.ticks / 60, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (s.ticks % 2 == 0) publish(s, ThunderClapPresentationEffect.Action.UPDATE);
        if (s.ticks >= 60) { stop(p); strike(p, d, s); }
        else AbilitySyncEvents.sync(p, false);
    }
    /** ブロックとの正確な交点、または40mの終点。エンティティの形は狙いを遮らない。 */
    public static Vec3 aim(ServerPlayer p) {
        var start = p.getEyePosition(); var direction = p.getLookAngle(); var end = start.add(direction.scale(40));
        if (!Double.isFinite(start.lengthSqr()) || !Double.isFinite(end.lengthSqr())) return null;
        var low = BlockPos.containing(Math.min(start.x, end.x) - 1, 0, Math.min(start.z, end.z) - 1);
        var high = BlockPos.containing(Math.max(start.x, end.x) + 1, 0, Math.max(start.z, end.z) + 1);
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++)
            if (!p.serverLevel().hasChunkAt(new BlockPos(x << 4, 0, z << 4))) return null;
        var hit = p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
    /** テスト・診断用の、サーバーが認めた現在の表示状態の読み取り専用版。 */
    public static ThunderClapPresentationEffect presentation(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? null : effect(session, ThunderClapPresentationEffect.Action.UPDATE, session.revision + 1);
    }
    private static ThunderClapPresentationEffect effect(Session session, ThunderClapPresentationEffect.Action action, int revision) {
        return new ThunderClapPresentationEffect(session.level.dimension().location(), session.ownerId, session.entityId,
                session.presentationSession, revision, action, session.aim);
    }
    private static void publish(Session session, ThunderClapPresentationEffect.Action action) {
        int revision = action == ThunderClapPresentationEffect.Action.START ? 0 : ++session.revision;
        var packet = effect(session, action, revision);
        for (var id : session.listeners) {
            var observer = session.level.getServer().getPlayerList().getPlayer(id);
            if (observer != null && observer.level() == session.level) AcademyNetwork.send(observer, packet);
        }
    }
    private static void strike(ServerPlayer p, PlayerAbilityData d, Session s) {
        var level = p.serverLevel(); float range = ArcGen.lerp(15, 30, s.proficiency);
        float damage = ArcGen.lerp(36, 72, s.proficiency) * (1 + .2f * (s.ticks - 40) / 60);
        d.setCooldown(ID, (int) (s.ticks * ArcGen.lerp(10, 6, s.proficiency)));
        // setVisualOnlyだけでは避雷針や銅のサーバー側の動作を抑えられない。
        // バニラの雷はクライアントでだけ描き、ゲームプレイはこの技能の範囲攻撃のまま。
        io.github.pinchan4273.reacademycraft.network.AcademyNetwork.lightning(level, s.aim);
        for (var target : level.getEntities(p, new AABB(s.aim, s.aim).inflate(range), e -> e.isAlive() && !e.isSpectator())) {
            if (!SkillCombat.mayAttack(p, target, ID)) continue;
            double distance = target.position().distanceTo(s.aim);
            if (distance < range) SkillCombat.attack(p, target, ID, damage * (float) (1 - distance / range));
        }
        d.addProficiency(ID, .003f); AbilitySyncEvents.sync(p, true);
        // START/UPDATE/STOPの表示は別に送る。バニラの雷は、落雷だけのディメンションに結び付いた効果のまま。
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) stop(p); if (e.getEntity() instanceof ServerPlayer p) clearSpeed(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        ACTIVE.values().forEach(s -> clearSpeed(s.owner)); ACTIVE.clear(); nextPresentationSession = 0;
    }
}
