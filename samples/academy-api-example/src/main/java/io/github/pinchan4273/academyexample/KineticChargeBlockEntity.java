package io.github.pinchan4273.academyexample;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putUUID("Actor", actorId);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.hasUUID("Actor")) actorId = tag.getUUID("Actor");
    }
}
