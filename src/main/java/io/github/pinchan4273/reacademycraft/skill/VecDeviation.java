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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作VecDeviation: ベクトル操作のレベル2の防御。1.5.2から防御の部類の基準と解析度に従う。
 *
 * <p>キーでモードをオン・オフする。起動はロック中・クールダウン中は拒否する。
 * <ul>
 * <li>1.5.2: 動いている間（手動・パッシブとも）、基本の維持を払う（{@link DefenseCost#startUpkeep}。額は0）。
 *     反射とは排他で、後から入れた方が勝つ。1.5.1までの起動のオーバーロードlerp(30, 14)、維持費（toggle_upkeep.vec_deviation）、
 *     毎tickの維持のオーバーロードlerp(0.05, 0.02)と、維持による熟練度は無くした。</li>
 * <li>受けたダメージ（9999まで）を、lerp(40%, 90%) × そのダメージの解析度（{@link VectorAnalysis}）だけ削る。未元物質に直接触れる攻撃
 *     （白翼刺突、甲虫の近接と突進）は、解析度100%でも50%まで。1.5.1までの「50%以上の白翼刺突と未元光線は膜を通る」は無い。</li>
 * <li>削った1回ごとに{@link DefenseCost.Kind#DEVIATION}の値段（20→10 CP ＋ 削ったダメージ × 4→2 CP、過負荷3→0）を、回復待ちを作らずに払う。
 *     払えなければその1回は削らない。熟練度は受けたダメージ1ポイントごとに0.0006（以前どおり）。</li>
 * <li>偏向か反射が働いている間に受けた1回ごとに、そのダメージの解析度が上がる（{@link VectorAnalysis#learn}。削れなかった1回も数える）。</li>
 * <li>飛び道具止め（おまけの効果）: 毎tick、5ブロック以内でまだ見ておらず、除外されておらず（EntityAffectionを参照）、まだ止めていない
 *     エンティティを探す: 大きな火の玉はその場で弾け、小さな火の玉は取り除かれ、それ以外（矢はまず無力化する）はその場で止まってマーカーが付く。
 *     1つごとにlerp(12, 6)のCPを、防御と同じく回復待ちを作らずに払う（1.5.1は強制のlerp(16, 12)）。払えなければ止めない。
 *     0.001 × 難しさの熟練度を得る。</li>
 * </ul>
 *
 * 原作はContextManager.findでモードを引いており、サーバーでは最初に出会ったいずれかのプレイヤーのコンテキストを返す: どのプレイヤーの
 * ダメージも削られ、モードをオンにした者のCPから払われた。ここでは術者自身のダメージだけを削る。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class VecDeviation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "vec_deviation");
    public static final double RANGE = 5;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final Set<UUID> visited = new HashSet<>();
        /** 1.5.2: 基本の維持。外れたらモードを止める。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
        }
    }

    private VecDeviation() { }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    /** 飛び道具止め1つのCP（1.5.2: lerp(12, 6)。1.5.1はlerp(16, 12)）。 */
    public static float stopConsumption(float exp) { return DefenseCost.deviationStop(exp); }
    /** 原作reduceDamageの削り（解析度を掛ける前）。 */
    public static float reduction(float exp) { return ArcGen.lerp(.4f, .9f, exp); }
    /**
     * このダメージを削る割合: lerp(40%, 90%) × 解析度。未元物質に直接触れる攻撃は{@link VectorAnalysis#DIRECT_CONTACT_CAP}まで。
     */
    public static float rate(float exp, float analysis, boolean directContact) {
        float rate = reduction(exp) * Math.max(0, Math.min(1, analysis));
        return directContact ? Math.min(rate, VectorAnalysis.DIRECT_CONTACT_CAP) : rate;
    }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }

    /** キー: モードがオンならオフに、そうでなければオンにする。slotが{@link PassiveSkills#SLOT}なら、パッシブの自動の起動。 */
    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        if (!PassiveSkills.slotMatches(data, ID, slot)
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.VECMANIP.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        // 1.5.2: 起動は無料（起動のオーバーロードは無い）。動いている間の基本の維持は0。開始の過負荷の確保は維持の枠で行う。
        // 偏向と反射は排他: 後から入れた方が勝ち、前の方を止める（止めるだけで再使用の待ちは始めない）。
        VecReflection.stop(player);
        var session = new Session(player, data, slot);
        session.upkeep = DefenseCost.startUpkeep(data, ID, () -> player.getAbilities().instabuild);
        if (session.upkeep == null) return "academy.cast.unlearned";
        ACTIVE.put(player.getUUID(), session);
        SkillModeState.send(player, ID, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        SkillModeState.send(player, ID, false);
        var data = data(player);
        if (data != null) { data.endUpkeep(session.upkeep); AbilitySyncEvents.sync(player, true); }
    }

    /** このtickもモードを続けてよいか。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        // 1.5.2: パッシブで動いているものは、プリセットとスロットを見ず、パッシブのままかを見る。
        return data != null && allowed(player, data) && data.upkeepHeld(session.upkeep) && !(session.level != player.level()
                || !PassiveSkills.sessionMatches(data, ID, session.preset, session.slot));
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        boolean creative = player.getAbilities().instabuild;
        var origin = player.position();
        var level = (ServerLevel) player.level();
        for (var entity : level.getEntities((Entity) null, new AABB(origin, origin).inflate(RANGE),
                e -> e.position().distanceToSqr(origin) <= RANGE * RANGE)) {
            if (!session.visited.add(entity.getUUID()) || EntityAffection.marked(entity)) continue;
            float difficulty = EntityAffection.difficulty(entity);
            if (difficulty < 0) continue;
            // 1.5.2: おまけの効果として1つごとに払う（回復待ちは作らない）。払えなければ止めない。
            if (!DefenseCost.payAddOn(data, ID, stopConsumption(data.getProficiency(ID)), 0, creative)) continue;
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
            // 熟練度のイベントのリスナーが、モードを終えた・置き換えたかもしれない。
            if (ACTIVE.get(player.getUUID()) != session) return;
        }
        AbilitySyncEvents.sync(player, false);
    }
    /** テスト用の入口: モードのサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /** 原作onLivingHurtとreduceDamage。術者だけに対して。 */
    @SubscribeEvent(receiveCanceled = true) public static void hurt(AcademyLivingHurtEvent event) {
        PierceHits.hurtStage(event, ev -> {
            if (ev.getEntity() instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID()) && ev.getAmount() <= 9999) defend(player, ev);
        });
    }
    /** 1回の被弾を解析度に応じて削る（テストからも呼ぶ）。 */
    public static void defend(ServerPlayer player, AcademyLivingHurtEvent event) {
        var data = data(player);
        var session = ACTIVE.get(player.getUUID());
        // 技能が外された（次のtickでモードが終わる前の被弾）なら何もしない。熟練度の加算は未習得を拒否する。
        // 維持が外れたモードも防がない。
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || !data.upkeepHeld(session.upkeep)) return;
        float amount = event.getAmount();
        // 有限でない量（NaN・正負の無限大）は、割合の軽減にも値段にも使わない。
        if (!(amount > 0) || !Float.isFinite(amount)) return;
        var source = event.getSource();
        float exp = data.getProficiency(ID);
        // 解析度は、この1回を守った後に上げる（この1回には、受ける前の解析度を使う）。
        float analysis = VectorAnalysis.analysisOf(player, source);
        float cut = Math.min(amount, amount * rate(exp, analysis, VectorAnalysis.directContact(source)));
        boolean defended = cut > 0 && DefenseCost.pay(data, ID, DefenseCost.Kind.DEVIATION, exp, DefenseCost.priced(player, source, cut), player.getAbilities().instabuild);
        VectorAnalysis.learn(player, source);
        if (!defended) return;
        data.addProficiency(ID, amount * .0006f);
        event.setAmount(amount - cut);
        // 原作MSG_PLAY: 術者の立っている所で。原作の止めたエンティティの音は決して鳴らない（クライアントがサーバーにしか無い印を確かめるため）
        // ので、ここでも止めた発射物は無音。
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_DEVIATION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        AbilitySyncEvents.sync(player, false);
    }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /** 死亡またはエンドからの帰還（作り直し）: 止めるときと同じモードの終了を本人へ送る（画面の波とキーの案内が使用中のまま残らないように。1.5.2）。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (ACTIVE.remove(e.getEntity().getUUID()) != null && e.getEntity() instanceof ServerPlayer p) SkillModeState.send(p, ID, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
