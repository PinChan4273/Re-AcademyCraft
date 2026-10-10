package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.network.SkillVisualMove;
import io.github.pinchan4273.reacademycraft.skill.PlasmaCannon;
import io.github.pinchan4273.reacademycraft.visual.LegacyTornado;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/**
 * contextを持つすべてのclientでの原作PlasmaCannonContextC:
 * - PlasmaBodyEffect: カメラへ向いた22ブロックの四角に、plasma_bodyのシェーダーでmetaballをray marchingで描く。
 *   大きな球4つ（1〜1.5）は中心から1.5以内、小さな球4〜5個（0.1〜0.3）は3以内にあり、それぞれ自身の正弦で回る。
 *   毎秒0.3でフェードインし、contextの後は毎秒1でフェードアウトし、alphaの2乗で描く。
 * - Tornadoのentity: 充電を始めた場所の下の地面（20下までの光線）に立つTornadoEffect(12, 8, 1, 0.3)。
 *   20tickでフェードインし、本体が飛ぶかcontextが終わると20tickでフェードアウトする。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientPlasmaCannonEffect {
    static final ResourceLocation TORNADO_TEXTURE = ClientStormWingEffect.TEXTURE;

    record Trig(float amp, float speed, float dphase) { float phase(float time) { return speed * time - dphase; } }
    record Ball(float size, float cx, float cy, float cz, Trig h, Trig v) { }

    static final class Effect {
        final List<Ball> balls = new ArrayList<>();
        @Nullable Vec3 position, previous, tornadoAt;
        @Nullable LegacyTornado tornado;
        int phase, tornadoTicks, deadTick; boolean terminated, tornadoDead;
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
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) { var effect = EFFECTS.get(packet.token()); if (effect != null) effect.terminated = true; return; }
        if (PlasmaCannon.ID.equals(packet.kind())) EFFECTS.put(packet.token(), new Effect(RANDOM));
    }
    public static void move(SkillVisualMove packet) {
        var effect = EFFECTS.get(packet.token());
        var level = Minecraft.getInstance().level;
        if (effect == null || level == null) return;
        effect.previous = effect.position == null ? packet.position() : effect.position;
        effect.position = packet.position(); effect.phase = packet.phase();
        if (effect.tornado == null) {
            // 原作Tornado: 充電の位置から20ブロック下への光線が当たった所、無ければ20下。
            var from = packet.position(); var to = from.add(0, -20, 0);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            effect.tornadoAt = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            effect.tornado = new LegacyTornado(12, 8, 1, .3, RANDOM);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        float partial = event.getPartialTick(); double time = now();
        for (var effect : EFFECTS.values()) {
            if (effect.tornado != null && effect.tornadoAt != null) tornado(event, effect, camera, time);
            if (effect.position != null) body(event, effect, camera, partial);
        }
    }

    private static void tornado(RenderLevelStageEvent event, Effect effect, Vec3 camera, double time) {
        float alpha = effect.tornadoAlpha() * .5f * .7f;
        if (alpha <= 0) return;
        var pose = event.getPoseStack(); var at = effect.tornadoAt.subtract(camera);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        // 原作は竜巻をalphaテストなしで描いた（LegacyAlphaShaders）。
        RenderSystem.setShader(LegacyAlphaShaders::positionTexColor);
        RenderSystem.setShaderTexture(0, TORNADO_TEXTURE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        try {
            var quads = effect.tornado.quads(effect.tornado.time(time));
            var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (var q : quads)
                for (int k = 0; k < 4; k++)
                    buffer.vertex(m, (float) q.x()[k], (float) q.y()[k], (float) q.z()[k]).uv((float) q.u()[k], (float) q.v()[k]).color(1, 1, 1, alpha).endVertex();
            BufferUploader.drawWithShader(buffer.end());
            drawnTornadoQuads += quads.size();
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); pose.popPose(); }
    }

    private static void body(RenderLevelStageEvent event, Effect effect, Vec3 camera, float partial) {
        var shader = DeveloperShaders.plasmaBody();
        if (shader == null) return;
        // 原作updateAlpha（毎フレーム）: 毎秒0.3で1へ近づき、終わった後は毎秒1で0へ近づく。
        double now = now(); float dt = (float) (now - effect.lastAlphaTime); effect.lastAlphaTime = now;
        float desired = effect.terminated ? 0 : 1, rate = effect.terminated ? 1 : .3f, delta = desired - effect.alpha;
        effect.alpha += Math.min(Math.abs(delta), dt * rate) * Math.signum(delta);
        float alpha = effect.alpha * effect.alpha;
        var centre = effect.previous.lerp(effect.position, partial);
        var at = centre.subtract(camera);
        var pose = event.getPoseStack();
        // シェーダーのcamspaceの空間: ワールドのpose、次にシェーダーが掛けるmodel-view。
        var toCamera = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        float elapsed = (float) (now - effect.initTime);
        int count = Math.min(16, effect.balls.size());
        try (var stack = MemoryStack.stackPush()) {
            FloatBuffer balls = stack.mallocFloat(16 * 4);
            for (int i = 0; i < 16; i++) {
                if (i >= count) { balls.put(0).put(0).put(0).put(0); continue; }
                var b = effect.balls.get(i);
                float hr = b.h().phase(elapsed), vt = b.v().phase(elapsed);
                var p = new Vector4f((float) (at.x + b.cx() + b.h().amp() * Mth.sin(hr)), (float) (at.y + b.cy() + b.v().amp() * Mth.sin(vt)),
                        (float) (at.z + b.cz() + b.h().amp() * Mth.cos(hr)), 1).mul(toCamera);
                balls.put(p.x).put(p.y).put(-p.z).put(b.size());
            }
            balls.flip();
            // 原作: 本体の位置に22の四角を置き、カメラへ向ける。
            double horizontal = Math.sqrt(at.x * at.x + at.z * at.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-at.x, at.z)), pitch = (float) -Math.toDegrees(Math.atan2(at.y, horizontal));
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw + 180));
            pose.mulPose(Axis.XP.rotationDegrees(-pitch));
            pose.scale(22, 22, 1);
            var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            buffer.vertex(m, -.5f, -.5f, 0).endVertex(); buffer.vertex(m, .5f, -.5f, 0).endVertex();
            buffer.vertex(m, .5f, .5f, 0).endVertex(); buffer.vertex(m, -.5f, .5f, 0).endVertex();
            var rendered = buffer.end();
            pose.popPose();
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            try {
                shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
                shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                shader.safeGetUniform("Alpha").set(alpha);
                shader.safeGetUniform("BallCount").set(count);
                shader.apply();
                // vec4の配列はJSONのuniformでは扱えないので、programへ直接設定する。
                int location = GL20.glGetUniformLocation(shader.getId(), "Balls");
                if (location >= 0) GL20.glUniform4fv(location, balls);
                BufferUploader.draw(rendered);
                shader.clear();
            } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
            drawnBodies++;
        }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { EFFECTS.clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawnBodies() { return drawnBodies; }
    public static long drawnTornadoQuads() { return drawnTornadoQuads; }
    public static int live() { return EFFECTS.size(); }
}
