package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.pinchan4273.reacademycraft.client.render.AcademyPipelines;
import io.github.pinchan4273.reacademycraft.client.render.CapturedBuffers;
import io.github.pinchan4273.reacademycraft.client.model.DarkWingsMesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 未元物質の六翼（W1）。プレイヤーの体の部品に付くレイヤーなので、通常／スリムの両スキン、しゃがみ、エリトラ姿勢に同じように付いて行く。
 * 明るさは光源に左右されない（白く発光して見える）。ランタイムはMinecraftのモデル部品だけを使い、バニラの粒子は使わない。
 */
public final class DarkWingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("academy", "textures/entity/dark_matter_wings.png");
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

    DarkWingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
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
     * 値は描く時の発光（{@link ClientSettings#drawnWingGlow()}）で、シェーダーパックの使用中は100%を90%として描く（2026-11-05のユーザーの決定）。
     * 面・発光の層・刺突・羽の球は、どれもこのglowLevelを読む。
     */
    private float glowLevel = 1;
    private int worldLight = LightCoordsUtil.FULL_BRIGHT;
    private void prepareGlow(int light) { glowLevel = ClientSettings.drawnWingGlow() / 100f; worldLight = light; }
    /** 面の明るさ: 周りの明るさから、発光の強さの分だけ最大の明るさへ寄せる。 */
    private int faceLight() {
        if (glowLevel >= 1) return LightCoordsUtil.FULL_BRIGHT;
        int block = Math.round(Mth.lerp(glowLevel, LightCoordsUtil.block(worldLight), 15)), sky = Math.round(Mth.lerp(glowLevel, LightCoordsUtil.sky(worldLight), 15));
        return LightCoordsUtil.pack(block, sky);
    }
    /** 面を描く描き方: 100%は自分で光る半透明（今までどおり）、それより下は周りの明るさを受ける半透明。 */
    private static RenderType faceType(float level) {
        return level >= 1 ? RenderTypes.entityTranslucentEmissive(TEXTURE) : RenderTypes.entityTranslucent(TEXTURE);
    }

    /**
     * 26.2: 1.21.1の{@code render}の引数は描画の状態から取る（partial・年齢は{@code partialTick}・{@code ageInTicks}、頭の向きはyRot・xRot）。
     * 頂点は{@link CapturedBuffers}へ書き、最後にまとめて提出する（翼の羽は1つの絵の中で同じ部品を何度も動かして描くので、部品のまま渡さない）。
     */
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState player, float headYaw, float headPitch) {
        // 自然能力の翼が無ければ、外部の演出（演出API版3）の翼を同じ描き方で描く。
        var state = ClientDarkWings.drawnState(player.id);
        if (state == null || player.isInvisible) return;
        prepareGlow(light);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        drawingId = player.id;
        var buffers = new CapturedBuffers();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var entity = level != null && level.getEntity(player.id) instanceof net.minecraft.world.entity.LivingEntity living ? living : null;
        draw(state, entity, pose, buffers, player.partialTick, player.ageInTicks, headYaw, headPitch, false);
        buffers.submit(collector);
        drawingId = -1;
        pose.popPose();
    }
    /** entityは外部の刺突の向きを求めるための描いている体（26.2の描画の状態からIDで引く。見つからなければnullで、外部の刺突は頭の向きで描く）。 */
    private void draw(ClientDarkWings.State state, @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity entity, PoseStack pose, CapturedBuffers buffers,
                      float partial, float age, float headYaw, float headPitch, boolean firstPerson) {
        float open = state.open(partial);
        if (open <= .001f) return;
        firstPersonNow = firstPerson;
        pose(state, open, partial, age);
        // 空気砲の風の柱も同じ根元から生やす（2026-11-03）。
        if (!firstPerson && drawingId >= 0 && (state.rayOn() || ClientDarkEffects.gusting(drawingId))) recordBeamRoots(pose);
        float reach = state.stabReach();
        var aimed = state.stabTarget();
        if (aimed != null && entity != null) {
            // 外部の刺突（演出API版3）: 頭の向きの代わりに、目から届く点への向きと距離を、体の向きからの角度にして同じ計算へ渡す。
            var d = aimed.subtract(entity.getEyePosition(partial));
            double length = d.length();
            if (length > 1e-4) {
                float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
                headYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)) - bodyYaw);
                headPitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(d.y / length, -1, 1)));
                reach = (float) length;
            }
        }
        Wing stabbing = aimStab(state, partial, headYaw, headPitch, reach);
        // 1人称では、刺している翼（drawStabが描く）だけを見せる（他の翼は視界の外なので、頭の中で切れた面を見せない）。
        if (firstPerson) for (var wing : parts) wing.root.visible = false;
        float alpha = Math.min(1, open * 1.4f), glow = GLOW * alpha * glowLevel;
        // 1.21のModelPart.renderは色を1つのARGBの整数で取る（Forge版は4つの小数）。
        wings.render(pose, buffers.getBuffer(faceType(glowLevel)), faceLight(), OverlayTexture.NO_OVERLAY, net.minecraft.util.ARGB.colorFromFloat(alpha, 1, 1, 1));
        // 面の向きで灰色に沈まないよう、同じ絵を無陰影で足して白く発光させる（発光の強さに比例。0%では足さない）。
        if (glow > 0) wings.render(pose, buffers.getBuffer(AcademyPipelines.eyesAdditive(TEXTURE)), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, net.minecraft.util.ARGB.colorFromFloat(1, glow, glow, glow));
        if (stabbing != null) drawStab(stabbing, pose, buffers, alpha, glow);
        ball(state, pose, buffers, partial, age, firstPerson);
        for (var wing : parts) { wing.root.visible = true; wing.mid.visible = true; }
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

    /**
     * 1人称では体の描画が走らないので、刺突と防御の間だけ、同じ羽を世界の描画で描く。
     * 26.2: 1.21.1のAFTER_ENTITIESの段階は、このMODの演出の提出の「半透明の地形の前」（{@code WorldSubmits.Stage.BEFORE_TRANSLUCENT_TERRAIN}）。
     */
    static void renderFirstPerson(io.github.pinchan4273.reacademycraft.client.render.WorldFrame event) {
        if (anyLayer == null) return;
        var client = net.minecraft.client.Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.getCameraEntity() != player || !client.options.getCameraType().isFirstPerson() || player.isInvisible()) return;
        var state = ClientDarkWings.drawnState(player.getId());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (state == null || (state.stab(partial) <= 0 && state.guard(partial) <= 0)) return;
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        var at = player.getPosition(partial).subtract(event.getCamera().position());
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.scale(-1, -1, 1);
        pose.scale(.9375f, .9375f, .9375f);
        pose.translate(0, -1.501f, 0);
        var buffers = new CapturedBuffers();
        anyLayer.prepareGlow(client.getEntityRenderDispatcher().getPackedLightCoords(player, partial));
        anyLayer.draw(state, player, pose, buffers, partial, player.tickCount + partial, Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - bodyYaw), player.getViewXRot(partial), true);
        buffers.submit(io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.current());
        pose.popPose();
    }

    /** 段ごとの翼の長さの倍率（生成スクリプトの表と同じ）。 */
    private static final float[] TIER_SCALE = {.9f, 1, .76f};
    /** 先の節の羽の長さ（倍率1）と、刺す翼を引き伸ばしてよい倍率。これを超える前に羽を足し始める。 */
    private static final float TIP_LENGTH = 42, MAX_STRETCH = 1.2f;
    /** 足す羽の間隔。翼の先の節の羽の長さより十分短くして、重ねて太さを保つ。 */
    private static final float EXTEND_STEP = 16;
    /**
     * 刺す翼の幅の倍率（3人称と1人称）。1人称では刺す翼が目のすぐ横を通って視界を大きく塞ぐので、広げ方を抑える。
     * 根元の節は元の幅のまま（体のすぐ後ろにあるので、広げると扇が体へ入る）。
     */
    private static final float STAB_WIDTH = 1.6f, FIRST_PERSON_STAB_WIDTH = 1.3f, ROOT_WIDTH = 1;
    /** 足す羽（先の節の、角度の小さい5枚の番号）。広がる2枚は先端の本来の翼にだけ残す。 */
    private static final int[] EXTEND_FEATHERS = {0, 1, 2, 3, 4};
    /** 体に当たらないかを測る中節の羽の数（根元の節の羽は全部測る）。 */
    private static final int PROBE_MID = 3;
    /** 曲がる側と曲がりの角が1秒に変わってよい角度（度）。刺している間に頭の向きが大きく変わっても、瞬間に跳ばずに回って移る。 */
    private static final float SIDE_TURN_RATE = 900, THETA_RATE = 900;
    /** 前の絵の曲がる側（取付の空間）と曲がりの角、翼の番号、時刻（ミリ秒）。 */
    private record Previous(org.joml.Vector3f side, float theta, int wing, long time) { }
    /** 前の絵の曲がり。3人称と1人称で分ける。刺突が終わると消す。 */
    private static final java.util.Map<ClientDarkWings.State, Previous> PREVIOUS = new java.util.WeakHashMap<>(), PREVIOUS_FIRST_PERSON = new java.util.WeakHashMap<>();
    /** 今の刺突のしなる線。aimStabが決めてdrawStabが使う。 */
    private final WingBend bend = new WingBend();
    /** 翼ごとの、体に当たらないかを測る羽（基本の姿勢から作る）。 */
    private final java.util.Map<Wing, List<WingBend.Probe>> probes = new java.util.IdentityHashMap<>();
    /** 今の刺突で見せる長さ・引き伸ばし・幅・曲がりへ移る割合（0は今の翼の姿勢、1はしなった姿勢）と、先の節の付け根・伸ばさない翼の長さ。 */
    private float stabShown, stabStretch, stabWidth, stabTurn, stabTipStart, stabNatural;
    /** 背中の向き（取付の空間）。 */
    private final org.joml.Vector3f stabBack = new org.joml.Vector3f();

    /**
     * 刺突（2026-11-05のユーザーの要望「元の直線的な翼のままの刺突を緩やかにしならせる。動く前の翼の形状と配置を維持しながら、足りない分枚数を増やして伸ばす」と、
     * 同じ日に決めた「しなりを相手までの距離で変える」。Forge 5f3d73d3 の試作の移植）: 元の刺突（翼をまっすぐ届いた点へ向けて伸ばす）の翼を、根元から届いた点までのしなる線
     * （{@link WingBend}）に沿わせる。線の形は距離で連続に変わる（{@link WingBend#design}）: 近いほど真横へ出て真円に近い弧で体の横を包むように回り、
     * 遠いほど外かつ前へ出て根元の近くでゆるく曲がり、長くまっすぐ伸びる。いつも翼と同じ側から回り、頭の上は越えない。
     * 根元・中節・先の節の羽は、まっすぐな翼での付け根の位置まで線に沿って進めた所へ、元の扇の角のまま置く（{@link #drawStab}）。届かない長さは、
     * 元と同じく先の節の羽を足して伸ばし、余る長さは相手を突き抜ける。サーバーが送るのは目から届いた点までの距離で、方向は頭の向きと視線の上下から
     * 描画側で求める。
     */
    private @javax.annotation.Nullable Wing aimStab(ClientDarkWings.State state, float partial, float headYaw, float headPitch, float stabReach) {
        var previous = firstPersonNow ? PREVIOUS_FIRST_PERSON : PREVIOUS;
        float extend = state.stab(partial);
        if (extend <= 0) { previous.remove(state); return null; }
        int number = state.stabWing();
        Wing wing = null;
        for (var candidate : parts) if (candidate.tier * 2 + (candidate.right ? 1 : 0) == number) wing = candidate;
        if (wing == null) return null;
        float yaw = headYaw * Mth.DEG_TO_RAD, pitch = headPitch * Mth.DEG_TO_RAD, reach = stabReach * 16;
        if (firstPersonNow) { wing.root.x += FIRST_PERSON_SIDE; wing.root.y += FIRST_PERSON_DOWN; }
        // 体の部品から見た届いた点（モデルは前が-z、下が+y。目は体の上端より4上）と、取付の空間の届いた点。
        float cp = Mth.cos(pitch);
        var target = toMount(wing, new org.joml.Vector3f(-cp * Mth.sin(yaw) * reach, -4 + Mth.sin(pitch) * reach, -cp * Mth.cos(yaw) * reach));
        var root = new org.joml.Vector3f(wing.root.x, wing.root.y, wing.root.z);
        // 背中の向き（体の+z）を取付の空間で。右は親がY軸で180度回っている。
        stabBack.set(0, 0, wing.right ? -1 : 1);
        stabTipStart = wing.mid.x + wing.tip.x;
        stabNatural = stabTipStart + TIP_LENGTH * TIER_SCALE[wing.tier];
        stabWidth = firstPersonNow ? FIRST_PERSON_STAB_WIDTH : STAB_WIDTH;
        var last = previous.get(state);
        var before = last != null && last.wing == number ? last : null;
        var choice = new WingBend.Shape();
        if (!WingBend.design(root, target, wing.right, probes.computeIfAbsent(wing, this::probesOf), stabNatural, MAX_STRETCH, stabWidth, ROOT_WIDTH,
                choice)) return null;
        var u = new org.joml.Vector3f(target).sub(root);
        float distance = u.length();
        u.div(distance);
        // 前の絵の曲がりから、時間に応じた角度までしか変えない。
        long now = net.minecraft.util.Util.getMillis();
        var side = new org.joml.Vector3f(choice.side);
        float theta = choice.theta;
        if (before != null) {
            float seconds = Mth.clamp((now - before.time) / 1000f, .005f, .2f);
            var from = new org.joml.Vector3f(u).mul(-before.side.dot(u)).add(before.side);
            if (from.lengthSquared() > 1e-6f) {
                from.normalize();
                float limit = SIDE_TURN_RATE * seconds, between = WingBend.angle(from, side);
                if (between > limit) {
                    // 届いた点の向きのまわりに回す（曲がる側はいつもその向きに直角）。
                    float sign = new org.joml.Vector3f(from).cross(side).dot(u) < 0 ? -1 : 1;
                    side.set(from).rotateAxis(sign * limit * Mth.DEG_TO_RAD, u.x, u.y, u.z);
                }
            }
            theta = before.theta + Mth.clamp(theta - before.theta, -THETA_RATE * seconds, THETA_RATE * seconds);
        }
        if (!bend.set(root, u, side, theta, distance, choice.radius, choice.curl)) {
            side.set(choice.side); theta = choice.theta;
            if (!bend.set(root, u, side, theta, distance, choice.radius, choice.curl)) return null;
        }
        previous.put(state, new Previous(side, theta, number, now));
        // 伸びの最初の4割で今の翼の姿勢からしなった姿勢へ移り、戻りの最後の4割で今の翼の姿勢へ戻る（元の刺突と同じ）。
        stabTurn = Math.min(1, extend * 2.5f);
        stabShown = bend.length * extend;
        stabStretch = Mth.clamp(stabShown / stabNatural, 1, MAX_STRETCH);
        // 刺す翼はdrawStabが羽1枚ずつ描く。
        wing.root.visible = false;
        return wing;
    }
    /**
     * 翼の、体に当たらないかを測る羽（根元の節の全部と中節の最初の{@link #PROBE_MID}枚）。基本の姿勢の付け根の位置と回転を使う。
     * 26.2: 基本の姿勢（{@code PartPose}）はrecordなので、値は{@code x()}などで読む（Forge版は項目）。
     */
    private List<WingBend.Probe> probesOf(Wing wing) {
        var list = new ArrayList<WingBend.Probe>();
        int mid = 0;
        for (var feather : feathers) {
            if (feather.chain[2] != wing.root || feather.segment > 1 || (feather.segment == 1 && mid++ >= PROBE_MID)) continue;
            var pose = feather.part.getInitialPose();
            float base = (feather.segment == 1 ? wing.mid.getInitialPose().x() : 0) + pose.x();
            var turn = new org.joml.Matrix3f().rotationZYX(pose.zRot(), pose.yRot(), pose.xRot());
            list.add(new WingBend.Probe(base, turn, feather.length, feather.width, feather.segment == 0));
        }
        return list;
    }
    /** 体の部品の空間の位置を、取付の空間へ。vを書き換えて返す。 */
    private static org.joml.Vector3f toMount(Wing wing, org.joml.Vector3f v) {
        return wing.right ? v.set(-1.5f - v.x, v.y - 3, 3 - v.z) : v.set(v.x - 1.5f, v.y - 3, v.z - 3);
    }
    /**
     * 刺す翼を描く: 根元・中節・先の節の羽を、まっすぐな翼での付け根の位置（引き伸ばし込み）までしなる線に沿って進めた所へ1枚ずつ、元の扇の角のまま置く。
     * 届かない分は、先の節の羽（{@link #EXTEND_FEATHERS}）を先の節の付け根から線に沿って重ねて足す。伸び始めと戻り終わりは、今の翼の姿勢と混ぜる。
     * 26.2: 頂点は他の翼と同じ{@link CapturedBuffers}へ書き、光る層は{@link AcademyPipelines#eyesAdditive}（Forge版はRenderType.eyes）。
     */
    private void drawStab(Wing wing, PoseStack pose, CapturedBuffers buffers, float alpha, float glow) {
        var mount = wing.right ? mountRight : mountLeft;
        if (!mount.visible) return;
        pose.pushPose();
        wings.translateAndRotate(pose);
        mount.translateAndRotate(pose);
        // 今の翼の姿勢での、各節の付け根の位置と回転（取付の空間）。伸び始めと戻り終わりに混ぜる。
        var segments = new org.joml.Matrix4f[3];
        segments[0] = new org.joml.Matrix4f().translation(wing.root.x, wing.root.y, wing.root.z).rotateZYX(wing.root.zRot, wing.root.yRot, wing.root.xRot);
        segments[1] = new org.joml.Matrix4f(segments[0]).translate(wing.mid.x, wing.mid.y, wing.mid.z).rotateZYX(wing.mid.zRot, wing.mid.yRot, wing.mid.xRot);
        segments[2] = new org.joml.Matrix4f(segments[1]).translate(wing.tip.x, wing.tip.y, wing.tip.z).rotateZYX(wing.tip.zRot, wing.tip.yRot, wing.tip.xRot);
        stabLayer(wing, segments, pose, buffers.getBuffer(faceType(glowLevel)), 1, alpha, faceLight());
        if (glow > 0) stabLayer(wing, segments, pose, buffers.getBuffer(AcademyPipelines.eyesAdditive(TEXTURE)), glow, 1, LightCoordsUtil.FULL_BRIGHT);
        pose.popPose();
    }
    private void stabLayer(Wing wing, org.joml.Matrix4f[] segments, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float shade, float alpha, int light) {
        for (var feather : feathers) {
            if (feather.chain[2] != wing.root) continue;
            float base = (feather.segment == 0 ? 0 : feather.segment == 1 ? wing.mid.x : stabTipStart) + feather.part.x;
            stabFeather(feather.part, base, feather.length, feather.segment == 0, segments[feather.segment], pose, buffer, shade, alpha, light);
        }
        // 届かない分: 先の節の羽を、先の節の付け根から線に沿って重ねて足す。
        float beyond = stabShown / stabStretch - stabNatural;
        if (beyond <= 0) return;
        int count = Mth.ceil(beyond / EXTEND_STEP);
        float step = beyond / count;
        for (int i = 1; i <= count; i++) {
            for (int n : EXTEND_FEATHERS) {
                var part = wing.tip.getChild(wing.name + n);
                var cube = part.getRandomCube(net.minecraft.util.RandomSource.create(0));
                stabFeather(part, stabTipStart + i * step + part.x, cube.maxX - cube.minX, false, null, pose, buffer, shade, alpha, light);
            }
        }
    }
    /**
     * 羽1枚を、付け根baseまでしなる線に沿って進めた所へ置き、羽の長さの先の線の点（弦）へ向けて描く。羽の節の中の位置は外し、羽自身の角（扇）は残す。
     * 引き伸ばしと幅の倍率は線の向きの空間で掛ける（元の刺突と同じく、扇の広がりも幅と一緒に広がる）。
     * segmentがあれば、伸び始めと戻り終わりに今の翼の姿勢（その節の付け根の位置と回転）と混ぜる。
     * 26.2: ModelPart.renderは色を1つのARGBの整数で取る（Forge版は4つの小数）。
     */
    private void stabFeather(ModelPart part, float base, float length, boolean root, @javax.annotation.Nullable org.joml.Matrix4f segment, PoseStack pose,
                             com.mojang.blaze3d.vertex.VertexConsumer buffer, float shade, float alpha, int light) {
        var at = new org.joml.Vector3f();
        var turn = bend.feather(base * stabStretch, length * stabStretch, stabBack, at, new org.joml.Matrix3f()).getNormalizedRotation(new org.joml.Quaternionf());
        float sx = stabStretch, sy = root ? ROOT_WIDTH : stabWidth;
        if (segment != null && stabTurn < 1) {
            var restAt = segment.transformPosition(part.x, 0, 0, new org.joml.Vector3f());
            var restTurn = segment.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
            at.set(restAt.lerp(at, stabTurn));
            turn = restTurn.slerp(turn, stabTurn);
            sx = Mth.lerp(stabTurn, 1, sx); sy = Mth.lerp(stabTurn, 1, sy);
        }
        pose.pushPose();
        pose.translate(at.x / 16, at.y / 16, at.z / 16);
        pose.mulPose(turn);
        pose.scale(sx, sy, 1);
        float x = part.x;
        part.x = 0;
        part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, net.minecraft.util.ARGB.colorFromFloat(alpha, shade, shade, shade));
        part.x = x;
        pose.popPose();
    }

    /**
     * 防御の球の半径（モデルの単位）と中心の高さ、外側の羽の長さと幅、内側の層の羽の長さと幅。
     * 平らな羽は球の外形を折れ線にする（長さ15では半径22で約40度の弦）。外側の羽を短くして折れ目を細かくし、内側の層は大きくして隙間を埋める。
     */
    private static final float BALL_RADIUS = 22, BALL_Y = 8, BALL_FEATHER_LENGTH = 11, BALL_FEATHER_WIDTH = 9, BALL_INNER_LENGTH = 14, BALL_INNER_WIDTH = 12;
    /** 内側の層: 席を回す角度（度）と、外側より内へ下げる量。 */
    private static final float[][] BALL_LAYERS = {{61, .8f}, {127, 1.6f}, {193, 2.4f}};

    /**
     * 防御: 翼を構成する羽そのものを球面へ動かして体を包む。左の翼の羽が左半球、右の翼の羽が右半球を受け持つので、片側の防御は半球になる。
     * 羽は球面に接する向きで、両端がちょうど球面に乗るよう中心を内へ下げて置くので、先が外へ突き出さない（2026-11-04のユーザーの要望「羽でなるべく球体に、刃先が飛び出さない」）。
     * 羽は今の翼の姿勢から、球の中心のまわりの弧に沿って席へ回り込むので、翼が前へ巻き込んで球になる。
     * 球の面は向きによって灰色に沈むので、光の影響を受けない半透明の描画を使う。
     */
    private void ball(ClientDarkWings.State state, PoseStack pose, CapturedBuffers buffers, float partial, float age, boolean firstPerson) {
        float guard = state.guard(partial);
        int dir = state.guardDir();
        if (guard <= 0 || dir == 3) return;
        // 100%は今までどおり（自分で光る）。下げたときは翼の面と同じく周りの明るさを受ける。
        var buffer = buffers.getBuffer(glowLevel >= 1 ? RenderTypes.beaconBeam(TEXTURE, true) : RenderTypes.entityTranslucent(TEXTURE));
        int face = faceLight();
        var rest = new org.joml.Vector3f(); var from = new org.joml.Quaternionf();
        // 内側の層: 同じ羽の絵を、席をx軸まわりに回した位置へ足して隙間を埋める。翼から巻き込む羽より後から現れる。
        float inner = firstPerson ? 0 : Mth.clamp(guard * 2.5f - 1.2f, 0, 1);
        float own = firstPerson ? FIRST_PERSON_BALL_ALPHA : 1;
        if (inner > 0) {
            for (var layer : BALL_LAYERS) {
                var spin = new org.joml.Quaternionf().rotationX(layer[0] * Mth.DEG_TO_RAD);
                var at = new org.joml.Vector3f();
                float radius = seatRadius(BALL_RADIUS - layer[1], BALL_INNER_LENGTH) / 16;
                for (var feather : feathers) {
                    if (!(dir == 0 || (dir == 1) == feather.right)) continue;
                    spin.transform(feather.ballAt, at);
                    pose.pushPose();
                    pose.translate(at.x * radius, BALL_Y / 16 + at.y * radius, at.z * radius);
                    pose.mulPose(new org.joml.Quaternionf(spin).mul(feather.ballTurn));
                    pose.scale(BALL_INNER_LENGTH / feather.length, BALL_INNER_WIDTH / feather.width, 1);
                    pose.translate(-feather.length / 2 / 16, 0, 0);
                    bare(feather, pose, buffer, inner, face);
                    pose.popPose();
                }
            }
        }
        var center = new org.joml.Vector3f(0, BALL_Y / 16, 0);
        var at = new org.joml.Vector3f(); var turn = new org.joml.Quaternionf();
        for (var feather : feathers) {
            if (!(dir == 0 || (dir == 1) == feather.right)) continue;
            // 今の翼の姿勢での、この羽の位置と向き。
            var local = new PoseStack();
            for (var part : feather.chain) part.translateAndRotate(local);
            feather.part.translateAndRotate(local);
            var matrix = local.last().pose();
            matrix.getTranslation(rest);
            matrix.getUnnormalizedRotation(from).normalize();
            // 根元の節から順に巻き込む。構えの途中（約6割）で全部の羽が球に揃い、球の形の見える時間を長くする。
            float raw = Mth.clamp(guard * 1.7f - feather.segment * .2f, 0, 1);
            float k = smooth(raw);
            // 羽の形（長さ・幅・向き）は位置より先に球の羽へ揃える。長い羽が向きの途中で外へ突き出さないように。
            float shape = smooth(Mth.clamp(raw * 1.8f, 0, 1));
            // 球面の上でわずかに呼吸する（羽が細かく動く）。
            float radius = seatRadius(BALL_RADIUS + Mth.sin(age * .23f + feather.phase) * .25f, BALL_FEATHER_LENGTH) / 16;
            turn.set(feather.ballTurn);
            var outward = new org.joml.Vector3f(rest).sub(center);
            float distance = outward.length();
            if (distance > 1e-3f) {
                // 球の中心のまわりの弧に沿って回り込む（翼が前へ巻き込む）。中心からの距離は弧より先に球の半径へ寄せる。
                outward.div(distance);
                new org.joml.Quaternionf().slerp(new org.joml.Quaternionf().rotationTo(outward, feather.ballAt), k).transform(outward, at);
                // 今の方向での、球面に接する向き（席での向きを、今の方向まで回した物）。
                new org.joml.Quaternionf().rotationTo(feather.ballAt, at).mul(feather.ballTurn, turn);
                at.mul(Mth.lerp(smooth(Mth.clamp(raw * 1.5f, 0, 1)), distance, radius)).add(center);
            } else {
                at.set(feather.ballAt).mul(Mth.lerp(k, 0, radius)).add(center);
            }
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(from.slerp(turn, shape, new org.joml.Quaternionf()));
            pose.scale(Mth.lerp(shape, 1, BALL_FEATHER_LENGTH / feather.length), Mth.lerp(shape, 1, BALL_FEATHER_WIDTH / feather.width), 1);
            pose.translate(-feather.length / 2 * shape / 16, 0, 0);
            bare(feather, pose, buffer, own, face);
            pose.popPose();
        }
    }
    private static float smooth(float k) { return k * k * (3 - 2 * k); }
    /**
     * 平らな羽の中心を置く半径。羽は球面に接する平面なので、中心を球面に置くと両端が球の外へ出る。
     * 両端（中心から長さの半分）がちょうど球面に乗るよう、中心を内へ下げる。
     */
    private static float seatRadius(float radius, float length) {
        float half = length / 2;
        return Mth.sqrt(Math.max(radius * radius - half * half, 1));
    }
    /** 羽の取付の位置と回転を外して、今の変換のまま描く。 */
    private static void bare(Feather feather, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float alpha, int light) {
        float px = feather.part.x, py = feather.part.y, pz = feather.part.z, rx = feather.part.xRot, ry = feather.part.yRot, rz = feather.part.zRot;
        feather.part.setPos(0, 0, 0);
        feather.part.setRotation(0, 0, 0);
        feather.part.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, net.minecraft.util.ARGB.colorFromFloat(alpha, 1, 1, 1));
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

    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class Registration {
        private Registration() { }
        @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
                if (renderer != null) { renderer.addLayer(anyLayer = new DarkWingsLayer(renderer)); renderer.addLayer(new DarkHaloLayer(renderer)); }
            }
        }
    }
    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class FirstPerson {
        private FirstPerson() { }
        @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent event) {
            io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.on(event, io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.Stage.BEFORE_TRANSLUCENT_TERRAIN, DarkWingsLayer::renderFirstPerson);
        }
    }
}
