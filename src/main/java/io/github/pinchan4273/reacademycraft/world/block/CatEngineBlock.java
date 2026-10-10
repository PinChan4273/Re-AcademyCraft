package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 原作BlockCatEngine: 右クリックで、届く範囲のnodeのうちそれを覆うものからランダムに選んで繋ぎ、もう一度で外す。 */
public final class CatEngineBlock extends BaseEntityBlock {
    public CatEngineBlock() { super(Properties.of().strength(0f).requiresCorrectToolForDrops() /* 原作: 硬さ無し、Material.ROCK */.sound(SoundType.STONE).noOcclusion()); }
    /** 原作EnumBlockRenderType.INVISIBLE: 代わりにRenderCatEngineが絵を描く。 */
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AcademyContent.CAT_ENGINE_ENTITY.get(), CatEngineBlockEntity::serverTick);
    }
    /** 原作isOpaqueCubeはfalseで、1.12ではそれにより光も通していた。 */
    @Override public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) { return true; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CatEngineBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.sidedSuccess(level.isClientSide);
        if (!(level.getBlockEntity(pos) instanceof CatEngineBlockEntity tile) || tile.readOnly()) return InteractionResult.FAIL;
        var networks = WirelessNetworks.of(server);
        if (networks.connectionOf(pos) != null) {
            networks.unlinkUser(pos);
            player.sendSystemMessage(Component.translatable("academy.cat_engine.unlink"));
            return InteractionResult.CONSUME;
        }
        var nodes = WirelessNetworks.nodesInRange(level, pos);
        if (nodes.isEmpty()) {
            player.sendSystemMessage(Component.translatable("academy.cat_engine.not_found"));
            return InteractionResult.CONSUME;
        }
        var node = nodes.get(level.getRandom().nextInt(nodes.size()));
        boolean linked = networks.linkUser(level, node, pos);
        player.sendSystemMessage(Component.translatable(linked ? "academy.cat_engine.linked" : "academy.cat_engine.refused",
                level.getBlockEntity(node) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity n ? n.nodeName() : node.toShortString()));
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) {
            if (level instanceof ServerLevel server) WirelessNetworks.of(server).unlinkUser(pos);
            super.onRemove(state, level, pos, next, moving);
        }
    }
}
