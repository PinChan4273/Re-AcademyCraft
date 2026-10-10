package io.github.pinchan4273.reacademycraft.visual;

import net.minecraft.util.RandomSource;

/**
 * AcademyCraft commit 7b1401cの原作EntityRayBaseのタイミングと、その上に作られたmeltdownerの2つの光線（EntityMdRaySmallと
 * EntityMDRay）の、範囲を限ったCPU版。ワールド・テクスチャ・姿勢・GPUの状態は持たない。
 *
 * 光線は現れるときに全長まで伸び、保ち、細くなって消える。原作はその3つすべてを生成からのミリ秒で測り、下の定数もその単位。
 */
public final class LegacyMdRayAnimation {
    /** 原作の2つの光線エンティティ: そのタイミングと、RendererRayCompositeの幅とアルファ。 */
    public enum Profile {
        /**
         * EntityMdRaySmall: Electron Bomb、Scatter Bomb、Electron Missileが撃つもの。基底の幅の変化を独自のもので上書きし、消え始める所ではなく
         * 光線が伸びきった所から始め、基底クラスの幅の揺れを持たない。
         */
        SMALL(14, 15, 200, 400, 500, false, .03, .045, .3, 127, "mdray_small"),
        /** EntityMDRay: Meltdowner技能自身の光線。EntityRayBaseの幅の変化と揺れを保ち、タイミングと描画の幅だけを変える。 */
        MELTDOWNER(50, 30, 200, 700, 300, true, .17, .22, 1.5, 204, "mdray"),
        /**
         * EntityMineRayBasic: 技能が続く間（life 233333）保つEntityRayBase。長さ15、小さな光線のテクスチャにBasicMineRayRenderの幅。
         */
        MINE_BASIC(233333, 15, 200, 400, 300, true, .03, .045, .3, 127, "mdray_small"),
        /**
         * EntityMineRayExpert: ExpertRayRendererは毎フレーム色をリセットし、コンストラクタの230と0.7の代わりに、内側の円柱のアルファを180、
         * 光を0.5にする。
         */
        MINE_EXPERT(233333, 15, 200, 400, 300, true, .045, .056, .5, 127, "mdray_expert",
                new int[] {216, 248, 216, 180}, new int[] {106, 242, 106, 50}),
        /** EntityMineRayLuck: LuckRayRenderの紫の円柱と0.6の光を、独自のテクスチャで。 */
        MINE_LUCK(233333, 15, 200, 400, 300, true, .04, .05, .45, 153, "mdray_luck",
                new int[] {241, 229, 247, 230}, new int[] {205, 166, 232, 50}),
        /**
         * Silbarnに当たらなかったEntityBarrageRayPre: BRPRenderの少し広い円柱。独自のgetWidth()は寿命の間ずっと幅を1に保つ
         * （原作が適用するつもりだった縮小は、範囲が逆向きのclampd(1, 0, ...)で打ち消される）ので、何も縮まない。
         */
        BARRAGE_PRE(30, 15, 200, 400, 0, false, .045, .052, .4, 127, "mdray_small"),
        /** Silbarnに当たった場合の同じ光線: 原作は寿命を30ではなく50にする。 */
        BARRAGE_PRE_HIT(50, 15, 200, 400, 0, false, .045, .052, .4, 127, "mdray_small"),
        /** EntityMdRayBarrage: 散る光線。小さな光線の描画処理で描くが、50tick生き、EntityRayBase自身のブレンドと幅の変化を保つ。 */
        BARRAGE(50, 15, 100, 300, 300, true, .03, .045, .3, 127, "mdray_small"),
        /**
         * EntityRailgunFX: Railgunの光線。最も広く、揺れの半径と速さがEntityRayBaseと異なる唯一のもの（0.1と0.4に対して0.3と0.8）で、
         * 光の帯は始点で0.3後ろへ、終点で0.3先へ伸びる。円柱はmdの緑ではなく、RailgunRender独自の淡い白の芯とオレンジの鞘。
         * mdの光線と違い、RailgunRenderは光の色を設定しないので、光はRendererRayGlowのColors.white()（アルファ255）のまま。
         */
        RAILGUN(50, 45, 150, 1000, 800, true, .09, .13, 1.1, 255, "railgun",
                new int[] {241, 240, 222, 200}, new int[] {236, 170, 93, 60}, .3, .8, -.3, .3);

