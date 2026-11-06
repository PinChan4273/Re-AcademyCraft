package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import io.github.pinchan4273.reacademycraft.visual.LegacyPortableDeveloperPose;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作ItemDeveloperのTEISR: developer_portable.objを拡大せず、視点自身の行列で描く。GUIではここに来ない。
 * そこではアイテムモデルが原作の平たいアイコンを表示する。
 */
public final class PortableDeveloperRenderer {
    public static final Identifier MODEL = Identifier.fromNamespaceAndPath("academy", "item/developer_portable_render");
    private static final AtomicLong QUADS = new AtomicLong();
    private PortableDeveloperRenderer() { }
    public static long quads() { return QUADS.get(); }
    static void submit(ItemDisplayContext context, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        var matrix = LegacyPortableDeveloperPose.item(context);
        pose.pushPose();
        // フックと同じく、アイテム描画の半ブロック分を戻し、視点を適用して、改めて下がる。
        if (matrix != null) { pose.translate(.5f, .5f, .5f); pose.mulPose(matrix); pose.translate(-.5f, -.5f, -.5f); }
        QUADS.addAndGet(MagHookRenderer.draw(MODEL, pose, buffers, light));
        pose.popPose();
    }
}
