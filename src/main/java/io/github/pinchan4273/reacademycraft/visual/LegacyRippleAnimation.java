package io.github.pinchan4273.reacademycraft.visual;

import java.util.List;

/** AcademyCraft commit 7b1401cの原作EntityRippleMarkの描画処理の、範囲を限ったCPU版。ワールド・テクスチャ・姿勢・GPUの状態は持たない。 */
public final class LegacyRippleAnimation {
    public static final double CYCLE_SECONDS = 3.6;
    public static final int RED = 204, GREEN = 204, BLUE = 204, BASE_ALPHA = 179;
    private static final double FADE_SECONDS = 1.6;
    private static final double[] OFFSETS = {0, 1.2, 2.4};

    public record Ring(int index, double phase, double height, double size, double alpha) { }

    private double elapsed;
    private boolean stopped;

    public void advance(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(elapsed + seconds))
            throw new IllegalArgumentException("Ripple delta must keep finite monotonic time");
        if (!stopped) elapsed += seconds;
    }

    public List<Ring> snapshot() {
        if (stopped) return List.of();
        return sample(elapsed);
    }

    /** 読み取り専用の滑らかな描画用の標本。範囲を限った状態は変えない。 */
    public static List<Ring> sample(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0)
            throw new IllegalArgumentException("Ripple sample time must be finite and nonnegative");
        return List.of(ring(elapsedSeconds, 0), ring(elapsedSeconds, 1), ring(elapsedSeconds, 2));
    }

    public void stop() { stopped = true; }
    public boolean stopped() { return stopped; }
    public double elapsed() { return elapsed; }

    private static Ring ring(double elapsedSeconds, int index) {
        double phase = (elapsedSeconds + OFFSETS[index]) % CYCLE_SECONDS;
        double height = phase * .3;
        double size = 1.9 + (1.4 - 1.9) * (phase / CYCLE_SECONDS);
        double alpha = phase < FADE_SECONDS ? phase / FADE_SECONDS
                : phase > CYCLE_SECONDS - FADE_SECONDS
                ? 1 - (phase - (CYCLE_SECONDS - FADE_SECONDS)) / FADE_SECONDS : 1;
        return new Ring(index, phase, height, size, alpha);
    }
}
