package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 原作MDDamageHelper: meltdownerのすべての技能はここを通してダメージを与える。Radiation Intensifyを習得した術者が当てた相手を
 * 被曝させるため。電撃使いの技能が帯電クリーパー化のためにEMDamageHelperを通すのと同じ。
 */
public final class MDDamageHelper {
    private MDDamageHelper() { }
    public static boolean attack(ServerPlayer caster, PlayerAbilityData data, Entity target,
                                 ResourceLocation skill, float damage) {
        boolean hurt = SkillCombat.attack(caster, target, skill, damage);
        // 原作MDDamageHelper.attackは何も返さないctx.attackの後で印を付ける: ダメージが通ったかどうかに関係なく印は残る。
        RadiationIntensify.mark(data, target);
        return hurt;
    }
}
