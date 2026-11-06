package io.github.pinchan4273.reacademycraft.world;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;

/**
 * WeAthFolDの原作の風力発電機の規則に対する、読み取り専用の構造のアダプタ。
 * このprobeはブロック・インベントリ・生成のtickを登録しない。
 */
public final class WindStructureProbe {
    private WindStructureProbe() { }

    public enum Kind { AIR, OTHER, PILLAR, BASE, MAIN }
    public enum Status { INVALID_BASE, UNAVAILABLE, BASE_ONLY, NO_TOP, COMPLETE_NOT_WORKING, COMPLETE }

    /**
     * 所有権はプレイヤーごとではなく本体ごと。土台と本体は独立したUUIDを持つ。
     * デコーダーは、取り除かれた、または将来の形式のエンティティを、有効な部品ではなくOTHERとして拒否しなければならない。
     */
    public record Cell(Kind kind, BlockPos origin, UUID structureId, Direction facing, int part, boolean rotor) {
        public Cell { if (origin != null) origin = origin.immutable(); }
        public static Cell simple(Kind kind) { return new Cell(kind, null, null, null, -1, false); }
    }

    /** available()が成功するまでread()を見ない。サーバースレッドで使う。 */
    public interface View {
        boolean available(BlockPos pos);
        Cell read(BlockPos pos);
    }

    /** 登録済みの風力の状態に対する、読み込みを起こさないワールドの境界のアダプタ。 */
    public static View loadedWorld(Level level, Function<BlockPos, Cell> decoder) {
        return new View() {
            @Override public boolean available(BlockPos pos) {
                return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                        && level.hasChunkAt(pos);
            }
            @Override public Cell read(BlockPos pos) {
                if (!available(pos)) throw new IllegalStateException("Unavailable wind cell " + pos);
                return decoder.apply(pos);
            }
        };
    }

    /**
     * 登録済みの物理的な部品とルートの回転翼スロットの、本番用のデコーダー。
     * COMPLETEは適格性を示す。probeが蓄えるエネルギーを生むことはない。
     */
    public static Cell decodeLoaded(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return Cell.simple(Kind.AIR);
        if (state.is(AcademyContent.WIND_PILLAR.get())) return Cell.simple(Kind.PILLAR);
        if ((state.is(AcademyContent.WIND_BASE.get()) || state.is(AcademyContent.WIND_MAIN.get()))
                && level.getBlockEntity(pos) instanceof WindBodyBlockEntity part) return part.probeCell();
        return Cell.simple(Kind.OTHER);
    }

    public record Result(Status status, int pillars, BlockPos mainOrigin) {
        public Result { if (mainOrigin != null) mainOrigin = mainOrigin.immutable(); }
        public int generationUnits() {
            return status == Status.COMPLETE && mainOrigin != null
                    ? WindGeneratorRules.generationUnits(mainOrigin.getY()) : 0;
        }
    }

    /**
     * 最大で土台2 + 柱41 + 本体の代理2 + 空き224の読み取り = 269。
     * この結果は現在の観測であり、保存される証明や削除の許可ではない。
     */
    public static Result inspect(View view, BlockPos baseOrigin) {
        if (!view.available(baseOrigin)) return result(Status.UNAVAILABLE, 0);
        Cell base = view.read(baseOrigin);
        if (!root(base, Kind.BASE, baseOrigin)) return result(Status.INVALID_BASE, 0);
        Status body = body(view, base, WindGeneratorRules.baseCells(baseOrigin));
        if (body != null) return result(body == Status.UNAVAILABLE ? body : Status.INVALID_BASE, 0);

        for (int pillars = 0; pillars <= WindGeneratorRules.MAX_PILLARS; pillars++) {
            BlockPos pos = baseOrigin.above(2 + pillars);
            if (!view.available(pos)) return result(Status.UNAVAILABLE, pillars);
            Cell cell = view.read(pos);
            if (cell != null && cell.kind() == Kind.PILLAR) {
                if (pillars == WindGeneratorRules.MAX_PILLARS) return result(Status.NO_TOP, pillars + 1);
                continue;
            }
            if (cell == null || cell.kind() != Kind.MAIN)
                return result(pillars < WindGeneratorRules.MIN_PILLARS ? Status.BASE_ONLY : Status.NO_TOP, pillars);
            if (!WindGeneratorRules.validPillars(pillars) || !root(cell, Kind.MAIN, pos))
                return result(Status.NO_TOP, pillars);
            body = body(view, cell, WindGeneratorRules.mainCells(pos, cell.facing()));
            if (body != null) return result(body, pillars);
            // 回転翼が無くても本体全体を検証する。孤立した代理を認めない。
            if (!cell.rotor()) return new Result(Status.COMPLETE_NOT_WORKING, pillars, pos);
            for (BlockPos clearance : WindGeneratorRules.clearanceCells(pos, cell.facing())) {
                if (!view.available(clearance)) return result(Status.UNAVAILABLE, pillars);
                Cell space = view.read(clearance);
                if (space == null || space.kind() != Kind.AIR)
                    return new Result(Status.COMPLETE_NOT_WORKING, pillars, pos);
            }
            return new Result(Status.COMPLETE, pillars, pos);
        }
        throw new AssertionError("Bounded wind column scan did not terminate");
    }

    private static boolean root(Cell cell, Kind kind, BlockPos pos) {
        return cell != null && cell.kind() == kind && cell.part() == 0 && pos.equals(cell.origin())
                && cell.structureId() != null && cell.facing() != null && cell.facing().getAxis() != Direction.Axis.Y;
    }

    private static Status body(View view, Cell root, List<BlockPos> cells) {
        // 呼び出し元が、ちょうど原点の位置のpart 0を既に確かめている。
        for (int part = 1; part < cells.size(); part++) {
            BlockPos pos = cells.get(part);
            if (!view.available(pos)) return Status.UNAVAILABLE;
            Cell cell = view.read(pos);
            if (cell == null || cell.kind() != root.kind() || cell.part() != part
                    || !root.origin().equals(cell.origin()) || !root.structureId().equals(cell.structureId())
                    || cell.facing() != root.facing()) return Status.NO_TOP;
        }
        return null;
    }

    private static Result result(Status status, int pillars) { return new Result(status, pillars, null); }
}
