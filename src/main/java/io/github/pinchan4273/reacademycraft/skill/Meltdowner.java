package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.MdRayEffect;
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
 * 原作Meltdowner（WeAthFolD）: カテゴリの名前の由来であるレベル3技能。
 *
 * 押し続けて溜める。Scatter BombやLight Shieldと同じく、オーバーロードは開始時に1回払ってそこを下限にし、CPは毎tick払う。
 * 20tick以上溜めてから離すと、広く貫く光線を1本撃つ。それより早く離すか中断すると何も撃たず、溜めは失われる。100tickを超えて
 * 押し続けても撃たずに溜めが終わる。光線の強さは溜めた長さ（40tickが上限）に従い、原作のtimeRateは20tickで0.8、40tickで1.2まで上がる。
 *
 * 光線は原作RangedRayDamage.Reflectibleで、Railgunが作るのと同じクラスなので、写さずにRangedRayを通してRailgunの移植
 * （RailgunAttack、RailgunTerrain、RailgunGeometry）を共有する: 半径lerp(2, 3, exp)の円柱に沿ったエンティティへのダメージ
 * （横の距離で減衰）、次に同じ断面でのエネルギーに限りのある地形の光線。光線を反射する対象（リスナーが取り消したSkillReflectEvent）は
 * そこで光線を止め、反射した者からlerp(20, 50, exp)の半分で10ブロックの光線を1本撃つ。原作の唯一のリスナーはVector Manipulationの
 * Vector Reflectionなので、移植版ではまだこのイベントを取り消すものは無い。
 *
 * 原作はここでMDDamageHelperではなくctx.attackでダメージを与えるので、カテゴリの名前の由来である光線はRadiation Intensifyの印を
 * 残さない。書かれたとおりに移植している。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class Meltdowner {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "meltdowner");
    /** 原作TICKS_MIN、TICKS_MAX、TICKS_TOLE。 */
    public static final int TICKS_MIN = 20, TICKS_MAX = 40, TICKS_TOLE = 100;
    /** 原作s_reflected: Raytrace.traceLiving(reflector, 10)。 */
    private static final double REFLECT_RANGE = 10;
    /** 主光線と反射光線の、原作EntityMDRayの長さ。 */
    public static final double BEAM_LENGTH = 30, REFLECTED_BEAM_LENGTH = 10;
    /** 原作Context.getRange(): コンテキストのクライアントへのメッセージは50ブロック以内の全員へ届く。 */
    private static final double PRESENTATION_RANGE = 50;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp, overloadFloor;
        int ticks; long heartbeat;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); overloadFloor = data.getOverload(); heartbeat = level.getGameTime();
        }
        float tickConsumption() { return ArcGen.lerp(10, 15, exp); }
    }

    private Meltdowner() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }

    /** 原作timeRate(ct): 最小の溜めで0.8、最大で1.2。 */
    public static float timeRate(int chargeTicks) { return ArcGen.lerp(.8f, 1.2f, (chargeTicks - 20f) / 20f); }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        // 原作はオーバーロード全体を最初に払い、それより下へ減らさない。
        if (!data.consume(ID, 0, ArcGen.lerp(200, 170, data.getProficiency(ID)), player.getAbilities().instabuild))
            return "academy.cast.cp";
        ACTIVE.put(player.getUUID(), new Session(player, data, slot));
        // 原作c_start: md.md_chargeはコンテキストが終わるまで（離した後も含む）術者に付いて行く。
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MD_CHARGE.get(),
                net.minecraft.sounds.SoundSource.AMBIENT, 1f, false, () -> holding(player));
        return "";
    }

    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }

    /** 原作のキーアップ: TICKS_MIN溜めたときだけ撃ち、そうでなければ撃たずに終える。 */
    public static void release(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || session.ticks < TICKS_MIN) return;
        perform(player, data, session);
    }

    /** 原作のキーの中断: 溜めは失われ、何も撃たない。 */
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }

    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int chargeTicks(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.ticks;
    }

    private static void perform(ServerPlayer player, PlayerAbilityData data, Session session) {
        int chargeTicks = Math.min(session.ticks, TICKS_MAX);
        float rate = timeRate(chargeTicks), exp = session.exp;
        var ray = new RangedRay(ID, ArcGen.lerp(2, 3, exp), REFLECT_RANGE, .5f * ArcGen.lerp(20, 50, exp));
        Vec3 start = player.position(), look = player.getLookAngle();
        // 原作c_perform: PLAYERSカテゴリのmd.meltdowner。術者に付いて行く。地形より先に送り、撃った音自体が破壊音より先にクライアントへ届くようにする。
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MELTDOWNER.get(), net.minecraft.sounds.SoundSource.PLAYERS, .5f, 1f);
        // 原作と同じく、まずエンティティ、次に反射した者が光線を止めた所までの地形。
        var attack = RailgunAttack.perform(player, ray, start, look, rate * ArcGen.lerp(18, 50, exp));
        RailgunTerrain.perform(player, ray, start, look, rate * ArcGen.lerp(300, 700, exp),
                attack.terrainLimitSquared(), player.getRandom());
        data.addProficiency(ID, rate * .002f);
        data.setCooldown(ID, (int) (rate * 20 * ArcGen.lerp(15, 7, exp)));
        AbilitySyncEvents.sync(player, true);
        present(player, look, attack);
    }

    /**
     * 原作EntityMDRay(player, length): 目から視線に沿って1ブロック先で始まり、足元 + look * lengthを狙い、その長さで描かれるので、
     * 目の高さから少し下り坂になる。{origin, target}を返し、targetはちょうどlengthの距離。
     */
    public static Vec3[] beamEnds(Vec3 eyes, Vec3 feet, Vec3 look, double length) {
        var origin = eyes.add(look);
        var direction = feet.add(look.scale(length)).subtract(origin);
        if (direction.lengthSqr() < 1e-9) direction = look;
        return new Vec3[] { origin, origin.add(direction.normalize().scale(length)) };
    }

    /**
     * 原作c_perform/c_reflected。主光線は反射時にmin(30, reflector.getDistanceSq(player))へ短くなる。距離の2乗を長さと比べているが、
     * 描画にしか影響しないので再現している。反射光線は術者自身の視線に沿って、反射した者の目が術者から離れているのと同じだけ先から始まり、
     * 反射した者ではなく術者の方向を保つ: 原作は新しいEntityMDRay(player, 10)を回さずにそこへ動かし、ダメージ自体は反射した者の
     * 見る方向へ行く。
     */
    private static void present(ServerPlayer player, Vec3 look, RailgunAttack.Result attack) {
        var eyes = player.getEyePosition(); var feet = player.position();
        double length = Math.min(BEAM_LENGTH, attack.terrainLimitSquared());
        var dimension = player.level().dimension().location();
        if (length > 0) {
            var ends = beamEnds(eyes, feet, look, length);
            broadcast(player, new MdRayEffect(dimension, player.getUUID(), player.getId(), nextSession(), ends[0], ends[1], MdRayEffect.MELTDOWNER));
        }
        var reflector = attack.reflector();
        if (reflector != null) {
            var ends = beamEnds(eyes, feet, look, REFLECTED_BEAM_LENGTH);
            var start = eyes.add(look.scale(eyes.distanceTo(reflector.getEyePosition())));
            var shift = start.subtract(ends[0]);
            broadcast(player, new MdRayEffect(dimension, player.getUUID(), player.getId(), nextSession(),
                    start, ends[1].add(shift), MdRayEffect.MELTDOWNER));
        }
    }
    private static long nextSession() { return io.github.pinchan4273.reacademycraft.network.MdSessions.next(); }
    private static void broadcast(ServerPlayer player, MdRayEffect beam) {
        for (var observer : player.serverLevel().players())
            if (observer == player || observer.distanceToSqr(player) <= PRESENTATION_RANGE * PRESENTATION_RANGE)
                AcademyNetwork.send(observer, beam);
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
        // 原作はまずtickを数え、次にオーバーロードの下限を設け、CPを払い、許容時間を適用する。
        session.ticks++;
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
        if (!data.consume(ID, session.tickConsumption(), 0, player.getAbilities().instabuild) || session.ticks > TICKS_TOLE)
            stop(player);
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
