package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * サーバーの能力の登録簿の要約（サーバー→クライアント、1.4.0のS4、司令室11の裁定D7）。クライアントは自分の要約と比べ、違いを
 * {@link AbilityRegistryVerdict}で返す。
 */
public record AbilityRegistryCheck(List<String> entries) {
    public static final int MAX_ENTRIES = 4096, MAX_ENTRY = 600;

    public AbilityRegistryCheck {
        if (entries.size() > MAX_ENTRIES || entries.stream().anyMatch(e -> e.isEmpty() || e.length() > MAX_ENTRY))
            throw new IllegalArgumentException("Invalid ability registry summary");
        entries = List.copyOf(entries);
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (var entry : entries) buffer.writeUtf(entry, MAX_ENTRY);
    }
    public static AbilityRegistryCheck decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid ability registry summary");
        var entries = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) entries.add(buffer.readUtf(MAX_ENTRY));
        return new AbilityRegistryCheck(entries);
    }
}
