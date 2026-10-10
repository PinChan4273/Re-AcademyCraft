package io.github.pinchan4273.reacademycraft.world;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="academy")
public final class CoinEvents {
    private CoinEvents(){}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer player&&player.tickCount%5==0)
            CoinLedger.get(player.serverLevel()).refundDue(player);
    }
}
