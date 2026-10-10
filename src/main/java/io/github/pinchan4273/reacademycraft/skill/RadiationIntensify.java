package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RadiationIntensify（WeAthFolD、KSkun）: meltdownerで唯一の受動技能。
 *
 * meltdownerの技能がダメージを与えたものは3秒間被曝した状態になり、印がある間にそれへ当てたすべての攻撃はlerp(1.4, 1.8, rate)倍の
 * ダメージになる。
 *
 * 熟練度は獲得するものではなく導出する: 原作はgetSkillExpを上書きし、術者の最大CPがレベル5の術者の初期CPにどれだけ近づいたかを
 * 返す。習得してから最大CPを上げることで強くなる。
 *
 * 印は原作と同じくmapではなく印を付けたエンティティの永続データにあるので、エンティティに何が触れても残り、独自の後始末も要らない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class RadiationIntensify {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "rad_intensify");
    /** 原作MARKIDとRATEID、および印の最短60tick。 */
    private static final String MARK_TICKS = "academy_md_mark_ticks", MARK_RATE = "academy_md_mark_rate";
    public static final int MARK_DURATION = 60;

    private RadiationIntensify() { }

    /** 原作getSkillExp: 術者の最大CPが、レベル5の術者の開始時の値へどこまで近づいたか。 */
    public static float derivedProficiency(PlayerAbilityData data) {
        float reference = PlayerAbilityData.initialCp(5);
        return reference <= 0 ? 0 : Math.max(0, Math.min(1, data.getMaxCp() / reference));
    }

    public static float rate(PlayerAbilityData data) { return ArcGen.lerp(1.4f, 1.8f, derivedProficiency(data)); }

    /** 術者がそもそもこの受動技能を習得していれば、たった今当てた対象に印を付ける。 */
    public static void mark(PlayerAbilityData data, Entity target) {
        if (data == null || !data.hasLearned(ID) || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility())) return;
        target.getPersistentData().putInt(MARK_TICKS, MARK_DURATION);
        target.getPersistentData().putFloat(MARK_RATE, rate(data));
    }

    public static int markTicks(Entity target) { return target.getPersistentData().getInt(MARK_TICKS); }
    public static float markRate(Entity target) { return target.getPersistentData().getFloat(MARK_RATE); }

    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        var entity = event.getEntity();
        int ticks = markTicks(entity);
        if (ticks > 0) entity.getPersistentData().putInt(MARK_TICKS, ticks - 1);
    }

    /** 他の処理が既に決めた値に印の倍率を掛けるよう、後で実行する。原作自身の処理も他の技能に対してこの位置にある。 */
    @SubscribeEvent(priority = EventPriority.LOW) public static void hurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (markTicks(entity) <= 0) return;
        float rate = markRate(entity);
        if (rate > 0) event.setAmount(event.getAmount() * rate);
    }
}
