package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 新しいForgeの永続的な積荷の上での、原作MagManip（WeAthFolD/Paindar）のゲームプレイ。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class MagneticManipulation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "mag_manip");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final class Session {
        final Level level; final MagneticBlockEntity entity; final int preset, slot; final float proficiency;
        long heartbeat;
        Session(ServerPlayer p, PlayerAbilityData d, int slot, MagneticBlockEntity e) {
            level = p.level(); entity = e; preset = d.getCurrentPreset(); this.slot = slot;
            proficiency = d.getProficiency(ID); heartbeat = level.getGameTime();
        }
    }
    private MagneticManipulation() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer p, PlayerAbilityData d) {
        return d != null && !d.isReadOnly() && p.isAlive() && d.isActive() && !d.isOverloadLocked()
                && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID) && p.containerMenu == p.inventoryMenu;
    }
    public static boolean isHolding(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    public static String start(ServerPlayer p, PlayerAbilityData d, int slot) {
        if (slot < 0 || slot >= 4 || d == null || d.isReadOnly() || !p.isAlive()
                || !ArcGen.CATEGORY.equals(d.getAbility()) || !d.hasLearned(ID)
                || !ID.equals(d.getSlot(d.getCurrentPreset(), slot))) return "academy.cast.unlearned";
        if (!d.isActive()) return "academy.cast.inactive";
        if (d.isOverloadLocked()) return "academy.cast.overload";
        if (p.containerMenu != p.inventoryMenu) return "academy.cast.unlearned";
        if (isHolding(p)) return "";
        if (d.getCooldown(ID) > 0) return "academy.cast.cooldown";
        java.util.Optional<MagneticBlockEntity> entity;
        if (p.getMainHandItem().getItem() instanceof BlockItem item && MagneticTargets.isMetal(item.getBlock().defaultBlockState())) {
            // 持っている金属のNBTを保てないなら、ワールドのブロックを黙って動かさない。
            entity = MagneticBlockEntity.takeHand(p);
        } else {
            var from = p.getEyePosition(); var direction = p.getLookAngle(); var end = from.add(direction.scale(10));
            for (int i = 0; i <= 10; i++) if (!p.level().hasChunkAt(BlockPos.containing(from.add(direction.scale(i))))) return "academy.cast.no_metal";
            // 原作traceLiving(player, 10, nothing, accepts): LambdaLib2のRaytraceは選別が拒むブロックを1つずつ通り抜けるので、金属でないものは
            // 通過し、10以内の最初の金属ブロックを取る（壁の向こうでも）。1.12のcollisionRayTraceが外形の箱を使ったのと同じく、金属ブロックは外形で当てる。
            BlockPos metal = net.minecraft.world.level.BlockGetter.traverseBlocks(from, end, p.level(), (level, pos) -> {
                var state = level.getBlockState(pos);
                if (!MagneticTargets.isMetal(state)) return null;
                return state.getShape(level, pos).clip(from, end, pos) != null ? pos.immutable() : null;
            }, level -> null);
            if (metal == null) return "academy.cast.no_metal";
            entity = MagneticBlockEntity.take(p, metal);
        }
        if (entity.isEmpty()) return "academy.cast.block_denied";
        entity.get().hold(p);
        var session = new Session(p, d, slot, entity.get());
        ACTIVE.put(p.getUUID(), session);
        // 原作MagManipContextC: コンテキストが生きている間、em.lf_loopが術者に付いて行く。
        io.github.pinchan4273.reacademycraft.network.SkillVisual.show(p, ID, 0, () -> ACTIVE.get(p.getUUID()) == session);
        return "";
    }
    public static void renew(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) s.heartbeat = p.level().getGameTime();
    }
    public static void cancel(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) stop(p);
    }
    public static void stop(ServerPlayer p) {
        var s = ACTIVE.remove(p.getUUID()); if (s != null) s.entity.drop();
    }
    public static void release(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s == null || s.slot != slot) return;
        ACTIVE.remove(p.getUUID()); s.entity.drop(); var d = data(p);
        long now = p.level().getGameTime();
        if (!allowed(p, d) || p.level() != s.level || d.getCurrentPreset() != s.preset || !ID.equals(d.getSlot(s.preset, s.slot))
                || now < s.heartbeat || now - s.heartbeat >= 40
                || !s.entity.isCarrying() || p.distanceToSqr(s.entity) >= 25) return;
        var from = p.getEyePosition(); var to = from.add(p.getLookAngle().scale(20));
        for (int i = 1; i <= 20; i++) if (!p.level().hasChunkAt(BlockPos.containing(from.add(p.getLookAngle().scale(i))))) {
            to = from.add(p.getLookAngle().scale(i - 1)); break;
        }
        var hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        // 原作Raytrace.getLookingPos(player, 20): 術者以外のどのエンティティも数え、当たったものはその点から目の高さの0.6上を狙う。
        var creature = ProjectileHitSeam.first(p.level(), p, from, hit.getLocation());
        var aim = creature == null ? hit.getLocation() : creature.point().add(0, creature.entity().getEyeHeight() * .6, 0);
        var delta = aim.subtract(s.entity.position());
        if (delta.lengthSqr() < 1.0e-8 || !d.consume(ID, ArcGen.lerp(140, 270, s.proficiency), ArcGen.lerp(35, 20, s.proficiency), p.getAbilities().instabuild)) return;
        s.entity.throwFrom(p, delta.normalize().scale(ArcGen.lerp(.5f, 1, s.proficiency)));
        // すべてのクライアントでの原作c_perform: 術者の位置でem.mag_manip。
        p.level().playSound(null, p, io.github.pinchan4273.reacademycraft.world.AcademySounds.EM_MAG_MANIP.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, 1f);
        d.setCooldown(ID, (int)ArcGen.lerp(60, 40, s.proficiency)); d.addProficiency(ID, .005f); AbilitySyncEvents.sync(p, true);
    }
    public static void tick(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID()); if (s == null) return; var d = data(p); long now = p.level().getGameTime();
        if (!allowed(p, d) || p.level() != s.level || now < s.heartbeat || now - s.heartbeat >= 40 || !s.entity.isCarrying()
                || d.getCurrentPreset() != s.preset || !ID.equals(d.getSlot(s.preset, s.slot))) { stop(p); return; }
        s.entity.hold(p);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void stopServer(ServerStoppedEvent e) { ACTIVE.values().forEach(s -> s.entity.drop()); ACTIVE.clear(); }
}
