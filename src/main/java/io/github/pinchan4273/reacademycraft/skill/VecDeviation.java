package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.SkillModeState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作VecDeviation: ベクトル操作のレベル2の防御。
 *
 * キーでモードをオン・オフする。オンにするとlerp(80, 50, exp)のオーバーロードが加わり、モードの間は下がらない。原作は毎tick
 * lerp(13, 5, exp)のCP（払えなければモードを終える）と、さらにlerp(5, 2.5, exp)のCPとlerp(0.5, 0.2, exp)のオーバーロード
 * （g_tick。術者のクライアントだけでなくサーバーでも動いた）を払った。CPは今は切り替え型技能の維持コスト（止めている回復に、
 * toggle_upkeep.vec_deviation（熟練度0%で13 + 5）× (1 - 熟練度)を加えた量）で、払えなくなるとモードを終える。g_tickの
 * オーバーロードは残るので、最終的にはやはりオーバーロードでモードが終わる。毎tick、5ブロック以内でまだ見ておらず、除外されておらず
 * （EntityAffectionを参照）、まだ止めていないエンティティを探す: 大きな火の玉はその場で弾け、小さな火の玉は取り除かれ、それ以外
 * （矢はまず無力化する）はその場で止まって印が付く。それぞれlerp(15, 12, exp)のCPを強制で払い、0.001の経験値を与える。
 * モードがオンの間、術者が受けるダメージ（9999まで）をlerp(40%, 90%, exp)削り、最大lerp(15, 12, exp)のCPを払い、1ポイントごとに
 * 0.0006の経験値を与える。
 *
 * 原作はContextManager.findでモードを引いており、サーバーでは最初に出会ったいずれかのプレイヤーのコンテキストを返す: どのプレイヤーの
 * ダメージも削られ、モードをオンにした者のCPから払われた。ここでは術者自身のダメージだけを削る。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class VecDeviation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "vec_deviation");
    public static final double RANGE = 5;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; final Set<UUID> visited = new HashSet<>(); float overloadKeep;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; exp = data.getProficiency(ID);
        }
    }

    private VecDeviation() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    public static float normalOverload(float exp) { return ArcGen.lerp(.5f, .2f, exp); }
    public static float stopConsumption(float exp) { return ArcGen.lerp(15, 12, exp); }
    public static float startOverload(float exp) { return ArcGen.lerp(80, 50, exp); }
    /** 原作reduceDamageの削り。 */
    public static float reduction(float exp) { return ArcGen.lerp(.4f, .9f, exp); }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /** キー: モードがオンならオフに、そうでなければオンにする。 */
    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.VECMANIP.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        var session = new Session(player, data, slot);
        // 原作s_madeAlive: オーバーロードは収まるかどうかに関係なく加え、その後保つ。
        data.consumeInMode(ID, 0, startOverload(session.exp), player.getAbilities().instabuild);
        session.overloadKeep = data.getOverload();
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        SkillModeState.send(player, ID, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        SkillModeState.send(player, ID, false);
        if (data(player) != null) AbilitySyncEvents.sync(player, true);
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))) { stop(player); return; }
        boolean creative = player.getAbilities().instabuild;
        float exp = session.exp;
        // 原作s_tickのCPとg_tickのCP: このtickの始めに精算した維持コスト。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        if (data.getOverload() < session.overloadKeep) data.setOverload(session.overloadKeep);
        var origin = player.position();
        var level = (ServerLevel) player.level();
        for (var entity : level.getEntities((Entity) null, new AABB(origin, origin).inflate(RANGE),
                e -> e.position().distanceToSqr(origin) <= RANGE * RANGE)) {
            if (!session.visited.add(entity.getUUID()) || EntityAffection.marked(entity)) continue;
            float difficulty = EntityAffection.difficulty(entity);
            if (difficulty < 0) continue;
            data.consumeWithForce(ID, stopConsumption(exp), 0, creative);
            if (entity instanceof LargeFireball fireball) {
                int power = fireball.saveWithoutId(new CompoundTag()).getByte("ExplosionPower");
                fireball.discard();
                level.explode(null, fireball.getX(), fireball.getY(), fireball.getZ(), power, true,
                        level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
            } else if (entity instanceof SmallFireball) {
                entity.discard();
            } else {
                if (entity instanceof AbstractArrow arrow) arrow.setBaseDamage(0);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.hurtMarked = true;
                EntityAffection.mark(entity);
            }
            data.addProficiency(ID, .001f * difficulty);
        }
        // 原作g_tickのオーバーロード。サーバーでも。
        data.chargeUpkeepOverload(ID, normalOverload(data.getProficiency(ID)), creative);
        if (ACTIVE.get(player.getUUID()) != session) return;
        AbilitySyncEvents.sync(player, false);
    }
    /** テスト用の入口: モードのサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /** 原作onLivingHurtとreduceDamage。術者だけに対して。 */
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID()) || event.getAmount() > 9999) return;
        var data = data(player);
        if (data == null || data.isReadOnly()) return;
        float exp = data.getProficiency(ID);
        data.consumeInMode(ID, Math.min(data.getCp(), stopConsumption(exp)), 0, player.getAbilities().instabuild);
        data.addProficiency(ID, event.getAmount() * .0006f);
        event.setAmount(event.getAmount() * (1 - reduction(exp)));
        // 原作MSG_PLAY: 術者の立っている所で。原作の止めたエンティティの音は決して鳴らない（クライアントがサーバーにしか無い印を確かめるため）
        // ので、ここでも止めた発射物は無音。
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_DEVIATION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        AbilitySyncEvents.sync(player, false);
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
