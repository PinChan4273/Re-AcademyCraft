package io.github.pinchan4273.reacademycraft.visual;

import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/**
 * ItemDeveloperのTEISRの配置（AcademyCraft commit 7b1401c）: 独自の行列を持たないdeveloper_portable.objを、onModelBakeの表示の行列の下で描く。
 * GUIは、充電量に応じた平たいアイコンのまま。
 */
public final class LegacyPortableDeveloperPose {
    private LegacyPortableDeveloperPose() { }
    /** 原作が行列を対応付けない所（単位行列）はnull。 */
    public static Matrix4f item(ItemDisplayContext context) {
        return switch (context) {
            // 原作は一人称でも三人称でも、両手に同じ行列を対応付ける。
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND ->
                    new LegacyTransformChain().rotate(0, 180, 0).scale(.3f).translate(.34f, -.1f, -.1f).build();
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> new LegacyTransformChain().rotate(0, 180, 0).scale(.2f).build();
            case GROUND -> new LegacyTransformChain().scale(-.15f, -.15f, .15f).translate(0, .1f, 0).build();
            default -> null;
        };
    }
}
