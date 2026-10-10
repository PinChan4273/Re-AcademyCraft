package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * WeAthFolDによる原作ItemMatterUnitの4枚の絵のアニメーション。PNGは変更していない。
 * Forgeのproperty APIは値をclampするので、フレーム値を正規化している。ワールドやNBTは変更しない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MatterItemModels {
    private static long previousMillis = -1, elapsedMillis;
    private MatterItemModels() { }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(MatterItemModels::tick);
            ItemProperties.register(PhaseContent.FILLED.get(),
                ResourceLocation.fromNamespaceAndPath("academy", "frame"),
                (stack, level, entity, seed) -> stack.is(PhaseContent.FILLED.get())
                        ? (Math.floorMod(animationMillis() / 250L, 4L) / 4f) : 0f);
        });
    }
    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) animationMillis();
    }
    // 原作LambdaLibのGameTimerは一時停止中の時間を含まず、アイテムが見えていなくても進む。
    private static long animationMillis() {
        long now = Util.getMillis();
        if (previousMillis >= 0 && !Minecraft.getInstance().isPaused()) elapsedMillis += Math.max(0, now - previousMillis);
        previousMillis = now;
        return elapsedMillis;
    }
}
