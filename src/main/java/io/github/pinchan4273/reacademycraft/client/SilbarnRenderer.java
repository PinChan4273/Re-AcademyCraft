package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacySilbarnPose;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * 原作EntitySilbarn.RenderSibarn: 当たった後は何も描かない。それまでは原作のsilbarnモデルを0.05倍で、
 * 自身の軸の周りに1ミリ秒あたり0.03度転がし、-yaw、次にX軸周りに1/4回転して描く。手に持ったものはItemSilbarnのTEISR。
 * 原作は転がりを生成時のクライアントの時計から測るが、ここではエンティティ自身のtickで測る。ゲームが動いている間は同じになる。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilbarnRenderer extends EntityRenderer<SilbarnEntity> {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("academy", "entity/silbarn");
    private static final AtomicLong DRAWN = new AtomicLong();
    public SilbarnRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AcademyContent.SILBARN_ENTITY.get(), SilbarnRenderer::new);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(MODEL); }
    /** 投げたものに送った四角形の数（テスト用）。実際の見え方は画像で判定する。 */
    public static long drawn() { return DRAWN.get(); }
    @Override public void render(SilbarnEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        if (entity.isHit()) return;
        pose.pushPose();
        float s = LegacySilbarnPose.ENTITY_SCALE;
        pose.scale(s, s, s);
        float millis = (entity.tickCount + partialTick) * 50;
        pose.mulPose(new Quaternionf().rotationAxis(millis * LegacySilbarnPose.DEGREES_PER_MILLI * net.minecraft.util.Mth.DEG_TO_RAD,
                LegacySilbarnPose.axis(entity.getId())));
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        DRAWN.addAndGet(MagHookRenderer.draw(MODEL, pose, buffer, light));
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }
    @Override public ResourceLocation getTextureLocation(SilbarnEntity entity) { return InventoryMenu.BLOCK_ATLAS; }

    /** 原作ItemSilbarnのTEISR: TEISRModel自身の行列の下でモデルを描き、視点の行列で配置する。 */
    public static final class Held extends BlockEntityWithoutLevelRenderer {
        private static final AtomicLong QUADS = new AtomicLong();
        public Held() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
        public static long quads() { return QUADS.get(); }
        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var matrix = LegacySilbarnPose.item(context);
            pose.pushPose();
            // フックと同じく、アイテム描画の半ブロック分を戻し、視点を適用して、改めて下がる。
            if (matrix != null) { pose.translate(.5f, .5f, .5f); pose.mulPoseMatrix(matrix); pose.translate(-.5f, -.5f, -.5f); }
            pose.mulPoseMatrix(LegacySilbarnPose.base());
            QUADS.addAndGet(MagHookRenderer.draw(MODEL, pose, buffers, light));
            pose.popPose();
        }
    }
}
