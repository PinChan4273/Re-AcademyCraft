package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作MdParticleFactory（LambdaLib2のParticle上）: md_particle（またはLuck Mine Rayの絵）。大きさ0.05〜0.07、
 * アルファ76〜151、光の影響なし。25〜54tick生きてから20tickで消える（その前に既定の5tickのフェードイン）。
 * 漂う種類は速度を保ち、ブロックに当たる（既定のRigidbody）。Mine Rayのブロック用の種類は0.01で落下し、
 * ブロックをすり抜ける（専用のRigidbody）。
 */
public final class MdParticle extends TextureSheetParticle {
    static final int FADE_IN = 5, FADE_OUT = 20;
    private final int life;
    private final float startAlpha;
    MdParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean block) {
        super(level, x, y, z);
        RandomSource random = level.random;
        xd = vx; yd = vy; zd = vz;
        friction = 1;
        gravity = block ? .01f / .04f : 0; // バニラのgravityは1tickあたり0.04倍される
        hasPhysics = !block;
        quadSize = (.05f + random.nextFloat() * .02f) / 2;
        startAlpha = (76 + random.nextInt(152 - 76)) / 255f;
        alpha = 0;
        life = 25 + random.nextInt(55 - 25);
        lifetime = life + FADE_OUT + 1;
        pickSprite(sprites);
    }
    @Override public void tick() {
        super.tick();
        if (age > life) alpha = Math.max(0, 1 - (float) (age - life) / FADE_OUT) * startAlpha;
        else if (age < FADE_IN) alpha = startAlpha * age / FADE_IN;
        else alpha = startAlpha;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Providers {
        record Provider(SpriteSet sprites, boolean block) implements ParticleProvider<SimpleParticleType> {
            @Override public MdParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
                return new MdParticle(level, x, y, z, vx, vy, vz, sprites, block);
            }
        }
        @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AcademyParticles.MD.get(), sprites -> new Provider(sprites, false));
            event.registerSpriteSet(AcademyParticles.MD_LUCK.get(), sprites -> new Provider(sprites, false));
            event.registerSpriteSet(AcademyParticles.MD_BLOCK.get(), sprites -> new Provider(sprites, true));
            event.registerSpriteSet(AcademyParticles.MD_LUCK_BLOCK.get(), sprites -> new Provider(sprites, true));
        }
    }
}
