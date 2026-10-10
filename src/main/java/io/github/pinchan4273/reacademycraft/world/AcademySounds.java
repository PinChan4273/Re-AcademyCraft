package io.github.pinchan4273.reacademycraft.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 音の登録。音声は原作の資産で、その表記を保つ。 */
public final class AcademySounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "academy");
    /** 原作の虚像融合機の稼働音（machine/imag_fusor_work.ogg、バイト単位で同一）。 */
    public static final RegistryObject<SoundEvent> MACHINE_IMAG_FUSOR_WORK = SOUNDS.register("machine.imag_fusor_work",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "machine.imag_fusor_work")));
    public static final RegistryObject<SoundEvent> EM_ARC_WEAK = SOUNDS.register("em.arc_weak",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.arc_weak")));
    public static final RegistryObject<SoundEvent> EM_ARC_STRONG = SOUNDS.register("em.arc_strong",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.arc_strong")));
    public static final RegistryObject<SoundEvent> EM_RAILGUN = SOUNDS.register("em.railgun",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.railgun")));
    public static final RegistryObject<SoundEvent> ENTITY_FLIPCOIN = SOUNDS.register("entity.flipcoin",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "entity.flipcoin")));
    public static final RegistryObject<SoundEvent> EM_INTENSIFY_ACTIVATE = SOUNDS.register("em.intensify_activate",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.intensify_activate")));
    public static final RegistryObject<SoundEvent> EM_INTENSIFY_LOOP = SOUNDS.register("em.intensify_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.intensify_loop")));
    public static final RegistryObject<SoundEvent> EM_CHARGE_LOOP = SOUNDS.register("em.charge_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.charge_loop")));
    /** 原作のテレポーターの音。バイト単位で同一。 */
    public static final RegistryObject<SoundEvent> TP_TP = SOUNDS.register("tp.tp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "tp.tp")));
    public static final RegistryObject<SoundEvent> TP_GUTS = SOUNDS.register("tp.guts",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "tp.guts")));
    public static final RegistryObject<SoundEvent> TP_FLASHING = SOUNDS.register("tp.tp_flashing",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "tp.tp_flashing")));
    public static final RegistryObject<SoundEvent> TP_SHIFT = SOUNDS.register("tp.tp_shift",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "tp.tp_shift")));
    /** 原作のベクトル操作の音。バイト単位で同一。 */
    public static final RegistryObject<SoundEvent> VM_BLOOD_RETRO = SOUNDS.register("vecmanip.blood_retro",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.blood_retro")));
    public static final RegistryObject<SoundEvent> VM_DIRECTED_BLAST = SOUNDS.register("vecmanip.directed_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.directed_blast")));
    public static final RegistryObject<SoundEvent> VM_DIRECTED_SHOCK = SOUNDS.register("vecmanip.directed_shock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.directed_shock")));
    public static final RegistryObject<SoundEvent> VM_GROUNDSHOCK = SOUNDS.register("vecmanip.groundshock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.groundshock")));
    public static final RegistryObject<SoundEvent> VM_PLASMA_CANNON = SOUNDS.register("vecmanip.plasma_cannon",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.plasma_cannon")));
    public static final RegistryObject<SoundEvent> VM_PLASMA_CANNON_T = SOUNDS.register("vecmanip.plasma_cannon_t",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.plasma_cannon_t")));
    public static final RegistryObject<SoundEvent> VM_STORM_WING = SOUNDS.register("vecmanip.storm_wing",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.storm_wing")));
    public static final RegistryObject<SoundEvent> VM_VEC_ACCEL = SOUNDS.register("vecmanip.vec_accel",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.vec_accel")));
    public static final RegistryObject<SoundEvent> VM_VEC_DEVIATION = SOUNDS.register("vecmanip.vec_deviation",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.vec_deviation")));
    public static final RegistryObject<SoundEvent> VM_VEC_REFLECTION = SOUNDS.register("vecmanip.vec_reflection",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.vec_reflection")));
    /** 原作のmeltdownerの音。バイト単位で同一。 */
    public static final RegistryObject<SoundEvent> MD_RAY_SMALL = SOUNDS.register("md.ray_small",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.ray_small")));
    public static final RegistryObject<SoundEvent> MD_MELTDOWNER = SOUNDS.register("md.meltdowner",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.meltdowner")));
    public static final RegistryObject<SoundEvent> MD_SHIELD_STARTUP = SOUNDS.register("md.shield_startup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.shield_startup")));
    public static final RegistryObject<SoundEvent> MD_MINE_BASIC_STARTUP = SOUNDS.register("md.mine_basic_startup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.mine_basic_startup")));
    public static final RegistryObject<SoundEvent> MD_MINE_EXPERT_STARTUP = SOUNDS.register("md.mine_expert_startup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.mine_expert_startup")));
    public static final RegistryObject<SoundEvent> MD_MINE_LUCK_STARTUP = SOUNDS.register("md.mine_luck_startup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.mine_luck_startup")));
    public static final RegistryObject<SoundEvent> MD_MD_CHARGE = SOUNDS.register("md.md_charge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.md_charge")));
    public static final RegistryObject<SoundEvent> MD_SHIELD_LOOP = SOUNDS.register("md.shield_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.shield_loop")));
    public static final RegistryObject<SoundEvent> MD_MINE_LOOP = SOUNDS.register("md.mine_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "md.mine_loop")));
    private AcademySounds() { }
    /** 原作の金属成形機の稼働音（machine/machine_work.ogg、バイト単位で同一）。 */
    public static final RegistryObject<SoundEvent> MACHINE_WORK = SOUNDS.register("machine.machine_work",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "machine.machine_work")));
    /** 原作EntitySilbarnの衝突音（entity/silbarn_heavy.oggとsilbarn_light.ogg、バイト単位で同一）。 */
    public static final RegistryObject<SoundEvent> SILBARN_HEAVY = SOUNDS.register("entity.silbarn_heavy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "entity.silbarn_heavy")));
    public static final RegistryObject<SoundEvent> SILBARN_LIGHT = SOUNDS.register("entity.silbarn_light",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "entity.silbarn_light")));
    /**
     * 原作のMagnetic Manipulation（ブロックを持っている間em/lf_loop.ogg、投げるときem/mag_manip.ogg）、Magnetic Movement
     * （em/move_loop.ogg）、Mine Detect（em/minedetect.ogg）。バイト単位で同一。
     */
    public static final RegistryObject<SoundEvent> EM_LF_LOOP = SOUNDS.register("em.lf_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.lf_loop")));
    public static final RegistryObject<SoundEvent> EM_MAG_MANIP = SOUNDS.register("em.mag_manip",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.mag_manip")));
    public static final RegistryObject<SoundEvent> EM_MOVE_LOOP = SOUNDS.register("em.move_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.move_loop")));
    public static final RegistryObject<SoundEvent> EM_MINEDETECT = SOUNDS.register("em.minedetect",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", "em.minedetect")));

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
