package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;

/**
 * 移植したダメージを与えるすべての技能が共有する、原作AbilityContextの攻撃の方針。
 * 防具・帰属・Forgeのイベントは、通常のダメージ処理が引き続き担う。
 */
public final class SkillCombat {
    private SkillCombat() { }
    public static boolean mayAttack(ServerPlayer caster, Entity target, ResourceLocation skill) {
        if (!target.isAlive() || target.isSpectator() || target.level() != caster.level()) return false;
        if (target instanceof Player other && (!AcademyConfig.ATTACK_PLAYERS.get()
                || !caster.serverLevel().getServer().isPvpAllowed() || !caster.canHarmPlayer(other))) return false;
        return !(target instanceof Painting || target instanceof ItemFrame) || AcademyConfig.canDestroy(caster.level(), skill);
    }
    public static boolean attack(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.TYPE);
    }
    /**
     * Light ShieldとJet Engineの接触攻撃（2026-10-16、統合指示書4章）: attackと同じ方針（SkillAttackのイベント、設定の倍率、mayAttackの
     * PvPと攻撃の権限、術者の帰属、死亡文）を、標準の被ダメージ後の待ちだけを飛ばす{@link SkillDamageSource#CONTACT}で。
     * 同じ対象への間隔（8tick）は、呼ぶ側の技能が守る。
     */
    public static boolean attackContact(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.CONTACT);
    }
    /** 原作AbilityContext.attackIgnoreArmor: 同じ方針を、防具を無視するダメージ源で。 */
    public static boolean attackIgnoreArmor(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.IGNORE_ARMOR);
    }
    private static boolean hurt(ServerPlayer caster, Entity target, ResourceLocation skill, float damage,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
        // 原作CalcEvent.SkillAttack: リスナーは設定の倍率の前にダメージを変更できる。
        var calc = new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillAttack(caster, skill, target, damage);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(calc);
        // 原作calc_global.damage_scaleと、技能自身のdamage_scale。
        float scaled = (float) (calc.value * AcademyConfig.DAMAGE_SCALE.get() * AcademyConfig.skillDamageScale(skill));
        return Float.isFinite(scaled) && scaled > 0 && mayAttack(caster, target, skill)
                && target.hurt(new SkillDamageSource(caster, skill, type), scaled);
    }
}
