package io.github.pinchan4273.reacademycraft.world;

import com.mojang.serialization.Codec;
import io.github.pinchan4273.reacademycraft.AcademyCraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * アイテムが持つ値（1.20.5からのData Component）。Forge 1.20.1版はスタックのNBTに同じ名前のキーで置いていた:
 * academy_energy_fe（エネルギーユニット）、academy_energy・academy_energy_remainder（携帯型開発機）。値の意味と範囲は同じ。
 */
public final class AcademyComponents {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(AcademyCraft.MODID);

    /** エネルギーユニットの正確なFE（Forge版のNBTキーacademy_energy_fe）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY_FE = integer("energy_fe");
    /** 携帯型開発機の蓄えたIF（Forge版のacademy_energy）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = integer("energy");
    /** 携帯型開発機の、IFに満たない端数のFE（Forge版のacademy_energy_remainder）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY_REMAINDER = integer("energy_remainder");

    private AcademyComponents() { }

    private static DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> integer(String name) {
        return COMPONENTS.registerComponentType(name, builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    }

    public static void register(IEventBus modBus) { COMPONENTS.register(modBus); }
}
