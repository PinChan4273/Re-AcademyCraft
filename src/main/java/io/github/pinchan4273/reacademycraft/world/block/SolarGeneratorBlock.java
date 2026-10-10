package io.github.pinchan4273.reacademycraft.world.block;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.menu.SolarGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 太陽光発電機。原作BlockSolarGen（WeAthFolD）: 岩の材質、硬さ1.5、ツルハシの採掘レベル1（石のツルハシ以上）。
 * 原作は当たり判定の指定（setBlockBounds）をコメントアウトしているので、当たり判定・選択枠・設置の判定は1ブロック全体。
 * isOpaqueCubeはfalseなので、隣のブロックの面を隠さない。ブロック自体は描かず（INVISIBLE）、SolarGeneratorRendererが
 * 原作のsolar.objを、原作のBlockMultiが設置時に決めた向きで描く。しゃがまずに使うと画面を開き、壊すと電池のスロットの中身を落とす。
 */
public final class SolarGeneratorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SolarGeneratorBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.STONE).strength(1.5f).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 置いた者の方を向く。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 原作BlockMulti.drMapの回転（度）。表は{0, 0, 180, 0, -90, 90}で、EnumFacingの序数（DOWN、UP、NORTH、SOUTH、WEST、EAST）で
     * 引く。NORTHが180、SOUTHが0になる（NORTH 0・SOUTH 180と読むと、モデルが半回転する）。
     */
    public static float legacyTurn(Direction facing) {
        return switch (facing) {
            case NORTH -> 180;
            case WEST -> -90;
            case EAST -> 90;
            default -> 0;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, AcademyContent.SOLAR_ENTITY.get(), SolarGeneratorBlockEntity::tick);
    }

    /** 原作onBlockActivated: しゃがんでいなければ画面を開く。 */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.FAIL;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer viewer) || !(level.getBlockEntity(pos) instanceof SolarGeneratorBlockEntity tile)) return InteractionResult.FAIL;
        // 書き込めない（新しい版の）データの機械や、別の画面を開いている者には開かない。
        if (tile.readOnly() || viewer.containerMenu != viewer.inventoryMenu) return InteractionResult.FAIL;
        var title = Component.translatable("container.academy.solar_gen");
        NetworkHooks.openScreen(viewer, new SimpleMenuProvider((id, inventory, who) -> new SolarGeneratorMenu(id, inventory, tile), title), pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.is(next.getBlock())) return;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarGeneratorBlockEntity tile && !tile.readOnly()) {
            ItemStack battery = tile.inventory().getStackInSlot(0);
            tile.inventory().setStackInSlot(0, ItemStack.EMPTY);
            if (!battery.isEmpty()) Containers.dropItemStack(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, battery);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
