package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.network.SkillVisualMove;
import io.github.pinchan4273.reacademycraft.skill.PlasmaCannon;
import io.github.pinchan4273.reacademycraft.visual.LegacyTornado;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import io.github.pinchan4273.reacademycraft.client.render.AcademyPipelines;
import io.github.pinchan4273.reacademycraft.client.render.PlasmaUniforms;
import io.github.pinchan4273.reacademycraft.client.render.ShaderPackDraw;
import io.github.pinchan4273.reacademycraft.client.render.WorldDraw;
import io.github.pinchan4273.reacademycraft.client.render.WorldSubmits;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * contextを持つすべてのclientでの原作PlasmaCannonContextC:
 * - PlasmaBodyEffect: カメラへ向いた22ブロックの四角に、plasma_bodyのシェーダーでmetaballをray marchingで描く。
 *   大きな球4つ（1〜1.5）は中心から1.5以内、小さな球4〜5個（0.1〜0.3）は3以内にあり、それぞれ自身の正弦で回る。
 *   毎秒0.3でフェードインし、contextの後は毎秒1でフェードアウトし、alphaの2乗で描く。
 * - Tornadoのentity: 充電を始めた場所の下の地面（20下までの光線）に立つTornadoEffect(12, 8, 1, 0.3)。
 *   20tickでフェードインし、本体が飛ぶかcontextが終わると20tickでフェードアウトする。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientPlasmaCannonEffect {
    static final Identifier TORNADO_TEXTURE = Identifier.fromNamespaceAndPath("academy", "textures/effects/tornado_ring.png");

    record Trig(float amp, float speed, float dphase) { float phase(float time) { return speed * time - dphase; } }
    record Ball(float size, float cx, float cy, float cz, Trig h, Trig v) { }

    static final class Effect {
        final List<Ball> balls = new ArrayList<>();
        @Nullable Vec3 position, previous, tornadoAt;
        @Nullable LegacyTornado tornado;
        int phase, tornadoTicks, deadTick; boolean terminated, tornadoDead;
        /** 球の大きさの倍率。自然能力は常に1（外部の演出だけが変える）。 */
        float scale = 1;
        float alpha; double initTime = now(), lastAlphaTime = now();
        Effect(Random r) {
            for (int i = 0; i < 4; i++)
                balls.add(new Ball(range(r, 1, 1.5f), range(r, -1.5f, 1.5f), range(r, -1.5f, 1.5f), range(r, -1.5f, 1.5f), trig(r, 1), trig(r, 1)));
            int small = 4 + r.nextInt(2);
            for (int i = 0; i < small; i++)
                balls.add(new Ball(range(r, .1f, .3f), range(r, -3, 3), range(r, -3, 3), range(r, -3, 3), trig(r, 2.5f), trig(r, 2.5f)));
        }
        static Trig trig(Random r, float size) { return new Trig(range(r, 1.4f, 2f) * size, range(r, .5f, .7f), range(r, 0, (float) Math.PI * 2)); }
        static float range(Random r, float min, float max) { return min + r.nextFloat() * (max - min); }
        /** 原作Tornado.alpha。 */
        float tornadoAlpha() { return !tornadoDead ? (tornadoTicks < 20 ? tornadoTicks / 20f : 1) : 1 - deadTick / 20f; }
    }
    private static final Map<Long, Effect> EFFECTS = new LinkedHashMap<>();
    private static final Random RANDOM = new Random();
    @Nullable private static ClientLevel world;
    private static long drawnBodies, drawnTornadoQuads;
    private ClientPlasmaCannonEffect() { }
    static double now() { return Util.getMillis() / 1000.0; }

    public static void receive(SkillVisual packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { EFFECTS.clear(); world = level; }
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) return;
        if (!packet.starts()) { var effect = EFFECTS.get(packet.token()); if (effect != null) effect.terminated = true; return; }
        if (PlasmaCannon.ID.equals(packet.kind())) EFFECTS.put(packet.token(), new Effect(RANDOM));
    }
    /** 外部の演出がまだ一覧にあるか（薄れている間も）。 */
    static boolean hasExternal(long token) { return EFFECTS.containsKey(token); }
    /** 外部の演出をすぐ取り除く（自然能力では呼ばない）。 */
    static void endExternal(long token) { EFFECTS.remove(token); }
    /** 外部の演出の球の大きさ（自然能力では呼ばない）。 */
    static void scaleExternal(long token, float scale) { var effect = EFFECTS.get(token); if (effect != null) effect.scale = scale; }
    public static void move(SkillVisualMove packet) {
        var effect = EFFECTS.get(packet.token());
        var level = Minecraft.getInstance().level;
        if (effect == null || level == null) return;
        effect.previous = effect.position == null ? packet.position() : effect.position;
        effect.position = packet.position(); effect.phase = packet.phase();
        // 消え終わった竜巻は作り直さない（deadTickが戻らず、演出がいつまでも一覧に残る。C200-P01）。
        if (effect.tornado == null && !effect.tornadoDead) {
            // 原作Tornado: 充電の位置から20ブロック下への光線が当たった所、無ければ20下。
            var from = packet.position(); var to = from.add(0, -20, 0);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            effect.tornadoAt = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            effect.tornado = new LegacyTornado(12, 8, 1, .3, RANDOM);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != world) { EFFECTS.clear(); world = level; return; }
        if (Minecraft.getInstance().isPaused()) return;
        EFFECTS.values().removeIf(effect -> {
            if (effect.tornado != null) {
                effect.tornadoTicks++;
                if (effect.phase == 1 || effect.terminated) effect.tornadoDead = true;
                if (effect.tornadoDead && ++effect.deadTick == 30) effect.tornado = null;
            }
            // 原作PlasmaBodyEffect.onUpdate: contextが終わり、本体がフェードアウトし切ったら消える。
            return effect.terminated && Math.abs(effect.alpha) <= 1e-3f && effect.tornado == null;
        });
    }

    /** 26.2: 1.21.1 の RenderLevelStageEvent（AFTER_PARTICLES）の描画に当たる。カメラと時刻をここで写し、提出は同じフレームの submit の段階。 */
    @SubscribeEvent public static void extract(ExtractLevelRenderStateEvent event) {
        if (EFFECTS.isEmpty()) return;
        var camera = event.getCamera().position();
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false); double time = now();
        for (var effect : EFFECTS.values()) {
            // 原作updateAlpha（毎フレーム）: 毎秒0.3で1へ近づき、終わった後は毎秒1で0へ近づく。
            if (effect.position != null) {
                float dt = (float) (time - effect.lastAlphaTime); effect.lastAlphaTime = time;
                float desired = effect.terminated ? 0 : 1, rate = effect.terminated ? 1 : .3f, delta = desired - effect.alpha;
                effect.alpha += Math.min(Math.abs(delta), dt * rate) * Math.signum(delta);
            }
        }
        WorldSubmits.add(event.getRenderState(), (draw, pose) -> {
            for (var effect : EFFECTS.values()) {
                if (effect.tornado != null && effect.tornadoAt != null) tornado(draw, pose, effect, camera, time);
                if (effect.position != null) body(draw, pose, effect, camera, partial, time);
            }
        });
    }

    /** 原作は竜巻をalphaテストなしで描いた（26.2 の vanilla の position_tex_color も 0 だけを捨てる）。 */
    private static final RenderType TORNADO = ShaderPackDraw.register(RenderType.create("academy_plasma_tornado",
            RenderSetup.builder(AcademyPipelines.TEX_COLOR_KEEP).withTexture("Sampler0", TORNADO_TEXTURE).createRenderSetup()), TORNADO_TEXTURE);
    private static final RenderType BODY = RenderType.create("academy_plasma_body", RenderSetup.builder(AcademyPipelines.PLASMA_BODY).createRenderSetup());

    private static void tornado(WorldDraw draw, com.mojang.blaze3d.vertex.PoseStack pose, Effect effect, Vec3 camera, double time) {
        float alpha = effect.tornadoAlpha() * .5f * .7f;
        if (alpha <= 0) return;
        var at = effect.tornadoAt.subtract(camera);
        var quads = effect.tornado.quads(effect.tornado.time(time));
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        draw.geometry(pose, TORNADO, (last, buffer) -> {
            var m = last.pose();
            for (var q : quads)
                for (int k = 0; k < 4; k++)
                    buffer.addVertex(m, (float) q.x()[k], (float) q.y()[k], (float) q.z()[k]).setUv((float) q.u()[k], (float) q.v()[k]).setColor(1f, 1f, 1f, alpha);
        });
        drawnTornadoQuads += quads.size();
        pose.popPose();
    }

    private static void body(WorldDraw draw, com.mojang.blaze3d.vertex.PoseStack pose, Effect effect, Vec3 camera, float partial, double now) {
        float alpha = effect.alpha * effect.alpha;
        var centre = effect.previous.lerp(effect.position, partial);
        var at = centre.subtract(camera);
        float elapsed = (float) (now - effect.initTime);
        int count = Math.min(PlasmaUniforms.MAX_BALLS, effect.balls.size());
        // カメラ基準のワールドの位置（シェーダーの camspace へは、下の uniforms の中で model-view を掛ける）。
        float[] world = new float[count * 4];
        for (int i = 0; i < count; i++) {
            var b = effect.balls.get(i);
            float hr = b.h().phase(elapsed), vt = b.v().phase(elapsed);
            world[i * 4] = (float) (at.x + effect.scale * (b.cx() + b.h().amp() * Mth.sin(hr)));
            world[i * 4 + 1] = (float) (at.y + effect.scale * (b.cy() + b.v().amp() * Mth.sin(vt)));
            world[i * 4 + 2] = (float) (at.z + effect.scale * (b.cz() + b.h().amp() * Mth.cos(hr)));
            world[i * 4 + 3] = effect.scale * b.size();
        }
        var poseMatrix = new Matrix4f(pose.last().pose());
        // 原作: 本体の位置に22の四角を置き、カメラへ向ける。
        double horizontal = Math.sqrt(at.x * at.x + at.z * at.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-at.x, at.z)), pitch = (float) -Math.toDegrees(Math.atan2(at.y, horizontal));
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + 180));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.scale(22 * effect.scale, 22 * effect.scale, 1);
        // シェーダーパックの間、Iris は独自のシェーダーで描いた物を画面へ届けない（ShaderPackCompat）。そこで本体は専用の描画先へ同じシェーダーで
        // 四角の全体を描き（ShaderPackDraw.plasmaBody）、その絵を vanilla のシェーダーで同じ四角へ貼る（plasmaComposite。1.21.1 の packComposite と同じ考え）。
        boolean pack = ShaderPackCompat.inUse();
        draw.geometry(pose, pack ? ShaderPackDraw.plasmaBody() : BODY, pack ? (last, buffer) -> {
            var m = last.pose();
            buffer.addVertex(m, -.5f, -.5f, 0).setUv(0, 0); buffer.addVertex(m, .5f, -.5f, 0).setUv(1, 0);
            buffer.addVertex(m, .5f, .5f, 0).setUv(1, 1); buffer.addVertex(m, -.5f, .5f, 0).setUv(0, 1);
        } : (last, buffer) -> {
            var m = last.pose();
            buffer.addVertex(m, -.5f, -.5f, 0); buffer.addVertex(m, .5f, -.5f, 0);
            buffer.addVertex(m, .5f, .5f, 0); buffer.addVertex(m, -.5f, .5f, 0);
        }, AcademyPipelines.PLASMA_BLOCK, modelView -> {
            // シェーダーのcamspaceの空間: ワールドのpose、次にシェーダーが掛けるmodel-view。
            var toCamera = new Matrix4f(modelView).mul(poseMatrix);
            float[] balls = new float[PlasmaUniforms.MAX_BALLS * 4];
            for (int i = 0; i < count; i++) {
                var p = new Vector4f(world[i * 4], world[i * 4 + 1], world[i * 4 + 2], 1).mul(toCamera);
                balls[i * 4] = p.x; balls[i * 4 + 1] = p.y; balls[i * 4 + 2] = -p.z; balls[i * 4 + 3] = world[i * 4 + 3];
            }
            return PlasmaUniforms.write(balls, count, alpha);
        });
        if (pack) {
            draw.geometry(pose, ShaderPackDraw.plasmaComposite(), (last, buffer) -> {
                var m = last.pose();
                var p = new org.joml.Vector3f();
                float[][] corners = {{-.5f, -.5f, 0, 0}, {.5f, -.5f, 1, 0}, {.5f, .5f, 1, 1}, {-.5f, .5f, 0, 1}};
                for (var c : corners) {
                    m.transformPosition(c[0], c[1], 0, p);
                    ShaderPackDraw.entityVertex(buffer, p.x, p.y, p.z, -1, c[2], c[3]);
                }
            });
            packComposites++;
        }
        pose.popPose();
        drawnBodies++;
    }
    private static long packComposites;
    /** テスト用: シェーダーパックの間に、専用の描画先から貼った回数。 */
    public static long packComposites() { return packComposites; }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { EFFECTS.clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawnBodies() { return drawnBodies; }
    public static long drawnTornadoQuads() { return drawnTornadoQuads; }
    public static int live() { return EFFECTS.size(); }
}
