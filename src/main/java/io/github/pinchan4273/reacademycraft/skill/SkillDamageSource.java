package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 原作SkillDamageSourceに対する、Forge 1.20.1の動的なダメージ型のアダプタ。
 * 術者の帰属、防具とForgeのダメージイベント、被害者・術者・技能の死亡文を保つ。
 */
public final class SkillDamageSource extends DamageSource {
    public static final ResourceKey<DamageType> TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("academy","skill"));
    /**
     * 原作SkillDamageSource.setDamageBypassesArmor(): isUnblockableで、1.12では防具と盾を飛ばし、空腹のコストも無い。
     * 1.20.1ではこれがbypasses_armorとbypasses_shieldのタグに分かれ、この型はその両方に属し、exhaustionは0。
     * エンチャントの防護とResistanceは、1.12の防げないダメージ源と同じく引き続き効く。
     */
    public static final ResourceKey<DamageType> IGNORE_ARMOR=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("academy","skill_ignore_armor"));
    /**
     * Light ShieldとJet Engineの接触攻撃だけの型（2026-10-16、統合指示書4章）。中身は{@link #TYPE}と同じ（防具は効く）で、
     * minecraft:bypasses_cooldownのタグにだけ属する: 同じ対象への8tickごとの命中が、標準の被ダメージ後の待ち（invulnerableTime &gt; 10）で
     * 削られないようにする。bypasses_invulnerabilityには入れないので、クリエイティブなどの本当の無敵、Forgeの攻撃イベントの取り消し、
     * 防具・Resistance・エンチャントはそのまま効く。共通の{@link #TYPE}には何も足さない（他の技能と通常の武器は従来どおり）。
     */
    public static final ResourceKey<DamageType> CONTACT=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("academy","skill_contact"));
    private final ResourceLocation skill;
    public SkillDamageSource(ServerPlayer caster,ResourceLocation skill) { this(caster,skill,TYPE); }
    public SkillDamageSource(ServerPlayer caster,ResourceLocation skill,ResourceKey<DamageType> type) {
        super(caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),caster);
        this.skill=java.util.Objects.requireNonNull(skill);
    }
    public ResourceLocation skill(){return skill;}
    @Override public Component getLocalizedDeathMessage(LivingEntity victim){
        var definition=SkillCatalog.find(skill);var caster=getEntity();
        return Component.translatable("death.attack.ac_skill",victim.getDisplayName(),caster==null?Component.literal("?"):caster.getDisplayName(),
                definition==null?Component.literal(skill.toString()):Component.translatable(definition.translation()));
    }
}
