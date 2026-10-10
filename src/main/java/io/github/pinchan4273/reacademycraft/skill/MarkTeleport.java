package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作MarkTeleport（WeAthFolD、KSkun）: テレポーターのレベル2技能。
 *
 * 押し続けて印を付け、離すと印へテレポートする。印は術者の見ている所にあるが、届く距離はキーを押している間に伸びる
 * （1tickに2ブロック、lerp(25, 60, exp)とCPで払える分まで）ので、軽く押しただけではほとんど届かない。3ブロックより近い印では何もしない。
 * ブロックの面に着くと、原作独自のずれで術者をその横か上に置く。エンティティに着くと、術者の足をそのエンティティ自身の目の高さに置く。
 *
 * 後払いで原作の強制consumeを使い、1ブロックごとにlerp(12, 4, exp)のCPと、lerp(40, 20, exp)のオーバーロード。
 * クールダウンlerp(30, 0, exp)は熟練度最大で0になる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class MarkTeleport {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "mark_teleport");
    /** 原作MINIMUM_VALID_DISTANCE。 */
    public static final double MINIMUM_DISTANCE = 3;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; long heartbeat; int ticks;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
        }
    }

    private MarkTeleport() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    /** 原作getCPB: 1ブロックあたりのCP。 */
    public static float consumption(float exp) { return ArcGen.lerp(12, 4, exp); }
    /** 原作getMaxDist: 押したtickごとに2ブロック。熟練度とCPで上限を設ける。 */
    public static double maxDistance(float exp, float cp, int ticks) {
        return Math.min((ticks + 1) * 2, Math.min(ArcGen.lerp(25, 60, exp), cp / consumption(exp)));
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // 術者のクライアントへの原作MSG_MADEALIVE: このセッションが生きている間、照準の印を表示する。
        io.github.pinchan4273.reacademycraft.network.AimState.announce(player, ID, () -> ACTIVE.get(player.getUUID()) == session);
        return "";
    }
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }
    public static void release(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()) return;
        execute(player, data, session.ticks);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? 0 : s.ticks; }

    private static void execute(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(ID);
        var dest = destination(player, data, ticks);
        double distance = dest.distanceTo(player.position());
        if (distance < MINIMUM_DISTANCE) return;
        var target = TPSkillHelper.permit(player, dest);
        if (target == null) return;
        distance = target.distanceTo(player.position());
        data.consumeWithForce(ID, (float) (distance * consumption(exp)), ArcGen.lerp(40, 20, exp), player.getAbilities().instabuild);
        TPSkillHelper.teleport(player, target);
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_TP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        data.addProficiency(ID, (float) (.00018 * distance));
        data.setCooldown(ID, (int) ArcGen.lerp(30, 0, exp));
        TPSkillHelper.incrTPCount(player);
        AbilitySyncEvents.sync(player, true);
    }

    /**
     * 原作getDest。ここでの原作Raytrace.traceLivingは、生き物だけでなく術者以外のどのエンティティも取る。それに着くと、術者を
     * そのエンティティ自身の位置（TPSkillHelper.traceLivingを参照）を目の高さだけ上げた所に置く。
     */
    public static Vec3 destination(net.minecraft.world.entity.player.Player player, PlayerAbilityData data, int ticks) {
        var trace = TPSkillHelper.traceLiving(player, maxDistance(data.getProficiency(ID), data.getCp(), ticks), e -> true);
        if (trace.entity() != null) return trace.position().add(0, trace.entity().getEyeHeight(), 0);
        var block = trace.block();
        if (block == null) return trace.position();
        Level level = player.level();
        double x = block.getLocation().x, y = block.getLocation().y, z = block.getLocation().z;
        int blockY = block.getBlockPos().getY();
        switch (block.getDirection()) {
            case DOWN -> y -= 1;
            case UP -> y += 1.8;
            case NORTH -> { z -= .6; y = blockY + 1.7; }
            case SOUTH -> { z += .6; y = blockY + 1.7; }
            case WEST -> { x -= .6; y = blockY + 1.7; }
            case EAST -> { x += .6; y = blockY + 1.7; }
        }
        // 原作は側面の横で頭上の空きを確かめ、原作と同じく(int)キャストで切り捨てる。
        if (block.getDirection().get3DDataValue() > 1
                && !level.isEmptyBlock(PenetrateTeleport.legacyBlock(x, y + 1, z))) y -= 1.25;
        return new Vec3(x, y, z);
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10) { stop(player); return; }
        session.ticks++;
    }
    /** テスト用の入口: サーバーのtickループを待たずに押し続けのセッションを進める。 */
    public static void tickForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.heartbeat = player.level().getGameTime();
        tick(player);
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
