package io.github.pinchan4273.reacademycraft.capability;

import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/** プレイヤーごとに1つのprovider。ForgeがプレイヤーのForgeCapsのNBTへ保存する。 */
public final class AbilityDataProvider implements ICapabilitySerializable<CompoundTag> {
    private final PlayerAbilityData data = new PlayerAbilityData();
    private LazyOptional<PlayerAbilityData> view = LazyOptional.of(() -> data);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return AbilityCapabilities.PLAYER_ABILITY.orEmpty(capability, view);
    }

    @Override
    public CompoundTag serializeNBT() { return data.save(); }

    @Override
    public void deserializeNBT(CompoundTag tag) { data.load(tag); data.forgetCooldowns(); }

    public void bindOwner(net.minecraft.world.entity.player.Player player) { data.bindOwner(player); }
    public void invalidate() {
        LazyOptional<PlayerAbilityData> previous = view;
        // Entityは、capabilityが無効の間は参照を受け付けない。Forgeが次元移動やcloneのために同じプレイヤーを
        // 復活させるときは、新しいwrapperが要る。古いwrapperは無効のままにし、状態そのものは保持する。
        view = LazyOptional.of(() -> data);
        previous.invalidate();
    }
}
