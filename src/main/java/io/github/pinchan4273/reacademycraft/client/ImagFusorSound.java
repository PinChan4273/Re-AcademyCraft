package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import io.github.pinchan4273.reacademycraft.world.block.ImagFusorBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity;

/**
 * 原作TileImagFusor.updateSounds: 作業中は、machine.imag_fusor_workを融合機の位置で音量0.6でループさせ、作業が止まるか
 * 融合機が無くなると止める（TileEntitySound）。原作は作業が妨げられていない間鳴らす。この移植の作業中の状態は、
 * 妨げられた作業を既に除いている。
 */
public final class ImagFusorSound {
    private ImagFusorSound() { }
    public static void update(ImagFusorBlockEntity tile) {
        MachineWorkSound.update(tile, ImagFusorBlock.WORKING, AcademySounds.MACHINE_IMAG_FUSOR_WORK.get());
    }
    /** テスト用: 融合機のループ音が今鳴っているか。 */
    public static boolean playing(ImagFusorBlockEntity tile) { return MachineWorkSound.playing(tile); }
}
