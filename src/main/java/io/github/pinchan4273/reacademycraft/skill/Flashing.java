package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.FlashingState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作Flashing（WeAthFolD）: テレポーターのレベル5技能。
 *
 * キーで瞬間移動モードをオン・オフする。オンにするとlerp(80, 60, exp)のCPとlerp(250, 180, exp)のオーバーロードがかかり、
 * 続く間オーバーロードはその値を下回らない。原作はlerp(60, 150, exp)tick後にモードを終える。移植版は原作とは異なる仕様として
 * 時間制限を設けず、代わりに切り替え型技能の維持コストを払い、払えなくなると終える（止めている回復に、toggle_upkeep.flashing
 * （熟練度0%で4）× (1 - 熟練度)を加えた量）。モード中は移動キーで狙う: どれかを押すと左・右・前・後のいずれかを選び、
 * 同じキーを離すと、術者の視線（上下を含む）を基準にその方向へlerp(12, 18, exp)ブロック瞬間移動する。1回ごとにlerp(13, 6, exp)のCP。
 * 各瞬間移動の後、術者自身のクライアントが2秒間重力のほとんどを打ち消す。モードの終了は、理由を問わずクールダウン
 * lerp(900, 400, exp)を始める。原作のterminate()は常にそれを設定するので、CP不足でオンにできなかったときも同じ。
 *
 * クライアントは4方向のどれかだけを送り、行き先はサーバーが自身の持つ術者の位置と視線から求める（原作のserverPerformと同じ）。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class Flashing {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "flashing");
    /** 原作のキーid: 1は左（A）、2は右（D）、3は前（W）、4は後（S）。 */
    public static final int LEFT = 1, RIGHT = 2, FORWARD = 3, BACK = 4;
    private static final Vec3[] DIRECTIONS = { null, new Vec3(0, 0, -1), new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(-1, 0, 0) };
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp, overloadKeep; int ticks;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot, float exp) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; this.exp = exp;
            overloadKeep = data.getOverload();
        }
    }

    private Flashing() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    public static float consumption(float exp) { return ArcGen.lerp(13, 6, exp); }
    public static float startConsumption(float exp) { return ArcGen.lerp(80, 60, exp); }
    public static float startOverload(float exp) { return ArcGen.lerp(250, 180, exp); }
    /** このスロットからオンにしたモードが動いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        return session != null && session.slot == slot ? session.ticks : -1;
    }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(900, 400, exp); }
    public static double distance(float exp) { return ArcGen.lerp(12, 18, exp); }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /** キー: モードがオンならオフにし、そうでなければオンを試みる。 */
    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        float exp = data.getProficiency(ID);
        // 原作serverMadeAlive: CPが無ければモードはすぐ終わり、その終了がクールダウンを設定する。
        if (!data.consume(ID, startConsumption(exp), startOverload(exp), player.getAbilities().instabuild)) {
            data.setCooldown(ID, cooldown(exp));
            AbilitySyncEvents.sync(player, true);
            return "academy.cast.cp";
        }
        // 支払いで術者がオーバーロードすることがあり、そのイベントは、このモードがまだ存在しないうちにすべてのモードを終える:
        // その場合は何も始めない。オーバーロードさせた分は、他のオーバーロードを起こすコストと同じく払ったまま。
        // 原作のオーバーロードは、生まれたばかりのモードを中止し、その終了が上と同じくクールダウンを設定する。
        if (!allowed(player, data) || data.isOverloadLocked() || data.isInterfering() || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))) {
            if (!data.isReadOnly() && data.hasLearned(ID)) data.setCooldown(ID, cooldown(exp));
            AbilitySyncEvents.sync(player, true);
            return "academy.cast.overload";
        }
        var session = new Session(player, data, slot, exp);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        send(player, true, false);
        AbilitySyncEvents.sync(player, true);
        return "";
    }

    /** 原作serverPerform: 4方向のいずれかへの1回の瞬間移動。 */
    public static void perform(ServerPlayer player, int direction) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        if (session == null || direction < LEFT || direction > BACK || !allowed(player, data)) return;
        // 原作の方向キーも技能キーなので、妨害されている間はどれも効かない。ただしモード自体は続く。
        if (data.isInterfering()) return;
        var target = TPSkillHelper.permit(player, destination(player, direction, session.exp));
        if (target == null) return;
        if (!data.consume(ID, consumption(session.exp), 0, player.getAbilities().instabuild)) return;
        TPSkillHelper.teleport(player, target);
        data.addProficiency(ID, .002f);
        TPSkillHelper.incrTPCount(player);
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_FLASHING.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, 1f);
        send(player, true, true);
        AbilitySyncEvents.sync(player, true);
    }

    /**
     * 原作getDest。方向をpitchでZ軸周りに、次にyawで回し、目から瞬間移動の届く距離まで伸ばす。ただし追跡は足元から始める。
     * 生き物（その位置を目の高さだけ上げた所）とブロックに当たる。Mark Teleportの面のずれを使うが、面の横では高さはブロックの
     * ものではなく、当たった点に1.7を足したもの。
     */
    public static Vec3 destination(net.minecraft.world.entity.player.Player player, int direction, float exp) {
        var end = player.getEyePosition().add(direction(direction, player.getYRot(), player.getXRot()).scale(distance(exp)));
        var trace = TPSkillHelper.trace(player, player.position(), end, TPSkillHelper::living);
        if (trace.entity() != null) return trace.position().add(0, trace.entity().getEyeHeight(), 0);
        var block = trace.block();
        if (block == null) return end;
        var hit = block.getLocation();
        double x = hit.x, y = hit.y, z = hit.z;
        switch (block.getDirection()) {
            case DOWN -> y -= 1;
            case UP -> y += 1.8;
            case NORTH -> { z -= .6; y = hit.y + 1.7; }
            case SOUTH -> { z += .6; y = hit.y + 1.7; }
            case WEST -> { x -= .6; y = hit.y + 1.7; }
            case EAST -> { x += .6; y = hit.y + 1.7; }
        }
        if (block.getDirection().getAxis() != Direction.Axis.Y
                && !player.level().isEmptyBlock(new BlockPos((int) x, (int) (y + 1), (int) z))) y -= 1.25;
        return new Vec3(x, y, z);
    }

    /**
     * 原作のキーの方向の回し方: LambdaLib2のrotateAroundZでpitchだけ回し、次に1.12のVec3d.rotateYawで-90 - yawだけ回す。
     * 原作と同じくラジアンとfloatの三角関数を使う。
     */
    public static Vec3 direction(int direction, float yawDegrees, float pitchDegrees) {
        var dir = DIRECTIONS[direction];
        float pitch = pitchDegrees * Mth.PI / 180, yaw = (-90 - yawDegrees) * Mth.PI / 180;
        float pc = Mth.cos(pitch), ps = Mth.sin(pitch);
        dir = new Vec3(dir.x * pc + dir.y * ps, dir.y * pc - dir.x * ps, dir.z);
        float yc = Mth.cos(yaw), ys = Mth.sin(yaw);
        return new Vec3(dir.x * yc + dir.z * ys, dir.y, dir.z * yc - dir.x * ys);
    }

    /** 理由を問わずモードを終え、原作のクールダウンを設定する。 */
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        if (data != null && !data.isReadOnly()) data.setCooldown(ID, cooldown(session.exp));
        send(player, false, false);
        if (data != null) AbilitySyncEvents.sync(player, true);
    }

    private static void send(ServerPlayer player, boolean active, boolean flashed) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new FlashingState(active, flashed));
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))) { stop(player); return; }
        // 原作serverTick: モードの間オーバーロードは回復しない。原作はmax_timeまで続いた。ここでは、このtickの始めに精算した
        // 維持コストを払えた間続く。
        if (data.getOverload() < session.overloadKeep) data.setOverload(session.overloadKeep);
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        session.ticks++;
    }
    /** テスト用の入口: モードのサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
