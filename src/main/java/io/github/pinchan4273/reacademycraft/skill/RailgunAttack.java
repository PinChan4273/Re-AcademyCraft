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
import net.minecraftforge.common.MinecraftForge;

/**
 * 原作Railgun/RangedRayDamageのエンティティへの処理。原作が共有していた当たりの印は、1回の発射ごとの局所的な状態にしている。
 * エンティティへのダメージは地形より先で、壁を貫ける。
 *
 * RangedRayを通してMeltdownerと共有する。RangedRayが技能・光線の半径・反射の光線を渡す。4引数のperform()はRailgun専用で、変更していない。
 */
public final class RailgunAttack {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "railgun");
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private RailgunAttack() { }
    public record Result(boolean hit, double terrainLimitSquared, Entity reflector, Entity reflectedTarget) { }

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
                if (MinecraftForge.EVENT_BUS.post(new SkillReflectEvent(caster, ray.skill(), target))) {
                    var reflected = reflect(caster, target, ray);
                    return new Result(hit || reflected != null, target.position().distanceToSqr(start), target, reflected);
                }
                // リスナーが対象を取り除いたりテレポートさせたりすることがある。古くなった当たりを適用しない。
                if (target.isAlive() && target.level() == level && mayAttack(caster, target, ray)
                        && RailgunGeometry.containsTarget(start, direction, target.position(), ray.entityRadius()))
                    hit |= SkillCombat.attack(caster, target, ray.skill(), damage * RailgunGeometry.damageFactor(start, direction, target.position()));
            }
            return new Result(hit, Double.POSITIVE_INFINITY, null, null);
        } finally { ACTIVE.remove(caster.getUUID()); }
    }

    /** 原作の反射は狙った1本の光線（Railgun: 15mで14ダメージ）で、球ではない。 */
    private static Entity reflect(ServerPlayer caster, Entity reflector, RangedRay ray) {
        var level = caster.serverLevel();
        if (!reflector.isAlive() || reflector.level() != level || !mayAttack(caster, reflector, ray)) return null;
        var start = reflector.getEyePosition(); var end = start.add(reflector.getLookAngle().scale(ray.reflectRange()));
        // clipは地形を問い合わせることがあるので、小さなchunkの範囲全体を先に確かめる。
        int minX = BlockPos.containing(Math.min(start.x, end.x) - 1, 0, 0).getX() >> 4;
        int maxX = BlockPos.containing(Math.max(start.x, end.x) + 1, 0, 0).getX() >> 4;
        int minZ = BlockPos.containing(0, 0, Math.min(start.z, end.z) - 1).getZ() >> 4;
        int maxZ = BlockPos.containing(0, 0, Math.max(start.z, end.z) + 1).getZ() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!level.hasChunkAt(new BlockPos(x << 4, 0, z << 4))) return null;
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, reflector));
        double nearest = block.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        Entity selected = null;
        // 原作reflectServer: 選別無しのRaytrace.traceLiving(reflector, 15)なので、生き物だけでなく衝突できるもの（ボート、トロッコ、額縁）にも当たる。
        for (var target : level.getEntities(reflector, new AABB(start, end).inflate(1),
                e -> e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e))) {
            var box = target.getBoundingBox().inflate(.3);
            var intercept = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                nearest = start.distanceToSqr(intercept.get()); selected = target;
            }
        }
        return selected != null && mayAttack(caster, selected, ray)
                && SkillCombat.attack(caster, selected, ray.skill(), ray.reflectDamage()) ? selected : null;
    }
    private static boolean mayAttack(ServerPlayer caster, Entity target, RangedRay ray) {
        return SkillCombat.mayAttack(caster, target, ray.skill());
    }
}
