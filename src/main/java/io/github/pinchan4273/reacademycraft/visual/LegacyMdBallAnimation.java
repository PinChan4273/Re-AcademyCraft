package io.github.pinchan4273.reacademycraft.visual;

import net.minecraft.util.RandomSource;

/**
 * AcademyCraft commit 7b1401cの原作EntityMdBallの描画処理の、範囲を限ったCPU版。
 * ワールド・テクスチャ・姿勢・GPUの状態は持たない。
 *
 * 原作は描画tickから実時間の秒に対してこれを動かすので、揺れの速さはフレームレートに従う。ここではクライアントtickごとに1回進める。
 * これは決定的で、このパッケージの他のアニメーションとも合う。1段あたりの加速の上限は変えていない。
 */
public final class LegacyMdBallAnimation {
    public static final int TEXTURE_COUNT = 5;
    public static final double SECONDS_PER_TICK = .05;
    /** 原作burstTime、ブレンドの窓、オーブが落ち着くアルファ。 */
    public static final float BURST_SECONDS = .4f, BLEND_SECONDS = .15f, RESTING_ALPHA = .6f;
    public static final float FADE_IN_SECONDS = .3f;
    private static final double MAX_ACCELERATION = 4;

    /** 光と芯は、同じ点の2枚のビルボードとして、原作自身の大きさで描く。 */
    public record Snapshot(float alpha, float size, int texture, float wiggle,
                           double offsetX, double offsetY, double offsetZ, boolean finished) {
        public float glowAlpha() { return alpha * (.3f + wiggle * .7f); }
        public float coreAlpha() { return alpha * (.8f + .2f * wiggle); }
        public float glowSize() { return .7f * size; }
        public float coreSize() { return .5f * size; }
    }

    private int lifeTicks;
    private int elapsedTicks;
    private float wiggle = .8f;
    private double acceleration;
    private int texture;

    public LegacyMdBallAnimation(int lifeTicks) {
        if (lifeTicks <= 0 || lifeTicks > 1200) throw new IllegalArgumentException("Invalid md ball life");
        this.lifeTicks = lifeTicks;
    }

    public void tick(RandomSource random) {
        if (elapsedTicks >= lifeTicks) return;
        elapsedTicks++;
        if (random.nextInt(8) < 3) acceleration = random.nextDouble() * 2 * MAX_ACCELERATION - MAX_ACCELERATION;
        wiggle = (float) Math.max(0, Math.min(1, wiggle + acceleration * SECONDS_PER_TICK));
        if (random.nextInt(8) < 2) texture = random.nextInt(TEXTURE_COUNT);
    }

    public Snapshot snapshot() {
        double seconds = elapsedTicks * SECONDS_PER_TICK;
        // 原作の周囲の揺れ。ビルボードを描く前に平行移動として適用する。
        float phase = (float) (seconds / .3f);
        return new Snapshot(alpha(seconds), size(seconds), texture, wiggle,
                .03 * Math.sin(phase), .04 * Math.cos(phase * 1.4 + Math.PI / 3.5), .03 * Math.cos(phase),
                elapsedTicks >= lifeTicks);
    }

    /** 既に表示しているオーブの更新または退去: 今からremaining tick生き、フェードインはやり直さない（Electron Missileの切り替え型モード）。 */
    public void renew(int remaining) {
        if (remaining <= 0 || remaining > 1200) throw new IllegalArgumentException("Invalid md ball life");
        if (elapsedTicks >= lifeTicks) return;
        lifeTicks = Math.max(elapsedTicks + 1, (int) Math.min(Integer.MAX_VALUE, (long) elapsedTicks + remaining));
    }
    public int lifeTicks() { return lifeTicks; }
    public int elapsedTicks() { return elapsedTicks; }

    private float alpha(double seconds) {
        float life = lifeTicks * (float) SECONDS_PER_TICK;
        if (seconds > life - BLEND_SECONDS)
            return Math.max(0, lerp(1, 0, (float) (seconds - (life - BLEND_SECONDS)) / BLEND_SECONDS));
        if (seconds > life - BURST_SECONDS)
            return lerp(RESTING_ALPHA, 1, (float) (seconds - (life - BURST_SECONDS)) / (BURST_SECONDS - BLEND_SECONDS));
        if (seconds < FADE_IN_SECONDS) return lerp(0, RESTING_ALPHA, (float) seconds / FADE_IN_SECONDS);
        return RESTING_ALPHA;
    }

    /**
     * 原作getSizeは経過秒を、ミリ秒で作った閾値（life * 50）と比べるので、原作のどのオーブの寿命でも分岐は発火せず、オーブは
     * 寿命の間ずっと同じ大きさのまま。それが原作の見た目なので、それを再現する。変えると意図的な見た目の変更になり、移植の修正ではない。
     */
    private float size(double seconds) {
        double lifeMilliseconds = lifeTicks * 50;
        if (seconds > lifeMilliseconds - 100)
            return Math.max(0, lerp(1.5f, 0, (float) (seconds - (lifeMilliseconds - 100)) / 100));
        if (seconds > lifeMilliseconds - 300)
            return lerp(1, 1.5f, (float) (seconds - (lifeMilliseconds - 300)) / 200);
        return 1;
    }

    private static float lerp(float from, float to, float progress) { return from + (to - from) * progress; }
}
