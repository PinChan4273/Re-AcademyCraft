package io.github.pinchan4273.reacademycraft.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/**
 * 原作SettingsUIの"generic"の項目のうち、ワールドではなくプレイヤー自身の設定:
 * headsOrTails（コインを投げるたびに、どちらの面が出たかをチャットに出す）と、useMouseWheel（ホイールで
 * Penetrate Teleportの距離を変える）。原作は設定ファイルに保存する。ここではconfig/academy_client.propertiesに保存し、
 * 原作と同じくどちらも既定はOFF。
 */
public final class ClientSettings {
    public static final String HEADS_OR_TAILS = "headsOrTails", USE_MOUSE_WHEEL = "useMouseWheel";
    private static final String FILE = "academy_client.properties";
    private static Properties props;
    private ClientSettings() { }

    public static boolean get(String id) { return Boolean.parseBoolean(load().getProperty(id, "false")); }
    public static void set(String id, boolean on) {
        load().setProperty(id, Boolean.toString(on));
        try {
            Files.createDirectories(file().getParent());
            try (Writer out = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) { props.store(out, "AcademyCraft client settings (legacy SettingsUI generic)"); }
        } catch (IOException e) {
            LogUtils.getLogger().warn("Could not write {}", FILE, e);
        }
    }
    private static Path file() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE); }
    private static Properties load() {
        if (props != null) return props;
        props = new Properties();
        if (Files.isRegularFile(file())) {
            try (Reader in = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) { props.load(in); }
            catch (IOException e) { LogUtils.getLogger().warn("Could not read {}", FILE, e); }
        }
        return props;
    }
}
