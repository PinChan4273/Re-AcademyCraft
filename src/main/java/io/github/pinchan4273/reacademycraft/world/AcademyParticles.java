package io.github.pinchan4273.reacademycraft.world;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 原作の効果のための粒子の型。いずれもクライアントだけで生成し、送らない。 */
public final class AcademyParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE, "academy");
    /** 原作TPParticleFactory: effects/tp_particle.png。 */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> TP = PARTICLES.register("tp", () -> new SimpleParticleType(false));
    /** 原作MdParticleFactory: effects/md_particle.png（漂う）と、Luck Mine Ray独自の絵。 */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MD = PARTICLES.register("md", () -> new SimpleParticleType(false)),
            MD_LUCK = PARTICLES.register("md_luck", () -> new SimpleParticleType(false));
    /** 同じものを、Mine Rayが削るブロックから散らすもの: 重力0.01、ブロックをすり抜ける。 */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> MD_BLOCK = PARTICLES.register("md_block", () -> new SimpleParticleType(false)),
            MD_LUCK_BLOCK = PARTICLES.register("md_luck_block", () -> new SimpleParticleType(false));
    /** 原作SmokeEffect: effects/smokes.pngの4分割のいずれか（Groundshock）。最後に追加する。 */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SMOKE = PARTICLES.register("smoke", () -> new SimpleParticleType(false));
    /** 原作EntitySilbarnの破片: entities/silbarn_frag.png、転がる。 */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SILBARN_FRAG = PARTICLES.register("silbarn_frag", () -> new SimpleParticleType(false));
    private AcademyParticles() { }
    public static void register(IEventBus modBus) { PARTICLES.register(modBus); }
}
