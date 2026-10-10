package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 明示的なOP2の、本人だけに作用する手動テストの準備。ログイン時に自動で与えることはない。
 * テストは学習の前提条件を飛ばしてよいが、通常の機械での学習は変わらない。
 * セッションを止めたりプレイヤーのデータを変えたりする前に、要求全体を検証する。
 */
public final class AbilityTestCommands {
    private AbilityTestCommands() { }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Brigadierはこれらの子を既存のacademy_testコマンドに統合する。
        dispatcher.register(Commands.literal("academy_test").requires(s -> s.hasPermission(2))
                .executes(c -> help(c.getSource()))
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource(), null))
                        .then(skillArgument().executes(c -> status(c.getSource(), ResourceLocationArgument.getId(c, "skill")))))
                .then(Commands.literal("level").then(Commands.argument("value", IntegerArgumentType.integer(1, 5))
                        .executes(c -> level(c.getSource(), IntegerArgumentType.getInteger(c, "value")))))
                .then(Commands.literal("experience")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                .executes(c -> experience(c.getSource(), FloatArgumentType.getFloat(c, "value"), false)))
                        .then(Commands.literal("add").then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                .executes(c -> experience(c.getSource(), FloatArgumentType.getFloat(c, "value"), true)))))
                .then(Commands.literal("cp").then(Commands.argument("value", FloatArgumentType.floatArg(0))
                        .executes(c -> cp(c.getSource(), FloatArgumentType.getFloat(c, "value")))))
                .then(Commands.literal("learn").then(skillArgument()
                        .executes(c -> learn(c.getSource(), ResourceLocationArgument.getId(c, "skill"), false))))
                .then(Commands.literal("unlearn").then(skillArgument()
                        .executes(c -> learn(c.getSource(), ResourceLocationArgument.getId(c, "skill"), true))))
                .then(Commands.literal("learn_current").executes(c -> learnCurrent(c.getSource())))
                .then(Commands.literal("learn_all").executes(c -> learnThrough(c.getSource(), 5)))
                .then(Commands.literal("learn_to_level").then(Commands.argument("value", IntegerArgumentType.integer(1, 5))
                        .executes(c -> learnThrough(c.getSource(), IntegerArgumentType.getInteger(c, "value")))))
                .then(Commands.literal("category").then(Commands.argument("category", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(AbilityCategory.REGISTERED.stream().map(AbilityCategory::id), b))
                        .executes(c -> category(c.getSource(), ResourceLocationArgument.getId(c, "category"))))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> skillArgument() {
        return Commands.argument("skill", ResourceLocationArgument.id()).suggests((c, b) ->
                SharedSuggestionProvider.suggestResource(SkillCatalog.IMPLEMENTED.stream().map(SkillCatalog.Definition::id), b));
    }
    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("academy.test.help"), false); return 1;
    }
    private static int status(CommandSourceStack source, ResourceLocation selected) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (selected != null && SkillCatalog.find(selected) == null) return fail(source, "unknown_skill", selected);
        if (data == null) return fail(source, "unavailable");
        // 設計上読み取り専用。writable/applyを使わず、資源の補充、押し続け技能の取り消し、有効状態の変更、変更パケットの送信、
        // 生のNBTの公開はしない。
        source.sendSuccess(() -> Component.translatable("academy.test.status_header",
                data.getAbility() == null ? "-" : data.getAbility().toString(), data.getLevel(),
                Component.translatable(data.isActive() ? "academy.hud.active" : "academy.hud.inactive"),
                data.isReadOnly(), data.getCurrentPreset() + 1), false);
        source.sendSuccess(() -> Component.translatable("academy.test.status_resources",
                decimal(data.getCp()), decimal(data.getMaxCp()), decimal(data.getOverload()),
                decimal(data.getMaxOverload()), Float.toString(data.getLevelExperience())), false);
        for (var definition : SkillCatalog.IMPLEMENTED) {
            var id = definition.id();
            if (selected != null && !selected.equals(id)) continue;
            source.sendSuccess(() -> Component.translatable("academy.test.status_skill", id.toString(),
                    Component.translatable(data.hasLearned(id) ? "academy.preset.learned" : "academy.preset.unlearned"),
                    decimal(data.getProficiency(id) * 100), data.getCooldown(id)), false);
        }
        return 1;
    }
    private static String decimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
    private static PlayerAbilityData writable(CommandSourceStack source, boolean allowEmpty) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        var d = p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (!source.hasPermission(2) || !p.isAlive() || p.isSpectator() || p.containerMenu != p.inventoryMenu
                || d == null || d.isReadOnly()
                || !(AbilityCategory.find(d.getAbility()) != null || allowEmpty && !d.hasAbility())) {
            fail(source, "unavailable"); return null;
        }
        return d;
    }
    private static int fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable("academy.test." + key, args)); return 0;
    }
    private static int apply(CommandSourceStack source, PlayerAbilityData d, String operation, Runnable change)
            throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        d.setActive(false);
        CurrentCharging.stop(p); MagneticMovement.stop(p); MagneticManipulation.stop(p);
        BodyIntensify.stop(p); ThunderClap.stop(p); Railgun.stop(p);
        change.run();
        AbilitySyncEvents.sync(p, true);
        source.sendSuccess(() -> Component.translatable("academy.test.changed", operation, d.getLevel(),
                d.learnedSkills().size()), false);
        return 1;
    }
    private static int level(CommandSourceStack source, int value) throws CommandSyntaxException {
        var d = writable(source, false); if (d == null) return 0;
        return apply(source, d, "level", () -> d.setLevel(value));
    }
    private static int experience(CommandSourceStack source, float value, boolean add) throws CommandSyntaxException {
        var d = writable(source, false); if (d == null) return 0;
        double next = add ? (double)d.getLevelExperience() + value : value;
        if (!Float.isFinite(value) || next < 0 || next > Float.MAX_VALUE) return fail(source, "value_range");
        return apply(source, d, "experience", () -> d.setLevelExperience((float)next));
    }
    private static int cp(CommandSourceStack source, float value) throws CommandSyntaxException {
        var d = writable(source, false); if (d == null) return 0;
        if (!Float.isFinite(value) || value < 0 || value > d.getMaxCp()) return fail(source, "value_range");
        return apply(source, d, "cp", () -> d.setCp(value));
    }
    private static int learn(CommandSourceStack source, ResourceLocation id, boolean remove) throws CommandSyntaxException {
        var d = writable(source, false); if (d == null) return 0;
        var skill = SkillCatalog.find(id);
        if (skill == null) return fail(source, "unknown_skill", id);
        if (remove) {
            if (!d.hasLearned(id)) return fail(source, "skill_unavailable");
            return apply(source, d, "unlearn " + id, () -> d.unlearn(id));
        }
        if (d.getLevel() < skill.level()) return fail(source, "level_required", skill.level());
        if (!d.hasLearned(id) && d.learnedSkills().size() >= PlayerAbilityData.MAX_SKILLS) return fail(source, "skill_capacity");
        return apply(source, d, "learn " + id, () -> d.learn(id));
    }
    private static int learnThrough(CommandSourceStack source, int level) throws CommandSyntaxException {
        var d = writable(source, false); if (d == null) return 0;
        var skills = SkillCatalog.IMPLEMENTED.stream().filter(s -> s.level() <= level && s.offeredTo(d.getAbility())).toList();
        long missing = skills.stream().filter(s -> !d.hasLearned(s.id())).count();
        if (d.learnedSkills().size() + missing > PlayerAbilityData.MAX_SKILLS) return fail(source, "skill_capacity");
        return apply(source, d, "learn_to_level " + level, () -> {
            d.setLevel(Math.max(d.getLevel(), level));
            skills.forEach(s -> d.learn(s.id()));
        });
    }
    private static int learnCurrent(CommandSourceStack source) throws CommandSyntaxException {
        var d = writable(source, false); if (d == null) return 0;
        int level = d.getLevel();
        var skills = SkillCatalog.IMPLEMENTED.stream().filter(s -> s.level() <= level && s.offeredTo(d.getAbility())).toList();
        long missing = skills.stream().filter(s -> !d.hasLearned(s.id())).count();
        if (d.learnedSkills().size() + missing > PlayerAbilityData.MAX_SKILLS) return fail(source, "skill_capacity");
        return apply(source, d, "learn_current", () -> skills.forEach(s -> d.learn(s.id())));
    }
    private static int category(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        var d = writable(source, true); if (d == null) return 0;
        if (AbilityCategory.find(id) == null) return fail(source, "unsupported_category", id);
        return apply(source, d, "category " + id, () -> d.setAbility(id));
    }
}
