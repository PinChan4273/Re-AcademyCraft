package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PhaseFluidClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(PhaseContent.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PhaseContent.FLOWING.get(), RenderType.translucent());
        });
    }
}
