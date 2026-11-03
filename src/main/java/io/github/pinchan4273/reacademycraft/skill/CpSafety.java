package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.ability.SkillDispatch;
import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v1.5.0 §14 CPセーフティの作動（全系統共通）。支払いの判定は{@link PlayerAbilityData}が行い、作動したら次のtickの終わりにここが消費技能をまとめて止める。
 * 同じtickに成立した支払いと効果はそのまま確定する。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class CpSafety {
    private CpSafety() { }

    /** テスト用の入口も兼ねる。 */
    public static void tick(ServerPlayer player) {
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data != null && data.takeSafetyTrip()) SkillDispatch.stopForSafety(player);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
}
