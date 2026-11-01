package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 能力設定の画面（1.4.0のS1）が名前で読み書きする設定の一覧と、その入口（サーバー）。
 * <p>
 * 書き込みは、コマンドと同じ入口（{@link ElectronMissileSettings}・{@link LightShieldSettings}）を呼ぶだけで、値の保存・検証・
 * 動作中のセッションへの反映は、そちらのまま。ここは、画面から届いた「設定の名前と値の文字列」を、その入口の呼び出しへ置き換える。
 * 知らない名前や、許されない値は何も変えない。
 * <p>
 * 設定の名前は名前空間付きのID（例: {@code academy:electron_missile.aim}）。S2で外部のアドオンが設定を登録できるようにするとき、
 * 同じ形の名前で並べられるようにしてある（S1では本体の6つだけ）。
 */
public final class AbilitySettings {
    private AbilitySettings() { }

    public static final ResourceLocation MISSILE_AIM = id("electron_missile.aim");
    public static final ResourceLocation SHIELD_MODE = id("light_shield.mode");
    /** 値の文字列の上限（通信でも使う）。 */
    public static final int MAX_VALUE = 16;
    public static final String ON = "on", OFF = "off";

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }

    /** Electron Missileの攻撃対象の1分類の設定の名前。 */
    public static ResourceLocation missileTarget(ElectronMissileTargetKind kind) { return id("electron_missile.target." + kind.id()); }

    /** 設定の名前と、その設定が属する技能。画面の並びはこの順。 */
    public record Entry(ResourceLocation id, ResourceLocation skill) { }

    /** 本体の設定のすべて（S1）。 */
    public static List<Entry> all() {
        var out = new ArrayList<Entry>();
        out.add(new Entry(MISSILE_AIM, ElectronMissile.ID));
        for (var kind : ElectronMissileTargetKind.values()) out.add(new Entry(missileTarget(kind), ElectronMissile.ID));
        out.add(new Entry(SHIELD_MODE, LightShield.ID));
        return List.copyOf(out);
    }

    public static boolean known(ResourceLocation id) { return all().stream().anyMatch(e -> e.id().equals(id)); }

    /** 書き込みの結果。CHANGED・UNCHANGEDはコマンドの結果と同じ意味。 */
    public enum Outcome {
        CHANGED, UNCHANGED, NO_DATA, READ_ONLY, UNKNOWN_SETTING, INVALID_VALUE,
        /** 送り手が死んでいるか観戦者なので、適用しなかった（通知の受け取りが返す）。 */
        REFUSED_STATE,
        /** 1tickの上限を超えたので、適用しなかった（通知の受け取りが返す）。 */
        OVER_BUDGET
    }

    /**
     * 本人の設定を1つ変える。値は、照準とLight Shieldのモードなら{@code auto}・{@code semi}、攻撃対象なら{@code on}・{@code off}。
     * Light Shieldの{@code manual}は、コマンドと同じく{@code semi}の別名として受ける。
     */
    public static Outcome set(ServerPlayer player, ResourceLocation id, String value) {
        if (MISSILE_AIM.equals(id)) {
            var mode = ElectronMissileAimMode.parse(value);
            return mode == null ? Outcome.INVALID_VALUE : missile(ElectronMissileSettings.setMode(player, mode).outcome());
        }
        if (SHIELD_MODE.equals(id)) {
            var mode = LightShieldMode.parse(value);
            return mode == null ? Outcome.INVALID_VALUE : shield(LightShieldSettings.set(player, mode).outcome());
        }
        for (var kind : ElectronMissileTargetKind.values()) {
            if (!missileTarget(kind).equals(id)) continue;
            Boolean on = ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
            return on == null ? Outcome.INVALID_VALUE : missile(ElectronMissileSettings.setTarget(player, kind, on).outcome());
        }
        return Outcome.UNKNOWN_SETTING;
    }

    /**
     * 本人の今の値（名前の順は{@link #all()}）。データが読めないか読み取り専用ならnull（画面は値を出さない）。
     * 値は、選んだことが無ければ既定の値（コマンドの表示と同じ）。
     */
    public static @Nullable Map<ResourceLocation, String> values(ServerPlayer player) {
        var missile = ElectronMissileSettings.get(player);
        var shield = LightShieldSettings.get(player);
        if (missile.outcome() != ElectronMissileSettings.Outcome.UNCHANGED || shield.outcome() != LightShieldSettings.Outcome.UNCHANGED) return null;
        var out = new java.util.LinkedHashMap<ResourceLocation, String>();
        out.put(MISSILE_AIM, missile.mode().id());
        for (var kind : ElectronMissileTargetKind.values()) out.put(missileTarget(kind), missile.targets().get(kind) ? ON : OFF);
        out.put(SHIELD_MODE, shield.mode().command());
        return out;
    }

    private static Outcome missile(ElectronMissileSettings.Outcome outcome) {
        return switch (outcome) {
            case CHANGED -> Outcome.CHANGED;
            case UNCHANGED -> Outcome.UNCHANGED;
            case NO_DATA -> Outcome.NO_DATA;
            case READ_ONLY -> Outcome.READ_ONLY;
        };
    }
    private static Outcome shield(LightShieldSettings.Outcome outcome) {
        return switch (outcome) {
            case CHANGED -> Outcome.CHANGED;
            case UNCHANGED -> Outcome.UNCHANGED;
            case NO_DATA -> Outcome.NO_DATA;
            case READ_ONLY -> Outcome.READ_ONLY;
        };
    }
}
