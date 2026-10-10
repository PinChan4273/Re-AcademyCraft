package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyMagHookPose;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RendererMagHook: フックが飛んでいる間は閉じたモデル、掴んだ後は開いたモデルを、フック自身のyaw・pitchで回し、
 * 原作の0.0054倍で描く。モデルとテクスチャは原作のもので、風力発電機と同じくForgeのOBJローダーでbakeする。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MagHookRenderer extends EntityRenderer<MagHookEntity> {
    public static final ResourceLocation CLOSED = ResourceLocation.fromNamespaceAndPath("academy", "entity/maghook"),
            OPEN = ResourceLocation.fromNamespaceAndPath("academy", "entity/maghook_open");
    private static final RandomSource RANDOM = RandomSource.create(42);
    /** どちらかのモデルで送った四角形の数（テスト用）。数えるのは呼び出し回数で、実際の見え方は画像で判定する。 */
    private static final AtomicLong OPEN_QUADS = new AtomicLong(), CLOSED_QUADS = new AtomicLong();
    public MagHookRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AcademyContent.MAG_HOOK_ENTITY.get(), MagHookRenderer::new);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(CLOSED); event.register(OPEN); }
    public static long openQuads() { return OPEN_QUADS.get(); }
    public static long closedQuads() { return CLOSED_QUADS.get(); }

    @Override public void render(MagHookEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        boolean hit = entity.isHit();
        float[] turn = LegacyMagHookPose.orientation(hit, entity.hitSide(), entity.getYRot(), entity.getXRot());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-turn[0] + 90));
        pose.mulPose(Axis.ZP.rotationDegrees(turn[1] - 90));
        pose.scale(LegacyMagHookPose.ENTITY_SCALE, LegacyMagHookPose.ENTITY_SCALE, LegacyMagHookPose.ENTITY_SCALE);
        (hit ? OPEN_QUADS : CLOSED_QUADS).addAndGet(draw(hit ? OPEN : CLOSED, pose, buffer, light));
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }
    @Override public ResourceLocation getTextureLocation(MagHookEntity entity) { return InventoryMenu.BLOCK_ATLAS; }

    /** 毎回引き直す。リソース再読込で古いspriteが残らないようにするため。 */
    static int draw(ResourceLocation id, PoseStack pose, MultiBufferSource buffers, int light) {
        var model = Minecraft.getInstance().getModelManager().getModel(id);
        // 原作の描画は素のOpenGLで、独自のカリングは無い。モデルは閉じた殻になっている。
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        int drawn = 0;
        for (int side = 0; side <= 6; side++) {
            RANDOM.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], RANDOM, ModelData.EMPTY, null)) {
                consumer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, true);
                drawn++;
            }
        }
        return drawn;
    }

    /**
     * 原作ItemMagHookのTEISR: 閉じたモデルを0.01倍で、視点自身の行列で配置する。GUIではここに来ない。
     * そこではアイテムモデルが平たいアイコンを表示する（原作と同じ）。
     */
    public static final class Held extends BlockEntityWithoutLevelRenderer {
        private static final AtomicLong QUADS = new AtomicLong();
        public Held() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
        public static long quads() { return QUADS.get(); }
        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var matrix = LegacyMagHookPose.item(context);
            pose.pushPose();
            // アイテム描画は既に半ブロック後ろへ下がっているが、原作はそれを視点行列の後で行う。
            // そこで一度戻し、行列を適用してから、改めて下がる。
            if (matrix != null) { pose.translate(.5f, .5f, .5f); pose.mulPoseMatrix(matrix); pose.translate(-.5f, -.5f, -.5f); }
            pose.scale(LegacyMagHookPose.ITEM_SCALE, LegacyMagHookPose.ITEM_SCALE, LegacyMagHookPose.ITEM_SCALE);
            QUADS.addAndGet(draw(CLOSED, pose, buffers, light));
            pose.popPose();
        }
    }
}
