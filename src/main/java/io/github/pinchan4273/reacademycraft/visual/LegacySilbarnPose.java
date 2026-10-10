package io.github.pinchan4273.reacademycraft.visual;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 原作AcademyCraft（commit 7b1401c）がSilbarn自身のモデルを置く方法: 投げたものはEntitySilbarn.RenderSibarnで、独自の軸の周りに
 * 転がる。手に持ったものはItemSilbarnのTEISRと、その手・地面の変換。ワールド・テクスチャ・GPUの状態は持たない。
 */
public final class LegacySilbarnPose {
    /** RenderSibarnの倍率と回転: エンティティの生成から1ミリ秒あたり0.03度。 */
    public static final float ENTITY_SCALE = .05f, DEGREES_PER_MILLI = .03f;
    private LegacySilbarnPose() { }

    /**
     * 原作の軸は、エンティティ自身の乱数からのnew Vec3d(rand.nextInt(), rand.nextInt(), rand.nextInt())で、glRotatedが正規化する。
     * ここではエンティティのidから引くので、Silbarnごとに固定になる。
     */
    public static Vector3f axis(int entityId) {
        var random = RandomSource.create(entityId);
        var axis = new Vector3f(random.nextInt(), random.nextInt(), random.nextInt());
        return axis.lengthSquared() == 0 ? new Vector3f(0, 1, 0) : axis.normalize();
    }

    /** TEISRModel自身の行列。すべての表示の行列の下で適用する: 0.0625倍、次にrotate(90, 0, 0)。 */
    public static Matrix4f base() { return new LegacyTransformChain().scale(.0625f).rotate(90, 0, 0).build(); }

    /** ItemSilbarn.onModelBakeの行列。表示ごとに1つ。原作が何も対応付けない所はnull。GUIは原作と同じく平たいアイテムのまま。 */
    public static Matrix4f item(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_RIGHT_HAND -> firstPerson();
            case FIRST_PERSON_LEFT_HAND -> new LegacyTransformChain(firstPerson()).translate(0, -1, 0).build();
            case THIRD_PERSON_RIGHT_HAND, GROUND -> thirdPerson();
            case THIRD_PERSON_LEFT_HAND -> new LegacyTransformChain(thirdPerson()).translate(0, 0, .5f).build();
            default -> null;
        };
    }
    private static Matrix4f firstPerson() { return new LegacyTransformChain().rotate(0, 90, 90).translate(1, .5f, .2f).build(); }
    private static Matrix4f thirdPerson() { return new LegacyTransformChain().rotate(90, 0, 90).scale(.6f).translate(-.3f, .3f, -.3f).build(); }
}
