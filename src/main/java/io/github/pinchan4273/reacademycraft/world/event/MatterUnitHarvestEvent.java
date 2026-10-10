package io.github.pinchan4273.reacademycraft.world.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** 原作MatterUnitHarvestEventは事後の通知で、取り消し可能な拒否ではない。 */
public final class MatterUnitHarvestEvent extends Event {
    public final ServerPlayer player;
    public final BlockPos pos;
    public final boolean collected;
    public MatterUnitHarvestEvent(ServerPlayer player, BlockPos pos, boolean collected) {
        this.player = player; this.pos = pos.immutable(); this.collected = collected;
    }
}
