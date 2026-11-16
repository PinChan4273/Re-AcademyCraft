package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.event.SkillReflectEvent;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.SkillModeState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingAttackEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作VecReflection: ベクトル操作のレベル4の防御。1.5.2から防御の部類の基準と解析度に従う。
 *
 * <p>キーでモードをオン・オフする。起動はロック中・クールダウン中は拒否する。
 * 1.5.2から、動いている間は基本の維持を払う（額は0。下の1.5.2の段落。1.5.1までの起動のオーバーロードlerp(80, 40)、
 * 維持費toggle_upkeep.vec_reflection、維持による熟練度は無くした）。
 *
 * <p>守り方（割合 lerp(0.6, 1.2) × そのダメージの解析度。{@link VectorAnalysis}）:
 * <ul>
 * <li>無効: 割合が1以上（熟練度約67%以上）で、そのダメージの解析度が100%のときだけ、攻撃をそのまま取り消す（{@code LivingAttackEvent}。
 *     量の上限なし。{@code /kill}・奈落も、それぞれの分類（防具・防御を無視する物）の解析度が100%なら無効にする）。</li>
 * <li>それ以外（量の上限なし）: min(割合, 1) × 解析度だけ削る（解析度100%未満では無効にならない）。未元物質に直接触れる攻撃（白翼刺突、
 *     甲虫の近接と突進）は、解析度100%でも50%までで、無効にも打ち返しにもならない。</li>
 * <li>防いだ・削った1回ごとに{@link DefenseCost.Kind#REFLECTION}の値段（40→20 CP ＋ 防いだダメージ × 6→3 CP、過負荷6→0）を、回復待ちを
 *     作らずに払う。払えなければその1回は防がない。熟練度は防いだダメージ1ポイントごとに0.0006（偏向と同じ）。</li>
 * <li>偏向か反射が働いている間に受けた1回ごとに、そのダメージの解析度が上がる（{@link VectorAnalysis#learn}）。</li>
 * </ul>
 *
 * <p>おまけの効果（起きたときに別に払う。払えなければ起こさないだけで、モードは続く）:
 * <ul>
 * <li>近接の打ち返し: このモードが取り消したか削った直接の近接攻撃（プレイヤーかmob自身の攻撃で、攻撃者自身が与えたもの）は、解析度100%の
 *     ときだけ、その同じ割合を技能自身のダメージで攻撃者へ打ち返す。ダメージ × lerp(8, 4)のCP（1.5.1はlerp(20, 16)で、払えないとモードが
 *     終わった）とダメージ × 0.0004の経験値で、攻撃者の位置で反射の音と波を出す。1回の攻撃に1回だけ。発射物・棘の鎧・爆発・環境・技能は
 *     打ち返さない（打ち返しは技能のダメージなので、2人の反射者が永遠に打ち合うことはない）。原作のhandleAttackは、この部分を決して
 *     実行しなかった（移植版の、原作とは異なる仕様）。</li>
 * <li>飛び道具の向け直し: 毎tick、4ブロック以内でまだ見ておらず、除外されておらず（EntityAffectionを参照）、まだ向きを変えていない
 *     エンティティを、術者の見ている20ブロック先へ向けてそれ自身の速さで向け直す。その物の解析度（{@link VectorAnalysis#analysisOfEntity}。
 *     Re の能力者が飛ばした物はその能力の解析度、それ以外の物体は物理で100%）が100%のときだけ。難しさ × lerp(60, 30)のCP
 *     （1.5.1はlerp(300, 160)）と0.0008の経験値。火の玉は新しいものに置き換える: 大きな火の玉は威力と撃ち手を保ち、
 *     それ以外の同種のものは小さな火の玉として戻る。原作は新しい火の玉に撃ち手の位置を加速度として渡し、火の玉はそれを保ったが、
 *     1.5.2から加速度も向け直した向きへ揃える（バニラで殴り返した火の玉と同じ0.1）。</li>
 * </ul>
 *
 * <p>1.5.2: 返す向きはプレイヤーごとの「反射方向」（{@link ReflectionDirection}）で決まる。
 * 防御・軽減、解析度、CPの支払い、未元物質の直接の接触の上限と打ち返しの制限を先に判定し、打ち返し・向け直し・光線の折り返しが
 * 成り立ったときだけ、その瞬間にサーバーが1回だけ向きを決める（カメラは動かさない）。
 * <ul>
 * <li>攻撃者へ（既定）: 飛び道具は撃った者（反射の瞬間の位置）へ。撃った者が分からなければ飛んできた向きの逆へ。近接は殴った本人へ
 *     （背後からでも）。光線は反射点から元の発射者へ。</li>
 * <li>視線方向: 飛び道具は今までどおり見ている所へ。近接は照準の方向の{@link #MELEE_RANGE}の中で最初に当たる有効な敵へ（空振りなら
 *     誰にも当てず、払わない）。光線は照準の方向へ。</li>
 * <li>最寄りの敵・視界内の敵: 見通せる有効な敵（{@link #autoCandidate}。既定は敵対モブ、味方と飼い動物は除く、PvPの相手は設定で）の
 *     うち、反射者に最も近い物へ。視界内は前方の視野（半角{@link #VIEW_HALF_ANGLE}度）の中だけ。候補が無ければ視線方向。探す半径は、
 *     飛び道具が{@link #AUTO_RANGE}、近接が{@link #MELEE_RANGE}、光線がその技能の反射の射程。</li>
 * </ul>
 * 向け直した向きが反射者自身の体を通るときは、飛び道具を体の向こう側へ出してから飛ばす（自分に当たって、同じ弾をもう一度防がない）。
 * 途中に壁や相手があって出せないときは、攻撃者へと同じ向きで返す。
 *
 * <p>1.5.2:
 * <ul>
 * <li>動いている間（手動・パッシブとも）、基本の維持を払う（額は0）。偏向とは排他で、後から入れた方が勝つ。</li>
 * <li>向け直した飛び道具（矢など）と、作り直す火の玉の持ち主を、反射した人にする（当たったときの帰属は反射した人。拾えるかは元のまま）。</li>
 * <li>近接の打ち返しの元に、Re 本体の貫通の近接（直接のエンティティが攻撃者で、{@link #MELEE_RANGE}の中）を加え、その打ち返しは貫通で当てる。
 *     遠隔の技能・甲虫（直接のエンティティが甲虫）は打ち返さない。打ち返しの中の打ち返しは起きない（{@code strikingBack}）。</li>
 * </ul>
 *
 * <p>1.5.2: 折り返した光線（レールガン・原子崩し）の量は、元の光線の量 × 反射した人の割合（{@link #beamDamage}）。
 * 近接の打ち返しと同じ考え方。1.12.2版では、レールガンが14で固定、原子崩しが元の技能のlerp(20, 50, 熟練度)の半分だった。
 * 向け直した飛び道具（矢など）は向きだけを変え、その物の威力のまま。近接の打ち返しの量は変えない。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class VecReflection {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "vec_reflection");
    public static final double RANGE = 4;
    /** 原作Raytrace.getLookingPos(player, 20): 視線方向の向け直しが狙う所までの距離。 */
    public static final double LOOK_RANGE = 20;
    /**
     * 1.5.2（暫定の値）: 反射方向の自動の2方式で、飛び道具の向け直しの対象を探す半径（反射者の足元から）。視線方向の向け直しが
     * 狙う所までの距離{@link #LOOK_RANGE}と同じにし、自動の2方式が視線方向より遠くを狙わないようにする。
     */
    public static final double AUTO_RANGE = LOOK_RANGE;
    /**
     * 1.5.2（暫定の値）: 視界内の敵の、前方の視野の半角（度。目から相手の当たり判定の中心への向きと、視線のなす角）。
     * バニラの既定の視野角70度（縦）の半分で、既定の画面なら、どの縦横比でも選んだ相手が画面の中に映っている。
     */
    public static final double VIEW_HALF_ANGLE = 35;
    /**
     * 1.5.2（暫定の値）: 視線方向・自動の2方式の近接の打ち返しの射程（目から）。サバイバルでブロックに届く距離（4.5）と同じ。
     */
    public static final double MELEE_RANGE = 4.5;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final Set<UUID> visited = new HashSet<>();
        /** 取り消しを試して払えなかった攻撃（同じ1回を、削る段でもう一度払おうとしない）。 */
        DamageSource refused; long refusedAt = -1;
        /** 1.5.2: 基本の維持。外れたらモードを止める。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
        }
    }

    private VecReflection() { }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    /** 向け直し1つの、難しさ1あたりのCP（1.5.2: lerp(60, 30)。1.5.1はlerp(300, 160)）。 */
    public static float entityConsumption(float exp) { return DefenseCost.reflectionRedirect(exp, 1); }
    /** 攻撃に対する原作reflectDamageの割合（解析度を掛ける前）。 */
    public static float share(float exp) { return ArcGen.lerp(.6f, 1.2f, exp); }
    /**
     * 1.5.2: 折り返した光線（レールガン・原子崩し）の量。元の光線の量（{@link SkillReflectEvent#damage()}。
     * 技能が撃った量で、補正のイベントの後、設定の倍率の前）× 反射した人の割合{@link #share}。近接の打ち返しと同じ考え方。
     * 設定の倍率は、折り返した光線を当てるとき（{@link SkillCombat#attack}）に1回だけ掛かる。1.12.2版では、レールガンが14、原子崩しが元の技能の
     * lerp(20, 50, 熟練度)の半分だった。
     */
    public static float beamDamage(float damage, float exp) { return damage * share(exp); }
    /** 無効にするか: 割合が1以上で、解析度が100%で、未元物質に直接触れる攻撃でない。 */
    public static boolean nullifies(float exp, float analysis, boolean directContact) {
        return !directContact && VectorAnalysis.full(analysis) && share(exp) >= 1;
    }
    /**
     * 削る割合: min(割合, 1) × 解析度（解析度100%未満では1に届かない）。未元物質に直接触れる攻撃は{@link VectorAnalysis#DIRECT_CONTACT_CAP}まで。
     */
    public static float rate(float exp, float analysis, boolean directContact) {
        float rate = Math.min(1, share(exp)) * Math.max(0, Math.min(1, analysis));
        return directContact ? Math.min(rate, VectorAnalysis.DIRECT_CONTACT_CAP) : rate;
    }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }

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
        VecDeviation.stop(player);
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

    /** 原作Raytrace.getLookingPos(player, 20): 視線が何かに当たる所。エンティティなら目の高さの60%だけ上げた所、または届く範囲の終点。 */
    public static Vec3 lookingAt(ServerPlayer player) {
        var trace = TPSkillHelper.traceLiving(player, LOOK_RANGE, e -> true);
        return trace.entity() == null ? trace.position() : trace.position().add(0, trace.entity().getEyeHeight() * .6, 0);
    }
    /** 原作reflect: 同じ速さで、エンティティの頭から術者の見ている所へ向け直す。 */
    public static Vec3 turned(ServerPlayer player, Entity entity) {
        return lookingAt(player).subtract(entity.getEyePosition()).normalize().scale(entity.getDeltaMovement().length());
    }

    // ---- 1.5.2: 反射方向 ----
    /** 狙う所: 相手の当たり判定の中心。 */
    public static Vec3 aim(Entity target) { return target.getBoundingBox().getCenter(); }
    /**
     * 自動の2方式の候補になる「有効な敵」か（近接の視線方向の打ち返しも同じ）。分類はエレクトロン・ミサイルと同じ
     * （{@link ElectronMissileTargeting#classify}）で、敵対（今まさに反射者を狙う中立を含む）だけ。プレイヤーは、設定でPvPの相手を
     * 含めるときだけで、同じチームの人は除き、サーバーのPvP・チームの同士討ちの設定（{@link SkillCombat#mayAttack}）に従う。
     * 味方・飼い動物・友好・怒っていない中立は候補にしない。
     * <p>
     * 1.5.2: 飼い主のいる生き物（{@link net.minecraft.world.entity.OwnableEntity}で
     * 飼い主がいる物）は、プレイヤーと同じ扱いにする。「PvPの相手も狙う」がオフなら、飼い主が誰でも、反射者に敵対していても候補にしない。
     * オンなら、PvPの相手として狙える飼い主のペットは、敵対していなくても候補にする（「PvP中のペットは対象にしていい。巻き込みたくないなら
     * そのプレイヤーが気をつけるべき」）。狙えない飼い主のペット（{@link #petOfTargetableOwner}）は除く。特定のMODの生き物は名指しせず、
     * バニラの飼い主の仕組みだけで決める。
     */
    public static boolean autoCandidate(ServerPlayer player, PlayerAbilityData data, Entity e) {
        if (e == player || !(e instanceof LivingEntity)) return false;
        if (e instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwnerUUID() != null)
            return data.isReflectionPvp() && petOfTargetableOwner(player, owned) && SkillCombat.mayAttack(player, e, ID);
        var kind = ElectronMissileTargeting.classify(player, e);
        if (kind == null) return false;
        if (kind == ElectronMissileTargetKind.PLAYER) {
            if (!data.isReflectionPvp() || ElectronMissileTargeting.ally(player, e)) return false;
        } else if (kind != ElectronMissileTargetKind.HOSTILE) return false;
        return SkillCombat.mayAttack(player, e, ID);
    }
    /**
     * 飼い主のいる生き物が、PvPの相手として狙える飼い主のペットか。次のどれかなら狙えない:
     * 自分のペット、生き物自身が自分と同じチーム、サーバーのPvPかAcademyのプレイヤー攻撃の設定がオフ、飼い主がその場にいて同じチームか
     * チームの同士討ちの禁止で攻撃できない（{@code canHarmPlayer}）、飼い主がその場にいないときは、名前が分かる範囲で同じチーム。
     * 飼い主がその場にいない（オフライン）で同じチームと分からなければ、狙える。
     */
    public static boolean petOfTargetableOwner(ServerPlayer player, net.minecraft.world.entity.OwnableEntity owned) {
        var cache = player.serverLevel().getServer().getProfileCache();
        return petOfTargetableOwner(player, owned, cache == null ? null : cache::get);
    }
    /**
     * {@link #petOfTargetableOwner(ServerPlayer, net.minecraft.world.entity.OwnableEntity)} の中身。その場にいない飼い主の名前を、
     * 渡された引き方（ふつうはサーバーのプロフィールの記録 {@code GameProfileCache#get(UUID)}）で調べる。nullなら名前は分からない扱い。
     * <p>
     * 引き方を引数にしたのはテストのため: GameTest のサーバーにはプロフィールの記録が無く（{@code getProfileCache()}が
     * null）、「その場にいない同じチームの飼い主」を確かめられないので、テストは自分で作った記録を渡す。製品は上の入口から
     * サーバーの記録だけを渡すので、挙動は変わらない。
     */
    public static boolean petOfTargetableOwner(ServerPlayer player, net.minecraft.world.entity.OwnableEntity owned,
            @Nullable java.util.function.Function<UUID, java.util.Optional<com.mojang.authlib.GameProfile>> awayProfiles) {
        var ownerId = owned.getOwnerUUID();
        if (ownerId == null || ownerId.equals(player.getUUID()) || !(owned instanceof Entity pet) || player.isAlliedTo(pet)) return false;
        var server = player.serverLevel().getServer();
        if (!server.isPvpAllowed() || !AcademyConfig.ATTACK_PLAYERS.get()) return false;
        var owner = owned.getOwner();
        if (owner instanceof net.minecraft.world.entity.player.Player online)
            return !player.isAlliedTo(online) && player.canHarmPlayer(online);
        if (owner != null) return !player.isAlliedTo(owner);
        // その場にいない飼い主: 名前が分かれば、そのチームが自分と同じかを見る。
        var mine = player.getTeam();
        if (mine != null && awayProfiles != null) {
            var profile = awayProfiles.apply(ownerId);
            if (profile.isPresent()) {
                var theirs = player.getScoreboard().getPlayersTeam(profile.get().getName());
                if (theirs != null && mine.isAlliedTo(theirs)) return false;
            }
        }
        return true;
    }
    /**
     * 自動の2方式の対象: 反射者から半径rangeの中の、見通せる（壁越しでない）有効な敵のうち、反射者に最も近い物（同じ距離ならIDの
     * 小さい物）。視界内の敵は、さらに前方の視野（{@link #VIEW_HALF_ANGLE}）の中だけ。距離と角度の測り方はエレクトロン・ミサイルと同じ。
     * 自動の方式でないか、候補が無ければnull。
     */
    public static @Nullable LivingEntity autoTarget(ServerPlayer player, PlayerAbilityData data, ReflectionDirection mode, double range) {
        if (!mode.automatic() || !(range > 0) || !Double.isFinite(range)) return null;
        LivingEntity best = null; double closest = Double.MAX_VALUE;
        for (var e : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(range),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            double distance = e.distanceToSqr(player);
            if (distance > range * range || distance > closest || distance == closest && best != null && e.getId() > best.getId()) continue;
            if (!autoCandidate(player, data, e)) continue;
            if (mode == ReflectionDirection.VISIBLE && ElectronMissileTargeting.angle(player, e) > VIEW_HALF_ANGLE) continue;
            if (!player.hasLineOfSight(e)) continue;
            best = (LivingEntity) e; closest = distance;
        }
        return best;
    }
    /** 飛び道具を撃った者（反射の瞬間に同じ世界で生きている、反射者以外の者）。分からなければnull。 */
    public static @Nullable Entity shooter(ServerPlayer player, Entity entity) {
        var owner = entity instanceof Projectile projectile ? projectile.getOwner() : null;
        return owner != null && owner != player && owner.isAlive() && owner.level() == entity.level() ? owner : null;
    }
    /**
     * 飛んできた向き: 速度。火の玉は撃たれた直後は速度が無く加速度だけで進むので、速度が無ければ加速度の向き。どちらも無ければ0。
     */
    public static Vec3 incoming(Entity entity) {
        var velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 1e-12 || !(entity instanceof AbstractHurtingProjectile fireball)) return velocity;
        // 1.21の火の玉は速度の向きへ加速するので、速度が無ければ加速の向きも無い。1.5.1までに作り直して保存された火の玉だけは、
        // 保存した固定の加速（{@link #FIREBALL_PUSH}）の向きを使う。
        var push = fireball.getPersistentData().getCompound(FIREBALL_PUSH);
        return new Vec3(push.getDouble("x"), push.getDouble("y"), push.getDouble("z"));
    }
    /**
     * 飛び道具を向け直す向き（長さ1。決まらなければ0）。反射方向で決める: 攻撃者へは撃った者の中心へ（分からなければ飛んできた向きの逆）、
     * 視線方向は見ている所へ（{@link #turned}と同じ）、自動の2方式は{@link #AUTO_RANGE}の中の対象の中心へ（いなければ視線方向）。
     * 向きはエンティティの頭から測る。
     */
    public static Vec3 redirectDirection(ServerPlayer player, PlayerAbilityData data, Entity entity) {
        var origin = entity.getEyePosition();
        var mode = data.getReflectionDirection();
        Vec3 direction = switch (mode) {
            case ATTACKER -> attackerDirection(player, entity);
            case LOOK -> lookingAt(player).subtract(origin);
            case NEAREST, VISIBLE -> {
                var target = autoTarget(player, data, mode, AUTO_RANGE);
                yield (target != null ? aim(target) : lookingAt(player)).subtract(origin);
            }
        };
        return direction.normalize();
    }
    /**
     * 「攻撃者へ」の向き（長さ1。決まらなければ0）: 撃った者の中心へ、分からなければ飛んできた向きの逆。向きはエンティティの頭から測る。
     * 体の向こう側へ出せないときの返し方にも使う。向きを変える前に測ること（飛んできた向きを使うため）。
     */
    public static Vec3 attackerDirection(ServerPlayer player, Entity entity) {
        var owner = shooter(player, entity);
        return (owner != null ? aim(owner).subtract(entity.getEyePosition()) : incoming(entity).scale(-1)).normalize();
    }
    /** 飛び道具を向け直す速度: {@link #redirectDirection}の向きに、今の速さ。 */
    public static Vec3 redirected(ServerPlayer player, PlayerAbilityData data, Entity entity) {
        return redirectDirection(player, data, entity).scale(entity.getDeltaMovement().length());
    }
    /**
     * 向け直した向きが反射者の体を通るなら、飛び道具を体の向こう側（向きに沿って体から出る所）へ出す。自分に当たって同じ弾をもう一度
     * 防ぐ（CPをもう一度払う）ことが無いように。体を通らなければ動かさない。
     * <p>
     * 1.5.2: 移す前に、今の位置から出る所までに、ぶつかるブロック（{@code Level.clip}、衝突の形）と、反射者以外の
     * 当たる生き物・物が無いかを調べる。どちらかがあれば、弾は動かさず{@link Clearance#BLOCKED}を返す（壁や相手を飛び越えない）。
     * 呼ぶ側は、そのときは「攻撃者へ」の向き（{@link #attackerDirection}）で返す。
     */
    public enum Clearance {
        /** 向きが体を通らない（動かさない）。 */
        NOT_THROUGH,
        /** 体の向こう側へ動かした。 */
        MOVED,
        /** 体を通るが、途中にブロックか相手があって動かせない（動かさない）。 */
        BLOCKED
    }
    /** 体を通るなら、途中に何も無いときだけ体の向こう側へ出す（上の説明を参照）。 */
    public static Clearance clearOf(ServerPlayer player, Entity entity, Vec3 velocity) {
        double length = velocity.length();
        if (!(length > 1e-6) || !Double.isFinite(length)) return Clearance.NOT_THROUGH;
        var dir = velocity.scale(1 / length);
        var self = entity.getBoundingBox();
        double hx = self.getXsize() / 2, hy = self.getYsize() / 2, hz = self.getZsize() / 2;
        var box = player.getBoundingBox().inflate(hx + .05, hy + .05, hz + .05);
        var center = self.getCenter();
        double reach = center.distanceTo(box.getCenter()) + box.getXsize() + box.getYsize() + box.getZsize();
        var far = center.add(dir.scale(reach));
        if (!box.contains(center) && box.clip(center, far).isEmpty()) return Clearance.NOT_THROUGH;
        var exit = box.clip(far, center);
        if (exit.isEmpty()) return Clearance.NOT_THROUGH;
        var to = exit.get().add(dir.scale(.05));
        if (blockedBetween(player, entity, center, to, hx, hy, hz)) return Clearance.BLOCKED;
        var shift = to.subtract(center);
        entity.setPos(entity.getX() + shift.x, entity.getY() + shift.y, entity.getZ() + shift.z);
        return Clearance.MOVED;
    }
    /**
     * 弾の中心が from から to まで動く間に、ブロック（衝突の形）か、反射者と弾自身を除く当たる相手（生きていて、傍観者でなく、選べる物）に
     * ぶつかるか。相手は弾の大きさの半分だけ広げた当たり判定で調べる。ブロックは中心の線と、弾の大きさの角の線（上下左右の4本）で調べる。
     */
    private static boolean blockedBetween(ServerPlayer player, Entity entity, Vec3 from, Vec3 to, double hx, double hy, double hz) {
        var level = entity.level();
        for (var corner : new double[][] {{0, 0, 0}, {hx, hy, hz}, {-hx, hy, -hz}, {hx, -hy, -hz}, {-hx, -hy, hz}}) {
            var offset = new Vec3(corner[0] * .99, corner[1] * .99, corner[2] * .99);
            var hit = level.clip(new net.minecraft.world.level.ClipContext(from.add(offset), to.add(offset),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return true;
        }
        var path = new AABB(from, to).inflate(hx, hy, hz);
        for (var other : level.getEntities(entity, path, e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable()))
            if (other.getBoundingBox().inflate(hx, hy, hz).clip(from, to).isPresent() || other.getBoundingBox().inflate(hx, hy, hz).contains(from)) return true;
        return false;
    }
    /**
     * 近接の打ち返しの相手。攻撃者へは殴った本人。視線方向は照準の方向、自動の2方式は{@link #MELEE_RANGE}の中の対象への向き（いなければ
     * 照準の方向）に、目から{@link #MELEE_RANGE}だけ調べ、壁より手前で最初に当たる有効な敵（{@link #autoCandidate}）。いなければnull
     * （空振り。殴った相手にも当てない）。
     */
    public static @Nullable LivingEntity strikeTarget(ServerPlayer player, PlayerAbilityData data, LivingEntity attacker) {
        var mode = data.getReflectionDirection();
        if (mode == ReflectionDirection.ATTACKER) return attacker;
        var start = player.getEyePosition();
        var direction = player.getLookAngle();
        if (mode.automatic()) {
            var target = autoTarget(player, data, mode, MELEE_RANGE);
            if (target != null && aim(target).subtract(start).lengthSqr() > 1e-9) direction = aim(target).subtract(start);
        }
        var end = start.add(direction.normalize().scale(MELEE_RANGE));
        var trace = TPSkillHelper.trace(player, start, end, e -> autoCandidate(player, data, e));
        return trace.entity() instanceof LivingEntity living ? living : null;
    }
    /**
     * 光線を折り返す向き（長さ1）。攻撃者へは反射者の目から元の発射者の中心へ、視線方向は照準の方向、自動の2方式は技能の反射の射程の中の
     * 対象の中心へ（いなければ照準の方向）。
     */
    public static Vec3 beamDirection(ServerPlayer player, PlayerAbilityData data, ServerPlayer caster, double range) {
        var eye = player.getEyePosition();
        var mode = data.getReflectionDirection();
        Vec3 to = switch (mode) {
            case ATTACKER -> caster.isAlive() && caster.level() == player.level() ? aim(caster).subtract(eye) : null;
            case LOOK -> null;
            case NEAREST, VISIBLE -> {
                var target = autoTarget(player, data, mode, range);
                yield target == null ? null : aim(target).subtract(eye);
            }
        };
        return to == null || to.lengthSqr() < 1e-9 ? player.getLookAngle().normalize() : to.normalize();
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
            if (EntityAffection.marked(entity) || session.visited.contains(entity.getUUID())) continue;
            float difficulty = EntityAffection.difficulty(entity);
            if (difficulty < 0) { session.visited.add(entity.getUUID()); continue; }
            // 1.5.2: 向け直しは、その物の解析度が100%のときだけ（解析が進めば、まだ範囲にいる物を後のtickで向け直せる）。
            if (!VectorAnalysis.full(VectorAnalysis.analysisOfEntity(player, entity))) continue;
            session.visited.add(entity.getUUID());
            // 1.5.2: おまけの効果として払う（回復待ちは作らない）。払えなければ向け直さない。
            if (!DefenseCost.payAddOn(data, ID, DefenseCost.reflectionRedirect(data.getProficiency(ID), difficulty), 0, creative)) continue;
            // 1.5.2: 向きは反射方向で、この瞬間に1回だけ決める（以後は通常の飛翔と衝突に従う）。
            var direction = redirectDirection(player, data, entity);
            // 体の向こう側へ出せないときの返し方（攻撃者へ）。飛んできた向きを使うので、向きを変える前に測る。
            var back = attackerDirection(player, entity);
            double speed = entity.getDeltaMovement().length();
            if (entity instanceof AbstractHurtingProjectile fireball) replace(player, level, fireball, direction, back, speed);
            else {
                // 1.5.2: 向け直した飛び道具の持ち主を反射した人にする。矢の setOwner は拾えるかを持ち主で決め直すので、元に戻す。
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) takeOver(projectile, player);
                entity.setDeltaMovement(direction.scale(speed));
                if (clearOf(player, entity, direction) == Clearance.BLOCKED && back.lengthSqr() > 1e-12) {
                    // 途中に壁や相手があって体の向こう側へ出せない: 攻撃者へと同じ向きで返す（体を通らないので、自分にも壁にも刺さらない）。
                    entity.setDeltaMovement(back.scale(speed));
                    clearOf(player, entity, back);
                }
                entity.hurtMarked = true;
                EntityAffection.mark(entity);
            }
            // 原作reflectEffect: 向きを変えたエンティティの頭で。
            var head = entity.getEyePosition();
            level.playSound(null, head.x, head.y, head.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_REFLECTION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
            io.github.pinchan4273.reacademycraft.network.VmWave.send(player, new io.github.pinchan4273.reacademycraft.network.VmWave(level.dimension().location(),
                    head, player.getYHeadRot(), player.getXRot(), 0, 0, 2, 1.1f));
            data.addProficiency(ID, difficulty * .0008f);
            // 熟練度のイベントのリスナーが、モードを終えた・置き換えたかもしれない。
            if (ACTIVE.get(player.getUUID()) != session) return;
        }
        AbilitySyncEvents.sync(player, false);
    }
    /** テスト用の入口: モードのサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /**
     * 1.5.2: 向け直した飛び道具の持ち主を、反射した人にする。矢（{@link net.minecraft.world.entity.projectile.AbstractArrow}）の{@code setOwner}は、プレイヤーが
     * 持ち主だと拾えるかを決め直すので、元の値に戻す（拾えない矢を拾えるようにしない）。
     */
    public static void takeOver(net.minecraft.world.entity.projectile.Projectile projectile, ServerPlayer player) {
        var pickup = projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow ? arrow.pickup : null;
        projectile.setOwner(player);
        if (pickup != null) ((net.minecraft.world.entity.projectile.AbstractArrow) projectile).pickup = pickup;
    }
    /**
     * 原作createNewFireball。1.5.2から、新しい火の玉の持ち主は反射した人（バニラで殴り返した火の玉と同じ）。
     * 攻撃者へ返すときの向きは、消す前の元の火の玉から決めてある。大きな火の玉は威力を保つ。1.5.2: 加速度も向け直した向きへ揃える。
     */
    private static void replace(ServerPlayer player, ServerLevel level, AbstractHurtingProjectile source, Vec3 direction, Vec3 back, double speed) {
        source.discard();
        AbstractHurtingProjectile fireball;
        if (source instanceof LargeFireball large) {
            int power = large.saveWithoutId(new CompoundTag()).getByte("ExplosionPower");
            fireball = new LargeFireball(level, player, direction, power);
        } else {
            fireball = new SmallFireball(level, player, direction);
        }
        fireball.setPos(source.getX(), source.getY(), source.getZ());
        aimFireball(fireball, direction, speed);
        if (clearOf(player, fireball, direction) == Clearance.BLOCKED && back.lengthSqr() > 1e-12) {
            // 体の向こう側へ出せない: 攻撃者へと同じ向きで返す。
            aimFireball(fireball, back, speed);
            clearOf(player, fireball, back);
        }
        EntityAffection.mark(fireball);
        level.addFreshEntity(fireball);
    }
    /** 1.5.1までに作り直して保存された火球の、Forge 1.20.1版の固定の加速（xPower・yPower・zPower）。1.5.2から新しくは置かない（{@link #aimFireball}）。 */
    public static final String FIREBALL_PUSH = "academy:reflected_fireball_push";
    /**
     * Forge 1.20.1版の火球のtickは、速度を(速度 + 固定の加速) * 慣性にした（慣性は0.95、水中は0.8）。1.21のtickは加速0の火球の速度を
     * 速度 * 慣性にするので、その後に固定の加速 * 慣性を足すと同じ式になる。サーバーだけで行う（クライアントの見た目は位置の同期で直る）。
     * プレイヤーが殴って向きを変えた火球は、1.21が加速を0.1に戻す。Forge版も殴った向きの0.1の固定の加速になり、それは1.21の
     * 速度の向きへの加速と同じなので、ここでは何もしない。
     */
    @SubscribeEvent public static void fireballTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractHurtingProjectile fireball) || fireball.isRemoved() || fireball.level().isClientSide) return;
        var saved = fireball.getPersistentData();
        if (!saved.contains(FIREBALL_PUSH)) return;
        if (fireball.accelerationPower != 0) { saved.remove(FIREBALL_PUSH); return; }
        var push = saved.getCompound(FIREBALL_PUSH);
        double inertia = fireball.isInWater() ? .8f : .95f;
        fireball.setDeltaMovement(fireball.getDeltaMovement().add(push.getDouble("x") * inertia, push.getDouble("y") * inertia, push.getDouble("z") * inertia));
    }

    /**
     * 原作は撃ち手の位置を加速度として渡していた（向きが決まらない）。殴り返したバニラの火の玉と同じく、向きに沿って0.1。
     * 1.21の火球は、今の速度の向きへaccelerationPowerだけ加速する。速度を向きに揃えるので、Forge版の向きに沿った固定の加速0.1と同じ動きになる。
     */
    private static void aimFireball(AbstractHurtingProjectile fireball, Vec3 direction, double speed) {
        fireball.setDeltaMovement(direction.scale(speed));
        fireball.accelerationPower = .1;
        fireball.getPersistentData().remove(FIREBALL_PUSH);
    }
    /** 原作onLivingAttack: 無効にできる攻撃（割合1以上・解析度100%）はまったく当たらない。 */
    @SubscribeEvent(receiveCanceled = true) public static void attacked(AcademyLivingAttackEvent event) {
        PierceHits.attack(event, ev -> {
            if (!(ev.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID())) return;
            if (nullify(player, ev.getSource(), ev.getAmount())) ev.setCanceled(true);
        });
    }
    /** 取り消すなら値段を払ってtrue（テストからも呼ぶ）。払えなければ、この1回は削る段でも防がない。 */
    public static boolean nullify(ServerPlayer player, DamageSource source, float amount) {
        var data = data(player);
        var session = ACTIVE.get(player.getUUID());
        // 技能が外された（次のtickでモードが終わる前の被弾）なら何もしない。熟練度の加算は未習得を拒否する。
        // 有限でない量（NaN・正負の無限大）は、無効にも値段にも使わない（無敵を貫く物の体力換算より先に弾く）。
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || !data.upkeepHeld(session.upkeep)
                || !(amount > 0) || !Float.isFinite(amount)) return false;
        float exp = data.getProficiency(ID);
        if (!nullifies(exp, VectorAnalysis.analysisOf(player, source), VectorAnalysis.directContact(source))) return false;
        float blocked = DefenseCost.priced(player, source, amount);
        boolean paid = DefenseCost.pay(data, ID, DefenseCost.Kind.REFLECTION, exp, blocked, player.getAbilities().instabuild);
        VectorAnalysis.learn(player, source);
        if (!paid) { session.refused = source; session.refusedAt = player.level().getGameTime(); return false; }
        data.addProficiency(ID, blocked * .0006f);
        strikeBack(player, data, source, amount);
        return true;
    }
    /** 原作onLivingHurt: 通ったものを割合だけ削る。 */
    @SubscribeEvent(receiveCanceled = true) public static void hurt(AcademyLivingHurtEvent event) {
        PierceHits.hurtStage(event, ev -> { if (ev.getEntity() instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID())) defend(player, ev); });
    }
    /** 1回の被弾を解析度に応じて削る（テストからも呼ぶ）。 */
    public static void defend(ServerPlayer player, AcademyLivingHurtEvent event) {
        var data = data(player);
        var session = ACTIVE.get(player.getUUID());
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || !data.upkeepHeld(session.upkeep)) return;
        var source = event.getSource();
        float amount = event.getAmount();
        // 有限でない量は、割合の軽減にも値段にも使わない（残りのダメージをNaNにしない）。
        if (!(amount > 0) || !Float.isFinite(amount)) return;
        // 取り消す段で払えなかった同じ1回は、もう防がない。
        if (session.refused == source && session.refusedAt == player.level().getGameTime()) return;
        float exp = data.getProficiency(ID);
        float analysis = VectorAnalysis.analysisOf(player, source);
        boolean contact = VectorAnalysis.directContact(source);
        float cut = Math.min(amount, amount * rate(exp, analysis, contact));
        boolean defended = cut > 0 && DefenseCost.pay(data, ID, DefenseCost.Kind.REFLECTION, exp, DefenseCost.priced(player, source, cut), player.getAbilities().instabuild);
        VectorAnalysis.learn(player, source);
        if (!defended) return;
        data.addProficiency(ID, cut * .0006f);
        float left = amount - cut;
        event.setAmount(left);
        if (left <= 0) event.setCanceled(true);
        // 打ち返しは解析度100%のときだけ（未元物質に直接触れる攻撃は打ち返さない）。
        if (!contact && VectorAnalysis.full(analysis)) strikeBack(player, data, source, amount);
    }

    private static boolean strikingBack;
    /**
     * プレイヤーかmob自身の、攻撃者自身が与えた打撃: 矢・棘の鎧・爆発・環境・技能ではない（打ち返しは技能のダメージなので、
     * 2人の反射者が永遠に打ち合うことはない）。
     */
    public static boolean closeAttack(ServerPlayer player, DamageSource source) {
        return (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || reMeleePierce(player, source))
                && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && attacker != player && attacker.isAlive();
    }
    /**
     * 1.5.2: Re 本体の貫通の近接か（Re の技能の打撃で当たり方が貫通、直接のエンティティが攻撃者自身で、{@link #MELEE_RANGE}の中）。
     * 打ち返しの元にし、打ち返しも貫通で当てる。遠隔の技能（距離の外）・甲虫（直接のエンティティが甲虫）は含めない。
     */
    public static boolean reMeleePierce(ServerPlayer player, DamageSource source) {
        return source instanceof SkillDamageSource && SkillDamageSource.isPierce(source) && source.getEntity() != null
                && source.getDirectEntity() == source.getEntity()
                && source.getEntity().getBoundingBox().distanceToSqr(player.getEyePosition()) <= MELEE_RANGE * MELEE_RANGE;
    }
    /**
     * モードが取り消したか削ったばかりの近接攻撃への打ち返し（クラスの説明を参照）。払えなければ打ち返さない（モードは続く）。
     * 1.5.2: 相手は反射方向で決める（{@link #strikeTarget}）。空振りなら誰にも当てず、払わない。
     */
    private static void strikeBack(ServerPlayer player, PlayerAbilityData data, DamageSource source, float damage) {
        if (strikingBack || !closeAttack(player, source) || !(damage > 0) || !Float.isFinite(damage)) return;
        var target = strikeTarget(player, data, (LivingEntity) source.getEntity());
        if (target == null) return;
        float exp = data.getProficiency(ID);
        if (!DefenseCost.payAddOn(data, ID, DefenseCost.reflectionStrikeBack(exp, damage), 0, player.getAbilities().instabuild)) return;
        strikingBack = true;
        // 1.5.2: 元が Re の貫通の近接なら、打ち返しも貫通（種類は academy:vecmanip_pierce）。
        var style = reMeleePierce(player, source) ? SkillDamageSource.Style.PIERCE : SkillDamageSource.Style.PLAIN;
        // 1.5.2: 貫通の打ち返しは、元の打撃の貫通の段を引き継ぐ。
        var tier = style == SkillDamageSource.Style.PIERCE ? SkillDamageSource.pierceTier(source) : null;
        try { SkillCombat.attack(player, target, ID, damage * share(exp), style, tier); }
        finally { strikingBack = false; }
        data.addProficiency(ID, damage * .0004f);
        var head = target.getEyePosition(); var level = player.serverLevel();
        level.playSound(null, head.x, head.y, head.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_REFLECTION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        io.github.pinchan4273.reacademycraft.network.VmWave.send(player, new io.github.pinchan4273.reacademycraft.network.VmWave(level.dimension().location(),
                head, player.getYHeadRot(), player.getXRot(), 0, 0, 2, 1.1f));
        AbilitySyncEvents.sync(player, false);
    }

    /**
     * 1.5.2: 反射に対応する光線（レールガン・原子崩しの光線）。反射者が自分以外の術者の光線を受けたとき、そのダメージを
     * 無効にできる（{@link #nullify}と同じ条件と値段）なら、取り消して反射方向へ折り返す。光線は反射者に当たらないので、払うのはこの1回だけ。
     * 無効にできなければ取り消さず、光線は今までどおり当たり、無効・軽減の段が扱う。
     */
    @SubscribeEvent public static void beamReflect(SkillReflectEvent event) {
        if (event.isCanceled() || !(event.target() instanceof ServerPlayer player) || event.caster() == player || !ACTIVE.containsKey(player.getUUID())) return;
        if (reflectBeam(player, event)) event.setCanceled(true);
    }
    /** 光線を折り返すなら値段を払い、向きを決めてtrue（テストからも呼ぶ）。払えなければfalse（解析もここでは進めず、当たったときに進む）。 */
    public static boolean reflectBeam(ServerPlayer player, SkillReflectEvent event) {
        var data = data(player);
        var session = ACTIVE.get(player.getUUID());
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || !data.upkeepHeld(session.upkeep)) return false;
        // 実際に当たるときと同じ量で、無効と値段を判定する。event.damage() は、ふつうのダメージの補正のイベント（SkillAttack）を通った後の
        // 量（RailgunAttack が先に通す）で、そこへ全体と技能ごとのダメージの倍率を掛ける（SkillCombat と同じ順）。
        float amount = (float) (event.damage() * AcademyConfig.DAMAGE_SCALE.get() * AcademyConfig.skillDamageScale(event.skill()));
        if (!(amount > 0) || !Float.isFinite(amount)) return false;
        var source = new SkillDamageSource(event.caster(), event.skill());
        float exp = data.getProficiency(ID);
        if (!nullifies(exp, VectorAnalysis.analysisOf(player, source), VectorAnalysis.directContact(source))) return false;
        float blocked = DefenseCost.priced(player, source, amount);
        if (!DefenseCost.pay(data, ID, DefenseCost.Kind.REFLECTION, exp, blocked, player.getAbilities().instabuild)) return false;
        VectorAnalysis.learn(player, source);
        data.addProficiency(ID, blocked * .0006f);
        // 1.5.2: 折り返す量は、元の光線の量 × 割合。割合は、無効の判定に使ったのと同じ熟練度（この反射で増える前）で決める。
        event.setReflection(player, beamDirection(player, data, event.caster(), event.reflectRange()), beamDamage(event.damage(), exp));
        var head = player.getEyePosition(); var level = player.serverLevel();
        level.playSound(null, head.x, head.y, head.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_VEC_REFLECTION.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        io.github.pinchan4273.reacademycraft.network.VmWave.send(player, new io.github.pinchan4273.reacademycraft.network.VmWave(level.dimension().location(),
                head, player.getYHeadRot(), player.getXRot(), 0, 0, 2, 1.1f));
        AbilitySyncEvents.sync(player, false);
        return true;
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
