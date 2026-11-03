package io.github.pinchan4273.reacademycraft.client;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * v1.5.0 §14: CP表示の形式、非使用時の大きさ、セーフティの明滅の計算。Minecraftのクライアントに依存しないので、GameTestのサーバーでも確かめられる
 * （{@link DebugConsoleLayout}と同じ）。値の保存は{@link ClientSettings}、描画は{@link CpBarHud}。
 */
public final class CpHudStyle {
    /** CP表示の形式。既定はゲージのみ。 */
    public enum Display {
        GAUGE("gauge", true, false), BOTH("both", true, true), NUMBER("number", false, true);
        private final String id;
        private final boolean gauge, number;
        Display(String id, boolean gauge, boolean number) { this.id = id; this.gauge = gauge; this.number = number; }
        public String id() { return id; }
        public boolean gauge() { return gauge; }
        public boolean number() { return number; }
        public String translation() { return "academy.ability_settings.cp_display." + id; }
        /** 保存された文字列から。知らない値や無い値は既定（手で壊した設定ファイルでも表示を失わない）。 */
        public static Display parse(@Nullable String value) {
            for (var display : values()) if (display.id.equals(value)) return display;
            return GAUGE;
        }
    }
    /** 非使用時（VモードOFF）の大きさ。既定は縮小（70%）。 */
    public enum IdleSize {
        SAME("same"), SMALL("small"), CUSTOM("custom");
        private final String id;
        IdleSize(String id) { this.id = id; }
        public String id() { return id; }
        public String translation() { return "academy.ability_settings.cp_idle." + id; }
        public static IdleSize parse(@Nullable String value) {
            for (var size : values()) if (size.id.equals(value)) return size;
            return SMALL;
        }
    }
    /** 非使用時（VモードOFF）にCPを出すか。既定は満タンでなければ表示。 */
    public enum IdleShow {
        NOT_FULL("not_full"), ALWAYS("always"), HIDDEN("hidden");
        private final String id;
        IdleShow(String id) { this.id = id; }
        public String id() { return id; }
        public String translation() { return "academy.ability_settings.cp_idle_show." + id; }
        public static IdleShow parse(@Nullable String value) {
            for (var show : values()) if (show.id.equals(value)) return show;
            return NOT_FULL;
        }
        /** 今のCPで出すか。 */
        public boolean visible(float cp, float maxCp) {
            return switch (this) {
                case ALWAYS -> true;
                case HIDDEN -> false;
                case NOT_FULL -> cp < maxCp;
            };
        }
    }
    /** 非使用時の表示が現れる・消える速さ（1秒あたり）。VのON/OFFのフェードと同じ0.2秒。 */
    public static final float IDLE_FADE_PER_SECOND = 5;
    public static final int SMALL_PERCENT = 70, CUSTOM_MIN = 50, CUSTOM_MAX = 150, CUSTOM_STEP = 10;
    /** 任意倍率の初めの値。「縮小」から切り替えたときに大きさが跳ねないよう、同じ70%にする。 */
    public static final int CUSTOM_DEFAULT = SMALL_PERCENT;
    /** 明滅の周期（ミリ秒）と、いちばん暗いときの濃さ。完全に消さず、読める明るさを保つ（§14）。 */
    public static final long BLINK_PERIOD = 1000;
    public static final float BLINK_MIN = .45f;

    private CpHudStyle() { }

    /** 任意倍率を範囲へ寄せ、10%刻みに丸める。 */
    public static int normalizeCustom(int percent) {
        int clamped = Math.max(CUSTOM_MIN, Math.min(CUSTOM_MAX, percent));
        return CUSTOM_MIN + Math.round((clamped - CUSTOM_MIN) / (float) CUSTOM_STEP) * CUSTOM_STEP;
    }
    /** 非使用時の倍率（既存のUIの大きさに掛ける）。 */
    public static float idleScale(IdleSize size, int customPercent) {
        return switch (size) {
            case SAME -> 1;
            case SMALL -> SMALL_PERCENT / 100f;
            case CUSTOM -> normalizeCustom(customPercent) / 100f;
        };
    }
    /** VのON/OFFの0.2秒のフェードの間に、大きさも同じ進み具合で動かす。presenceは1で使用中、0で非使用。 */
    public static float sizeAt(float idleScale, float presence) {
        float p = Math.max(0, Math.min(1, presence));
        return idleScale + (1 - idleScale) * p;
    }
    /** セーフティの明滅の濃さ（約1秒周期）。{@link #BLINK_MIN}から1の間で、0にはならない。 */
    public static float blinkPulse(long millis) {
        double phase = 2 * Math.PI * Math.floorMod(millis, BLINK_PERIOD) / BLINK_PERIOD;
        return (float) (BLINK_MIN + (1 - BLINK_MIN) * (.5 + .5 * Math.cos(phase)));
    }
    /** 数値の表示: 現在CP/最大CP。 */
    public static String number(float cp, float maxCp) { return String.format(Locale.ROOT, "%.0f/%.0f", cp, maxCp); }
    /** 並びの次（dirが負なら前）。端では反対の端へ回る。 */
    public static <E> E cycle(E[] values, E current, int dir) {
        for (int i = 0; i < values.length; i++)
            if (values[i].equals(current)) return values[Math.floorMod(i + Integer.signum(dir), values.length)];
        return values[0];
    }
    /** 停止ライン（%）を動かす。範囲は1〜95で、端で止まる（端から反対へ回さない。1%のつもりが95%になるのは危ない）。 */
    public static int stepSafety(int percent, int delta, int min, int max) { return Math.max(min, Math.min(max, percent + delta)); }
}
