package io.github.pinchan4273.reacademycraft.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * WeAthFolD（LambdaInnovation AcademyCraft）による原作の風力発電機の幾何と数値。
 * この規則の層だけでは風力発電機を登録も有効化もしない。
 */
public final class WindGeneratorRules {
    public static final int MIN_PILLARS = 8, MAX_PILLARS = 40;
    public static final int UNITS_PER_IF = 12, UNITS_PER_FE = 3;
    public static final int CAPACITY_UNITS = 20000 * UNITS_PER_IF;
    public static final int OUTPUT_FE = 300 * 4;
    private WindGeneratorRules() { }

    public static boolean validPillars(int count) { return count >= MIN_PILLARS && count <= MAX_PILLARS; }

    /** 正確な15 * lerp(.5, 1, clamp((mainY-70)/90)) IF/tick。tickごとの丸めはしない。 */
    public static int generationUnits(int mainY) {
        return 90 + (int)Math.max(0L, Math.min(90L, (long)mainY - 70));
    }

    public static List<BlockPos> baseCells(BlockPos origin) {
        return List.of(origin.immutable(), origin.above());
    }

    /** part0は原点、part1は前、part2は後ろ。古いローカルの前は負のZ。 */
    public static List<BlockPos> mainCells(BlockPos origin, Direction facing) {
        horizontal(facing);
        return List.of(origin.immutable(), origin.relative(facing), origin.relative(facing.getOpposite()));
    }

    public static BlockPos rotate(int x, int y, int z, Direction facing) {
        horizontal(facing);
        return switch (facing) {
            case NORTH -> new BlockPos(x, y, z);
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> throw new IllegalArgumentException("Wind generator requires horizontal facing");
        };
    }

    /** 前の本体の中央のセルは、意図的に15x15の平面から除く。 */
    public static List<BlockPos> clearanceCells(BlockPos origin, Direction facing) {
        horizontal(facing);
        var cells = new ArrayList<BlockPos>(224);
        for (int x = -7; x <= 7; x++) for (int y = -7; y <= 7; y++)
            if (x != 0 || y != 0) cells.add(origin.offset(rotate(x, y, -1, facing)));
        return List.copyOf(cells);
    }

    /**
     * ワールドの素材を見る前に利用可能かを確かめる。読み込まれていない所を空気と解釈しない。
     * 呼び出し元が、読み込みを起こさない利用可能性（高さ・境界・chunk）とAIRの判定を渡す。
     */
    public static boolean hasClearance(BlockPos origin, Direction facing,
            Predicate<BlockPos> available, Predicate<BlockPos> air) {
        for (var cell : clearanceCells(origin, facing))
            if (!available.test(cell) || !air.test(cell)) return false;
        return true;
    }

    private static void horizontal(Direction facing) {
        if (facing == null || facing.getAxis() == Direction.Axis.Y)
            throw new IllegalArgumentException("Wind generator requires horizontal facing");
    }
}
