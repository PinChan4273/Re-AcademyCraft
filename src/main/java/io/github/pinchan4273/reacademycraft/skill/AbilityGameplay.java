package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "academy")
public final class AbilityGameplay {
    private AbilityGameplay() {}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player && player.isAlive())
            player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(PlayerAbilityData::tick);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        // 素材のレシピ・発電機・因子の戦利品で、通常のサバイバルでの取得を支える。
        // プレビュー用のコマンドは明示的なオペレーター専用。通常のログインで能力を与えることはない。
        event.getDispatcher().register(Commands.literal("academy_test").requires(source -> source.hasPermission(2))
                .then(Commands.literal("prepare").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElseThrow(IllegalStateException::new);
                    if (data.isReadOnly() || (data.hasAbility() && !ArcGen.CATEGORY.equals(data.getAbility()))) return 0;
                    // 別のテストの準備で、進んだプレイヤーを格下げしない。
                    if (!data.hasAbility()) { data.setAbility(ArcGen.CATEGORY); data.setLevel(1); }
                    data.learn(ArcGen.ID);
                    data.setSlot(0, 0, ArcGen.ID); data.setCurrentPreset(0); data.recoverAfterDeath();
                    AbilitySyncEvents.sync(player, true);
                    context.getSource().sendSuccess(() -> Component.translatable("academy.test.prepared"), false);
                    return 1;
                }))
                .then(Commands.literal("charge").then(Commands.argument("energy", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 10000))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var stack = player.getMainHandItem();
                            if (!(stack.getItem() instanceof io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable))
                                return testFailure(context.getSource(), "academy.test.portable_required");
                            int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "energy");
                            io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable.setEnergy(stack, value);
                            player.getInventory().setChanged();
                            context.getSource().sendSuccess(() -> Component.translatable("academy.test.portable_charged", value), false);
                            return 1;
                        })))
                .then(Commands.literal("charge_machine").then(Commands.argument("energy", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 200000))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var start = player.getEyePosition(); var direction = player.getLookAngle(); var level = player.serverLevel();
                            for (int i = 0; i <= 6; i++) if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(start.add(direction.scale(i)))))
                                return testFailure(context.getSource(), "academy.test.machine_unavailable");
                            var hit = level.clip(new net.minecraft.world.level.ClipContext(start, start.add(direction.scale(6)),
                                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !level.mayInteract(player,hit.getBlockPos())
                                    || !(level.getBlockEntity(hit.getBlockPos()) instanceof io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity part))
                                return testFailure(context.getSource(), "academy.test.machine_required");
                            var main = part.main();
                            int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"energy");
                            if (main == null || !main.setEnergyIFForTest(value))
                                return testFailure(context.getSource(), "academy.test.machine_unavailable");
                            context.getSource().sendSuccess(() -> Component.translatable("academy.test.machine_charged", value), false); return 1;
                        })))
                .then(Commands.literal("recover").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(PlayerAbilityData::recoverAfterDeath);
                    AbilitySyncEvents.sync(player, true); return 1;
                }))
                .then(Commands.literal("proficiency")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0, 1))
                                .executes(context -> proficiency(context.getSource(), ArcGen.ID, FloatArgumentType.getFloat(context, "value"))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
                                        SkillCatalog.IMPLEMENTED.stream().map(SkillCatalog.Definition::id), builder))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, 1))
                                        .executes(context -> proficiency(context.getSource(), ResourceLocationArgument.getId(context, "skill"),
                                                FloatArgumentType.getFloat(context, "value")))))));
        AbilityTestCommands.register(event.getDispatcher());
    }
    private static int testFailure(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(key));
        return 0;
    }
    private static int proficiency(CommandSourceStack source, ResourceLocation skill, float value) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElseThrow(IllegalStateException::new);
        var definition = SkillCatalog.find(skill);
        if (data.isReadOnly() || definition == null || !definition.offeredTo(data.getAbility()) || !data.hasLearned(skill)) {
            source.sendFailure(Component.translatable("academy.test.skill_unavailable")); return 0;
        }
        // テストの準備専用: 技能を与えたり、スロットを変えたり、レベルの経験値を作ったりはしない。
        data.setProficiency(skill, value); AbilitySyncEvents.sync(player, true);
        source.sendSuccess(() -> Component.translatable("academy.test.proficiency", Component.translatable(SkillCatalog.find(skill).translation()), Math.round(value * 100)), false);
        return 1;
    }
}
