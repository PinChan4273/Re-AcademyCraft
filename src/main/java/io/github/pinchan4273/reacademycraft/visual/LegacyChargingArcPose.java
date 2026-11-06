package io.github.pinchan4273.reacademycraft.visual;

/**
 * WeAthFolDのEntityArc.setFromTo（AcademyCraft 7b1401c）からのCPUでの向きに、公開されたLambdaLib2のViewOptimize（abb4b9f）の
 * 手のずれを加えたもの。そのヘルパーの版がLambdaLib 0.2.0とバイト単位で同じとは証明されていない。
 * ここではカメラ・エンティティ・パケット・エネルギーの対象・描画処理を選ばない。
 */
public final class LegacyChargingArcPose {
    public record Orientation(double length, float yawDegrees, float pitchDegrees) { }
    public record HandOffset(double x, double y, double z) { }
    private static final HandOffset FIRST_PERSON = new HandOffset(-.05, -.25, .2);
    private static final HandOffset THIRD_PERSON = new HandOffset(.15, -.8, .23);

    private LegacyChargingArcPose() { }

    /**
     * 始点から終点への相対的な差で、ワールドの絶対座標や能力の射程の許可ではない。メッシュの有限の0〜32の表示範囲に合わせる。
     * 平行移動、正のY軸周りに-(yaw+90)の回転、正のZ軸周りに-pitchの回転、次にローカルの手のずれを適用する。テンプレートは+Xへ伸びる。
     */
    public static Orientation orientation(double dx, double dy, double dz) {
        double length = Math.hypot(Math.hypot(dx, dz), dy);
        if (!Double.isFinite(length) || length > 32)
            throw new IllegalArgumentException("Arc presentation length outside0..32");
        return direction(dx, dy, dz);
    }

    /**
     * メッシュの範囲制限の無い同じ向き。原作自身のEntityRayBase.setFromToにはそのような制限が無く（32で止まるのは溜めの電弧の
     * メッシュ）、光線はふつうもっと長い: Railgunのものは45。光線に電弧の範囲制限を適用するとここで例外になり、光線が作られず、
     * Railgunが何も描かなかった。
     */
    public static Orientation rayOrientation(double dx, double dy, double dz) {
        double length = Math.hypot(Math.hypot(dx, dz), dy);
        if (!Double.isFinite(length)) throw new IllegalArgumentException("Non-finite ray length");
        return direction(dx, dy, dz);
    }

    private static Orientation direction(double dx, double dy, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz))
            throw new IllegalArgumentException("Non-finite arc direction");
        double horizontal = Math.hypot(dx, dz), length = Math.hypot(horizontal, dy);
        // 一致した場合の正規の有限の向き。メッシュの原作の、0での区間開始を含む切り捨てを変えたり、ノイズを拡大縮小したりしない。
        if (length == 0) return new Orientation(0, 0, 0);
        return new Orientation(length, (float) Math.toDegrees(-Math.atan2(dx, dz)),
                (float) Math.toDegrees(-Math.atan2(dy, horizontal)));
    }

    /** 観測者は、自身のカメラが一人称でも三人称のずれを使う。 */
    public static HandOffset handOffset(boolean firstPersonView, boolean localSource) {
        return firstPersonView && localSource ? FIRST_PERSON : THIRD_PERSON;
    }

    /**
     * 原作RendererRayBaseSimpleとRendererRayBaseGlowは、光線の終点ではなく始点を、Vec3d.rotateYaw((270 - yaw)をラジアンで)で回した
     * 手のずれだけ動かす。yawは光線自身のもの。ワールド空間のずれx、y、zを返す。
     */
    public static double[] rayStartShift(HandOffset hand, float yawDegrees) {
        double angle = Math.toRadians(270 - yawDegrees), c = Math.cos(angle), s = Math.sin(angle);
        return new double[] {hand.x() * c + hand.z() * s, hand.y(), hand.z() * c - hand.x() * s};
    }
}
