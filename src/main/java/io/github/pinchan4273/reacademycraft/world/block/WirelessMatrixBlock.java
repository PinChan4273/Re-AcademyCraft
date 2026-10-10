package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 原作BlockMatrix: 無線ネットワークを持つ。1辺2ブロックのBlockMultiで、原点がtileを持ち、他の7つのセルは置いた向きに対して
 * BlockMulti.rotateが原作の子ブロックを置く位置に立つ。どのセルでも原点のパネルを開き、どのセルを壊しても機械全体が壊れ、
 * ドロップするのは原点だけ。原作はどのセルも描かず、モデルだけを描く。
 */
public final class WirelessMatrixBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    /** BlockMatrixのaddSubBlockの呼び出し（順に、北向きで見たもの）。原点はpart 0。 */
    private static final int[][] SUB = {{0, 0, 0}, {0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
    /** まとめて壊しているセル。1つを壊したことで他の破壊を再び始めないようにする。 */
    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);

    public WirelessMatrixBlock() {
        // 原作BlockMatrixのsetLightLevel(1f): どのセルも明るさ15。
        super(Properties.of().strength(3f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().lightLevel(state -> 15));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new WirelessMatrixBlockEntity(pos, state) : null;
    }

    /** BlockMulti.rotate: 置いた向きに対する子ブロックのずれ。 */
    public static BlockPos offset(int part, Direction facing) {
        int dx = SUB[part][0], dy = SUB[part][1], dz = SUB[part][2];
        return switch (facing) {
            case EAST -> new BlockPos(-dz, dy, dx);
            case WEST -> new BlockPos(dz, dy, -dx);
            case SOUTH -> new BlockPos(-dx, dy, -dz);
            default -> new BlockPos(dx, dy, dz);
        };
    }
    public static List<BlockPos> cells(BlockPos origin, Direction facing) {
        var cells = new ArrayList<BlockPos>(8);
        for (int part = 0; part < 8; part++) cells.add(origin.offset(offset(part, facing)));
        return cells;
    }
    /** BlockMulti.getOrigin: このセルの原点の位置。 */
    public static BlockPos origin(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.getValue(PART), state.getValue(FACING)));
    }
    @Nullable public static WirelessMatrixBlockEntity originTile(Level level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(PART)) return null;
        var origin = origin(pos, state);
        return level.hasChunkAt(origin) && level.getBlockEntity(origin) instanceof WirelessMatrixBlockEntity tile ? tile : null;
    }

    /** BlockMulti.onBlockPlacedBy: 向きはrotMap[floor(yaw * 4 / 360 + 0.5) & 3]で、設置した者の方を向く側。 */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return fits(context.getLevel(), context.getClickedPos(), state.getValue(FACING), context) ? state : null;
    }
    /** 8つのセルがすべて空いているか: 原点は設置が求めるとおり置き換え可能で、残りは空。 */
    public static boolean fits(Level level, BlockPos origin, Direction facing, @Nullable BlockPlaceContext context) {
        var cells = cells(origin, facing);
        for (int part = 0; part < cells.size(); part++) {
            var pos = cells.get(part);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            var here = level.getBlockState(pos);
            if (part == 0 && context != null ? !here.canBeReplaced(context) : !here.canBeReplaced()) return false;
            var player = context == null ? null : context.getPlayer();
            if (player != null && !level.mayInteract(player, pos)) return false;
        }
        return true;
    }
    /** 原点を置いた後で他の7つのセルを置き、設置者を覚える。 */
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        build(level, pos, state.getValue(FACING));
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof WirelessMatrixBlockEntity tile) tile.setPlacer(player);
    }
    /** 既に立っている原点の周りの他のセルを埋める。設置とテストで使う。 */
    public static boolean build(Level level, BlockPos origin, Direction facing) {
        var cells = cells(origin, facing);
        for (int part = 1; part < cells.size(); part++) if (!level.getBlockState(cells.get(part)).canBeReplaced()) return false;
        var base = io.github.pinchan4273.reacademycraft.world.AcademyContent.MATRIX.get().defaultBlockState().setValue(FACING, facing);
        if (!level.getBlockState(origin).is(base.getBlock())) level.setBlock(origin, base.setValue(PART, 0), Block.UPDATE_ALL);
        for (int part = 1; part < cells.size(); part++) level.setBlock(cells.get(part), base.setValue(PART, part), Block.UPDATE_ALL);
        return true;
    }

    @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return net.minecraft.world.InteractionResult.FAIL;
        if (player instanceof net.minecraft.server.level.ServerPlayer owner) {
            // 原作onBlockActivatedは、使われたセルがどれでも原点のパネルを開く。
            var tile = originTile(level, pos, state);
            if (tile == null || tile.readOnly() || owner.containerMenu != owner.inventoryMenu) return net.minecraft.world.InteractionResult.FAIL;
            net.minecraftforge.network.NetworkHooks.openScreen(owner, new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new io.github.pinchan4273.reacademycraft.world.menu.WirelessMatrixMenu(id, inv, tile),
                    net.minecraft.network.chat.Component.translatable("container.academy.wireless_matrix")), buffer -> {
                        buffer.writeBlockPos(tile.getBlockPos());
                        String placer = tile.placer() == null ? "" : tile.placer();
                        buffer.writeUtf(placer, 64);
                        var network = io.github.pinchan4273.reacademycraft.energy.WirelessNetworks.of(owner.serverLevel()).networkAt(tile.getBlockPos());
                        buffer.writeUtf(placer.equals(owner.getGameProfile().getName()) && network != null ? network.password() : "", 32);
                    });
        } return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) dismantle(level, pos, state, !player.isCreative() && player.hasCorrectToolForDrops(state));
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && !level.restoringBlockSnapshots && !level.captureBlockSnapshots) dismantle(level, pos, state, true);
            if (!level.isClientSide && state.getValue(PART) == 0 && level.getBlockEntity(pos) instanceof WirelessMatrixBlockEntity tile && !tile.readOnly()) {
                for (int i = 0; i < 4; i++) {
                    var stack = tile.inventory().getStackInSlot(i); tile.inventory().setStackInSlot(i, ItemStack.EMPTY);
                    if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
                }
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    var networks = io.github.pinchan4273.reacademycraft.energy.WirelessNetworks.of(server);
                    var network = networks.networkAt(pos);
                    // 原作は、matrixが無くなったネットワークを外す。
                    if (network != null && network.matrix().equals(pos)) networks.dispose(network.ssid());
                }
            }
            super.onRemove(state, level, pos, next, moving);
        }
    }
    /**
     * BlockMulti.breakBlock: 構造全体がまとめて壊れる。アイテムをドロップするのは原点の戦利品だけなので、どのセルを壊しても、
     * 原点はドロップありで、残りはドロップ無しで壊す。
     */
    private static void dismantle(Level level, BlockPos removed, BlockState state, boolean drop) {
        if (DISMANTLING.get() || !state.hasProperty(PART)) return;
        DISMANTLING.set(true);
        try {
            var origin = origin(removed, state);
            var cells = cells(origin, state.getValue(FACING));
            for (int part = 0; part < cells.size(); part++) {
                var cell = cells.get(part);
                if (cell.equals(removed) || !level.hasChunkAt(cell)) continue;
                var here = level.getBlockState(cell);
                if (here.getBlock() instanceof WirelessMatrixBlock && here.getValue(FACING) == state.getValue(FACING) && here.getValue(PART) == part)
                    level.destroyBlock(cell, drop && part == 0);
            }
        } finally { DISMANTLING.set(false); }
    }
}
