package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.tutorial.TutorialState;
import io.github.pinchan4273.reacademycraft.tutorial.Tutorials;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作TutorialDataの同期と"activate"メッセージ: プレイヤーが解放した記事、ミサカ番号、たった今解放された記事
 * （原作のNotifyUIと同じく、クライアントが知らせる）。
 */
public record TutorialSnapshot(List<String> activated, int misaka, List<String> fresh) {
    public static final int MAX_NAME = 32;
    public TutorialSnapshot {
        Objects.requireNonNull(activated); Objects.requireNonNull(fresh);
        int limit = Tutorials.all().size();
        if (activated.size() > limit || fresh.size() > limit) throw new IllegalArgumentException("Too many tutorials");
        for (var id : activated) if (id == null || id.isEmpty() || id.length() > MAX_NAME) throw new IllegalArgumentException("Invalid tutorial");
        for (var id : fresh) if (id == null || id.isEmpty() || id.length() > MAX_NAME) throw new IllegalArgumentException("Invalid tutorial");
        activated = List.copyOf(activated); fresh = List.copyOf(fresh);
    }
    public void encode(FriendlyByteBuf b) {
        write(b, activated); b.writeVarInt(misaka); write(b, fresh);
    }
    private static void write(FriendlyByteBuf b, List<String> ids) {
        b.writeVarInt(ids.size());
        for (var id : ids) b.writeUtf(id, MAX_NAME);
    }
    private static List<String> read(FriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > Tutorials.all().size()) throw new io.netty.handler.codec.DecoderException("Too many tutorials");
        var ids = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) ids.add(b.readUtf(MAX_NAME));
        return ids;
    }
    public static TutorialSnapshot decode(FriendlyByteBuf b) {
        var activated = read(b); int misaka = b.readVarInt();
        return new TutorialSnapshot(activated, misaka, read(b));
    }
    public static void send(ServerPlayer player, List<String> fresh) {
        if (player == null || player.connection == null) return;
        AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TutorialSnapshot(List.copyOf(TutorialState.activated(player)), TutorialState.misakaId(player), fresh));
    }
    public void handle(net.minecraft.world.entity.player.Player player) { TutorialState.accept(player, activated, misaka); }
}
