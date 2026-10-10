package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 3種類の原作BlockNode。原作はmetadataを持つ1つのブロックとしていた。 */
public final class WirelessNodeBlock extends BaseEntityBlock {
    private final WirelessNodeBlockEntity.Kind kind;
    /** 原作BlockNodeのCONNECTEDとENERGY。上面と側面のテクスチャを選ぶ。 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CONNECTED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("connected");
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty ENERGY =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("energy", 0, 4);
    public WirelessNodeBlock(WirelessNodeBlockEntity.Kind kind) {
        super(Properties.of().strength(2.5f).requiresCorrectToolForDrops().sound(SoundType.METAL));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false).setValue(ENERGY, 0));
    }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(CONNECTED, ENERGY);
    }
    public WirelessNodeBlockEntity.Kind kind() { return kind; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WirelessNodeBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AcademyContent.NODE_ENTITY.get(), WirelessNodeBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.world.entity.player.Player player
                && level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity tile) tile.setPlacer(player);
    }
    /** 原作TileInventory: nodeが無くなると2つのアイテムをドロップする。 */
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity tile && !tile.readOnly()) {
                for (int slot = 0; slot < 2; slot++) {
                    var item = tile.inventory().getStackInSlot(slot); tile.inventory().setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    if (!item.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, item);
                }
            }
            super.onRemove(state, level, pos, next, moving);
        }
    }
    @Override public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return net.minecraft.world.InteractionResult.FAIL;
        if (player instanceof net.minecraft.server.level.ServerPlayer owner) {
            if (!(level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity tile) || tile.readOnly()
                    || owner.containerMenu != owner.inventoryMenu) return net.minecraft.world.InteractionResult.FAIL;
            net.minecraftforge.network.NetworkHooks.openScreen(owner, new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new io.github.pinchan4273.reacademycraft.world.menu.WirelessNodeMenu(id, inv, tile),
                    net.minecraft.network.chat.Component.translatable("container.academy.wireless_node")),
                    buffer -> { buffer.writeBlockPos(pos); buffer.writeUtf(tile.nodeName(), 32);
                        buffer.writeUtf(tile.placer() == null ? "" : tile.placer(), 64);
                        buffer.writeUtf(tile.placer() != null && tile.placer().equals(owner.getGameProfile().getName()) ? tile.password() : "", 32); });
        } return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
