package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.TerminalSnapshot;
import io.github.pinchan4273.reacademycraft.terminal.TerminalSettings;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * 端末のsnapshotのclient側。サーバーが読み込まないこのクラスに置くことで、専用サーバーでclientの型の名前を
 * 一切参照しないようにしている。
 */
public final class ClientTerminal {
    private static final Map<String, Boolean> SETTINGS = new HashMap<>();
    private static boolean maySet;
    private ClientTerminal() { }
    public static void receive(TerminalSnapshot packet) {
        packet.handle(Minecraft.getInstance().player);
        for (int i = 0; i < TerminalSettings.ALL.size(); i++)
            SETTINGS.put(TerminalSettings.ALL.get(i), packet.settings().get(i));
        maySet = packet.maySet();
    }
    public static void receive(io.github.pinchan4273.reacademycraft.network.MediaSnapshot packet) { packet.handle(Minecraft.getInstance().player); }
    /** サーバーが最後に伝えた値。 */
    public static boolean setting(String id) { return Boolean.TRUE.equals(SETTINGS.get(id)); }
    /** このプレイヤーが変更できるか: 原作と同じく、シングルプレイのワールドの主だけ。 */
    public static boolean maySet() { return maySet; }
}
