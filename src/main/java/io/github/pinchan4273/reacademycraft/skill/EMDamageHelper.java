package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;

/**
 * 原作EMDamageHelperの30%の帯電クリーパー化。ArcとThunderBoltだけが共有する。
 * リフレクションや落雷の代わりに、ForgeのATとバニラの同期データを使う。
 * 拒否されたダメージを尊重し、死んだエンティティは変化させない。
 */
public final class EMDamageHelper {
    private EMDamageHelper() { }
    public static boolean attack(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        boolean hurt = SkillCombat.attack(caster, target, skill, damage);
        if (hurt && target instanceof Creeper creeper && creeper.isAlive() && !creeper.isPowered()
                && caster.getRandom().nextFloat() < .3f)
            creeper.getEntityData().set(Creeper.DATA_IS_POWERED, true);
        return hurt;
    }
}
