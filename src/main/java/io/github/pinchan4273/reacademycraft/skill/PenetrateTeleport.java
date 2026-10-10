package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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
 * 原作PenetrateTeleport（WeAthFolD）: テレポーターのレベル2技能。
 *
 * 押し続けて狙い、離すと間にあるものを貫いてまっすぐ前へテレポートする。原作は術者の足元から視線方向へ0.8ブロックずつ進む:
 * 空いた所を通り、障害物へ入り、反対側へ出て、さらに最大4歩進み、その歩みが止まった所へテレポートする。歩みは距離
 * （最大lerp(10, 35, exp)）と、その距離に要するCPで制限される。
 *
 * 原作の3つの挙動を書かれたとおりに移植している: ブロックの判定は座標を(int)キャストで切り捨てるので、floorになるのは正の値だけ。
 * その4歩の間に出会った2つ目の障害物では、その中で歩みが止まる。障害物の中で歩みが尽きると使えないと印が付くが、原作の
 * terminate()は呼び出し元のメソッドから戻らないので、それでもテレポートする。
 *
 * 原作は照準中にマウスホイールで0.5から最大までの距離を設定でき、それはプレイヤーのuseMouseWheelの設定（既定でオフ）に従う。
 * 原作のクライアントは離すときに距離を送る。ここではクライアントは変わるたびに送り（PenetrateDistance）、サーバーはその範囲に収める。
 * 設定がオフなら最大のまま。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class PenetrateTeleport {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "penetrate_teleport");
    /** 原作STEPと、障害物を出た後に進む4歩。 */
    public static final double STEP = .8;
    private static final int STEPS_AFTER = 4;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; long heartbeat; double distance;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); heartbeat = level.getGameTime(); distance = maxDistance(exp);
        }
    }

    /** 原作Dest: 歩みが止まった所と、障害物の外で止まったか。 */
    public record Dest(Vec3 position, boolean available) { }

    private PenetrateTeleport() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static double maxDistance(float exp) { return ArcGen.lerp(10, 35, exp); }
    /** 原作PTContext.minDist。 */
    public static final double MIN_DISTANCE = .5;
    /** テスト用の入口: 押し続けのセッションの距離。セッションが無ければNaN。 */
    public static double distance(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? Double.NaN : session.distance;
    }
    /** 術者のホイールが設定した距離を原作の範囲に収めたもの。セッションが無ければ何もしない。 */
    public static void setDistance(ServerPlayer player, double distance) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || !Double.isFinite(distance)) return;
        session.distance = Math.max(MIN_DISTANCE, Math.min(maxDistance(session.exp), distance));
    }
    /** 原作getConsumption: 進んだ1ブロックあたりのCP。 */
    public static float consumption(float exp) { return ArcGen.lerp(14, 9, exp); }

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
        // 原作l_onKeyUp: サーバーが動かす前に、キーが上がると術者自身のクライアントがtp.tpを鳴らすので、術者だけが、立っていた場所で聞く。
        player.playNotifySound(io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_TP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        execute(player, data, session);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    private static void execute(ServerPlayer player, PlayerAbilityData data, Session session) {
        float exp = session.exp;
        var dest = destination(player.level(), player.position(), player.getLookAngle(), session.distance, data.getCp(), exp);
        // 原作は使えない行き先でterminate()を呼ぶが、そのまま続けてテレポートする。
        var target = TPSkillHelper.permit(player, dest.position());
        if (target == null) return;
        double distance = player.position().distanceTo(target);
        data.consumeWithForce(ID, (float) (distance * consumption(exp)), ArcGen.lerp(80, 50, exp), player.getAbilities().instabuild);
        data.addProficiency(ID, (float) (.00014 * distance));
        data.setCooldown(ID, (int) ArcGen.lerp(50, 30, exp));
        TPSkillHelper.incrTPCount(player);
        TPSkillHelper.teleport(player, target);
        AbilitySyncEvents.sync(player, true);
    }

    /** 原作getDest: 上で述べた歩み。最大値だけでなくCPも距離を制限する。 */
    public static Dest destination(Level level, Vec3 feet, Vec3 look, double distance, float cp, float exp) {
        double dist = Math.min(distance, cp / consumption(exp));
        var dir = look.normalize();
        double x = feet.x, y = feet.y, z = feet.z, travelled = 0;
        int stage = 0, after = 0;
        while (travelled <= dist) {
            boolean open = hasPlace(level, x, y, z);
            if (stage == 0) { if (!open) stage = 1; }
            else if (stage == 1) { if (open) stage = 2; }
            else if (!open || ++after > STEPS_AFTER) break;
            travelled += STEP;
            x += STEP * dir.x; y += STEP * dir.y; z += STEP * dir.z;
        }
        return new Dest(new Vec3(x, y, z), stage != 1);
    }

    /** 原作の(int)x、(int)y、(int)z: 切り捨てで、下ではなく0へ向かって丸める。 */
    // xとzの(int)キャストは原作のもの（1.12でも既に負になりえた）。yは切り捨てで、原作が見たすべてのy（0未満は無い）で同じ結果になり、
    // 1.20のy < 0にある足は立っているブロックで判定する。
    public static BlockPos legacyBlock(double x, double y, double z) { return new BlockPos((int) x, net.minecraft.util.Mth.floor(y), (int) z); }
    /**
     * 原作hasPlace: そのブロックと上のブロックがどちらも光線が通り抜けるもの。
     * 原作はcanCollideCheck(state, false)を問う。外形の形を持たないブロックがそれに相当する。
     */
    public static boolean hasPlace(Level level, double x, double y, double z) {
        var feet = legacyBlock(x, y, z);
        if (!level.hasChunkAt(feet)) return false;
        return level.getBlockState(feet).getShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getShape(level, feet.above()).isEmpty();
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10) stop(player);
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
