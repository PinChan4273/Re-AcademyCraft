package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作VecAccel: ベクトル操作のレベル2の発射。
 *
 * 押し続けて溜め、離すと術者を視線から10度上げた方向へ、sin(lerp(0.4, 1, min(押したtick数 / 20, 1))) * 2.5ブロック毎tickで発射する
 * （すぐ離すと約0.97、溜めきると2.1）。熟練度50%未満では、足元の2ブロック以内に地面（空気以外なら何でもよく、水も含む）が要る。
 * lerp(120, 80, exp)のCPとlerp(30, 15, exp)のオーバーロード。落下をリセットする。クールダウンlerp(80, 50, exp)、経験値0.002。
 *
 * 原作は術者自身のクライアントで速度を設定し、後でサーバーへ伝えるだけで、サーバーは何も確かめずに課金し経験値を与えた。
 * ここではサーバーが地面とコストを確かめ、速度を求め、バニラのノックバックと同じく術者のクライアントへ送る。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class VecAccel {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "vec_accel");
    /** 原作MAX_VELOCITYとMAX_CHARGE。 */
    public static final double MAX_VELOCITY = 2.5;
    public static final int MAX_CHARGE = 20;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float consumption; long heartbeat; int ticks;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
            consumption = consumption(data.getProficiency(ID));
        }
    }

    private VecAccel() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    /** 原作の消費。コンテキストを作るときに固定する。 */
    public static float consumption(float exp) { return ArcGen.lerp(120, 80, exp); }
    public static float overload(float exp) { return ArcGen.lerp(30, 15, exp); }
    /** 原作の速さ: sin(lerp(0.4, 1, clamp(ticks / 20))) * 2.5。 */
    public static double speed(int ticks) {
        double progress = .4 + (1 - .4) * Mth.clamp(ticks / (double) MAX_CHARGE, 0, 1);
        return Math.sin(progress) * MAX_VELOCITY;
    }
    /** 原作initSpeed: pitchを10度上げた視線に速さを掛ける。 */
    public static Vec3 velocity(float yaw, float pitch, int ticks) {
        return Vec3.directionFromRotation(pitch - 10, yaw).scale(speed(ticks));
    }
    /** 原作checkGround: 足元から2ブロック下への追跡が、空気以外のどのブロック（LambdaLib2のfilNothing）にも当たる。液体を含む。 */
    public static boolean onGround(net.minecraft.world.entity.player.Player player) {
        var from = player.position(); var to = from.add(0, -2, 0);
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player))
                .getType() == HitResult.Type.BLOCK;
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.VECMANIP.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // 術者のクライアントへの原作MSG_MADEALIVE: この溜めが生きている間、ParabolaEffectを表示する。
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
        float exp = data.getProficiency(ID);
        // 原作canPerform: 熟練度50%を超えると地面は関係ない。
        if (!(exp > .5f || onGround(player))) return;
        if (!data.consume(ID, session.consumption, overload(exp), player.getAbilities().instabuild)) return;
        player.setDeltaMovement(velocity(player.getYRot(), player.getXRot(), session.ticks));
        player.hurtMarked = true;
        player.fallDistance = 0;
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_ACCEL.get(), net.minecraft.sounds.SoundSource.AMBIENT, .35f, 1f);
        data.setCooldown(ID, (int) ArcGen.lerp(80, 50, exp));
        data.addProficiency(ID, .002f);
        AbilitySyncEvents.sync(player, true);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

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
