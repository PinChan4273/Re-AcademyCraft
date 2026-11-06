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
 * <p>
 * v1.5.0 §14のCP表示の形式と非使用時の大きさも、ここに置く。表示だけの個人設定で、サーバーのルールに関わらず、
 * どのワールド・サーバーでも同じ値を使い、系統の変更や能力のリセットの影響を受けない（能力データの外にあるため）。
 */
public final class ClientSettings {
    public static final String HEADS_OR_TAILS = "headsOrTails", USE_MOUSE_WHEEL = "useMouseWheel";
    private static final String FILE = "academy_client.properties";
    private static Properties props;
    private ClientSettings() { }

    public static boolean get(String id) { return Boolean.parseBoolean(load().getProperty(id, "false")); }
    /**
     * 整数の設定（範囲の外は端へ寄せる。読めなければ既定）。外部の演出の持ち主ごとの光線の上限{@code externalBeamsPerOwner}
     * （1〜256、既定32は採用済みの値。Issue #5 AAA-RE-VFX-20261025-001、6399564392）に使う。外部の演出の上限はファイルへ書き戻さない（手で書いた値だけを読む）。
     */
    public static int getInt(String id, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(load().getProperty(id, Integer.toString(fallback)).trim()))); }
        catch (NumberFormatException e) { return fallback; }
    }
    public static final String EXTERNAL_BEAMS_PER_OWNER = "externalBeamsPerOwner";
    public static final String EXTERNAL_EFFECTS_PER_OWNER = "externalEffectsPerOwner";
    /** v1.5.0 §14: 設定ファイルの項目の名前。 */
    public static final String CP_DISPLAY = "cpDisplay", CP_IDLE_SHOW = "cpIdleShow", CP_IDLE_SIZE = "cpIdleSize", CP_IDLE_CUSTOM = "cpIdleCustomPercent";
    /** v1.5.0（2026-11-03のユーザーの決定）: 未元物質の翼の発光の強さ（%、0〜100、10刻み、既定100）。シェーダーで光りすぎるときに下げる。 */
    public static final String WING_GLOW = "wingGlowPercent";
    public static final int WING_GLOW_STEP = 10;
    public static int wingGlow() { return Math.round(getInt(WING_GLOW, 100, 0, 100) / (float) WING_GLOW_STEP) * WING_GLOW_STEP; }
    public static void setWingGlow(int percent) { write(WING_GLOW, Integer.toString(Math.round(Math.max(0, Math.min(100, percent)) / (float) WING_GLOW_STEP) * WING_GLOW_STEP)); }
    /**
     * 描く時の翼の発光（%）。翼の面・発光の層・刺突・羽の球・頭上の輪は、設定の値ではなくこれを読む。
     * シェーダーパックの使用中は100%を90%として描く（{@link WingGlowRule}、2026-11-05のユーザーの決定）。保存した値は変えない。
     */
    public static int drawnWingGlow() { return WingGlowRule.drawn(wingGlow(), WingGlowRule.shaderPack()); }
    public static CpHudStyle.Display cpDisplay() { return CpHudStyle.Display.parse(load().getProperty(CP_DISPLAY)); }
    public static void setCpDisplay(CpHudStyle.Display display) { write(CP_DISPLAY, display.id()); }
    public static CpHudStyle.IdleShow cpIdleShow() { return CpHudStyle.IdleShow.parse(load().getProperty(CP_IDLE_SHOW)); }
    public static void setCpIdleShow(CpHudStyle.IdleShow show) { write(CP_IDLE_SHOW, show.id()); }
    public static CpHudStyle.IdleSize cpIdleSize() { return CpHudStyle.IdleSize.parse(load().getProperty(CP_IDLE_SIZE)); }
    public static void setCpIdleSize(CpHudStyle.IdleSize size) { write(CP_IDLE_SIZE, size.id()); }
    public static int cpIdleCustom() {
        return CpHudStyle.normalizeCustom(getInt(CP_IDLE_CUSTOM, CpHudStyle.CUSTOM_DEFAULT, CpHudStyle.CUSTOM_MIN, CpHudStyle.CUSTOM_MAX));
    }
    public static void setCpIdleCustom(int percent) { write(CP_IDLE_CUSTOM, Integer.toString(CpHudStyle.normalizeCustom(percent))); }
    /** 今の設定での、非使用時の倍率。 */
    public static float cpIdleScale() { return CpHudStyle.idleScale(cpIdleSize(), cpIdleCustom()); }
    /** v1.5.0 §12: 天界発現の表示だけの設定。効果と費用を変えない。既定はどれもON、発光の強度は100%。 */
    public static final String HEAVEN_GLOW = "heavenGlowPercent", HEAVEN_RINGS = "heavenRings", HEAVEN_BORDER = "heavenBorder", HEAVEN_SOUND = "heavenSound";
    public static int heavenGlowPercent() { return getInt(HEAVEN_GLOW, 100, 0, 100); }
    public static void setHeavenGlowPercent(int percent) { write(HEAVEN_GLOW, Integer.toString(Math.max(0, Math.min(100, percent)))); }
    /** 既定がONの真偽の設定。 */
    public static boolean getOn(String id) { return Boolean.parseBoolean(load().getProperty(id, "true")); }
    public static void set(String id, boolean on) { write(id, Boolean.toString(on)); }
    /** 値を変えて、すぐファイルへ書く（§14の「即時反映・再起動後保持」）。 */
    private static void write(String id, String value) {
        load().setProperty(id, value);
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
