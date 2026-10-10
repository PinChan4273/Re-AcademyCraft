package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作CommandAIMBaseを2つのコマンドとして: /aimは入力した本人に作用し、そのcheatsがオンになるまで何もしない
 * （原作と同じくクリエイティブも含む）。/aimpはプレイヤーを指定する。どちらもオペレーター用: 原作はCommandBaseのレベル4を
 * 下げていない。ここではそれを/giveや/gamemodeと同じゲームのレベル2とする（違いが出るのはレベル2・3のオペレーターだけ）。
 *
 * 原作は引数の文字列を自分で解析する。ここではゲーム自身のコマンドツリーを使うので、サブコマンドは同じ語で同じことをし、
 * 引数の確認と補完はゲームが行う。これが原作独自の"help"、"?"、"catlist"、「番号による技能指定」を置き換える。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class AbilityCommands {
    /** 原作はこれをプレイヤー自身のタグに保存し、ここでも同じ。 */
    public static final String CHEATS = "academy_aim_cheats";
    private AbilityCommands() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        // 原作/aim: オペレーターのコマンドだが、cheatsがオンになるまで何もしない。
        var aim = Commands.literal("aim").requires(source -> source.hasPermission(2))
                .then(Commands.literal("cheats_on").executes(context -> {
                    context.getSource().getPlayerOrException().getPersistentData().putBoolean(CHEATS, true);
                    reply(context, Component.translatable("academy.command.cheats_on"));
                    reply(context, Component.translatable("academy.command.cheats_warning"));
                    return 1;
                }))
                .then(Commands.literal("cheats_off").executes(context -> {
                    context.getSource().getPlayerOrException().getPersistentData().putBoolean(CHEATS, false);
                    reply(context, Component.translatable("academy.command.cheats_off"));
                    return 1;
                }));
        subcommands(aim, AbilityCommands::self);
        event.getDispatcher().register(aim);

        // 原作/aimp: 作用するプレイヤーを指定する、オペレーターのコマンド。
        var target = Commands.argument("target", EntityArgument.player());
        subcommands(target, AbilityCommands::named);
        event.getDispatcher().register(Commands.literal("aimp").requires(source -> source.hasPermission(2)).then(target));
    }

    /** 原作と同じく、cheatsがオンかクリエイティブの、入力した本人。 */
    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        // 原作: コマンドを許可したワールド（cheatsオンで作成、またはcheatsありでLANに公開）では、/aimが自身のcheatsをオンにするので、
        // そこではcheats_on無しで動く。
        if (!player.getPersistentData().getBoolean(CHEATS) && player.server.getWorldData().getAllowCommands())
            player.getPersistentData().putBoolean(CHEATS, true);
        if (!player.getPersistentData().getBoolean(CHEATS) && !player.isCreative()) {
            reply(context, Component.translatable("academy.command.not_active"));
            return null;
        }
        return player;
    }
    private static ServerPlayer named(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayer(context, "target");
    }

    /** 原作が挙げるサブコマンド。組み立て中の2つのコマンドのどちらかに付ける。 */
    private static void subcommands(ArgumentBuilder<CommandSourceStack, ?> parent, Who who) {
        parent.then(Commands.literal("cat")
                .executes(c -> run(c, who, (player, data) -> {
                    reply(c, data.hasAbility()
                            ? Component.translatable("academy.command.category",
                                    Component.translatable(AbilityCategory.find(data.getAbility()).translation()))
                            : Component.translatable("academy.command.no_category"));
                    return 1;
                }))
                .then(Commands.argument("category", ResourceLocationArgument.id())
                        .suggests((c, builder) -> {
                            for (var category : AbilityCategory.REGISTERED) builder.suggest(category.id().toString());
                            return builder.buildFuture();
                        })
                        .executes(c -> run(c, who, (player, data) -> {
                            var wanted = AbilityCategory.find(ResourceLocationArgument.getId(c, "category"));
                            if (wanted == null) { reply(c, Component.translatable("academy.command.unknown_category")); return 0; }
                            data.setAbility(wanted.id());
                            return done(c, player);
                        }))));
        parent.then(Commands.literal("reset").executes(c -> run(c, who, (player, data) -> {
            data.setAbility(null);
            return done(c, player);
        })));
        parent.then(Commands.literal("learn").then(skillArgument()
                .executes(c -> run(c, who, (player, data) -> {
                    // 原作nonecathint: カテゴリが無ければ指定する技能も無い。
                    if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
                    var skill = skill(c, data);
                    if (skill == null) { reply(c, Component.translatable("academy.command.unknown_skill")); return 0; }
                    data.learn(skill);
                    return done(c, player);
                }))));
        parent.then(Commands.literal("unlearn").then(skillArgument()
                .executes(c -> run(c, who, (player, data) -> {
                    // 原作nonecathint: カテゴリが無ければ指定する技能も無い。
                    if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
                    var skill = skill(c, data);
                    if (skill == null) { reply(c, Component.translatable("academy.command.unknown_skill")); return 0; }
                    data.unlearn(skill);
                    return done(c, player);
                }))));
        parent.then(Commands.literal("learn_all").executes(c -> run(c, who, (player, data) -> {
            if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
            learnAll(data);
            return done(c, player);
        })));
        parent.then(Commands.literal("learned").executes(c -> run(c, who, (player, data) -> {
            var names = new StringBuilder();
            for (var skill : data.learnedSkills().keySet())
                names.append(names.isEmpty() ? "" : ", ").append(skill.getPath());
            reply(c, Component.translatable("academy.command.learned", names.toString()));
            return 1;
        })));
        parent.then(Commands.literal("skills").executes(c -> run(c, who, (player, data) -> {
            if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
            for (var skill : SkillCatalog.IMPLEMENTED)
                if (skill.offeredTo(data.getAbility()))
                    reply(c, Component.literal(skill.id().getPath() + " (" + skill.level() + "): ")
                            .append(Component.translatable(skill.translation())));
            return 1;
        })));
        parent.then(Commands.literal("level")
                .executes(c -> run(c, who, (player, data) -> {
                    reply(c, Component.literal(Integer.toString(data.getLevel())));
                    return 1;
                }))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                        .executes(c -> run(c, who, (player, data) -> {
                            data.setLevel(IntegerArgumentType.getInteger(c, "level"));
                            return done(c, player);
                        }))));
        parent.then(Commands.literal("exp").then(skillArgument()
                .executes(c -> run(c, who, (player, data) -> {
                    // 原作nonecathint: カテゴリが無ければ指定する技能も無い。
                    if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
                    var skill = skill(c, data);
                    if (skill == null) { reply(c, Component.translatable("academy.command.unknown_skill")); return 0; }
                    reply(c, Component.translatable("academy.command.exp", skill.getPath(),
                            String.format(java.util.Locale.ROOT, "%.1f", data.getProficiency(skill) * 100)));
                    return 1;
                }))
                .then(Commands.argument("proficiency", FloatArgumentType.floatArg(0, 1))
                        .executes(c -> run(c, who, (player, data) -> {
                            if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
                            var skill = skill(c, data);
                            if (skill == null) { reply(c, Component.translatable("academy.command.unknown_skill")); return 0; }
                            data.setProficiency(skill, FloatArgumentType.getFloat(c, "proficiency"));
                            return done(c, player);
                        })))));
        parent.then(Commands.literal("fullcp").executes(c -> run(c, who, (player, data) -> {
            if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
            data.setCp(data.getMaxCp()); data.setOverload(0);
            return done(c, player);
        })));
        parent.then(Commands.literal("cd_clear").executes(c -> run(c, who, (player, data) -> {
            clearCooldowns(data);
            return done(c, player);
        })));
        parent.then(Commands.literal("maxout").executes(c -> run(c, who, (player, data) -> {
            if (!data.hasAbility()) { reply(c, Component.translatable("academy.command.no_category")); return 0; }
            maxOut(data);
            return done(c, player);
        })));
    }

    /** 原作learnAllSkills: プレイヤーの持つカテゴリについて。 */
    public static void learnAll(PlayerAbilityData data) {
        for (var skill : SkillCatalog.IMPLEMENTED)
            if (skill.offeredTo(data.getAbility())) data.learn(skill.id());
    }
    /** 原作CooldownData.clear()。クールダウンを消せるのは習得済みの技能だけで、それ以外への設定はそのまま拒否する。 */
    public static void clearCooldowns(PlayerAbilityData data) {
        for (var skill : SkillCatalog.IMPLEMENTED)
            if (data.hasLearned(skill.id())) data.setCooldown(skill.id(), 0);
    }
    /**
     * 原作maxOutLevelProgress: このレベルで習得したものはすべて最大になり、レベルの進捗も最後まで進むので、次のレベルを取れる。
     * 進捗は1で頭打ちの比なので、レベルの閾値を超えた経験値は満杯と読める。
     */
    public static void maxOut(PlayerAbilityData data) {
        for (var skill : SkillCatalog.IMPLEMENTED)
            if (skill.offeredTo(data.getAbility()) && data.hasLearned(skill.id())) data.setProficiency(skill.id(), 1);
        data.setLevelExperience(1_000_000f);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> skillArgument() {
        return Commands.argument("skill", ResourceLocationArgument.id())
                .suggests((c, builder) -> {
                    for (var skill : SkillCatalog.IMPLEMENTED) builder.suggest(skill.id().toString());
                    return builder.buildFuture();
                });
    }

    /** 原作は技能をカテゴリ内の名前か番号で受け取る。ここではidで受け取り、それはカテゴリが提供する技能でなければならない。 */
    private static ResourceLocation skill(CommandContext<CommandSourceStack> context, PlayerAbilityData data) {
        var found = SkillCatalog.find(ResourceLocationArgument.getId(context, "skill"));
        return found != null && found.offeredTo(data.getAbility()) ? found.id() : null;
    }

    interface Who { ServerPlayer of(CommandContext<CommandSourceStack> context) throws CommandSyntaxException; }
    interface Action { int run(ServerPlayer player, PlayerAbilityData data); }
    private static int run(CommandContext<CommandSourceStack> context, Who who, Action action) throws CommandSyntaxException {
        var player = who.of(context);
        if (player == null) return 0;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || data.isReadOnly()) { reply(context, Component.translatable("academy.command.no_data")); return 0; }
        return action.run(player, data);
    }
    private static int done(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        AbilitySyncEvents.sync(player, true);
        reply(context, Component.translatable("academy.command.done"));
        return 1;
    }
    private static void reply(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }
}
