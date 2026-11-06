package io.github.pinchan4273.reacademycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.pinchan4273.reacademycraft.world.AcademyEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * アイテムの消費（牛乳など）の効果を当てている間を {@link AcademyEffects} に知らせる。表示だけの効果「天界」を、治療アイテムでは
 * 消さないため（Forge 1.20.1 版の治療アイテムの一覧が空なのと同じ結果。26.2 には治療の仕組みが無い）。
 * 特定のアイテムを名指ししない: 消費の効果で外れる場合はどのアイテムでも同じに扱う。
 */
@Mixin(Consumable.class)
abstract class ConsumableMixin {
    @WrapMethod(method = "onConsume")
    private ItemStack academy$consume(Level level, LivingEntity user, ItemStack stack, Operation<ItemStack> original) {
        AcademyEffects.beginConsume();
        try {
            return original.call(level, user, stack);
        } finally {
            AcademyEffects.endConsume();
        }
    }
}
