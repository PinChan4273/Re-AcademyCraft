package io.github.pinchan4273.reacademycraft.skill;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 通常のプレイヤー向けのElectron Missileの設定コマンド（F9-02）。{@link ShieldCommands}と同じ方針。
 * <ul>
 * <li>{@code /academy missile mode}（照会）、{@code /academy missile mode auto|semi}: 照準のモード。</li>
 * <li>{@code /academy missile target}（4分類の照会）、{@code /academy missile target hostile|neutral|friendly|player on|off}: 攻撃対象のフィルター。</li>
 * <li>オペレーター権限も{@code /aim cheats_on}も要らない（権限のレベル0）。実行した本人の設定だけを読む・変える。コンソールなどプレイヤーでない実行者は使えない。</li>
 * <li>変える処理は{@link ElectronMissileSettings}だけが行う。</li>
 * </ul>
 * {@code /academy}の根は{@link ShieldCommands}も登録する。Brigadierは同じ名前の根へ子を合わせるので、{@code shield}と{@code missile}は
 * 同じ{@code /academy}の下に並ぶ。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class MissileCommands {
    private static final String KEY = "academy.electron_missile.";
    private MissileCommands() { }

    @SubscribeEvent public static void registerMissileCommands(RegisterCommandsEvent event) {
        var mode = Commands.literal("mode").executes(MissileCommands::showMode);
        for (var choice : ElectronMissileAimMode.values())
            mode.then(Commands.literal(choice.id()).executes(context -> changeMode(context, choice)));
        var target = Commands.literal("target").executes(MissileCommands::showTargets);
        for (var kind : ElectronMissileTargetKind.values())
            target.then(Commands.literal(kind.id()).executes(context -> showTargets(context))
                    .then(Commands.literal("on").executes(context -> changeTarget(context, kind, true)))
                    .then(Commands.literal("off").executes(context -> changeTarget(context, kind, false))));
        event.getDispatcher().register(Commands.literal("academy").requires(source -> source.hasPermission(0))
                .then(Commands.literal("missile").then(mode).then(target)));
    }

    /** 読めない・変えられないときの断り。断ったら0を返し、そうでなければ-1。 */
    private static int refuse(CommandContext<CommandSourceStack> context, ElectronMissileSettings.Result result) {
        switch (result.outcome()) {
            case NO_DATA -> { context.getSource().sendFailure(Component.translatable(KEY + "settings.no_data")); return 0; }
            case READ_ONLY -> { context.getSource().sendFailure(Component.translatable(KEY + "settings.read_only")); return 0; }
            default -> { return -1; }
        }
    }
    private static void notes(CommandContext<CommandSourceStack> context, ElectronMissileSettings.Result result, boolean changed) {
        if (changed && result.active()) reply(context, Component.translatable(KEY + "settings.active").withStyle(ChatFormatting.GRAY));
        if (!result.learned()) reply(context, Component.translatable(KEY + "settings.not_learned").withStyle(ChatFormatting.GRAY));
    }

    private static int showMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var result = ElectronMissileSettings.get(context.getSource().getPlayerOrException());
        int refused = refuse(context, result);
        if (refused >= 0) return refused;
        reply(context, Component.translatable(result.chosen() ? KEY + "mode.current" : KEY + "mode.current_default", name(result.mode())));
        notes(context, result, false);
        return 1;
    }
    private static int changeMode(CommandContext<CommandSourceStack> context, ElectronMissileAimMode mode) throws CommandSyntaxException {
        var result = ElectronMissileSettings.setMode(context.getSource().getPlayerOrException(), mode);
        int refused = refuse(context, result);
        if (refused >= 0) return refused;
        boolean changed = result.outcome() == ElectronMissileSettings.Outcome.CHANGED;
        reply(context, Component.translatable(changed ? KEY + "mode.changed" : KEY + "mode.unchanged", name(mode)));
        notes(context, result, changed);
        return 1;
    }
    private static int showTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var result = ElectronMissileSettings.get(context.getSource().getPlayerOrException());
        int refused = refuse(context, result);
        if (refused >= 0) return refused;
        reply(context, Component.translatable(KEY + "target.header"));
        for (var kind : ElectronMissileTargetKind.values())
            reply(context, Component.translatable(KEY + "target.line", name(kind), state(result.targets().get(kind))));
        if (result.targets().values().stream().noneMatch(Boolean::booleanValue))
            reply(context, Component.translatable(KEY + "target.none").withStyle(ChatFormatting.YELLOW));
        notes(context, result, false);
        return 1;
    }
    private static int changeTarget(CommandContext<CommandSourceStack> context, ElectronMissileTargetKind kind, boolean on) throws CommandSyntaxException {
        var result = ElectronMissileSettings.setTarget(context.getSource().getPlayerOrException(), kind, on);
        int refused = refuse(context, result);
        if (refused >= 0) return refused;
        boolean changed = result.outcome() == ElectronMissileSettings.Outcome.CHANGED;
        reply(context, Component.translatable(changed ? KEY + "target.changed" : KEY + "target.unchanged", name(kind), state(on)));
        if (kind == ElectronMissileTargetKind.PLAYER && on) reply(context, Component.translatable(KEY + "target.player_rules").withStyle(ChatFormatting.GRAY));
        if (result.targets().values().stream().noneMatch(Boolean::booleanValue))
            reply(context, Component.translatable(KEY + "target.none").withStyle(ChatFormatting.YELLOW));
        notes(context, result, changed);
        return 1;
    }

    private static Component name(ElectronMissileAimMode mode) { return Component.translatable(mode.translation()).withStyle(ChatFormatting.AQUA); }
    private static Component name(ElectronMissileTargetKind kind) { return Component.translatable(kind.translation()).withStyle(ChatFormatting.AQUA); }
    private static Component state(boolean on) {
        return Component.translatable(on ? KEY + "target.on" : KEY + "target.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
    private static void reply(CommandContext<CommandSourceStack> context, Component message) { context.getSource().sendSuccess(() -> message, false); }
}
