package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作SmokeEffect（generic/client/effect/SmokeEffect.scala）: effects/smokes.pngの4分割のいずれかを、幅2ブロックの
 * ビルボードで描く。毎tick自身の動きで漂い、重力も抵抗も無い。アルファは、生きた秒数を0.5〜0.7の寿命で割った値に対し:
 * 最初の0.3で上がり、1.5まで最大、2で0まで下がる。原作は4秒保持し、その後は見えない。
 */
public final class SmokeParticle extends TextureSheetParticle {
    private final float life;
    SmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx; yd = vy; zd = vz;
        gravity = 0; friction = 1; hasPhysics = false;
        // 原作の四角形は大きさ1で-1から1まで。
        quadSize = 1;
        life = .5f + random.nextFloat() * .2f;
        lifetime = 80;
        setSprite(sprites.get(random.nextInt(4), 3));
        alpha = 0;
    }
    @Override public void tick() {
        super.tick();
        alpha = alpha(age / 20f / life);
    }
    static float alpha(float dt) {
        if (dt <= .3f) return dt / .3f;
        if (dt <= 1.5f) return 1;
        if (dt <= 2) return 1 - (dt - 1.5f) / .5f;
        return 0;
    }
    /** 原作SmokeEffectRendererはアルファテストを切って描いていたので、消えていく最後まで見える（LegacyAlphaShaders）。 */
    @Override public ParticleRenderType getRenderType() { return LegacyAlphaShaders.PARTICLE_SHEET_TRANSLUCENT; }

    /**
     * Providerという名前にしない: Forgeのイベントバスは購読メソッドごとに、クラスの単純名・メソッド名・イベントから
     * 生成クラスの名前を決める。2つ目のProvider.register(RegisterParticleProvidersEvent)がTpParticle.Providerの
     * 枠を奪い、テレポーターの粒子がproviderを失っていた。
     */
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class SmokeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        SmokeProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public SmokeParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SmokeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
        @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AcademyParticles.SMOKE.get(), SmokeProvider::new);
        }
    }
}
