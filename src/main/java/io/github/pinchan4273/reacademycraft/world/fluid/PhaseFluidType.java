package io.github.pinchan4273.reacademycraft.world.fluid;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;

/**
 * 虚像投影液（原作ACFluids.fluidImagProj、ID "imagproj"）。原作の設定は、光量8・粘度6000・温度0で、密度は7000を設定した後に
 * 1で上書きしているので1。原作のブロックBlockImagPhaseはBlockFluidClassicで、源を作らない（canCreateSourcesの既定はfalse）。
 * 材質はMaterial.WATERなので、1.12では水と同じく燃えているエンティティの火を消し、耕地を湿らせる。1.20.1のFluidTypeは
 * 指定しない限りどちらもしないので、ここで有効にする。バケツの音は1.12のFluidの既定（バケツの水の音）。
 */
public final class PhaseFluidType extends FluidType {
    /** 原作ACFluidsの見た目の画像: 静止・流れとも黒。光る層はPhaseLiquidRenderer（原作RenderImagPhaseLiquid）が重ねる。 */
    private static final ResourceLocation BLACK = ResourceLocation.fromNamespaceAndPath("academy", "block/black");

    public PhaseFluidType() {
        super(legacyProperties());
    }

    private static Properties legacyProperties() {
        Properties properties = Properties.create().descriptionId("block.academy.phase_liquid");
        properties.lightLevel(8).viscosity(6000).temperature(0).density(1);
        properties.canConvertToSource(false);
        properties.canExtinguish(true).canHydrate(true);
        properties.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY);
        // 舟は浮かべない（以前の版からの挙動を保つ）。
        properties.supportsBoating(false);
        return properties;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return BLACK; }
            @Override public ResourceLocation getFlowingTexture() { return BLACK; }
        });
    }
}
