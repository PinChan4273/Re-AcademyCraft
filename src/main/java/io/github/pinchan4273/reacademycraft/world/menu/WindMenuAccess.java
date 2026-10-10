package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.world.WindGeneratorRules;
import io.github.pinchan4273.reacademycraft.world.WindStructureProbe;
import io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindItemExchange;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 原作の別々の土台・本体のメニューのための、サーバーでの結び付きと数値のスナップショット。
 * 画面は原作の風力発電機のGUI（WeAthFolD）。
 * この10個のshortを通して、インベントリやブロックエンティティの完全なNBTは公開しない。
 */
public final class WindMenuAccess {
    public static final int DATA_COUNT = 10;
    public static final int NO_MAIN = Integer.MIN_VALUE;
    private final Player owner;
    private final Level level;
    private final WindBodyBlockEntity part, root;
    private final UUID structure;
    private final BlockPos clickedPos, rootPos;
    private final boolean main;
    private final int[] values = new int[DATA_COUNT];
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return values[index]; }
        @Override public void set(int index, int value) { /* サーバーの観測はクライアントから書けない。 */ }
        @Override public int getCount() { return DATA_COUNT; }
    };

    private WindMenuAccess(Player owner, WindBodyBlockEntity part, WindBodyBlockEntity root) {
        this.owner = owner; this.level = owner.level(); this.part = part; this.root = root;
        structure = root.probeCell().structureId(); clickedPos = part.getBlockPos().immutable();
        rootPos = root.getBlockPos().immutable();
        main = ((WindBodyBlock)root.getBlockState().getBlock()).body() == WindBodyBlock.Body.MAIN;
        clear();
    }

    public static @Nullable WindMenuAccess bind(Player player, WindBodyBlockEntity clicked) {
        if (player.level().isClientSide || clicked.getLevel() != player.level()) return null;
        WindBodyBlockEntity root = clicked.root();
        if (root == null || !root.complete()) return null;
        WindMenuAccess access = new WindMenuAccess(player, clicked, root);
        if (!access.stillValid(player)) return null;
        access.refresh(); return access;
    }

    /** オブジェクトとUUIDの正確な同一性で、置き換えや再生成が古いメニューを再利用するのを防ぐ。 */
    public boolean stillValid(Player player) {
        return player == owner && player.level() == level && !level.isClientSide
                && player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(clickedPos.getX() + .5, clickedPos.getY() + .5, clickedPos.getZ() + .5) <= 64
                && level.hasChunkAt(clickedPos) && level.hasChunkAt(rootPos)
                && level.getBlockEntity(clickedPos) == part && level.getBlockEntity(rootPos) == root
                && !part.isRemoved() && !part.removing() && !part.isFuture()
                && !root.isRemoved() && !root.removing() && !root.isFuture()
                && structure != null && structure.equals(root.probeCell().structureId())
                && structure.equals(part.probeCell().structureId()) && part.root() == root && root.complete();
    }

    public BlockPos rootPos() { return rootPos; }
    public boolean main() { return main; }
    public ContainerData data() { return data; }

    /**
     * このメニューの所有者と、まだ所有しているカーソル・ホットバーのアイテムに限って入れ替える。
     * GUIは、返った取引が成功した後でだけ元を消費する。
     */
    public WindItemExchange exchange(Player player, ItemStack incoming,
            java.util.function.BooleanSupplier sourceStillValid) {
        return exchange(player, incoming, false, sourceStillValid);
    }
    public WindItemExchange exchange(Player player, ItemStack incoming, boolean onlyIfEmpty,
            java.util.function.BooleanSupplier sourceStillValid) {
        if (!stillValid(player)) return WindItemExchange.rejected();
        return part.exchangeMenuItem(incoming, onlyIfEmpty, () -> stillValid(player)
                && sourceStillValid.getAsBoolean() && stillValid(player));
    }

    /** メニューがデータを送る前に1回呼び、すべてのフィールドが1つの観測を表すようにする。 */
    public boolean refresh() {
        if (!stillValid(owner)) { clear(); return false; }
        clear(); putInt(4, rootPos.getY()); values[9] = main ? 1 : 0;
        if (main) {
            putInt(6, rootPos.getY()); values[8] = root.hasFanInstalled() ? 1 : 0;
        } else {
            var view = WindStructureProbe.loadedWorld(level, p -> WindStructureProbe.decodeLoaded(level, p));
            var observed = WindStructureProbe.inspect(view, rootPos);
            putInt(0, root.energyUnits()); values[2] = observed.status().ordinal(); values[3] = observed.pillars();
            if (observed.mainOrigin() != null) {
                BlockPos top = observed.mainOrigin(); putInt(6, top.getY());
                if (level.hasChunkAt(top) && level.getBlockEntity(top) instanceof WindBodyBlockEntity rotor)
                    values[8] = rotor.hasFanInstalled() ? 1 : 0;
            }
        }
        return true;
    }

    private void clear() {
        Arrays.fill(values, 0); values[2] = WindStructureProbe.Status.INVALID_BASE.ordinal();
        putInt(6, NO_MAIN);
    }
    private void putInt(int index, int value) { values[index] = value & 65535; values[index + 1] = value >>> 16; }
    private static int readInt(ContainerData data, int index) {
        return (data.get(index) & 65535) | ((data.get(index + 1) & 65535) << 16);
    }
    public static int energyUnits(ContainerData data) {
        return Math.max(0, Math.min(WindGeneratorRules.CAPACITY_UNITS, readInt(data, 0)));
    }
    public static WindStructureProbe.Status status(ContainerData data) {
        int value = data.get(2);
        return value >= 0 && value < WindStructureProbe.Status.values().length
                ? WindStructureProbe.Status.values()[value] : WindStructureProbe.Status.INVALID_BASE;
    }
    public static int pillars(ContainerData data) { return Math.max(0, Math.min(41, data.get(3))); }
    public static int altitude(ContainerData data) { return readInt(data, 4); }
    public static int mainAltitude(ContainerData data) { return readInt(data, 6); }
    public static boolean fanInstalled(ContainerData data) { return data.get(8) == 1; }
}
