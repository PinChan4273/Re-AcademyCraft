package io.github.pinchan4273.reacademycraft.client;

import com.mojang.serialization.MapCodec;
import io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable;
import io.github.pinchan4273.reacademycraft.world.item.EnergyUnit;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * 原作ItemEnergyBase/IFItemManagerの絵の切り替え閾値。正確なFEは既存のNBTに残る。
 * WeAthFolD。このクライアント側アダプタは耐久値を書かず、他modのcapabilityも問い合わせない。
 * 26.2: 1.21.1 の ItemProperties は assets/academy/items の range_dispatch と、このプロパティになった。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MaterialItemModels {
    private MaterialItemModels() { }

    private record Energy() implements RangeSelectItemModelProperty {
        static final MapCodec<Energy> CODEC = MapCodec.unit(new Energy());
        @Override public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            if (stack.getItem() instanceof EnergyUnit) return picture(EnergyUnit.energyFE(stack), EnergyUnit.MAX_FE);
            return stack.getCount() == 1 ? picture(DeveloperPortable.energyFE(stack), DeveloperPortable.MAX_ENERGY * DeveloperPortable.FE_PER_IF) : 0f;
        }
        @Override public MapCodec<Energy> type() { return CODEC; }
    }

    @SubscribeEvent public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath("academy", "energy"), Energy.CODEC);
    }
    private static float picture(int stored, int capacity) {
        long legacyDamage = Math.round((1.0 - stored / (double) capacity) * 13);
        return legacyDamage < 3 ? 1f : legacyDamage > 10 ? 0f : .5f;
    }
}