        public final int lifeTicks;
        public final double defaultLength;
        public final long blendInMs, blendOutMs, widthShrinkMs;
        public final boolean widthWiggles;
        public final double innerWidth, outerWidth, glowWidth;
        /** RendererRayGlow自身の色のアルファ: 小さな光線は0.5、Meltdownerのものは0.8。 */
        public final int glowAlpha;
        /** 3部分から成る光の帯のテクスチャのフォルダ（textures/effects以下）。 */
        public final String texture;
        /** RendererRayCompositeの円柱の色（RGBA）。 */
        public final int[] inner, outer;
        /** EntityRayBaseのwidthWiggleRadiusとmaxWiggleSpeed。変えるのはRailgunだけ。 */
        public final double wiggleRadius, wiggleSpeed;
        /** RendererRayGlowのstartFixとendFix: 光の帯が始まり終わる所（ブロック単位）。 */
        public final double glowStartFix, glowEndFix;

        Profile(int lifeTicks, double defaultLength, long blendInMs, long blendOutMs, long widthShrinkMs, boolean widthWiggles,
                double innerWidth, double outerWidth, double glowWidth, int glowAlpha, String texture) {
            this(lifeTicks, defaultLength, blendInMs, blendOutMs, widthShrinkMs, widthWiggles, innerWidth, outerWidth, glowWidth, glowAlpha, texture,
                    new int[] {INNER_RED, INNER_GREEN, INNER_BLUE, INNER_ALPHA}, new int[] {OUTER_RED, OUTER_GREEN, OUTER_BLUE, OUTER_ALPHA});
        }
        Profile(int lifeTicks, double defaultLength, long blendInMs, long blendOutMs, long widthShrinkMs, boolean widthWiggles,
                double innerWidth, double outerWidth, double glowWidth, int glowAlpha, String texture, int[] inner, int[] outer) {
            this(lifeTicks, defaultLength, blendInMs, blendOutMs, widthShrinkMs, widthWiggles, innerWidth, outerWidth,
                    glowWidth, glowAlpha, texture, inner, outer, WIGGLE_RADIUS, DEFAULT_WIGGLE_SPEED, 0, 0);
        }
        Profile(int lifeTicks, double defaultLength, long blendInMs, long blendOutMs, long widthShrinkMs, boolean widthWiggles,
                double innerWidth, double outerWidth, double glowWidth, int glowAlpha, String texture, int[] inner, int[] outer,
                double wiggleRadius, double wiggleSpeed, double glowStartFix, double glowEndFix) {
            this.inner = inner; this.outer = outer;
            this.wiggleRadius = wiggleRadius; this.wiggleSpeed = wiggleSpeed;
            this.glowStartFix = glowStartFix; this.glowEndFix = glowEndFix;
            this.lifeTicks = lifeTicks; this.defaultLength = defaultLength;
            this.blendInMs = blendInMs; this.blendOutMs = blendOutMs; this.widthShrinkMs = widthShrinkMs;
            this.widthWiggles = widthWiggles;
            this.innerWidth = innerWidth; this.outerWidth = outerWidth; this.glowWidth = glowWidth;
            this.glowAlpha = glowAlpha; this.texture = texture;
        }
    }

    /** 原作の両方の光線の円柱の色。違うのは幅だけ。 */
    public static final int INNER_RED = 216, INNER_GREEN = 248, INNER_BLUE = 216, INNER_ALPHA = 230;
    public static final int OUTER_RED = 106, OUTER_GREEN = 242, OUTER_BLUE = 106, OUTER_ALPHA = 50;
    public static final long MILLISECONDS_PER_TICK = 50;
    /**
     * EntityRayBaseのwidthWiggleRadius/glowWiggleRadius（0.1）と揺れの速さ（毎秒0.4）。原作は描画フレームごとに進め、ここではtickごとに進める。
     */
    public static final double WIGGLE_RADIUS = .1, DEFAULT_WIGGLE_SPEED = .4;
    public static final double WIGGLE_STEP = DEFAULT_WIGGLE_SPEED * MILLISECONDS_PER_TICK / 1000.0;

