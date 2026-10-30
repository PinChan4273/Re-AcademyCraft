package io.github.pinchan4273.reacademycraft.visual;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * 原作AcademyCraft（commit 7b1401c）が磁気フック自身のモデルを置く方法: 投げたフックはRendererMagHook、手に持ったものは
 * ItemMagHookのTEISRと、その手・地面の変換。ワールド・テクスチャ・GPUの状態は持たない。
 *
 * 原作はフックが飛んでいる間は閉じたモデルを、掴んだ後は開いたモデルを描く。モデルを入れ替えるだけで、原作に開くアニメーションは無い。
 */
public final class LegacyMagHookPose {
    /** 投げたフックに対するRendererMagHookの倍率と、手に持ったものに対するTEISRModelの倍率。 */
    public static final float ENTITY_SCALE = .0054f, ITEM_SCALE = .01f;
    private LegacyMagHookPose() { }

    /** EntityMagHook.preRender: 掴んだフックは当たった側面の外を向く。床や天井に当たったときは、原作と同じく飛んでいたyawを保つ。 */
    public static float[] orientation(boolean hit, Direction side, float yaw, float pitch) {
        if (!hit) return new float[] {yaw, pitch};
        return switch (side) {
            case DOWN -> new float[] {yaw, -90};
            case UP -> new float[] {yaw, 90};
            case NORTH -> new float[] {0, 0};
            case SOUTH -> new float[] {180, 0};
            case WEST -> new float[] {-90, 0};
            case EAST -> new float[] {90, 0};
        };
    }

    /**
     * ItemMagHook.onModelBakeの行列。表示ごとに1つ。原作が何も対応付けない所（単位行列）はnull。
     * GUIはここに無い: 原作はそこで平たいアイテムを表示し、アイテムモデルもそうする。
     */
    public static Matrix4f item(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_RIGHT_HAND -> firstPerson();
            case FIRST_PERSON_LEFT_HAND -> new LegacyTransformChain(firstPerson()).translate(1.4f, 0, 0).build();
            case THIRD_PERSON_RIGHT_HAND -> thirdPerson();
            case THIRD_PERSON_LEFT_HAND -> new LegacyTransformChain(thirdPerson()).translate(.9f, 0, 0).build();
            case GROUND -> new LegacyTransformChain().rotate(0, 90, 180).translate(-.4f, .9f, .7f).scale(.5f).build();
            default -> null;
        };
    }
    private static Matrix4f firstPerson() { return new LegacyTransformChain().scale(1.4f).rotate(0, 90, 180).translate(0, .5f, .4f).build(); }
    private static Matrix4f thirdPerson() { return new LegacyTransformChain().rotate(0, 90, 180).scale(.8f).translate(-.4f, .5f, .7f).build(); }

}
