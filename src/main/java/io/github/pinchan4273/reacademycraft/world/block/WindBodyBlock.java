package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.WindGeneratorRules;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.util.BlockSnapshot;
import org.jetbrains.annotations.Nullable;

/**
 * WeAthFolDの2セルの土台と3セルの本体に対する、取引としてのForgeのアダプタ。
 * 回転翼のインベントリは本体のルートに属し、エネルギーを生むのは土台のルートだけ。
 * メニュー、原作の絵、回転翼の描画（WindGeneratorRenderer）は実装済み。
 */
public final class WindBodyBlock extends BaseEntityBlock {
    public enum Body { BASE, MAIN }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    private final Body body;

    public WindBodyBlock(Body body) {
        super(BlockBehaviour.Properties.of().strength(4).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        this.body = body;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }
    public Body body() { return body; }
    public List<BlockPos> cells(BlockPos origin, Direction facing) {
        return body == Body.BASE ? WindGeneratorRules.baseCells(origin) : WindGeneratorRules.mainCells(origin, facing);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindBodyBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) != 0 ? null
                : createTickerHelper(type, AcademyContent.WIND_BODY_ENTITY.get(), WindBodyBlockEntity::tick);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        // 原作の土台の操作は、柱のアイテムの通常の設置を続けさせるので、塔を建てるのにUIを開閉する必要はない。
        if (player.isShiftKeyDown() || body == Body.BASE
                && player.getItemInHand(hand).is(AcademyContent.WIND_PILLAR_ITEM.get()))
            return net.minecraft.world.InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return net.minecraft.world.InteractionResult.FAIL;
        if (player instanceof net.minecraft.server.level.ServerPlayer owner) {
            if (owner.containerMenu != owner.inventoryMenu
                    || !(level.getBlockEntity(pos) instanceof WindBodyBlockEntity part))
                return net.minecraft.world.InteractionResult.FAIL;
            var access = io.github.pinchan4273.reacademycraft.world.menu.WindMenuAccess.bind(owner, part);
            if (access == null) return net.minecraft.world.InteractionResult.FAIL;
            net.minecraftforge.network.NetworkHooks.openScreen(owner, new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, viewer) -> access.stillValid(viewer)
                            ? new io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu(id, inventory, part, access) : null,
                    net.minecraft.network.chat.Component.translatable("container.academy.windgen_" + (access.main() ? "main" : "base"))),
                    access.rootPos());
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (level.getBlockEntity(pos) instanceof WindBodyBlockEntity part) part.reconcile();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return fits(context, state) ? state : null;
    }
    private boolean fits(BlockPlaceContext context, BlockState state) {
        Level level = context.getLevel(); Player player = context.getPlayer();
        List<BlockPos> positions = cells(context.getClickedPos(), state.getValue(FACING));
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    // 原作ItemBlockMulti: 各セルは置き換え可能（草、雪、水も）であればよく、エンティティを確かめるのは原点だけ（World.mayPlace）。
                    || (i == 0 ? !level.getBlockState(pos).canBeReplaced(context) : !level.getBlockState(pos).canBeReplaced())
                    || i == 0 && !level.isUnobstructed(state.setValue(PART, i), pos,
                        player == null ? net.minecraft.world.phys.shapes.CollisionContext.empty()
                                : net.minecraft.world.phys.shapes.CollisionContext.of(player))
                    || player != null && (!level.mayInteract(player, pos)
                        || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) return false;
        }
        return true;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        UUID id = UUID.randomUUID(); List<BlockPos> positions = cells(pos, state.getValue(FACING));
        for (int i = 0; i < positions.size(); i++) {
            BlockPos cell = positions.get(i);
            if (level.getBlockState(cell).equals(state.setValue(PART, i))
                    && level.getBlockEntity(cell) instanceof WindBodyBlockEntity part)
                part.initialize(pos, id);
        }
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide)
            dismantle(level, pos, state, !player.isCreative() && player.hasCorrectToolForDrops(state));
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide
                && !level.restoringBlockSnapshots && !level.captureBlockSnapshots)
            dismantle(level, pos, state, true);
        super.onRemove(state, level, pos, next, moving);
    }
    private void dismantle(Level level, BlockPos removedPos, BlockState state, boolean dropRoot) {
        if (!(level.getBlockEntity(removedPos) instanceof WindBodyBlockEntity removed)
                || removed.removing() || removed.isFuture()) return;
        WindBodyBlockEntity root = removed.root();
        if (root == null || root.isFuture()) return;
        List<BlockPos> positions = cells(root.getBlockPos(), root.getBlockState().getValue(FACING));
        var matches = new ArrayList<BlockPos>();
        for (BlockPos cell : positions)
            if (level.hasChunkAt(cell) && level.getBlockEntity(cell) instanceof WindBodyBlockEntity part
                    && part.sameStructure(root)) {
                part.beginRemoval(); matches.add(cell);
            }
        root.dropFanOnRemoval();
        root.dropChargeOnRemoval();
        for (BlockPos cell : matches)
            if (!cell.equals(removedPos)) level.destroyBlock(cell, dropRoot && cell.equals(root.getBlockPos()));
    }

    public static final class WindBodyItem extends io.github.pinchan4273.reacademycraft.world.item.WindPartItem {
        public WindBodyItem(Block block, Properties properties) { super(block, properties); }
        @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
            WindBodyBlock body = (WindBodyBlock)getBlock();
            if (!body.fits(context, state)) return false;
            Level level = context.getLevel();
            List<BlockPos> positions = body.cells(context.getClickedPos(), state.getValue(FACING));
            var snapshots = new ArrayList<BlockSnapshot>();
            for (BlockPos pos : positions) snapshots.add(BlockSnapshot.create(level.dimension(), level, pos));
            for (int i = 0; i < positions.size(); i++) {
                if (!level.setBlock(positions.get(i), state.setValue(PART, i),
                        Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE)) {
                    boolean capture = level.captureBlockSnapshots, restoring = level.restoringBlockSnapshots;
                    try {
                        level.captureBlockSnapshots = false; level.restoringBlockSnapshots = true;
                        for (int j = i - 1; j >= 0; j--) snapshots.get(j).restore(true, false);
                    } finally {
                        level.captureBlockSnapshots = capture; level.restoringBlockSnapshots = restoring;
                    }
                    return false;
                }
            }
            return true;
        }
    }
}
