package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 状態効果の登録（v1.5.0）。技能の強化・弱体はバニラの状態効果を付けずにサーバーが直接処理する（{@code skill/DirectEffects}・{@code skill/HeavenEffects}）ので、
 * ここに置くのは表示のための物だけ。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class AcademyEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, AcademyCraft.MODID);
    /**
     * 天界発現の領域の中にいることを、標準のHUD・インベントリの効果の一覧へ1件で示す中立の効果「天界」（白）。付け外しは{@code skill/HeavenDisplay}が行う。
     * 効果そのものは何もしないので、コマンドで付けても領域や怪現象は起きない。
     */
    public static final DeferredHolder<MobEffect, MobEffect> HEAVEN = EFFECTS.register("heaven", DisplayOnlyEffect::new);

    /** 消費したアイテムの効果（牛乳など）を当てている深さ。{@code mixin/ConsumableMixin}が増減する。 */
    private static final ThreadLocal<int[]> CONSUMING = ThreadLocal.withInitial(() -> new int[1]);

    private AcademyEffects() { }

    public static void register(IEventBus bus) { EFFECTS.register(bus); }

    /** 消費したアイテムの効果を当て始める（{@code mixin/ConsumableMixin}から）。 */
    public static void beginConsume() { CONSUMING.get()[0]++; }
    /** 消費したアイテムの効果を当て終える。 */
    public static void endConsume() { CONSUMING.get()[0]--; }
    /** 今、消費したアイテムの効果を当てている途中か。 */
    public static boolean consuming() { return CONSUMING.get()[0] > 0; }

    /**
     * 治療アイテムでは消えないこと（Forge 1.20.1 版が治療アイテムを空にしたのと同じ結果）。
     * <p>
     * 26.2 には治療の仕組み（NeoForge 1.21.1 の {@code EffectCure}）が無く、牛乳もトーテムも {@code /effect clear} も同じ
     * {@code LivingEntity#removeAllEffects} で効果を外す。そこで、アイテムの消費（{@code Consumable#onConsume}。牛乳と、同じ仕組みで
     * 効果を外す他のアイテム）の途中の削除だけを取り消す。トーテム（{@code DeathProtection}）と {@code /effect clear} は消費の外なので、
     * Forge 1.20.1 版と同じく外れる。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keepOnConsume(MobEffectEvent.Remove event) {
        if (consuming() && event.getEffect().value() == HEAVEN.get()) event.setCanceled(true);
    }

    /** 表示だけの効果。毎tickの処理も属性の補正も持たず、牛乳などの治療アイテムでは消えない（{@link #keepOnConsume}）。 */
    public static final class DisplayOnlyEffect extends MobEffect {
        DisplayOnlyEffect() { super(MobEffectCategory.NEUTRAL, 0xFFFFFF); }
        /** 何もしない（26.2 は引数にサーバーの世界がある）。 */
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) { return true; }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return false; }
    }
}
