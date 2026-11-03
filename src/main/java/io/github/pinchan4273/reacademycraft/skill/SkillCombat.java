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
        // §9: 自分の甲虫は、自分のどの技能の対象にもならない（照準も遮らない）。
        if (target instanceof io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle beetle && beetle.isOwnedBy(caster)) return false;
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
    /**
     * v1.5.0 白翼刺突（§4・R08）。熟練度50%未満は{@link SkillDamageSource#STAB}（被ダメージ後の待ちだけを飛ばす）。50%以上は
     * {@link SkillDamageSource#PIERCE}: 種類のタグで盾・防具・防護エンチャント・耐性・効果を、各技能の防御のリスナーが
     * {@link SkillDamageSource#ignoresDefenses}で自分を飛ばし、吸収はこの1撃の間だけ0にして体へ通す（後で戻す）。攻撃できるかの判定
     * （{@link #mayAttack}）はどちらでも先に行う。
     */
    public static boolean attackStab(ServerPlayer caster, Entity target, ResourceLocation skill, float damage, boolean pierce) {
        if (!pierce) return hurt(caster, target, skill, damage, SkillDamageSource.STAB);
        var living = target instanceof net.minecraft.world.entity.LivingEntity l ? l : null;
        float absorption = living == null ? 0 : living.getAbsorptionAmount();
        if (absorption > 0) living.setAbsorptionAmount(0);
        try {
            return hurt(caster, target, skill, damage, SkillDamageSource.PIERCE);
        } finally {
            if (absorption > 0 && living.isAlive()) living.setAbsorptionAmount(absorption);
        }
    }
    /**
     * §9: 甲虫の攻撃。方針は{@link #attackContact}と同じ（3体が同じ敵へ当てても標準の被ダメージ後の待ちで消えない。防具は効く）で、
     * 直接の加害を甲虫にする（盾の向きなどは甲虫の位置で決まる）。帰属は所有者。
     */
    public static boolean attackVia(ServerPlayer caster, Entity direct, Entity target, ResourceLocation skill, float damage) {
        var calc = new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillAttack(caster, skill, target, damage);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(calc);
        float scaled = (float) (calc.value * AcademyConfig.DAMAGE_SCALE.get() * AcademyConfig.skillDamageScale(skill));
        var type = caster.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(SkillDamageSource.CONTACT);
        return Float.isFinite(scaled) && scaled > 0 && mayAttack(caster, target, skill)
                && target.hurt(new SkillDamageSource(type, direct, caster, skill), scaled);
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
