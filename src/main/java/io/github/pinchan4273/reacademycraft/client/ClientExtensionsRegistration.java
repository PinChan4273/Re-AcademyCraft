package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;

/**
 * 液体のクライアント側の登録。相位液は黒いテクスチャで、半透明の層に置く（独自の描画で見た目を作る。PhaseLiquidRenderer）。
 * 26.2: 1.21.1 の IClientFluidTypeExtensions と ItemBlockRenderTypes の代わりに FluidModel を登録する。
 * 手に持つ物の独自の描画は HeldItemRenderers。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientExtensionsRegistration {
    private ClientExtensionsRegistration() { }
    private static final Identifier BLACK = Identifier.fromNamespaceAndPath("academy", "block/black");

    @SubscribeEvent public static void registerFluidModels(RegisterFluidModelsEvent event) {
        var black = new Material(BLACK, true);
        event.register(new FluidModel.Unbaked(black, black, null, (net.neoforged.neoforge.client.fluid.FluidTintSource) null), PhaseContent.SOURCE, PhaseContent.FLOWING);
    }
}