    /** 小さな光線の別名。小さな光線しか意図していなかった呼び出し元とテストのために残す。 */
    public static final int LIFE_TICKS = Profile.SMALL.lifeTicks;
    public static final double FULL_LENGTH = Profile.SMALL.defaultLength;
    public static final long BLEND_IN_MS = Profile.SMALL.blendInMs, BLEND_OUT_MS = Profile.SMALL.blendOutMs,
            WIDTH_SHRINK_MS = Profile.SMALL.widthShrinkMs;

    /**
     * glowはEntityRayBase.getGlowAlpha(): (1 - 0.1 + glowWiggle) * alpha。原作の光は色のアルファ × getAlpha() × それで描くので、
     * 光はアルファの2乗で消えていく。
     */
    public record Snapshot(double length, double width, double alpha, double glow, boolean finished) { }

    private final Profile profile;
    private final double fullLength;
    private int elapsedTicks;
    private double widthWiggle, glowWiggle;

    public LegacyMdRayAnimation() { this(Profile.SMALL, Profile.SMALL.defaultLength); }
    public LegacyMdRayAnimation(Profile profile, double fullLength) {
        if (profile == null || !Double.isFinite(fullLength) || fullLength <= 0 || fullLength > 64)
            throw new IllegalArgumentException("Invalid md ray");
        this.profile = profile; this.fullLength = fullLength;
    }

    public Profile profile() { return profile; }

    /** 時間だけを進める。揺れはそのまま。 */
    public void tick() { if (elapsedTicks < profile.lifeTicks) elapsedTicks++; }

    /** 時間と、EntityRayBaseの2つのランダムウォークの揺れを進め、[0, 0.1]に収める。 */
    public void tick(RandomSource random) {
        tick();
        widthWiggle = walk(widthWiggle, random, profile);
        glowWiggle = walk(glowWiggle, random, profile);
    }
    private static double walk(double value, RandomSource random, Profile profile) {
        double step = profile.wiggleSpeed * MILLISECONDS_PER_TICK / 1000.0;
        return Math.max(0, Math.min(profile.wiggleRadius, value + (random.nextDouble() * 2 - 1) * step));
    }

    public Snapshot snapshot() { return sample(profile, fullLength, elapsedTicks, widthWiggle, glowWiggle); }

    public int elapsedTicks() { return elapsedTicks; }

    /** 既定の長さで揺れの無い小さな光線。原作の曲線のテストが固定するもの。 */
    public static Snapshot sample(int elapsedTicks) { return sample(Profile.SMALL, FULL_LENGTH, elapsedTicks, 0, 0); }

    public static Snapshot sample(Profile profile, double fullLength, int elapsedTicks, double widthWiggle, double glowWiggle) {
        if (elapsedTicks < 0 || elapsedTicks > profile.lifeTicks) throw new IllegalArgumentException("Invalid md ray time");
        long milliseconds = elapsedTicks * MILLISECONDS_PER_TICK, life = profile.lifeTicks * MILLISECONDS_PER_TICK;
        double length = (milliseconds < profile.blendInMs ? (double) milliseconds / profile.blendInMs : 1) * fullLength;
        double alpha = Math.max(0, milliseconds > life - profile.blendOutMs
                ? 1 - (double) (milliseconds + profile.blendOutMs - life) / profile.blendOutMs : 1);
        double shrink = milliseconds > life - profile.widthShrinkMs
                ? 1 - Math.max(0, Math.min(1, (double) (milliseconds - (life - profile.widthShrinkMs)) / profile.widthShrinkMs)) : 1;
        double width = shrink + (profile.widthWiggles ? widthWiggle : 0);
        double glow = (1 - profile.wiggleRadius + glowWiggle) * alpha;
        return new Snapshot(length, width, alpha, glow, elapsedTicks >= profile.lifeTicks);
    }
}
