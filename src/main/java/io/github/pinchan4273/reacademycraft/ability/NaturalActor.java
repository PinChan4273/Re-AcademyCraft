package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.ActorKind;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 自然能力者（プレイヤー本人）の主体。値は呼ぶたびにプレイヤーから読む。 */
record NaturalActor(ServerPlayer player) implements AbilityActor {
    @Override public UUID id() { return player.getUUID(); }
    @Override public ActorKind kind() { return ActorKind.NATURAL; }
    @Override public ServerLevel level() { return player.serverLevel(); }
    @Override public Vec3 position() { return player.getEyePosition(); }
    @Override public Vec3 look() { return player.getLookAngle(); }
    @Override public Entity entity() { return player; }
    @Override public ServerPlayer attributedPlayer() { return player; }
}
