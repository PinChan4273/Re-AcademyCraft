package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.event.SkillReflectEvent;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 原作Railgun/RangedRayDamageのエンティティへの処理。原作が共有していた当たりのフラグは、1回の発射ごとの局所的な状態にしている。
 * エンティティへのダメージは地形より先で、壁を貫ける。
 *
 * RangedRayを通してMeltdownerと共有する。RangedRayが技能・光線の半径・反射の光線を渡す。4引数のperform()はRailgun専用で、変更していない。
 */
public final class RailgunAttack {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "railgun");
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private RailgunAttack() { }
    /**
     * 1回の発射の結果。reflectDirectionは、反射されたときに折り返した向き（長さ1。実際の当たりの向きで、描画もこの向きを使う）。
     * reflectOriginは、折り返した光線の当たり判定の始点（反射した者の目。描画もここから始める）。反射されなければどちらもnull。
     */
    public record Result(boolean hit, double terrainLimitSquared, Entity reflector, Entity reflectedTarget, Vec3 reflectDirection, Vec3 reflectOrigin) {
        public Result(boolean hit, double terrainLimitSquared, Entity reflector, Entity reflectedTarget) { this(hit, terrainLimitSquared, reflector, reflectedTarget, null, null); }
    }
    /** 反射の光線1本の結果（当たった物と、折り返した向きと、当たり判定の始点）。 */
    public record Reflection(Entity target, Vec3 direction, Vec3 origin) { }

    /** 反射の光線の描画の始点と終点: originから、directionへlengthだけ。 */
    public static Vec3[] reflectedBeam(Vec3 origin, Vec3 direction, double length) {
        return new Vec3[] { origin, origin.add(direction.normalize().scale(length)) };
    }
    /**
     * 1.5.2: Railgun・Meltdownerが描く反射の光線。実際の当たりと同じ始点（反射した者の目、
     * {@link Result#reflectOrigin}）から、同じ向きへlengthだけ。原作は主光線の上で反射した者までの距離の所から描いたので、主光線の中心から
     * 横にずれた反射者では、描いた光線と当たりの始点がずれた。始点が分からない結果だけ、原作の置き方（eyeからlookに沿って、反射した者の
     * 目までの距離の所）に戻る。
     */
    public static Vec3[] reflectedBeam(Vec3 eye, Vec3 look, Result attack, double length) {
        var reflector = attack.reflector();
        var away = attack.reflectDirection() != null ? attack.reflectDirection() : reflector.getLookAngle();
        var origin = attack.reflectOrigin() != null ? attack.reflectOrigin()
                : eye.add(look.normalize().scale(eye.distanceTo(reflector.getEyePosition())));
        return reflectedBeam(origin, away, length);
    }

    public static Result perform(ServerPlayer caster, Vec3 start, Vec3 look, float damage) {
        return perform(caster, RangedRay.RAILGUN, start, look, damage);
    }

    public static Result perform(ServerPlayer caster, RangedRay ray, Vec3 start, Vec3 look, float damage) {
        var direction = RailgunGeometry.direction(look);
        if (!Double.isFinite(start.lengthSqr()) || !Float.isFinite(damage) || damage < 0 || damage > 110)
            throw new IllegalArgumentException("Invalid Railgun attack");
        if (!caster.isAlive() || !ACTIVE.add(caster.getUUID()))
            return new Result(false, 0, null, null);
        try {
            var level = caster.serverLevel();
            var area = new AABB(start, start.add(direction.scale(RailgunGeometry.ENTITY_LENGTH))).inflate(ray.entityRadius());
            var targets = level.getEntities(caster, area, e -> e.isAlive() && !e.isSpectator()
                    && RailgunGeometry.containsTarget(start, direction, e.position(), ray.entityRadius()));
            targets.sort(Comparator.comparingDouble((Entity e) -> e.position().distanceToSqr(start)).thenComparingInt(Entity::getId));
            boolean hit = false;
            for (var target : targets) {
                if (!mayAttack(caster, target, ray)) continue;
                // 1.5.2: 反射の判定（ベクトル反射の無効の条件と値段）に、この対象へ与えるはずのダメージと、技能の反射の射程を渡す。
                // ダメージは、ふつうのダメージの補正のイベント（SkillAttack）を先に1回通した後の量（ほかの防御と同じく、
                // アドオンが補正した後の量で値段を決める）。反射されなければ、同じ量で当てる（補正のイベントを二度は通さない）。
                float factor = RailgunGeometry.damageFactor(start, direction, target.position());
                float adjusted = SkillCombat.adjust(caster, target, ray.skill(), damage * factor);
                var event = new SkillReflectEvent(caster, ray.skill(), target, adjusted, ray.reflectRange());
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                    var reflected = reflect(caster, target, ray, event);
                    return new Result(hit || reflected.target() != null, target.position().distanceToSqr(start), target, reflected.target(),
                            reflected.direction(), reflected.origin());
                }
                // リスナーが対象を取り除いたりテレポートさせたりすることがある。古くなった当たりを適用しない。
                if (target.isAlive() && target.level() == level && mayAttack(caster, target, ray)
                        && RailgunGeometry.containsTarget(start, direction, target.position(), ray.entityRadius())) {
                    // 動かされて横の減衰が変わったときだけ、その量で補正のイベントを通し直す（ふつうは、反射の判定に使った量のまま当てる）。
                    float now = RailgunGeometry.damageFactor(start, direction, target.position());
                    hit |= now == factor ? SkillCombat.attackAdjusted(caster, target, ray.skill(), adjusted)
                            : SkillCombat.attack(caster, target, ray.skill(), damage * now);
                }
            }
            return new Result(hit, Double.POSITIVE_INFINITY, null, null);
        } finally { ACTIVE.remove(caster.getUUID()); }
    }

    /**
     * 原作の反射は狙った1本の光線（Railgun: 15mで14ダメージ）で、球ではない。1.5.2: リスナーが反射した者と向きを決めたら
     * （ベクトル反射の反射方向）、その向きへ、反射した者のベクトル反射の攻撃として撃つ（射程は技能の反射のまま）。決めなければ
     * 原作どおり、反射した者の見る方向へ、元の術者の攻撃として撃つ。返す向きは、当たらなくても描画のために返す。
     * 1.5.2: 量は、リスナーが決めた量（ベクトル反射は、元の光線の量 × 反射した人の割合。
     * {@link VecReflection#beamDamage}）。量を決めなければ、技能の反射の量（{@link RangedRay#reflectDamage}）。
     */
    public static Reflection reflect(ServerPlayer caster, Entity reflector, RangedRay ray, SkillReflectEvent event) {
        var level = caster.serverLevel();
        var direction = event.direction() != null ? event.direction() : reflector.getLookAngle();
        // 当たり判定の始点は反射した者の目。描画もここから始める。
        var start = reflector.getEyePosition();
        if (!reflector.isAlive() || reflector.level() != level || !mayAttack(caster, reflector, ray)) return new Reflection(null, direction, start);
        var end = start.add(direction.scale(ray.reflectRange()));
        // clipは地形を問い合わせることがあるので、小さなchunkの範囲全体を先に確かめる。
        int minX = BlockPos.containing(Math.min(start.x, end.x) - 1, 0, 0).getX() >> 4;
        int maxX = BlockPos.containing(Math.max(start.x, end.x) + 1, 0, 0).getX() >> 4;
        int minZ = BlockPos.containing(0, 0, Math.min(start.z, end.z) - 1).getZ() >> 4;
        int maxZ = BlockPos.containing(0, 0, Math.max(start.z, end.z) + 1).getZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!level.hasChunkAt(new BlockPos(x << 4, 0, z << 4))) return new Reflection(null, direction, start);
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, reflector));
        double nearest = block.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        Entity selected = null;
        // 原作reflectServer: フィルター無しのRaytrace.traceLiving(reflector, 15)なので、生き物だけでなく衝突できるもの（ボート、トロッコ、額縁）にも当たる。
        for (var target : level.getEntities(reflector, new AABB(start, end).inflate(1),
                e -> e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e))) {
            var box = target.getBoundingBox().inflate(.3);
            var intercept = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                nearest = start.distanceToSqr(intercept.get()); selected = target;
            }
        }
        var attacker = event.reflectedBy() != null ? event.reflectedBy() : caster;
        var skill = event.reflectedBy() != null ? VecReflection.ID : ray.skill();
        // 1.5.2: 折り返した光線は、元の技能の当たり方（レールガン・主砲はいつも貫通）を引き継ぐ。攻撃者・技能は反射した人の
        // ベクトル反射のまま（帰属・死亡文・倍率）で、種類は既存の academy:vecmanip_pierce。量・射程・向きは今のまま。無敵の貫通は足さない。
        var style = SkillDamageSource.styleFor(ray.skill(), SkillDamageSource.Style.PLAIN);
        // 1.5.2: 貫通の段も元の技能の段を引き継ぐ（レールガン・原子崩しは段 3）。
        // 1.5.2: 量はリスナーが決めた量（ベクトル反射は元の光線の量 × 割合）。決めなければ技能の反射の量。
        float amount = event.reflectedDamage() >= 0 ? event.reflectedDamage() : ray.reflectDamage();
        boolean struck = selected != null && SkillCombat.mayAttack(attacker, selected, skill)
                && SkillCombat.attack(attacker, selected, skill, amount, style, SkillDamageSource.tierOf(ray.skill()));
        return new Reflection(struck ? selected : null, direction, start);
    }
    private static boolean mayAttack(ServerPlayer caster, Entity target, RangedRay ray) {
        return SkillCombat.mayAttack(caster, target, ray.skill());
    }
}
