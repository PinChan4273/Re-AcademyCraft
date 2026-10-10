package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhaseFluidClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(PhaseContent.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PhaseContent.FLOWING.get(), RenderType.translucent());
        });
    }
}
