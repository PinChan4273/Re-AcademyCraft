package io.github.pinchan4273.reacademycraft.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 原作の機械のupdateSounds（TileImagFusor、TileMetalFormer）: 機械が働いている間、その位置で音量0.6のループ音を鳴らし
 * （TileEntitySound）、仕事が止まるか機械が無くなると止める。移植版は「稼働中」をブロック状態WORKINGで持ち、
 * クライアントのtickerがそれを読む。
 */
public final class MachineWorkSound {
    private static final Map<BlockEntity, Loop> PLAYING = new WeakHashMap<>();
    private MachineWorkSound() { }
    public static void update(BlockEntity tile, BooleanProperty working, SoundEvent event) {
        boolean on = at(tile, working);
        var sound = PLAYING.get(tile);
        if (sound != null && (sound.isStopped() || !on)) { sound.end(); PLAYING.remove(tile); sound = null; }
        if (sound == null && on) {
            sound = new Loop(tile, working, event);
            PLAYING.put(tile, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }
    /** テスト用: 機械のループ音がいま鳴っているか。 */
    public static boolean playing(BlockEntity tile) {
        var sound = PLAYING.get(tile);
        return sound != null && !sound.isStopped() && Minecraft.getInstance().getSoundManager().isActive(sound);
    }
    private static boolean at(BlockEntity tile, BooleanProperty working) {
        var state = tile.getBlockState();
        return !tile.isRemoved() && state.hasProperty(working) && state.getValue(working);
    }
    static final class Loop extends AbstractTickableSoundInstance {
        private final BlockEntity tile;
        private final BooleanProperty working;
        Loop(BlockEntity tile, BooleanProperty working, SoundEvent event) {
            super(event, SoundSource.BLOCKS, RandomSource.create());
            this.tile = tile; this.working = working;
            var pos = tile.getBlockPos();
            x = pos.getX() + .5; y = pos.getY() + .5; z = pos.getZ() + .5;
            looping = true; delay = 0; volume = .6f;
        }
        void end() { stop(); }
        @Override public void tick() { if (!at(tile, working)) stop(); }
    }
}
