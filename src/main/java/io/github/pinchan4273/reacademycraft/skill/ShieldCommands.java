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
 * 通常のプレイヤー向けの設定コマンド（工程2）: {@code /academy shield mode [auto|semi]}。{@code manual}は{@code semi}の互換の別名
 * （同じSEMI-AUTOを選ぶ。2026-10-23の開発司令室9でモード名を統一）。
 * <ul>
 * <li>オペレーター権限も{@code /aim cheats_on}も要らない（権限のレベル0）。</li>
 * <li>実行した本人の設定だけを読む・変える。他のプレイヤーは指定できない。コンソールなどプレイヤーでない実行者は使えない。</li>
 * <li>変える処理は{@link LightShieldSettings}だけが行う（将来の能力調整GUIも同じ入口を使う）。</li>
 * </ul>
 * 結果はチャットに出す: 今のモード、切り替えた結果、既にそのモードだったこと、盾がONならすぐ効くこと、未習得・カテゴリ違いなら
 * 「習得した後にこのモードで動く」こと、読み取り専用の保存データなら変えられないこと。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ShieldCommands {
    private ShieldCommands() { }

    @SubscribeEvent public static void registerShieldCommands(RegisterCommandsEvent event) {
        var mode = Commands.literal("mode").executes(ShieldCommands::show);
        for (var choice : LightShieldMode.values())
            mode.then(Commands.literal(choice.command()).executes(context -> change(context, choice)));
        // 互換の別名: 以前の正式名manualは、SEMI-AUTO（MANUAL）を選ぶ。
        mode.then(Commands.literal(LightShieldMode.MANUAL.id()).executes(context -> change(context, LightShieldMode.MANUAL)));
        event.getDispatcher().register(Commands.literal("academy").requires(source -> source.hasPermission(0))
                .then(Commands.literal("shield").then(mode)));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var result = LightShieldSettings.get(player);
        switch (result.outcome()) {
            case NO_DATA -> { context.getSource().sendFailure(Component.translatable("academy.light_shield.mode.no_data")); return 0; }
            case READ_ONLY -> { context.getSource().sendFailure(Component.translatable("academy.light_shield.mode.read_only")); return 0; }
            default -> { }
        }
        reply(context, Component.translatable(result.chosen() ? "academy.light_shield.mode.current" : "academy.light_shield.mode.current_default",
                name(result.mode())));
        if (!result.learned()) reply(context, Component.translatable("academy.light_shield.mode.not_learned").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> context, LightShieldMode mode) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var result = LightShieldSettings.set(player, mode);
        switch (result.outcome()) {
            case NO_DATA -> { context.getSource().sendFailure(Component.translatable("academy.light_shield.mode.no_data")); return 0; }
            case READ_ONLY -> { context.getSource().sendFailure(Component.translatable("academy.light_shield.mode.read_only")); return 0; }
            case UNCHANGED -> reply(context, Component.translatable("academy.light_shield.mode.unchanged", name(mode)));
            case CHANGED -> reply(context, Component.translatable(result.active() ? "academy.light_shield.mode.changed_active"
                    : "academy.light_shield.mode.changed", name(mode)));
        }
        if (!result.learned()) reply(context, Component.translatable("academy.light_shield.mode.not_learned").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static Component name(LightShieldMode mode) { return Component.translatable(mode.translation()).withStyle(ChatFormatting.AQUA); }
    private static void reply(CommandContext<CommandSourceStack> context, Component message) { context.getSource().sendSuccess(() -> message, false); }
}
