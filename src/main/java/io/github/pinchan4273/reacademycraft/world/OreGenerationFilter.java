package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 原作WorldGenInit/ACWorldGenのgenOresの関門。配置されたfeatureが実行されるときに評価する。
 * 鉱石の配置データは登録したままで、既存のブロックには触れない。
 */
public final class OreGenerationFilter extends PlacementFilter {
    public static final OreGenerationFilter INSTANCE = new OreGenerationFilter();
    public static final Codec<OreGenerationFilter> CODEC = Codec.unit(() -> INSTANCE);
    private static final DeferredRegister<PlacementModifierType<?>> TYPES = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, "academy");
    public static final RegistryObject<PlacementModifierType<OreGenerationFilter>> TYPE = TYPES.register("ore_generation_enabled", () -> () -> CODEC);
    private OreGenerationFilter() { }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) { return AcademyConfig.GENERATE_ORES.get(); }
    @Override public PlacementModifierType<?> type() { return TYPE.get(); }
}
