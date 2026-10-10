package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 原作ReflectEvent（WeAthFolD）のForge移植: 取り消しは反射の要求であり、通常のダメージの取り消しではない。
 * ダメージの拒否にはLivingHurtEventを使う。反射の受動能力は、その能力が移植されたときにここを購読できる。
 */
@Cancelable
public final class SkillReflectEvent extends Event {
    private final ServerPlayer caster;
    private final ResourceLocation skill;
    private final Entity target;
    public SkillReflectEvent(ServerPlayer caster, ResourceLocation skill, Entity target) {
        this.caster = caster; this.skill = skill; this.target = target;
    }
    public ServerPlayer caster() { return caster; }
    public ResourceLocation skill() { return skill; }
    public Entity target() { return target; }
}
