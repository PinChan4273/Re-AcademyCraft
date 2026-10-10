package io.github.pinchan4273.reacademycraft.client.media;

import com.mojang.blaze3d.audio.OggAudioStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * 原作MediaBackendのsource "AC_MediaPlayer": 位置を持たず減衰もしない1つのストリーム音を、メディアプレイヤー自身の音量で
 * 鳴らす。原作はMinecraftの音のカテゴリを通さず音声システムで直接鳴らす。ここではmasterカテゴリだけを通すので、
 * masterのスライダーは効くが、他は効かない。
 *
 * メディアはゲームの知っている音イベントではないので、音を引かずに独自の名前を付け、外部のものはプレイヤーのファイルから
 * ストリーム再生する。
 */
final class MediaSound extends AbstractTickableSoundInstance {
    private final Media media;
    MediaSound(Media media) {
        super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(media.location()), SoundSource.MASTER, RandomSource.create());
        this.media = media;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = false;
        this.volume = MediaSettings.volume();
    }
    Media media() { return media; }
    /** 音声エンジンは毎tick音量を読み直すので、変更はすぐ聞こえる。 */
    @Override public void tick() { volume = MediaSettings.volume(); }
    /** 原作は無音を含むどの音量でもsourceを開始する。 */
    @Override public boolean canStartSilent() { return true; }
    @Override public WeighedSoundEvents resolve(SoundManager manager) {
        // ストリーム再生する: 原作のnewStreamingSource。
        sound = new Sound(media.location().toString(), ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        return new WeighedSoundEvents(media.location(), null);
    }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        if (media.file() == null) return buffers.getStream(media.internalSource(), looping);
        return CompletableFuture.supplyAsync(() -> {
            try { return new OggAudioStream(Files.newInputStream(media.file())); }
            catch (IOException e) { throw new CompletionException(e); }
        }, Util.backgroundExecutor());
    }
}
