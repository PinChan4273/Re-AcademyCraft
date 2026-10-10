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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作VecReflection: ベクトル操作のレベル4の防御。
 *
 * キーでモードをオン・オフする。オンにするとlerp(350, 250, exp)のオーバーロードが加わり、モードの間は下がらない。原作の毎tickの
 * lerp(15, 11, exp)のCPは、今は切り替え型技能の維持コスト（止めている回復に、toggle_upkeep.vec_reflection（熟練度0%で15）×
 * (1 - 熟練度)を加えた量）で、払えなくなるとモードを終える。毎tick、4ブロック以内でまだ見ておらず、除外されておらず
 * （EntityAffectionを参照）、まだ向きを変えていないエンティティを、術者の見ている20ブロック先へ向けてそれ自身の速さで向け直す。
 * それぞれ難しさ * lerp(300, 160, exp)のCPと0.0008の経験値。火の玉は新しいものに置き換える: 大きな火の玉は威力と撃ち手を保ち、
 * それ以外の同種のものは小さな火の玉として戻る。
 *
 * lerp(0.6, 1.2, exp)の割合が1以上になる術者への攻撃は、そのまま取り消す。それより弱い攻撃（9999まで）はその割合だけ削り、
 * 何も残らなければ取り消す。
 *
 * 原作のhandleAttackは再入の防止を設定してから逆向きに判定するので、ダメージのCPを払い、経験値を与え、攻撃者へ打ち返す部分は
 * 決して実行されない。移植版は、近接攻撃への打ち返しを原作とは異なる仕様として採用している: このモードが吸収または削った直接の
 * 近接攻撃（プレイヤーかmob自身の攻撃で、攻撃者自身が与えたもの）は、その同じ割合を技能自身のダメージで攻撃者へ打ち返す。
 * ダメージ * lerp(20, 15, exp)のCPとダメージ * 0.0004の経験値で、攻撃者の位置で反射の音と波を出す。打撃に作用した2つの処理の
 * どちらか一方で1回だけ起きる。CPが無ければ打撃は吸収または削られるが打ち返さず、モードは終わる。発射物・棘の鎧・爆発・環境・
 * 技能は打ち返さない。原作は新しい火の玉に撃ち手の位置を加速度として渡し、火の玉はそれを保つ。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class VecReflection {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "vec_reflection");
    public static final double RANGE = 4;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; final Set<UUID> visited = new HashSet<>(); float overloadKeep;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; exp = data.getProficiency(ID);
        }
    }

    private VecReflection() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    public static float startOverload(float exp) { return ArcGen.lerp(350, 250, exp); }
    public static float entityConsumption(float exp) { return ArcGen.lerp(300, 160, exp); }
    /** 攻撃に対する原作reflectDamageの割合。 */
    public static float share(float exp) { return ArcGen.lerp(.6f, 1.2f, exp); }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

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

    /** 原作Raytrace.getLookingPos(player, 20): 視線が何かに当たる所。エンティティなら目の高さの60%だけ上げた所、または届く範囲の終点。 */
    public static Vec3 lookingAt(ServerPlayer player) {
        var trace = TPSkillHelper.traceLiving(player, 20, e -> true);
        return trace.entity() == null ? trace.position() : trace.position().add(0, trace.entity().getEyeHeight() * .6, 0);
    }
    /** 原作reflect: 同じ速さで、エンティティの頭から術者の見ている所へ向け直す。 */
    public static Vec3 turned(ServerPlayer player, Entity entity) {
        return lookingAt(player).subtract(entity.getEyePosition()).normalize().scale(entity.getDeltaMovement().length());
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))) { stop(player); return; }
        // 維持コストはこのtickの始めに精算済み。払えなかった要求は外れている。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        boolean creative = player.getAbilities().instabuild;
        if (data.getOverload() < session.overloadKeep) data.setOverload(session.overloadKeep);
        var origin = player.position();
        var level = (ServerLevel) player.level();
        for (var entity : level.getEntities((Entity) null, new AABB(origin, origin).inflate(RANGE),
                e -> e.position().distanceToSqr(origin) <= RANGE * RANGE)) {
            if (!session.visited.add(entity.getUUID()) || EntityAffection.marked(entity)) continue;
            float difficulty = EntityAffection.difficulty(entity);
            if (difficulty < 0) continue;
            if (!data.consumeInMode(ID, difficulty * entityConsumption(session.exp), 0, creative)) continue;
            if (entity instanceof AbstractHurtingProjectile fireball) replace(player, level, fireball);
            else {
                entity.setDeltaMovement(turned(player, entity));
                entity.hurtMarked = true;
                EntityAffection.mark(entity);
            }
            // 原作reflectEffect: 向きを変えたエンティティの頭で。
            var head = entity.getEyePosition();
            level.playSound(null, head.x, head.y, head.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_REFLECTION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
            io.github.pinchan4273.reacademycraft.network.VmWave.send(player, new io.github.pinchan4273.reacademycraft.network.VmWave(level.dimension().location(),
                    head, player.getYHeadRot(), player.getXRot(), 0, 0, 2, 1.1f));
            data.addProficiency(ID, difficulty * .0008f);
        }
        if (ACTIVE.get(player.getUUID()) != session) return;
        AbilitySyncEvents.sync(player, false);
    }
    /** テスト用の入口: モードのサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /** 原作createNewFireball。 */
    private static void replace(ServerPlayer player, ServerLevel level, AbstractHurtingProjectile source) {
        var velocity = turned(player, source);
        source.discard();
        var shooter = source.getOwner() instanceof LivingEntity owner ? owner : null;
        AbstractHurtingProjectile fireball;
        if (source instanceof LargeFireball large && shooter != null) {
            int power = large.saveWithoutId(new CompoundTag()).getByte("ExplosionPower");
            fireball = new LargeFireball(level, shooter, shooter.getX(), shooter.getY(), shooter.getZ(), power);
        } else if (shooter != null) {
            fireball = new SmallFireball(level, shooter, shooter.getX(), shooter.getY(), shooter.getZ());
        } else {
            fireball = new SmallFireball(level, source.getX(), source.getY(), source.getZ(), source.getX(), source.getY(), source.getZ());
        }
        fireball.setPos(source.getX(), source.getY(), source.getZ());
        fireball.setDeltaMovement(velocity);
        EntityAffection.mark(fireball);
        level.addFreshEntity(fireball);
    }

    /** 原作onLivingAttack: 割合が1以上の攻撃はまったく当たらない。 */
    @SubscribeEvent public static void attacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID())) return;
        var data = data(player);
        if (data != null && share(data.getProficiency(ID)) * event.getAmount() >= 1) {
            event.setCanceled(true);
            strikeBack(player, data, event.getSource(), event.getAmount());
        }
    }
    /** 原作onLivingHurt: 通ったものを割合だけ削る。 */
    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID()) || event.getAmount() > 9999) return;
        var data = data(player);
        if (data == null) return;
        float amount = event.getAmount();
        float left = amount - share(data.getProficiency(ID)) * amount;
        event.setAmount(left);
        if (left <= 0) event.setCanceled(true);
        strikeBack(player, data, event.getSource(), amount);
    }

    private static boolean strikingBack;
    /**
     * プレイヤーかmob自身の、攻撃者自身が与えた打撃: 矢・棘の鎧・爆発・環境・技能ではない（打ち返しは技能のダメージなので、
     * 2人の反射者が永遠に打ち合うことはない）。
     */
    public static boolean closeAttack(ServerPlayer player, DamageSource source) {
        return (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO))
                && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && attacker != player && attacker.isAlive();
    }
    /** モードが吸収または削ったばかりの近接攻撃への打ち返し（クラスの説明を参照）。 */
    private static void strikeBack(ServerPlayer player, PlayerAbilityData data, DamageSource source, float damage) {
        if (strikingBack || !closeAttack(player, source) || !(damage > 0) || !Float.isFinite(damage)) return;
        var attacker = (LivingEntity) source.getEntity();
        float exp = data.getProficiency(ID);
        if (!data.consumeInMode(ID, damage * ArcGen.lerp(20, 15, exp), 0, player.getAbilities().instabuild)) { stop(player); return; }
        strikingBack = true;
        try { SkillCombat.attack(player, attacker, ID, damage * share(exp)); }
        finally { strikingBack = false; }
        data.addProficiency(ID, damage * .0004f);
        var head = attacker.getEyePosition(); var level = player.serverLevel();
        level.playSound(null, head.x, head.y, head.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_REFLECTION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        io.github.pinchan4273.reacademycraft.network.VmWave.send(player, new io.github.pinchan4273.reacademycraft.network.VmWave(level.dimension().location(),
                head, player.getYHeadRot(), player.getXRot(), 0, 0, 2, 1.1f));
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
