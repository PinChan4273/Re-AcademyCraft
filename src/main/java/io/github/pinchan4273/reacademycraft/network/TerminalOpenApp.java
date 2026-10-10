package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.terminal.TerminalApp;
import io.github.pinchan4273.reacademycraft.terminal.TerminalApps;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 端末で開いた、背後にメニューが必要なアプリ。原作は技能の木アプリを、既に持っている能力データを読んで完全にクライアントで
 * 動かす。移植版は開発機と同じ画面を表示し、ここでは画面はメニューとして開くので、クライアントがメニューを要求する。
 *
 * メニューは背後に開発機が無い状態で開く。それが読み取り専用である理由: セッションが無いので、原作のアプリと同じく
 * 何も習得・強化できない。
 */
public record TerminalOpenApp(String app) {
    public TerminalOpenApp {
        Objects.requireNonNull(app);
        if (app.isEmpty() || app.length() > 32) throw new IllegalArgumentException("Invalid app name");
    }
    public void encode(FriendlyByteBuf b) { b.writeUtf(app, 32); }
    public static TerminalOpenApp decode(FriendlyByteBuf b) { return new TerminalOpenApp(b.readUtf(32)); }

    public void handle(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        // プレイヤーがどのみち開けるメニューだけ: 端末がその人のもので、アプリがそこにあること。
        TerminalApp found = TerminalApps.byName(app);
        if (found == null || found.kind() != TerminalApp.Kind.SKILL_TREE || !TerminalState.has(player, found)) return;
        if (player.containerMenu != player.inventoryMenu) return;
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, owner) -> io.github.pinchan4273.reacademycraft.develop.DeveloperMenu.viewer(id, inventory),
                net.minecraft.network.chat.Component.translatable(found.titleKey())), extra -> extra.writeBoolean(true));
    }
}
