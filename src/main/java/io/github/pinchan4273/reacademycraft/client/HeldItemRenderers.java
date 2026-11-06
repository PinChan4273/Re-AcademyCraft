package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Vector3fc;

/**
 * 携帯の能力開発機・電磁フック・Silbarn の、手と地面の独自のモデル（1.21.1 の IClientItemExtensions と BlockEntityWithoutLevelRenderer）。
 * 26.2 の特殊な描画は持ち方を受け取らないので、assets/academy/items の select で持ち方ごとに分け、持ち方をここへ渡す。
 * GUI は平たいアイコンで、ここへ来ない（原作と同じ）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class HeldItemRenderers {
    private HeldItemRenderers() { }

    @FunctionalInterface
    interface Body {
        void submit(ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector, int light, int overlay);
    }
    private static Body body(String kind) {
        return switch (kind) {
            case "developer_portable" -> PortableDeveloperRenderer::submit;
            case "mag_hook" -> MagHookRenderer::submitHeld;
            case "silbarn" -> SilbarnRenderer::submitHeld;
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private record Renderer(Body body, ItemDisplayContext context) implements NoDataSpecialModelRenderer {
        @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outlineColor) {
            body.submit(context, pose, collector, light, overlay);
        }
        @Override public void getExtents(Consumer<Vector3fc> output) { }
    }

    private record Unbaked(String kind, ItemDisplayContext context) implements NoDataSpecialModelRenderer.Unbaked {
        static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Unbaked::kind),
                ItemDisplayContext.CODEC.fieldOf("context").forGetter(Unbaked::context)
        ).apply(i, Unbaked::new));
        @Override public MapCodec<Unbaked> type() { return CODEC; }
        @Override public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) { return new Renderer(body(kind), this.context); }
    }

    @SubscribeEvent public static void register(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath("academy", "held"), Unbaked.CODEC);
    }
}
