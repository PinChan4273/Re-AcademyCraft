package io.github.pinchan4273.reacademycraft.api.mc;

import net.minecraft.resources.Identifier;

/**
 * 能力の演出の音の名前（公開API、実験的。API版2）。{@code api.client}の演出は音を鳴らさないので、音はアドオンがこの名前で鳴らす
 * （例: {@code BuiltInRegistries.SOUND_EVENT.get(AcademySoundIds.EM_RAILGUN)}）。誰に聞かせるかもアドオンが決める。
 * ここにある名前は公開の約束で、変えるときはAPIの版を上げる。
 */
public final class AcademySoundIds {
    private AcademySoundIds() { }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", path); }

    // 電撃使い
    public static final Identifier EM_ARC_WEAK = id("em.arc_weak");
    public static final Identifier EM_ARC_STRONG = id("em.arc_strong");
    public static final Identifier EM_RAILGUN = id("em.railgun");
    public static final Identifier EM_INTENSIFY_ACTIVATE = id("em.intensify_activate");
    public static final Identifier EM_CHARGE_LOOP = id("em.charge_loop");
    public static final Identifier EM_INTENSIFY_LOOP = id("em.intensify_loop");
    public static final Identifier EM_LF_LOOP = id("em.lf_loop");
    public static final Identifier EM_MAG_MANIP = id("em.mag_manip");
    public static final Identifier EM_MOVE_LOOP = id("em.move_loop");
    public static final Identifier EM_MINEDETECT = id("em.minedetect");
    public static final Identifier ENTITY_FLIPCOIN = id("entity.flipcoin");
    // 原子崩し
    public static final Identifier MD_RAY_SMALL = id("md.ray_small");
    public static final Identifier MD_MELTDOWNER = id("md.meltdowner");
    public static final Identifier MD_SHIELD_STARTUP = id("md.shield_startup");
    public static final Identifier MD_SHIELD_LOOP = id("md.shield_loop");
    public static final Identifier MD_MINE_BASIC_STARTUP = id("md.mine_basic_startup");
    public static final Identifier MD_MINE_EXPERT_STARTUP = id("md.mine_expert_startup");
    public static final Identifier MD_MINE_LUCK_STARTUP = id("md.mine_luck_startup");
    public static final Identifier MD_MINE_LOOP = id("md.mine_loop");
    public static final Identifier MD_CHARGE = id("md.md_charge");
    public static final Identifier ENTITY_SILBARN_HEAVY = id("entity.silbarn_heavy");
    public static final Identifier ENTITY_SILBARN_LIGHT = id("entity.silbarn_light");
    // 空間移動
    public static final Identifier TP_TP = id("tp.tp");
    public static final Identifier TP_GUTS = id("tp.guts");
    public static final Identifier TP_FLASHING = id("tp.tp_flashing");
    public static final Identifier TP_SHIFT = id("tp.tp_shift");
    // ベクトル操作
    public static final Identifier VM_BLOOD_RETRO = id("vecmanip.blood_retro");
    public static final Identifier VM_DIRECTED_BLAST = id("vecmanip.directed_blast");
    public static final Identifier VM_DIRECTED_SHOCK = id("vecmanip.directed_shock");
    public static final Identifier VM_GROUNDSHOCK = id("vecmanip.groundshock");
    public static final Identifier VM_PLASMA_CANNON = id("vecmanip.plasma_cannon");
    public static final Identifier VM_PLASMA_CANNON_T = id("vecmanip.plasma_cannon_t");
    public static final Identifier VM_STORM_WING = id("vecmanip.storm_wing");
    public static final Identifier VM_VEC_ACCEL = id("vecmanip.vec_accel");
    public static final Identifier VM_VEC_DEVIATION = id("vecmanip.vec_deviation");
    public static final Identifier VM_VEC_REFLECTION = id("vecmanip.vec_reflection");
}
