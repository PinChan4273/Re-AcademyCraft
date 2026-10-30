package io.github.pinchan4273.reacademycraft.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * 以前の版のワールドごとのacademy-server.tomlはもう読まない: 設定はインスタンスのもので、academy-common.tomlにある。
 * 古いファイルで設定した値を黙って失うことも、推測で統合することもしない: サーバーの起動時（専用・統合とも同じ方法）に、
 * ワールドの近くにある古いファイルを探し、その中の既知の設定をcommonファイルと比べ、違うものと移し方をログに出す。
 * 何も書かず、何も消さない。古いファイルは、所有者が写したり戻したりできるよう残す。
 */
@EventBusSubscriber(modid = "academy")
public final class LegacyServerConfigCheck {
    public static final String LEGACY_FILE = "academy-server.toml";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 設定ファイルはこれほど大きくならない。これより大きいものは読まない。 */
    private static final long MAX_BYTES = 1 << 20;
    private static final int MAX_WORLDS = 1024;
    private LegacyServerConfigCheck() { }

    public record Difference(String key, Object legacy, Object current) { }
    /** 古いファイル1つ: 持っている既知の設定、そのうちcommonファイルと違うもの、この版が知らないキー、または読めなかった理由。 */
    public record FileReport(Path file, Map<String, Object> known, List<Difference> differences, List<String> unknownKeys,
                             @Nullable String error) { }
    /** 不一致: 読める2つの古いファイルが、ある既知の設定を違う値にしている。どちらも選ばない。 */
    public record Report(List<FileReport> files, boolean disagree) {
        public boolean differs() { return files.stream().anyMatch(f -> !f.differences().isEmpty()); }
    }

    /**
     * 読み込まれたacademy-common.toml。Forge版はConfigTracker.INSTANCE.fileMap()から引いたが、NeoForge（FML 4）ではそれが公開されて
     * いないので、読み込みのイベントで覚える。
     */
    @Nullable private static volatile ModConfig registered;
    @EventBusSubscriber(modid = "academy")
    public static final class LegacyConfigFileTracker {
        private LegacyConfigFileTracker() { }
        @SubscribeEvent public static void legacyConfigFileLoaded(ModConfigEvent.Loading event) {
            if (event.getConfig().getSpec() == AcademyConfig.SPEC) registered = event.getConfig();
        }
    }

    @SubscribeEvent public static void legacyConfigOnServerStart(ServerAboutToStartEvent event) {
        var world = event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var registered = LegacyServerConfigCheck.registered;
        var common = registered == null || registered.getFullPath() == null ? null : registered.getFullPath().toAbsolutePath().normalize();
        var report = examine(candidates(world, FMLPaths.GAMEDIR.get()));
        LOGGER.info("ACADEMY_CONFIG_SCOPE: common={} type={} world={} legacyFiles={}", common,
                registered == null ? null : registered.getType(), world, report.files().size());
        log(report, common);
    }

