package io.github.pinchan4273.reacademycraft.world.item;

import net.minecraft.world.InteractionResult;
import io.github.pinchan4273.reacademycraft.world.entity.CoinEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** コイン（原作ItemCoin）。投げられるのは同時に1つだけで、出現と引き落としはサーバーが行う。 */
public final class Coin extends DescribedItem {
    public Coin(){super(io.github.pinchan4273.reacademycraft.world.AcademyIds.item());}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(level.isClientSide())return InteractionResult.SUCCESS;
        return player instanceof ServerPlayer server&&CoinEntity.toss(server,hand).isPresent()
                ?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
}
