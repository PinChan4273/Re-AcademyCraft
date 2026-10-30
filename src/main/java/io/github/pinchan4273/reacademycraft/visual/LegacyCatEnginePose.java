package io.github.pinchan4273.reacademycraft.visual;

/**
 * 原作RenderCatEngine（AcademyCraft commit 7b1401c）: 無限発電機は立方体ではなく、見る人の方を向いた猫の1枚の絵で、少し上下し、
 * 引き出されている速さに応じて水平軸の周りに回る。ワールド・テクスチャ・GPUの状態は持たない。
 */
public final class LegacyCatEnginePose {
    /** TileCatEngine.getGenerationの上限（1tickあたりのIF）と、移植版のIFに対するFE。 */
    public static final double MAX_GENERATION_IF = 500, FE_PER_IF = 4;
    private LegacyCatEnginePose() { }

    /** thisTickGen: 1tickの間に引き出された量（IF）。原作の500を超えない。 */
    public static double generation(long drawnFe) { return Math.min(MAX_GENERATION_IF, Math.max(0, drawnFe) / FE_PER_IF); }

    /** rotation += (time - lastRender) * thisTickGen * 1e-2。度で、間隔はミリ秒。[0, 360)に保つ。 */
    public static double spin(double rotation, long elapsedMillis, double generationIf) {
        double next = (rotation + Math.max(0, elapsedMillis) * generationIf * 1e-2) % 360;
        return next < 0 ? next + 360 : next;
    }

    /** 秒単位のGameTimer.getTime()に対する0.03の上下動。 */
    public static double bob(double seconds) { return .03 * Math.sin(seconds * .006); }

    /** yaw = カメラから見たブロックの中央のatan2(x, z)。絵はyaw + 180だけ回る。 */
    public static double facing(double dx, double dz) { return Math.toDegrees(Math.atan2(dx, dz)) + 180; }
}
