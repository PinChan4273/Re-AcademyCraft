package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;

/**
 * 原作RangedRayDamage（WeAthFolD）のうち、呼び出し元ごとの半分。
 *
 * 原作にはRailgunとMeltdownerの両方が作るRangedRayDamageクラスが1つあり、違いは渡すものだけだった。RailgunGeometry、RailgunAttack、
 * RailgunTerrainはその共有クラスの移植版（最初に必要とした技能の名前を付けている）で、このrecordが呼び出し元ごとに選ぶ部分を運ぶ:
 *
 * - skill: 誰の当たりか。ダメージ源、反射イベント、技能ごとの地形のスイッチに使う。
 * - radius: 原作のコンストラクタが"range"と呼ぶもので、光線の断面の半径。エンティティは、原作の範囲のフィルターと同じく、その1.2倍まで
 *   光線の内側と数える。
 * - reflectRange/reflectDamage: 対象が光線を反射したときに原作の反射のコールバックが行うこと。RailgunとMeltdownerは、それぞれ
 *   反射した者から自身の狙った光線を1本撃つ。1.5.2から、ベクトル反射が折り返す光線の量は
 *   reflectDamageを使わず、元の光線の量 × 反射した人の割合（{@link VecReflection#beamDamage}）。reflectDamageは、量を決めない反射
 *   （{@link io.github.pinchan4273.reacademycraft.event.SkillReflectEvent}を取り消すだけのリスナー）で使う。
 */
public record RangedRay(ResourceLocation skill, double radius, double reflectRange, float reflectDamage) {
    /** 原作Railgun: range 2のRangedRayDamage。反射は15mの光線1本で14。 */
    public static final RangedRay RAILGUN = new RangedRay(RailgunAttack.ID, 2, 15, 14);

    public RangedRay {
        if (skill == null || !Double.isFinite(radius) || radius <= 0 || radius > 4
                || !Double.isFinite(reflectRange) || reflectRange <= 0 || reflectRange > 20
                || !Float.isFinite(reflectDamage) || reflectDamage < 0 || reflectDamage > 110)
            throw new IllegalArgumentException("Invalid ranged ray");
    }

    /** 原作の範囲のフィルター: proj.length() < range * 1.2。 */
    public double entityRadius() { return radius * 1.2; }
}
