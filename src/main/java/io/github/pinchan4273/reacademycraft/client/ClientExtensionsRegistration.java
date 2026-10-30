package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * アイテムと液体のクライアント側の拡張（手に持った物の独自の描画、液体のテクスチャ）。
 * Forge 1.20.1版は各classのinitializeClientで渡した。NeoForge 21.1でもinitializeClientは呼ばれるが、1.21から削除予定の非推奨
 * （ClientExtensionsManager.earlyInit）で、後継はこのRegisterClientExtensionsEventになった。両方とも同じ表へ登録するので、
 * 中身はそのまま、ここへ移した。共通のclassからクライアントのclassを参照する所も無くなる。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientExtensionsRegistration {
    private ClientExtensionsRegistration() { }
    /** 相位液は黒いテクスチャ（独自の描画で見た目を作る）。 */
    private static final ResourceLocation BLACK = ResourceLocation.fromNamespaceAndPath("academy", "block/black");

    @SubscribeEvent public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return BLACK; }
            @Override public ResourceLocation getFlowingTexture() { return BLACK; }
        }, PhaseContent.TYPE.get());
        // 原作は、携帯の能力開発機・電磁フック・Silbarnを、手と地面でアイコンではなく独自のモデルで描く。
        event.registerItem(held(PortableDeveloperRenderer::new), AcademyContent.DEVELOPER_PORTABLE.get());
        event.registerItem(held(MagHookRenderer.Held::new), AcademyContent.MAG_HOOK.get());
        event.registerItem(held(SilbarnRenderer.Held::new), AcademyContent.SILBARN.get());
    }

    /** 初めて描くときに作る、独自の描画。 */
    private static IClientItemExtensions held(java.util.function.Supplier<BlockEntityWithoutLevelRenderer> factory) {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = factory.get();
                return renderer;
            }
        };
    }
}
