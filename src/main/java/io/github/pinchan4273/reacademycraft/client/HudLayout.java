package io.github.pinchan4273.reacademycraft.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/**
 * 原作ACHudのnode: CustomizeUIでプレイヤーが位置を変えられるHUD。それぞれ決まった大きさと倍率の部品で、画面の辺に揃え、
 * そこからのずれを原作は設定の"gui"の項目に保存する。ここではconfig/academy_hud.propertiesに原作の名前で保存する。
 * ずれが無いnodeは原作の既定の位置。
 */
public final class HudLayout {
    public enum Align { START, CENTER, END }
    /** 原作の4つのnode。原作の登録順。 */
    public enum Node {
        CPBAR("cpbar", 964, 147, .2f, Align.END, Align.START, -12, 12),
        KEYHINT("keyhint", 140, 210, .23f, Align.END, Align.CENTER, 0, 30),
        NOTIFICATION("notification", 517, 170, .25f, Align.START, Align.START, 0, 15),
        MEDIA("media", 145, 36, 1, Align.END, Align.END, -6, -6);
        public final String id;
        public final float width, height, scale, defaultX, defaultY;
        final Align alignX, alignY;
        Node(String id, float width, float height, float scale, Align alignX, Align alignY, float defaultX, float defaultY) {
            this.id = id; this.width = width; this.height = height; this.scale = scale;
            this.alignX = alignX; this.alignY = alignY; this.defaultX = defaultX; this.defaultY = defaultY;
        }
        public String nameKey() { return "academy.uiedit.elm." + id; }
    }
    /** 原作CustomizeUIは、この範囲の外の座標を受け付けない。 */
    public static final float LIMIT = 512;
    private static final String FILE = "academy_hud.properties";
    private static Map<Node, float[]> positions;
    private HudLayout() { }

    public static float x(Node node) { return load().get(node)[0]; }
    public static float y(Node node) { return load().get(node)[1]; }
    /** CGuiの、揃えた部品の配置: この大きさの画面での左と上。 */
    public static float left(Node node, float screenWidth) { return place(node.alignX, screenWidth, node.width * node.scale, x(node)); }
    public static float top(Node node, float screenHeight) { return place(node.alignY, screenHeight, node.height * node.scale, y(node)); }
    private static float place(Align align, float screen, float size, float offset) {
        return switch (align) { case START -> offset; case CENTER -> (screen - size) / 2 + offset; case END -> screen - size + offset; };
    }
    /** 原作Node.setPosition。すぐに設定を書き込む。 */
    public static void set(Node node, float x, float y) {
        if (!valid(x) || !valid(y)) throw new IllegalArgumentException("Coordinate out of range");
        load().put(node, new float[]{x, y});
        save();
    }
    public static boolean valid(double value) { return Double.isFinite(value) && value >= -LIMIT && value <= LIMIT; }
    /** テスト用: すべてのnodeを原作の既定へ戻す。 */
    public static void reset() {
        for (var node : Node.values()) load().put(node, new float[]{node.defaultX, node.defaultY});
        save();
    }

    private static Path file() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE); }
    private static Map<Node, float[]> load() {
        if (positions != null) return positions;
        positions = new EnumMap<>(Node.class);
        var props = new Properties();
        if (Files.isRegularFile(file())) {
            try (Reader in = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) { props.load(in); }
            catch (IOException e) { LogUtils.getLogger().warn("Could not read {}", FILE, e); }
        }
        for (var node : Node.values()) {
            float[] pos = {node.defaultX, node.defaultY};
            var value = props.getProperty("gui." + node.id);
            if (value != null) {
                try {
                    var parts = value.split(",");
                    float x = Float.parseFloat(parts[0].trim()), y = Float.parseFloat(parts[1].trim());
                    if (valid(x) && valid(y)) pos = new float[]{x, y};
                } catch (RuntimeException e) { LogUtils.getLogger().warn("Ignoring the bad position of {} in {}", node.id, FILE); }
            }
            positions.put(node, pos);
        }
        return positions;
    }
    private static void save() {
        var props = new Properties();
        for (var e : load().entrySet())
            props.setProperty("gui." + e.getKey().id, String.format(Locale.ROOT, "%s,%s", e.getValue()[0], e.getValue()[1]));
        try {
            Files.createDirectories(file().getParent());
            try (Writer out = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) { props.store(out, "AcademyCraft HUD positions (legacy CustomizeUI)"); }
        } catch (IOException e) {
            LogUtils.getLogger().warn("Could not write {}", FILE, e);
        }
    }
}
