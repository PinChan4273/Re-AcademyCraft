package io.github.pinchan4273.reacademycraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 描画だけのための公開された観測。アイテム・エネルギー・capability・保存された所有権は含まない。
 * 表示は原作の完成・空きの判定で決める。
 */
public record WindVisualState(boolean fanInstalled, boolean structureComplete, boolean running) {
    public static final WindVisualState NONE = new WindVisualState(false, false, false);
    public WindVisualState {
        if (running && (!fanInstalled || !structureComplete)) throw new IllegalArgumentException("Invalid running wind visual");
    }
    public CompoundTag encode() {
        var tag = new CompoundTag(); tag.putInt("wind_visual_schema", 1);
        tag.putByte("wind_visual_flags", (byte)((fanInstalled ? 1 : 0) | (structureComplete ? 2 : 0) | (running ? 4 : 0)));
        return tag;
    }
    public static WindVisualState decode(CompoundTag tag) {
        if (!tag.contains("wind_visual_schema", Tag.TAG_INT) || tag.getInt("wind_visual_schema") != 1
                || !tag.contains("wind_visual_flags", Tag.TAG_BYTE)) return NONE;
        int flags = tag.getByte("wind_visual_flags");
        if (flags < 0 || flags > 7 || (flags & 4) != 0 && (flags & 3) != 3) return NONE;
        return new WindVisualState((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }
    public static WindVisualState fromBase(WindStructureProbe.View view, BlockPos base) {
        var result = WindStructureProbe.inspect(view, base);
        if (result.mainOrigin() == null || !view.available(result.mainOrigin())) return NONE;
        var top = view.read(result.mainOrigin());
        boolean complete = result.status() == WindStructureProbe.Status.COMPLETE
                || result.status() == WindStructureProbe.Status.COMPLETE_NOT_WORKING;
        return new WindVisualState(top != null && top.rotor(), complete,
                result.status() == WindStructureProbe.Status.COMPLETE);
    }
    /**
     * 最大311回の読み取り: 本体1 + 下方向41セル + 前方の検証269。
     * 見かけ上の上側の土台は、実際の完成した2セルの所有者に解決しなければならない。
     */
    public static WindVisualState fromMain(WindStructureProbe.View view, BlockPos main) {
        if (!view.available(main)) return NONE;
        var top = view.read(main);
        if (top == null || top.kind() != WindStructureProbe.Kind.MAIN || top.part() != 0
                || !main.equals(top.origin()) || top.structureId() == null || top.facing() == null
                || top.facing().getAxis() == net.minecraft.core.Direction.Axis.Y) return NONE;
        var stopped = new WindVisualState(top.rotor(), false, false);
        for (int pillars = 0; pillars <= WindGeneratorRules.MAX_PILLARS; pillars++) {
            var pos = main.below(1 + pillars);
            if (!view.available(pos)) return stopped;
            var cell = view.read(pos);
            if (cell != null && cell.kind() == WindStructureProbe.Kind.PILLAR) continue;
            if (!WindGeneratorRules.validPillars(pillars) || cell == null
                    || cell.kind() != WindStructureProbe.Kind.BASE || cell.part() != 1
                    || !pos.below().equals(cell.origin())) return stopped;
            var result = WindStructureProbe.inspect(view, pos.below());
            boolean complete = main.equals(result.mainOrigin()) && (result.status() == WindStructureProbe.Status.COMPLETE
                    || result.status() == WindStructureProbe.Status.COMPLETE_NOT_WORKING);
            return new WindVisualState(top.rotor(), complete, complete && result.status() == WindStructureProbe.Status.COMPLETE);
        }
        return stopped;
    }
}
