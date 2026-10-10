package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.MdBallEffect;
import io.github.pinchan4273.reacademycraft.network.MdRayEffect;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
 * 原作ElectronBomb（WeAthFolD）: meltdownerのレベル1技能。
 *
 * 原作のEntityMdBallは発射物ではない: 術者の周りのランダムなずれを1回選び、寿命の間ずっと術者に付いて行き、その後で発射する。
 * 読み込み時には保存されず消えるので、移植版は保存を防がなければならないワールドのエンティティの代わりに、待機中の爆弾を
 * PlayerTickEventで動かすサーバー側の状態として持つ（CurrentChargingやBodyIntensifyと同じ形）。
 *
 * ビームは発動時ではなく発射時に狙う。原作getDest(player)は起爆のコールバックの中で評価されるため。待機中に狙い直せるのは意図どおり。
 *
 * 原作はこの技能でCPもオーバーロードも消費しない: すべての電撃使いの技能と違い、コンテキストがconsumeを呼ばず、設定は倍率しか持たない。
 * 推測で調整せず、書かれたとおりに移植している。
 *
 * 原作はビームを20ブロック以内の全員へ送る。オーブはそこでは追跡されるエンティティなので、移植版は持たないエンティティ追跡に
 * 頼らず、同じ方法で送る。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ElectronBomb {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "electron_bomb");
    /** 原作Life/LifeImprovedとDistance。 */
    private static final int LIFE = 20, LIFE_IMPROVED = 5;
    private static final double DISTANCE = 15;
    private static final double PRESENTATION_RANGE = 20;
    private static final Map<UUID, Pending> ACTIVE = new HashMap<>();

    private static final class Pending {
        final Level level; final float exp; final double subX, subY, subZ; final long session; int remaining;
        Pending(ServerPlayer player, float exp) {
            level = player.level(); this.exp = exp;
            session = io.github.pinchan4273.reacademycraft.network.MdSessions.next();
            // 原作のずれ: yaw基準で0.9piの弧の中の角度、半径0.8〜1.3、下向きの偏り。
            float theta = (float) (-player.getYRot() / 180 * Math.PI)
                    + Mth.randomBetween(player.getRandom(), -(float) Math.PI * .45f, (float) Math.PI * .45f);
            float radius = Mth.randomBetween(player.getRandom(), .8f, 1.3f);
            subX = Mth.sin(theta) * radius; subZ = Mth.cos(theta) * radius;
            subY = Mth.randomBetween(player.getRandom(), -1.2f, .2f);
            // 原作はボールが消える2tick前にコールバックを予約する。
            remaining = (exp > .8f ? LIFE_IMPROVED : LIFE) - 2;
        }
        Vec3 position(ServerPlayer player) { return player.position().add(subX, subY, subZ); }
    }

    private ElectronBomb() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }

    /** 翻訳キーを返す。爆弾が待機に入ったら空。 */
    public static String cast(ServerPlayer player, PlayerAbilityData data) {
        if (!player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (ACTIVE.containsKey(player.getUUID())) return "academy.cast.cooldown";
        float exp = data.getProficiency(ID);
        var bomb = new Pending(player, exp);
        ACTIVE.put(player.getUUID(), bomb);
        broadcast(player, new MdBallEffect(player.level().dimension().location(), player.getUUID(), player.getId(),
                bomb.session, bomb.subX, bomb.subY, bomb.subZ, bomb.remaining + 2));
        data.addProficiency(ID, .005f);
        data.setCooldown(ID, (int) ArcGen.lerp(20, 10, exp));
        return "";
    }

    public static boolean pending(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    private static void detonate(ServerPlayer player, Pending bomb) {
        var level = player.serverLevel();
        Vec3 start = bomb.position(player).add(0, player.getEyeHeight(), 0);
        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(DISTANCE));
        for (Vec3 step : new Vec3[]{start, end})
            if (!level.hasChunkAt(BlockPos.containing(step))) return;
// LambdaLib2 Raytraceの既定のfilNormalは当たり判定の箱でだけ止まるので、液体では止まらない。
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        Entity target = null;
        // 原作は術者と他のmdボールを除く。移植版には除くべきボールのエンティティが無い。
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(DISTANCE + 1),
                e -> e.isAlive() && ProjectileHitSeam.hittable(e) && !e.isSpectator())) {
            var intercept = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                target = entity; nearest = start.distanceToSqr(intercept.get());
            }
        }
        if (target != null) MDDamageHelper.attack(player, data(player), target, ID, ArcGen.lerp(6, 12, bomb.exp));
        // 原作はlife - 2でボールのコールバックから発射し、ボールはlifeで自然に消える。そこでビームは独自のセッションを持ち、
        // オーブには消えていく最後の2tickを残す。
        long raySession = io.github.pinchan4273.reacademycraft.network.MdSessions.next();
        broadcast(player, new MdRayEffect(level.dimension().location(), player.getUUID(), player.getId(),
                raySession, start, end));
        // 原作EntityMdRaySmall.onFirstUpdate: 光線の始点で。
        player.level().playSound(null, start.x, start.y, start.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_RAY_SMALL.get(), net.minecraft.sounds.SoundSource.AMBIENT, .8f, 1f);
    }

    /** 原作は表示を術者と近くの全員へ送り、ワールド全体へは送らない。 */
    private static java.util.List<ServerPlayer> listeners(ServerPlayer player) {
        var out = new java.util.LinkedHashSet<ServerPlayer>(); out.add(player);
        for (var observer : player.serverLevel().players())
            if (observer.distanceToSqr(player) <= PRESENTATION_RANGE * PRESENTATION_RANGE) out.add(observer);
        out.removeIf(listener -> listener.level() != player.level());
        return java.util.List.copyOf(out);
    }
    private static void broadcast(ServerPlayer player, MdBallEffect orb) {
        for (var listener : listeners(player)) AcademyNetwork.send(listener, orb);
    }
    private static void broadcast(ServerPlayer player, MdRayEffect beam) {
        for (var listener : listeners(player)) AcademyNetwork.send(listener, beam);
    }

    private static void tick(ServerPlayer player) {
        var bomb = ACTIVE.get(player.getUUID());
        if (bomb == null) return;
        var data = data(player);
        if (bomb.level != player.level() || !player.isAlive()
                || data == null || data.isReadOnly() || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility())) {
            ACTIVE.remove(player.getUUID()); return;
        }
        if (--bomb.remaining > 0) return;
        ACTIVE.remove(player.getUUID());
        detonate(player, bomb);
    }

    /** テスト用の入口: サーバーのtickループを待たずに待機中の爆弾を進める。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
