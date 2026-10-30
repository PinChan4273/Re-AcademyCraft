package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 原作TornadoEffectとTornadoRendererの幾何: 高さまで積んだテクスチャ付きの輪の柱。輪はそれぞれ幅・位相・大きさを持ち、時間と共に
 * Perlinノイズで揺れ、高いほど速く回る。生成時に1回決め、独自の時間のずれを持つ。時間は原作のGameTimerの秒 × 4。
 * 描画処理は輪を四角形にする。描くのは呼び出し元。
 */
public final class LegacyTornado {
    public record Ring(double y, double width, double phase, double sizeScale) { }
    /** 原作TornadoEffect_.divideとTornadoRenderer.div。 */
    public static final int DIVIDE = 40, SEGMENTS = 20;
    private static final double[][] CIRCLE = new double[SEGMENTS][];
    static {
        for (int i = 0; i < SEGMENTS; i++) {
            double rad = i / (double) SEGMENTS * Math.PI * 2;
            CIRCLE[i] = new double[] {Math.sin(rad), Math.cos(rad)};
        }
    }

    public final double height, size, density, dscale;
    public final List<Ring> rings = new ArrayList<>();
    private final double timeOffset;
    public double alpha = 1;

    public LegacyTornado(double height, double size, double density, double dscale, Random random) {
        this.height = height; this.size = size; this.density = density; this.dscale = dscale;
        timeOffset = random.nextDouble() * 20;
        double accum = 0, step = height / DIVIDE;
        while (accum < height) {
            accum += step * (1 + random.nextGaussian() * .2);
            if (random.nextDouble() < density) {
                // 原作のcase classは、ここで与えるコンストラクタ引数から位相を取る。
                rings.add(new Ring(accum, step * range(random, 1.8, 2.2), random.nextDouble() * 360, range(random, .9, 1.2)));
                if (random.nextDouble() < .35)
                    rings.add(new Ring(accum, step * range(random, 1.8, 2.2), random.nextDouble() * 360, range(random, 1.2, 1.7)));
            }
        }
    }
    private static double range(Random random, double min, double max) { return min + random.nextDouble() * (max - min); }

    /** 原作time(): GameTimerの秒 × 4から、この竜巻のずれを引いたもの。 */
    public double time(double seconds) { return seconds * 4 - timeOffset; }

    /** 四角形1つ: テクスチャのu, vを持つ4つの角(x, y, z)。 */
    public record Quad(double[] x, double[] y, double[] z, double[] u, double[] v) { }

    /** ある時刻の原作doRenderの輪を、四角形として。 */
    public List<Quad> quads(double time) {
        var result = new ArrayList<Quad>(rings.size() * SEGMENTS);
        for (var ring : rings) {
            double ny = ring.y() / height;
            double t = time * .1, sway = .3 + Math.pow(ny * 2, 1.4);
            double dx = LegacyNoise.noise(ny, t) * sway * size * dscale;
            double dz = LegacyNoise.noise(ny, t, 1) * sway * size * dscale;
            double r = ((.5 + .3 * LegacyNoise.noise(ny, .2 * time)) + .5 * Math.pow(1.5 * ny, 2) + LegacyNoise.noise(ny)) * size * ring.sizeScale();
            double rot = .1 * (1 + .5 * ny) * time + ring.phase();
            double y0 = ring.y() + ring.width() / 2, y1 = ring.y() - ring.width() / 2, uStep = 1.0 / SEGMENTS;
            for (int i = 0; i < SEGMENTS; i++) {
                double[] a = CIRCLE[i], b = CIRCLE[(i + 1) % SEGMENTS];
                double x0 = a[0] * r, z0 = a[1] * r, x1 = b[0] * r, z1 = b[1] * r;
                double u0 = uStep * i - rot, u1 = u0 + uStep;
                result.add(new Quad(new double[] {x0 + dx, x0 + dx, x1 + dx, x1 + dx}, new double[] {y0, y1, y1, y0},
                        new double[] {z0 + dz, z0 + dz, z1 + dz, z1 + dz}, new double[] {u0, u0, u1, u1}, new double[] {0, 1, 1, 0}));
            }
        }
        return result;
    }
}
