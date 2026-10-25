package io.github.pinchan4273.reacademycraft.api;

import javax.annotation.Nullable;

/**
 * 開発機の技能の木の中の場所（公開API、実験的）。木の領域は257×139で、技能の印は16×16なので、xは0〜241、yは0〜123。
 * parentは線を引く元の技能（無ければnull）。線は、その技能の前提の1つから引く（前提でない技能からは引かない）。
 */
public record TreePosition(float x, float y, @Nullable AbilityId parent) {
    public static final float MAX_X = 257 - 16, MAX_Y = 139 - 16;

    public TreePosition {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0 || x > MAX_X || y > MAX_Y)
            throw new IllegalArgumentException("A tree position must be inside the tree area: " + x + ", " + y);
    }
}
