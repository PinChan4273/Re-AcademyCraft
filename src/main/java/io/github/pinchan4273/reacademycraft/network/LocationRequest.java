package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.LocationTeleport;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Location Teleportの一覧の要求。原作のperformメッセージはクライアントから地点全体を運び、送られたとおりに従っていた。
 * ここでは登録済みの地点を番号で指し、確認はすべてサーバーに任せる（LocationTeleport.perform）。
 */
public record LocationRequest(int action, int index, String name) {
    public static final int QUERY = 0, ADD = 1, REMOVE = 2, PERFORM = 3;
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    public LocationRequest {
        if (action < QUERY || action > PERFORM || index < 0 || index >= LocationTeleport.MAX_LOCATIONS
                || name.length() > LocationTeleport.NAME_LENGTH * 2)
            throw new IllegalArgumentException("Invalid location request");
    }
    public static LocationRequest query() { return new LocationRequest(QUERY, 0, ""); }
    public static LocationRequest add(String name) { return new LocationRequest(ADD, 0, LocationTeleport.clean(name)); }
    public static LocationRequest remove(int index) { return new LocationRequest(REMOVE, index, ""); }
    public static LocationRequest perform(int index) { return new LocationRequest(PERFORM, index, ""); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action); buffer.writeByte(index); buffer.writeUtf(name, LocationTeleport.NAME_LENGTH * 2);
    }
    public static LocationRequest decode(FriendlyByteBuf buffer) {
        return new LocationRequest(buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUtf(LocationTeleport.NAME_LENGTH * 2));
    }

    public void handle(ServerPlayer player) {
        if (player == null) return;
        // 他の能力の要求と同じく、1tickに1要求。
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_REQUEST.put(player, now);
        if (previous != null && previous == now) return;
        switch (action) {
            case ADD -> LocationTeleport.add(player, name);
            case REMOVE -> LocationTeleport.remove(player, index);
            case PERFORM -> {
                var failure = LocationTeleport.perform(player, index);
                if (!failure.isEmpty()) player.displayClientMessage(Component.translatable(failure), true);
            }
            default -> { }
        }
        LocationList.send(player);
    }
}
