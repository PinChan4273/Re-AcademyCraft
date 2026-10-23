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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作LightShield（WeAthFolD）: meltdownerのもう1つのレベル2技能。
 *
 * 盾が術者の前にあり、前方60度の弧に入ったものを焼き、18tickごとに受ける攻撃1回の大部分を吸収する。
 * Scatter Bombと同じく、オーバーロードは開始時に1回払ってそこを下限にするので、続けている間は回復しない。
 *
 * <p>原作は押し続け型で、熟練度に応じて6〜9秒で切れた。1.0.0の後に承認した案B（docs/CP_RECOVERY_TOGGLE_SPEC_JA.md 9章）で、
 * 1回押すとON、もう一度押すとOFFのトグルにし、固定の時間制限を外した。キーを離しても切れない。続く間の費用は切り替え型技能の
 * 維持の経路（toggle_upkeep.light_shield、熟練度0%で9 CP/tick。原作の1tickあたりlerp(9, 4)の0%値）で払い、払えなくなると終わる。
 * 熟練度100%で単独・待機（防御も接触攻撃も無い）なら、通常の回復の半分が正味で残る。実際に防いだとき・焼いたときの費用は原作のまま残す。
 *
 * 終えると5秒の鈍化と、続けた長さに比例するクールダウン（熟練度最大で半分）がかかる。長さは原作の上限lerp(120, 180)tickで打ち切るので、
 * クールダウンは原作で起こりえた最大（0%で240tick、100%で180tick）を超えない。起動と吸収のオーバーロードは2026-10-16に変えた（下）。起動のCP（0）・吸収のCP・接触の費用・鈍化は原作のまま。
 *
 * 原作は吸収のコストを、盾が敵に触れたときは(overload, cp)、攻撃を吸収したときは(cp, overload)として渡しており、
 * 両方が意図どおりということはありえない。どちらかを選ばず、両方とも書かれたとおりに移植している。
 * 2026-10-16から、吸収のオーバーロードだけをlerp(10, 4)にした（統合指示書の100%の5を、ユーザーの確定で偶数の4へ。吸収のCPと、接触のCP・オーバーロードは原作のまま）。
 *
 * <p>接触攻撃（2026-10-16、統合指示書4章）: 生き物には、盾のセッションごと・対象ごとに、前の接触から{@link #CONTACT_INTERVAL}tickに
 * 最大1回。標準の被ダメージ後の待ちには従わない専用の型（{@link SkillDamageSource#CONTACT}）で当てる。以前は対象の
 * invulnerableTimeが0になるまで飛ばしていたので、生き物には実質20tickに1回だった。間隔は、課金して攻撃した時刻から測る
 * （攻撃が通らなかったとき、例えばPvPが無効・相手が無敵でも、8tickは空ける。毎tick課金しないため）。
 * 生き物でないもの（ドロップしたアイテム、経験値オーブなど）は以前どおり、そのinvulnerableTimeだけを見て、通常の型で焼く。
 * 防御吸収の間隔（{@link #ABSORB_INTERVAL}）は接触とは別で、変えていない。
 *
 * <p>共通の盾面と自動追従（工程2、2026-10-23の司令室8の指示）: 盾はサーバーが持つ1枚の有限の面（法線・距離・半径、
 * {@link LightShieldGeometry}）で、防御の判定とクライアントの描画（{@link io.github.pinchan4273.reacademycraft.network.LightShieldState}）は
 * 同じ値を使う。向きはプレイヤーの設定（{@link LightShieldMode}、AUTOかMANUAL）に従って{@link LightShieldTargeting}が選んだ目標へ、
 * 1tickに最大{@link LightShieldGeometry#TURN_DEGREES}度ずつ回る。起動したときの向きは視線。カメラは動かさない。盾は1枚で、全方向を同時には防がない。
 * <ul>
 * <li>防御吸収: 攻撃の元（直接の原因のEntity）から術者への経路が、盾の円を通るときだけ。直接の原因が無い攻撃は1.2.0どおり吸収する（P0-F3は未決）。</li>
 * <li>生き物への接触攻撃: 盾の円を、法線の向きへ{@link #REACH}まで伸ばした円柱に入った生き物。1.2.0の「視線のyawの前方±60度・半径3」から変えた。</li>
 * <li>生き物でないもの（アイテム・経験値オーブ）の焼却: 1.2.0のまま（視線のyawの前方±60度・半径3）。工程2では変えていない。</li>
 * </ul>
 * 費用（起動・維持・接触・吸収）、接触の8tick、吸収の18tick、クールダウン、鈍化は変えていない。
 *
 * <p>工程3・4（暫定の試作、2026-10-23の包括指示による。数値は正式な値ではない）:
 * <ul>
 * <li>押し留め（{@link #hold}）: 接触の円柱に入った生き物について、毎tick、術者へ近づく速度の成分を消す。盾の面より内側へ入り込んで
 *     いれば、外への小さな補正（1tickに最大{@link #SEPARATION}、ノックバック耐性の分だけ弱める）を加える。接触攻撃の8tickとは別の時計で、
 *     費用は維持の中に含める（追加の費用なし）。プレイヤーは術者が傷つけられる相手（{@code canHarmPlayer}）だけ。無敵・傍観者・
 *     クリエイティブ・ボス・乗り物に乗っているものは押さない。</li>
 * <li>飛来物（{@link #intercept}）: 術者に当たる直前の飛来物の位置が、盾の円を通る経路にあれば、防御吸収と同じ費用・同じ
 *     {@link #ABSORB_INTERVAL}tickの待ちで、当たりを止めて消す。待ちの間や払えないとき、円の外からのものは、これまでどおり当たる。</li>
 * <li>爆発: 工程2の吸収のまま（直接の原因があれば円で判定、無ければ従来どおり。P0-F3は未決）。</li>
 * </ul>
 * 費用の区別: 接触の判定と押し留めは無料（維持の中）、接触攻撃は対象ごと8tickに1回、防御（被弾の吸収・飛来物の止め）は18tickに1回。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class LightShield {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "light_shield");
    /** 原作ACTION_INTERVAL、届く距離、覆う前方の弧。 */
    private static final int ABSORB_INTERVAL = 18;
    /** 同じ生き物への接触攻撃の最小の間隔（tick。2026-10-16、統合指示書4章。旧案の1tickは撤回）。 */
    public static final int CONTACT_INTERVAL = 8;
    private static final double REACH = 3, ARC_DEGREES = 60;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    /** 盾の状態を送り直す間隔（tick）。向きや大きさが変わらなくても、この間隔で送る。 */
    private static final int RESYNC_TICKS = 10;
    /** 押し留めの、外への補正の上限（1tickあたりの速さ。工程3の暫定値）。 */
    public static final double SEPARATION = .1;
    private static long nextToken;

    private static final class Session {
        final Level level; final int preset, slot; final float exp, overloadFloor, maxTicks;
        int ticks; int lastAbsorb = -1;
        /** このセッションが最近接触攻撃した生き物と、そのときのticks。CONTACT_INTERVAL tick後に忘れる（セッションと共に消える）。 */
        final Map<UUID, Integer> lastContact = new HashMap<>();
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        /** 盾面の法線（単位ベクトル）。起動したときは視線。 */
        Vec3 normal;
        /** 追従の状態（標的と、選んだtick）。 */
        final LightShieldTargeting.Memory memory = new LightShieldTargeting.Memory();
        /** 状態の通信: セッションの識別、番号、最後に送った法線・半径・tick。 */
        final long token = ++nextToken;
        int sequence; Vec3 sentNormal; float sentRadius = -1; int sentTick;
        /** 工程3・4の数（テストと記録用）: 押し留めたtick数、止めた飛来物の数。 */
        int held, intercepted;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); overloadFloor = data.getOverload();
            maxTicks = ArcGen.lerp(120, 180, exp);
            normal = LightShieldGeometry.unit(player.getLookAngle(), new Vec3(0, 0, 1));
        }
        float radius() { return LightShieldGeometry.radius(ticks); }
        float absorbDamage() { return ArcGen.lerp(15, 50, exp); }
        float touchDamage() { return ArcGen.lerp(2, 6, exp); }
        /** 接触攻撃ではオーバーロード、防御吸収ではCPとして渡る（原作の引数の順。下のクラスの説明）。 */
        float absorbOverload() { return ArcGen.lerp(5, 3, exp); }
        /** 接触攻撃ではCPとして渡る。防御吸収のオーバーロードは2026-10-16からdefenseOverload。 */
        float absorbConsumption() { return ArcGen.lerp(50, 30, exp); }
        /**
         * 防御吸収が成立したときのオーバーロード（2026-10-16のユーザーの確定でlerp(10, 4)。統合指示書はlerp(10, 5)。原作では吸収のconsumeに
         * absorbConsumptionのlerp(50, 30)が渡っていた）。補間の途中は丸めない。
         */
        float defenseOverload() { return ArcGen.lerp(10, 4, exp); }
        /** 続けた長さに比例するクールダウン。長さは原作の時間制限で打ち切る（有限で、保存できる範囲に収まる）。 */
        int cooldown() { float held = Math.min(ticks, maxTicks); return (int) ArcGen.lerp(2 * held, held, exp); }
    }

    private LightShield() { }
    /** 起動のオーバーロード（2026-10-16、統合指示書3章。原作lerp(110, 60)から）。 */
    public static float startOverload(float exp) { return ArcGen.lerp(40, 20, exp); }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    /**
     * 盾を続けてよいか。妨害（能力妨害器）も含む: 押し続け型だった1.0.0では、妨害されたクライアントが中断を送り心拍も届かなくなって
     * 終わっていた。トグルではキーの経路に頼れないので、サーバーが毎tick確かめ、妨害が始まったtickに通常の終了（維持・音・盾を外し、
     * 鈍化とクールダウン）をする。
     */
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        // TOGGLE-GUI-001: コンテナの画面を開いただけでは止めない。画面を開いている間の新しい開始は、start()が拒否する。
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive() && !data.isInterfering()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID);
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
        if (!data.consume(ID, 0, startOverload(data.getProficiency(ID)), player.getAbilities().instabuild))
            return "academy.cast.cp";
        // 起動のオーバーロードで術者がオーバーロードした: そのイベントが全セッションを止めた後なので、何も始めない。
        if (data.isOverloadLocked()) return "academy.cast.overload";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        // 原作c_spawn（EntityMdShield）: 術者と追跡している各クライアントへ、盾面の状態を送る（工程2からSkillVisualに代えた）。
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
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        // 習得していない技能（止める前にリスナーやカテゴリの変更が外した）にはクールダウンを書かない（M-110-02）。
        if (data.hasLearned(ID)) data.setCooldown(ID, session.cooldown());
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
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
     * 原作は術者の前方60度の弧を、yawだけで測って覆う。工程2からは、生き物でないもの（アイテム・経験値オーブ）の焼却だけに使う
     * （1.2.0の挙動を変えないため。±180度の境目の問題P0-F2もそのまま）。生き物と防御は盾面で判定する。
     */
    private static boolean reachable(ServerPlayer player, Entity target) {
        double yaw = -Math.toDegrees(Math.atan2(target.getX() - player.getX(), target.getZ() - player.getZ()));
        return Math.abs(yaw - player.getYRot()) % 360 < ARC_DEGREES;
    }

    /** このtickもモードを続けてよいか（tickの始めと、熟練度のイベントの後に確かめる）。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        return data != null && allowed(player, data) && !(session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot)));
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        // 続ける費用は、このtickの始めに精算した維持コスト。払えなかった要求は外れている。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        // 維持の熟練度（1.1.0、T178-10）。1.0.0の毎tick 1e-6はこれへ統合した。
        data.upkeepEarn(session.upkeep);
        // 熟練度のイベントのリスナーが、モードを終えた・置き換えた・続けられなくした（能力、スロット、プリセット）かもしれない:
        // このtickを古いセッションで続けない（M-110-01）。
        if (ACTIVE.get(player.getUUID()) != session) return;
        if (!continues(player, data, session) || !data.upkeepHeld(session.upkeep)) { stop(player); return; }
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
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
            // 工程3: 押し留めは毎tick（接触攻撃の8tickとは別の時計、費用なし）。
            if (touching) hold(player, session, base, (LivingEntity) target);
            if (living ? session.lastContact.containsKey(target.getUUID()) : target.invulnerableTime > 0) continue;
            // 生き物は盾面に触れているか（工程2）。それ以外は1.2.0どおり視線のyawの弧。
            if (living ? !touching : !reachable(player, target)) continue;
            // 原作はここで接触を(overload, cp)として課す。
            if (!data.consume(ID, session.absorbConsumption(), session.absorbOverload(), player.getAbilities().instabuild)) continue;
            // 接触攻撃のtickは待機ではない。
            data.upkeepActed(session.upkeep);
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

    /** 押し留める相手か（工程3）。耐性・PvP・保護は迂回しない。 */
    private static boolean holdable(ServerPlayer player, LivingEntity target) {
        if (target.isInvulnerable() || target.isSpectator() || target.isPassenger() || target.isVehicle()) return false;
        if (target.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)) return false;
        if (target instanceof net.minecraft.world.entity.player.Player other)
            return !other.isCreative() && !other.isSpectator() && player.canHarmPlayer(other);
        return true;
    }
    /** 工程3: 術者へ近づく速度の成分を消し、盾の面より内側にいれば外への小さな補正を加える。 */
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

    /** 工程4: 術者に当たる直前の飛来物を、盾の円を通るなら防御として止める。 */
    @SubscribeEvent public static void intercept(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) return;
        var projectile = event.getProjectile();
        if (projectile.getOwner() == player || event.getImpactResult() != net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.DEFAULT) return;
        if (intercepts(player, projectile)) {
            event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
            projectile.discard();
        }
    }
    /** 止めるなら費用を払ってtrue（吸収と同じ費用・同じ待ち）。テストからも呼ぶ。 */
    public static boolean intercepts(ServerPlayer player, Entity projectile) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || data.isInterfering()) return false;
        // WORK-124: 盾自身のtickが止める前（プリセット・スロットの変更、能力のオフ、維持費が払えなかった）に届いた飛来物は止めない。
        if (!continues(player, data, session) || !data.upkeepHeld(session.upkeep)) return false;
        if (session.lastAbsorb != -1 && session.ticks - session.lastAbsorb <= ABSORB_INTERVAL) return false;
        if (!LightShieldGeometry.covers(LightShieldGeometry.base(player.position()), session.normal, session.radius(),
                projectile.getBoundingBox().getCenter(), Math.min(.6, projectile.getBbWidth() / 2))) return false;
        if (!data.consume(ID, session.absorbOverload(), session.defenseOverload(), player.getAbilities().instabuild)) return false;
        session.lastAbsorb = session.ticks;
        data.upkeepActed(session.upkeep);
        data.addProficiency(ID, .001f);
        session.intercepted++;
        return true;
    }
    /** テスト用: このセッションが押し留めた回数と、止めた飛来物の数。 */
    public static int heldCount(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? 0 : s.held; }
    public static int interceptedCount(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? 0 : s.intercepted; }

    /** テスト用の入口: サーバーのtickループを待たずにセッションを1tick進める。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /** 盾が自身の分を食べた後に残るダメージを返す。 */
    public static float absorb(ServerPlayer player, DamageSource source, float damage) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        // 技能が外された（次のtickでモードが終わる前の被弾）なら何もしない。熟練度の加算は未習得を拒否する（M-110-02）。
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || damage == 0) return damage;
        // 妨害が始まってから盾自身のtickで終わるまでの間に届いた攻撃も、盾は食べない。
        if (data.isInterfering()) return damage;
        if (session.lastAbsorb != -1 && session.ticks - session.lastAbsorb <= ABSORB_INTERVAL) return damage;
        var attacker = source.getDirectEntity();
        // 範囲外では吸収を飛ばすだけ（間隔もやり直さない）。原作handleAttackedは範囲外でも.001を得たが、1.1.0（T178-10）は効果を
        // 出していない被弾に熟練度を与えない。
        // 工程2: 攻撃の元から術者への経路が盾の円を通るときだけ（飛来物はその位置、生き物は当たり判定の中心、円は相手の半分の幅だけ広げる）。
        if (attacker != null && !LightShieldGeometry.covers(LightShieldGeometry.base(player.position()), session.normal, session.radius(),
                attacker.getBoundingBox().getCenter(), Math.min(.6, attacker.getBbWidth() / 2))) return damage;
        session.lastAbsorb = session.ticks;
        float result = damage;
        // 原作は吸収を、上の接触とは逆に(cp, overload)として課す。
        // CPは原作どおりabsorbOverloadのlerp(5, 3)、オーバーロードは2026-10-16からdefenseOverloadのlerp(10, 4)。
        if (data.consume(ID, session.absorbOverload(), session.defenseOverload(), player.getAbilities().instabuild)) {
            result -= Math.min(damage, session.absorbDamage());
            // 防御吸収のtickは待機ではない。
            data.upkeepActed(session.upkeep);
            // 吸収できたときだけ（1.1.0、T178-10）。払えず吸収しなかった被弾には与えない（原作は払えなくても.001を得た）。
            data.addProficiency(ID, .001f);
        }
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
        if (session != null && viewer.connection != null)
            io.github.pinchan4273.reacademycraft.network.AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), state(caster, session));
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
    /** 術者自身と、術者を追跡している各クライアントへ。 */
    private static void send(ServerPlayer player, io.github.pinchan4273.reacademycraft.network.LightShieldState state) {
        var channel = io.github.pinchan4273.reacademycraft.network.AcademyNetwork.CHANNEL;
        if (player.connection != null) channel.send(PacketDistributor.PLAYER.with(() -> player), state);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel) channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), state);
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
    /** 盾の向きを直接置く（判定の試験で、向きを決めてから確かめるため）。追従の状態も捨てる。 */
    public static void pointForTest(ServerPlayer player, Vec3 normal) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) { session.normal = LightShieldGeometry.unit(normal, session.normal); session.memory.clear(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
