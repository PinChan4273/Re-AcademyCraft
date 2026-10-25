package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.LearnResult;
import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import net.minecraft.server.level.ServerPlayer;

/** 外部の技能の習得の公開APIの実装（1.4.0のS4、内部の実装）。 */
public final class AddonLearning {
    private AddonLearning() { }

    private static PlayerAbilityData data(ServerPlayer player) {
        return player.getCapability(AbilityCapabilities.PLAYER_ABILITY).resolve().orElse(null);
    }
    private static void requireServerThread(ServerPlayer player) {
        if (player.getServer() != null && !player.getServer().isSameThread())
            throw new IllegalStateException("Ability learning must run on the server thread");
    }

    public static boolean canLearn(ServerPlayer player, AbilityId skill) {
        requireServerThread(player);
        var definition = SkillCatalog.find(RegistryViews.location(skill));
        var data = data(player);
        return definition != null && data != null && definition.canLearn(data);
    }

    public static LearnResult grant(ServerPlayer player, AbilityId skill) {
        requireServerThread(player);
        if (skill.isAcademy()) return LearnResult.REFUSED_BUILT_IN;
        var location = RegistryViews.location(skill);
        var definition = SkillCatalog.find(location);
        if (definition == null) return LearnResult.REFUSED_UNKNOWN;
        var data = data(player);
        if (data == null || data.isReadOnly()) return LearnResult.REFUSED_READ_ONLY;
        if (!data.hasAbility() || !definition.offeredTo(data.getAbility())) return LearnResult.REFUSED_CATEGORY;
        if (data.hasLearned(location)) return LearnResult.ALREADY_LEARNED;
        if (!io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillEnabled(location)) return LearnResult.REFUSED_DISABLED;
        if (data.learnedSkills().size() >= PlayerAbilityData.MAX_SKILLS) return LearnResult.REFUSED_FULL;
        data.learn(location);
        io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents.sync(player, true);
        return LearnResult.LEARNED;
    }
}
