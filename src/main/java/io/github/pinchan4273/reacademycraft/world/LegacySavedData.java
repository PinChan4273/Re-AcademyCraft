package io.github.pinchan4273.reacademycraft.world;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 1.21.1 の {@code data/<名前>.dat} から 26.2 の {@code data/academy/<名前>.dat} への移行（無線網とコイン台帳だけ）。
 *
 * <ul>
 * <li>旧ファイルは消さない。新しいファイルは一時ファイルへ書き、読み直して同じことを確かめてから置く。</li>
 * <li>新旧の両方があれば、有効な新形式を使う。上書きも合算もしない。旧の方と中身が違えばログに出す。</li>
 * <li>壊れたファイルや未知の形式を空のデータで置き換えない。{@link Result#blocked()} のとき呼び出し側は保存しないデータを使う。</li>
 * <li>移行の後は印のファイル（{@code .migrated}）を置く。旧ファイルが残っていても、再起動で移行し直さない。</li>
 * </ul>
 * この 2 種類以外の、旧ワールド全体の互換を保証する物ではない。
 */
public final class LegacySavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Result> FAILURES = Collections.synchronizedList(new ArrayList<>());

    private LegacySavedData() { }

    public enum Outcome {
        /** どちらのファイルも無い。 */ NOTHING,
        /** 新形式だけがある。 */ NEW_ONLY,
        /** 前に移行した（旧ファイルは残っている）。 */ ALREADY_MIGRATED,
        /** 移行の印が無いのに両方があり、新形式を使った。 */ BOTH_NEW_PREFERRED,
        /** 今回移行した。 */ MIGRATED,
        /** 新形式のファイルが壊れているか未知の形式。 */ FAILED_BAD_NEW,
        /** 旧形式のファイルが壊れているか未知の形式。 */ FAILED_BAD_LEGACY,
        /** 新形式を書けなかった。 */ FAILED_WRITE
    }

    public record Result(Outcome outcome, Path file, String detail) {
        /** 読めるデータを信用できないので、この実行では何も保存してはいけない。 */
        public boolean blocked() {
            return outcome == Outcome.FAILED_BAD_NEW || outcome == Outcome.FAILED_BAD_LEGACY || outcome == Outcome.FAILED_WRITE;
        }
    }

    /** {@code data} の中身を調べる。問題があればその説明、無ければ null。 */
    @FunctionalInterface
    public interface Validator {
        @Nullable String problem(CompoundTag data);
    }

    /** このセッションで起きた失敗（試験と運用の確かめ用）。 */
    public static List<Result> failures() { return List.copyOf(FAILURES); }

    private static CompoundTag read(Path file, Validator validator) throws IOException {
        var root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        if (root.get("data") == null || root.get("data").getId() != Tag.TAG_COMPOUND) throw new IOException("no data compound");
        var problem = validator.problem(root.getCompoundOrEmpty("data"));
        if (problem != null) throw new IOException(problem);
        return root;
    }

    private static Path marker(Path file) { return file.resolveSibling(file.getFileName() + ".migrated"); }

    /** ファイルだけの処理（ゲームの状態に触らない）。{@code legacyFiles} は調べる順。 */
    public static Result migrate(Path newFile, List<Path> legacyFiles, Validator validator) {
        var legacy = legacyFiles.stream().filter(Files::isRegularFile).toList();
        if (Files.exists(newFile)) {
            CompoundTag current;
            try {
                current = read(newFile, validator);
            } catch (Exception e) {
                return new Result(Outcome.FAILED_BAD_NEW, newFile, String.valueOf(e));
            }
            if (Files.exists(marker(newFile))) return new Result(Outcome.ALREADY_MIGRATED, newFile, "");
            if (legacy.isEmpty()) return new Result(Outcome.NEW_ONLY, newFile, "");
            // 書いた直後に印を置けなかった時は、中身が同じなので移行済みと分かる。
            for (var old : legacy) {
                try {
                    if (!NbtIo.readCompressed(old, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("data").equals(current.getCompoundOrEmpty("data")))
                        return new Result(Outcome.BOTH_NEW_PREFERRED, newFile, "different from " + old);
                } catch (Exception e) {
                    return new Result(Outcome.BOTH_NEW_PREFERRED, newFile, "unreadable " + old + ": " + e);
                }
            }
            return new Result(Outcome.ALREADY_MIGRATED, newFile, "");
        }
        if (legacy.isEmpty()) return new Result(Outcome.NOTHING, newFile, "");
        var source = legacy.getFirst();
        CompoundTag root;
        try {
            root = read(source, validator);
        } catch (Exception e) {
            return new Result(Outcome.FAILED_BAD_LEGACY, source, String.valueOf(e));
        }
        var temporary = newFile.resolveSibling(newFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(newFile.getParent());
            NbtIo.writeCompressed(root, temporary);
            if (!NbtIo.readCompressed(temporary, NbtAccounter.unlimitedHeap()).equals(root)) throw new IOException("read back differs");
            Files.move(temporary, newFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            return new Result(Outcome.FAILED_WRITE, newFile, String.valueOf(e));
        }
        try {
            Files.writeString(marker(newFile), source.toString());
        } catch (IOException e) {
            LOGGER.warn("Could not write the migration marker for {} (the contents still show it was migrated)", newFile, e);
        }
        return new Result(Outcome.MIGRATED, newFile, "from " + source + (legacy.size() > 1 ? "; also present: " + legacy.subList(1, legacy.size()) : ""));
    }

    private static Path dataFolder(ServerLevel level) {
        return DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data").normalize();
    }

    /** 1.21.1 の次元の data フォルダ（26.2 の世界の変換は MOD のファイルを動かさない）。 */
    private static Path legacyFolder(ServerLevel level) {
        var root = level.getServer().getWorldPath(LevelResource.ROOT);
        if (level.dimension() == Level.OVERWORLD) return root.resolve("data").normalize();
        if (level.dimension() == Level.NETHER) return root.resolve("DIM-1/data").normalize();
        if (level.dimension() == Level.END) return root.resolve("DIM1/data").normalize();
        return dataFolder(level);
    }

    /**
     * 最初の 1 回だけ移行を調べ、そのデータを返す。失敗の時は {@code blocked} のデータ（保存しない）をこの次元のデータにする。
     * {@code seen} は種類ごとの「調べた storage」の集合（{@link #seen()}）。
     */
    public static <T extends SavedData> T open(ServerLevel level, SavedDataType<T> type, String name, Validator validator, Supplier<T> blocked, Set<SavedDataStorage> seen) {
        var storage = level.getDataStorage();
        if (seen.add(storage)) {
            var folder = dataFolder(level);
            var result = migrate(folder.resolve("academy").resolve(name + ".dat"), List.of(folder.resolve(name + ".dat"), legacyFolder(level).resolve(name + ".dat")).stream().distinct().toList(), validator);
            switch (result.outcome()) {
                case MIGRATED -> LOGGER.info("Migrated {} in {} to {} ({}); the old file is kept", name, level.dimension().identifier(), result.file(), result.detail());
                case BOTH_NEW_PREFERRED -> LOGGER.warn("Both old and new {} exist in {}: using the new one {} without merging ({})", name, level.dimension().identifier(), result.file(), result.detail());
                case FAILED_BAD_NEW, FAILED_BAD_LEGACY, FAILED_WRITE -> {
                    FAILURES.add(result);
                    LOGGER.error("Could not load {} in {}: {} {} {}. The files are left as they are and this data will not be saved in this session.", name, level.dimension().identifier(), result.outcome(), result.file(), result.detail());
                    storage.set(type, blocked.get());
                }
                default -> { }
            }
        }
        return storage.computeIfAbsent(type);
    }

    public static Set<SavedDataStorage> seen() { return Collections.newSetFromMap(new WeakHashMap<>()); }
}
