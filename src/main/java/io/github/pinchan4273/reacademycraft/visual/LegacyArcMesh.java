package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * WeAthFolDのArcFactory.Arc.handleSegment（7b1401c）に対するCPUの四角形アダプタ。
 * 継ぎ目の向き、UVの順、区間全体の開始Xでの切り捨てを保つ。
 * この不変のメッシュはGPUの描画処理でも、受け付けた溜めのセッションでもない。
 */
public final class LegacyArcMesh {
    public static final int MAX_VERTICES = LegacyArcGeometry.MAX_SEGMENTS * 4;
    private static final double EPSILON_SQUARED = 1.0e-16;
    private static final Vec3 NORMAL = new Vec3(0, 0, 1);

    public record Vertex(Vec3 position, float u, float v) { }
    public record Quad(Vertex first, Vertex second, Vertex third, Vertex fourth, float alpha) { }
    public record Mesh(List<Quad> quads, boolean saturated, int skippedDegenerateSegments) {
        public Mesh { quads = List.copyOf(quads); }
        public int vertexCount() { return quads.size() * 4; }
    }

    private LegacyArcMesh() { }

    /**
     * 選んだテンプレート・見た目の更新ごとに1回bakeし、描画パスごとに別々にはbakeしない。
     * 切り捨てはゲームプレイの射程を許可せず、テンプレートのノイズを拡大縮小もしない。
     * 原作の描画と同じく、ちょうど切り捨ての位置から始まる区間は残す。
     */
    public static Mesh bake(LegacyArcGeometry.Pattern pattern, double cutoff, RandomGenerator random) {
        Objects.requireNonNull(pattern); Objects.requireNonNull(random);
        if (!Double.isFinite(cutoff) || cutoff < 0 || cutoff > 32)
            throw new IllegalArgumentException("Arc cutoff outside finite 0..32 bound");
        validate(pattern);
        var quads = new ArrayList<Quad>();
        int skipped = 0;
        for (var chain : pattern.chains()) {
            Vec3 previous = null;
            for (var segment : chain) {
                if (segment.start().position().x > cutoff) break;
                Vec3 delta = segment.end().position().subtract(segment.start().position());
                if (delta.lengthSqr() < EPSILON_SQUARED) {
                    skipped++;
                    previous = null;
                    continue;
                }
                Vec3 perpendicular = delta.cross(NORMAL);
                // 生成した形状はXに沿って走る。ゼロベクトルを正規化せず、将来の垂直・平行な入力でも有限の幅を保つ。
                if (perpendicular.lengthSqr() < EPSILON_SQUARED)
                    perpendicular = delta.cross(new Vec3(0, 1, 0));
                Vec3 direction = rotate(random, perpendicular).normalize();
                if (previous == null) previous = direction;
                Vec3 start = segment.start().position(), end = segment.end().position();
                Vec3 startOffset = previous.scale(segment.start().width());
                Vec3 endOffset = direction.scale(segment.end().width());
                quads.add(new Quad(new Vertex(start.add(startOffset), 0, 0),
                        new Vertex(start.subtract(startOffset), 0, 1),
                        new Vertex(end.subtract(endOffset), 1, 1),
                        new Vertex(end.add(endOffset), 1, 0), (float) segment.alpha()));
                previous = direction;
            }
        }
        return new Mesh(quads, pattern.saturated(), skipped);
    }

    private static void validate(LegacyArcGeometry.Pattern pattern) {
        if (!Double.isFinite(pattern.length()) || pattern.length() < 0 || pattern.length() > 32
                || pattern.chains().size() > LegacyArcGeometry.MAX_CHAINS)
            throw new IllegalArgumentException("Invalid arc pattern bounds");
        int count = 0;
        for (var chain : pattern.chains()) {
            count += chain.size();
            if (count > LegacyArcGeometry.MAX_SEGMENTS)
                throw new IllegalArgumentException("Arc segment budget exceeded");
            for (var segment : chain) {
                if (!Double.isFinite(segment.alpha()) || segment.alpha() < 0 || segment.alpha() > 1)
                    throw new IllegalArgumentException("Invalid arc alpha");
                for (var point : List.of(segment.start(), segment.end())) {
                    Vec3 p = point.position();
                    if (!bounded(p.x) || !bounded(p.y) || !bounded(p.z)
                            || !Double.isFinite(point.width()) || point.width() <= 0 || point.width() > 32)
                        throw new IllegalArgumentException("Invalid arc point/width");
                }
            }
        }
    }

    private static boolean bounded(double value) { return Double.isFinite(value) && Math.abs(value) <= 128; }

    // 原作randomRotate(15)と同じpitch/yaw/MCPの時計回りZの摂動。
    private static Vec3 rotate(RandomGenerator random, Vec3 direction) {
        float a = (float) (between(random, -15, 15) / 180 * Math.PI);
        Vec3 result = direction.xRot(between(random, -a, a)).yRot(between(random, -a, a));
        float z = between(random, -a, a), sin = Mth.sin(z), cos = Mth.cos(z);
        return new Vec3(result.x * cos + result.y * sin, result.y * cos - result.x * sin, result.z);
    }

    private static float between(RandomGenerator random, float low, float high) {
        return low + (high - low) * random.nextFloat();
    }
}
