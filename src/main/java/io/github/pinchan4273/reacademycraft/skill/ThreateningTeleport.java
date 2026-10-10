package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
 * 原作ThreateningTeleport（WeAthFolD）: テレポーターのレベル1技能。
 *
 * 押し続けて狙い（無料）、離すとメインハンドにあるものを1つ、術者の見ている所（lerp(8, 15, exp)ブロック先）へテレポートさせる。
 * 狙いはまず地形を完全に無視して生き物を探すので、壁の向こうの対象も見つかる。居なければ地形に対して追跡し、アイテムの着く所を探す。
 * 対象はlerp(3, 6, exp)のダメージを防具を通して受け、70%はアイテムを保持し、残りの30%はアイテムが対象の頭に落ちる。対象が無ければ
 * アイテムは常に狙いの終わった所に落ちる。中断するか、狙っている間に手が空になると、何も送らずに終わる。
 *
 * 原作は手のスタックを減らした後で落とすスタックを写す。1.12のItemStackは個数0でもアイテムを保つので、手の最後の1個も失われずに
 * テレポートする。1.20.1のcopy()はそこでEMPTYを返すので、ここでは先に写す。原作の針は1.5倍のダメージを与え、針も移植したので
 * ここでも同じ。一撃はTPSkillHelperでテレポーターのクリティカルを判定する。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ThreateningTeleport {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "threatening_teleport");
    /** 対象に当たったときの原作dropProb。 */
    public static final double DROP_ON_HIT = .3;
    /** 原作getDamage: lerp(3, 6, exp)。投げたものが針なら1.5倍。 */
    public static float damage(float exp, net.minecraft.world.item.ItemStack stack) {
        float damage = ArcGen.lerp(3, 6, exp);
        return stack.is(io.github.pinchan4273.reacademycraft.world.MaterialContent.item("needle")) ? damage * 1.5f : damage;
    }
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; long heartbeat;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); heartbeat = level.getGameTime();
        }
    }

    /** アイテムの着く所と、途中で当たる生き物（あれば）。 */
    public record Aim(Vec3 position, Entity target) { }

    private ThreateningTeleport() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static double range(float exp) { return ArcGen.lerp(8, 15, exp); }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        // 原作は手が空なら黙って終える。ここでは理由を伝える。
        if (player.getMainHandItem().isEmpty()) return "academy.cast.no_item";
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

    /** 原作のキーアップ: ここでテレポートする。 */
    public static void release(ServerPlayer player, int slot) { release(player, slot, player.getRandom().nextDouble()); }
    /** テスト用の入口: 原作が自身の乱数から引くドロップの判定を、固定値にして離す。 */
    public static void release(ServerPlayer player, int slot, double dropRoll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()) return;
        execute(player, data, session.exp, dropRoll);
    }

    /** 原作のキーの中断: 何も送らない。 */
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    private static void execute(ServerPlayer player, PlayerAbilityData data, float exp, double dropRoll) {
        var stack = player.getMainHandItem();
        // 原作はここで自身の宣言順でconsume(overload, cp)を課す: 18〜10と35〜100。
        if (stack.isEmpty() || !data.consume(ID, ArcGen.lerp(35, 100, exp), ArcGen.lerp(18, 10, exp), player.getAbilities().instabuild))
            return;
        var drop = stack.copyWithCount(1);
        var aim = aim(player, exp);
        double dropChance = 1;
        boolean hit = false;
        if (aim.target() != null) {
            hit = true;
            TPSkillHelper.attackIgnoreArmor(player, data, aim.target(), ID, damage(exp, stack));
            dropChance = DROP_ON_HIT;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (dropRoll < dropChance) {
            var item = new ItemEntity(player.level(), aim.position().x, aim.position().y, aim.position().z, drop);
            player.level().addFreshEntity(item);
        }
        data.addProficiency(ID, (hit ? 1 : .2f) * .003f);
        data.setCooldown(ID, (int) ArcGen.lerp(30, 15, exp));
        // 原作c_end: アイテムが行った後、術者に付いて行くtp.tpを半分の音量で。
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_TP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        // そして術者の半ブロック下からアイテムが行った所までのtp粒子の線。
        // これを守る原作の"attacked"フラグは技能が通った時点で立つ。対象に当たったことを示すものではなく、それは経験値にだけ使う。
        io.github.pinchan4273.reacademycraft.network.SkillTrail.show(player, player.position().add(0, -.5, 0),
                aim.position(), io.github.pinchan4273.reacademycraft.network.SkillTrail.THREATENING);
        player.inventoryMenu.broadcastChanges();
        AbilitySyncEvents.sync(player, true);
    }

    /** 原作calcDropPos: まず地形を無視して生き物、次に地形だけ。当たった対象には頭の上にアイテムを置き、そうでなければ追跡が止まった所に着く。 */
    public static Aim aim(net.minecraft.world.entity.player.Player player, float exp) {
        double range = range(exp);
        var start = player.getEyePosition(); var end = start.add(player.getLookAngle().scale(range));
        Entity target = null; double nearest = Double.MAX_VALUE;
        for (var entity : player.level().getEntities(player, new net.minecraft.world.phys.AABB(start, end).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e))) {
            var box = entity.getBoundingBox().inflate(.3);
            var intercept = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                nearest = start.distanceToSqr(intercept.get()); target = entity;
            }
        }
        if (target != null) return new Aim(new Vec3(target.getX(), target.getY() + target.getBbHeight(), target.getZ()), target);
        if (!player.level().hasChunkAt(BlockPos.containing(end))) return new Aim(end, null);
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return new Aim(block.getType() == HitResult.Type.MISS ? end : block.getLocation(), null);
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10
                // 原作s_tick: 手が空になると狙いを終える。
                || player.getMainHandItem().isEmpty()) stop(player);
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
