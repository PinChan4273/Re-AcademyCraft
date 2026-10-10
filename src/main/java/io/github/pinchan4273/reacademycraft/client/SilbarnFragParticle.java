package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 原作EntitySilbarnの破片（entities/silbarn_fragを使うLambdaLib2のParticle）: 大きさ0.1、重力は1tickあたり0.03、
 * 光の影響なし、5tickでフェードイン。customRotationにより、カメラを向かず自身のyaw・pitchで回る平らな破片になる:
 * ランダムなyawと±90以内のpitchから始まり、1tickあたり25 × (sin phi, cos phi)度転がる。phiは原作の単一の
 * decoratorと同じく、全破片で1回だけ決める。原作の粒子は終わらず落ちた所に残るが、こちらは100tick後に消える。
 */
public final class SilbarnFragParticle extends TextureSheetParticle {
    static final int FADE_IN = 5, LIFE = 100, FADE_OUT = 20;
    private static final double PHI = Math.random() * Math.PI * 2;
    private static final float SPIN_YAW = (float) (Math.sin(PHI) * 25), SPIN_PITCH = (float) (Math.cos(PHI) * 25);
    private float yaw, pitch, yawO, pitchO;
    SilbarnFragParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        // バニラは1tickあたり重力0.04、原作は0.03。
        gravity = .75f; friction = .98f;
        quadSize = .05f;
        lifetime = LIFE + FADE_OUT;
        yaw = yawO = random.nextFloat() * 360; pitch = pitchO = random.nextFloat() * 180 - 90;
        alpha = 0;
        pickSprite(sprites);
    }
    @Override public void tick() {
        yawO = yaw; pitchO = pitch;
        super.tick();
        yaw += SPIN_YAW; pitch += SPIN_PITCH;
        if (age > LIFE) alpha = Math.max(0, 1 - (float) (age - LIFE) / FADE_OUT);
        else alpha = Math.min(1, (float) age / FADE_IN);
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }
    /** 自身のyaw・pitchで回した平らな四角形を、両面に描く（原作のcullFaceオフ）。 */
    @Override public void render(VertexConsumer buffer, Camera camera, float partial) {
        var cam = camera.getPosition();
        float px = (float) (Mth.lerp(partial, xo, x) - cam.x()), py = (float) (Mth.lerp(partial, yo, y) - cam.y()),
                pz = (float) (Mth.lerp(partial, zo, z) - cam.z());
        var turn = new Quaternionf().rotationYXZ(-Mth.lerp(partial, yawO, yaw) * Mth.DEG_TO_RAD, -Mth.lerp(partial, pitchO, pitch) * Mth.DEG_TO_RAD, 0);
        float s = getQuadSize(partial);
        Vector3f[] corners = {new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, -1, 0)};
        for (var c : corners) c.rotate(turn).mul(s).add(px, py, pz);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partial);
        float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
        for (int i = 0; i < 4; i++) vertex(buffer, corners[i], uv[i], light);
        for (int i = 3; i >= 0; i--) vertex(buffer, corners[i], uv[i], light);
    }
    private void vertex(VertexConsumer buffer, Vector3f at, float[] uv, int light) {
        buffer.vertex(at.x(), at.y(), at.z()).uv(uv[0], uv[1]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    /**
     * この粒子専用の名前にしている: Forgeのイベントバスは購読メソッドごとに「単純クラス名・メソッド名・イベント」から
     * クラスを生成するため、同じ組が他にあると片方の登録が失われる。
     */
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SilbarnFragProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        SilbarnFragProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public SilbarnFragParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SilbarnFragParticle(level, x, y, z, vx, vy, vz, sprites);
        }
        @SubscribeEvent public static void registerSilbarnFrag(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AcademyParticles.SILBARN_FRAG.get(), SilbarnFragProvider::new);
        }
    }
}
