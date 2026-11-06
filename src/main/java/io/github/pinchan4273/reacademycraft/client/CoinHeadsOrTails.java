package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.entity.CoinEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 原作EntityCoinThrowingのPLAY_HEADS_OR_TAILS: プレイヤーが投げたコインが落ちてきたとき、headsOrTailsがONなら、
 * clientがどちらの面が出たかをランダムに決めてチャットで伝える。
 */
public final class CoinHeadsOrTails {
    private static int told;
    private static String last = "";
    private CoinHeadsOrTails() { }
    public static void landed(CoinEntity coin) {
        var player = Minecraft.getInstance().player;
        if (player == null || !coin.owner().filter(player.getUUID()::equals).isPresent()) return;
        if (!ClientSettings.get(ClientSettings.HEADS_OR_TAILS)) return;
        var message = Component.translatable("academy.headsOrTails." + player.getRandom().nextInt(2));
        player.sendSystemMessage(message);
        last = message.getString();
        told++;
    }
    /** テスト用の入口: このclientが伝えた回数。 */
    public static int told() { return told; }
    public static String last() { return last; }
}
