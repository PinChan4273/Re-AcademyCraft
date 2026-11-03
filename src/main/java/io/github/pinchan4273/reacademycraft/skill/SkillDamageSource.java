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
    /**
     * v1.5.0の白翼刺突（採用仕様・版41の§4）の、熟練度50%未満の打撃。中身は{@link #TYPE}と同じ（防具は効く）で、
     * minecraft:bypasses_cooldownにだけ属する: 0.4〜0.6秒（8〜12tick）の連撃が標準の被ダメージ後の待ちで削られないようにする。
     */
    public static final ResourceKey<DamageType> STAB=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("academy","skill_stab"));
    /**
     * 白翼刺突の熟練度50%以上の打撃（§4・R08）。盾・防具・防具強度・防護エンチャント・耐性（バニラと技能の直接効果）を通らない
     * （minecraft:bypasses_armor・bypasses_shield・bypasses_effects・bypasses_enchantments・bypasses_resistance・bypasses_cooldown）。
     * 吸収とこのmodの防御（Light Shield、ベクトル操作の反射・偏向、Jetの前面防御）も{@link #ignoresDefenses}で通す。
     * bypasses_invulnerabilityには入れない: クリエイティブなどの本当の無敵、攻撃の可否（PvP・チームの保護）は先に効く。
     */
    public static final ResourceKey<DamageType> PIERCE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("academy","skill_pierce"));
    /** 防御・軽減を通さない打撃か（{@link #PIERCE}）。 */
    public static boolean ignoresDefenses(@javax.annotation.Nullable DamageSource source) { return source != null && source.is(PIERCE); }
    /** ベクトルの防御膜が反応しない打撃か: 上の防御無視と未元光線（§6）。未元空気砲は技能が段ごとに幕を精算するので、損傷の時には通す（§7）。 */
    public static boolean passesVectorMembrane(@javax.annotation.Nullable DamageSource source) {
        if (ignoresDefenses(source)) return true;
        return source instanceof SkillDamageSource s && (s.skill.equals(DarkMatter.RAY) || s.skill.equals(DarkMatter.AIR_CANNON));
    }
    private final ResourceLocation skill;
    public SkillDamageSource(ServerPlayer caster,ResourceLocation skill) { this(caster,skill,TYPE); }
    public SkillDamageSource(ServerPlayer caster,ResourceLocation skill,ResourceKey<DamageType> type) {
        super(caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),caster);
        this.skill=java.util.Objects.requireNonNull(skill);
    }
    /**
     * 外部の主体の技能のダメージ（1.4.0のS5）。directは直接の加害（主体の実体。無ければnull）、causingは帰属（確かめられた帰属先の
     * プレイヤー、無ければ主体の実体）。
     */
    public SkillDamageSource(net.minecraft.core.Holder<DamageType> type, @javax.annotation.Nullable net.minecraft.world.entity.Entity direct,
                             @javax.annotation.Nullable net.minecraft.world.entity.Entity causing, ResourceLocation skill) {
        super(type, direct, causing);
        this.skill = java.util.Objects.requireNonNull(skill);
    }
    public ResourceLocation skill(){return skill;}
    @Override public Component getLocalizedDeathMessage(LivingEntity victim){
        var definition=SkillCatalog.find(skill);var caster=getEntity()!=null?getEntity():getDirectEntity();
        return Component.translatable("death.attack.ac_skill",victim.getDisplayName(),caster==null?Component.literal("?"):caster.getDisplayName(),
                definition==null?Component.literal(skill.toString()):Component.translatable(definition.translation()));
    }
}
