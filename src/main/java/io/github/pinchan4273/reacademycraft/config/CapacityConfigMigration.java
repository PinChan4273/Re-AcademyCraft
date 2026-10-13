package io.github.pinchan4273.reacademycraft.config;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 1.0.0の後に承認したCPの容量・回復（docs/CP_RECOVERY_TOGGLE_SPEC_JA.md）の設定の移し方。
 *
 * <p>Forgeは、ファイルに無い新しい項目を既定値で足してファイルを書き直すが、そのときバックアップは取らない（ForgeConfigSpec.setConfigは
 * "Correcting"のログを出して保存するだけ）。そこで新しい項目（[ability.capacity]）がまだ無いファイルを、Forgeが読む前に一度だけ
 * {@link #BACKUP}へ写す。既にある写しは上書きしない。
 *
 * <p>旧項目（init_cp・add_cp・cp_recover_cooldown・overload_recover_cooldown・toggle_upkeep.storm_wing）はファイルから消さず値を残すが、
 * もう使わない。サーバーの起動時に、旧項目が1.0.0の既定値から変えられていれば、使われないことと対応する新しい項目をログで知らせる。
 * 値を新しい項目へ自動では移さない（意味が違う: 旧容量はコースの加算を含み、旧待ちはコースで変わらない）。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class CapacityConfigMigration {
    public static final String BACKUP = AcademyConfig.FILE + ".before-capacity-v2.bak";
    static final String NEW_SECTION = "[ability.capacity]";
    private static final Logger LOGGER = LogUtils.getLogger();
    private CapacityConfigMigration() { }

    /** Forgeが読む前（modのコンストラクタ）に呼ぶ。写したらそのパス、写す必要が無ければnull。 */
    public static Path backUpBeforeLoad(Path configDir) {
        var file = configDir.resolve(AcademyConfig.FILE);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > (1 << 20)) return null;
            if (Files.readString(file, StandardCharsets.UTF_8).contains(NEW_SECTION)) return null;
            var backup = configDir.resolve(BACKUP);
            if (Files.exists(backup)) return null;
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.info("ACADEMY_CAPACITY_CONFIG_BACKUP: {} copied to {} before the new capacity settings are added", file, backup);
            return backup;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("ACADEMY_CAPACITY_CONFIG_BACKUP: could not back up {}: {}", file, e.toString());
            return null;
        }
    }

    /** 1.0.0の既定値から変えられている旧項目と、その代わりの新しい項目。 */
    public static List<String> customisedRetired() {
        var out = new ArrayList<String>();
        if (!AcademyConfig.loaded()) return out;
        if (!same(AcademyConfig.INIT_CP.get(), AcademyConfig.LEGACY_INIT_CP)) out.add("ability.data.init_cp -> ability.capacity.base_cp");
        if (!same(AcademyConfig.ADD_CP.get(), AcademyConfig.LEGACY_ADD_CP)) out.add("ability.data.add_cp -> ability.capacity.growth_cp");
        if (AcademyConfig.CP_RECOVER_COOLDOWN.get() != 15) out.add("ability.data.cp_recover_cooldown -> ability.capacity.cp_recover_wait_by_course");
        if (AcademyConfig.OVERLOAD_RECOVER_COOLDOWN.get() != 32)
            out.add("ability.data.overload_recover_cooldown -> ability.capacity.overload_recover_wait_by_course");
        var wing = AcademyConfig.TOGGLE_UPKEEP.get("storm_wing");
        if (wing != null && wing.get() != 40d) out.add("ability.toggle_upkeep.storm_wing -> ability.storm_wing.*");
        return out;
    }
    private static boolean same(List<? extends Double> a, List<Double> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!Objects.equals(a.get(i).doubleValue(), b.get(i))) return false;
        return true;
    }

    @SubscribeEvent public static void capacityConfigOnServerStart(ServerAboutToStartEvent event) {
        var retired = customisedRetired();
        LOGGER.info("ACADEMY_CAPACITY_CONFIG: base_cp={} growth_cp={} recover_per_level={} recover_per_course={} cp_wait={} overload_wait={} customisedRetired={}",
                AcademyConfig.BASE_CP.get(), AcademyConfig.GROWTH_CP.get(), AcademyConfig.CP_RECOVER_PER_LEVEL.get(), AcademyConfig.CP_RECOVER_PER_COURSE.get(),
                AcademyConfig.CP_RECOVER_WAIT_BY_COURSE.get(), AcademyConfig.OVERLOAD_RECOVER_WAIT_BY_COURSE.get(), retired.size());
        for (var key : retired)
            LOGGER.warn("ACADEMY_CAPACITY_CONFIG: {} was changed from its 1.0.0 default but is no longer used; set the new key instead", key);
    }
}
