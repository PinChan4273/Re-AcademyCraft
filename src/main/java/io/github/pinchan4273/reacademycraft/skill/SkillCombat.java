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
 * 1.5.2から、ダメージの種類は当たり方（{@link SkillDamageSource.Style}）と技能のカテゴリで選ぶ（{@link SkillDamageSource#typeKey}）。
 */
public final class SkillCombat {
    private SkillCombat() { }
    public static boolean mayAttack(ServerPlayer caster, Entity target, ResourceLocation skill) {
        if (!target.isAlive() || target.isSpectator() || target.level() != caster.level()) return false;
        // 自分の甲虫は、自分のどの技能の対象にもならない（照準も遮らない）。
        if (target instanceof io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle beetle && beetle.isOwnedBy(caster)) return false;
        if (target instanceof Player other && (!AcademyConfig.ATTACK_PLAYERS.get()
                || !caster.serverLevel().getServer().isPvpAllowed() || !caster.canHarmPlayer(other))) return false;
        return !(target instanceof Painting || target instanceof ItemFrame) || AcademyConfig.canDestroy(caster.level(), skill);
    }
    public static boolean attack(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.Style.PLAIN);
    }
    /**
     * 1.5.2: {@link #attack}と同じ方針（補正のイベント、設定の倍率、{@link #mayAttack}、術者の帰属）で、当たり方を指定して当てる。
     * 反射で折り返した光線・打ち返しが、元の攻撃の当たり方（貫通）を引き継ぐため。いつも貫通の技能は、頼んだ当たり方に関係なく貫通。
     */
    public static boolean attack(ServerPlayer caster, Entity target, ResourceLocation skill, float damage, SkillDamageSource.Style style) {
        return attack(caster, target, skill, damage, style, null);
    }
    /**
     * 1.5.2: {@link #attack(ServerPlayer, Entity, ResourceLocation, float, SkillDamageSource.Style)}に、元の打撃の貫通の段（{@code inherited}）を足したもの。
     * 反射で折り返した光線と貫通の近接への打ち返しは、技能がベクトル反射になっても、元の打撃の段で当てる。null なら技能の段。
     */
    public static boolean attack(ServerPlayer caster, Entity target, ResourceLocation skill, float damage, SkillDamageSource.Style style,
                                 @javax.annotation.Nullable SkillDamageSource.PierceTier inherited) {
        return hurtAdjusted(caster, target, skill, adjust(caster, target, skill, damage), style, inherited);
    }
    /**
     * Light ShieldとJet Engineの接触攻撃: attackと同じ方針（SkillAttackのイベント、設定の倍率、mayAttackの
     * PvPと攻撃の権限、術者の帰属、死亡文）を、標準の被ダメージ後の待ちだけを飛ばす{@link SkillDamageSource.Style#CONTACT}で。
     * 同じ対象への間隔（8tick）は、呼ぶ側の技能が守る。
     */
    public static boolean attackContact(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.Style.CONTACT);
    }
    /**
     * v1.5.0 白翼刺突。1.5.2から、熟練度に関係なくいつも{@link SkillDamageSource.Style#PIERCE}で当てる
     * （白翼刺突は{@link SkillDamageSource#PIERCING_SKILLS}にあり、{@link #hurtAdjusted}が貫通にする）。「ブロックを通り抜けて届く」は熟練度50%以上だけで、
     * 呼ぶ側（{@link WhiteWingStab#hit}）が決める。攻撃できるかの判定（{@link #mayAttack}）は先に行う。
     */
    public static boolean attackStab(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.Style.STAB);
    }
    /**
     * 甲虫の攻撃。方針は{@link #attackContact}と同じ（3体が同じ敵へ当てても標準の被ダメージ後の待ちで消えない。防具は効く）で、
     * 直接の加害を甲虫にする（盾の向きなどは甲虫の位置で決まる）。帰属は所有者。
     */
    public static boolean attackVia(ServerPlayer caster, Entity direct, Entity target, ResourceLocation skill, float damage) {
        return attackVia(caster, direct, target, skill, damage, false);
    }
    /**
     * {@link #attackVia(ServerPlayer, Entity, Entity, ResourceLocation, float)}に、未元物質に直接触れる攻撃のフラグ（1.5.2、
     * {@link SkillDamageSource#markDirectContact}）を付けるかを足したもの。甲虫の近接と突進は付け、空気弾は付けない。
     * 1.5.2: フラグを付ける攻撃（甲虫の近接と突進）は、いつも{@link SkillDamageSource.Style#PIERCE}で当てる（{@link PierceHits}が他の Mod の守りを外す。
     * Re 自身の防御は効く）。空気弾は今までどおり接触の当たり方。
     */
    public static boolean attackVia(ServerPlayer caster, Entity direct, Entity target, ResourceLocation skill, float damage, boolean directContact) {
        var calc = new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillAttack(caster, skill, target, damage);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(calc);
        float scaled = (float) (calc.value * AcademyConfig.DAMAGE_SCALE.get() * AcademyConfig.skillDamageScale(skill));
        var style = directContact ? SkillDamageSource.Style.PIERCE : SkillDamageSource.styleFor(skill, SkillDamageSource.Style.CONTACT);
        var type = SkillDamageSource.type(caster.level().registryAccess(), skill, style);
        if (!(Float.isFinite(scaled) && scaled > 0 && mayAttack(caster, target, skill))) return false;
        var source = new SkillDamageSource(type, direct, caster, skill);
        if (directContact) source.markDirectContact();
        return PierceHits.hurt(target, source, scaled);
    }
    /** 原作AbilityContext.attackIgnoreArmor: 同じ方針を、防具を無視するダメージ源で。 */
    public static boolean attackIgnoreArmor(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        return hurt(caster, target, skill, damage, SkillDamageSource.Style.IGNORE_ARMOR);
    }
    private static boolean hurt(ServerPlayer caster, Entity target, ResourceLocation skill, float damage,
                                SkillDamageSource.Style style) {
        return hurtAdjusted(caster, target, skill, adjust(caster, target, skill, damage), style, null);
    }
    /**
     * 原作CalcEvent.SkillAttack: リスナーは設定の倍率の前にダメージを変更できる。そのイベントを1回通した後の量を返す（設定の倍率は掛けない）。
     * 1.5.2: 光線は、反射の判定（{@code SkillReflectEvent}）の前にここを通し、反射の値段もこの量で決める。
     */
    public static float adjust(ServerPlayer caster, Entity target, ResourceLocation skill, float damage) {
        var calc = new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillAttack(caster, skill, target, damage);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(calc);
        return calc.value;
    }
    /**
     * {@link #adjust}を通した後の量で、{@link #attack}と同じ方針で当てる（補正のイベントをもう一度は通さない）。光線が、反射の判定に使った
     * 量と同じ量で当てるため。
     */
    public static boolean attackAdjusted(ServerPlayer caster, Entity target, ResourceLocation skill, float adjusted) {
        return hurtAdjusted(caster, target, skill, adjusted, SkillDamageSource.Style.PLAIN, null);
    }
    /**
     * 1.5.2: いつも貫通の技能（{@link SkillDamageSource#PIERCING_SKILLS}）は、頼まれた当たり方に関係なく{@link SkillDamageSource.Style#PIERCE}で当てる。
     * 貫通の打撃は{@link PierceHits#hurt}を通し、吸収と他の Mod の守りを外す。攻撃できるか（PvP・チーム・Re の設定・傍観者）は先に{@link #mayAttack}で確かめ、
     * クリエイティブなどの本当の無敵と{@code isInvulnerableTo}には当てない。
     * 1.5.2: {@code inherited}があれば、ダメージの元にその貫通の段を持たせる（{@link SkillDamageSource#inheritPierceTier}）。
     */
    private static boolean hurtAdjusted(ServerPlayer caster, Entity target, ResourceLocation skill, float adjusted, SkillDamageSource.Style style,
                                        @javax.annotation.Nullable SkillDamageSource.PierceTier inherited) {
        // 原作calc_global.damage_scaleと、技能自身のdamage_scale。
        float scaled = (float) (adjusted * AcademyConfig.DAMAGE_SCALE.get() * AcademyConfig.skillDamageScale(skill));
        return Float.isFinite(scaled) && scaled > 0 && mayAttack(caster, target, skill)
                && PierceHits.hurt(target, new SkillDamageSource(caster, skill, SkillDamageSource.styleFor(skill, style)).inheritPierceTier(inherited), scaled);
    }
}
