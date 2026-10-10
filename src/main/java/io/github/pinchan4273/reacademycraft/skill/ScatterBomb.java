package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.MdBallEffect;
import io.github.pinchan4273.reacademycraft.network.MdRayEffect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * 原作ScatterBomb（WeAthFolD、KSkun、Paindar）: meltdownerのレベル2技能。
 *
 * 1回押すのではなく押し続ける: キーが押されている間に術者の横にオーブが集まり、離すとすべてが発射される。原作はオーバーロードを
 * 開始時に1回払い、押している間ずっとそこを下限にするので、離しても押し続けより安くはならない。CPは代わりに毎tick払う。
 *
 * 長く押しすぎると痛い。200tickで原作は術者にダメージを与え、技能自体を終える。
 *
 * 熟練度が半分を超えると一部の光線が追尾する: 原作は5ブロック以内の生きたエンティティを選び、その数のオーブをそれらへ向け、
 * 残りはランダムに散らす。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ScatterBomb {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "scatter_bomb");
    /** 原作MAX_TICKS、生成の間隔と最初の生成、自傷の期限、RAY_RANGE。 */
    private static final int MAX_TICKS = 80, SPAWN_INTERVAL = 10, FIRST_SPAWN = 20, SELF_HARM_TICKS = 200;
    private static final double RAY_RANGE = 15, HOMING_RANGE = 5;
    private static final float SELF_HARM_DAMAGE = 6;
    private static final double PRESENTATION_RANGE = 25;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private record Orb(long session, double subX, double subY, double subZ) { }

    private static final class Session {
        final Level level; final int preset, slot; final float exp;
        final List<Orb> orbs = new ArrayList<>();
        final float overloadFloor; int ticks; long heartbeat;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); overloadFloor = data.getOverload(); heartbeat = level.getGameTime();
        }
    }

    private ScatterBomb() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        // 原作はオーバーロード全体を最初に払い、それより下へ減らさない。
        if (!data.consume(ID, 0, ArcGen.lerp(80, 60, data.getProficiency(ID)), player.getAbilities().instabuild))
            return "academy.cast.cp";
        ACTIVE.put(player.getUUID(), new Session(player, data, slot));
        return "";
    }

    /** クライアントはキーが押されている間これを繰り返す。更新の無いセッションは離したものとみなす。 */
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }

    public static void stop(ServerPlayer player) { release(player, ACTIVE.remove(player.getUUID())); }
    /**
     * キーアップまたは中断: 原作はそれを自身のキーが始めたコンテキストにだけ渡すので、別のスロットのキーではこれは止まらない。
     * すべてのセッションを終える処理は、引き続きstop(player)を呼ぶ。
     */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int orbCount(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.orbs.size();
    }

    private static void spawn(ServerPlayer player, Session session) {
        float theta = (float) (-player.getYRot() / 180 * Math.PI)
                + Mth.randomBetween(player.getRandom(), -(float) Math.PI * .45f, (float) Math.PI * .45f);
        float radius = Mth.randomBetween(player.getRandom(), .8f, 1.3f);
        var orb = new Orb(io.github.pinchan4273.reacademycraft.network.MdSessions.next(),
                Mth.sin(theta) * radius, Mth.randomBetween(player.getRandom(), -1.2f, .2f), Mth.cos(theta) * radius);
        session.orbs.add(orb);
        // オーブは押している時間より長く生きるので、自傷の期限を寿命として送る。通常の離しでは、光線が先にそれを退かせる。
        broadcast(player, new MdBallEffect(session.level.dimension().location(), player.getUUID(), player.getId(),
                orb.session(), orb.subX(), orb.subY(), orb.subZ(), SELF_HARM_TICKS));
    }

    /** 原作は見ている位置の先を、各軸最大12.5度散らして狙う。 */
    private static Vec3 spread(ServerPlayer player) {
        var begin = player.getEyePosition().add(player.getLookAngle().scale(RAY_RANGE));
        float pitch = (player.getRandom().nextFloat() - .5f) * 25, yaw = (player.getRandom().nextFloat() - .5f) * 25;
        var look = player.getLookAngle().xRot((float) Math.toRadians(pitch)).yRot((float) Math.toRadians(yaw));
        return begin.add(look.scale(RAY_RANGE));
    }

    /**
     * 原作の選別はt.isInstanceOf[EntityLiving]: 意思を持つ生き物で、1.12がEntityLivingの外に置くプレイヤーや防具立ては含まない。
     * 1.20.1のMobがそのクラスに当たる。
     */
    public static boolean homesOn(Entity entity) { return entity instanceof net.minecraft.world.entity.Mob && entity.isAlive(); }

    private static void release(ServerPlayer player, Session session) {
        if (session == null) return;
        var data = data(player);
        if (session.level != player.level() || data == null || data.isReadOnly()) return;
        var level = player.serverLevel();
        // 熟練度が半分を超えると、原作はこの数の光線を近くの生き物へ追尾させる。
        int homing = session.exp > .5f ? (int) (session.orbs.size() * session.exp) : 0;
        List<Entity> targets = homing <= 0 ? List.of()
                : new ArrayList<>(level.getEntities(player, player.getBoundingBox().inflate(HOMING_RANGE), ScatterBomb::homesOn));
        for (var orb : session.orbs) {
            Vec3 start = player.position().add(orb.subX(), orb.subY() + player.getEyeHeight(), orb.subZ());
            Vec3 end = spread(player);
            if (homing > 0 && !targets.isEmpty()) {
                var target = targets.get(player.getRandom().nextInt(targets.size()));
                end = new Vec3(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
                homing--;
            }
            if (level.hasChunkAt(BlockPos.containing(start)) && level.hasChunkAt(BlockPos.containing(end)))
                fire(player, level, start, end, session.exp);
            broadcast(player, new MdRayEffect(session.level.dimension().location(), player.getUUID(), player.getId(),
                    orb.session(), start, end));
            player.level().playSound(null, start.x, start.y, start.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_RAY_SMALL.get(), net.minecraft.sounds.SoundSource.AMBIENT, .8f, 1f);
        }
        if (!session.orbs.isEmpty()) data.addProficiency(ID, .001f * session.orbs.size());
    }

    private static void fire(ServerPlayer player, net.minecraft.server.level.ServerLevel level,
                             Vec3 start, Vec3 end, float exp) {
// LambdaLib2 Raytraceの既定のfilNormalは当たり判定の箱でだけ止まるので、液体では止まらない。
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        Entity target = null;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(RAY_RANGE * 2 + 1),
                e -> e.isAlive() && ProjectileHitSeam.hittable(e) && !e.isSpectator())) {
            var intercept = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                target = entity; nearest = start.distanceToSqr(intercept.get());
            }
        }
        if (target == null) return;
        // 原作は当たりのクールダウンを消すので、一斉射は最初の1本だけでなくすべての光線を当てる。
        target.invulnerableTime = -1; // 原作hurtResistantTime = -1（ElectronMissileやPlasmaCannonと同じ）
        MDDamageHelper.attack(player, data(player), target, ID, ArcGen.lerp(5, 9, exp));
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
        // 原作はオーバーロードを開始時のコストが残した値を下限にするので、押し続けても回復しない。
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
        session.ticks++;
        if (session.ticks <= MAX_TICKS) {
            if (session.ticks >= FIRST_SPAWN && session.ticks % SPAWN_INTERVAL == 0) spawn(player, session);
            if (!data.consume(ID, ArcGen.lerp(3, 6, session.exp), 0, player.getAbilities().instabuild)) { stop(player); return; }
        }
        if (session.ticks >= SELF_HARM_TICKS) {
            // 原作attackEntityFrom(DamageSource.causePlayerDamage(player), 6): 術者自身の攻撃なので、PvPやチームの規則が適用され、
            // 死亡メッセージは術者の名前を出す。
            player.hurt(player.damageSources().playerAttack(player), SELF_HARM_DAMAGE);
            stop(player);
        }
    }

    private static void broadcast(ServerPlayer player, MdBallEffect orb) {
        for (var listener : listeners(player)) AcademyNetwork.send(listener, orb);
    }
    private static void broadcast(ServerPlayer player, MdRayEffect beam) {
        for (var listener : listeners(player)) AcademyNetwork.send(listener, beam);
    }
    /** 原作はこの一斉射を、単発の爆弾より遠い25ブロック以内の全員へ送る。 */
    private static List<ServerPlayer> listeners(ServerPlayer player) {
        var out = new java.util.LinkedHashSet<ServerPlayer>(); out.add(player);
        for (var observer : player.serverLevel().players())
            if (observer.distanceToSqr(player) <= PRESENTATION_RANGE * PRESENTATION_RANGE) out.add(observer);
        out.removeIf(listener -> listener.level() != player.level());
        return List.copyOf(out);
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
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getEntity() instanceof ServerPlayer p) ACTIVE.remove(p.getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
