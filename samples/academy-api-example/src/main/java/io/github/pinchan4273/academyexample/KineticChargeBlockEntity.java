package io.github.pinchan4273.academyexample;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Kinetic Chargeの持ち主（置いた人のUUID）と、機械の主体の識別子を保存する。 */
public final class KineticChargeBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private UUID actorId = UUID.randomUUID();

    public KineticChargeBlockEntity(BlockPos pos, BlockState state) { super(AcademyExample.KINETIC_CHARGE_ENTITY.get(), pos, state); }

    public @Nullable UUID owner() { return owner; }
    public UUID actorId() { return actorId; }
    void setOwner(UUID owner) { this.owner = owner; setChanged(); }

    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        output.store("Actor", net.minecraft.core.UUIDUtil.CODEC, actorId);
    }
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        input.read("Actor", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> actorId = id);
    }
}
