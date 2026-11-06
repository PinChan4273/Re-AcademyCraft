package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 未元物質人間の頭上の輪（COMMAND-RE-150-DARK-BODY-VISUAL-R1-20261104の初稿）。白い同心円の輪2本を、頭の上に水平に重ねる（中心と高さは共通）。
 * 能力OFF（翼が無い）でも出す。静止中はわずかに、歩く・飛ぶ間は大きく上下に揺れる（内側の輪の方が大きく、位相もずらす）。
 * 明るさは翼の発光の設定に従い、0%でも周りの明るさで形が見える。
 */
public final class DarkHaloLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/ripple.png");
    /** 輪の見た目の直径（ブロック。素材の輪の濃い線の位置）。素材の輪の線は、四角の幅の{@link #RING_AT}倍の直径にある。 */
    public static final float OUTER_DIAMETER = .68f, INNER_DIAMETER = .48f, RING_AT = .79f;
    /** 頭のてっぺんから輪の中心までの高さ（ブロック）。 */
    public static final float HEIGHT = .16f;
    /** 揺れの幅（ブロック）: 静止時と、歩く・飛ぶ間の最大。 */
    public static final float OUTER_IDLE = .01f, INNER_IDLE = .015f, OUTER_MOVE = .04f, INNER_MOVE = .06f;
    /** 内側の輪の揺れの位相のずれ（ラジアン）。 */
    private static final float INNER_PHASE = 1.3f;
    /** 輪の不透明度。 */
    private static final float ALPHA = .9f;

    DarkHaloLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbAmount,
                                 float partial, float age, float headYaw, float headPitch) {
        // 自然能力の未元物質人間でなければ、外部の演出（演出API版3）の輪を同じ描き方で描く（見る人の翼の発光にも同じく従う）。
        var mark = ClientDarkBody.haloMark(player.getId());
        if (mark == null || !mark.body() || player.isInvisible()) return;
        float move = mark.move(partial), phase = mark.phase(partial);
        float outerBob = Mth.lerp(move, OUTER_IDLE, OUTER_MOVE) * Mth.sin(phase), innerBob = Mth.lerp(move, INNER_IDLE, INNER_MOVE) * Mth.sin(phase + INNER_PHASE);
        var head = getParentModel().head;
        // 描画の空間は下が+y（1単位＝1ブロック、モデルの1画素＝1/16）。頭の付け根から8画素上がてっぺん。
        float x = head.x / 16, top = head.y / 16 - .5f - HEIGHT, z = head.z / 16;
        // 描く時の発光（シェーダーパックの使用中は100%を90%として描く。翼と同じ値）。
        float glowLevel = ClientSettings.drawnWingGlow() / 100f;
        int face = glowLevel >= 1 ? LightTexture.FULL_BRIGHT
                : LightTexture.pack(Math.round(Mth.lerp(glowLevel, LightTexture.block(light), 15)), Math.round(Mth.lerp(glowLevel, LightTexture.sky(light), 15)));
        // 100%は陰影を付けない半透明（防御の球と同じ。体の描画の陰影では水平な面が灰色に沈んだ）。下げたときは周りの明るさを受ける。
        var faces = buffers.getBuffer(glowLevel >= 1 ? RenderType.beaconBeam(TEXTURE, true) : RenderType.entityTranslucent(TEXTURE));
        ring(pose, faces, x, top - outerBob, z, OUTER_DIAMETER, 1, ALPHA, face);
        ring(pose, faces, x, top - innerBob, z, INNER_DIAMETER, 1, ALPHA, face);
        // 翼のような無陰影の加算の層は足さない（加算は素材の透明度を見ないので、輪の外の四角まで白く光った。初稿の撮影で確認）。
    }
    /** 水平な輪を1本、上下の両面で描く。 */
    private static void ring(PoseStack pose, VertexConsumer buffer, float x, float y, float z, float diameter, float shade, float alpha, int light) {
        float h = diameter / RING_AT / 2;
        Matrix4f m = pose.last().pose();
        var n = pose.last(); // 1.21のsetNormalはPoseStack.Poseを取る
        float[][] corners = {{-h, -h, 0, 0}, {-h, h, 0, 1}, {h, h, 1, 1}, {h, -h, 1, 0}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                var c = corners[side == 0 ? i : 3 - i];
                buffer.addVertex(m, x + c[0], y, z + c[1]).setColor(shade, shade, shade, alpha).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(n, 0, side == 0 ? -1 : 1, 0);
            }
        }
    }
}
