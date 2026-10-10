package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.MaterialContent;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable;
import io.github.pinchan4273.reacademycraft.world.item.EnergyUnit;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 原作ItemEnergyBase/IFItemManagerの絵の切り替え閾値。正確なFEは既存のNBTに残る。
 * WeAthFolD。このクライアント側アダプタは耐久値を書かず、他modのcapabilityも問い合わせない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MaterialItemModels {
    private MaterialItemModels() { }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var property = ResourceLocation.fromNamespaceAndPath("academy", "energy");
            ItemProperties.register(MaterialContent.item("energy_unit"), property,
                    (stack, level, entity, seed) -> picture(EnergyUnit.energyFE(stack), EnergyUnit.MAX_FE));
            ItemProperties.register(AcademyContent.DEVELOPER_PORTABLE.get(), property,
                    (stack, level, entity, seed) -> stack.getCount() == 1
                            ? picture(DeveloperPortable.energyFE(stack), DeveloperPortable.MAX_ENERGY * DeveloperPortable.FE_PER_IF) : 0f);
        });
    }
    private static float picture(int stored, int capacity) {
        long legacyDamage = Math.round((1.0 - stored / (double) capacity) * 13);
        return legacyDamage < 3 ? 1f : legacyDamage > 10 ? 0f : .5f;
    }
}
