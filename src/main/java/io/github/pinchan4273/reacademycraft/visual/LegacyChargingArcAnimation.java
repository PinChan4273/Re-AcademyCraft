package io.github.pinchan4273.reacademycraft.visual;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * WeAthFolDのEntityArcの更新順に、CurrentChargingの確率を合わせたJavaだけの版（AcademyCraft 7b1401c）。対象・ネットワーク・GPU・
 * セッションの寿命は持たない。
 * クライアントtickごとに1回進め、描画フレームは不変のスナップショットを読むだけ。
 */
public final class LegacyChargingArcAnimation {
    public static final int TEMPLATES = 20;
    public record Frame(int template, boolean visible) { }

    // 原作のiidは0で埋めた配列。生成で乱数を消費しない。
    private int template;
    private boolean visible = true;
    // EntityArcのtexWiggle、showWiggle、hideWiggle。既定はCurrentChargingのもの。
    private final double texWiggle, showWiggle, hideWiggle;
    public LegacyChargingArcAnimation() { this(.8, .2, .8); }
    /** 別のEntityArc自身の揺れ: Magnetic Movementのものは1、0.1、0.6。 */
    public LegacyChargingArcAnimation(double texWiggle, double showWiggle, double hideWiggle) {
        this.texWiggle = texWiggle; this.showWiggle = showWiggle; this.hideWiggle = hideWiggle;
    }

    public void tick(RandomGenerator random) {
        Objects.requireNonNull(random);
        if (random.nextDouble() < texWiggle) template = random.nextInt(TEMPLATES);
        if (visible) {
            if (random.nextDouble() < showWiggle) visible = false;
        } else if (random.nextDouble() < hideWiggle) visible = true;
    }

    public Frame snapshot() { return new Frame(template, visible); }
}
