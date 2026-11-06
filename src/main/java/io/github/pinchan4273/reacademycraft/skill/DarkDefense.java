package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質の両防御（§8）: 白翼自動防御（Lv2、軽減40→70→100%、トグル）と未元装甲（Lv3、20→35→50%）。起動・待機は無料。
 * 未元装甲は2026-11-03のユーザーの決定で受動になった: キーに割り当てず、能力使用モードの間だけ働く（{@link #armorOn}）。
 * 通常の防御の後の残損傷へ、装甲→翼の順に掛け、カットした1HPにつき0.25CPを払う（回復待ちを作らない）。払えない・セーフティで払わない防御は成立しない。
 * 近接・飛び道具・爆発・落下・高速衝突だけを防ぎ、状態異常・奈落・防御を無視する攻撃は対象外。プリセットの変更では続き、能力モードのOFFで止まる
 * （装甲も能力モードのOFFの間は働かない）。
 * 翼の防御が成立すると光線を止め、刺突と空気砲を中断する（既に与えた損傷と払った費用はそのまま）。装甲は攻撃を中断しない。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class DarkDefense {
    /**
     * 熟練度の得方（翼・装甲とも。2026-11-03のユーザーの決定、以前は1HPにつき0.01%・毎秒0.1%までで育ちにくかった）: 軽減が成立した
     * 攻撃1回ごとに0.1%と、軽減した1HPごとに0.02%。合わせて毎秒0.5%まで。
     */
    public static final float CP_PER_HP = .25f, GAIN_PER_HIT = .001f, GAIN_PER_HP = .0002f, GAIN_PER_SECOND = .005f;
    private static final Map<ResourceLocation, Map<UUID, Holder>> ACTIVE = new HashMap<>();
    static { ACTIVE.put(DarkMatter.GUARD, new HashMap<>()); ACTIVE.put(DarkMatter.ARMOR, new HashMap<>()); }

    private static final class Holder { int age; long second = -1; float earned; }
    /**
     * 翼か装甲が軽減した矢の、軽減した時点の体に刺さった矢の数。バニラは命中の直後に刺さった矢を1本足すので、次のtickでこの数へ戻す
     * （2026-11-03のユーザーの指摘と決定: 防いだのに矢が刺さる。装甲で受けた矢も刺さらないほうが、そのまま受けたのとの違いが分かる）。
     * 軽減の割合にかかわらず、どちらかの防御が成立した矢は刺さらない。
     */
    private static final Map<UUID, Integer> GUARDED_ARROWS = new HashMap<>();
    private DarkDefense() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static float guardRate(float p) { return p <= .5f ? .4f + .6f * p : .7f + .6f * (p - .5f); }
    public static float armorRate(float p) { return ArcGen.lerp(.2f, .5f, p); }
    /** 働いているか。翼の防御はトグルの状態、装甲は{@link #armorOn}。 */
    public static boolean active(ServerPlayer player, ResourceLocation skill) {
        return DarkMatter.ARMOR.equals(skill) ? armorOn(data(player)) : ACTIVE.get(skill).containsKey(player.getUUID());
    }
    /** 受動の未元装甲が働くか: 未元物質の系統で習得済みで、能力使用モードの間。干渉中は{@link #absorb}がどちらの防御も掛けない。 */
    public static boolean armorOn(PlayerAbilityData data) {
        return data != null && !data.isReadOnly() && data.isActive() && DarkMatter.CATEGORY.equals(data.getAbility()) && data.hasLearned(DarkMatter.ARMOR);
    }
    public static int ageOn(ServerPlayer player, ResourceLocation skill) { var h = ACTIVE.get(skill).get(player.getUUID()); return h == null ? -1 : h.age; }

    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot, ResourceLocation skill) {
        // 装甲は受動なので、キーからは入れない。
        if (DarkMatter.ARMOR.equals(skill)) return "academy.cast.unlearned";
        if (ACTIVE.get(skill).containsKey(player.getUUID())) { stop(player, skill); return ""; }
        if (slot < 0 || slot >= 4 || !skill.equals(data.getSlot(data.getCurrentPreset(), slot)) || !player.isAlive() || data.isReadOnly()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(skill)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        ACTIVE.get(skill).put(player.getUUID(), new Holder());
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, skill, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player, ResourceLocation skill) {
        if (ACTIVE.get(skill).remove(player.getUUID()) != null) io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, skill, false);
    }
    /** 翼の防御を止め、装甲の熟練度の数えを捨てる（装甲は受動なので画面の点灯は送らない）。 */
    private static void stopBoth(ServerPlayer player) { stop(player, DarkMatter.GUARD); ACTIVE.get(DarkMatter.ARMOR).remove(player.getUUID()); }

    /** 両防御が扱う損傷か。 */
    public static boolean covers(DamageSource source) {
        if (SkillDamageSource.ignoresDefenses(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) return false;
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.getEntity() != null || source.getDirectEntity() != null;
    }
    /** 1種類の防御を掛ける。戻り値は残りの損傷。 */
    private static float cut(ServerPlayer player, PlayerAbilityData data, ResourceLocation skill, float rate, float amount) {
        float cut = amount * rate;
        if (cut <= 0) return amount;
        boolean creative = player.getAbilities().instabuild;
        float cost = cut * CP_PER_HP;
        if (!creative && (data.getCp() < cost || !data.safetyPermits(skill, cost, false))) return amount;
        data.payWithoutWait(creative ? 0 : cost);
        // 装甲の持ち物は、熟練度の毎秒の上限の数えだけに使う（受動なので、ここで作る）。
        var holder = ACTIVE.get(skill).computeIfAbsent(player.getUUID(), id -> new Holder());
        long second = player.level().getGameTime() / 20;
        if (holder.second != second) { holder.second = second; holder.earned = 0; }
        float gain = Math.min(GAIN_PER_HIT + cut * GAIN_PER_HP, GAIN_PER_SECOND - holder.earned);
        if (gain > 0) { holder.earned += gain; data.addProficiency(skill, gain); }
        return amount - cut;
    }
    /** 残損傷へ装甲→翼の順に掛ける（試験からも呼ぶ）。戻り値は残りの損傷。 */
    public static float absorb(ServerPlayer player, DamageSource source, float amount) {
        var data = data(player);
        if (data == null || data.isReadOnly() || !covers(source) || amount <= 0 || data.isInterfering() || !DarkMatter.CATEGORY.equals(data.getAbility())) return amount;
        float incoming = amount;
        if (armorOn(data))
            amount = cut(player, data, DarkMatter.ARMOR, armorRate(data.getProficiency(DarkMatter.ARMOR)), amount);
        if (active(player, DarkMatter.GUARD) && data.hasLearned(DarkMatter.GUARD)) {
            float before = amount;
            amount = cut(player, data, DarkMatter.GUARD, guardRate(data.getProficiency(DarkMatter.GUARD)), amount);
            if (amount < before) {
                // 翼の防御が成立した: 攻撃を中断する（飛行は続く）。
                DarkMatterRay.stop(player);
                WhiteWingStab.stop(player);
                DarkMatterAirCannon.stop(player);
                DarkWingAction.send(player, DarkWingAction.GUARD, direction(player, source), 0);
            }
        }
        // 翼か装甲が軽減した矢は刺さらない。貫通の矢は刺さった矢を足さない（バニラ）。
        if (amount < incoming && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow && arrow.getPierceLevel() <= 0)
            GUARDED_ARROWS.putIfAbsent(player.getUUID(), player.getArrowCount());
        return amount;
    }
    /**
     * 翼の向き: 0は六翼で包む（方向が分からない、正面・背面・上下）、1は右、2は左。落下も六翼の球で包む（2026-11-04のユーザーの決定。
     * 以前は翼を下へ広げるだけの3を送っていた。クライアントは3の描き方を残している）。
     */
    static int direction(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL)) return DarkWingAction.GUARD_ALL;
        Vec3 from = source.getSourcePosition();
        if (from == null) return DarkWingAction.GUARD_ALL;
        var to = from.subtract(player.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) return DarkWingAction.GUARD_ALL;
        double yaw = Math.toRadians(player.yBodyRot);
        // 体の右向きの成分（南を向くと西が右）。
        double right = to.normalize().dot(new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)));
        return right > .5 ? DarkWingAction.GUARD_RIGHT : right < -.5 ? DarkWingAction.GUARD_LEFT : DarkWingAction.GUARD_ALL;
    }
    @SubscribeEvent(priority = EventPriority.LOW) public static void damage(AcademyLivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) event.setAmount(absorb(player, event.getSource(), event.getAmount()));
    }
    private static void tick(ServerPlayer player) {
        Integer arrows = GUARDED_ARROWS.remove(player.getUUID());
        if (arrows != null && player.getArrowCount() > arrows) player.setArrowCount(arrows);
        var data = data(player);
        boolean on = data != null && player.isAlive() && data.isActive() && DarkMatter.CATEGORY.equals(data.getAbility());
        for (var entry : ACTIVE.entrySet()) {
            var holder = entry.getValue().get(player.getUUID());
            if (holder == null) continue;
            boolean armor = DarkMatter.ARMOR.equals(entry.getKey());
            if (!on || !data.hasLearned(entry.getKey())) {
                entry.getValue().remove(player.getUUID());
                if (!armor) io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, entry.getKey(), false);
            }
            else holder.age++;
        }
    }
    /** テスト用の入口（FakePlayerはtickの出来事を出さない）。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }
    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) { stopBoth(p); GUARDED_ARROWS.remove(p.getUUID()); } }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer p) stopBoth(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.values().forEach(Map::clear); GUARDED_ARROWS.clear(); }
}
