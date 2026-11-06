package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作TPParticleFactory（LambdaLib2のParticle上）: tp_particle.pngのビルボード。大きさ0.1〜0.2、アルファ153〜204、
 * 光の影響なし、重力も抵抗も無し。5tickでフェードインし、20tick続き、さらに20tickでフェードアウトする。
 */
public final class TpParticle extends SingleQuadParticle {
    static final int FADE_IN = 5, LIFE = 20, FADE_OUT = 20;
    private final float startAlpha;
    TpParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(level.getRandom()));
        RandomSource random = this.random;
        xd = vx; yd = vy; zd = vz;
        gravity = 0; friction = 1;
        // LambdaLib2のSpriteは幅がsize。バニラの四角形は両側へquadSizeずつ広がる。
        quadSize = (.1f + random.nextFloat() * .1f) / 2;
        startAlpha = (153 + random.nextInt(204 - 153)) / 255f;
        alpha = 0;
        lifetime = LIFE + FADE_OUT + 1;
    }
    @Override public void tick() {
        super.tick();
        // LambdaLib2のParticle.onUpdate。ageがticksExistedに当たる。
        if (age > LIFE) alpha = Math.max(0, 1 - (float) (age - LIFE) / FADE_OUT) * startAlpha;
        else if (age < FADE_IN) alpha = startAlpha * age / FADE_IN;
        else alpha = startAlpha;
    }
    @Override protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }
    /** 原作hasLight = false: その場所の明るさに関係なく最大の明るさで描く。 */
    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    public float startAlpha() { return startAlpha; }

    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public TpParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz, net.minecraft.util.RandomSource random) {
            return new TpParticle(level, x, y, z, vx, vy, vz, sprites);
        }
        @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AcademyParticles.TP.get(), Provider::new);
        }
    }
}
