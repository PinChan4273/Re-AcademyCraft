package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * WeAthFolDのCurrentChargingHUD/SubArc2D（AcademyCraft 7b1401c）のCPUだけの版。呼び出し元が単調増加する経過秒と、tickごとの1回の更新を渡す。
 * 受け付けたセッションの寿命・テクスチャ・ネットワーク・Forgeのオーバーレイは結び付けない。
 */
public final class LegacyIntensifyHudAnimation {
    public static final int TEMPLATES = 10;
    public record ArcFrame(double x, double y, double size, int template,
                           int lifetime, boolean visible, boolean dead) { }
    public record Frame(double maskAlpha, double arcAlpha, boolean disposed, List<ArcFrame> arcs) {
        public Frame { arcs = List.copyOf(arcs); }
    }
    private static final class Arc {
        final double x, y, size;
        final int life;
        final double switchRate;
        int template, lifetime;
        boolean visible = true, dead;
        Arc(double x, double y, double size, boolean release, RandomGenerator random) {
            this.x = x; this.y = y; this.size = size;
            life = release ? 25 : 233333; switchRate = release ? .2 : 0;
            template = random.nextInt(TEMPLATES);
        }
        void tick(RandomGenerator random) {
            if (random.nextDouble() < .5 * .3) template = random.nextInt(TEMPLATES);
            if (random.nextDouble() < .9) lifetime++;
            if (lifetime == life) dead = true;
            if (visible) {
                if (random.nextDouble() < .4 * switchRate) visible = false;
            } else if (random.nextDouble() < .3 * switchRate) visible = true;
        }
        ArcFrame frame() { return new ArcFrame(x, y, size, template, lifetime, visible, dead); }
    }
    private final List<Arc> arcs = new ArrayList<>(14);
    private double blendAt = -1;
    private boolean cleared;

    public LegacyIntensifyHudAnimation(RandomGenerator random) { generate(false, Objects.requireNonNull(random)); }

    private void generate(boolean release, RandomGenerator random) {
        // RandUtils.rangeiは上限を含まない: 押している間は5〜6、離した後は10〜14。
        int count = release ? 10 + random.nextInt(5) : 5 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double radius = release ? .6 + random.nextDouble() * .4 : .84 + random.nextDouble() * .12;
            double theta = random.nextDouble() * Math.PI * 2;
            double size = (release ? 35 : 25) + random.nextDouble() * 5;
            arcs.add(new Arc(radius * Math.sin(theta), radius * Math.cos(theta), size, release, random));
        }
    }
    /** 冪等な終了への遷移。成功した完了の許可は、呼び出し元が別に行わなければならない。 */
    public boolean startBlend(double ageSeconds, boolean performed, boolean firstPerson, RandomGenerator random) {
        validateTime(ageSeconds); Objects.requireNonNull(random);
        if (cleared || blendAt >= 0) return false;
        blendAt = ageSeconds; arcs.clear();
        if (performed && firstPerson) generate(true, random);
        return true;
    }
    public void tick(double ageSeconds, RandomGenerator random) {
        validateTime(ageSeconds); Objects.requireNonNull(random);
        if (disposed(ageSeconds)) { clear(); return; }
        var iterator = arcs.iterator();
        while (iterator.hasNext()) {
            var arc = iterator.next();
            // 原作の処理は、新しく死んだ電弧を次のtickに取り除く。
            if (arc.dead) iterator.remove(); else arc.tick(random);
        }
    }
    /** 読み取りは乱数を消費せず、寿命を更新せず、破棄の状態も変えない。 */
    public Frame snapshot(double ageSeconds) {
        validateTime(ageSeconds);
        if (disposed(ageSeconds)) return new Frame(0, 0, true, List.of());
        double alpha = blendAt < 0 ? Math.min(ageSeconds / .5, 1)
                : Math.max(1 - (ageSeconds - blendAt) / .2, 0);
        // 原作の電弧の不透明度は、マスクのフェードとは独立。
        return new Frame(alpha, blendAt < 0 ? .3 : .4, false, arcs.stream().map(Arc::frame).toList());
    }
    public void clear() { cleared = true; arcs.clear(); }
    private boolean disposed(double ageSeconds) { return cleared || blendAt >= 0 && ageSeconds - blendAt > 1; }
    private void validateTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || blendAt >= 0 && seconds < blendAt)
            throw new IllegalArgumentException("HUD elapsed time must be finite, nonnegative and not precede release");
    }
}
