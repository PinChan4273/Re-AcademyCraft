package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.pinchan4273.reacademycraft.client.model.DarkBeetleMesh;
import io.github.pinchan4273.reacademycraft.client.render.AcademyPipelines;
import io.github.pinchan4273.reacademycraft.client.render.CapturedBuffers;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

/**
 * 未元物質の甲虫の描画。骨はDarkBeetleMesh、面はDarkBeetleFacets、動きはここ（Forge v1.5.0 2b1a8119の同じclassを元にした）。
 * <p>
 * 26.2: 箱の無いモデルを{@code Model#renderToBuffer}（26.2ではfinal）で面として描けないので、体の面と発光の面を2つの層で描く。
 * 層の前に{@link Model#setupAnim}が同じ状態で呼ばれ、層は面の頂点をその時の骨の姿勢で{@link CapturedBuffers}へ書いてから提出する
 * （骨の部品のまま渡すと、後で描く時の最後の甲虫の姿勢で全部が描かれる）。部品が空のモデルそのものは提出しない（{@link #getRenderType}がnull）。
 * 体の面の描き方・明るさ・被弾の赤・色は、Forge版の{@code MobRenderer}が{@code renderToBuffer}へ渡した物と同じ
 * （1.20.1の{@code entityCutoutNoCull}は26.2の{@code entityCutout}）。発光の面はForge版の層と同じ（{@code eyes}・最大の明るさ・GLOW）。
 * 26.2のvanillaの{@code eyes}は半透明で混ぜ、白い外殻を GLOW の灰色で塗りつぶすので、1.21.1と同じ加算の{@link AcademyPipelines#eyesAdditive}で描く。
 */
