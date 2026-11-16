package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Jet Engineの前方シールドの防御（前方限定）。
 * <p>
 * 推進の間、既存の菱形の演出が覆う前方から来る攻撃だけを、一定の割合で軽減する。全身・全方向の防御は無い。
 * <ul>
 * <li>前方: 演出と同じ菱形の角錐。頂点は胸の高さ（足元＋{@link #CHEST}）。視線の向き（上下を含む3次元）を前、向き（yaw）から取る
 *     水平の右を横、その2つに直交する軸を縦とし、攻撃の元への向きを(横, 縦, 前)に分けたとき、前 &gt; 0 かつ |横|＋|縦| ≦
 *     {@link #SPREAD}×前。菱形は胸から前へ1ブロックの所で上下左右に1.5なので、頂点の方向は atan(1.5) ≈ 56.3度、
 *     辺の中ほど（斜め45度）の方向は atan(1.5/√2) ≈ 46.7度。</li>
 * <li>攻撃の元: 直接の原因のEntityの当たり判定の中心。直接の原因が無い攻撃は、向きが分からないので軽減しない。</li>
 * <li>対象: 近接（{@code mob_attack}・{@code player_attack}・{@code mob_attack_no_aggro}）と、直接の原因が飛来物の攻撃。
 *     爆発、無敵を貫くもの、Academyの技能（他のプレイヤーの技能を含む）は軽減しない（許可リスト）。</li>
 * <li>割合: 熟練度で10%から20%。セッションの開始時の熟練度で決まる。</li>
 * <li>費用: 1.5.2から防御の部類の基準に入れ、軽減が働いた攻撃1回ごとに{@link DefenseCost.Kind#JET_FRONT}の値段
 *     （6→3 CP ＋ 軽減したダメージ × 2→1 CP、過負荷なし）を回復待ちを作らずに払う。払えなければその1回は軽減しない
 *     （1.5.1までは1回ごとの費用は無く、推進の維持コストの中で守っていた）。熟練度はセッションの開始時の値（割合と同じ）。</li>
 * <li>有効の判定: 被弾の時点で、推進のtickと同じ条件でセッションが続いていること（{@link JetEngine#defending}: 生存・能力・習得・
 *     スロット・プリセット・妨害・維持費の支払い）。さらに読み取り専用・妨害・過負荷のロックのときは軽減しない。</li>
 * <li>Light Shieldとの順: Light Shieldの吸収（NORMALの優先度）が先に固定量を食べ、その残りにこの割合を掛ける（LOWの優先度）。
 *     残りが0ならLight Shieldが事象を取り消すので、こちらは呼ばれない。こちらは量を書き換えるだけで、新しい被ダメージは起こさない。</li>
 * </ul>
 * 角度・割合は開始値で、バランスの調整で直しうる。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class JetFrontDefense {
    private JetFrontDefense() { }
    /** 足元から胸の高さ（菱形の演出と同じ）。 */
    public static final double CHEST = 1.1;
    /** 菱形の広がり: 胸から前へ1の所での、上下左右の頂点までの距離（演出のSCALE 1.5）。 */
    public static final double SPREAD = 1.5;
    /** 菱形の頂点の方向の半角（度）: atan(1.5) ≈ 56.3。 */
    public static final double VERTEX_DEGREES = Math.toDegrees(Math.atan(SPREAD));
    /** 菱形の辺の中ほど（斜め45度）の方向の半角（度）: atan(1.5/√2) ≈ 46.7。 */
    public static final double EDGE_DEGREES = Math.toDegrees(Math.atan(SPREAD / Math.sqrt(2)));

    /** 軽減の割合: 熟練度で10%から20%。 */
    public static float rate(float exp) { return ArcGen.lerp(.10f, .20f, exp); }

    /** 軽減の対象の攻撃か（許可リスト）。 */
    public static boolean covered(DamageSource source) {
        if (source.getDirectEntity() == null || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source instanceof SkillDamageSource) return false;
        return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.getDirectEntity() instanceof Projectile;
    }
    /** 視線の枠: 前（視線）、横（向きから取る水平の右）、縦（2つに直交）。どれも単位ベクトル。 */
    private static Vec3[] frame(ServerPlayer player) {
        Vec3 forward = player.getLookAngle().normalize();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 up = forward.cross(right).normalize();
        return new Vec3[] {forward, right, up};
    }
    /**
     * pointが術者の前方の菱形の角錐の中か。胸の中心から見た向きを単位ベクトルにしてから判定する（距離に依らず、同じ向きは同じ答え）。
     * 胸の中心そのもの（向きが無い）は前方としない。
     */
    public static boolean inFront(ServerPlayer player, Vec3 point) {
        Vec3 d = point.subtract(player.position().add(0, CHEST, 0));
        double length = d.length();
        if (!(length > 0) || !Double.isFinite(length)) return false;
        d = d.scale(1 / length);
        var axes = frame(player);
        double ahead = d.dot(axes[0]);
        return ahead > 0 && Math.abs(d.dot(axes[1])) + Math.abs(d.dot(axes[2])) <= SPREAD * ahead + 1e-9;
    }

    /** 軽減した後のダメージ。 */
    public static float mitigate(ServerPlayer player, DamageSource source, float amount) {
        if (amount <= 0 || !JetEngine.defending(player) || !covered(source)) return amount;
        if (!inFront(player, source.getDirectEntity().getBoundingBox().getCenter())) return amount;
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null || data.isReadOnly() || data.isInterfering() || data.isOverloadLocked()) return amount;
        float exp = JetEngine.sessionExp(player);
        float cut = amount * rate(exp);
        // 1.5.2: 軽減が働いた1回ごとに払う。払えなければこの1回は軽減しない。
        if (!DefenseCost.pay(data, JetEngine.ID, DefenseCost.Kind.JET_FRONT, exp, DefenseCost.priced(player, cut), player.getAbilities().instabuild)) return amount;
        return amount - cut;
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true) public static void frontHurt(AcademyLivingHurtEvent event) {
        PierceHits.hurtStage(event, JetFrontDefense::frontHurtBody);
    }
    /** 前面の軽減の本体（1.5.2: 貫通の打撃では、他の Mod の取り消しの後も {@link PierceHits} を通して動く）。 */
    private static void frontHurtBody(AcademyLivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        // 白翼刺突（いつも貫通）だけは Re の防御も通る（1.5.2。ほかの貫通の打撃は軽減する）。
        if (SkillDamageSource.bypassesReDefenses(event.getSource())) return;
        float left = mitigate(player, event.getSource(), event.getAmount());
        if (left != event.getAmount()) event.setAmount(left);
    }
}
