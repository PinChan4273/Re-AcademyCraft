package io.github.pinchan4273.reacademycraft.client;

import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * 刺突でしなる翼の中心の線（2026-11-05のユーザーの要望「元の直線的な翼のままの刺突を緩やかにしならせる」と、同じ日に決めた「しなりを相手までの距離で変える」）。
 * <p>
 * 線は根元Pから出だしの向きe1で出て、e1とe2の面の上で曲がる。向きの角φは、根元から曲がりの長さLbまでは θ·g(s/Lb)、その先はθのまま
 * （まっすぐ相手へ向かう）。g は曲率が κ ∝ 1 - C·u の形で、C（曲がり方の配分）が0なら真円の弧、大きいほど根元の側に曲がりが集まる。
 * Lb = θ·平均の半径。曲がる側wとθと半径とCを決めると、届いた点へ必ず着く出だしの向きが閉じた式で決まる。
 * <p>
 * 形は相手までの距離で連続に変える（{@link #design}）: 近いほど真横へ出て真円に近い小さな弧で体の横を包むように回り、遠いほど外かつ前へ出て、
 * 根元の近くでゆるく曲がってから長くまっすぐ伸びる。曲がる側はいつも翼と同じ側（外）で、頭の上を越えて反対側へ回ることはない。
 * <p>
 * Minecraftの部品に依らない（jomlだけを使う）ので、掃引の道具からも同じ物を動かせる。位置と長さは翼の取付の空間（モデルの単位。+xが翼の外側）。
 */
final class WingBend {
    /** 距離の境目（ブロック、根元から届いた点まで）: これ以下は一番近い形、これ以上は一番遠い形（射程の上限16ブロック）。間は連続に移す。 */
    static final float NEAR = 2, FAR = 16;
    /** 曲がりの平均の半径（モデルの単位）: 近い端・遠い端。近い所では、根元から届いた点までの距離に対する比RADIUS_SHAREまで（真円の弧が相手に届く大きさ）。 */
    static final float RADIUS_NEAR = 16, RADIUS_FAR = 80, RADIUS_SHARE = .46f;
    /** 曲がり方の配分C（κ ∝ 1 - C·u）: 近い端は真円、遠い端は根元に集める。 */
    static final float CURL_NEAR = 0, CURL_FAR = .75f;
    /** 根元の向き: 真横（外）から前へ回す角（度）。近い端は真横、遠い端は外かつ前（体に当たらない分だけ外へ出す）。 */
    static final float FORWARD_NEAR = 0, FORWARD_FAR = 50;
    /** 出だしの向きと届いた点の水平の向きの角の下限と上限（度）。下限: 同じ側の斜め前でも、いつも少ししなる。上限: 反対側の的でも曲がりが180度に収まる。 */
    static final float OPEN_MIN = 18, OPEN_MAX = 150;
    /** 曲がりの角の上限（度）。 */
    static final float THETA_LIMIT = 240;
    /** 曲がりの中を区切る数。 */
    static final int NT = 48;

    final Vector3f from = new Vector3f(), e1 = new Vector3f(), e2 = new Vector3f();
    /** 曲がりの角（度）、曲がりの長さ、根元から届いた点までの長さ、曲がり方の配分、出だしの向きと届いた点の向きの角（度）。 */
    float theta, bend, length, curl, open;
    private final float[] rowX = new float[NT + 1], rowY = new float[NT + 1];

    /** 曲がりの中の向きの角の割合（u: 0〜1、c: 曲がり方の配分）。 */
    static double g(double u, double c) { return (u - c * u * u / 2) / (1 - c / 2); }

    /**
     * 根元p、届いた点の向きu（単位）、曲がる側w（uに直角な単位ベクトル。出だしはuからwの側へ開き、そこからuの側へ曲がって戻る）、
     * 曲がりの角θ（度）、根元から届いた点までの距離distance、平均の半径radius、曲がり方の配分curlから線を決める。届かない組み合わせならfalse。
     */
    boolean set(Vector3f p, Vector3f u, Vector3f w, float thetaDeg, float distance, float radius, float curl) {
        float th = Math.max(0, Math.min(thetaDeg, THETA_LIMIT)), rad = (float) Math.toRadians(th);
        float lb = Math.max(1, rad * radius);
        // 長さ1の曲がりを、区切りごとにシンプソンの式で足す（x: e1の向き、y: e2の向き）。
        double x = 0, y = 0, du = 1.0 / NT, c0 = 1, s0 = 0;
        rowX[0] = 0; rowY[0] = 0;
        for (int k = 1; k <= NT; k++) {
            double am = rad * g((k - .5) * du, curl), a1 = rad * g(k * du, curl), c1 = Math.cos(a1), s1 = Math.sin(a1);
            x += (c0 + 4 * Math.cos(am) + c1) / 6 * du; y += (s0 + 4 * Math.sin(am) + s1) / 6 * du;
            rowX[k] = (float) x; rowY[k] = (float) y;
            c0 = c1; s0 = s1;
        }
        // 曲がりの終わりBと、そこからのまっすぐな向きh。届いた点はhの線の上（Bより先）にある。
        float bx = rowX[NT] * lb, by = rowY[NT] * lb, hx = (float) Math.cos(rad), hy = (float) Math.sin(rad);
        float tb = bx * hx + by * hy, fx = bx - tb * hx, fy = by - tb * hy, p2 = fx * fx + fy * fy;
        if (distance * distance < p2) return false;
        float t = (float) Math.sqrt(distance * distance - p2);
        if (t < tb) return false;
        double a = Math.atan2(fy + t * hy, fx + t * hx);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        from.set(p);
        e1.set(u).mul(ca).fma(sa, w);
        e2.set(u).mul(sa).fma(-ca, w);
        theta = th; bend = lb; length = lb + t - tb; this.curl = curl; open = (float) Math.toDegrees(a);
        return true;
    }
    /** 根元から線に沿ってsだけ進んだ所。届いた点より先も、最後の向きのまままっすぐ続く。 */
    Vector3f at(float s, Vector3f out) {
        float x, y;
        if (s >= bend) {
            float over = s - bend, rad = (float) Math.toRadians(theta);
            x = rowX[NT] * bend + over * (float) Math.cos(rad); y = rowY[NT] * bend + over * (float) Math.sin(rad);
        } else {
            float k = Math.max(0, s / bend) * NT;
            int k0 = Math.min((int) k, NT - 1); float f = k - k0;
            x = (rowX[k0] * (1 - f) + rowX[k0 + 1] * f) * bend; y = (rowY[k0] * (1 - f) + rowY[k0 + 1] * f) * bend;
        }
        return out.set(from).fma(x, e1).fma(y, e2);
    }
    /** sでの向きの角（ラジアン）。 */
    float phi(float s) { return (float) (Math.toRadians(theta) * g(Math.max(0, Math.min(1, s / bend)), curl)); }

    /** 根元で羽の面を背中に平行にしておく長さと、そこから曲がりの面へねじる長さ。 */
    static final float TWIST_FROM = 4, TWIST = 16;
    /**
     * sでの羽の向き（列: x=線の向き、y=扇の広がる向き、z=面の法線）。羽は曲がりの面に寝かせ、扇（羽の角の正の側）は曲がりの外へ広がる。
     * 根元では面を背中に平行にして幅が体へ向かないようにし、{@link #TWIST_FROM}から{@link #TWIST}の長さで曲がりの面へねじる。backは背中の向き。
     */
    Matrix3f frame(float s, Vector3f back, Matrix3f out) {
        float ph = phi(s), c = (float) Math.cos(ph), sn = (float) Math.sin(ph);
        var x = new Vector3f(e1).mul(c).fma(sn, e2);
        var y = new Vector3f(e1).mul(sn).fma(-c, e2);
        var z = new Vector3f(x).cross(y);
        var zb = new Vector3f(x).mul(-back.dot(x)).add(back);
        float l = zb.length();
        if (l > .2f) {
            zb.div(l);
            var yb = new Vector3f(zb).cross(x);
            if (yb.dot(y) < 0) yb.negate();
            double psi = Math.atan2(yb.dot(z), yb.dot(y)) * (1 - smooth(Math.max(0, Math.min(1, (s - TWIST_FROM) / TWIST))));
            float cp = (float) Math.cos(psi), sp = (float) Math.sin(psi);
            var y2 = new Vector3f(y).mul(cp).fma(sp, z);
            z.mul(cp).fma(-sp, y);
            y.set(y2);
        }
        return out.set(x, y, z);
    }
    /**
     * 羽1枚の置き方: 付け根を線の上のs、羽の長さlengthの先の線の点（弦）へ向けた羽の向き（列: x, y, z）を outへ。付け根の位置をatへ。
     * 曲がる所では、付け根の向きのままだと長い羽が曲がりの外へまっすぐ突き出すので、弦へ向ける。
     */
    Matrix3f feather(float s, float length, Vector3f back, Vector3f at, Matrix3f out) {
        frame(s, back, out);
        at(s, at);
        var ahead = at(s + length, new Vector3f()).sub(at);
        if (ahead.lengthSquared() > 1e-6f) {
            ahead.normalize();
            var x = out.getColumn(0, new Vector3f());
            var turn = new org.joml.Quaternionf().rotationTo(x, ahead);
            out.rotateLocal(turn);
        }
        return out;
    }
    static float smooth(float k) { return k * k * (3 - 2 * k); }

    // ---- 距離で形を決める ----

    /** 決めた形: 曲がる側（取付の空間、届いた点の向きに直角）、曲がりの角（度）、平均の半径、曲がり方の配分。 */
    static final class Shape {
        final Vector3f side = new Vector3f();
        float theta, radius, curl, gamma;  // gamma: 根元の向き（真横から前へ、度）
    }
    /** θを探す刻み（度）と、細かく詰める回数、届かないときに半径を縮める割合と回数。 */
    private static final float SCAN_STEP = 3, SHRINK = .8f;
    private static final int REFINE = 12, SHRINK_TRIES = 5;

    /** 根元から届いた点までの距離（モデルの単位）の、近い形（0）から遠い形（1）への割合。 */
    static float reachShare(float distance) {
        return Math.max(0, Math.min(1, (distance / 16 - NEAR) / (FAR - NEAR)));
    }

    /**
     * 根元pから届いた点targetまでの形を、距離で決める。根元の向きは真横（外、+x）から前へ{@link #FORWARD_NEAR}〜{@link #FORWARD_FAR}度だけ
     * 水平に回した物（ただし届いた点の水平の向きとの開きは{@link #OPEN_MIN}〜{@link #OPEN_MAX}度）で、上の的では少し下へ向ける。曲がる面は
     * 根元の向きと届いた点の向きを含む面で、いつも翼と同じ側から回る（上下の的では面が傾くが、頭の上を越えて反対側へは回らない）。
     * 半径と曲がり方の配分も距離で移し、その根元の向きになる曲がりの角θを解く。
     * <p>
     * 体に当たらない分だけ外へ出す: その形で中心の線と根元・中節の羽（probes）が体（{@link #DEEP}）へ入るなら、根元の向きを入らなくなる所まで外へ戻す
     * （連続に動くよう二分法で詰める。外へ戻しても入るなら、刻んだ中で一番浅い所）。rightは右の翼か（取付の空間の前の向きが左右で逆）。
     * naturalは伸ばさない翼の長さ、maxStretchは引き伸ばしてよい倍率、width・rootWidthは中節から先と根元の節の幅の倍率（描くときと同じ）。
     * 届く組がなければfalse。
     */
    static boolean design(Vector3f p, Vector3f target, boolean right, java.util.List<Probe> probes, float natural, float maxStretch, float width,
                          float rootWidth, Shape out) {
        var u = new Vector3f(target).sub(p);
        float distance = u.length();
        if (distance < 1e-2f) return false;
        u.div(distance);
        float k = reachShare(distance);
        // 取付の空間の前の向き（左は-z、右は+z。右は親がY軸で180度回っている）。届いた点の水平の向き（真横から前へ測る角、度）。
        float forward = right ? 1 : -1, ahead = u.z * forward;
        float azimuth = u.x * u.x + ahead * ahead < 1e-4f ? 90 : (float) Math.toDegrees(Math.atan2(ahead, u.x));
        // 反対側の的では、真横から出ると曲がりの角の上限を超えないと届かないので、開きがOPEN_MAXを超えないよう根元を前へ回す。
        float least = azimuth - OPEN_MAX;
        float gamma = Math.max(least, Math.min(lerp(FORWARD_NEAR, FORWARD_FAR, k), azimuth - OPEN_MIN));
        float curl = lerp(CURL_NEAR, CURL_FAR, k);
        float radius = Math.min(lerp(RADIUS_NEAR, RADIUS_FAR, k), RADIUS_SHARE * distance);
        // 上の的では、根元を届いた点の仰角のROOT_DIP倍だけ下へ向けて出す（根元の羽が頭の下へ入らない。そこから上へ曲がる）。
        float dip = ROOT_DIP * Math.max(0, (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -u.y)))));
        var curve = new WingBend();
        var trial = new Shape();
        if (!solve(p, u, distance, gamma, dip, forward, curl, radius, curve, out)) return false;
        float depth = pierce(p, u, distance, right, probes, natural, maxStretch, width, rootWidth, out, curve);
        float floor = Math.max(GAMMA_MIN, least);
        if (depth <= 0 || gamma <= floor) return true;
        if (!solve(p, u, distance, floor, dip, forward, curl, radius, curve, trial)) return true;
        if (pierce(p, u, distance, right, probes, natural, maxStretch, width, rootWidth, trial, curve) > 0) {
            // 一番外へ戻しても入る: 刻んだ中で一番浅い所。
            float best = depth;
            for (int i = 1; i <= CLEAR_STEPS; i++) {
                float g = gamma + (floor - gamma) * i / CLEAR_STEPS;
                if (!solve(p, u, distance, g, dip, forward, curl, radius, curve, trial)) continue;
                float d = pierce(p, u, distance, right, probes, natural, maxStretch, width, rootWidth, trial, curve);
                if (d < best - .01f) { best = d; copy(trial, out); }
            }
            return true;
        }
        // 入らない一番前の向きを二分法で詰める。
        float lo = floor, hi = gamma;
        copy(trial, out);
        for (int i = 0; i < CLEAR_STEPS; i++) {
            float mid = (lo + hi) / 2;
            if (!solve(p, u, distance, mid, dip, forward, curl, radius, curve, trial)) { hi = mid; continue; }
            if (pierce(p, u, distance, right, probes, natural, maxStretch, width, rootWidth, trial, curve) > 0) hi = mid;
            else { lo = mid; copy(trial, out); }
        }
        return true;
    }
    private static void copy(Shape from, Shape to) { to.side.set(from.side); to.theta = from.theta; to.radius = from.radius; to.curl = from.curl; to.gamma = from.gamma; }
    /** 根元の向きを外へ戻す下限（度。負は外かつ後ろ）と、外へ戻すときに詰める回数。 */
    static final float GAMMA_MIN = -30;
    private static final int CLEAR_STEPS = 6;
    /** 上の的で根元を下へ向ける、届いた点の仰角に対する比。 */
    static final float ROOT_DIP = .6f;

    // ---- 体の干渉を測る ----

    /** 形を測る羽: 真っ直ぐな翼での付け根の位置（根元からの長さ）、羽自身の回転、長さ、幅、根元の節か。 */
    record Probe(float base, Matrix3f turn, float length, float width, boolean root) { }
    /**
     * 体の箱（体の部品の空間、{x, y, z の最小, x, y, z の最大}）。余裕なしの頭と胴を1だけ内へ縮めた物（根元は背中のすぐ後ろにあり、止まっている
     * 翼の根元の羽も余裕なしの箱へ少し入るので、はっきり貫くかをこちらで測る）。
     */
    static final float[][] DEEP = {{-3, -7, -3, 3, -1, 3}, {-3, 1, -1, 3, 11, 1}};
    /** 中心の線を確かめるときに区切る数。 */
    static final int SPINE_SAMPLES = 12;

    /** 形shapeの中心の線（届いた点まで）と羽probesが{@link #DEEP}を通る長さの合計。curveは作業用。 */
    static float pierce(Vector3f p, Vector3f u, float distance, boolean right, java.util.List<Probe> probes, float natural, float maxStretch,
                        float width, float rootWidth, Shape shape, WingBend curve) {
        if (!curve.set(p, u, shape.side, shape.theta, distance, shape.radius, shape.curl)) return Float.MAX_VALUE;
        float total = 0;
        var from = toBody(right, curve.at(0, new Vector3f()));
        var to = new Vector3f();
        for (int k = 1; k <= SPINE_SAMPLES; k++) {
            toBody(right, curve.at(curve.length * k / SPINE_SAMPLES, to));
            total += through(from, to, DEEP);
            from.set(to);
        }
        float stretch = Math.max(1, Math.min(curve.length / natural, maxStretch));
        var back = new Vector3f(0, 0, right ? -1 : 1);
        var at = new Vector3f(); var turn = new Matrix3f(); var shapeOf = new Matrix3f(); var scratch = new Vector3f();
        for (var probe : probes) {
            curve.feather(probe.base * stretch, probe.length * stretch, back, at, turn);
            // 羽の板: 付け根から+xへ長さ、±yへ幅の半分（扇の角は羽自身の回転）。引き伸ばしと幅の倍率は羽の向きの空間で掛ける。
            shapeOf.set(turn).scale(stretch, probe.root ? rootWidth : width, 1).mul(probe.turn);
            total += kite(right, at, shapeOf, probe.length, probe.width, DEEP, scratch);
        }
        return total;
    }
    /** 羽の板（付け根at、向きと倍率shape、長さ、幅）を涙形に近い凧形（付け根・長さの15%で一番広い所・先）で、箱を通る長さを測る。 */
    static float kite(boolean right, Vector3f at, Matrix3f shape, float length, float width, float[][] boxes, Vector3f scratch) {
        var base = toBody(right, new Vector3f(at));
        var end = toBody(right, shape.transform(scratch.set(length, 0, 0)).add(at));
        var wide = shape.transform(new Vector3f(length * .15f, 0, 0)).add(at);
        var half = shape.transform(new Vector3f(0, width / 2, 0));
        var up = toBody(right, new Vector3f(wide).add(half));
        var down = toBody(right, new Vector3f(wide).sub(half));
        return through(base, end, boxes) + through(base, up, boxes) + through(base, down, boxes) + through(up, end, boxes)
                + through(down, end, boxes) + through(down, up, boxes);
    }
    /** 取付の空間の位置を、体の部品の空間へ（翼の親は(0,3,3)、取付は左右に1.5。右はY軸で180度回っている）。vを書き換えて返す。 */
    static Vector3f toBody(boolean right, Vector3f v) {
        return right ? v.set(-1.5f - v.x, 3 + v.y, 3 - v.z) : v.set(1.5f + v.x, 3 + v.y, 3 + v.z);
    }
    /** 線分abが、箱（{x, y, z の最小, x, y, z の最大}の並び）の中を通る長さ。 */
    static float through(Vector3f a, Vector3f b, float[][] boxes) {
        float total = 0, length = a.distance(b);
        for (var box : boxes) {
            float from = 0, to = 1;
            for (int axis = 0; axis < 3 && from <= to; axis++) {
                float start = a.get(axis), delta = b.get(axis) - start;
                if (Math.abs(delta) < 1e-6f) { if (start < box[axis] || start > box[axis + 3]) to = -1; continue; }
                float near = (box[axis] - start) / delta, far = (box[axis + 3] - start) / delta;
                from = Math.max(from, Math.min(near, far)); to = Math.min(to, Math.max(near, far));
            }
            if (to > from) total += (to - from) * length;
        }
        return total;
    }

    /** 根元の向き（真横から前へgamma度、下へdip度）になる曲がりの角θを解く。届かないときは半径を縮める。 */
    private static boolean solve(Vector3f p, Vector3f u, float distance, float gammaDeg, float dipDeg, float forward, float curl, float radius, WingBend curve,
                                 Shape out) {
        float gamma = (float) Math.toRadians(gammaDeg), dip = (float) Math.toRadians(dipDeg), flat = (float) Math.cos(dip);
        var start = new Vector3f((float) Math.cos(gamma) * flat, (float) Math.sin(dip), (float) Math.sin(gamma) * flat * forward);
        // 曲がる側: 根元の向きの、届いた点の向きに直角な成分。
        var side = new Vector3f(start).fma(-start.dot(u), u);
        if (side.lengthSquared() < 1e-4f) return false;
        side.normalize();
        float open = Math.max(angle(start, u), OPEN_MIN);
        float bestTheta = -1, bestRadius = radius, bestGap = Float.MAX_VALUE;
        for (int attempt = 0; attempt < SHRINK_TRIES; attempt++, radius *= SHRINK) {
            // θを小さい方から進め、出だしの角がopenを越える所を挟んで詰める。
            float below = 0;
            for (float th = SCAN_STEP; th <= THETA_LIMIT; th += SCAN_STEP) {
                if (!curve.set(p, u, side, th, distance, radius, curl)) break;
                float gap = Math.abs(curve.open - open);
                if (gap < bestGap) { bestGap = gap; bestTheta = th; bestRadius = radius; }
                if (curve.open >= open) {
                    float lo = below, hi = th;
                    for (int i = 0; i < REFINE; i++) {
                        float mid = (lo + hi) / 2;
                        if (curve.set(p, u, side, mid, distance, radius, curl) && curve.open < open) lo = mid; else hi = mid;
                    }
                    out.side.set(side); out.theta = hi; out.radius = radius; out.curl = curl; out.gamma = gammaDeg;
                    return true;
                }
                below = th;
            }
        }
        if (bestTheta < 0) return false;
        out.side.set(side); out.theta = bestTheta; out.radius = bestRadius; out.curl = curl; out.gamma = gammaDeg;
        return true;
    }
    static float lerp(float a, float b, float k) { return a + (b - a) * k; }
    /** 2つの単位ベクトルの間の角度（度）。 */
    static float angle(Vector3f a, Vector3f b) {
        return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.dot(b)))));
    }
}
