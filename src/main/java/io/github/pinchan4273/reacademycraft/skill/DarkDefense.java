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
 * v1.5.0 未元物質の防御: 白翼自動防御（Lv2、軽減40→70→100%、トグル。1.5.2 から熟練度100%でパッシブにもできる）。
 * 1.5.2で未元装甲（Lv3、20→35→50%、パッシブ）を廃止し、防御を翼に集約した。装甲の軽減・費用・熟練度は
 * もう無い（旧IDは{@link DarkMatter#ARMOR}、記録の保持だけ）。以下の段落の装甲の記述は、廃止までの経緯として残す。
 * 旧: 未元物質の両防御は、白翼自動防御と未元装甲（Lv3、20→35→50%）。起動・待機は無料で、CPの回復も止めない。
 * 未元装甲はパッシブ: キーに割り当てず、能力使用モードの間だけ働く（{@link #armorOn}）。
 * 通常の防御の後の残損傷へ、装甲→翼の順に掛ける。1回の大きさに上限は無い（翼は熟練度100%で効く範囲の攻撃を無効にする）。
 * 1.5.2から、防御が働いた攻撃1回ごとに{@link DefenseCost}の値段（防いだ1HPにつき0.25CPと、過負荷）を、
 * 回復待ちを作らずに払う（値は下の段落）。
 * 技能の倍率を掛け、払った分だけ最大CPが伸びる。払えない・セーフティで払わない防御は成立しない。
 * 近接・飛び道具・爆発・落下・高速衝突だけを防ぎ、状態異常・奈落・防御を無視する攻撃は対象外。プリセットの変更では続き、能力モードのOFFで止まる
 * （装甲も能力モードのOFFの間は働かない）。
 * 翼の防御が成立すると光線を止め、刺突と空気砲を中断する（既に与えた損傷と払った費用はそのまま）。翼には打ち返しも向け直しも無い（純粋な防御版の反射）。
 * 装甲は攻撃を中断しない。
 * <p>1.5.2: 翼が動いている間（手動・パッシブとも）、基本の維持を払う（{@link DefenseCost#startUpkeep}。
 * CP は0）。維持が外れたら翼は止まる。白翼刺突も、ほかの攻撃と同じ熟練度の割合で防ぐ。
 * 防いだら自分の攻撃を止めるのも、例外なく同じ。
 * <p>1.5.2: CP は 1.5.1 と同じ（基本の維持は0、被弾1回は防いだ1HPにつき0.25CP）。制限は過負荷
 * （開始の確保 lerp(40, 20)、1回 lerp(4, 1.5)）で効かせる。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class DarkDefense {
    /**
     * 熟練度の得方（翼・装甲とも）: 軽減が成立した
     * 攻撃1回ごとに0.1%と、軽減した1HPごとに0.02%。合わせて毎秒0.5%まで。
     */
    public static final float GAIN_PER_HIT = .001f, GAIN_PER_HP = .0002f, GAIN_PER_SECOND = .005f;
    private static final Map<ResourceLocation, Map<UUID, Holder>> ACTIVE = new HashMap<>();
    static { ACTIVE.put(DarkMatter.GUARD, new HashMap<>()); }

    private static final class Holder {
        int age; long second = -1; float earned;
        /** 1.5.2: パッシブ（能力使用モードで自動で起動）で入れた翼の防御か。 */
        boolean passive;
        /** 1.5.2: 基本の維持。外れたら翼を止める。 */
        PlayerAbilityData.Upkeep upkeep;
    }
    /**
     * 翼が軽減した矢の、軽減した時点の体に刺さった矢の数（1.5.2 で装甲を廃止するまでは、装甲で受けた矢も数えた）。バニラは命中の直後に刺さった矢を1本足すので、次のtickでこの数へ戻す
     * （装甲で受けた矢も刺さらないほうが、そのまま受けたのとの違いが分かる）。
     * 軽減の割合にかかわらず、どちらかの防御が成立した矢は刺さらない。
     */
    private static final Map<UUID, Integer> GUARDED_ARROWS = new HashMap<>();
    private DarkDefense() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static float guardRate(float p) { return p <= .5f ? .4f + .6f * p : .7f + .6f * (p - .5f); }
    /** 働いているか（翼の防御のトグルの状態）。翼の防御でない技能（廃止した装甲の旧IDも）はいつも false。 */
    public static boolean active(ServerPlayer player, ResourceLocation skill) {
        var holders = ACTIVE.get(skill);
        return holders != null && holders.containsKey(player.getUUID());
    }
    public static int ageOn(ServerPlayer player, ResourceLocation skill) { var h = ACTIVE.get(skill).get(player.getUUID()); return h == null ? -1 : h.age; }
    /** 1.5.2: 翼の防御がパッシブ（自動で起動）で働いているか。 */
    public static boolean passive(ServerPlayer player) { var h = ACTIVE.get(DarkMatter.GUARD).get(player.getUUID()); return h != null && h.passive; }
    /**
     * 1.5.2: パッシブに切り替えた翼の防御を、能力使用モードの間に自動で入れる（{@link PassiveSkills}が呼ぶ）。条件はキーで入れるときと同じで、
     * オーバーロードのロック中は入れない。働いていれば何もしない。断る理由はテストのために返す。
     */
    public static String startPassive(ServerPlayer player, PlayerAbilityData data) {
        if (ACTIVE.get(DarkMatter.GUARD).containsKey(player.getUUID())) return "";
        if (!player.isAlive() || data.isReadOnly() || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(DarkMatter.GUARD)
                || !data.isPassiveSkill(DarkMatter.GUARD)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(DarkMatter.GUARD)) return "academy.cast.overload";
        var holder = new Holder();
        holder.passive = true;
        holder.upkeep = DefenseCost.startUpkeep(data, DarkMatter.GUARD, () -> player.getAbilities().instabuild);
        if (holder.upkeep == null) return "academy.cast.unlearned";
        ACTIVE.get(DarkMatter.GUARD).put(player.getUUID(), holder);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, DarkMatter.GUARD, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }

    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot, ResourceLocation skill) {
        // 翼の防御だけを扱う（廃止した装甲の旧IDなどは断る）。
        if (!DarkMatter.GUARD.equals(skill)) return "academy.cast.unlearned";
        if (ACTIVE.get(skill).containsKey(player.getUUID())) { stop(player, skill); return ""; }
        if (slot < 0 || slot >= 4 || !skill.equals(data.getSlot(data.getCurrentPreset(), slot)) || !player.isAlive() || data.isReadOnly()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(skill)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        var holder = new Holder();
        holder.upkeep = DefenseCost.startUpkeep(data, skill, () -> player.getAbilities().instabuild);
        if (holder.upkeep == null) return "academy.cast.unlearned";
        ACTIVE.get(skill).put(player.getUUID(), holder);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, skill, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player, ResourceLocation skill) {
        var holders = ACTIVE.get(skill);
        var holder = holders == null ? null : holders.remove(player.getUUID());
        if (holder == null) return;
        var data = data(player);
        if (data != null) data.endUpkeep(holder.upkeep);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, skill, false);
    }
    /** 翼の防御を止める。 */
    private static void stopGuard(ServerPlayer player) { stop(player, DarkMatter.GUARD); }

    /** 翼の防御が扱う損傷か。 */
    public static boolean covers(DamageSource source) {
        // 1.5.2: 白翼刺突も翼は防ぐ（盾・Jet の前面などほかの Re の防御は、今までどおり刺突を通す）。
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) return false;
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.getEntity() != null || source.getDirectEntity() != null;
    }
    /** 翼の防御を掛ける。戻り値は残りの損傷。 */
    private static float cut(ServerPlayer player, PlayerAbilityData data, ResourceLocation skill, float rate, float amount) {
        float cut = Math.min(amount, amount * rate);
        if (!(cut > 0)) return amount;
        boolean creative = player.getAbilities().instabuild;
        // 1.5.2: 回数の分 ＋ 防いだダメージ × 係数と過負荷を、回復待ちを作らずに払う（技能の倍率を掛け、最大CPが伸びる）。
        // 払えない・セーフティで払わないときは、この1回は防がない。
        if (!DefenseCost.pay(data, skill, DefenseCost.Kind.GUARD, data.getProficiency(skill), DefenseCost.priced(player, cut), creative)) return amount;
        var holder = ACTIVE.get(skill).computeIfAbsent(player.getUUID(), id -> new Holder());
        long second = player.level().getGameTime() / 20;
        if (holder.second != second) { holder.second = second; holder.earned = 0; }
        float gain = Math.min(GAIN_PER_HIT + cut * GAIN_PER_HP, GAIN_PER_SECOND - holder.earned);
        if (gain > 0) { holder.earned += gain; data.addProficiency(skill, gain); }
        return amount - cut;
    }
    /** 残損傷へ翼の防御を掛ける（テストからも呼ぶ）。戻り値は残りの損傷。 */
    public static float absorb(ServerPlayer player, DamageSource source, float amount) {
        var data = data(player);
        if (data == null || data.isReadOnly() || !covers(source) || amount <= 0 || data.isInterfering() || !DarkMatter.CATEGORY.equals(data.getAbility())) return amount;
        float incoming = amount;
        var guard = ACTIVE.get(DarkMatter.GUARD).get(player.getUUID());
        // 維持が外れた翼（次のtickで止まる前の被弾）は防がない。
        if (guard != null && data.upkeepHeld(guard.upkeep) && data.hasLearned(DarkMatter.GUARD)) {
            float before = amount;
            amount = cut(player, data, DarkMatter.GUARD, guardRate(data.getProficiency(DarkMatter.GUARD)), amount);
            if (amount < before) {
                // 翼の防御が成立した: 攻撃を中断する（飛行は続く）。
                DarkMatterRay.stop(player);
                WhiteWingStab.stop(player);
                DarkMatterAirCannon.stop(player);
                // 1.5.2: 高速飛行は通常の移動へ戻し、羽の防御の動きが終わるまで高速へ戻せない。
                WhiteWingFlight.guarded(player);
                DarkWingAction.send(player, DarkWingAction.GUARD, direction(player, source), 0);
            }
        }
        // 翼が軽減した矢は刺さらない。貫通の矢は刺さった矢を足さない（バニラ）。
        if (amount < incoming && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow && arrow.getPierceLevel() <= 0)
            GUARDED_ARROWS.putIfAbsent(player.getUUID(), player.getArrowCount());
        return amount;
    }
    /**
     * 翼の向き: 0は六翼で包む（方向が分からない、正面・背面・上下）、1は右、2は左。落下も六翼の球で包む（
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
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true) public static void damage(AcademyLivingDamageEvent event) {
        PierceHits.damageStage(event, ev -> { if (ev.getEntity() instanceof ServerPlayer player) ev.setAmount(absorb(player, ev.getSource(), ev.getAmount())); });
    }
    private static void tick(ServerPlayer player) {
        Integer arrows = GUARDED_ARROWS.remove(player.getUUID());
        if (arrows != null && player.getArrowCount() > arrows) player.setArrowCount(arrows);
        var data = data(player);
        boolean on = data != null && player.isAlive() && data.isActive() && DarkMatter.CATEGORY.equals(data.getAbility());
        for (var entry : ACTIVE.entrySet()) {
            var holder = entry.getValue().get(player.getUUID());
            if (holder == null) continue;
            // 1.5.2: パッシブで入れた翼の防御は、アクティブに戻したら止める。維持が外れたら止める。
            if (!on || !data.hasLearned(entry.getKey()) || (holder.passive && !data.isPassiveSkill(entry.getKey())) || !data.upkeepHeld(holder.upkeep)) {
                entry.getValue().remove(player.getUUID());
                if (data != null) data.endUpkeep(holder.upkeep);
                io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, entry.getKey(), false);
            }
            else holder.age++;
        }
    }
    /** テスト用の入口（FakePlayerはtickの出来事を出さない）。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }
    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) { stopGuard(p); GUARDED_ARROWS.remove(p.getUUID()); } }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer p) stopGuard(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.values().forEach(Map::clear); GUARDED_ARROWS.clear(); }
}
