package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.Immediate;
import io.github.pinchan4273.reacademycraft.client.render.WorldFrame;
import io.github.pinchan4273.reacademycraft.client.render.WorldSubmits;
import io.github.pinchan4273.reacademycraft.skill.DarkMatterRay;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import io.github.pinchan4273.reacademycraft.visual.LegacyTornado;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 未元物質の独自描画（バニラの粒子を使わない）。未元光線は六翼それぞれから、視線の先の狙っている点へ集まる。未元空気砲は六翼それぞれから、
 * 風の翼と同じ竜巻の輪でできた柱を生やし、照準の周りの射程の先まで輪を増やしながら伸ばす。サーバーからは動作の種類だけを受け、向きはその場の視線を使う。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkEffects {
    private static final Identifier WIND = ClientStormWingEffect.TEXTURE;
    /** 翼の段の向き（生成スクリプトの表と同じ）と、段ごとの長さの倍率と取付の高さ。 */
    private static final float[] TIER_ANGLE = {-36, -4, 27}, TIER_SCALE = {.9f, 1, .76f}, TIER_Y = {-2, 1, 4};
    /** 光線の出る位置の、翼の根元からの距離（モデルの単位）と、光線で翼を前へ寄せたあとの水平の向き（度）。 */
    private static final float BEAM_ROOT = 30, BEAM_YAW = 6;
    /**
     * 空気砲の風の柱（2026-11-03のユーザーの要望で、飛ぶ竜巻から、翼から生えて伸びる柱に変えた）。
     * 柱の出ている時間・伸びきるまで・消え始めるまで（tick）。柱は段ごとに1tickずつ遅れて出る（上の段から）。
     */
    private static final int GUST_TICKS = 12, GROW_TICKS = 5, FADE_FROM = 8;
    /** 柱の行き先の、照準からの左右と上下の開き（度）。左の翼は左へ、上の段は上へ。 */
    private static final double COLUMN_SIDE = 18, COLUMN_UP = 12;
    /** 輪の太さ（LegacyTornadoのsize。根元で半径およそ0.3、先で0.9〜1.5）と揺れ。 */
    private static final double COLUMN_RING = .55, COLUMN_SWAY = .8;
    /** 濃さ、伸びている先で薄くする長さ（ブロック）、1人称で目の前を塞がないよう描かない根元の長さ（ブロック）。 */
    private static final float COLUMN_ALPHA = .6f, TIP_FADE = 1, FIRST_PERSON_SKIP = 2.5f;

    private static final class Gust {
        final int entityId, tier; final boolean right; final Vec3 spawnRoot, end; final double height; final LegacyTornado tornado; int age;
        /** 外部の演出（公開APIのDARK_AIR_CANNON、演出API版3）の札。自然能力の柱はnull。 */
        @javax.annotation.Nullable final Object token;
        Gust(int entityId, int tier, boolean right, Vec3 spawnRoot, Vec3 end, Random random, @javax.annotation.Nullable Object token) {
            this.entityId = entityId; this.tier = tier; this.right = right; this.spawnRoot = spawnRoot; this.end = end; this.token = token;
            height = Math.max(1, spawnRoot.distanceTo(end));
            tornado = new LegacyTornado(height, COLUMN_RING, 1, COLUMN_SWAY, random);
        }
        /** 出てからのtick（段の遅れを引いた物）。 */
        float time(float partial) { return age + partial - tier; }
    }
    private static final List<Gust> GUST_LIST = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long drawn;
    private ClientDarkEffects() { }

    /**
     * 空気砲の放出: 六翼それぞれから風の柱を生やし、照準の周りの射程の先（壁があれば壁）まで、輪を増やしながら伸ばす。
     * 行き先は放出の時点の視線で決めて動かさない（サーバーも放出の時点で照準を固定する）。根元は翼に付いて動く。
     */
    static void gust(LivingEntity entity, float range) {
        columns(entity.level(), entity.getId(), entity.getEyePosition(), entity.getYRot(), entity.getXRot(), range, entity,
                (tier, right) -> wingPoint(entity, tier, right, 1), null);
    }
    /** 翼の根元（段と左右から）。 */
    private interface Roots { Vec3 at(int tier, boolean right); }
    /** 六翼それぞれの柱を、目から視線（yaw・pitchは度）の周りの射程の先（壁があれば壁）へ向けて足す。 */
    private static void columns(net.minecraft.world.level.Level level, int entityId, Vec3 eye, float yawDegrees, float pitchDegrees, float range,
                                @javax.annotation.Nullable net.minecraft.world.entity.Entity except, Roots roots, @javax.annotation.Nullable Object token) {
        float yaw = (float) -Math.toRadians(yawDegrees), pitch = (float) -Math.toRadians(pitchDegrees);
        for (int tier = 0; tier < 3; tier++) {
            for (boolean right : new boolean[]{false, true}) {
                // 視線の空間（前が+z、左が+x、上が+y）での向き。
                var dir = new Vec3((right ? -1 : 1) * Math.sin(Math.toRadians(COLUMN_SIDE)), (1 - tier) * Math.sin(Math.toRadians(COLUMN_UP)), 1)
                        .normalize().xRot(pitch).yRot(yaw);
                var end = eye.add(dir.scale(range));
                // 26.2: ClipContextは実体にnullを取らない。実体の無い外部の空気砲は、1.20.1のnullの実体と同じ空の判定の文脈で当てる。
                var context = except != null ? net.minecraft.world.phys.shapes.CollisionContext.of(except) : net.minecraft.world.phys.shapes.CollisionContext.empty();
                var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
                if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
                GUST_LIST.add(new Gust(entityId, tier, right, roots.at(tier, right), end, RANDOM, token));
            }
        }
    }
    /** 立ったプレイヤーの目の高さ（ブロック）。外部の空気砲は、{@code position}をこの高さの目とみなして翼の根元を決める。 */
    private static final double EYE_HEIGHT = 1.62;
    /**
     * 外部の演出（公開APIのDARK_AIR_CANNON、演出API版3。Forge 1.5.1 の 91f33867 の移植）の空気砲: {@code eye}を撃つ者の目とみなし、{@code target}への向きの周りへ、
     * 自然能力と同じ6本の柱を出す。根元は、その目の位置に立つ体（向きは{@code target}の水平の向き）の六翼の根元。実体には付かない。
     */
    static void gustExternal(net.minecraft.world.level.Level level, Vec3 eye, Vec3 target, Object token) {
        var d = target.subtract(eye);
        double length = d.length();
        if (length < 1e-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(d.y / length, -1, 1)));
        var feet = eye.subtract(0, EYE_HEIGHT, 0);
        columns(level, -1, eye, yaw, pitch, (float) length, null, (tier, right) -> wingPoint(feet, yaw, tier, right), token);
    }
    /** その札の柱が残っているか。 */
    static boolean gusting(Object token) {
        for (var gust : GUST_LIST) if (gust.token == token) return true;
        return false;
    }
    /** その札の柱をすぐ消す。 */
    static void endGusts(Object token) { GUST_LIST.removeIf(gust -> gust.token == token); }
    /** その実体の風の柱が出ているか（翼の描画が根元を記録するかの判断）。 */
    static boolean gusting(int entityId) {
        for (var gust : GUST_LIST) if (gust.entityId == entityId && entityId >= 0) return true;
        return false;
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        GUST_LIST.removeIf(gust -> ++gust.age > GUST_TICKS + gust.tier);
    }

    /**
     * 絵の番号（1つの絵の描画の準備のたびに1つ進む）。翼の描画が記録した光線の根元が、この絵の物かを見分ける。
     * 26.2: 1.21.1のAFTER_SKYの代わりに、同じ絵の準備（{@code ExtractLevelRenderStateEvent}）で進める。体の提出（翼の層）は
     * その後で、光線の提出（{@code SubmitCustomGeometryEvent}）は体の後なので、同じ絵の根元を読む順は1.21.1と同じ。
     */
    private static long frame;
    static long frame() { return frame; }
    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent extract) {
        frame++;
        WorldSubmits.on(extract, WorldSubmits.Stage.AFTER_PARTICLES, ClientDarkEffects::renderFrame);
    }
    private static void renderFrame(WorldFrame event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().position();
        // 深度テストを明示する（2026-11-03のユーザーの指摘: 光線がブロック越しに見えた）。26.2では描画が状態を持たないので、
        // 演出ごとに深度の比較ありのパイプラインを選ぶ（Immediateは提出のたびに1.21.1の入ってきた時の状態から始まる）。
        Immediate.enableDepthTest();
        beams(event, level, partial, camera);
        gusts(event, level, partial, camera);
    }

    /** 翼の出る位置（世界の座標）。翼の姿勢は光線の構えの固定の向きで計算する。高速飛行の傾きは数えない。 */
    private static Vec3 wingPoint(LivingEntity living, int tier, boolean right, float partial) {
        return wingPoint(living.getPosition(partial), Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot), tier, right);
    }
    /** 足元{@code feet}に立ち、胴の向きが{@code bodyYawDegrees}の体の翼の出る位置（外部の空気砲も同じ計算を使う）。 */
    private static Vec3 wingPoint(Vec3 feet, float bodyYawDegrees, int tier, boolean right) {
        float k = TIER_SCALE[tier], length = BEAM_ROOT * k, z = TIER_ANGLE[tier] * Mth.DEG_TO_RAD, y = BEAM_YAW * Mth.DEG_TO_RAD;
        float side = right ? -1 : 1;
        // 体の部品の空間（前が-z、下が+y）。
        float mx = side * (1.5f + length * Mth.cos(y) * Mth.cos(z)), my = 3 + TIER_Y[tier] + length * Mth.cos(y) * Mth.sin(z), mz = 3 - length * Mth.sin(y);
        // 描画の変換と同じ: 上下左右を返して、胴の向きへ回す。
        double bodyYaw = Math.toRadians(180 - bodyYawDegrees);
        double px = -mx * .9375 / 16, py = (24 - my) * .9375 / 16, pz = mz * .9375 / 16;
        double cos = Math.cos(bodyYaw), sin = Math.sin(bodyYaw);
        return feet.add(px * cos + pz * sin, py, pz * cos - px * sin);
    }
    /** 根元がカメラの前にあれば、狙いへ向かう線に沿ってカメラの面の少し後ろまで下げる（線の向きは変えない）。 */
    private static Vec3 behindCamera(Vec3 from, Vec3 target, Vec3 camera, Vec3 look) {
        double ahead = from.subtract(camera).dot(look) + .35;
        if (ahead <= 0) return from;
        var axis = target.subtract(from).normalize();
        double along = axis.dot(look);
        return along > .05 ? from.subtract(axis.scale(ahead / along)) : from;
    }
    /** 視線の先の、光線が狙っている点。 */
    private static Vec3 aimed(LivingEntity living, net.minecraft.client.multiplayer.ClientLevel level, float range, float partial) {
        var eye = living.getEyePosition(partial);
        var end = eye.add(living.getViewVector(partial).scale(range));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, living));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        double nearest = Double.MAX_VALUE;
        var result = end;
        for (var candidate : level.getEntities(living, new AABB(eye, end).inflate(1), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
                && !(e instanceof DarkMatterBeetle beetle && living.getUUID().equals(beetle.ownerId())))) {
            var at = candidate.getBoundingBox().inflate(.3).clip(eye, end);
            if (at.isPresent() && eye.distanceToSqr(at.get()) < nearest) { nearest = eye.distanceToSqr(at.get()); result = at.get(); }
        }
        return result;
    }
    private static void beams(WorldFrame event, net.minecraft.client.multiplayer.ClientLevel level, float partial, Vec3 camera) {
        var ids = ClientDarkWings.ids();
        if (ids.isEmpty()) return;
        var matrix = event.getPoseStack().last().pose();
        Immediate.setShader(Immediate.Shader.COLOR);
        Immediate.enableBlend(); Immediate.additiveBlendFunc();
        Immediate.disableCull(); Immediate.depthMask(false);
        var buffer = Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
        boolean any = false;
        for (int id : ids) {
            var state = ClientDarkWings.state(id);
            if (state == null || !state.rayOn() || !(level.getEntity(id) instanceof LivingEntity living)) continue;
            float charge = Math.min(1, (state.rayTicks() + partial) / DarkMatterRay.CHARGE_TICKS);
            var target = aimed(living, level, state.rayRange(), partial);
            var client = Minecraft.getInstance();
            boolean own = living == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
            // 体を描いた絵では、実際の翼の位置を根元にする（描画の空間から、カメラからの相対の世界の座標へ戻す）。
            var drawnRoots = own ? null : DarkWingsLayer.beamRoots(id);
            var toWorld = drawnRoots == null ? null : new org.joml.Matrix4f(matrix).invert();
            var look = Vec3.directionFromRotation(event.getCamera().xRot(), event.getCamera().yRot());
            for (int tier = 0; tier < 3; tier++) {
                for (boolean right : new boolean[]{false, true}) {
                    Vec3 from;
                    var drawnRoot = drawnRoots == null ? null : drawnRoots[tier * 2 + (right ? 1 : 0)];
                    if (drawnRoot != null) {
                        var relative = toWorld.transformPosition(new org.joml.Vector3f(drawnRoot));
                        from = camera.add(relative.x, relative.y, relative.z);
                    } else {
                        from = wingPoint(living, tier, right, partial);
                        // 1人称では翼を描かないので、根元が画面の中に浮いて途切れて見えないよう、カメラの後ろまで光線に沿って引き下げる。
                        if (own) from = behindCamera(from, target, camera, look);
                    }
                    if (charge < 1) {
                        // チャージ: 羽の位置に光が集まる。狙いへ向かって短く伸びるだけで、まだ撃たない。
                        var tip = from.add(target.subtract(from).normalize().scale(.5 * charge));
                        beam(buffer, matrix, camera, from, tip, .22f * charge, 0, 1, 1, 1, .9f * charge);
                    } else ray(buffer, matrix, camera, from, target, own);
                    any = true;
                }
            }
        }
        if (any) { buffer.end(); drawn++; }
        Immediate.depthMask(true); Immediate.enableCull(); Immediate.defaultBlendFunc();
    }
    private static void gusts(WorldFrame event, net.minecraft.client.multiplayer.ClientLevel level, float partial, Vec3 camera) {
        if (GUST_LIST.isEmpty()) return;
        var pose = event.getPoseStack();
        var toWorld = new org.joml.Matrix4f(pose.last().pose()).invert();
        double time = Util.getMillis() / 1000.0;
        var client = Minecraft.getInstance();
        // 1.21.1のLegacyAlphaShaders.worldPositionTexColor（アルファ0だけを捨てる）。Storm Wingの輪と同じ。
        Immediate.setShader(Immediate.Shader.TEX_COLOR_KEEP);
        Immediate.setShaderTexture(0, WIND);
        Immediate.enableBlend(); Immediate.defaultBlendFunc(); Immediate.disableCull(); Immediate.depthMask(false);
        try {
            for (var gust : GUST_LIST) {
                float t = gust.time(partial);
                if (t <= 0 || t > GUST_TICKS) continue;
                // 伸び（速く出て、先で緩む）と、終わりの薄まり。
                float grow = Math.min(1, t / GROW_TICKS);
                grow = 1 - (1 - grow) * (1 - grow);
                float alpha = COLUMN_ALPHA * (t < FADE_FROM ? 1 : Math.max(0, (GUST_TICKS - t) / (GUST_TICKS - FADE_FROM)));
                // 根元: 体を描いた絵では実際の翼の位置、描いていなければ（1人称・画面の外）計算した翼の位置、実体が居なければ出た時の位置。
                boolean own = false;
                Vec3 root = gust.spawnRoot;
                if (gust.entityId >= 0 && level.getEntity(gust.entityId) instanceof LivingEntity living) {
                    own = living == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
                    var drawnRoots = own ? null : DarkWingsLayer.beamRoots(gust.entityId);
                    var drawnRoot = drawnRoots == null ? null : drawnRoots[gust.tier * 2 + (gust.right ? 1 : 0)];
                    if (drawnRoot != null) {
                        var relative = toWorld.transformPosition(new org.joml.Vector3f(drawnRoot));
                        root = camera.add(relative.x, relative.y, relative.z);
                    } else root = wingPoint(living, gust.tier, gust.right, partial);
                }
                var axis = gust.end.subtract(root);
                double length = axis.length();
                if (length < .3) continue;
                axis = axis.scale(1 / length);
                // 柱の空間は高さ（gust.height）で作ってあるので、今の根元から行き先までの長さへ縦に合わせる。
                float stretch = (float) (length / gust.height);
                double visible = gust.height * grow, tipFade = TIP_FADE / stretch, skip = own ? FIRST_PERSON_SKIP / stretch : -1;
                var at = root.subtract(camera);
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) axis.x, (float) axis.y, (float) axis.z));
                pose.scale(1, stretch, 1);
                var buffer = Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
                var m = pose.last().pose();
                boolean any = false;
                for (var q : gust.tornado.quads(gust.tornado.time(time))) {
                    // 伸びた所までの輪だけを描く（輪が根元から順に増える）。1人称では目の前の根元を描かない。
                    double y = (q.y()[0] + q.y()[1]) / 2;
                    if (y > visible || y < skip) continue;
                    float a = alpha * (float) Math.min(1, (visible - y) / tipFade);
                    for (int k = 0; k < 4; k++)
                        buffer.addVertex(m, (float) q.x()[k], (float) q.y()[k], (float) q.z()[k]).setUv((float) q.u()[k], (float) q.v()[k]).setColor(1, 1, 1, a);
                    any = true;
                }
                if (any) { buffer.end(); drawn++; }
                pose.popPose();
            }
        } finally { Immediate.depthMask(true); Immediate.enableCull(); }
    }
    /** 撃っている光線1本（白い芯と淡い光の帯の2枚）。自分の視点では翼がカメラのすぐ横にあり、同じ太さだと視界を塞ぐので細くする。 */
    private static void ray(Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Vec3 from, Vec3 target, boolean own) {
        float core = own ? .025f : .09f, halo = own ? .07f : .24f;
        beam(buffer, matrix, camera, from, target, core, .05f, 1, 1, 1, own ? .8f : .95f);
        beam(buffer, matrix, camera, from, target, halo, .12f, .8f, .9f, 1, own ? .25f : .35f);
    }
    /**
     * 外部の演出の光線（公開APIの{@code BeamStyle.DARK_MATTER_RAY}、演出API版3。Forge 1.5.1 の 91f33867 の移植）を描く: 自然能力の撃っている光線（{@link #ray}、
     * 3人称の太さ）と同じ芯と光の帯を、同じ描き方（{@link Immediate}の position_color・加算・深度テストあり・深度を書かない・カリングなし）で1本ずつ。
     * 描いた四角形の数を返す。26.2: 提出は{@link WorldSubmits}の今の提出先（AFTER_PARTICLES）へ行き、シェーダーパックの間は自然能力の光線と同じく
     * {@code ShaderPackDraw}の描き方へ書き直される。描いた後は、提出の初めの状態（深度を書く・カリングあり・半透明の合成）へ戻す。
     */
    static int drawExternalRays(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, List<Vec3[]> rays) {
        if (rays.isEmpty()) return 0;
        var matrix = pose.last().pose();
        Immediate.enableDepthTest();
        Immediate.setShader(Immediate.Shader.COLOR);
        Immediate.enableBlend(); Immediate.additiveBlendFunc();
        Immediate.disableCull(); Immediate.depthMask(false);
        try {
            var buffer = Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
            for (var ray : rays) ray(buffer, matrix, camera, ray[0], ray[1], false);
            buffer.end();
            return rays.size() * 2;
        } finally { Immediate.depthMask(true); Immediate.enableCull(); Immediate.defaultBlendFunc(); }
    }
    /** カメラを向く細い帯。太さは根元と先で変えられる。 */
    private static void beam(Immediate.Vertices b, Matrix4f m, Vec3 camera, Vec3 from, Vec3 to, float w0, float w1, float r, float g, float bl, float a) {
        var axis = to.subtract(from);
        if (axis.lengthSqr() < 1e-4) return;
        var side = axis.cross(from.subtract(camera));
        if (side.lengthSqr() < 1e-6) return;
        side = side.normalize();
        var f = from.subtract(camera); var t = to.subtract(camera);
        vertex(b, m, f.add(side.scale(w0)), r, g, bl, a);
        vertex(b, m, t.add(side.scale(w1)), r, g, bl, a);
        vertex(b, m, t.subtract(side.scale(w1)), r, g, bl, a);
        vertex(b, m, f.subtract(side.scale(w0)), r, g, bl, a);
    }
    private static void vertex(Immediate.Vertices b, Matrix4f m, Vec3 p, float r, float g, float bl, float a) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, bl, a);
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { GUST_LIST.clear(); }
    /** テスト用の入口。 */
    public static long drawn() { return drawn; }
}
