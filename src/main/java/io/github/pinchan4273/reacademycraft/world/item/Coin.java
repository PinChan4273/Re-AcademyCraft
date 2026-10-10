package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.world.entity.CoinEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** コイン（原作ItemCoin）。投げられるのは同時に1つだけで、出現と引き落としはサーバーが行う。 */
public final class Coin extends DescribedItem {
    public Coin(){super(new Properties());}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        return player instanceof ServerPlayer server&&CoinEntity.toss(server,hand).isPresent()
                ?InteractionResultHolder.consume(player.getItemInHand(hand)):InteractionResultHolder.fail(stack);
    }
}
