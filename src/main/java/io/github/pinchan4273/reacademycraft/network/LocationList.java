package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.LocationTeleport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** 術者が登録した地点。原作のfutureがリスト全体で答えたのと同じく、要求のたびに全体を送る。 */
public record LocationList(List<LocationTeleport.Location> locations) {
    public LocationList {
        if (locations.size() > LocationTeleport.MAX_LOCATIONS) throw new IllegalArgumentException("Too many locations");
        locations = List.copyOf(locations);
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(locations.size());
        for (var location : locations) location.encode(buffer);
    }
    public static LocationList decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > LocationTeleport.MAX_LOCATIONS) throw new IllegalArgumentException("Too many locations");
        var locations = new ArrayList<LocationTeleport.Location>(size);
        for (int i = 0; i < size; i++) locations.add(LocationTeleport.Location.decode(buffer));
        return new LocationList(locations);
    }
    static void send(ServerPlayer player) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new LocationList(LocationTeleport.locations(player)));
    }
}
