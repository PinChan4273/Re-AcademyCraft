package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.PhaseGeneratorMenu;
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

/** 虚像発電機（原作BlockPhaseGen）。採掘の段階と硬さは原作のもの。 */
public final class PhaseGeneratorBlock extends BaseEntityBlock {
    public PhaseGeneratorBlock() { super(Properties.of().strength(2.5f).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion()); }
    /** 原作BlockPhaseGenはINVISIBLEで不透明でない: RenderPhaseGenが独自のモデルを描く。 */
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) { return true; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PhaseGeneratorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AcademyContent.PHASE_GEN_ENTITY.get(), PhaseGeneratorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer owner) {
            if (!(level.getBlockEntity(pos) instanceof PhaseGeneratorBlockEntity tile) || tile.readOnly() || owner.containerMenu != owner.inventoryMenu) return InteractionResult.FAIL;
            NetworkHooks.openScreen(owner, new SimpleMenuProvider((id, inv, p) -> new PhaseGeneratorMenu(id, inv, tile), Component.translatable("container.academy.phase_gen")), pos);
        } return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PhaseGeneratorBlockEntity tile && !tile.readOnly()) {
                for (int i = 0; i < 3; i++) {
                    var stack = tile.inventory().getStackInSlot(i); tile.inventory().setStackInSlot(i, ItemStack.EMPTY);
                    if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
                }
            } super.onRemove(state, level, pos, next, moving);
        }
    }
}