    /**
     * このインスタンスの以前の設定を持ちうる古いファイル: ワールド自身のもの、隣のワールドのもの
     * （他のセーブ、またはサーバーの他のフォルダ）、新しいワールド用のdefaultconfigsの写し（Forge版のもの）。
     */
    public static List<Path> candidates(Path world, Path gameDirectory) {
        var found = new TreeSet<Path>();
        add(found, world.resolve("serverconfig").resolve(LEGACY_FILE));
        var parent = world.toAbsolutePath().normalize().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            try (var siblings = Files.list(parent)) {
                siblings.filter(Files::isDirectory).limit(MAX_WORLDS).forEach(dir -> add(found, dir.resolve("serverconfig").resolve(LEGACY_FILE)));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("ACADEMY_LEGACY_SERVER_CONFIG: could not look for old settings beside {}", world, e);
            }
        }
        add(found, gameDirectory.resolve("defaultconfigs").resolve(LEGACY_FILE));
        return List.copyOf(found);
    }
    private static void add(TreeSet<Path> found, Path file) {
        if (Files.isRegularFile(file)) found.add(file.toAbsolutePath().normalize());
    }

    /** 各ファイルを読み、読み込み済みのcommon設定と比べる。書き込みは一切しない。 */
    public static Report examine(List<Path> files) {
        var current = new LinkedHashMap<String, Object>();
        flattenSpec(AcademyConfig.SPEC.getValues(), "", current);
        var reports = new ArrayList<FileReport>();
        for (var file : files) reports.add(examine(file, current));
        boolean disagree = false;
        var readable = reports.stream().filter(r -> r.error() == null).toList();
        for (int i = 0; i < readable.size() && !disagree; i++) for (int j = i + 1; j < readable.size() && !disagree; j++) {
            var a = readable.get(i).known(); var b = readable.get(j).known();
            for (var key : current.keySet()) if (a.containsKey(key) && b.containsKey(key) && !a.get(key).equals(b.get(key))) { disagree = true; break; }
        }
        return new Report(List.copyOf(reports), disagree);
    }
    private static FileReport examine(Path file, Map<String, Object> current) {
        Map<String, Object> raw = new LinkedHashMap<>();
        try {
            if (Files.size(file) > MAX_BYTES) return new FileReport(file, Map.of(), List.of(), List.of(), "larger than " + MAX_BYTES + " bytes");
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { flattenFile(new TomlParser().parse(in), "", raw); }
        } catch (IOException | RuntimeException e) {
            return new FileReport(file, Map.of(), List.of(), List.of(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        var known = new LinkedHashMap<String, Object>(); var differences = new ArrayList<Difference>(); var unknown = new ArrayList<String>();
        for (var entry : raw.entrySet()) {
            if (!current.containsKey(entry.getKey())) { unknown.add(entry.getKey()); continue; }
            var legacy = normal(entry.getValue()); known.put(entry.getKey(), legacy);
            var now = current.get(entry.getKey());
            if (!legacy.equals(now)) differences.add(new Difference(entry.getKey(), legacy, now));
        }
        return new FileReport(file, known, List.copyOf(differences), List.copyOf(unknown), null);
    }
    // NightConfig 3.8（NeoForge 21.1）ではvalueMap()が非推奨。entrySet()のgetRawValueはvalueMap()の値と同じ生の値。
    private static void flattenSpec(UnmodifiableConfig values, String prefix, Map<String, Object> into) {
        for (var entry : values.entrySet()) {
            var key = prefix + entry.getKey();
            if (entry.<Object>getRawValue() instanceof UnmodifiableConfig nested) flattenSpec(nested, key + ".", into);
            else if (entry.<Object>getRawValue() instanceof ModConfigSpec.ConfigValue<?> value) into.put(key, normal(value.get()));
        }
    }
    private static void flattenFile(UnmodifiableConfig values, String prefix, Map<String, Object> into) {
        for (var entry : values.entrySet()) {
            var key = prefix + entry.getKey();
            if (entry.<Object>getRawValue() instanceof UnmodifiableConfig nested) flattenFile(nested, key + ".", into);
            else into.put(key, entry.<Object>getRawValue());
        }
    }
    /** 数値はdoubleとして比べ（ファイルの2と設定の2.0は同じ値）、リストは要素ごとに比べる。 */
    private static Object normal(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof List<?> list) return list.stream().map(LegacyServerConfigCheck::normal).toList();
        return value == null ? "" : value;
    }

    private static void log(Report report, @Nullable Path common) {
        for (var file : report.files()) {
            if (file.error() != null) {
                LOGGER.warn("ACADEMY_LEGACY_SERVER_CONFIG: {} could not be read ({}); it is no longer used and is left as it is.", file.file(), file.error());
                continue;
            }
            if (!file.unknownKeys().isEmpty())
                LOGGER.info("ACADEMY_LEGACY_SERVER_CONFIG: {} has settings this version does not know, ignored: {}", file.file(), file.unknownKeys());
            if (file.differences().isEmpty()) {
                LOGGER.info("ACADEMY_LEGACY_SERVER_CONFIG: {} is no longer used; every setting in it is the same in {}. It is left as it is.", file.file(), common);
                continue;
            }
            LOGGER.warn("ACADEMY_LEGACY_SERVER_CONFIG: {} is no longer used - AcademyCraft now keeps its settings for the whole instance in {}."
                    + " {} setting(s) there differ from this old file:", file.file(), common, file.differences().size());
            for (var d : file.differences()) LOGGER.warn("ACADEMY_LEGACY_SERVER_CONFIG:   {} = {} in the old file, {} now", d.key(), d.legacy(), d.current());
        }
        if (report.disagree())
            LOGGER.warn("ACADEMY_LEGACY_SERVER_CONFIG: the old files above disagree with each other. AcademyCraft does not choose one or merge them.");
        if (report.differs())
            LOGGER.warn("ACADEMY_LEGACY_SERVER_CONFIG: to keep old values, stop the game or server and copy the settings you want into {}"
                    + " (the keys are the same; a whole old file may be copied over it). AcademyCraft never changes or removes the old files.", common);
    }
}