public final class DarkBeetleRenderer extends MobRenderer<DarkMatterBeetle, DarkBeetleRenderer.State, DarkBeetleRenderer.Model> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("academy", "textures/entity/dark_matter_beetle.png");
    /** 白い外殻が陰の面でも灰色に沈まないよう、同じ絵を無陰影で薄く足す量。 */
    private static final float GLOW = .3f;
    private static long drawn;

    /**
     * 26.2の描画の状態: Forge版の{@code setupAnim}が実体から読んだ値（飛行・攻撃の種類）を写す。
     * v1.5.1（Forge 17561eb5 の試作）: 出現の途中か（体も影も描かない）と、体の倍率（現れた直後と回収の写し）も写す。
     * Forge版は描く時に実体から読んだが、26.2では描く時に実体が無いので、状態を作る時に読む。
     */
    public static final class State extends LivingEntityRenderState {
        boolean flying;
        byte attackKind;
        boolean emerging;
        float renderScale = 1;
    }
    private static final float SHADOW = 1.3f;

    public DarkBeetleRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(DarkBeetleMesh.create().bakeRoot()), SHADOW);
        // 体の面（Forge版の本体の描画）。
        addLayer(new RenderLayer<>(this) {
            @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                if (state.isInvisible || state.emerging) return;
                var buffers = new CapturedBuffers();
                getParentModel().faces(pose, buffers.getBuffer(RenderTypes.entityCutout(TEXTURE)), light,
                        LivingEntityRenderer.getOverlayCoords(state, 0), 1, 1, 1, 1);
                buffers.submit(collector);
                drawn++;
            }
        });
        // 発光の面（Forge版の層）。
        addLayer(new RenderLayer<>(this) {
            @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                if (state.isInvisible || state.emerging) return;
                var buffers = new CapturedBuffers();
                getParentModel().faces(pose, buffers.getBuffer(AcademyPipelines.eyesAdditive(TEXTURE)), LightCoordsUtil.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, GLOW, GLOW, GLOW, 1);
                buffers.submit(collector);
            }
        });
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(DarkMatterBeetle beetle, State state, float partialTicks) {
        super.extractRenderState(beetle, state, partialTicks);
        state.flying = beetle.flying();
        state.attackKind = beetle.attackKind();
        state.emerging = beetle.emerging();
        state.renderScale = beetle.renderScale(partialTicks);
    }
    /**
     * 現れる途中は影を描かない（輪はClientDarkBeetleSummonが描く）。現れた直後と回収の写しは、倍率に合わせて影も縮める。
     * 影の大きさは{@code finalizeRenderState}が{@link #extractRenderState}の後に読むので、状態の値で決まる（Forge版はshadowRadiusを描く前に書き換えた）。
     */
    @Override protected float getShadowRadius(State state) {
        return state.emerging ? 0 : super.getShadowRadius(state) * state.renderScale;
    }
    @Override protected void scale(State state, PoseStack pose) {
        float scale = state.renderScale;
        if (scale != 1) pose.scale(scale, scale, scale);
    }
    @Override public Identifier getTextureLocation(State state) { return TEXTURE; }
    /** 部品が空のモデルは提出しない（面は層が描く）。 */
    @Override protected @Nullable RenderType getRenderType(State state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) { return null; }

    /** 試験用: 体の面を提出した回数。 */
    public static long drawn() { return drawn; }

    /** 3対の脚は三脚歩行（左前・右中・左後ろが同じ組）で、節ごとに持ち上げる。飛行翅はB2まで畳んで隠す。 */
    public static final class Model extends EntityModel<State> {
        private final ModelPart beetleRoot, head;
        private final ModelPart[] coxa = new ModelPart[6], tibia = new ModelPart[6];
        private final ModelPart wingLeft, wingRight, shellLeft, shellRight, bodyPart;
        private final java.util.List<DarkBeetleFacets.Part> facets = DarkBeetleFacets.parts();
        private final ModelPart[][] chains;
        Model(ModelPart baked) {
            super(baked);
            beetleRoot = baked.getChild("root");
            chains = facets.stream().map(part -> DarkBeetleFacets.chain(beetleRoot, part.path())).toArray(ModelPart[][]::new);
            // 26.2のEntityModelは最上位の骨をrootという名で持つので、甲虫の"root"の骨はbeetleRootから辿る（rootからでは"body"が無く、資源の読み込みが止まる）。
            var body = beetleRoot.getChild("body");
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
        /**
         * 箱は無く、全身を多角形の面で描く（骨の変換に沿う）。Forge版の{@code renderToBuffer}と同じ（26.2では{@code renderToBuffer}がfinalなので名前を変えた）。
         */
        void faces(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
            for (int i = 0; i < chains.length; i++) {
                var chain = chains[i];
                boolean visible = true;
                for (var part : chain) visible &= part.visible;
                if (!visible) continue;
                pose.pushPose();
                for (var part : chain) part.translateAndRotate(pose);
                var matrix = pose.last().pose(); var normal = pose.last();
                float[] data = facets.get(i).faces();
                for (int f = 0; f < data.length; f += DarkBeetleFacets.STRIDE) {
                    float cr = r * data[f + 15], cg = g * data[f + 16], cb = b * data[f + 17];
                    for (int v = 0; v < 4; v++)
                        buffer.addVertex(matrix, data[f + v * 3] / 16, data[f + v * 3 + 1] / 16, data[f + v * 3 + 2] / 16).setColor(cr, cg, cb, a)
                                .setUv(.5f, .5f).setOverlay(overlay).setLight(light).setNormal(normal, data[f + 12], data[f + 13], data[f + 14]);
                }
                pose.popPose();
            }
        }
        @Override public void setupAnim(State state) {
            super.setupAnim(state);
            float headYaw = state.yRot, headPitch = state.xRot, age = state.ageInTicks;
            float limbSwing = state.walkAnimationPos, limbAmount = state.walkAnimationSpeed;
            head.yRot = headYaw * Mth.DEG_TO_RAD * .35f;
            head.xRot = headPitch * Mth.DEG_TO_RAD * .3f;
            // 飛行: 上翅を開いて飛行翅を羽ばたかせ、脚を縮める（B2の初稿）。
            boolean flying = state.flying;
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
            switch (state.attackKind) {
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

    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AcademyContent.DARK_MATTER_BEETLE.get(), DarkBeetleRenderer::new);
        }
    }
}
