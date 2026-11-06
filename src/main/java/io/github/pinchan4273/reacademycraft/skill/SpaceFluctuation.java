package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.Identifier;

/**
 * 原作SpaceFluctuation（WeAthFolD）: テレポーターのレベル4の受動技能。原作でもダミーの置き場で、TPSkillHelperがクリティカルの
 * 3つの段すべてでこれを読む。
 *
 * カタログ上の前提条件はShift Teleport。原作と同じ方法でも習得できる: テレポーターのクリティカルはどれもこれに経験値を与え、
 * 原作のaddSkillExpは術者のレベルに関係なく、経験値を与えた技能を習得させる。
 */
public final class SpaceFluctuation {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("academy", "space_fluct");
    /** 原作CatTeleporter: spaceFluct.setParent(shiftTP, 0)。 */
    public static final Identifier PARENT = ShiftTeleport.ID;
    private SpaceFluctuation() { }
}
