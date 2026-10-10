package io.github.pinchan4273.reacademycraft.terminal;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 原作AppSettingsのスイッチ。原作は4つ表示する: うち2つ（能力がプレイヤーを傷つけてよいか、ブロックを壊してよいか）は移植版が
 * 既に持つ設定で、原作がシングルプレイ専用とする2つでもある。他の2つ（コインの表裏と、Penetrate Teleportの距離のマウスホイール）は
 * 移植版が採っていない挙動に属するので、ここには無い。
 *
 * 原作はこれをプレイヤー自身の設定ファイルへ書く。ここではAcademyConfigのもので、インスタンス全体でacademy-common.tomlに保存する
 * ので、あるシングルプレイのワールドで切り替えたものはインスタンスの他のワールドでも効く。変更できるのはシングルプレイのワールドの
 * ホストだけで、それはちょうど原作が表示していた場所。
 */
public final class TerminalSettings {
    public static final String DESTROY_BLOCKS = "destroyBlocks", ATTACK_PLAYER = "attackPlayer";
    public static final List<String> ALL = List.of(DESTROY_BLOCKS, ATTACK_PLAYER);
    private TerminalSettings() { }

    public static boolean get(String id) {
        var value = value(id);
        return value != null && value.get();
    }
    /** シングルプレイのワールドのホストだけ。原作もそこでだけ表示した。 */
    public static boolean mayChange(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && server.isSingleplayer()
                && server.isSingleplayerOwner(player.getGameProfile());
    }
    /** 名前がこれらの設定のどれでもなければfalseを返す。 */
    public static boolean set(String id, boolean on) {
        var value = value(id);
        if (value == null) return false;
        value.set(on);
        value.save();
        return true;
    }
    private static ForgeConfigSpec.BooleanValue value(String id) {
        return switch (id) {
            case DESTROY_BLOCKS -> AcademyConfig.DESTROY_BLOCKS;
            case ATTACK_PLAYER -> AcademyConfig.ATTACK_PLAYERS;
            default -> null;
        };
    }
}
