package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作LightShield（WeAthFolD）: meltdownerのもう1つのレベル2技能。1.5.2から防御の部類の基準に従う。
 *
 * <p>盾は術者の前にある、盾の形をしたビーム（反射ではない）。1回押すとON、もう一度押すとOFFのトグルで、時間制限は無い。
 * <ul>
 * <li>維持（1.5.2）: 盾が動いている間（手動・パッシブとも）、基本の維持を払う（額は0。開始の過負荷の確保だけ）
 *     （{@link DefenseCost#startUpkeep}。1.5.1までの toggle_upkeep.light_shield は使わない）。維持が外れたら盾は止まる。起動のオーバーロードは無い。
 *     起動はロック中・クールダウン中は拒否する。</li>
 * <li>防御（1.5.2）: 効く範囲（{@link #covers}: 近接・射撃などの直接の原因がある攻撃、飛び道具、爆発）の攻撃で、盾の円を通るものを
 *     一般に{@link #GENERAL_RATE}（30%）軽減する。原子崩しの通常ビーム（{@link #NORMAL_BEAMS}）は100%相殺する（反射ではない）。
 *     Re 本体の{@code Style.PIERCE}の打撃は盾を完全に通る（軽減も費用も無い）。1回の大きさに上限は無い。防いだ1回ごとに
 *     {@link DefenseCost.Kind#LIGHT_SHIELD}の値段（30→20 CP ＋ 防いだダメージ × 6→3 CP、過負荷4→0）を回復待ちを作らずに払い、払えなければ
 *     その1回は防がない。</li>
 * <li>触れた相手への攻撃（おまけの効果）: 生き物には、盾のセッションごと・対象ごとに、前の接触から{@link #CONTACT_INTERVAL}tickに
 *     最大1回、標準の被ダメージ後の待ちには従わない専用の型（{@link SkillDamageSource.Style#CONTACT}）で当てる。1体ごとにCP 30→15・
 *     過負荷2→0を、防御と同じく回復待ちを作らずに払う（1.5.1はCP 50→30・過負荷5→3）。払えなければ当てない。間隔は課金して攻撃した時刻から測る。
 *     生き物でないもの（ドロップしたアイテム、経験値オーブなど）は以前どおり、そのinvulnerableTimeだけを見て、通常の型で焼く。</li>
 * <li>止めた後: 熟練度で60→20tickの決まった長さのクールダウン（1.5.1の「張っていた時間 × 2 → 1」から変えた）。
 *     1.5.1の5秒の鈍足の反動は無くした。</li>
 * </ul>
 *
 * <p>共通の盾面と自動追従: 盾はサーバーが持つ1枚の有限の面（法線・距離・半径、
 * {@link LightShieldGeometry}）で、防御の判定とクライアントの描画（{@link io.github.pinchan4273.reacademycraft.network.LightShieldState}）は
 * 同じ値を使う。向きはプレイヤーの設定（{@link LightShieldMode}、AUTOかMANUAL）に従って{@link LightShieldTargeting}が選んだ目標へ、
 * 1tickに最大{@link LightShieldGeometry#TURN_DEGREES}度ずつ回る。起動したときの向きは視線。カメラは動かさない。盾は1枚で、全方向を同時には防がない。
 * <ul>
 * <li>防御: 攻撃の元（直接の原因のEntity）から術者への経路が、盾の円を通るときだけ。直接の原因が無い攻撃（爆発など）は1.2.0どおり防ぐ。</li>
 * <li>生き物への接触攻撃: 盾の円を、法線の向きへ{@link #REACH}まで伸ばした円柱に入った生き物。</li>
 * <li>生き物でないもの（アイテム・経験値オーブ）の焼却: 1.2.0のまま（視線のyawの前方±60度・半径3）。</li>
 * </ul>
 *
 * <p>暫定の作り:
 * <ul>
 * <li>押し留め（{@link #hold}）: 接触の円柱に入った生き物について、毎tick、術者へ近づく速度の成分を消す。盾の面より内側へ入り込んで
 *     いれば、外への小さな補正（1tickに最大{@link #SEPARATION}、ノックバック耐性の分だけ弱める）を加える。費用は無い。
 *     プレイヤーは術者が傷つけられる相手（{@code canHarmPlayer}）だけ。無敵・傍観者・クリエイティブ・ボス・乗り物に乗っているものは押さない。</li>
 * <li>飛来物（{@link #intercept}）: 術者に当たる直前の飛来物の位置が、盾の円を通る経路にあれば、防御と同じ値段で当たりを止めて消す。
 *     値段の「防いだダメージ」は、矢（{@link AbstractArrow}）はバニラと同じ速さ × 基本ダメージの見積もり、それ以外は0（回数の分だけ）。
 *     払えないとき、円の外からのものは、これまでどおり当たる。</li>
 * </ul>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class LightShield {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "light_shield");
    /** 止めた後の決まったクールダウン（tick。熟練度0% → 100%）。 */
    public static final int COOLDOWN_AT_ZERO = 60, COOLDOWN_AT_FULL = 20;
    /** 同じ生き物への接触攻撃の最小の間隔（tick）。 */
    public static final int CONTACT_INTERVAL = 8;
    private static final double REACH = 3, ARC_DEGREES = 60;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    /** 盾の状態を送り直す間隔（tick）。向きや大きさが変わらなくても、この間隔で送る。 */
    private static final int RESYNC_TICKS = 10;
    /** 押し留めの、外への補正の上限（1tickあたりの速さ。暫定値）。 */
    public static final double SEPARATION = .1;
    private static long nextToken;
    /** 一般の攻撃を軽減する割合（1.5.2）。 */
    public static final float GENERAL_RATE = .3f;
    /** 100%相殺する原子崩しの通常ビーム（1.5.2）。主砲（{@link Meltdowner}）と、盾・Jet の接触は含めない。 */
    public static final java.util.Set<ResourceLocation> NORMAL_BEAMS = java.util.Set.of(RayBarrage.ID, ElectronBomb.ID, ScatterBomb.ID, ElectronMissile.ID);

    private static final class Session {
        final Level level; final int preset, slot; final float exp;
        int ticks;
        /** このセッションが最近接触攻撃した生き物と、そのときのticks。CONTACT_INTERVAL tick後に忘れる（セッションと共に消える）。 */
        final Map<UUID, Integer> lastContact = new HashMap<>();
        /** 盾面の法線（単位ベクトル）。起動したときは視線。 */
        Vec3 normal;
        /** 追従の状態（標的と、選んだtick）。 */
        final LightShieldTargeting.Memory memory = new LightShieldTargeting.Memory();
        /** 状態の通信: セッションのトークン、シーケンス番号、最後に送った法線・半径・tick。 */
        final long token = ++nextToken;
        int sequence; Vec3 sentNormal; float sentRadius = -1; int sentTick;
        /** テストと記録用の数: 押し留めたtick数、止めた飛来物の数。 */
        int held, intercepted;
        /** 1.5.2: 基本の維持。外れたら盾を止める。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID);
            normal = LightShieldGeometry.unit(player.getLookAngle(), new Vec3(0, 0, 1));
        }
        float radius() { return LightShieldGeometry.radius(ticks); }
        float touchDamage() { return ArcGen.lerp(2, 6, exp); }
    }

    private LightShield() { }
    /** 止めた後の決まったクールダウン（熟練度で60 → 20tick。端数は四捨五入）。 */
    public static int cooldown(float exp) { return Math.round(ArcGen.lerp(COOLDOWN_AT_ZERO, COOLDOWN_AT_FULL, Math.max(0, Math.min(1, exp)))); }
    /**
     * 防ぐ攻撃か（近接・射撃などの攻撃と、爆発）。飛び道具（{@code is_projectile}）、爆発（{@code is_explosion}）、
     * 直接の原因がある攻撃（近接、術者の技能など。Re 本体の貫通の打撃もここでは範囲に入るが、{@link #absorb}がそのまま通す）。白翼刺突、無敵を貫くもの（奈落・{@code /kill}）、
     * 魔法・棘・衝撃波（{@code witch_resistant_to}）と衰弱は防がない。
     */
    public static boolean covers(DamageSource source) {
        if (SkillDamageSource.bypassesReDefenses(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypes.WITHER)) return false;
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION) || source.getDirectEntity() != null;
    }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    /**
     * 盾を続けてよいか。妨害（能力妨害器）も含む: 押し続け型だった1.0.0では、妨害されたクライアントが中断を送りハートビートも届かなくなって
     * 終わっていた。トグルではキーの経路に頼れないので、サーバーが毎tick確かめ、妨害が始まったtickに通常の終了（維持・音・盾を外し、
     * 鈍化とクールダウン）をする。
     */
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        // コンテナの画面を開いただけでは止めない。画面を開いている間の新しい開始は、start()が拒否する。
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive() && !data.isInterfering()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID);
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (!PassiveSkills.slotMatches(data, ID, slot)
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        // 1.5.2: 起動は無料（起動のオーバーロードは無い）。動いている間の基本の維持は0。開始の過負荷の確保は維持の枠で行う。
        var session = new Session(player, data, slot);
        session.upkeep = DefenseCost.startUpkeep(data, ID, () -> player.getAbilities().instabuild);
        if (session.upkeep == null) return "academy.cast.unlearned";
        ACTIVE.put(player.getUUID(), session);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        // 原作c_spawn（EntityMdShield）: 術者と追跡している各クライアントへ、盾面の状態を送る（SkillVisualに代えた）。
        sync(player, session, true);
        // 原作c_start: 起動音と、盾を押している間のループ音。
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_SHIELD_STARTUP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_SHIELD_LOOP.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, true, () -> holding(player));
        return "";
    }

    /** このスロットからオンにした盾が続いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        return session != null && session.slot == slot ? session.ticks : -1;
    }

    /**
     * このスロットの盾だけを終える（自身のキーの2度目の押下、またはクライアントの安全な停止）。別のスロットのキーではこれは止まらない。
     * すべてのセッションを終える処理は、引き続きstop(player)を呼ぶ。
     */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
        send(player, ended(player, session));
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        if (data == null || data.isReadOnly() || session.level != player.level()) return;
        // 1.5.2: 止めたときの鈍足の反動は無い。クールダウンは熟練度で60 → 20tickの決まった長さ。
        // 習得していない技能（止める前にリスナーやカテゴリの変更が外した）にはクールダウンを書かない。
        if (data.hasLearned(ID)) data.setCooldown(ID, cooldown(data.getProficiency(ID)));
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }
    public static int heldTicks(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.ticks;
    }
    /** この盾が最近接触攻撃した生き物の数（CONTACT_INTERVAL tick後に忘れる）。 */
    public static int remembered(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.lastContact.size();
    }

    /**
     * 原作は術者の前方60度の弧を、yawだけで測って覆う。ここでは、生き物でないもの（アイテム・経験値オーブ）の焼却だけに使う
     * （1.2.0の挙動を変えないため。±180度の境目の問題もそのまま）。生き物と防御は盾面で判定する。
     */
    private static boolean reachable(ServerPlayer player, Entity target) {
        double yaw = -Math.toDegrees(Math.atan2(target.getX() - player.getX(), target.getZ() - player.getZ()));
        return Math.abs(yaw - player.getYRot()) % 360 < ARC_DEGREES;
    }

    /** このtickもモードを続けてよいか（tickの始めと、熟練度のイベントの後に確かめる）。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        // 1.5.2: パッシブで動いているものは、プリセットとスロットを見ず、パッシブのままかを見る。
        return data != null && allowed(player, data) && data.upkeepHeld(session.upkeep) && !(session.level != player.level()
                || !PassiveSkills.sessionMatches(data, ID, session.preset, session.slot));
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        // 1.5.2: 維持はこのtickの始めに精算済み。外れていたら止める。維持の熟練度は無い（熟練度は防いだ・焼いた1回ごとに得る）。
        if (!continues(player, data, session)) { stop(player); return; }
        session.ticks++;
        // 盾の向き: モードが選んだ目標へ、1tickに最大の角度だけ回す（目標が無いAUTOは、最後の向きのまま）。
        Vec3 goal = LightShieldTargeting.aim(player, data.getLightShieldMode(), session.memory, session.ticks);
        if (goal != null) session.normal = LightShieldGeometry.turn(session.normal, goal, LightShieldGeometry.TURN_DEGREES);
        sync(player, session, false);
        Vec3 base = LightShieldGeometry.base(player.position());
        // CONTACT_INTERVAL tick経った記録を忘れる（離れた・消えた対象の記録も、ここで消える）。
        session.lastContact.values().removeIf(at -> session.ticks - at >= CONTACT_INTERVAL);
        var level = player.serverLevel();
        // 原作basicSelectorはEntitySelectors.everything(): ドロップしたアイテムや経験値オーブも焼く。
        for (var target : level.getEntities(player, player.getBoundingBox().inflate(REACH),
                e -> e.isAlive() && !e.isSpectator())) {
            // 生き物は、前の接触からCONTACT_INTERVAL tick（標準の被ダメージ後の待ちは見ない）。それ以外は以前どおりinvulnerableTime。
            boolean living = target instanceof LivingEntity;
            boolean touching = living && LightShieldGeometry.touches(base, session.normal, session.radius(), target.getBoundingBox().getCenter(), target.getBbWidth() / 2, REACH);
            // 押し留めは毎tick（接触攻撃の8tickとは別の時計、費用なし）。
            if (touching) hold(player, session, base, (LivingEntity) target);
            if (living ? session.lastContact.containsKey(target.getUUID()) : target.invulnerableTime > 0) continue;
            // 生き物は盾面に触れているか。それ以外は1.2.0どおり視線のyawの弧。
            if (living ? !touching : !reachable(player, target)) continue;
            // 1.5.2: 触れた相手への攻撃は、おまけの効果として1体ごとに別に払う（CP 30→15・過負荷2→0、回復待ちは作らない）。
            float exp = data.getProficiency(ID);
            if (!DefenseCost.payAddOn(data, ID, DefenseCost.lightShieldContactCp(exp), DefenseCost.lightShieldContactOverload(exp),
                    player.getAbilities().instabuild)) continue;
            if (ACTIVE.get(player.getUUID()) != session) return;
            if (living) {
                // 課金した攻撃の時刻を記録する（通らなくても、同じ対象へはCONTACT_INTERVAL tick空ける）。
                session.lastContact.put(target.getUUID(), session.ticks);
                MDDamageHelper.attackContact(player, data, target, ID, session.touchDamage());
            } else {
                MDDamageHelper.attack(player, data, target, ID, session.touchDamage());
            }
            data.addProficiency(ID, .001f);
        }
    }

    /** 押し留める相手か。耐性・PvP・保護は迂回しない。 */
    private static boolean holdable(ServerPlayer player, LivingEntity target) {
        if (target.isInvulnerable() || target.isSpectator() || target.isPassenger() || target.isVehicle()) return false;
        // NeoForgeのボスのタグは`c:bosses`（Forge版は`forge:bosses`）。どちらもエンダードラゴンとウィザーを含む。
        if (target.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)) return false;
        if (target instanceof net.minecraft.world.entity.player.Player other)
            return !other.isCreative() && !other.isSpectator() && player.canHarmPlayer(other);
        return true;
    }
    /** 術者へ近づく速度の成分を消し、盾の面より内側にいれば外への小さな補正を加える。 */
    private static void hold(ServerPlayer player, Session session, Vec3 base, LivingEntity target) {
        if (!holdable(player, target)) return;
        Vec3 normal = session.normal, v = target.getDeltaMovement();
        double inward = v.dot(normal);
        if (inward < 0) v = v.subtract(normal.scale(inward));
        double along = target.getBoundingBox().getCenter().subtract(base).dot(normal);
        double depth = LightShieldGeometry.DISTANCE + target.getBbWidth() / 2 - along;
        if (depth > 0) {
            double resist = target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            v = v.add(normal.scale(Math.min(SEPARATION, depth * .5) * Math.max(0, 1 - resist)));
        }
        if (!v.equals(target.getDeltaMovement())) {
            target.setDeltaMovement(v);
            target.hurtMarked = true;
            session.held++;
        }
    }

    /**
     * 術者に当たる直前の飛来物を、盾の円を通るなら防御として止める。
     * Forge版は結果をSTOP_AT_CURRENT_NO_DAMAGEにして飛来物を消す。NeoForge 1.21.1のこのイベントには結果の種類が無く、取り消すと
     * 衝突を処理しない（矢は貫通の繰り返しを抜ける）。取り消して消すことで、同じく「当たらずにその場で止まる」にする。
     */
    @SubscribeEvent public static void intercept(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) return;
        var projectile = event.getProjectile();
        if (projectile.getOwner() == player || event.isCanceled()) return;
        if (intercepts(player, projectile)) {
            event.setCanceled(true);
            projectile.discard();
        }
    }
    /** 止めるなら防御の値段を払ってtrue（防御と同じ値段。間隔は無い）。テストからも呼ぶ。 */
    public static boolean intercepts(ServerPlayer player, Entity projectile) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || data.isInterfering()) return false;
        // 盾自身のtickが止める前（プリセット・スロットの変更、能力のオフ）に届いた飛来物は止めない。
        if (!continues(player, data, session)) return false;
        if (!LightShieldGeometry.covers(LightShieldGeometry.base(player.position()), session.normal, session.radius(),
                projectile.getBoundingBox().getCenter(), Math.min(.6, projectile.getBbWidth() / 2))) return false;
        if (!DefenseCost.pay(data, ID, DefenseCost.Kind.LIGHT_SHIELD, data.getProficiency(ID), DefenseCost.priced(player, estimate(projectile)),
                player.getAbilities().instabuild)) return false;
        data.addProficiency(ID, .001f);
        session.intercepted++;
        return true;
    }
    /** 止める飛来物の、値段に使うダメージの見積もり: 矢はバニラと同じ「速さ × 基本ダメージ」の切り上げ、それ以外は0。 */
    public static float estimate(Entity projectile) {
        if (!(projectile instanceof AbstractArrow arrow)) return 0;
        double damage = Math.ceil(arrow.getDeltaMovement().length() * arrow.getBaseDamage());
        return (float) Math.max(0, Math.min(Integer.MAX_VALUE, Double.isFinite(damage) ? damage : 0));
    }
    /** テスト用: このセッションが押し留めた回数と、止めた飛来物の数。 */
    public static int heldCount(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? 0 : s.held; }
    public static int interceptedCount(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? 0 : s.intercepted; }

    /** テスト用の入口: サーバーのtickループを待たずにセッションを1tick進める。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /**
     * 盾が防いだ後に残るダメージを返す（1.5.2）。効く範囲（{@link #covers}）で、攻撃の元から術者への経路が盾の円を通り、
     * 防御の値段を払えたときだけ防ぐ。原子崩しの通常ビーム（{@link #NORMAL_BEAMS}）は0にし、それ以外は{@link #GENERAL_RATE}だけ軽減する。
     * Re 本体の{@code Style.PIERCE}の打撃は、そのまま通す（費用も無い）。直接の原因のエンティティが無い効く範囲の攻撃（原因の無い爆発など）は、
     * 位置を問わず一般の割合で軽減する（今のまま）。
     */
    public static float absorb(ServerPlayer player, DamageSource source, float damage) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        // 技能が外された（次のtickでモードが終わる前の被弾）なら何もしない。熟練度の加算は未習得を拒否する。
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || !(damage > 0)) return damage;
        // 妨害が始まってから盾自身のtickで終わるまでの間に届いた攻撃も、盾は防がない。維持が外れた盾も防がない。
        if (data.isInterfering() || !data.upkeepHeld(session.upkeep) || !covers(source)) return damage;
        // 1.5.2: Re 本体の貫通の打撃は、盾を完全に通る（盾だけの判定。ほかの防御は変えない）。
        if (piercesShield(source)) return damage;
        var attacker = source.getDirectEntity();
        // 攻撃の元から術者への経路が盾の円を通るときだけ（飛来物はその位置、生き物は当たり判定の中心、円は相手の半分の幅だけ広げる）。
        // 範囲外では何も払わない。
        if (attacker != null && !LightShieldGeometry.covers(LightShieldGeometry.base(player.position()), session.normal, session.radius(),
                attacker.getBoundingBox().getCenter(), Math.min(.6, attacker.getBbWidth() / 2))) return damage;
        // 1.5.2: 通常ビームは全部、それ以外は30%。回数の分 ＋ 防いだダメージ × 係数と過負荷を払えたら防ぐ。払えなければこの1回は防がない。
        float blocked = normalBeam(source) ? damage : damage * GENERAL_RATE;
        if (!DefenseCost.pay(data, ID, DefenseCost.Kind.LIGHT_SHIELD, data.getProficiency(ID), DefenseCost.priced(player, source, blocked),
                player.getAbilities().instabuild)) return damage;
        // 防げたときだけ（1.1.0）。
        data.addProficiency(ID, .001f);
        return Math.max(0, damage - blocked);
    }
    /** 盾が100%相殺する原子崩しの通常ビームか（Re 本体の技能の打撃で、技能の ID が{@link #NORMAL_BEAMS}）。 */
    public static boolean normalBeam(DamageSource source) {
        return source instanceof SkillDamageSource skill && NORMAL_BEAMS.contains(skill.skill());
    }
    /** 盾を完全に通る打撃か（Re 本体の技能の打撃で、当たり方が{@code Style.PIERCE}）。他の Mod の独自のタグは見ない。 */
    public static boolean piercesShield(DamageSource source) {
        return source instanceof SkillDamageSource && SkillDamageSource.isPierce(source);
    }

    @SubscribeEvent(receiveCanceled = true) public static void hurt(AcademyLivingHurtEvent event) { PierceHits.hurtStage(event, LightShield::hurtBody); }
    /** 盾の本体（1.5.2: 貫通の打撃では、他の Mod の取り消しの後も {@link PierceHits} を通して動く）。 */
    private static void hurtBody(AcademyLivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID())) return;
        // 白翼刺突（いつも貫通）は Re の防御も通る（1.5.2）。ほかの Re 本体の貫通の打撃は、absorb がそのまま通す。
        if (SkillDamageSource.bypassesReDefenses(event.getSource())) return;
        float left = absorb(player, event.getSource(), event.getAmount());
        event.setAmount(left);
        if (left == 0) event.setCanceled(true);
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        var session = ACTIVE.remove(p.getUUID());
        if (session == null) return;
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(p, ID, false);
        // 古い体（別のentityId）の盾を、各クライアントで消す。
        if (e.getOriginal() instanceof ServerPlayer old) send(p, ended(old, session));
    }
    /** 途中から術者を追跡し始めたクライアント（範囲に入った観測者、再接続）へ、今の盾を送る。 */
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer caster) || !(e.getEntity() instanceof ServerPlayer viewer)) return;
        var session = ACTIVE.get(caster.getUUID());
        if (session != null && viewer.connection != null) PacketDistributor.sendToPlayer(viewer, state(caster, session));
    }

    // ---- 盾の状態の通信 ----
    private static io.github.pinchan4273.reacademycraft.network.LightShieldState state(ServerPlayer player, Session session) {
        var data = data(player);
        var mode = data == null || data.isReadOnly() ? LightShieldMode.DEFAULT : data.getLightShieldMode();
        return new io.github.pinchan4273.reacademycraft.network.LightShieldState(session.level.dimension().location(), player.getId(), session.token,
                session.sequence, true, (float) session.normal.x, (float) session.normal.y, (float) session.normal.z, session.radius(), mode);
    }
    private static io.github.pinchan4273.reacademycraft.network.LightShieldState ended(ServerPlayer player, Session session) {
        return new io.github.pinchan4273.reacademycraft.network.LightShieldState(session.level.dimension().location(), player.getId(), session.token,
                ++session.sequence, false, 0, 0, 1, 0, LightShieldMode.DEFAULT);
    }
    /** 向きか大きさが変わった、再送の間隔が来た、またはforceのときに、今の状態を送る。 */
    private static void sync(ServerPlayer player, Session session, boolean force) {
        float radius = session.radius();
        boolean changed = session.sentNormal == null || LightShieldGeometry.degrees(session.sentNormal, session.normal) > .25
                || Math.abs(radius - session.sentRadius) > 1e-3;
        if (!force && !changed && session.ticks - session.sentTick < RESYNC_TICKS) return;
        session.sequence++;
        send(player, state(player, session));
        session.sentNormal = session.normal; session.sentRadius = radius; session.sentTick = session.ticks;
    }
    /** 術者自身と、術者を追跡している各クライアントへ（NeoForgeの追跡先への送信も、Forge版のTRACKING_ENTITYと同じく術者自身を含まない）。 */
    private static void send(ServerPlayer player, io.github.pinchan4273.reacademycraft.network.LightShieldState state) {
        if (player.connection != null) PacketDistributor.sendToPlayer(player, state);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel) PacketDistributor.sendToPlayersTrackingEntity(player, state);
    }
    /**
     * モードが変わった（{@link LightShieldSettings#set}）: 盾がONなら、古い標的と追従の状態を捨て、今の状態を送り直す。セッションは
     * そのまま続く（起動のやり直し・費用・クールダウンは無い）。次のtickから新しいモードで向きを選ぶ。
     */
    static void modeChanged(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        session.memory.clear();
        sync(player, session, true);
    }

    // ---- テスト用の入口 ----
    /** 盾面の法線。盾がONでなければnull。 */
    public static @javax.annotation.Nullable Vec3 normal(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? null : session.normal;
    }
    /** 盾の半径（判定と描画の大きさ）。盾がONでなければ0。 */
    public static float radius(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.radius();
    }
    /** 今の標的のUUID（無ければnull）と分類（{@link LightShieldTargeting}の定数）。 */
    public static @javax.annotation.Nullable UUID target(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? null : session.memory.target();
    }
    public static int targetKind(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? LightShieldTargeting.NONE : session.memory.kind();
    }
    /** 今送るはずの状態（送らない）。盾がONでなければnull。 */
    public static @javax.annotation.Nullable io.github.pinchan4273.reacademycraft.network.LightShieldState stateForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? null : state(player, session);
    }
    /** 盾の向きを直接置く（判定のテストで、向きを決めてから確かめるため）。追従の状態も捨てる。 */
    public static void pointForTest(ServerPlayer player, Vec3 normal) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) { session.normal = LightShieldGeometry.unit(normal, session.normal); session.memory.clear(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
