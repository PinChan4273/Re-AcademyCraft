package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.DevelopAddonAvailability;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** クライアントが受け取った、開いている開発機で学べる外部の技能（1.4.0のS4）。表示のためだけの値。 */
public final class ClientDevelopAddons {
    private ClientDevelopAddons() { }
    private static int menu = -1;
    private static List<ResourceLocation> skills = List.of();

    public static void receive(DevelopAddonAvailability packet) { menu = packet.menu(); skills = packet.skills(); }
    /** 今のメニューで学べる外部の技能か。メニューが変われば、届くまで何も学べないと扱う。 */
    public static boolean available(ResourceLocation skill) {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu.containerId == menu && skills.contains(skill);
    }
}
