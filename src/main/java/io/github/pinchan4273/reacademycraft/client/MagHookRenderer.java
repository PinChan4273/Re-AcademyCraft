package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import io.github.pinchan4273.reacademycraft.visual.LegacyMagHookPose;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RendererMagHook: フックが飛んでいる間は閉じたモデル、掴んだ後は開いたモデルを、フック自身のyaw・pitchで回し、
 * 原作の0.0054倍で描く。モデルとテクスチャは原作のもので、風力発電機と同じくForgeのOBJローダーでbakeする。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MagHookRenderer extends LegacyEntityRenderer<MagHookEntity> {
    public static final Identifier CLOSED = Identifier.fromNamespaceAndPath("academy", "entity/maghook"),
            OPEN = Identifier.fromNamespaceAndPath("academy", "entity/maghook_open");
    /** どちらかのモデルで送った四角形の数（テスト用）。数えるのは呼び出し回数で、実際の見え方は画像で判定する。 */
    private static final AtomicLong OPEN_QUADS = new AtomicLong(), CLOSED_QUADS = new AtomicLong();
    public MagHookRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AcademyContent.MAG_HOOK_ENTITY.get(), MagHookRenderer::new);
    }
    public static long openQuads() { return OPEN_QUADS.get(); }
    public static long closedQuads() { return CLOSED_QUADS.get(); }

    @Override protected void render(MagHookEntity entity, float partialTick, PoseStack pose, SubmitNodeCollector buffer, int light) {
        boolean hit = entity.isHit();
        float[] turn = LegacyMagHookPose.orientation(hit, entity.hitSide(), entity.getYRot(), entity.getXRot());
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-turn[0] + 90));
        pose.mulPose(Axis.ZP.rotationDegrees(turn[1] - 90));
        pose.scale(LegacyMagHookPose.ENTITY_SCALE, LegacyMagHookPose.ENTITY_SCALE, LegacyMagHookPose.ENTITY_SCALE);
        (hit ? OPEN_QUADS : CLOSED_QUADS).addAndGet(draw(hit ? OPEN : CLOSED, pose, buffer, light));
        pose.popPose();
    }

    /** 毎回引き直す。リソース再読込で古いspriteが残らないようにするため。原作の描画は素のOpenGLで、独自のカリングは無い。モデルは閉じた殻になっている。 */
    static int draw(Identifier id, PoseStack pose, SubmitNodeCollector buffers, int light) {
        return ObjModels.draw(id, RenderTypes.entityCutout(ObjModels.atlas()), pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }

    private static final AtomicLong HELD_QUADS = new AtomicLong();
    public static long heldQuads() { return HELD_QUADS.get(); }
    /**
     * 原作ItemMagHookのTEISR: 閉じたモデルを0.01倍で、視点自身の行列で配置する。GUIではここに来ない。
     * そこではアイテムモデルが平たいアイコンを表示する（原作と同じ）。
     */
    /** 試験用: 手の磁力鉤を描いた回数。 */
    public static final class Held { private static long quads; public static long quads() { return quads; } }
    static void submitHeld(ItemDisplayContext context, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        Held.quads++;
        var matrix = LegacyMagHookPose.item(context);
        pose.pushPose();
        // アイテム描画は既に半ブロック後ろへ下がっているが、原作はそれを視点行列の後で行う。
        // そこで一度戻し、行列を適用してから、改めて下がる。
        if (matrix != null) { pose.translate(.5f, .5f, .5f); pose.mulPose(matrix); pose.translate(-.5f, -.5f, -.5f); }
        pose.scale(LegacyMagHookPose.ITEM_SCALE, LegacyMagHookPose.ITEM_SCALE, LegacyMagHookPose.ITEM_SCALE);
        HELD_QUADS.addAndGet(draw(CLOSED, pose, buffers, light));
        pose.popPose();
    }
}
