package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** プレイヤーのすべての押し続け技能のセッションをまとめて扱う: 原作ContextManager.disposePlayer。 */
@Mod.EventBusSubscriber(modid = "academy")
public final class SkillSessions {
    private SkillSessions() { }

    /**
     * プレイヤーが押し続けているものを終える。能力キーの切り替えがこれを行い、原作のServerManager.__onOverloadと
     * __onCategoryChangeと同じく、オーバーロードや新しいカテゴリもこれを行う。
     */
    public static void stopAll(ServerPlayer player) {
        // 1.4.0のS3から、振り分けの表の停止の順（1.3.0のこの一覧と同じ順。SkillDispatchGameTests）。
        io.github.pinchan4273.reacademycraft.ability.SkillDispatch.stopAll(player);
    }
    @SubscribeEvent public static void skillSessionsOnOverload(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload event) {
        if (event.player() instanceof ServerPlayer player) stopAll(player);
    }
    @SubscribeEvent public static void skillSessionsOnCategoryChange(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CategoryChange event) {
        if (event.player() instanceof ServerPlayer player) stopAll(player);
    }
}
