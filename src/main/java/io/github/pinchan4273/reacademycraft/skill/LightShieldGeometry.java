package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.world.phys.Vec3;

/**
 * Light Shieldの1枚の盾面の幾何（工程2）。サーバーの判定とクライアントの描画は、同じ値（法線・距離・半径）からここの式で盾を置く。
 * <ul>
 * <li>盾の基点は術者の足元から{@link #CHEST}上（胸の高さ。1.2.0の描画と同じ）。中心は基点から法線の向きへ{@link #DISTANCE}。</li>
 * <li>見た目は法線の周りに回る正方形（一辺{@link #SIZE}、1.2.0と同じ）なので、判定は回転によらない内接円（半径＝一辺の半分）にする。
 * 中心から見た半角は約42度（Phase 0のS-A）。</li>
 * <li>大きさは起動から{@link #GROW_TICKS}tickで0.2倍から1倍へ育つ（1.2.0の描画と同じ）。判定も同じ大きさで育てる。</li>
 * </ul>
 * 数値はPhase 0の比較の第一候補（R-B 12、W-B 18度/tick、S-A 1.8）で、正式な値ではない。
 */
public final class LightShieldGeometry {
    private LightShieldGeometry() { }
    /** 足元から盾の基点までの高さ。 */
    public static final double CHEST = 1.1;
    /** 基点から盾の中心までの距離。 */
    public static final double DISTANCE = 1.0;
    /** 盾の一辺（満の大きさ）。 */
    public static final float SIZE = 1.8f;
    /** 大きさが育つtick数と、そのときの初めの倍率。 */
    public static final int GROW_TICKS = 15;
    public static final float GROW_FROM = .2f;
    /** 1tickに回せる最大の角度（W-B）。 */
    public static final double TURN_DEGREES = 18;
    /** 標的を探す半径（R-B）。 */
    public static final double SEARCH_RADIUS = 12;

    /** 起動からticks経った盾の半径（判定にも描画にも使う）。 */
    public static float radius(int ticks) {
        float grown = GROW_FROM + (1 - GROW_FROM) * Math.min(1f, Math.max(0, ticks) / (float) GROW_TICKS);
        return SIZE * grown / 2;
    }
    /** 盾の基点（胸の高さ）。 */
    public static Vec3 base(Vec3 feet) { return feet.add(0, CHEST, 0); }
    /** 盾の中心。 */
    public static Vec3 center(Vec3 feet, Vec3 normal) { return base(feet).add(normal.scale(DISTANCE)); }

    /** 単位ベクトルにする。長さがほぼ0ならfallbackを返す。 */
    public static Vec3 unit(Vec3 v, Vec3 fallback) {
        double length = v.length();
        return length < 1e-6 ? fallback : v.scale(1 / length);
    }

    /**
     * fromからtoへ、最大maxDegrees度だけ回した単位ベクトル（球面補間）。ほぼ真逆のときは、回す軸を決めておく（真上・真下でも崩れない
     * 補助の軸と、fromの外積）ので、毎tick同じ側へ回り続ける。
     */
    public static Vec3 turn(Vec3 from, Vec3 to, double maxDegrees) {
        double cos = Math.max(-1, Math.min(1, from.dot(to)));
        double angle = Math.acos(cos), step = Math.toRadians(maxDegrees);
        if (angle <= step || angle < 1e-9) return to;
        Vec3 axisPart;
        if (Math.PI - angle < 1e-6) {
            // 真逆: fromに直交する向き（補助の軸×from）へ回す。補助の軸は、fromがほぼ真上・真下なら北、それ以外は真上
            // （水平な盾の反転は、真上を通らず水平に回る）。
            Vec3 helper = Math.abs(from.y) > .99 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            axisPart = unit(helper.cross(from), new Vec3(1, 0, 0));
        } else {
            axisPart = unit(to.subtract(from.scale(cos)), new Vec3(1, 0, 0));
        }
        return unit(from.scale(Math.cos(step)).add(axisPart.scale(Math.sin(step))), to);
    }

    /** 2つの単位ベクトルの間の角度（度）。 */
    public static double degrees(Vec3 a, Vec3 b) { return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.dot(b))))); }

    /**
     * 攻撃の元の位置pointから術者へ向かう攻撃を、盾が受けるか。pointが盾の面より前にあれば、pointと基点を結ぶ線が面を通る点が、
     * 円（半径radius + margin）の内にあるかで決める。面より手前（基点と面の間）にあれば、その位置の横のずれで決める。
     * 基点より後ろ（法線との内積が0以下）は受けない。marginは攻撃の元の当たり判定の半分の幅など、円を広げる量。
     */
    public static boolean covers(Vec3 base, Vec3 normal, double radius, Vec3 point, double margin) {
        Vec3 d = point.subtract(base);
        double along = d.dot(normal);
        if (along <= 1e-6) return false;
        Vec3 side = d.subtract(normal.scale(along));
        double scale = along > DISTANCE ? DISTANCE / along : 1;
        return side.length() * scale <= radius + margin;
    }

    /**
     * 生き物が盾に触れているか（接触攻撃の領域）: 盾の円（半径radius + 相手の半分の幅）を、基点から法線の向きへreachまで伸ばした
     * 円柱に、相手の中心が入るか。1.2.0の届く距離（3）を奥行きにする。
     */
    public static boolean touches(Vec3 base, Vec3 normal, double radius, Vec3 center, double halfWidth, double reach) {
        Vec3 d = center.subtract(base);
        double along = d.dot(normal);
        if (along < 0 || along > reach + halfWidth) return false;
        return d.subtract(normal.scale(along)).length() <= radius + halfWidth;
    }

    /** 法線から、描画に使うyawとピッチ（Minecraftの向きの決まり。getLookAngleの逆）。 */
    public static float yaw(Vec3 normal) { return (float) Math.toDegrees(Math.atan2(-normal.x, normal.z)); }
    public static float pitch(Vec3 normal) { return (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -normal.y)))); }
}
