package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehaviorRegistrar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 外部の技能の振る舞いの登録簿（1.4.0のS4、内部の実装）。能力の登録簿と対になり、同じ時機に凍結する。本体の41技能は、ここではなく
 * 振り分けの表（{@link BuiltinSkillDispatch}）が受け持つ。
 * <p>
 * 断る登録: 本体の名前空間、登録されていない技能、ほかの登録者の技能、受動の技能、同じ技能の2回目、凍結の後。凍結の時には、受動でない
 * 外部の技能すべてに振る舞いがあることを確かめ、無ければ読み込みを止める。凍結の後、振り分けの表へ登録の順で足す。
 */
public final class SkillBehaviorRegistry {
    public static final SkillBehaviorRegistry INSTANCE = new SkillBehaviorRegistry(AbilityRegistry.INSTANCE);

    private final AbilityRegistry abilities;
    private final Map<AbilityId, SkillBehavior> behaviors = new LinkedHashMap<>();
    private boolean frozen;

    private SkillBehaviorRegistry(AbilityRegistry abilities) { this.abilities = abilities; }
    /** 試験のための、その登録簿と対になる新しい振る舞いの登録簿。凍結しても振り分けの表には足さない。 */
    public static SkillBehaviorRegistry forTest(AbilityRegistry abilities) { return new SkillBehaviorRegistry(abilities); }

    public SkillBehaviorRegistrar registrar(String owner) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("A registrar needs its owner's mod id");
        return new SkillBehaviorRegistrar() {
            @Override public String owner() { return owner; }
            @Override public void behavior(AbilityId skill, SkillBehavior behavior) { add(owner, skill, behavior); }
        };
    }

    private synchronized void add(String owner, AbilityId skill, SkillBehavior behavior) {
        if (behavior == null) throw new IllegalArgumentException(owner + " registered no behavior for " + skill);
        if (frozen) throw new IllegalStateException(owner + " tried to register the behavior of " + skill + " after the ability registry was frozen");
        if (skill.isAcademy()) throw new IllegalArgumentException(owner + " may not register a behavior in the academy namespace: " + skill);
        var definition = abilities.skill(skill);
        if (definition == null) throw new IllegalArgumentException(owner + " registered the behavior of an unknown skill " + skill);
        if (!owner.equals(abilities.owner(skill)))
            throw new IllegalArgumentException(owner + " registered the behavior of " + skill + ", which " + abilities.owner(skill) + " registered");
        if (definition.kind() == ActivationKind.PASSIVE) throw new IllegalArgumentException(owner + " registered a behavior for the passive skill " + skill);
        if (behaviors.containsKey(skill)) throw new IllegalArgumentException(owner + " registered the behavior of " + skill + " again");
        behaviors.put(skill, behavior);
    }

    /** 窓を閉じる（能力の登録簿の凍結の後に呼ぶ）。振る舞いの無い受動でない外部の技能があれば例外。2回目以降は何もしない。 */
    public synchronized void freeze() {
        if (frozen) return;
        if (!abilities.frozen()) throw new IllegalStateException("The skill behaviors were frozen before the ability registry");
        var missing = new ArrayList<String>();
        for (var skill : abilities.skills())
            if (!skill.id().isAcademy() && skill.kind() != ActivationKind.PASSIVE && !behaviors.containsKey(skill.id()))
                missing.add(skill.id() + " (" + abilities.owner(skill.id()) + ")");
        if (!missing.isEmpty()) throw new IllegalStateException("Skills without a behavior: " + missing);
        frozen = true;
        if (this == INSTANCE)
            for (var skill : abilities.skills())
                if (behaviors.containsKey(skill.id())) AddonSessions.addToDispatch(RegistryViews.location(skill.id()), skill.kind());
    }

    public synchronized boolean frozen() { return frozen; }
    public synchronized @Nullable SkillBehavior behavior(AbilityId skill) { return behaviors.get(skill); }
}
