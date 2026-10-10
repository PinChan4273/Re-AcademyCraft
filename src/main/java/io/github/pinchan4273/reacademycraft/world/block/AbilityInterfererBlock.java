package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * 原作BlockAbilityInterferer: 周囲の全員に能力を使わせない機械。
 * 原作と同じく設置した者を覚え、その者には作用しない。
 */
public final class AbilityInterfererBlock extends BaseEntityBlock {
    /** 原作BlockAbilityInterferer.PROP_ON: オンの間のオンのテクスチャ。 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ON =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("on");
    public AbilityInterfererBlock() {
        // 原作BlockAbilityInterfererは硬さを設定しない（0）が、そのMaterial.ROCKはドロップにツルハシが要る。
        super(Properties.of().strength(0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
        registerDefaultState(stateDefinition.any().setValue(ON, false));
    }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(ON); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AbilityInterfererBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AcademyContent.INTERFERER_ENTITY.get(), AbilityInterfererBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof AbilityInterfererBlockEntity tile) tile.setPlacer(player);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer owner) {
            if (!(level.getBlockEntity(pos) instanceof AbilityInterfererBlockEntity tile) || tile.readOnly()
                    || owner.containerMenu != owner.inventoryMenu) return InteractionResult.FAIL;
            NetworkHooks.openScreen(owner, new SimpleMenuProvider((id, inv, p) -> new AbilityInterfererMenu(id, inv, tile),
                    Component.translatable("container.academy.ability_interferer")), pos);
        } return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof AbilityInterfererBlockEntity tile && !tile.readOnly()) {
                var stack = tile.inventory().getStackInSlot(0); tile.inventory().setStackInSlot(0, ItemStack.EMPTY);
                if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
            } super.onRemove(state, level, pos, next, moving);
        }
    }
}
