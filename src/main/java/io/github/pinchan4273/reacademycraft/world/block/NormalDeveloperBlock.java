package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.util.BlockSnapshot;
import org.jetbrains.annotations.Nullable;

/**
 * 据え置きの開発機（原作BlockDeveloper、WeAthFolD）。原作ACBlockMultiと同じく、原点と7つの子ブロック
 * （addSubBlock(0,1,0)、(0,0,1)、(0,1,1)、(0,2,1)、(0,0,2)、(0,1,2)、(0,2,2)）の8セルを占める。各セルは自分の番号（PART）と
 * 向き（FACING）を状態に持ち、原作BlockMultiと同じく、そこから原点の位置を逆算する。どの機械の部品かは位置と状態だけで決まり、
 * 保存する識別子は持たない。ブロックは描かず、開発機の描画処理が原点に機械を描く。
 */
public final class NormalDeveloperBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    private static final int[][] OFFSETS = {{0,0}, {1,0}, {0,1}, {1,1}, {2,1}, {0,2}, {1,2}, {2,2}};
    private final io.github.pinchan4273.reacademycraft.develop.DeveloperTier tier;
    public NormalDeveloperBlock() { this(io.github.pinchan4273.reacademycraft.develop.DeveloperTier.NORMAL); }
    public NormalDeveloperBlock(io.github.pinchan4273.reacademycraft.develop.DeveloperTier tier) {
        super(BlockBehaviour.Properties.of().strength(4).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());
        if (tier != io.github.pinchan4273.reacademycraft.develop.DeveloperTier.NORMAL && tier != io.github.pinchan4273.reacademycraft.develop.DeveloperTier.ADVANCED)
            throw new IllegalArgumentException("Stationary developer tier required");
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }
    public io.github.pinchan4273.reacademycraft.develop.DeveloperTier tier() { return tier; }
    /** この部品の状態から逆算した原点（原作BlockMulti.getOriginPos）。 */
    public static BlockPos origin(BlockPos pos, BlockState state) {
        int[] offset = OFFSETS[state.getValue(PART)];
        return pos.below(offset[0]).relative(state.getValue(FACING), offset[1]);
    }
    public static List<BlockPos> cells(BlockPos origin, Direction facing) {
        var result = new ArrayList<BlockPos>(8);
        for (int[] offset : OFFSETS) result.add(origin.above(offset[0]).relative(facing.getOpposite(), offset[1]));
        return List.copyOf(result);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NormalDeveloperBlockEntity(pos, state); }
    /** 原作BlockMulti: INVISIBLE。RenderDeveloperNormal/Advancedが原点に機械を描く。 */
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (level.getBlockEntity(pos) instanceof NormalDeveloperBlockEntity be) be.reconcile();
    }
    @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer owner) || player.containerMenu != player.inventoryMenu
                || !(level.getBlockEntity(pos) instanceof NormalDeveloperBlockEntity part)) return net.minecraft.world.InteractionResult.FAIL;
        var data = player.getCapability(io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        var main = part.main();
        if (main == null || data == null || data.isReadOnly()) return net.minecraft.world.InteractionResult.FAIL;
        var binding = main.bind(owner);
        // 原作BlockDeveloperは誰も使っていないときだけuse()を呼び、そうでなければ黙って何もしない。
        if (binding == null) return net.minecraft.world.InteractionResult.FAIL;
        io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents.sync(owner, true);
        try {
            net.minecraftforge.network.NetworkHooks.openScreen(owner, new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, p) -> new io.github.pinchan4273.reacademycraft.develop.DeveloperMenu(id, inventory, binding),
                    net.minecraft.network.chat.Component.translatable(getDescriptionId())), extra -> extra.writeBoolean(false));
        } finally {
            if (!(owner.containerMenu instanceof io.github.pinchan4273.reacademycraft.develop.DeveloperMenu menu) || !menu.usesDevice(binding)) binding.close();
        }
        // 原作MSG_GET_NODE: パネルはこの開発機が電力を引くnodeを表示する。
        if (owner.containerMenu instanceof io.github.pinchan4273.reacademycraft.develop.DeveloperMenu menu && menu.usesDevice(binding))
            io.github.pinchan4273.reacademycraft.network.DeveloperNode.send(owner, menu.containerId, main.getBlockPos());
        return net.minecraft.world.InteractionResult.CONSUME;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return fits(context, state) ? state : null;
    }
    private static boolean fits(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel(); var player = context.getPlayer(); var positions = cells(context.getClickedPos(), state.getValue(FACING));
        for (int i = 0; i < positions.size(); i++) {
            var pos = positions.get(i);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    // 原作ItemBlockMulti: 各セルは置き換え可能（草、雪、水も）であればよく、エンティティを確かめるのは原点だけ（World.mayPlace）。
                    || (i == 0 ? !level.getBlockState(pos).canBeReplaced(context) : !level.getBlockState(pos).canBeReplaced())
                    || i == 0 && !level.isUnobstructed(state.setValue(PART, i), pos, player == null ? net.minecraft.world.phys.shapes.CollisionContext.empty() : net.minecraft.world.phys.shapes.CollisionContext.of(player))
                    || player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) return false;
        }
        return true;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // アイテムのBlockEntityTagで、蓄えたエネルギーを注入させない。
        for (var cell : cells(pos, state.getValue(FACING)))
            if (level.getBlockEntity(cell) instanceof NormalDeveloperBlockEntity be) be.initialize();
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) dismantle(level, pos, state, !player.isCreative() && player.hasCorrectToolForDrops(state));
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && !level.restoringBlockSnapshots && !level.captureBlockSnapshots)
            dismantle(level, pos, state, true);
        super.onRemove(state, level, pos, next, moving);
    }
    private static void dismantle(Level level, BlockPos removedPos, BlockState state, boolean dropMain) {
        if (!(level.getBlockEntity(removedPos) instanceof NormalDeveloperBlockEntity removed) || removed.removing()) return;
        var main = removed.main(); if (main == null) return;
        var positions = cells(main.getBlockPos(), main.getBlockState().getValue(FACING));
        var matches = new ArrayList<BlockPos>();
        for (var cell : positions) if (level.hasChunkAt(cell) && level.getBlockEntity(cell) instanceof NormalDeveloperBlockEntity be && be.sameStructure(main)) {
            be.beginRemoval(); matches.add(cell);
        }
        // 再帰的なコールバックの前に、一致するグループ全体に印を付ける。戦利品は本体のセル1つだけ。
        for (var cell : matches) if (!cell.equals(removedPos))
            level.destroyBlock(cell, dropMain && cell.equals(main.getBlockPos()));
    }
    public static final class DeveloperItem extends BlockItem {
        public DeveloperItem(Block block, Properties properties) { super(block, properties); }
        @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
            if (!fits(context, state)) return false;
            var level = context.getLevel(); var positions = cells(context.getClickedPos(), state.getValue(FACING));
            var snapshots = new ArrayList<BlockSnapshot>();
            for (var pos : positions) snapshots.add(BlockSnapshot.create(level.dimension(), level, pos));
            for (int i = 0; i < positions.size(); i++) {
                if (!level.setBlock(positions.get(i), state.setValue(PART, i), Block.UPDATE_ALL | Block.UPDATE_IMMEDIATE)) {
                    boolean capture = level.captureBlockSnapshots, restoring = level.restoringBlockSnapshots;
                    try {
                        level.captureBlockSnapshots = false; level.restoringBlockSnapshots = true;
                        for (int j = i - 1; j >= 0; j--) snapshots.get(j).restore(true, false);
                    } finally { level.captureBlockSnapshots = capture; level.restoringBlockSnapshots = restoring; }
                    return false;
                }
            }
            return true; // Forgeは8つのセルすべてを、取り消し可能な1つの複数設置イベントとして取り込む。
        }
    }
}
