package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.5.2: 水中・溶岩の中の能力の飛行。白翼飛行・風の翼・ジェット・エンジンで同じ決まりにする。
 * <ul>
 * <li>水中・溶岩の中からも離陸でき、液体の中でも飛び続けられる。</li>
 * <li>速さは空中の速さ × 倍率（熟練度とともに速くなる）。倍率は液体の種類と、通常・高速の扱いで決まる（{@link #speedFactor}）。
 *     白翼飛行の通常の飛行は「通常」、高速飛行は「高速」。風の翼の推進は「高速」、ジェット・エンジンはいつも「高速」の扱い。</li>
 * <li>バニラの液体の抵抗（移動の後に、水は横 0.8 倍（走っていれば 0.9 倍）・縦 0.8 倍、溶岩は 0.5 倍）で、毎tick目標へ少しずつ近づける
 *     飛行が目標に届かなくなる。前のtickに実際に動いた速さを抵抗の前へ戻してから近づける（{@link #restore}）ことで、抵抗を打ち消す。</li>
 * <li>CP と過負荷は空中と同じ。溶岩のダメージと火は変えない。</li>
 * </ul>
 * 計算の部分（{@link #speedFactor}・{@link #drag}・{@link #restore}）は、テストで値を確かめられるよう、世界に依らない形にしてある。
 */
public final class FluidFlight {
    /** 飛んでいる場所。溶岩を先に見る（Forge の液体の種類は溶岩も含むため）。溶岩以外の液体は水と同じに扱う。 */
    public enum Medium { AIR, WATER, LAVA }
    /** 水中・溶岩の中の、空中の速さに掛ける倍率（通常・高速）。 */
    public static final double WATER_NORMAL = .75, WATER_FAST = .75, LAVA_NORMAL = .30, LAVA_FAST = .30;
    /** バニラの液体の中の移動の後の減衰（水の縦・溶岩・浅い溶岩の縦）と、プレイヤーの水中の既定の減衰。 */
    public static final double WATER_VERTICAL = .8, LAVA = .5, SHALLOW_LAVA_VERTICAL = .8, WATER_SLOW_DOWN = .8, WATER_SPRINT = .9;

    private FluidFlight() { }

    /** 液体の種類と通常・高速の扱いから、空中の速さに掛ける倍率。空中は1。 */
    public static double speedFactor(Medium medium, boolean fast) {
        return switch (medium) {
            case WATER -> fast ? WATER_FAST : WATER_NORMAL;
            case LAVA -> fast ? LAVA_FAST : LAVA_NORMAL;
            default -> 1;
        };
    }
    /** そのエンティティが今いる場所（溶岩・水などの液体・空中）。 */
    public static Medium of(LivingEntity entity) {
        if (entity.isInLava()) return Medium.LAVA;
        if (entity.isInWater() || entity.isInFluidType()) return Medium.WATER;
        return Medium.AIR;
    }
    /** そのエンティティの、今の速さの倍率（{@link #speedFactor}を今いる場所で）。 */
    public static double speedFactor(LivingEntity entity, boolean fast) { return speedFactor(of(entity), fast); }

    /**
     * バニラの水中の横の減衰（LivingEntity.travel と同じ: 走っていれば 0.9、そうでなければ 0.8。水中歩行のエンチャントと、
     * イルカの好意で変わる）。
     */
    public static double waterSlowDown(LivingEntity entity) {
        float slow = entity.isSprinting() ? (float) WATER_SPRINT : (float) WATER_SLOW_DOWN;
        // 1.21は水中の歩きの効率を属性で持つ（水中歩行のエンチャント1レベルで1/3。Forge 1.20.1版はレベルを3で割った値）。
        float strider = (float) Math.min(1, entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY));
        if (!entity.onGround()) strider *= .5f;
        if (strider > 0) slow += (.54600006f - slow) * strider;
        if (entity.hasEffect(MobEffects.DOLPHINS_GRACE)) slow = .96f;
        return slow;
    }
    /** そのエンティティの重力（ブロック/tick²）。重力が無ければ0。 */
    public static double gravity(LivingEntity entity) {
        return entity.isNoGravity() ? 0 : entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
    }
    /** 浅い溶岩か（バニラは、溶岩の高さが跳べる高さ以下なら、縦の減衰を 0.8 にする）。 */
    public static boolean shallowLava(LivingEntity entity) {
        return entity.getFluidHeight(FluidTags.LAVA) <= entity.getFluidJumpThreshold();
    }

    /**
     * バニラの液体の中の、移動の後の速さ（この計算はテストと打ち消しの確かめのため。おおよそバニラの LivingEntity.travel と同じ）。
     * 水: 横 × slow、縦 × 0.8 − 重力/16（走っているときは重力を引かない）。溶岩: × 0.5（浅ければ縦は × 0.8）、縦 − 重力/4。
     */
    public static Vec3 drag(Medium medium, double slow, boolean sprinting, boolean shallow, double gravity, Vec3 used) {
        return switch (medium) {
            case WATER -> new Vec3(used.x * slow, used.y * WATER_VERTICAL - (sprinting ? 0 : gravity / 16), used.z * slow);
            case LAVA -> new Vec3(used.x * LAVA, used.y * (shallow ? SHALLOW_LAVA_VERTICAL : LAVA) - gravity / 4, used.z * LAVA);
            default -> used;
        };
    }
    /**
     * 前のtickに実際に動いた速さ（{@link #drag}を戻した値）。液体の中の飛行は、この値から目標へ近づける（抵抗を打ち消す）。空中はそのまま返す
     * （空中の戻し方は、それぞれの飛行の今の作りのまま）。
     */
    public static Vec3 restore(Medium medium, double slow, boolean sprinting, boolean shallow, double gravity, Vec3 motion) {
        return switch (medium) {
            case WATER -> new Vec3(motion.x / slow, (motion.y + (sprinting ? 0 : gravity / 16)) / WATER_VERTICAL, motion.z / slow);
            case LAVA -> new Vec3(motion.x / LAVA, (motion.y + gravity / 4) / (shallow ? SHALLOW_LAVA_VERTICAL : LAVA), motion.z / LAVA);
            default -> motion;
        };
    }
    /** そのエンティティの今の速さを、前のtickに実際に動いた速さへ戻す（{@link #restore}をそのエンティティの今の状態で）。 */
    public static Vec3 restore(LivingEntity entity, Vec3 motion) {
        var medium = of(entity);
        if (medium == Medium.AIR) return motion;
        return restore(medium, waterSlowDown(entity), entity.isSprinting(), medium == Medium.LAVA && shallowLava(entity), gravity(entity), motion);
    }
}
