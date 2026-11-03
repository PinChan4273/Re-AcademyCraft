package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 状態効果の登録（v1.5.0）。技能の強化・弱体はバニラの状態効果を付けずにサーバーが直接処理する（{@code skill/DirectEffects}・{@code skill/HeavenEffects}）ので、
 * ここに置くのは表示のための物だけ。
 */
public final class AcademyEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, AcademyCraft.MODID);
    /**
     * 天界発現の領域の中にいることを、標準のHUD・インベントリの効果の一覧へ1件で示す中立の効果「天界」（白）。付け外しは{@code skill/HeavenDisplay}が行う。
     * 効果そのものは何もしないので、コマンドで付けても領域や怪現象は起きない。
     */
    public static final RegistryObject<MobEffect> HEAVEN = EFFECTS.register("heaven", DisplayOnlyEffect::new);

    private AcademyEffects() { }

    public static void register(IEventBus bus) { EFFECTS.register(bus); }

    /** 表示だけの効果。毎tickの処理も属性の補正も持たず、牛乳などの治療アイテムでは消えない。 */
    public static final class DisplayOnlyEffect extends MobEffect {
        DisplayOnlyEffect() { super(MobEffectCategory.NEUTRAL, 0xFFFFFF); }
        /** 何もしない。 */
        @Override public void applyEffectTick(LivingEntity entity, int amplifier) { }
        @Override public boolean isDurationEffectTick(int duration, int amplifier) { return false; }
        /** Forge 1.20.1の治療アイテム（既定は牛乳）を空にする。新しく作られるインスタンスはこの一覧で初期化される。 */
        @Override public List<ItemStack> getCurativeItems() { return new ArrayList<>(); }
    }
}
