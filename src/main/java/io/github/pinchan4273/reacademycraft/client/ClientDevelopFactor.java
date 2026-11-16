package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.develop.FactorPlan;
import io.github.pinchan4273.reacademycraft.network.DevelopFactorPlan;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

/** クライアントが受け取った、開いている開発機の取得・変更の計画（1.5.2）。表示のためだけの値。 */
public final class ClientDevelopFactor {
    private ClientDevelopFactor() { }
    private static int menu = -1;
    private static @Nullable FactorPlan plan;
    /** 開始の要求を、計画の食い違いで断られた回数（コンソールが、送った後に増えたかを見る）。 */
    private static int refusals;

    public static void receive(DevelopFactorPlan packet) {
        menu = packet.menu(); plan = packet.plan();
        if (packet.refused()) refusals++;
    }
    /** そのメニューの計画を受け取ったか（メニューが変われば、届くまで受け取っていないと扱う）。 */
    public static boolean received(int containerId) {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu.containerId == containerId && menu == containerId;
    }
    /** そのメニューの計画（受け取っていない、または出す計画が無ければnull）。 */
    public static @Nullable FactorPlan plan(int containerId) { return received(containerId) ? plan : null; }
    /** 計画の食い違いで開始を断られた回数（増えたら、画面は新しい計画を出し直す）。 */
    public static int refusals() { return refusals; }
}
