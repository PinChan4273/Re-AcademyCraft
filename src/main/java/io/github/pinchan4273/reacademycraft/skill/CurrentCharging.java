package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopEffect;
import io.github.pinchan4273.reacademycraft.network.ChargingArcTarget;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作CurrentCharging（WeAthFolD、KSkun）。押している間の処理はサーバーで行い、数値と熟練度の増分は原作のもの。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class CurrentCharging {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "charging");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final int LEASE_TICKS = 40;
    private static long nextEffectSession;
    private static final class Session {
        final ServerLevel level;
        final UUID owner;
        final int entityId;
        final long effectSession;
        final Set<UUID> listeners = new LinkedHashSet<>();
        long lastEffect;
        long targetRevision;
        ChargingArcTarget target;
        final int preset, slot;
        final float proficiency;
        final boolean itemMode;
        long lastHeartbeat, lastTick = Long.MIN_VALUE;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.serverLevel(); preset = data.getCurrentPreset(); this.slot = slot;
            owner = player.getUUID(); entityId = player.getId(); effectSession = Math.incrementExact(nextEffectSession);
            nextEffectSession = effectSession; listeners.add(owner);
            for (var observer : level.players()) if (observer.distanceToSqr(player) <= 25 * 25) listeners.add(observer.getUUID());
            lastEffect = level.getGameTime();
            proficiency = data.getProficiency(ID);
            // モードはサーバーが決める。古いクライアントのisItemフラグは決して信用しない。
            itemMode = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            lastHeartbeat = level.getGameTime();
        }
    }
    private CurrentCharging() {}
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && !data.isReadOnly() && data.isActive()
                && ArcGen.CATEGORY.equals(data.getAbility()) && data.hasLearned(ID) && !data.isOverloadLocked()
                && player.containerMenu == player.inventoryMenu;
    }
    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !ArcGen.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        // 習得済みの技能は、オフやオーバーロードのロック中も習得済みのまま。ArcGenと同じく、学習が失われたと言わずに実際の制限を報告する。
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        float exp = data.getProficiency(ID);
        if (!data.consume(ID, 0, ArcGen.lerp(65, 48, exp), player.getAbilities().instabuild)) return "academy.cast.cp";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session); broadcast(session, true); return "";
    }
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && slot == session.slot) session.lastHeartbeat = player.level().getGameTime();
    }
    public static void release(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static boolean isCharging(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session != null) broadcast(session, false);
    }
    public static ChargingLoopEffect effect(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID()); return session == null ? null : effect(session, true);
    }
    /** 診断用の、現在のサーバーの判断の読み取り専用版。光線を追ったりエネルギーを移したりはしない。 */
    public static ChargingArcTarget target(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID()); return session == null ? null : session.target;
    }
    private static ChargingLoopEffect effect(Session session, boolean playing) {
        return new ChargingLoopEffect(session.level.dimension().location(), session.owner, session.entityId, session.effectSession, playing, session.itemMode);
    }
    private static void broadcast(Session session, boolean playing) {
        var packet = effect(session, playing);
        for (var id : session.listeners) {
            var observer = session.level.getServer().getPlayerList().getPlayer(id);
            if (observer != null && observer.level() == session.level) AcademyNetwork.send(observer, packet);
        }
    }
    public static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID()); if (session == null) return;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        long now = player.level().getGameTime();
        if (data == null || !allowed(player, data) || player.level() != session.level
                || data.getCurrentPreset() != session.preset || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.lastHeartbeat >= LEASE_TICKS || now < session.lastHeartbeat) { stop(player); return; }
        if (session.lastTick == now) return; session.lastTick = now;
        // ワールドを変える前に引き落とす: CPが尽きたときに最後の充電をただでさせない。
        if (!data.consume(ID, ArcGen.lerp(3, 7, session.proficiency), 0, player.getAbilities().instabuild)) { stop(player); return; }
        IEnergyStorage target = null;
        net.minecraft.world.phys.Vec3 endpoint = null;
        BlockPos hitBlock = null;
        boolean traced = false;
        if (session.itemMode) target = player.getMainHandItem().getCapability(ForgeCapabilities.ENERGY).orElse(null);
        else {
            var start = player.getEyePosition(); var direction = player.getLookAngle();
            endpoint = start.add(direction.scale(15));
            boolean loaded = true;
            for (int i = 0; i <= 15; i++)
                if (!player.level().hasChunkAt(BlockPos.containing(start.add(direction.scale(i))))) { loaded = false; break; }
            if (loaded) {
                traced = true;
                var hit = player.level().clip(new ClipContext(start, endpoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                // 原作Raytrace.traceLivingは術者以外のどのエンティティにも当たる: ブロックより手前にあれば電弧はそこで止まり、何も充電しない。
                var creature = ProjectileHitSeam.firstEntity(player.level(), player, start, hit.getLocation());
                endpoint = creature != null ? creature : hit.getLocation();
                if (creature == null && hit.getType() == HitResult.Type.BLOCK && player.level().mayInteract(player, hit.getBlockPos())) {
                    hitBlock = hit.getBlockPos();
                    var entity = player.level().getBlockEntity(hit.getBlockPos());
                    if (entity != null) target = entity.getCapability(ForgeCapabilities.ENERGY, hit.getDirection()).orElse(null);
                }
            }
        }
        boolean supported = target != null && target.canReceive();
        if (supported) {
            int ifAmount = (int) Math.floor(ArcGen.lerp(15, 35, session.proficiency));
            target.receiveEnergy(ifAmount * 4, false); // 原作RFSupport: 1 IF = 4 RF/FE。
            player.getInventory().setChanged();
        }
        data.addProficiency(ID, supported ? .0001f : .00003f);
        if (now - session.lastEffect >= 10) { session.lastEffect = now; broadcast(session, true); }
        if (!session.itemMode) {
            session.target = new ChargingArcTarget(session.level.dimension().location(), session.owner, session.entityId,
                    session.effectSession, ++session.targetRevision, traced, endpoint, supported ? hitBlock : null);
            // 最初の判断はすぐ、以降は2回に1回。許可の更新は対象より先に送る。
            if (session.targetRevision == 1 || session.targetRevision % 2 == 0) for (var id : session.listeners) {
                var observer = session.level.getServer().getPlayerList().getPlayer(id);
                if (observer != null && observer.level() == session.level) AcademyNetwork.send(observer, session.target);
            }
        }
        AbilitySyncEvents.sync(player, false);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) tick(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) stop(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) stop(player); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { if (event.getEntity() instanceof ServerPlayer player) stop(player); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); nextEffectSession = 0; }
}
