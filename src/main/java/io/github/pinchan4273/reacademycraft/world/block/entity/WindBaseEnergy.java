package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.world.WindGeneratorRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 原作の風力のバッファそのもの（WeAthFolDのTileWindGenBase/TileGeneratorBase）。
 * ワールドの所有権はWindBodyBlockEntityが守る。変更可能な公開バッファや
 * クライアント側のsetterは公開しない。
 */
final class WindBaseEnergy {
    static final String KEY = "academy_wind_base";
    private int units;
    private @Nullable Tag original;
    private CompoundTag preserved = new CompoundTag();
    private boolean readOnly;
    private long processedTick = Long.MIN_VALUE, exportedTick = Long.MIN_VALUE;
    private int exportedFE, reservedChargeUnits;

    boolean readOnly() { return readOnly; }
    int units() { return readOnly ? 0 : units; }

    void load(CompoundTag tag, boolean root) {
        units = 0; original = null; preserved = new CompoundTag(); readOnly = false;
        processedTick = exportedTick = Long.MIN_VALUE; exportedFE = reservedChargeUnits = 0;
        if (!tag.contains(KEY)) return; // エネルギー導入前の風力のセーブは空から始まる。
        original = tag.get(KEY).copy();
        if (!root || !(original instanceof CompoundTag saved)
                || !saved.contains("schema_version", Tag.TAG_INT) || saved.getInt("schema_version") != 1
                || !saved.contains("energy_units", Tag.TAG_INT)) {
            readOnly = true; return;
        }
        preserved = saved.copy();
        units = Math.max(0, Math.min(WindGeneratorRules.CAPACITY_UNITS, saved.getInt("energy_units")));
    }

    void save(CompoundTag tag, boolean root, boolean futureBody) {
        if (readOnly || futureBody || !root) {
            if (original != null) tag.put(KEY, original.copy());
            return;
        }
        CompoundTag saved = preserved.copy();
        // 受け口のコールバックが、エネルギーを予約している間に所有者を保存することがある。取引前の合計を保存し、精算が後で最終的な合計を書く。
        saved.putInt("schema_version", 1);
        saved.putInt("energy_units", Math.min(WindGeneratorRules.CAPACITY_UNITS, units + reservedChargeUnits));
        tag.put(KEY, saved);
    }

    /** 発電とアイテムの充電をまとめて確保する。発電が0でも同じ。 */
    boolean beginTick(long tick) {
        if (readOnly || processedTick == tick) return false;
        processedTick = tick; return true;
    }

    /** 読み込まれていない間の追いつきは無い。呼び出し元は先にこのゲームtickを確保しなければならない。 */
    int generate(int amount) {
        if (readOnly) return 0;
        int added = Math.max(0, Math.min(WindGeneratorRules.CAPACITY_UNITS - units, amount));
        units += added; return added;
    }

    /** 原作のアイテムの経路は独自の300 IF/tの上限を持ち、FEの出力とは独立している。 */
    int reserveChargeFE() {
        if (readOnly || reservedChargeUnits != 0) return 0;
        int offered = Math.min(WindGeneratorRules.OUTPUT_FE, units / WindGeneratorRules.UNITS_PER_FE);
        reservedChargeUnits = offered * WindGeneratorRules.UNITS_PER_FE;
        units -= reservedChargeUnits; return offered;
    }

    void settleCharge(int offeredFE, int acceptedFE, boolean refund) {
        int expected = Math.max(0, offeredFE) * WindGeneratorRules.UNITS_PER_FE;
        if (reservedChargeUnits != expected) throw new IllegalStateException("Wind charge reservation mismatch");
        int accepted = Math.max(0, Math.min(offeredFE, acceptedFE));
        reservedChargeUnits = 0;
        if (refund) units += (offeredFE - accepted) * WindGeneratorRules.UNITS_PER_FE;
    }

    /** 共有の外部へのFEの予算。原作の将来のアイテム充電の経路とは別。 */
    int extract(int amount, boolean simulate, long tick) {
        if (readOnly || amount <= 0) return 0;
        int budget = WindGeneratorRules.OUTPUT_FE - (exportedTick == tick ? exportedFE : 0);
        int sent = Math.min(amount, Math.min(budget, units / WindGeneratorRules.UNITS_PER_FE));
        if (!simulate && sent > 0) {
            if (exportedTick != tick) { exportedTick = tick; exportedFE = 0; }
            exportedFE += sent; units -= sent * WindGeneratorRules.UNITS_PER_FE;
        }
        return sent;
    }
}
