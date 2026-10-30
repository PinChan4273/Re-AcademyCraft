package io.github.pinchan4273.reacademycraft.skill;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * LambdaLib2のRaytrace.rayTraceEntities: 当たりうる（1.12のcanBeCollidedWith、ここではisPickable）エンティティのうち、0.3広げた箱を
 * 線分が横切る最も近いもの。持ち上げたブロックはこれに当たらない: 原作のEntityBlockはcanBeCollidedWithを上書きしておらず（false）、
 * 移植版がpickableにしているのは取り戻せるようにするためだけ。
 */
final class ProjectileHitSeam {
    private ProjectileHitSeam() { }
    record Hit(Entity entity, Vec3 point) { }

    /** 技能の追跡に使う1.12のcanBeCollidedWith: pickableだが、持ち上げたブロックは除く。 */
    static boolean hittable(Entity e) {
        return e.isPickable() && !(e instanceof io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity);
    }

    /** 線分が{@code except}以外のそのようなエンティティに最初に出会う点。出会わなければnull。 */
    @Nullable static Vec3 firstEntity(Level level, @Nullable Entity except, Vec3 from, Vec3 to) {
        var hit = first(level, except, from, to);
        return hit == null ? null : hit.point();
    }
    @Nullable static Hit first(Level level, @Nullable Entity except, Vec3 from, Vec3 to) {
        Hit nearest = null; double best = Double.MAX_VALUE;
        for (var entity : level.getEntities(except, new AABB(from, to).inflate(1),
                e -> e.isAlive() && hittable(e))) {
            var box = entity.getBoundingBox().inflate(.3);
            var hit = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) { best = from.distanceToSqr(hit.get()); nearest = new Hit(entity, hit.get()); }
        }
        return nearest;
    }
}
