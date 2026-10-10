package io.github.pinchan4273.reacademycraft.visual;

import java.util.Random;

/** 固定した原作のコインの描画処理から取った、Minecraftに依存しない定数とアニメーション。 */
public final class LegacyCoinPresentation {
    public static final float ENTITY_SCALE = .3f;
    public static final float ENTITY_HALF_THICKNESS = .0625f;
    public static final float ITEM_HALF_THICKNESS = .04f;
    public static final float OFFSET_X = -.63f;
    public static final float OFFSET_Y = 1f;
    public static final float OFFSET_Z = .30f;
    public static final int TEXTURE_SIZE = 32;
    public static final ItemTransform FIRST_PERSON = new ItemTransform(.5f, .5f, .5f, .2f, 0, -.1f);
    public static final ItemTransform THIRD_PERSON = new ItemTransform(.2f, .2f, .2f, 0, 0, 0);
    public static final ItemTransform GROUND = new ItemTransform(-.3f, -.3f, .3f, 0, .1f, 0);
    /** 原作のXY平面のTEISRの面を表示するのに必要な、現行のbake済みモデルの向き。 */
    public static final ItemRotation FIRST_PERSON_RIGHT_ROTATION = new ItemRotation(0, -90, 25);
    public static final ItemRotation FIRST_PERSON_LEFT_ROTATION = new ItemRotation(0, 90, -25);
    /**
     * 三人称の手のコインの向き。原作のTEISRはコインを自身の四角形の空間で描いていたので、1.20.1の三人称の腕の変換の下で
     * コインの面が見えるよう回す。
     */
    public static final ItemRotation THIRD_PERSON_RIGHT_ROTATION = new ItemRotation(0, -90, 55);
    public static final ItemRotation THIRD_PERSON_LEFT_ROTATION = new ItemRotation(0, 90, -55);
    /**
     * 現行の拳からの離れ（ブロック単位）。三人称の手にだけ使う。
     * 原作のTEISRはコインを自身の四角形の空間で描き、1.20.1の手のずれを表さなかったので、固定した三人称の倍率.2だけでは、
     * .2ブロックのコインが.25ブロックの腕の内側に入り、完全に隠れる。これは互換用の回転と同じく、座標系のアダプタの役割。
     *
     * バニラの三人称の腕の経路はbake済みのRx(-90)/Ry(180)の補正を持つので、単純な「上」への大きな平行移動は、拳から持ち上げる
     * のではなくコインを手から遠くへ飛ばす変位として解釈される。そのため、手の近くに留まりつつ見える小さな値にしている。
     */
    public static final ItemTranslation THIRD_PERSON_CLEARANCE = new ItemTranslation(0, .12f, .05f);

    private LegacyCoinPresentation() { }

    /** 原作のGameTimerの式: {@code (milliseconds % 150) * 360 / 300}。 */
    public static float angleDegrees(float ageTicks) {
        if (!Float.isFinite(ageTicks) || ageTicks < 0) throw new IllegalArgumentException("Invalid coin age");
        return (float) (((ageTicks * 50.0) % 150.0) * 360.0 / 300.0);
    }

    /** 原作のクライアントごとのランダムな軸の代わりの、観測者に依存しない安定した軸。 */
    public static SpinAxis axis(long seed) {
        var random = new Random(seed);
        double x = .1 + random.nextDouble();
        double y = random.nextDouble();
        double z = random.nextDouble();
        double length = Math.sqrt(x * x + y * y + z * z);
        return new SpinAxis((float) (x / length), (float) (y / length), (float) (z / length));
    }

    public record SpinAxis(float x, float y, float z) {
        public SpinAxis {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || x <= 0 || y < 0 || z < 0 || Math.abs(x * x + y * y + z * z - 1) > .00001f)
                throw new IllegalArgumentException("Invalid coin axis");
        }
    }

    /** 1つの表示の文脈について、原作のコインのTEISRが対応付ける正確な拡大縮小・平行移動の連鎖。 */
    public record ItemTransform(float scaleX, float scaleY, float scaleZ,
                                float translateX, float translateY, float translateZ) {
        public ItemTransform {
            if (!Float.isFinite(scaleX) || !Float.isFinite(scaleY) || !Float.isFinite(scaleZ)
                    || !Float.isFinite(translateX) || !Float.isFinite(translateY) || !Float.isFinite(translateZ)
                    || scaleX == 0 || scaleY == 0 || scaleZ == 0)
                throw new IllegalArgumentException("Invalid coin item transform");
        }
    }

    public record ItemRotation(float x, float y, float z) {
        public ItemRotation {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || Math.abs(x) > 180 || Math.abs(y) > 180 || Math.abs(z) > 180)
                throw new IllegalArgumentException("Invalid coin item rotation");
        }
    }

    /** ブロック空間の平行移動。Minecraftは16分の1の形式を5ブロックで切り詰める。 */
    public record ItemTranslation(float x, float y, float z) {
        public ItemTranslation {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || Math.abs(x) > 5 || Math.abs(y) > 5 || Math.abs(z) > 5)
                throw new IllegalArgumentException("Invalid coin item translation");
        }

        /** 同じ値を、アイテムモデルのJSONが保存する16分の1の単位で表したもの。 */
        public float[] sixteenths() {
            return new float[] {x * 16, y * 16, z * 16};
        }
    }
}
