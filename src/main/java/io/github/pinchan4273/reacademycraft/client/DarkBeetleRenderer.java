package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.model.DarkBeetleMesh;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 未元物質の甲虫の描画。骨はDarkBeetleMesh、面はDarkBeetleFacets、動きはここ。 */
public final class DarkBeetleRenderer extends MobRenderer<DarkMatterBeetle, DarkBeetleRenderer.Model> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/entity/dark_matter_beetle.png");
    /** 白い外殻が陰の面でも灰色に沈まないよう、同じ絵を無陰影で薄く足す量。 */
    private static final float GLOW = .3f;

    public DarkBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(DarkBeetleMesh.create().bakeRoot()), SHADOW);
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
            @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, DarkMatterBeetle beetle,
                                         float limbSwing, float limbAmount, float partial, float age, float headYaw, float headPitch) {
                getParentModel().renderToBuffer(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(TEXTURE)), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, GLOW, GLOW, GLOW, 1);
            }
        });
    }
    @Override public ResourceLocation getTextureLocation(DarkMatterBeetle beetle) { return TEXTURE; }
    private static final float SHADOW = 1.3f;
    /** 現れる途中は体も影も描かない（輪はClientDarkBeetleSummonが描く）。現れた直後と回収の写しは、倍率に合わせて影も縮める。 */
    @Override public void render(DarkMatterBeetle beetle, float yaw, float partial, com.mojang.blaze3d.vertex.PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (beetle.emerging()) { shadowRadius = 0; return; }
        shadowRadius = SHADOW * beetle.renderScale(partial);
        super.render(beetle, yaw, partial, pose, buffers, light);
    }
    @Override protected void scale(DarkMatterBeetle beetle, com.mojang.blaze3d.vertex.PoseStack pose, float partial) {
        float scale = beetle.renderScale(partial);
        if (scale != 1) pose.scale(scale, scale, scale);
    }

    /** 3対の脚は三脚歩行（左前・右中・左後ろが同じ組）で、節ごとに持ち上げる。飛行翅はB2まで畳んで隠す。 */
    public static final class Model extends HierarchicalModel<DarkMatterBeetle> {
        private final ModelPart root, head;
        private final ModelPart[] coxa = new ModelPart[6], tibia = new ModelPart[6];
        private final ModelPart wingLeft, wingRight, shellLeft, shellRight, bodyPart;
        private final java.util.List<DarkBeetleFacets.Part> facets = DarkBeetleFacets.parts();
        private final ModelPart[][] chains;
        Model(ModelPart baked) {
            root = baked.getChild("root");
            chains = facets.stream().map(part -> DarkBeetleFacets.chain(root, part.path())).toArray(ModelPart[][]::new);
            var body = root.getChild("body");
            head = body.getChild("head");
            bodyPart = body;
            shellLeft = body.getChild("elytron_left");
            shellRight = body.getChild("elytron_right");
            wingLeft = body.getChild("flight_wing_left");
            wingRight = body.getChild("flight_wing_right");
            String[] names = {"front_left", "middle_right", "rear_left", "front_right", "middle_left", "rear_right"};
            for (int i = 0; i < 6; i++) {
                coxa[i] = body.getChild("leg_" + names[i]);
                tibia[i] = coxa[i].getChild("leg_" + names[i] + "_femur");
            }
        }
        @Override public ModelPart root() { return root; }
        /** 箱は無く、全身を多角形の面で描く（骨の変換に沿う）。 */
        @Override public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
            for (int i = 0; i < chains.length; i++) {
                var chain = chains[i];
                boolean visible = true;
                for (var part : chain) visible &= part.visible;
                if (!visible) continue;
                pose.pushPose();
                for (var part : chain) part.translateAndRotate(pose);
                var matrix = pose.last().pose(); var normal = pose.last().normal();
                float[] data = facets.get(i).faces();
                for (int f = 0; f < data.length; f += DarkBeetleFacets.STRIDE) {
                    float cr = r * data[f + 15], cg = g * data[f + 16], cb = b * data[f + 17];
                    for (int v = 0; v < 4; v++)
                        buffer.vertex(matrix, data[f + v * 3] / 16, data[f + v * 3 + 1] / 16, data[f + v * 3 + 2] / 16).color(cr, cg, cb, a)
                                .uv(.5f, .5f).overlayCoords(overlay).uv2(light).normal(normal, data[f + 12], data[f + 13], data[f + 14]).endVertex();
                }
                pose.popPose();
            }
        }
        @Override public void setupAnim(DarkMatterBeetle beetle, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
            root().getAllParts().forEach(ModelPart::resetPose);
            head.yRot = headYaw * Mth.DEG_TO_RAD * .35f;
            head.xRot = headPitch * Mth.DEG_TO_RAD * .3f;
            // 飛行: 上翅を開いて飛行翅を羽ばたかせ、脚を縮める（B2の初稿）。
            boolean flying = beetle.flying();
            wingLeft.visible = wingRight.visible = flying;
            if (flying) {
                shellLeft.zRot = -.9f; shellRight.zRot = .9f;
                shellLeft.yRot = -.2f; shellRight.yRot = .2f;
                float flap = Mth.sin(age * 2.4f) * .55f;
                wingLeft.zRot = flap - .15f; wingRight.zRot = -flap + .15f;
                bodyPart.xRot = -.18f;
                for (var femur : tibia) femur.zRot *= .35f;
            }
            // 攻撃: 近接は頭を突き上げ、突進は頭を下げて構え、砲撃は反動で少し引く（時間は同期の種類だけで作る）。
            switch (beetle.attackKind()) {
                case DarkMatterBeetle.ATTACK_MELEE -> head.xRot += -.35f + Mth.sin(age * 1.1f) * .25f;
                case DarkMatterBeetle.ATTACK_DASH -> { head.xRot += .3f; bodyPart.xRot += .12f; }
                case DarkMatterBeetle.ATTACK_SHOT -> head.xRot += -.12f;
                default -> { }
            }
            float swing = limbSwing * .9f, amount = flying ? 0 : Math.min(1, limbAmount * 1.6f);
            for (int i = 0; i < 6; i++) {
                float phase = i < 3 ? 0 : Mth.PI;
                coxa[i].yRot += Mth.cos(swing + phase) * .42f * amount;
                tibia[i].zRot += Math.max(0, Mth.sin(swing + phase)) * (i == 0 || i == 2 || i == 4 ? -.3f : .3f) * amount;
            }
        }
    }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AcademyContent.DARK_MATTER_BEETLE.get(), DarkBeetleRenderer::new);
        }
    }
}
