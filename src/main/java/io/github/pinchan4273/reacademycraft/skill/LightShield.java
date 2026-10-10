package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作LightShield（WeAthFolD）: meltdownerのもう1つのレベル2技能。
 *
 * 押し続ける。盾が術者の前にあり、前方60度の弧に入ったものを焼き、18tickごとに受ける攻撃1回の大部分を吸収する。
 * Scatter Bombと同じく、オーバーロードは開始時に1回払ってそこを下限にするので、押し続けても回復しない。
 *
 * 離すと5秒の鈍化と、押していた長さに比例するクールダウン（熟練度最大で半分）がかかるので、長く押すのはただではない。
 *
 * 原作は吸収のコストを、盾が敵に触れたときは(overload, cp)、攻撃を吸収したときは(cp, overload)として渡しており、
 * 両方が意図どおりということはありえない。どちらかを選ばず、両方とも書かれたとおりに移植している。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class LightShield {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "light_shield");
    /** 原作ACTION_INTERVAL、届く距離、覆う前方の弧。 */
    private static final int ABSORB_INTERVAL = 18;
    private static final double REACH = 3, ARC_DEGREES = 60;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp, overloadFloor, maxTicks;
        int ticks; int lastAbsorb = -1; long heartbeat;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); overloadFloor = data.getOverload();
            maxTicks = ArcGen.lerp(120, 180, exp); heartbeat = level.getGameTime();
        }
        float absorbDamage() { return ArcGen.lerp(15, 50, exp); }
        float touchDamage() { return ArcGen.lerp(2, 6, exp); }
        float absorbOverload() { return ArcGen.lerp(5, 3, exp); }
        float absorbConsumption() { return ArcGen.lerp(50, 30, exp); }
        int cooldown() { return (int) ArcGen.lerp(2 * ticks, ticks, exp); }
    }

    private LightShield() { }
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
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        if (!data.consume(ID, 0, ArcGen.lerp(110, 60, data.getProficiency(ID)), player.getAbilities().instabuild))
            return "academy.cast.cp";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // コンテキストを持つ各クライアントでの原作c_spawn: 押している間のEntityMdShield。
        io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, ID, 0, () -> ACTIVE.get(player.getUUID()) == session);
        // 原作c_start: 起動音と、盾を押している間のループ音。
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_SHIELD_STARTUP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_SHIELD_LOOP.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, true, () -> holding(player));
        return "";
    }

    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }

    /**
     * キーアップまたは中断: 原作はそれを自身のキーが始めたコンテキストにだけ渡すので、別のスロットのキーではこれは止まらない。
     * すべてのセッションを終える処理は、引き続きstop(player)を呼ぶ。
     */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (data == null || data.isReadOnly() || session.level != player.level()) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        data.setCooldown(ID, session.cooldown());
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.ticks;
    }

    /** 原作は術者の前方60度の弧を、yawだけで測って覆う。 */
    private static boolean reachable(ServerPlayer player, Entity target) {
        double yaw = -Math.toDegrees(Math.atan2(target.getX() - player.getX(), target.getZ() - player.getZ()));
        return Math.abs(yaw - player.getYRot()) % 360 < ARC_DEGREES;
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
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
        session.ticks++;
        if (session.ticks > session.maxTicks) { stop(player); return; }
        if (!data.consume(ID, ArcGen.lerp(9, 4, session.exp), 0, player.getAbilities().instabuild)) { stop(player); return; }
        data.addProficiency(ID, 1e-6f);
        var level = player.serverLevel();
        // 原作basicSelectorはEntitySelectors.everything(): ドロップしたアイテムや経験値オーブも焼く。
        for (var target : level.getEntities(player, player.getBoundingBox().inflate(REACH),
                e -> e.isAlive() && !e.isSpectator())) {
            if (target.invulnerableTime > 0 || !reachable(player, target)) continue;
            // 原作はここで接触を(overload, cp)として課す。
            if (!data.consume(ID, session.absorbConsumption(), session.absorbOverload(), player.getAbilities().instabuild)) continue;
            MDDamageHelper.attack(player, data, target, ID, session.touchDamage());
            data.addProficiency(ID, .001f);
        }
    }

    /** テスト用の入口: サーバーのtickループを待たずに押し続けのセッションを進める。 */
    public static void tickForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.heartbeat = player.level().getGameTime();
        tick(player);
    }

    /** 盾が自身の分を食べた後に残るダメージを返す。 */
    public static float absorb(ServerPlayer player, DamageSource source, float damage) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        if (session == null || data == null || data.isReadOnly() || damage == 0) return damage;
        if (session.lastAbsorb != -1 && session.ticks - session.lastAbsorb <= ABSORB_INTERVAL) return damage;
        var attacker = source.getDirectEntity();
        // 原作handleAttackedは、攻撃者が届く範囲に居たかどうかに関係なく.001を得る。範囲外では吸収を飛ばすだけ（間隔もやり直さない）。
        if (attacker != null && !reachable(player, attacker)) { data.addProficiency(ID, .001f); return damage; }
        session.lastAbsorb = session.ticks;
        float result = damage;
        // 原作は吸収を、上の接触とは逆に(cp, overload)として課す。
        if (data.consume(ID, session.absorbOverload(), session.absorbConsumption(), player.getAbilities().instabuild))
            result -= Math.min(damage, session.absorbDamage());
        data.addProficiency(ID, .001f);
        return Math.max(0, result);
    }

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID())) return;
        float left = absorb(player, event.getSource(), event.getAmount());
        event.setAmount(left);
        if (left == 0) event.setCanceled(true);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
