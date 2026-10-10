package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.ImagFusorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** 原作BlockImagFusor: 虚像投影液と電力で、低い結晶から上位の結晶を作る機械。操作と寿命は他の移植済みの機械に従う。 */
public final class ImagFusorBlock extends BaseEntityBlock {
    /**
     * 原作BlockImagFusor: 設置した者から決めるFACINGと、稼働中の前面。原作はクライアントの時計で毎秒2.5の速さでFRAME 1〜4を進める。
     * ここでは1つの稼働状態が、原作の4枚のフレームをその速さのアニメーションテクスチャとして表示する。原作は稼働中に明るさ6でも光る。
     */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty WORKING =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("working");
    public ImagFusorBlock() {
        super(Properties.of().strength(3f).requiresCorrectToolForDrops().sound(SoundType.STONE)
                .lightLevel(state -> state.getValue(WORKING) ? 6 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(WORKING, false));
    }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ImagFusorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return createTickerHelper(type, AcademyContent.IMAG_FUSOR_ENTITY.get(), (l, p, s, tile) ->
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ImagFusorSound.update(tile)));
        return createTickerHelper(type, AcademyContent.IMAG_FUSOR_ENTITY.get(), ImagFusorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer owner) {
            if (!(level.getBlockEntity(pos) instanceof ImagFusorBlockEntity tile) || tile.readOnly() || owner.containerMenu != owner.inventoryMenu) return InteractionResult.FAIL;
            NetworkHooks.openScreen(owner, new SimpleMenuProvider((id, inv, p) -> new ImagFusorMenu(id, inv, tile),
                    Component.translatable("container.academy.imag_fusor")), pos);
        } return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ImagFusorBlockEntity tile && !tile.readOnly()) {
                for (int i = 0; i < 5; i++) {
                    var stack = tile.inventory().getStackInSlot(i); tile.inventory().setStackInSlot(i, ItemStack.EMPTY);
                    if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
                }
            } super.onRemove(state, level, pos, next, moving);
        }
    }
}
