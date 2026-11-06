package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * WeAthFolDによるAcademyCraftのArcFactory（原作commit 7b1401c）のCPU版。
 * 交互のバッファと、その動的な枝の追加順を保つ。
 * GL・クライアント・ワールドの状態は持たない: 生成だけでは見える効果にはならない。
 */
public final class LegacyArcGeometry {
    public static final int MAX_CHAINS = 128;
    public static final int MAX_SEGMENTS = MAX_CHAINS * 32;

    public enum Profile {
        WEAK(6, .1, 1.1, .15, .7, 20, 20),
        STRONG(5, .3, 1.4, .3, .7, 20, 20),
        AOE(5, .13, 1.2, .28, .7, 20, 20),
        CHARGING(5, .1, 1.2, .3, .7, 20, 20),
        SURROUND_THIN(3, .2, .8, .7, .9, 1.5, 2),
        SURROUND_NORMAL(3, .3, .8, .7, .9, 3, 4),
        SURROUND_BOLD(3, .35, 1.2, .45, .9, 3.5, 4.5),
        /** EntityRailgunFX自身のArcFactory: Railgunの光線に沿って散る小さな電弧。 */
        RAILGUN(3, .3, .8, .7, .9, 2, 3),
        /** ArcPatterns.thinContiniousArc: Magnetic Movementが引き寄せる先への電弧。 */
        THIN_CONTINUOUS(5, .08, 1.2, .2, .7, 20, 20);

        private final int passes;
        private final double width, offset, branch, widthShrink, from, to;
        Profile(int passes, double width, double offset, double branch,
                double widthShrink, double from, double to) {
            this.passes = passes; this.width = width; this.offset = offset;
            this.branch = branch; this.widthShrink = widthShrink;
            this.from = from; this.to = to;
        }
    }

    public record Point(Vec3 position, double width) { }
    public record Segment(Point start, Point end, double alpha) { }
    public record Pattern(double length, List<List<Segment>> chains, boolean saturated) {
        public Pattern {
            chains = chains.stream().map(List::copyOf).toList();
        }
        public int segmentCount() { return chains.stream().mapToInt(List::size).sum(); }
    }

    private LegacyArcGeometry() { }

    public static Pattern generate(Profile profile, RandomGenerator random) {
        Objects.requireNonNull(profile); Objects.requireNonNull(random);
        return generate(profile, profile.from + random.nextDouble() * (profile.to - profile.from), random);
    }

    /** 長さは幾何の空間のもので、能力の射程や対象の許可ではない。 */
    public static Pattern generate(Profile profile, double length, RandomGenerator random) {
        Objects.requireNonNull(profile); Objects.requireNonNull(random);
        if (!Double.isFinite(length) || length < 0 || length > 32)
            throw new IllegalArgumentException("Arc length outside finite0..32 geometry bound");
        // 始点と終点が一致すると見える帯が無い。潰れたノイズの塊を避ける。
        if (length == 0) return new Pattern(0, List.of(), false);
        var lists = new ArrayList<List<Segment>>();
        var buffers = new ArrayList<List<Segment>>();
        lists.add(new ArrayList<>(List.of(new Segment(new Point(Vec3.ZERO, profile.width),
                new Point(new Vec3(length, 0, 0), profile.width), 1))));
        buffers.add(new ArrayList<>());
        boolean flip = false, saturated = false;
        double offset = profile.offset;
        for (int pass = 0; pass < profile.passes; pass++) {
            // 意図的にsizeを読み直す: 原作はこのパスで追加した枝も処理する。
            for (int chain = 0; chain < lists.size(); chain++) {
                var source = flip ? buffers.get(chain) : lists.get(chain);
                var destination = flip ? lists.get(chain) : buffers.get(chain);
                destination.clear();
                for (Segment segment : source) {
                    Vec3 center = segment.start.position.add(segment.end.position).scale(.5);
                    float theta = (float) (random.nextFloat() * Math.PI * 2);
                    double displacement = random.nextFloat() * offset;
                    var middle = new Point(center.add(0, displacement * Mth.sin(theta),
                            displacement * Mth.cos(theta)), (segment.start.width + segment.end.width) * .5);
                    destination.add(new Segment(segment.start, middle, segment.alpha));
                    destination.add(new Segment(middle, segment.end, segment.alpha));
                    if (random.nextDouble() < profile.branch) {
                        Vec3 direction = rotate(random, 10, middle.position.subtract(segment.start.position).scale(.7));
                        double width = middle.width * profile.widthShrink;
                        if (lists.size() < MAX_CHAINS) {
                            // flipに関係なく常にこれらのバッファ。木の走査へ正規化しない。
                            buffers.add(new ArrayList<>(List.of(new Segment(new Point(middle.position, width),
                                    new Point(middle.position.add(direction), width), segment.alpha * .9))));
                            lists.add(new ArrayList<>());
                        } else saturated = true;
                    }
                }
            }
            flip = !flip;
            offset *= .5;
        }
        return new Pattern(length, flip ? buffers : lists, saturated);
    }

    private static Vec3 rotate(RandomGenerator random, float range, Vec3 direction) {
        float a = (float) (between(random, -range, range) / 180 * Math.PI);
        Vec3 result = direction.xRot(between(random, -a, a)).yRot(between(random, -a, a));
        float z = between(random, -a, a), sin = Mth.sin(z), cos = Mth.cos(z);
        return new Vec3(result.x * cos + result.y * sin, result.y * cos - result.x * sin, result.z);
    }

    private static float between(RandomGenerator random, float low, float high) {
        return low + (high - low) * random.nextFloat();
    }
}
