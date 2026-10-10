package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 原作のTerminalDataは所有者へ同期される。これも同じこと（端末を持っているか、そこにどのアプリがあるか）を伝える。
 * ログイン時、戻ってきたとき、どちらかが変わったときに送る。
 */
@Mod.EventBusSubscriber(modid = "academy")
public record TerminalSnapshot(boolean installed, List<String> apps, List<Boolean> settings, boolean maySet) {
    public static final int MAX_APPS = 64, MAX_NAME = 32;
    public TerminalSnapshot(boolean installed, List<String> apps) {
        this(installed, apps, io.github.pinchan4273.reacademycraft.terminal.TerminalSettings.ALL.stream()
                .map(io.github.pinchan4273.reacademycraft.terminal.TerminalSettings::get).toList(), false);
    }
    public TerminalSnapshot {
        Objects.requireNonNull(apps);
        if (apps.size() > MAX_APPS) throw new IllegalArgumentException("Too many apps");
        for (var name : apps) if (name == null || name.isEmpty() || name.length() > MAX_NAME)
            throw new IllegalArgumentException("Invalid app name");
        apps = List.copyOf(apps);
        Objects.requireNonNull(settings);
        if (settings.size() != io.github.pinchan4273.reacademycraft.terminal.TerminalSettings.ALL.size())
            throw new IllegalArgumentException("Wrong number of settings");
        settings = List.copyOf(settings);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(installed); b.writeVarInt(apps.size());
        for (var name : apps) b.writeUtf(name, MAX_NAME);
        for (boolean on : settings) b.writeBoolean(on);
        b.writeBoolean(maySet);
    }
    public static TerminalSnapshot decode(FriendlyByteBuf b) {
        boolean installed = b.readBoolean();
        int count = b.readVarInt();
        if (count < 0 || count > MAX_APPS) throw new io.netty.handler.codec.DecoderException("Too many apps");
        var apps = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) apps.add(b.readUtf(MAX_NAME));
        var settings = new ArrayList<Boolean>();
        for (int i = 0; i < io.github.pinchan4273.reacademycraft.terminal.TerminalSettings.ALL.size(); i++) settings.add(b.readBoolean());
        return new TerminalSnapshot(installed, apps, settings, b.readBoolean());
    }
    public static void send(ServerPlayer player) {
        if (player == null || player.connection == null) return;
        AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TerminalSnapshot(TerminalState.installed(player), List.copyOf(TerminalState.apps(player)),
                        io.github.pinchan4273.reacademycraft.terminal.TerminalSettings.ALL.stream()
                                .map(io.github.pinchan4273.reacademycraft.terminal.TerminalSettings::get).toList(),
                        io.github.pinchan4273.reacademycraft.terminal.TerminalSettings.mayChange(player.getServer(), player)));
    }
    public void handle(net.minecraft.world.entity.player.Player player) {
        TerminalState.accept(player, installed, apps);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    /** 原作のデータ部分と同じく、端末は死亡とエンドからの帰還を越えて残るので、ここで引き継ぐ。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        // 原作LambdaLib2のEntityData.onPlayerCloneは、死亡かどうかを問わずすべてのデータ部分を写すので、エンドからの帰還でも残る
        // （Forge自身はPlayerPersistedしか引き継がない）。
        var old = event.getOriginal().getPersistentData();
        var fresh = event.getEntity().getPersistentData();
        for (var key : List.of("academy_terminal", "academy_terminal_apps"))
            if (old.contains(key)) fresh.put(key, old.get(key).copy());
    }
}
