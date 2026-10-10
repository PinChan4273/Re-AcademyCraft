package io.github.pinchan4273.reacademycraft;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Forgeでの最小限の起動処理。各ゲームシステムは、それぞれのクラスで登録する。 */
@Mod(AcademyCraft.MODID)
public final class AcademyCraft {
    public static final String MODID = "academy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AcademyCraft(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        // 設定はワールドごとではなく、ゲームのインスタンスにつき1つのファイル（config/academy-common.toml）。
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, io.github.pinchan4273.reacademycraft.config.AcademyConfig.SPEC,
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
        AcademyNetwork.register();
        modBus.addListener(AbilityCapabilities::register);
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, AbilityCapabilities::attach);
        MinecraftForge.EVENT_BUS.addListener(AbilityCapabilities::clonePlayer);
        MinecraftForge.EVENT_BUS.addListener(AbilityCapabilities::wakeUp);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("AcademyCraft gameplay systems initialized on Forge 1.20.1"));
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("academy_port_status")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("command.academy.port_status"), false);
                    return 1;
                }));
    }
}
