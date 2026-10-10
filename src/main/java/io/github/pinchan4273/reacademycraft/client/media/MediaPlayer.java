package io.github.pinchan4273.reacademycraft.client.media;

import com.mojang.blaze3d.audio.Channel;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作MediaBackend: 一度に1つのメディアを再生し、一時停止・再開・停止し、最後に再生したものを覚える。
 * 5tickごとに、メディアの再生中はゲーム自身の音楽を止め、音が終わったメディアを忘れる。
 *
 * 原作は再生位置を音声ライブラリのsourceから読む。ここの音声エンジンはそれを教えないので、ここで時間を数える:
 * メディアが再生中で、メディアもゲームも一時停止していない間だけ進む。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MediaPlayer {
    /** 原作MediaBackend.PlayInfo。 */
    public record PlayInfo(Media media, boolean paused, float time) {
        public String displayTime() { return Media.displayTime(time); }
    }
    @Nullable private static MediaSound sound;
    @Nullable private static Media last;
    private static boolean paused;
    private static double played;
    private static long lastMillis;
    private static int ticks;
    private MediaPlayer() { }

    public static void play(Media media) {
        stopSound();
        sound = new MediaSound(media);
        Minecraft.getInstance().getSoundManager().play(sound);
        last = media;
        paused = false; played = 0; lastMillis = Util.getMillis();
    }
    public static void pauseCurrent() {
        if (sound == null || paused) return;
        advance(); paused = true;
        channel(Channel::pause);
    }
    public static void continueCurrent() {
        if (sound == null || !paused) return;
        paused = false; lastMillis = Util.getMillis();
        channel(Channel::unpause);
    }
    public static void stopCurrent() { stopSound(); }
    public static Optional<PlayInfo> currentPlaying() {
        if (sound == null) return Optional.empty();
        advance();
        return Optional.of(new PlayInfo(sound.media(), paused, (float) played));
    }
    public static Optional<Media> lastPlayed() { return Optional.ofNullable(last); }
    public static float getVolume() { return MediaSettings.volume(); }
    /** 音は次のtickで新しい音量を拾う。 */
    public static void setVolume(float value) { MediaSettings.setVolume(value); }

    private static void stopSound() {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null; paused = false; played = 0;
    }
    private static void advance() {
        long now = Util.getMillis();
        if (sound != null && !paused && !Minecraft.getInstance().isPaused()) played += (now - lastMillis) / 1000.0;
        lastMillis = now;
    }
    private static void channel(Consumer<Channel> action) {
        var handle = Minecraft.getInstance().getSoundManager().soundEngine.instanceToChannel.get(sound);
        if (handle != null) handle.execute(action);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sound == null) return;
        advance();
        if (++ticks % 5 != 0) return;
        var client = Minecraft.getInstance();
        // 原作の5tickごとの2つの仕事: 終わったメディアを忘れることと、ゲームの音楽を止めておくこと。
        if (!client.getSoundManager().isActive(sound)) { sound = null; paused = false; played = 0; return; }
        if (!paused) client.getMusicManager().stopPlaying();
        // ゲーム自身の一時停止・再開はこの音を含むすべての音を止め・再開する。プレイヤーが一時停止したメディアは止まったまま。
        else channel(Channel::pause);
    }
}
