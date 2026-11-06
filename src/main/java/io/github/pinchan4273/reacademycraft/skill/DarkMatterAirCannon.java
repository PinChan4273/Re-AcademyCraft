package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質Lv3 未元空気砲（§7）。単発。発動で費用を払い（空振りでも）、予備動作4tickの終わりに起点と照準軸を固定し、放出から連続4tickで判定する。
 * 照準軸との角度が0〜15度で4段/tick、30度まで3段、45度まで2段（1対象あたり最大16段）。1段は全段命中の合計の1/16。距離減衰なし。壁は越えない。
 * ベクトルの防御幕は実際に受け止めた段ごとに、防御側の熟練度で16→12CPを払わせ、払えない段から残りの段は幕を通る（§7）。
 * 損傷は段の端数を保持して最後に1回だけ反映し、吹き飛ばしもその時に1回だけ。熟練度は有効に命中した対象ごとに＋0.2%、1発で最大＋0.6%。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class DarkMatterAirCannon {
    public static final Identifier ID = DarkMatter.AIR_CANNON;
    public static final int WINDUP = 4, RELEASE = 4;
    public static final float GAIN_PER_TARGET = .002f, GAIN_MAX = .006f;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Tally { float damage; boolean membraneBroken; int hits; }
    private static final class Session {
        final Level level; final float p; int age; Vec3 origin, axis; final Map<LivingEntity, Tally> tallies = new LinkedHashMap<>();
        /** §13: 放出の完了の後に移る攻撃と、そのキーのスロット。無ければnull。 */
        Identifier next; int nextSlot;
        Session(ServerPlayer player, float p) { level = player.level(); this.p = p; }
    }
    private DarkMatterAirCannon() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static double range(float p) { return ArcGen.lerp(8, 16, p); }
    public static float totalDamage(float p) { return ArcGen.lerp(10, 20, p); }
    public static float cp(float p) { return ArcGen.lerp(240, 120, p); }
    public static float overload(float p) { return ArcGen.lerp(24, 12, p); }
    public static int cooldown(float p) { return Math.round(ArcGen.lerp(80, 40, p)); }
    public static double knockback(float p) { return ArcGen.lerp(12, 24, p); }
    /** 防御幕が1段を受け止める費用（防御側の熟練度）。 */
    public static float membraneCost(float defenderProficiency) { return ArcGen.lerp(16, 12, defenderProficiency); }
    /** 照準軸からの角度（度）での、1tickの段数。 */
    public static int hitsPerTick(double degrees) { return degrees <= 15 ? 4 : degrees <= 30 ? 3 : degrees <= 45 ? 2 : 0; }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot)) || !player.isAlive() || data.isReadOnly()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        if (data.getCooldown(ID) > 0 || ACTIVE.containsKey(player.getUUID())) return "academy.cast.cooldown";
        float p = data.getProficiency(ID);
        if (!data.consume(ID, cp(p), overload(p), player.getAbilities().instabuild)) return "academy.cast.cp";
        // §13: 光線は止め、刺突は新しい打撃を止める。
        DarkMatterRay.stop(player);
        WhiteWingStab.stop(player);
        data.setCooldown(ID, cooldown(p));
        ACTIVE.put(player.getUUID(), new Session(player, p));
        DarkWingAction.send(player, DarkWingAction.CANNON, 0, (float) range(p));
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    /**
     * §13: 放出の途中で押された次の攻撃（光線か刺突）を受け付ける。費用・クールダウン・セーフティを今確かめ、使えなければ理由を返して空気砲だけを続ける。
     * 使えるなら完了の後に1回だけ移す（完了の時点でもう一度その技能自身が確かめる）。光線の再入力は待ちを取り消す。中断（翼防御など）では移らない。
     */
    public static String queueNext(ServerPlayer player, PlayerAbilityData data, Identifier skill, int slot, float cost) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return "academy.cast.cooldown";
        if (DarkMatter.RAY.equals(skill) && skill.equals(session.next)) { session.next = null; return ""; }
        if (slot < 0 || slot >= 4 || !skill.equals(data.getSlot(data.getCurrentPreset(), slot)) || data.isReadOnly() || !data.hasLearned(skill)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        if (data.getCooldown(skill) > 0) return "academy.cast.cooldown";
        boolean creative = player.getAbilities().instabuild;
        if ((!creative && data.getCp() < cost) || !data.safetyPermits(skill, cost, creative)) return "academy.cast.cp";
        session.next = skill; session.nextSlot = slot;
        return "";
    }
    /** 待っている次の攻撃（試験用）。無ければnull。 */
    public static Identifier queued(ServerPlayer player) { var session = ACTIVE.get(player.getUUID()); return session == null ? null : session.next; }
    /** そのスロットのキーが離されたら、待ちを取り消す（押し続けの刺突）。 */
    public static void dropQueued(ServerPlayer player, Identifier skill, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && skill.equals(session.next) && session.nextSlot == slot) session.next = null;
    }
    private static void startNext(ServerPlayer player, PlayerAbilityData data, Session session) {
        if (session.next == null || !player.isAlive()) return;
        if (DarkMatter.RAY.equals(session.next)) { if (!DarkMatterRay.active(player)) DarkMatterRay.toggle(player, data, session.nextSlot); }
        else if (DarkMatter.STAB.equals(session.next)) WhiteWingStab.start(player, data, session.nextSlot);
    }
    /** 終える。これまでの段の損傷は反映する（巻き戻さない）。白翼自動防御の中断と、死亡・移動から呼ぶ。 */
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session != null && player.isAlive() && session.level == player.level()) settle(player, session);
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!player.isAlive() || data == null || session.level != player.level()) { ACTIVE.remove(player.getUUID()); return; }
        if (session.age == WINDUP) { session.origin = player.getEyePosition(); session.axis = player.getLookAngle(); }
        if (session.age >= WINDUP) wave(player, session);
        if (++session.age >= WINDUP + RELEASE) { ACTIVE.remove(player.getUUID()); settle(player, session); startNext(player, data, session); }
    }
    private static void wave(ServerPlayer player, Session session) {
        double range = range(session.p);
        float each = totalDamage(session.p) / 16;
        for (var entity : player.level().getEntities(player, new AABB(session.origin, session.origin).inflate(range),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && SkillCombat.mayAttack(player, e, ID))) {
            var target = (LivingEntity) entity;
            var center = target.getBoundingBox().getCenter();
            var to = center.subtract(session.origin);
            double distance = to.length();
            if (distance > range + target.getBbWidth() / 2 || distance < 1e-4) continue;
            double degrees = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(session.axis)))));
            int hits = hitsPerTick(degrees);
            if (hits == 0) continue;
            if (player.level().clip(new ClipContext(session.origin, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
            var tally = session.tallies.computeIfAbsent(target, t -> new Tally());
            for (int i = 0; i < hits; i++) {
                tally.hits++;
                if (membraneBlocks(target, tally)) continue;
                tally.damage += each;
            }
        }
    }
    /** ベクトルの防御幕がこの段を受け止めるか。受け止めるなら防御側が払う。実CPで判定し、将来の回復を先取りしない。 */
    private static boolean membraneBlocks(LivingEntity target, Tally tally) {
        if (tally.membraneBroken || !(target instanceof ServerPlayer defender)) return false;
        Identifier skill = VecDeviation.active(defender) ? VecDeviation.ID : VecReflection.active(defender) ? VecReflection.ID : null;
        if (skill == null) return false;
        var data = data(defender);
        if (data == null) return false;
        boolean creative = defender.getAbilities().instabuild;
        float cost = membraneCost(data.getProficiency(skill));
        if (!creative && (data.getCp() < cost || !data.safetyPermits(skill, cost, false))) { tally.membraneBroken = true; return false; }
        data.payWithoutWait(creative ? 0 : cost);
        return true;
    }
    private static void settle(ServerPlayer player, Session session) {
        var data = data(player);
        float gain = 0;
        double push = knockback(session.p) / 20;
        for (var entry : session.tallies.entrySet()) {
            var target = entry.getKey();
            if (!target.isAlive()) continue;
            boolean hurt = entry.getValue().damage > 0 && SkillCombat.attackContact(player, target, ID, entry.getValue().damage);
            if (hurt && gain < GAIN_MAX) gain += GAIN_PER_TARGET;
            var direction = target.getBoundingBox().getCenter().subtract(session.origin == null ? player.getEyePosition() : session.origin);
            if (direction.lengthSqr() > 1e-6) {
                double resist = 1 - Math.max(0, Math.min(1, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
                target.setDeltaMovement(target.getDeltaMovement().add(direction.normalize().scale(push * resist)));
                target.hurtMarked = true;
            }
        }
        if (data != null && gain > 0 && data.hasLearned(ID)) data.addProficiency(ID, gain);
    }
    /** テスト用の入口。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
