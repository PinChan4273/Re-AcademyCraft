package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.LearnResult;
import net.minecraft.server.level.ServerPlayer;

/**
 * 外部の技能の習得（公開API、実験的。サーバーのスレッドでだけ呼ぶ）。アドオンは、開発機を通す経路
 * （技能の木に置いた技能は、本体の開発機で学べる）と、自分の条件で与えるこの経路の両方を使える。
 * 習得はサーバーが確かめて行い、結果をクライアントへ送る。
 */
public final class AbilityLearning {
    private AbilityLearning() { }

    /**
     * 本体の普通の条件（設定で有効・カテゴリ・レベル・前提の技能と熟練度・数の上限）で、今その技能を学べるか。
     * 開発機の段の条件は含まない。
     */
    public static boolean canLearn(ServerPlayer player, AbilityId skill) {
        return io.github.pinchan4273.reacademycraft.ability.AddonLearning.canLearn(player, skill);
    }

    /**
     * アドオン自身の条件で与える。Re:AcademyCraftが確かめるのは、外部の技能であること・設定で有効・カテゴリが提供すること・
     * データが書き込めること・数の上限だけ（レベルと前提は確かめない。アドオンの条件として扱う）。
     *
     * @throws IllegalStateException サーバーのスレッドの外から呼んだとき
     */
    public static LearnResult grant(ServerPlayer player, AbilityId skill) {
        return io.github.pinchan4273.reacademycraft.ability.AddonLearning.grant(player, skill);
    }
}
