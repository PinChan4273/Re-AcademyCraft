package io.github.pinchan4273.reacademycraft;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作CommandACACH: /acach ACHIEVEMENT_NAME [PLAYER_NAME] で、このMODの進捗を1つ、実行者
 * または指定したプレイヤーへ与える。原作のコマンドはCommandBaseの既定の権限レベルだったが、
 * ここではバニラの/advancementと同じ2を要求する。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class AdvancementCommand {
    private AdvancementCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var name = Commands.argument("advancement", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getServer().getAdvancements()
                        .getAllAdvancements().stream().map(a -> a.getId()).filter(id -> id.getNamespace().equals("academy"))
                        .map(id -> id.getPath()), builder))
                .executes(context -> grant(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> grant(context, EntityArgument.getPlayer(context, "player"))));
        event.getDispatcher().register(Commands.literal("acach").requires(source -> source.hasPermission(2)).then(name));
    }
    private static int grant(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        String name = StringArgumentType.getString(context, "advancement");
        var advancement = player.getServer().getAdvancements()
                .getAdvancement(net.minecraft.resources.ResourceLocation.tryBuild("academy", name));
        if (advancement == null) {
            context.getSource().sendFailure(Component.translatable("academy.command.unknown_advancement"));
            return 0;
        }
        // 原作trigger()は、知っている進捗であれば、既に持っているかどうかに関係なく成功する。
        AcademyAdvancements.grant(player, name);
        context.getSource().sendSuccess(() -> Component.translatable("academy.command.done"), true);
        return 1;
    }
}
