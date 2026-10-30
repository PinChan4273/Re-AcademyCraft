package io.github.pinchan4273.reacademycraft;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;

/** NeoForgeでの最小限の起動処理。各ゲームシステムは、それぞれのクラスで登録する。 */
@Mod(AcademyCraft.MODID)
public final class AcademyCraft {
    public static final String MODID = "academy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AcademyCraft(IEventBus modBus, ModContainer container) {
        // 設定はワールドごとではなく、ゲームのインスタンスにつき1つのファイル（config/academy-common.toml）。
        // 新しい容量の項目をNeoForgeが足す前に、1.0.0のファイルを一度だけ写す（Forge版と同じ。CapacityConfigMigrationの説明を参照）。
        io.github.pinchan4273.reacademycraft.config.CapacityConfigMigration.backUpBeforeLoad(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, io.github.pinchan4273.reacademycraft.config.AcademyConfig.SPEC,
                io.github.pinchan4273.reacademycraft.config.AcademyConfig.FILE);
        modBus.addListener(io.github.pinchan4273.reacademycraft.network.AcademyRulesSync::rulesOnConfigReload);
        AcademyContent.register(modBus);
        io.github.pinchan4273.reacademycraft.world.AcademySounds.register(modBus);
        io.github.pinchan4273.reacademycraft.world.AcademyParticles.register(modBus);
        io.github.pinchan4273.reacademycraft.world.PhaseContent.register(modBus);
        io.github.pinchan4273.reacademycraft.world.PhaseLakeFeature.register(modBus);
        io.github.pinchan4273.reacademycraft.world.MaterialContent.register(modBus);
        io.github.pinchan4273.reacademycraft.world.OreGenerationFilter.register(modBus);
        io.github.pinchan4273.reacademycraft.world.FactorLootModifier.register(modBus);
        io.github.pinchan4273.reacademycraft.crafting.MachineRecipes.register(modBus);
        AcademyNetwork.register(modBus);
        AbilityCapabilities.register(modBus);
        io.github.pinchan4273.reacademycraft.world.AcademyComponents.register(modBus);
        io.github.pinchan4273.reacademycraft.world.AcademyCapabilities.register(modBus);
        modBus.addListener(this::commonSetup);
        // NeoForge自身のattachmentの写しより後に代入する（AbilityCapabilitiesの説明を参照）。
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, AbilityCapabilities::clonePlayer);
        NeoForge.EVENT_BUS.addListener(AbilityCapabilities::wakeUp);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("AcademyCraft gameplay systems initialized on NeoForge 1.21.1"));
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("academy_port_status")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("command.academy.port_status"), false);
                    return 1;
                }));
    }
}
