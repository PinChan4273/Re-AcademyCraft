package io.github.pinchan4273.reacademycraft.visual;

/**
 * 原作TechUIの情報領域の開きとフェードの、CPUだけの版。
 * 呼び出し元が単調増加する秒の時計と現在の内容の高さを渡す。このクラスはMinecraft・メニュー・描画に依存しない。
 */
public final class LegacyWindInfoAnimation {
    public static final double SPEED = 500;
    public static final double MAX_STEP_SECONDS = .5;
    public static final double FADE_DELAY_SECONDS = .3;
    public static final double FADE_SECONDS = .3;

    public record Frame(double height, double contentAlpha) { }

    private final double startedAt;
    private double lastAt;
    private double height;

    public LegacyWindInfoAnimation(double nowSeconds) {
        validateTime(nowSeconds);
        startedAt = lastAt = nowSeconds;
    }

    /** 原作の0.5秒のフレーム差を上限に進め、両方向へ動く。 */
    public Frame advance(double nowSeconds, double targetHeight) {
        validateTime(nowSeconds);
        if (nowSeconds < lastAt) throw new IllegalArgumentException("Wind information clock moved backwards");
        if (!Double.isFinite(targetHeight) || targetHeight < 0)
            throw new IllegalArgumentException("Wind information target height must be finite and nonnegative");
        double maxMove = Math.min(nowSeconds - lastAt, MAX_STEP_SECONDS) * SPEED;
        double delta = targetHeight - height;
        height += Math.copySign(Math.min(maxMove, Math.abs(delta)), delta);
        lastAt = nowSeconds;
        return new Frame(height, contentAlpha(nowSeconds - startedAt));
    }

    private static double contentAlpha(double elapsed) {
        return Math.max(0, Math.min(1, (elapsed - FADE_DELAY_SECONDS) / FADE_SECONDS));
    }

    private static void validateTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Wind information time must be finite and nonnegative");
    }
}
