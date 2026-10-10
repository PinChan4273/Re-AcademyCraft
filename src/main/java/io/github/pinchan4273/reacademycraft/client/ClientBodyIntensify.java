package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.BodyIntensifyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 術者のBody Intensifyの状態（サーバーが最後に伝えたもの）: OFF・予備動作中・発動中と、どの枠から使ったか。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientBodyIntensify {
    private static int phase = BodyIntensifyState.OFF, slot = -1;
    private static ClientLevel world;
    private ClientBodyIntensify() { }
    public static void receive(BodyIntensifyState state) {
        world = Minecraft.getInstance().level;
        phase = state.phase(); slot = state.phase() == BodyIntensifyState.OFF ? -1 : state.slot();
    }
    private static void current() { if (Minecraft.getInstance().level != world) reset(); }
    public static boolean warmingUp() { current(); return phase == BodyIntensifyState.WARMUP; }
    public static boolean running() { current(); return phase == BodyIntensifyState.ACTIVE; }
    /** キーでONにした枠。OFFのときは-1。 */
    public static int slot() { current(); return slot; }
    public static void reset() { phase = BodyIntensifyState.OFF; slot = -1; world = null; }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
