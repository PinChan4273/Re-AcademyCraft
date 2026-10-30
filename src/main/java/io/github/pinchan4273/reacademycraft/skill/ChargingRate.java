package io.github.pinchan4273.reacademycraft.skill;

/**
 * ENERGY-RATE-001: カレント・チャージングが標準Forge Energyの受電先へ1 tickに差し出すFE。能力Lvの倍率は2026-10-18の
 * ユーザーの決定（[10, 30, 100, 300, 1000]、Lv1〜5）。熟練度による基本のIF（floor(lerp(15, 35, 熟練度))）と、1 IF = 4 FEは
 * 変えない。受電先が実際に受け取る量は、受電先のreceiveEnergyの上限に従う（ここでは差し出す量だけを決める）。
 * 特定のMODの判定はしない。Minecraftのクラスに依存しない（オフラインの試験で確かめる）。
 */
public final class ChargingRate {
    /** 能力Lv1〜5の倍率（ユーザーの決定）。 */
    public static final int[] LEVEL_MULTIPLIER = {10, 30, 100, 300, 1000};
    /** 原作RFSupport: 1 IF = 4 RF/FE。 */
    public static final int FE_PER_IF = 4;

    private ChargingRate() { }

    /** 能力Lvの倍率。定義の外のLv（0以下・6以上）は大きな出力にせず、強化しない（×1）。 */
    public static int multiplier(int level) {
        return level >= 1 && level <= LEVEL_MULTIPLIER.length ? LEVEL_MULTIPLIER[level - 1] : 1;
    }

    /** 1 tickに差し出すFE。基本のIF（負は0）× 4 × 倍率。intを超えない（今の値の範囲では最大140,000）。 */
    public static int offeredFe(int baseIf, int level) {
        long fe = (long) Math.max(0, baseIf) * FE_PER_IF * multiplier(level);
        return (int) Math.min(Integer.MAX_VALUE, fe);
    }
}
