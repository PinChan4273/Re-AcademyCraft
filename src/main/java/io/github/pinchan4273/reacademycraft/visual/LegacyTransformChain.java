package io.github.pinchan4273.reacademycraft.visual;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * LambdaLib2のTransformChain（abb4b9f1）。原作のアイテムの描画処理は、手と地面の行列をこれで作る: 各段は前の段の後に適用する。
 * そのlwjglの行列はJOMLと同じく列を先にフィールドを命名するので、TransformUtilsの要素は書かれたとおりに移せる。
 */
public final class LegacyTransformChain {
    private final Matrix4f result;
    public LegacyTransformChain() { result = new Matrix4f(); }
    public LegacyTransformChain(Matrix4f from) { result = new Matrix4f(from); }
    public LegacyTransformChain translate(float x, float y, float z) { return then(new Matrix4f().m30(x).m31(y).m32(z)); }
    public LegacyTransformChain scale(float s) { return then(new Matrix4f().m00(s).m11(s).m22(s)); }
    public LegacyTransformChain scale(float x, float y, float z) { return then(new Matrix4f().m00(x).m11(y).m22(z)); }
    /** TransformUtils.rotateEuler。要素ごとに同じ。 */
    public LegacyTransformChain rotate(float x, float y, float z) {
        float a3 = x * Mth.DEG_TO_RAD, a2 = y * Mth.DEG_TO_RAD, a1 = z * Mth.DEG_TO_RAD;
        float c1 = Mth.cos(a1), s1 = Mth.sin(a1), c2 = Mth.cos(a2), s2 = Mth.sin(a2), c3 = Mth.cos(a3), s3 = Mth.sin(a3);
        return then(new Matrix4f()
                .m00(c1 * c3 - s1 * s2 * s3).m01(c3 * s1 + c1 * s2 * s3).m02(-c2 * s3)
                .m10(-c2 * s1).m11(c1 * c2).m12(s2)
                .m20(c1 * s3 + c3 * s1 * s2).m21(s1 * s3 - c1 * c3 * s2).m22(c2 * c3));
    }
    /** Matrix4f.mul(temp, result, result)。 */
    private LegacyTransformChain then(Matrix4f step) { result.mulLocal(step); return this; }
    public Matrix4f build() { return new Matrix4f(result); }
}
