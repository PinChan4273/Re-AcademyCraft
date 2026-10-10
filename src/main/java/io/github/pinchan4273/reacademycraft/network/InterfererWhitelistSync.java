package io.github.pinchan4273.reacademycraft.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 開いているパネルへの機械のホワイトリスト。パネルからの要求はInterfererWhitelist。
 * チャンネルはメッセージのクラスごとに1方向なので、2つは別のrecordにしている。
 */
public record InterfererWhitelistSync(InterfererWhitelist list) {
    public InterfererWhitelistSync(int containerId, List<String> names) { this(new InterfererWhitelist(containerId, names)); }
    public void encode(FriendlyByteBuf b) { list.encode(b); }
    public static InterfererWhitelistSync decode(FriendlyByteBuf b) { return new InterfererWhitelistSync(InterfererWhitelist.decode(b)); }
}
