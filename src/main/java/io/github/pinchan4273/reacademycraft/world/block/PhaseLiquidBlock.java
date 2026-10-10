package io.github.pinchan4273.reacademycraft.world.block;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * 原作BlockImagPhase: ITileEntityProviderでもある液体ブロック。各ブロックがTileImagPhaseを持ち、RenderImagPhaseLiquidが
 * その上に光る層を描けるようにする。
 */
public final class PhaseLiquidBlock extends LiquidBlock implements EntityBlock {
    private final Supplier<BlockEntityType<Tile>> type;
    public PhaseLiquidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties, Supplier<BlockEntityType<Tile>> type) {
        super(fluid, properties);
        this.type = type;
    }
    // この液体は物質の単位を使う。バニラのLiquidBlockは、バケツが無いと分かる前にこれを取り除いてしまう。
    @Override public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Tile(type.get(), pos, state); }

    /**
     * TileImagPhase: 独自のものは何も持たず、描かれるためにある。原作はこれをpass 1、つまり半透明ブロックの後に描くが、ここの
     * ブロックエンティティの描画処理ではそれができない。代わりにクライアントが読み込まれたtileを保ち、PhaseLiquidRendererがその段階で描く。
     */
    public static final class Tile extends BlockEntity {
        public Tile(BlockEntityType<Tile> type, BlockPos pos, BlockState state) { super(type, pos, state); }
        @Override public void onLoad() {
            super.onLoad();
            if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> io.github.pinchan4273.reacademycraft.client.PhaseLiquidRenderer.track(this));
        }
    }
}
