package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillModeState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/** 術者の切り替え型の技能のモードのうち、どれがONか（サーバーが最後に伝えたもの）。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientSkillModes {
    private static final Set<ResourceLocation> ACTIVE = new HashSet<>();
    private ClientSkillModes() { }
    public static void receive(SkillModeState state) { if (state.active()) ACTIVE.add(state.skill()); else ACTIVE.remove(state.skill()); }
    public static boolean active(ResourceLocation skill) { return skill != null && ACTIVE.contains(skill); }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { ACTIVE.clear(); }
}
