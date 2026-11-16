package io.github.pinchan4273.reacademycraft.event;

import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

/**
 * 原作ReflectEvent（WeAthFolD）のForge移植: 取り消しは反射の要求であり、通常のダメージの取り消しではない。
 * ダメージの拒否にはAcademyLivingHurtEvent（Forge 1.20.1版のLivingHurtEventと同じ段階）を使う。反射のパッシブ能力は、その能力が移植されたときにここを購読できる。
 * <p>
 * 1.5.2: 光線がこの対象へ与えるはずのダメージ（設定の倍率の前）と、その技能の反射の射程を持つ。取り消すリスナーは
 * {@link #setReflection}で、反射した者（反射の光線の攻撃者）と折り返す向きを決められる（ベクトル反射の反射方向）。決めなければ、
 * 原作どおり反射した者の見る方向へ、元の術者の攻撃として折り返す。
 * <p>
 * 1.5.2: 取り消すリスナーは{@link #setReflection(ServerPlayer, Vec3, float)}で、折り返す光線の量も決められる
 * （ベクトル反射は、{@link #damage()}に反射した人の割合を掛けた量）。量を決めなければ、技能の反射の量（{@code RangedRay.reflectDamage}）で当てる。
 */
public final class SkillReflectEvent extends Event implements ICancellableEvent {
    private final ServerPlayer caster;
    private final Identifier skill;
    private final Entity target;
    private final float damage;
    private final double reflectRange;
    private @Nullable ServerPlayer reflectedBy;
    private @Nullable Vec3 direction;
    private float reflectedDamage = -1;
    public SkillReflectEvent(ServerPlayer caster, Identifier skill, Entity target) { this(caster, skill, target, 0, 0); }
    /** damageは光線がこの対象へ与えるはずのダメージ（設定の倍率の前。分からなければ0）、reflectRangeはその技能の反射の射程。 */
    public SkillReflectEvent(ServerPlayer caster, Identifier skill, Entity target, float damage, double reflectRange) {
        this.caster = caster; this.skill = skill; this.target = target;
        this.damage = Float.isFinite(damage) && damage > 0 ? damage : 0;
        this.reflectRange = Double.isFinite(reflectRange) && reflectRange > 0 ? reflectRange : 0;
    }
    public ServerPlayer caster() { return caster; }
    public Identifier skill() { return skill; }
    public Entity target() { return target; }
    /** 光線がこの対象へ与えるはずのダメージ（設定の倍率の前）。分からなければ0。 */
    public float damage() { return damage; }
    /** その技能の反射の光線の射程。分からなければ0。 */
    public double reflectRange() { return reflectRange; }
    /** 反射の光線の攻撃者（決めなければnullで、元の術者の攻撃として折り返す）。 */
    public @Nullable ServerPlayer reflectedBy() { return reflectedBy; }
    /** 折り返す向き（長さ1）。決めなければnullで、反射した者の見る方向。 */
    public @Nullable Vec3 direction() { return direction; }
    /**
     * 折り返す光線の量（設定の倍率の前）。{@link #setReflection(ServerPlayer, Vec3, float)}で決めた値で、決めていなければ負の値
     * （そのときは技能の反射の量で当てる）。
     */
    public float reflectedDamage() { return reflectedDamage; }
    /**
     * 反射した者と、折り返す向きを決める（向きは長さ1にする。長さの無い向きや有限でない向きは受けない）。折り返す光線の量は決めない
     * （前に決めた量も消し、技能の反射の量で当てる）。
     */
    public void setReflection(ServerPlayer reflector, Vec3 direction) {
        double length = direction.length();
        if (!Double.isFinite(length) || length < 1e-9) throw new IllegalArgumentException("Invalid reflection direction: " + direction);
        this.reflectedBy = reflector; this.direction = direction.scale(1 / length); this.reflectedDamage = -1;
    }
    /** 1.5.2: 反射した者と折り返す向きに加えて、折り返す光線の量（設定の倍率の前。0以上の有限の値）を決める。 */
    public void setReflection(ServerPlayer reflector, Vec3 direction, float damage) {
        if (!Float.isFinite(damage) || damage < 0) throw new IllegalArgumentException("Invalid reflected damage: " + damage);
        setReflection(reflector, direction);
        this.reflectedDamage = damage;
    }
}
