package io.github.pinchan4273.reacademycraft.world.item;

import net.minecraft.util.Mth;

/**
 * 原作ItemEnergyBaseのバー: IFItemManager.setEnergyはround((1 - energy / max) * 13)をアイテムのダメージとして保存し（setMaxDamage(13)）、
 * 1.12はそれを傷んだアイテムのバーとして描く。したがって満タンのアイテムにはバーが無く、バーの幅は13 - ダメージで、Forgeの既定の色相で
 * 緑から赤になる。
 */
public final class LegacyEnergyBar {
    static final int MAX_DAMAGE = 13;
    private LegacyEnergyBar() { }
    public static int damage(double energy, double max) {
        return (int) Math.round((1 - Math.min(max, Math.max(0, energy)) / max) * MAX_DAMAGE);
    }
    public static boolean visible(double energy, double max) { return damage(energy, max) > 0; }
    public static int width(double energy, double max) { return MAX_DAMAGE - damage(energy, max); }
    public static int color(double energy, double max) {
        return Mth.hsvToRgb(Math.max(0f, (MAX_DAMAGE - damage(energy, max)) / (float) MAX_DAMAGE) / 3f, 1f, 1f);
    }
}
