package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import io.github.pinchan4273.reacademycraft.crafting.MachineRecipes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** 26.2 はレシピを既定で同期しない。MachineRecipes が頼んで送らせた機械のレシピを受け取る。 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class ClientMachineRecipes {
    private ClientMachineRecipes() { }

    @SubscribeEvent
    public static void received(RecipesReceivedEvent event) { MachineRecipes.setClientRecipes(event.getRecipeMap()); }
}
