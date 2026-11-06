package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.ability.SkillDispatch;
import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * v1.5.0 §14 CPセーフティの作動（全系統共通。Forge v1.5.0のskill/CpSafetyと同じ意味）。支払いの判定は{@link PlayerAbilityData}が行い、
 * 作動したら次のtickの終わりにここが消費技能をまとめて止める。同じtickに成立した支払いと効果はそのまま確定する。
 * Forgeの{@code TickEvent.PlayerTickEvent}の{@code Phase.END}は、NeoForgeの{@link PlayerTickEvent.Post}。
 */
@EventBusSubscriber(modid = "academy")
public final class CpSafety {
    private CpSafety() { }

    /** テスト用の入口も兼ねる。 */
    public static void tick(ServerPlayer player) {
        var data = AbilityCapabilities.of(player).orElse(null);
        if (data != null && data.takeSafetyTrip()) SkillDispatch.stopForSafety(player);
    }
    @SubscribeEvent public static void cpSafetyPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
}
