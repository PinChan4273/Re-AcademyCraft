package io.github.pinchan4273.reacademycraft.terminal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 原作TerminalData: このプレイヤーがデータ端末を持っているか、そのアプリのうちどれを持っているか。
 *
 * 原作はこれをプレイヤーの独自の同期するデータ部分として持つ。移植版はプレイヤーの永続データに置き、同じく死亡とワールドの
 * 再読込を越えて残り、独自のパケットでクライアントへ伝える。原作がプリインストールとするアプリは、端末を持った瞬間からある。
 */
public final class TerminalState {
    private static final String INSTALLED = "academy_terminal", APPS = "academy_terminal_apps";
    private TerminalState() { }

    public static boolean installed(Player player) {
        return player != null && player.getPersistentData().getBoolean(INSTALLED);
    }
    /** 原作のinstall()と同じくサーバー専用。既にあればfalse。 */
    public static boolean install(Player player) {
        if (player == null || installed(player)) return false;
        player.getPersistentData().putBoolean(INSTALLED, true);
        if (!player.level().isClientSide) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.AcademyEvent.TerminalInstalled(player));
        return true;
    }
    public static Set<String> apps(Player player) {
        var out = new LinkedHashSet<String>();
        if (player == null) return out;
        for (Tag tag : player.getPersistentData().getList(APPS, Tag.TAG_STRING)) out.add(tag.getAsString());
        return out;
    }
    public static boolean has(Player player, TerminalApp app) {
        return installed(player) && (app.preInstalled() || apps(player).contains(app.name()));
    }
    /** サーバー専用。アプリが既にあればfalse。 */
    public static boolean installApp(Player player, TerminalApp app) {
        if (player == null || !installed(player) || has(player, app)) return false;
        var list = new ListTag();
        for (var name : apps(player)) list.add(StringTag.valueOf(name));
        list.add(StringTag.valueOf(app.name()));
        player.getPersistentData().put(APPS, list);
        if (!player.level().isClientSide) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.AcademyEvent.AppInstalled(player, app.name()));
        return true;
    }
    /** このプレイヤーが開けるアプリ。登録された順。 */
    public static List<TerminalApp> available(Player player) {
        return TerminalApps.all().stream().filter(app -> has(player, app)).toList();
    }
    /** クライアント専用: サーバーが最後に伝えた内容。 */
    public static void accept(Player player, boolean installed, List<String> apps) {
        if (player == null) return;
        player.getPersistentData().putBoolean(INSTALLED, installed);
        var list = new ListTag();
        for (var name : apps) list.add(StringTag.valueOf(name));
        player.getPersistentData().put(APPS, list);
    }
}
