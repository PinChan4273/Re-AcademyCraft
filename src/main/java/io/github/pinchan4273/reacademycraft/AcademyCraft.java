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
        // 新しい容量の項目をForgeが足す前に、1.0.0のファイルを一度だけ写す（Forgeは足すときにバックアップを取らない）。
        io.github.pinchan4273.reacademycraft.config.CapacityConfigMigration.backUpBeforeLoad(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
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
        event.enqueueWork(() -> {
            // 1.4.0のS2: アドオンの能力の登録の窓を開いて閉じる（すべてのmodのバスへ1回だけ出し、すぐに凍結する）。
            net.minecraftforge.fml.ModLoader.get().postEvent(new io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent());
            io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE.freeze();
            // 1.4.0のS4: 外部の技能の振る舞いの窓も閉じ、振り分けの表へ足す。
            io.github.pinchan4273.reacademycraft.ability.SkillBehaviorRegistry.INSTANCE.freeze();
            // 1.4.0のS4（D12）: 外部の技能の設定（名前空間ごとのサーバーの設定ファイル）。
            io.github.pinchan4273.reacademycraft.config.AddonSkillConfig.register(io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE);
            LOGGER.info("AcademyCraft ability registry frozen: {} categories, {} skills",
                    io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE.categories().size(),
                    io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE.skills().size());
            LOGGER.info("AcademyCraft gameplay systems initialized on Forge 1.20.1");
        });
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("academy_port_status")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable("command.academy.port_status"), false);
                    return 1;
                }));
    }
}
