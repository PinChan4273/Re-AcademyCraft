package io.github.pinchan4273.reacademycraft.world;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class CoinEvents {
    private CoinEvents(){}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(event.getEntity() instanceof ServerPlayer player&&player.tickCount%5==0)
            CoinLedger.get(player.serverLevel()).refundDue(player);
    }
}
