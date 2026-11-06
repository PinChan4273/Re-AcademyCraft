package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 原作EntitySilbarnの破片（entities/silbarn_fragを使うLambdaLib2のParticle）: 大きさ0.1、重力は1tickあたり0.03、
 * 光の影響なし、5tickでフェードイン。customRotationにより、カメラを向かず自身のyaw・pitchで回る平らな破片になる:
 * ランダムなyawと±90以内のpitchから始まり、1tickあたり25 × (sin phi, cos phi)度転がる。phiは原作の単一の
 * decoratorと同じく、全破片で1回だけ決める。原作の粒子は終わらず落ちた所に残るが、こちらは100tick後に消える。
 */
public final class SilbarnFragParticle extends SingleQuadParticle {
    static final int FADE_IN = 5, LIFE = 100, FADE_OUT = 20;
    private static final double PHI = Math.random() * Math.PI * 2;
    private static final float SPIN_YAW = (float) (Math.sin(PHI) * 25), SPIN_PITCH = (float) (Math.cos(PHI) * 25);
    private float yaw, pitch, yawO, pitchO;
    SilbarnFragParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        xd = vx; yd = vy; zd = vz;
        // バニラは1tickあたり重力0.04、原作は0.03。
        gravity = .75f; friction = .98f;
        quadSize = .05f;
        lifetime = LIFE + FADE_OUT;
        yaw = yawO = random.nextFloat() * 360; pitch = pitchO = random.nextFloat() * 180 - 90;
        alpha = 0;
    }
    @Override public void tick() {
        yawO = yaw; pitchO = pitch;
        super.tick();
        yaw += SPIN_YAW; pitch += SPIN_PITCH;
        if (age > LIFE) alpha = Math.max(0, 1 - (float) (age - LIFE) / FADE_OUT);
        else alpha = Math.min(1, (float) age / FADE_IN);
    }
    @Override protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }
    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    /**
     * 自身のyaw・pitchで回した平らな四角形を、両面に描く（原作のcullFaceオフ）。
     * 26.2 の粒子のパイプライン TRANSLUCENT_PARTICLE は裏面を消す（withCull の指定が無く、既定が有効）。そこで 1.21.1・Forge と同じく、
     * 表の四角形と、巻き順を逆にした裏の四角形を 1 枚ずつ出す。どの向きでもちょうど 1 枚だけが見え、濃さも 1.21.1 と同じ 1 枚分になる。
     * （以前は同じ巻き順の物を 2 枚出していたので、カメラに裏を向けた破片は消え、表を向けた破片は 2 枚重なって濃くなっていた。）
     */
    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state, Camera camera, float partial) {
        var cam = camera.position();
        float px = (float) (Mth.lerp(partial, xo, x) - cam.x()), py = (float) (Mth.lerp(partial, yo, y) - cam.y()),
                pz = (float) (Mth.lerp(partial, zo, z) - cam.z());
        var turn = new Quaternionf().rotationYXZ(-Mth.lerp(partial, yawO, yaw) * Mth.DEG_TO_RAD, -Mth.lerp(partial, pitchO, pitch) * Mth.DEG_TO_RAD, 0);
        float size = getQuadSize(partial);
        int color = net.minecraft.util.ARGB.colorFromFloat(alpha, rCol, gCol, bCol), light = getLightCoords(partial);
        // 表: 頂点ごとの UV を 1.21.1 の頂点と同じ所に置くため、26.2 の頂点の順に合わせて U を入れ替える。
        state.add(getLayer(), px, py, pz, turn.x, turn.y, turn.z, turn.w, size, getU1(), getU0(), getV0(), getV1(), color, light);
        // 裏: 面を自身の Y 軸のまわりに 180 度返すと、同じ 4 隅が逆の巻き順で並ぶ。各隅の UV が表と同じになるよう U は入れ替えない。
        var back = new Quaternionf(turn).rotateY((float) Math.PI);
        state.add(getLayer(), px, py, pz, back.x, back.y, back.z, back.w, size, getU0(), getU1(), getV0(), getV1(), color, light);
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class SilbarnFragProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        SilbarnFragProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public SilbarnFragParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz, net.minecraft.util.RandomSource random) {
            return new SilbarnFragParticle(level, x, y, z, vx, vy, vz, sprites);
        }
        @SubscribeEvent public static void registerSilbarnFrag(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AcademyParticles.SILBARN_FRAG.get(), SilbarnFragProvider::new);
        }
    }
}
