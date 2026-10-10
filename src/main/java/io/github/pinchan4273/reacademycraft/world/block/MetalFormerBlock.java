package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.MetalFormerMenu;
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

/**
 * 原作BlockMetalFormer: 6つの面を、設置した者から決めるFACINGで回す。WORKINGは原作のisWorkInProgressで、ブロック状態に持たせ、
 * 原作のTileMetalFormer.updateSoundsと同じくクライアントがmachine.machine_workをループできるようにする。原作と同じく見た目は変えない。
 */
public final class MetalFormerBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty WORKING =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("working");
    public MetalFormerBlock() {
        super(Properties.of().strength(3).requiresCorrectToolForDrops().sound(SoundType.METAL));
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
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MetalFormerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return createTickerHelper(type, AcademyContent.METAL_ENTITY.get(), (l, p, s, tile) ->
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.MachineWorkSound.update(tile, WORKING, io.github.pinchan4273.reacademycraft.world.AcademySounds.MACHINE_WORK.get())));
        return createTickerHelper(type, AcademyContent.METAL_ENTITY.get(), MetalFormerBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer owner) {
            if (!(level.getBlockEntity(pos) instanceof MetalFormerBlockEntity tile) || tile.readOnly() || owner.containerMenu != owner.inventoryMenu) return InteractionResult.FAIL;
            NetworkHooks.openScreen(owner, new SimpleMenuProvider((id, inv, p) -> new MetalFormerMenu(id, inv, tile), Component.translatable("container.academy.metal_former")), pos);
        } return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof MetalFormerBlockEntity tile && !tile.readOnly()) {
                for (int i = 0; i < 3; i++) {
                    var stack = tile.inventory().getStackInSlot(i); tile.inventory().setStackInSlot(i, ItemStack.EMPTY);
                    if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
                }
            } super.onRemove(state, level, pos, next, moving);
        }
    }
}
