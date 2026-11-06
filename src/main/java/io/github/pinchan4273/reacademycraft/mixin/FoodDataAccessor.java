package io.github.pinchan4273.reacademycraft.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v1.5.0 の未元物質人間の身体の維持（{@code skill/DarkBody}）が、疲労度を 0 に戻すための入口。
 * Forge 版・1.21.1 版の {@code FoodData#setExhaustion} は 26.2 に無いので、同じ値を直に書く。
 */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
    @Accessor("exhaustionLevel")
    void academy$setExhaustion(float value);
}
