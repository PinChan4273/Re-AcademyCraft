package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 状態効果の登録（v1.5.0）。技能の強化・弱体はバニラの状態効果を付けずにサーバーが直接処理する（{@code skill/DirectEffects}・{@code skill/HeavenEffects}）ので、
 * ここに置くのは表示のための物だけ。
 */
public final class AcademyEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, AcademyCraft.MODID);
    /**
     * 天界発現の領域の中にいることを、標準のHUD・インベントリの効果の一覧へ1件で示す中立の効果「天界」（白）。付け外しは{@code skill/HeavenDisplay}が行う
     * （第2便で移す。対応表のH）。効果そのものは何もしないので、コマンドで付けても領域や怪現象は起きない。
     */
    public static final DeferredHolder<MobEffect, MobEffect> HEAVEN = EFFECTS.register("heaven", DisplayOnlyEffect::new);

    private AcademyEffects() { }

    public static void register(IEventBus bus) { EFFECTS.register(bus); }

    /** 表示だけの効果。毎tickの処理も属性の補正も持たず、牛乳などの治療アイテムでは消えない。 */
    public static final class DisplayOnlyEffect extends MobEffect {
        DisplayOnlyEffect() { super(MobEffectCategory.NEUTRAL, 0xFFFFFF); }
        /** 何もしない。 */
        @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) { return true; }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return false; }
        /**
         * Forge 1.20.1版は治療アイテム（既定は牛乳）を空にした。NeoForge 1.21.1は治療を{@link EffectCure}で表し、既定は牛乳とトーテムなので、
         * 牛乳を外してトーテムだけを残す（Forge 1.20.1のトーテムは治療アイテムに関係なく全部の効果を外していたので、それと同じ結果）。
         */
        @Override public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) { cures.add(EffectCures.PROTECTED_BY_TOTEM); }
    }
}
