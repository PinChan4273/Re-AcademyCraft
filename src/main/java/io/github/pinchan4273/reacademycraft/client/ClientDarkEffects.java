package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.pinchan4273.reacademycraft.skill.DarkMatterRay;
import io.github.pinchan4273.reacademycraft.visual.LegacyTornado;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 未元物質の独自描画（バニラの粒子を使わない）。未元光線は六翼それぞれから、視線の先の狙っている点へ集まる。未元空気砲は六翼それぞれから、
 * 風の翼と同じ竜巻の輪でできた柱を生やし、照準の周りの射程の先まで輪を増やしながら伸ばす。サーバーからは動作の種類だけを受け、向きはその場の視線を使う。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkEffects {
    private static final ResourceLocation WIND = ClientStormWingEffect.TEXTURE;
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
        Gust(int entityId, int tier, boolean right, Vec3 spawnRoot, Vec3 end, Random random) {
            this.entityId = entityId; this.tier = tier; this.right = right; this.spawnRoot = spawnRoot; this.end = end;
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
        var level = entity.level();
        var eye = entity.getEyePosition();
        float yaw = (float) -Math.toRadians(entity.getYRot()), pitch = (float) -Math.toRadians(entity.getXRot());
        for (int tier = 0; tier < 3; tier++) {
            for (boolean right : new boolean[]{false, true}) {
                // 視線の空間（前が+z、左が+x、上が+y）での向き。
                var dir = new Vec3((right ? -1 : 1) * Math.sin(Math.toRadians(COLUMN_SIDE)), (1 - tier) * Math.sin(Math.toRadians(COLUMN_UP)), 1)
                        .normalize().xRot(pitch).yRot(yaw);
                var end = eye.add(dir.scale(range));
                var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
                GUST_LIST.add(new Gust(entity.getId(), tier, right, wingPoint(entity, tier, right, 1), end, RANDOM));
            }
        }
    }
    /** その実体の風の柱が出ているか（翼の描画が根元を記録するかの判断）。 */
    static boolean gusting(int entityId) {
        for (var gust : GUST_LIST) if (gust.entityId == entityId) return true;
        return false;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;
        GUST_LIST.removeIf(gust -> ++gust.age > GUST_TICKS + gust.tier);
    }

    /** 絵の番号（空の描画のたびに1つ進む）。翼の描画が記録した光線の根元が、この絵の物かを見分ける。 */
    private static long frame;
    static long frame() { return frame; }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { frame++; return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = event.getPartialTick();
        var camera = event.getCamera().getPosition();
        // 深度テストを明示する（2026-11-03のユーザーの指摘: 光線がブロック越しに見えた）。この段階で前に描いた物（他の技能・
        // 他のMOD）が深度テストを切ったまま戻さないと、それを引き継いで壁の手前に描いてしまう。入ってきたときの状態へ戻す。
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        int function = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        RenderSystem.enableDepthTest(); RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        try {
            beams(event, level, partial, camera);
            gusts(event, level, partial, camera);
        } finally {
            RenderSystem.depthFunc(function);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
    }

    /** 翼の出る位置（世界の座標）。翼の姿勢は光線の構えの固定の向きで計算する。高速飛行の傾きは数えない。 */
    private static Vec3 wingPoint(LivingEntity living, int tier, boolean right, float partial) {
        float k = TIER_SCALE[tier], length = BEAM_ROOT * k, z = TIER_ANGLE[tier] * Mth.DEG_TO_RAD, y = BEAM_YAW * Mth.DEG_TO_RAD;
        float side = right ? -1 : 1;
        // 体の部品の空間（前が-z、下が+y）。
        float mx = side * (1.5f + length * Mth.cos(y) * Mth.cos(z)), my = 3 + TIER_Y[tier] + length * Mth.cos(y) * Mth.sin(z), mz = 3 - length * Mth.sin(y);
        // 描画の変換と同じ: 上下左右を返して、胴の向きへ回す。
        double bodyYaw = Math.toRadians(180 - Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot));
        double px = -mx * .9375 / 16, py = (24 - my) * .9375 / 16, pz = mz * .9375 / 16;
        double cos = Math.cos(bodyYaw), sin = Math.sin(bodyYaw);
        return living.getPosition(partial).add(px * cos + pz * sin, py, pz * cos - px * sin);
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
    private static void beams(RenderLevelStageEvent event, net.minecraft.client.multiplayer.ClientLevel level, float partial, Vec3 camera) {
        var ids = ClientDarkWings.ids();
        if (ids.isEmpty()) return;
        var matrix = event.getPoseStack().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend(); RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull(); RenderSystem.depthMask(false);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
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
            var look = Vec3.directionFromRotation(event.getCamera().getXRot(), event.getCamera().getYRot());
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
                    } else {
                        // 自分の視点では翼がカメラのすぐ横にあり、同じ太さだと視界を塞ぐので細くする。
                        float core = own ? .025f : .09f, halo = own ? .07f : .24f;
                        beam(buffer, matrix, camera, from, target, core, .05f, 1, 1, 1, own ? .8f : .95f);
                        beam(buffer, matrix, camera, from, target, halo, .12f, .8f, .9f, 1, own ? .25f : .35f);
                    }
                    any = true;
                }
            }
        }
        var built = buffer.end();
        if (any) { BufferUploader.drawWithShader(built); drawn++; } else BufferUploader.reset();
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc();
    }
    private static void gusts(RenderLevelStageEvent event, net.minecraft.client.multiplayer.ClientLevel level, float partial, Vec3 camera) {
        if (GUST_LIST.isEmpty()) return;
        var pose = event.getPoseStack();
        var toWorld = new org.joml.Matrix4f(pose.last().pose()).invert();
        double time = Util.getMillis() / 1000.0;
        var client = Minecraft.getInstance();
        RenderSystem.setShader(LegacyAlphaShaders::worldPositionTexColor);
        RenderSystem.setShaderTexture(0, WIND);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
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
                if (level.getEntity(gust.entityId) instanceof LivingEntity living) {
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
                var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                var m = pose.last().pose();
                boolean any = false;
                for (var q : gust.tornado.quads(gust.tornado.time(time))) {
                    // 伸びた所までの輪だけを描く（輪が根元から順に増える）。1人称では目の前の根元を描かない。
                    double y = (q.y()[0] + q.y()[1]) / 2;
                    if (y > visible || y < skip) continue;
                    float a = alpha * (float) Math.min(1, (visible - y) / tipFade);
                    for (int k = 0; k < 4; k++)
                        buffer.vertex(m, (float) q.x()[k], (float) q.y()[k], (float) q.z()[k]).uv((float) q.u()[k], (float) q.v()[k]).color(1, 1, 1, a).endVertex();
                    any = true;
                }
                var built = buffer.end();
                if (any) { BufferUploader.drawWithShader(built); drawn++; } else BufferUploader.reset();
                pose.popPose();
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
    }
    /** カメラを向く細い帯。太さは根元と先で変えられる。 */
    private static void beam(BufferBuilder b, Matrix4f m, Vec3 camera, Vec3 from, Vec3 to, float w0, float w1, float r, float g, float bl, float a) {
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
    private static void vertex(BufferBuilder b, Matrix4f m, Vec3 p, float r, float g, float bl, float a) {
        b.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, bl, a).endVertex();
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { GUST_LIST.clear(); }
    /** テスト用の入口。 */
    public static long drawn() { return drawn; }
}
