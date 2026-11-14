package io.github.pinchan4273.reacademycraft.config;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

/**
 * 1.5.2で既定値を変えた設定の移し方（academy-common.toml）。
 *
 * <p>Forgeは、既にあるファイルの値を新しい既定値で書き換えない。そのため、1.5.1で作られたファイルは、1.5.1の既定値のまま残る。
 * 設定を読んだとき（{@link ModConfigEvent.Loading}・{@link ModConfigEvent.Reloading}）に、既定値を変えた項目（{@link #changes()}）を1つずつ見て、
 * ファイルの値が1.5.1の既定値とちょうど同じなら、1.5.2の既定値へ置き換えて保存し、ログに1行ずつ書く。プレイヤーや管理者が変えた値
 * （1.5.1の既定値と違う値）はそのまま残す。置き換えた後は1.5.1の既定値と違うので、何度読んでも1回しか移らない。
 *
 * <p>1.5.2で使わなくなった項目（{@link #unused()}）は、ファイルから消さず値も変えない。その一覧を、最初に読んだときに1回だけログに書く。
 */
@Mod.EventBusSubscriber(modid = "academy", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigDefaultMigration {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 使わなくなった項目の一覧を、もうログに書いたか（起動の間に1回だけ）。 */
    private static boolean unusedLogged;
    private ConfigDefaultMigration() { }

    /** 既定値を変えた1項目: 設定の名前（区分を点でつないだもの）、1.5.1の既定値、1.5.2の既定値。 */
    public record Change(String key, List<Double> before, List<Double> after) { }

    /** 1.5.2で既定値を変えた項目。 */
    public static List<Change> changes() {
        // Storm Wingの推進の過負荷: [3, 1] → [0, 0]。1.5.2の開発中の既定 [2, 0] も同じく移す。
        String key = path(AcademyConfig.STORM_WING_THRUST_OVERLOAD, "ability.storm_wing.thrust_overload_per_second");
        return List.of(new Change(key, List.of(3d, 1d), AcademyConfig.DEFAULT_STORM_WING_THRUST_OVERLOAD),
                new Change(key, List.of(2d, 0d), AcademyConfig.DEFAULT_STORM_WING_THRUST_OVERLOAD));
    }

    /** 1.5.2で使わなくなった項目（消さずに残す）。本体の技能はもう読まない。 */
    public static List<String> unused() {
        var out = new ArrayList<String>();
        // 防御の部類は1回ごとの支払いになり、維持費が無くなった。飛行の部類は飛行の基準で払う。
        for (var skill : List.of("vec_deviation", "vec_reflection", "light_shield", "jet_engine", "storm_wing")) {
            var value = AcademyConfig.TOGGLE_UPKEEP.get(skill);
            out.add(path(value, "ability.toggle_upkeep." + skill));
        }
        out.add(path(AcademyConfig.STORM_WING_IDLE_CP, "ability.storm_wing.idle_cp_per_second"));
        out.add(path(AcademyConfig.STORM_WING_IDLE_OVERLOAD_RECOVERY, "ability.storm_wing.idle_overload_recovery_per_second"));
        // 切り替え型は、どれも部類の基準か共通の形で払うので、「熟練度100%で1つだけのときの特典」は使われない。
        out.add(path(AcademyConfig.IDLE_RECOVERY_SHARE, "ability.capacity.idle_recovery_share"));
        return List.copyOf(out);
    }

    /** 設定の項目の名前（区分を点でつないだもの）。項目がまだ作られていなければ、代わりの名前。 */
    static String path(@Nullable ForgeConfigSpec.ConfigValue<?> value, String fallback) {
        return value == null ? fallback : String.join(".", value.getPath());
    }

    /**
     * 純粋な判定: ファイルの値が1.5.1の既定値とちょうど同じ（同じ数で、どの数も等しい）なら、置き換える1.5.2の既定値。違えばnull（そのまま残す）。
     */
    public static @Nullable List<Double> migrated(@Nullable List<?> stored, Change change) {
        if (stored == null || stored.size() != change.before().size()) return null;
        for (int i = 0; i < stored.size(); i++) {
            if (!(stored.get(i) instanceof Number number) || number.doubleValue() != change.before().get(i)) return null;
        }
        return change.after();
    }

    /**
     * 名前 → 値の表に移し方を当てる（テストと、実際の設定の両方の入口）。置き換えた項目を返す。表に無い項目は触らない。
     */
    public static List<Change> apply(Map<String, Object> values) {
        var applied = new ArrayList<Change>();
        for (var change : changes()) {
            var stored = values.get(change.key());
            var next = stored instanceof List<?> list ? migrated(list, change) : null;
            if (next == null) continue;
            values.put(change.key(), next);
            applied.add(change);
        }
        return applied;
    }

    /** 読んだ設定へ当てる。置き換えた項目があればファイルへ保存し、1項目ずつログに書く。 */
    static void run(ModConfig config) {
        if (config.getSpec() != AcademyConfig.SPEC) return;
        var applied = new ArrayList<Change>();
        var changes = changes();
        var values = bound();
        for (int i = 0; i < changes.size(); i++) {
            var change = changes.get(i);
            var next = migrated(values.get(i).get(), change);
            if (next == null) continue;
            values.get(i).set(next);
            applied.add(change);
        }
        if (!applied.isEmpty()) {
            try {
                config.save();
            } catch (RuntimeException e) {
                LOGGER.warn("ACADEMY_CONFIG_DEFAULTS_152: could not save {}: {}", config.getFileName(), e.toString());
            }
        }
        for (var change : applied)
            LOGGER.info("ACADEMY_CONFIG_DEFAULTS_152: {} still had the 1.5.1 default {}; replaced with the 1.5.2 default {} (1.5.1の既定値だったので1.5.2の既定値へ置き換えた)",
                    change.key(), change.before(), change.after());
        if (!unusedLogged) {
            unusedLogged = true;
            LOGGER.info("ACADEMY_CONFIG_DEFAULTS_152: no longer used since 1.5.2 and kept as they are (1.5.2から使わない項目。消さずに残す): {}", unused());
        }
    }
    /** {@link #changes()}と同じ順の、設定の項目そのもの。 */
    private static List<ForgeConfigSpec.ConfigValue<List<? extends Double>>> bound() {
        return List.of(AcademyConfig.STORM_WING_THRUST_OVERLOAD, AcademyConfig.STORM_WING_THRUST_OVERLOAD);
    }

    @SubscribeEvent public static void configDefaultsOnLoading(ModConfigEvent.Loading event) { run(event.getConfig()); }
    @SubscribeEvent public static void configDefaultsOnReloading(ModConfigEvent.Reloading event) { run(event.getConfig()); }
}
