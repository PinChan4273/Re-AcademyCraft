package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.pinchan4273.reacademycraft.client.model.DarkWingsMesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 未元物質の六翼（W1）。プレイヤーの体の部品に付くレイヤーなので、通常／スリムの両スキン、しゃがみ、エリトラ姿勢に同じように付いて行く。
 * 明るさは光源に左右されない（白く発光して見える）。ランタイムはMinecraftのモデル部品だけを使い、バニラの粒子は使わない。
 */
public final class DarkWingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/entity/dark_matter_wings.png");
    private static final String[] TIERS = {"upper", "middle", "lower"};
    private static final float GLOW = .16f;

    /** 1枚の翼（根元・中節・先端）。 */
    private record Wing(ModelPart root, ModelPart mid, ModelPart tip, int tier, boolean right, String name) { }

    private final ModelPart wings, mountLeft, mountRight;
    private final List<Wing> parts = new ArrayList<>();
    /** 羽1枚。phaseは揺れの位相、amountは節ごとの揺れの大きさ。 */
    private record Feather(ModelPart part, float phase, float amount, boolean right, int tier, int segment, ModelPart[] chain,
                           float length, float width, org.joml.Vector3f ballAt, org.joml.Quaternionf ballTurn) { }
    /** 羽の番号を球の席へ散らす（翼ごとに固まらないように）。69枚と互いに素。 */
    private static final int SEAT_STRIDE = 37, SIDE_FEATHERS = 69;
    private int seatLeft, seatRight;
    private final List<Feather> feathers = new ArrayList<>();
    private static long drawn;

    DarkWingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        wings = DarkWingsMesh.create().bakeRoot().getChild("wings");
        mountLeft = wings.getChild("mount_left");
        mountRight = wings.getChild("mount_right");
        for (int t = 0; t < TIERS.length; t++) {
            for (boolean right : new boolean[]{false, true}) {
                String side = right ? "right" : "left";
                var root = wings.getChild("mount_" + side).getChild(side + "_" + TIERS[t]);
                var mid = root.getChild(side + "_" + TIERS[t] + "_mid");
                var tip = mid.getChild(side + "_" + TIERS[t] + "_tip");
                parts.add(new Wing(root, mid, tip, t, right, side + "_" + TIERS[t] + "_tip_f"));
                String name = side + "_" + TIERS[t];
                var mount = wings.getChild("mount_" + side);
                collect(root, name + "_root_f", .55f, t, right, 0, new ModelPart[]{wings, mount, root});
                collect(mid, name + "_mid_f", .85f, t, right, 1, new ModelPart[]{wings, mount, root, mid});
                collect(tip, name + "_tip_f", 1.2f, t, right, 2, new ModelPart[]{wings, mount, root, mid, tip});
            }
        }
    }

    private void collect(ModelPart segment, String prefix, float amount, int tier, boolean right, int index, ModelPart[] chain) {
        int count = 0;
        while (segment.hasChild(prefix + count)) count++;
        for (int i = 0; i < count; i++) {
            var part = segment.getChild(prefix + i);
            var cube = part.getRandomCube(net.minecraft.util.RandomSource.create(0));
            int seat = ((right ? seatRight++ : seatLeft++) * SEAT_STRIDE) % SIDE_FEATHERS;
            // 球の席: 左右の半球それぞれに、面積が均等になるよう並べる（x軸まわりのらせん）。
            float x = (seat + .5f) / SIDE_FEATHERS * (right ? -1 : 1), around = seat * 2.39996f, ring = Mth.sqrt(1 - x * x);
            var normal = new org.joml.Vector3f(x, ring * Mth.cos(around), ring * Mth.sin(around));
            // 羽は球面に接し、背中から正面へ流れる。少しだけ向きを散らす。
            var flow = new org.joml.Vector3f(0, 0, -1).sub(new org.joml.Vector3f(normal).mul(-normal.z));
            if (flow.lengthSquared() < 1e-3f) flow.set(1, 0, 0);
            flow.normalize().rotateAxis((seat % 7 - 3) * 5 * Mth.DEG_TO_RAD, normal.x, normal.y, normal.z);
            var side = new org.joml.Vector3f(normal).cross(flow);
            var turn = new org.joml.Matrix3f(flow.x, flow.y, flow.z, side.x, side.y, side.z, normal.x, normal.y, normal.z).getNormalizedRotation(new org.joml.Quaternionf());
            feathers.add(new Feather(part, i * 1.73f + tier * 2.1f + (right ? 3.3f : 0), amount, right, tier, index, chain,
                    cube.maxX - cube.minX, cube.maxY - cube.minY, normal, turn));
        }
    }

    /**
     * 発光の強さ（各自の表示設定。2026-11-03のユーザーの決定: シェーダーでは自分で光る描き方に強いブルームが掛かる）と、その絵の周りの明るさ。
     * 100%は今までどおり（周りに関係なく白く光る）。下げるほど白く光らせる層を弱め、面の明るさを周りの明るさへ寄せる。0%は周りの明るさで照らす。
     */
    private float glowLevel = 1;
    private int worldLight = LightTexture.FULL_BRIGHT;
    private void prepareGlow(int light) { glowLevel = ClientSettings.wingGlow() / 100f; worldLight = light; }
    /** 面の明るさ: 周りの明るさから、発光の強さの分だけ最大の明るさへ寄せる。 */
    private int faceLight() {
        if (glowLevel >= 1) return LightTexture.FULL_BRIGHT;
        int block = Math.round(Mth.lerp(glowLevel, LightTexture.block(worldLight), 15)), sky = Math.round(Mth.lerp(glowLevel, LightTexture.sky(worldLight), 15));
        return LightTexture.pack(block, sky);
    }
    /** 面を描く描き方: 100%は自分で光る半透明（今までどおり）、それより下は周りの明るさを受ける半透明。 */
    private static RenderType faceType(float level) {
        return level >= 1 ? RenderType.entityTranslucentEmissive(TEXTURE) : RenderType.entityTranslucent(TEXTURE);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbAmount,
                                 float partial, float age, float headYaw, float headPitch) {
        var state = ClientDarkWings.state(player.getId());
        if (state == null || player.isInvisible()) return;
        prepareGlow(light);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        drawingId = player.getId();
        draw(state, pose, buffers, partial, age, headYaw, headPitch, false);
        drawingId = -1;
        pose.popPose();
    }
    private void draw(ClientDarkWings.State state, PoseStack pose, MultiBufferSource buffers, float partial, float age, float headYaw, float headPitch, boolean firstPerson) {
        float open = state.open(partial);
        if (open <= .001f) return;
        firstPersonNow = firstPerson;
        pose(state, open, partial, age);
        // 空気砲の風の柱も同じ根元から生やす（2026-11-03）。
        if (!firstPerson && drawingId >= 0 && (state.rayOn() || ClientDarkEffects.gusting(drawingId))) recordBeamRoots(pose);
        Wing stabbing = aimStab(state, partial, headYaw, headPitch);
        // 1人称では、刺している翼だけを見せる（他の翼は視界の外なので、頭の中で切れた面を見せない）。
        if (firstPerson) for (var wing : parts) wing.root.visible = wing == stabbing;
        float alpha = Math.min(1, open * 1.4f), glow = GLOW * alpha * glowLevel;
        wings.render(pose, buffers.getBuffer(faceType(glowLevel)), faceLight(), OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
        // 面の向きで灰色に沈まないよう、同じ絵を無陰影で足して白く発光させる（発光の強さに比例。0%では足さない）。
        if (glow > 0) wings.render(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1);
        if (stabbing != null) extendStab(stabbing, pose, buffers, alpha, glow);
        ball(state, pose, buffers, partial, age, firstPerson);
        for (var wing : parts) wing.root.visible = true;
        drawn++;
    }
    /** 描いている体の実体のid（体の描画の外では-1）。 */
    private int drawingId = -1;
    /** 光線の根元（中節の先＝先の節の付け根）の、この絵の描画の空間の位置。実体のidごとに6枚（段×左右の順）と、記録した絵の番号。 */
    private static final java.util.Map<Integer, org.joml.Vector3f[]> BEAM_ROOTS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Long> BEAM_ROOT_FRAME = new java.util.HashMap<>();
    /**
     * 光線の根元を、実際に描く翼の姿勢（旋回の遅れ・呼吸・しゃがみ・高速飛行の傾きを含む）から記録する。{@link ClientDarkEffects}は同じ絵の中でこれを使い、
     * 根元が翼から離れて途切れて見えないようにする（2026-11-03の試遊の指摘）。
     */
    private void recordBeamRoots(PoseStack pose) {
        var roots = BEAM_ROOTS.computeIfAbsent(drawingId, id -> new org.joml.Vector3f[6]);
        for (var wing : parts) {
            pose.pushPose();
            wings.translateAndRotate(pose);
            (wing.right ? mountRight : mountLeft).translateAndRotate(pose);
            wing.root.translateAndRotate(pose);
            wing.mid.translateAndRotate(pose);
            wing.tip.translateAndRotate(pose);
            var at = pose.last().pose().transformPosition(new org.joml.Vector3f());
            roots[wing.tier * 2 + (wing.right ? 1 : 0)] = at;
            pose.popPose();
        }
        BEAM_ROOT_FRAME.put(drawingId, ClientDarkEffects.frame());
    }
    /** この絵で記録した光線の根元（描画の空間）。この絵で体が描かれていなければnull。 */
    @javax.annotation.Nullable static org.joml.Vector3f[] beamRoots(int id) {
        var frame = BEAM_ROOT_FRAME.get(id);
        return frame != null && frame == ClientDarkEffects.frame() ? BEAM_ROOTS.get(id) : null;
    }
    /** 1人称の間の、刺す翼の根元のずらし（真後ろから出ると視界の中央を線で通るので、横の下から出す）。 */
    private static final float FIRST_PERSON_SIDE = 9, FIRST_PERSON_DOWN = 8;
    /** 球の中にカメラがあるので、1人称では羽を薄くして前が見えるようにする。内側の層は描かない。 */
    private static final float FIRST_PERSON_BALL_ALPHA = .3f;
    private boolean firstPersonNow;
    private static DarkWingsLayer anyLayer;

    /** 1人称では体の描画が走らないので、刺突と防御の間だけ、同じ羽を世界の描画で描く。 */
    static void renderFirstPerson(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES || anyLayer == null) return;
        var client = net.minecraft.client.Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.getCameraEntity() != player || !client.options.getCameraType().isFirstPerson() || player.isInvisible()) return;
        var state = ClientDarkWings.state(player.getId());
        float partial = event.getPartialTick();
        if (state == null || (state.stab(partial) <= 0 && state.guard(partial) <= 0)) return;
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        var at = player.getPosition(partial).subtract(event.getCamera().getPosition());
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.scale(-1, -1, 1);
        pose.scale(.9375f, .9375f, .9375f);
        pose.translate(0, -1.501f, 0);
        var buffers = client.renderBuffers().bufferSource();
        anyLayer.prepareGlow(client.getEntityRenderDispatcher().getPackedLightCoords(player, partial));
        anyLayer.draw(state, pose, buffers, partial, player.tickCount + partial, Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - bodyYaw), player.getViewXRot(partial), true);
        buffers.endBatch();
        pose.popPose();
    }

    /** 段ごとの翼の長さの倍率と、取付の高さ（生成スクリプトの表と同じ）。 */
    private static final float[] TIER_SCALE = {.9f, 1, .76f}, TIER_Y = {-2, 1, 4};
    /** 翼の長さ（倍率1で、根元15＋中節15＋先の羽42）と、そこから引き伸ばしてよい倍率。これを超える前に羽を足し始める。 */
    private static final float WING_LENGTH = 72, TIP_START = 30, MAX_STRETCH = 1.2f;
    /** 足す羽の間隔。翼の先の節の羽の長さより十分短くして、重ねて太さを保つ。 */
    private static final float EXTEND_STEP = 16;
    /** 刺す翼と足す羽の幅の倍率。 */
    private static final float STAB_WIDTH = 2.2f;
    /** 足す羽（先の節の、角度の小さい5枚の番号）。広がる2枚は先端の本来の翼にだけ残す。 */
    private static final int[] EXTEND_FEATHERS = {0, 1, 2, 3, 4};
    /** 今の刺突で見せる長さと引き伸ばし。aimStabが決めてextendStabが使う。 */
    private float stabShown, stabStretch;

    /**
     * 刺突: 刺す翼そのものを、届いた点へ向けて伸ばす。節をまっすぐにして、長さは{@link #MAX_STRETCH}倍まで翼を引き伸ばす。
     * サーバーが送るのは目から届いた点までの距離で、方向は頭の向きと視線の上下から描画側で求める。
     */
    private @javax.annotation.Nullable Wing aimStab(ClientDarkWings.State state, float partial, float headYaw, float headPitch) {
        float extend = state.stab(partial);
        if (extend <= 0) return null;
        int number = state.stabWing();
        Wing wing = null;
        for (var candidate : parts) if (candidate.tier * 2 + (candidate.right ? 1 : 0) == number) wing = candidate;
        if (wing == null) return null;
        float yaw = headYaw * Mth.DEG_TO_RAD, pitch = headPitch * Mth.DEG_TO_RAD, reach = state.stabReach() * 16;
        // 体の部品から見た届いた点（モデルは前が-z、下が+y。目は体の上端より4上）と、翼の根元からのベクトル。
        float cp = Mth.cos(pitch);
        float shiftSide = firstPersonNow ? FIRST_PERSON_SIDE : 0, shiftDown = firstPersonNow ? FIRST_PERSON_DOWN : 0;
        wing.root.x += shiftSide; wing.root.y += shiftDown;
        float dx = -cp * Mth.sin(yaw) * reach - (wing.right ? -1 : 1) * (1.5f + shiftSide), dy = -4 + Mth.sin(pitch) * reach - 3 - TIER_Y[wing.tier] - shiftDown, dz = -cp * Mth.cos(yaw) * reach - 3;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-3f) return null;
        // 右の翼は親がY軸で180度回っている。
        if (wing.right) { dx = -dx; dz = -dz; }
        float turn = Math.min(1, extend * 2.5f);
        // 展開中の翼の向きから、翼の軸（+x）だけを届いた点へ最短で回す。角度へ分解して直接決めると、真正面を狙うときにわずかなずれで大きくねじれる。
        var rest = new org.joml.Quaternionf().rotationZYX(wing.root.zRot, wing.root.yRot, wing.root.xRot);
        var axis = rest.transform(new org.joml.Vector3f(1, 0, 0));
        var aimed = new org.joml.Quaternionf().rotationTo(axis.x, axis.y, axis.z, dx / length, dy / length, dz / length).mul(rest);
        var angles = rest.slerp(aimed, turn, new org.joml.Quaternionf()).getEulerAnglesZYX(new org.joml.Vector3f());
        wing.root.setRotation(angles.x, angles.y, angles.z);
        wing.mid.yRot *= 1 - turn; wing.mid.zRot *= 1 - turn;
        wing.tip.yRot *= 1 - turn; wing.tip.zRot *= 1 - turn;
        stabShown = length * extend;
        stabStretch = Mth.clamp(stabShown / (WING_LENGTH * TIER_SCALE[wing.tier]), 1, MAX_STRETCH);
        wing.root.xScale = stabStretch;
        // 長さに対して細くならないよう、伸びるほど翼の幅を広げる（面は横を向くので、真横から羽が読める）。
        wing.root.yScale = Mth.lerp(turn, 1, STAB_WIDTH);
        return wing;
    }
    /** 引き伸ばしの上限を超える分は、伸びている途中から先の節の羽を重ねて足す。羽は引き伸ばさないので太さが保たれる。 */
    private void extendStab(Wing wing, PoseStack pose, MultiBufferSource buffers, float alpha, float glow) {
        float scale = TIER_SCALE[wing.tier];
        float beyond = stabShown / stabStretch - WING_LENGTH * scale;
        if (beyond <= 0) return;
        int count = Mth.ceil(beyond / EXTEND_STEP);
        float step = beyond / count;
        pose.pushPose();
        wings.translateAndRotate(pose);
        (wing.right ? mountRight : mountLeft).translateAndRotate(pose);
        wing.root.translateAndRotate(pose);
        var soft = buffers.getBuffer(faceType(glowLevel));
        int face = faceLight();
        for (int i = 1; i <= count; i++) extra(wing, pose, soft, TIP_START * scale + i * step, 1, 1, 1, alpha, face);
        if (glow > 0) {
            var bright = buffers.getBuffer(RenderType.eyes(TEXTURE));
            for (int i = 1; i <= count; i++) extra(wing, pose, bright, TIP_START * scale + i * step, glow, glow, glow, 1, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
    private static void extra(Wing wing, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float at, float r, float g, float b, float a, int light) {
        pose.pushPose();
        pose.translate(at / 16, 0, 0);
        for (int n : EXTEND_FEATHERS) wing.tip.getChild(wing.name + n).render(pose, buffer, light, OverlayTexture.NO_OVERLAY, r, g, b, a);
        pose.popPose();
    }

    /** 防御の球の半径（モデルの単位）と中心の高さ、球の上の羽の長さと幅。羽を短くして球面に接する向きで重ね、とげを出さない。 */
    private static final float BALL_RADIUS = 22, BALL_Y = 8, BALL_FEATHER_LENGTH = 15, BALL_FEATHER_WIDTH = 10;
    /** 内側の層: 席を回す角度（度）と、外側より内へ下げる量。 */
    private static final float[][] BALL_LAYERS = {{61, .8f}, {127, 1.6f}};

    /**
     * 防御: 翼を構成する羽そのものを球面へ動かして体を包む。左の翼の羽が左半球、右の翼の羽が右半球を受け持つので、片側の防御は半球になる。
     * 羽は球面に接する向きで、中央を席に合わせて置くので、先が外へ突き出さない。羽は今の翼の姿勢から席へ補間するので、翼が巻き込んで球になる。
     * 球の面は向きによって灰色に沈むので、光の影響を受けない半透明の描画を使う。
     */
    private void ball(ClientDarkWings.State state, PoseStack pose, MultiBufferSource buffers, float partial, float age, boolean firstPerson) {
        float guard = state.guard(partial);
        int dir = state.guardDir();
        if (guard <= 0 || dir == 3) return;
        // 100%は今までどおり（自分で光る）。下げたときは翼の面と同じく周りの明るさを受ける。
        var buffer = buffers.getBuffer(glowLevel >= 1 ? RenderType.beaconBeam(TEXTURE, true) : RenderType.entityTranslucent(TEXTURE));
        int face = faceLight();
        var rest = new org.joml.Vector3f(); var from = new org.joml.Quaternionf();
        // 内側の層: 同じ羽の絵を、席をx軸まわりに回した位置へ足して隙間を埋める。翼から巻き込む羽より後から現れる。
        float inner = firstPerson ? 0 : Mth.clamp(guard * 2 - 1, 0, 1);
        float own = firstPerson ? FIRST_PERSON_BALL_ALPHA : 1;
        if (inner > 0) {
            for (var layer : BALL_LAYERS) {
                var spin = new org.joml.Quaternionf().rotationX(layer[0] * Mth.DEG_TO_RAD);
                var at = new org.joml.Vector3f();
                for (var feather : feathers) {
                    if (!(dir == 0 || (dir == 1) == feather.right)) continue;
                    spin.transform(feather.ballAt, at);
                    float radius = (BALL_RADIUS - layer[1]) / 16;
                    pose.pushPose();
                    pose.translate(at.x * radius, BALL_Y / 16 + at.y * radius, at.z * radius);
                    pose.mulPose(new org.joml.Quaternionf(spin).mul(feather.ballTurn));
                    pose.scale(BALL_FEATHER_LENGTH * 1.25f / feather.length, BALL_FEATHER_WIDTH * 1.25f / feather.width, 1);
                    pose.translate(-feather.length / 2 / 16, 0, 0);
                    bare(feather, pose, buffer, inner, face);
                    pose.popPose();
                }
            }
        }
        for (var feather : feathers) {
            if (!(dir == 0 || (dir == 1) == feather.right)) continue;
            // 今の翼の姿勢での、この羽の位置と向き。
            var local = new PoseStack();
            for (var part : feather.chain) part.translateAndRotate(local);
            feather.part.translateAndRotate(local);
            var matrix = local.last().pose();
            matrix.getTranslation(rest);
            matrix.getUnnormalizedRotation(from).normalize();
            // 根元の節から順に巻き込む。
            float k = Mth.clamp(guard * 1.35f - feather.segment * .15f, 0, 1);
            k = k * k * (3 - 2 * k);
            // 球面の上でわずかに呼吸する（羽が細かく動く）。
            float radius = (BALL_RADIUS + Mth.sin(age * .23f + feather.phase) * .25f) / 16;
            pose.pushPose();
            pose.translate(Mth.lerp(k, rest.x, feather.ballAt.x * radius), Mth.lerp(k, rest.y, BALL_Y / 16 + feather.ballAt.y * radius), Mth.lerp(k, rest.z, feather.ballAt.z * radius));
            pose.mulPose(from.slerp(feather.ballTurn, k, new org.joml.Quaternionf()));
            pose.scale(Mth.lerp(k, 1, BALL_FEATHER_LENGTH / feather.length), Mth.lerp(k, 1, BALL_FEATHER_WIDTH / feather.width), 1);
            pose.translate(-feather.length / 2 * k / 16, 0, 0);
            bare(feather, pose, buffer, own, face);
            pose.popPose();
        }
    }
    /** 羽の取付の位置と回転を外して、今の変換のまま描く。 */
    private static void bare(Feather feather, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float alpha, int light) {
        float px = feather.part.x, py = feather.part.y, pz = feather.part.z, rx = feather.part.xRot, ry = feather.part.yRot, rz = feather.part.zRot;
        feather.part.setPos(0, 0, 0);
        feather.part.setRotation(0, 0, 0);
        feather.part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, alpha);
        feather.part.setPos(px, py, pz);
        feather.part.setRotation(rx, ry, rz);
    }

    /** 基本姿勢に、展開・待機の揺れ・旋回の遅れを重ねる。 */
    private void pose(ClientDarkWings.State state, float open, float partial, float age) {
        wings.getAllParts().forEach(ModelPart::resetPose);
        float folded = 1 - open;
        float lagMid = state.lagMid(partial) * Mth.DEG_TO_RAD, lagTip = state.lagTip(partial) * Mth.DEG_TO_RAD;
        float lift = state.lift(partial) * Mth.DEG_TO_RAD;
        for (var wing : parts) {
            float breathe = Mth.sin(age * .07f + wing.tier * 1.1f) * (2.2f + wing.tier * .5f) * Mth.DEG_TO_RAD;
            // 収納: 背中へ畳む（根元を後ろ下へ、先の節を内へ）。
            float back = (wing.right ? 1 : -1) * 72 * Mth.DEG_TO_RAD * folded;
            wing.root.yRot += back;
            wing.root.zRot += folded * (58 - wing.tier * 22) * Mth.DEG_TO_RAD + breathe * .5f + lift * .5f;
            wing.mid.zRot += folded * 24 * Mth.DEG_TO_RAD + breathe + lift;
            wing.tip.zRot += folded * 24 * Mth.DEG_TO_RAD + breathe * 1.3f + lift * 1.2f;
            // 旋回: 根元は体と一緒に回り、中節と先端が少し遅れる。yRotは左右とも同じ向きに足せばよい（右は親がY軸で180度回っている）。
            wing.mid.yRot -= lagMid * .55f;
            wing.tip.yRot -= (lagTip - lagMid) * .9f + lagMid * .25f;
            actions(state, wing, partial);
        }
        // 羽1枚ずつの細かい揺れ。周期の違う2つの波を重ね、同じ動きに揃わないようにする。動いている間は小さく細かくなる。
        float rush = state.sweep(partial) / 42;
        float rate = 1 + rush * .8f, size = (1 - rush * .35f) * Mth.DEG_TO_RAD;
        for (var feather : feathers) {
            float slow = Mth.sin(age * .23f * rate + feather.phase), quick = Mth.sin(age * .61f * rate + feather.phase * 2.4f);
            feather.part.zRot += (slow * 1.7f + quick * .75f) * feather.amount * size;
            feather.part.xRot += (quick * 2.6f - slow * 1.1f) * feather.amount * size;
        }
        // 防御で球になる側は、翼としては描かない（ballが同じ羽を動かして描く）。
        float guard = state.guard(partial);
        int guardDir = state.guardDir();
        mountLeft.visible = !(guard > 0 && (guardDir == 0 || guardDir == 2));
        mountRight.visible = !(guard > 0 && (guardDir == 0 || guardDir == 1));
    }

    /** 動作を基本姿勢へ重ねる。backは翼を後ろへ流す向き（右は親がY軸で180度回っているので符号が逆）。 */
    private static void actions(ClientDarkWings.State state, Wing wing, float partial) {
        float back = (wing.right ? 1 : -1) * Mth.DEG_TO_RAD;
        wing.root.yRot += back * state.sweep(partial) * (1 - wing.tier * .12f);
        // 刺突: 刺す翼はaimStabが向ける。同じ側の他の翼は少しだけ前へ振る。
        float stab = state.stab(partial);
        if (stab > 0 && wing.right == (state.stabWing() % 2 == 1) && wing.tier * 2 + (wing.right ? 1 : 0) != state.stabWing()) wing.root.yRot -= back * 15 * stab;
        // 光線: 六翼を前へ少し寄せて光を集める。
        if (state.rayOn()) wing.root.yRot -= back * 26;
        // 空気砲: 振りかぶって前へ振る。
        wing.root.yRot += back * 42 * state.windup(partial);
        wing.root.yRot -= back * 75 * state.swing(partial);
        // 防御: 受ける側の3枚（または6枚）を前へ回して包む。落下は下へ広げる。
        float guard = state.guard(partial);
        if (guard > 0) {
            int dir = state.guardDir();
            boolean mine = dir == 0 || dir == 3 || (dir == 1) == wing.right;
            // 落下は下へ広げる。それ以外の防御はballが羽を球へ動かす。
            if (mine && dir == 3) wing.root.zRot += (46 - wing.tier * 18) * Mth.DEG_TO_RAD * guard;
        }
    }

    /** テスト用の入口: 翼を描いた回数。 */
    public static long drawn() { return drawn; }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) { renderer.addLayer(anyLayer = new DarkWingsLayer(renderer)); renderer.addLayer(new DarkHaloLayer(renderer)); }
            }
        }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class FirstPerson {
        private FirstPerson() { }
        @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderLevelStageEvent event) { renderFirstPerson(event); }
    }
}
