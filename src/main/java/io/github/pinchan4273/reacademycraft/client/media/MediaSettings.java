package io.github.pinchan4273.reacademycraft.client.media;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * 原作がメディアプレイヤーのために独自の設定へ保存するもの: 音量（"media_player", "volume", 1.0）と、外部メディアごとの
 * 名前と説明（"media", id + "_name"とid + "_desc"。プレイヤーが変えるまではどちらもid）。移植版は独自のクライアント設定を
 * 持たないので、同じキーをconfig/academy_media_player.propertiesに保存する。
 */
public final class MediaSettings {
    private static Properties values;
    private MediaSettings() { }
    private static Path file() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("academy_media_player.properties"); }
    private static Properties values() {
        if (values == null) {
            values = new Properties();
            var file = file();
            if (Files.isRegularFile(file)) try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                values.load(in);
            } catch (IOException e) {
                LogUtils.getLogger().warn("Could not read the media player settings", e);
            }
        }
        return values;
    }
    /** ドラッグの各段階ではなく、変更が終わったときに書く。 */
    public static void save() {
        var file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { values().store(out, "AcademyCraft media player"); }
        } catch (IOException e) {
            LogUtils.getLogger().warn("Could not save the media player settings", e);
        }
    }
    public static float volume() {
        try { return Mth.clamp(Float.parseFloat(values().getProperty("volume", "1.0")), 0, 1); }
        catch (NumberFormatException e) { return 1; }
    }
    public static void setVolume(float value) { values().setProperty("volume", Float.toString(Mth.clamp(value, 0, 1))); }
    public static String name(String id) { return values().getProperty(id + "_name", id); }
    public static String desc(String id) { return values().getProperty(id + "_desc", id); }
    public static void setName(String id, String value) { values().setProperty(id + "_name", value); save(); }
    public static void setDesc(String id, String value) { values().setProperty(id + "_desc", value); save(); }
}
