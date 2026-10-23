package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Electron Missileの自動射撃の相手の選び方（F9-02、2026-10-23の開発司令室9の訂正）: 攻撃対象のフィルターと、AUTO／SEMIの照準。
 * 発射の間隔・オーブ・費用・射程（lerp(5,13)の立方体）は{@link ElectronMissile}が持ち、ここは「この発射のtickに、どの相手を撃つか」だけを決める。
 * 撃ってよい相手がいなければnullで、撃たず、費用も払わず、オーブも使わない。
 * <p>
 * 分類（{@link #classify}。重なるときは上から順に決める）:
 * <ol>
 * <li>プレイヤー → {@link ElectronMissileTargetKind#PLAYER}。</li>
 * <li>味方 → {@link ElectronMissileTargetKind#FRIENDLY}: 術者の飼い動物、術者と同じチーム（isAlliedTo）のプレイヤーが飼い主の動物、術者と同じチームの生き物。
 *     敵対の種類でも、味方なら友好として扱う。</li>
 * <li>敵対 → {@link ElectronMissileTargetKind#HOSTILE}: 今まさに術者を標的にしているMob（怒った中立Mobを含む）。</li>
 * <li>中立 → {@link ElectronMissileTargetKind#NEUTRAL}: {@link NeutralMob}（狼・エンダーマン・アイアンゴーレム・ゾンビピグリン・ハチなど）。
 *     エンダーマンやゾンビピグリンは{@link Enemy}（Monster）でもあるが、怒っていなければ中立とする。</li>
 * <li>敵対 → {@link ElectronMissileTargetKind#HOSTILE}: それ以外の{@link Enemy}（ゾンビ・スケルトン・クリーパー・スライムなど）。</li>
 * <li>それ以外の生き物 → {@link ElectronMissileTargetKind#FRIENDLY}（動物・村人など）。</li>
 * </ol>
 * 防具立てと、生きていない・傍観者の相手は分類しない（撃たない）。プレイヤーは分類がONでも、{@link SkillCombat#mayAttack}（PvPの設定・
 * Academyのプレイヤー攻撃の設定・チームの同士討ち）が断れば撃たない。
 */
public final class ElectronMissileTargeting {
    /** SEMIの視線の円錐の半角（度）。目から相手の当たり判定の中心への向きと、視線のなす角。 */
    public static final double SEMI_CONE_DEGREES = 20;

    private ElectronMissileTargeting() { }

    /** 生き物の分類。撃つ対象にならないもの（防具立て、生き物でない、生きていない、傍観者）はnull。 */
    public static @Nullable ElectronMissileTargetKind classify(ServerPlayer caster, Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand || !e.isAlive() || e.isSpectator()) return null;
        if (e instanceof Player) return ElectronMissileTargetKind.PLAYER;
        if (ally(caster, e)) return ElectronMissileTargetKind.FRIENDLY;
        if (e instanceof Mob mob && mob.getTarget() == caster) return ElectronMissileTargetKind.HOSTILE;
        if (e instanceof NeutralMob) return ElectronMissileTargetKind.NEUTRAL;
        if (e instanceof Enemy) return ElectronMissileTargetKind.HOSTILE;
        return ElectronMissileTargetKind.FRIENDLY;
    }
    /** 味方か: 術者の飼い動物、術者と同じチームのプレイヤーの飼い動物、術者と同じチームの生き物。 */
    public static boolean ally(ServerPlayer caster, Entity e) {
        if (caster.isAlliedTo(e)) return true;
        if (e instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            if (caster.getUUID().equals(owned.getOwnerUUID())) return true;
            var owner = owned.getOwner();
            return owner != null && caster.isAlliedTo(owner);
        }
        return false;
    }
    /** 撃ってよい相手か: 分類がONで、ダメージを与えられる（{@link SkillCombat#mayAttack}）。術者自身は撃たない。 */
    public static boolean allowed(ServerPlayer caster, PlayerAbilityData data, Entity e) {
        if (e == caster) return false;
        var kind = classify(caster, e);
        return kind != null && data.electronMissileTargets(kind) && SkillCombat.mayAttack(caster, e, ElectronMissile.ID);
    }
    /** 視線と、目から相手の中心への向きのなす角（度）。 */
    public static double angle(ServerPlayer caster, Entity e) {
        Vec3 to = e.getBoundingBox().getCenter().subtract(caster.getEyePosition());
        double length = to.length();
        if (length < 1e-6) return 0;
        double cos = Math.max(-1, Math.min(1, to.scale(1 / length).dot(caster.getLookAngle().normalize())));
        return Math.toDegrees(Math.acos(cos));
    }

    /**
     * 撃つ相手を選ぶ。candidatesは射程の中の生き物（呼び出し元が集める）。撃ってよい相手が無ければnull。
     * AUTOは最も近いもの。SEMIは円錐の中で視線に近いもの（同じ角度なら近いもの）、円錐の中に無ければ最も近いもの。
     */
    public static @Nullable Entity choose(ServerPlayer caster, PlayerAbilityData data, List<Entity> candidates) {
        Entity nearest = null, aimed = null; double closest = Double.MAX_VALUE, bestAngle = Double.MAX_VALUE, aimedDistance = Double.MAX_VALUE;
        boolean semi = data.getElectronMissileAimMode() == ElectronMissileAimMode.SEMI;
        for (var e : candidates) {
            if (!allowed(caster, data, e)) continue;
            double distance = e.distanceToSqr(caster);
            if (distance < closest) { closest = distance; nearest = e; }
            if (!semi) continue;
            double angle = angle(caster, e);
            if (angle > SEMI_CONE_DEGREES) continue;
            if (angle < bestAngle - 1e-9 || Math.abs(angle - bestAngle) <= 1e-9 && distance < aimedDistance) {
                bestAngle = angle; aimedDistance = distance; aimed = e;
            }
        }
        return aimed != null ? aimed : nearest;
    }
}
