package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyPortableDeveloperPose;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作ItemDeveloperのTEISR: developer_portable.objを拡大せず、視点自身の行列で描く。GUIではここに来ない。
 * そこではアイテムモデルが原作の平たいアイコンを表示する。
 */
public final class PortableDeveloperRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("academy", "item/developer_portable_render");
    private static final AtomicLong QUADS = new AtomicLong();
    public PortableDeveloperRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    public static long quads() { return QUADS.get(); }
    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var matrix = LegacyPortableDeveloperPose.item(context);
        pose.pushPose();
        // フックと同じく、アイテム描画の半ブロック分を戻し、視点を適用して、改めて下がる。
        if (matrix != null) { pose.translate(.5f, .5f, .5f); pose.mulPoseMatrix(matrix); pose.translate(-.5f, -.5f, -.5f); }
        QUADS.addAndGet(MagHookRenderer.draw(MODEL, pose, buffers, light));
        pose.popPose();
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(MODEL); }
    }
}
