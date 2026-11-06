package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 原作の効果が使うLambdaLib2のCubicCurve: 整列した点の間の3次エルミート区間。接線は隣の傾きの平均（端では片側）、両端の外は直線
 * （最初の直線は、原作の計算どおり最初の接線を単位長で取る）。
 */
public final class LegacyCubicCurve {
    private final List<double[]> points = new ArrayList<>();

    public LegacyCubicCurve add(double x, double y) {
        points.add(new double[] {x, y}); points.sort(Comparator.comparingDouble(p -> p[0]));
        return this;
    }
    public double valueAt(double x) {
        if (points.isEmpty()) return 0;
        int index = 0;
        while (index < points.size() && points.get(index)[0] < x) index++;
        if (index == points.size()) {
            var last = points.get(points.size() - 1);
            double k = points.size() >= 2 ? slope(index - 1, index - 2) : 0;
            return last[1] + (x - last[0]) * k;
        }
        if (index == 0) {
            var first = points.get(0);
            return first[1] + tangent(0, 1) * (x - first[0]);
        }
        var p0 = points.get(index - 1); var p1 = points.get(index);
        double l = p1[0] - p0[0], t = (x - p0[0]) / l, t2 = t * t, t3 = t2 * t;
        double y0 = p0[1], y1 = p1[1], m0 = tangent(index - 1, l), m1 = tangent(index, l);
        return t3 * (m0 + m1 + 2 * y0 - 2 * y1) + t2 * (-2 * m0 - m1 - 3 * y0 + 3 * y1) + t * m0 + y0;
    }
    private double tangent(int i, double l) {
        double k;
        if (i == 0) k = points.size() == 1 ? 0 : slope(i, i + 1);
        else if (i == points.size() - 1) k = slope(i, i - 1);
        else k = .5 * (slope(i + 1, i) + slope(i, i - 1));
        return k * l;
    }
    private double slope(int i1, int i2) {
        var a = points.get(i1); var b = points.get(i2);
        return (b[1] - a[1]) / (b[0] - a[0]);
    }
}
