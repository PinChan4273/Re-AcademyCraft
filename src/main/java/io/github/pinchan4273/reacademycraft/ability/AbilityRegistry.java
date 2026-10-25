package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.AbilityRegistrar;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.SkillRequirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 能力のカテゴリと技能の登録簿（1.4.0のS2、内部の実装。公開の契約は{@code api}パッケージの型だけ）。
 * <p>
 * 本体の4カテゴリと41技能は、作った時点で1.3.0と同じ順番で登録する（{@link BuiltinAbilities}）。外部のアドオンは、本体が開く登録の窓
 * （{@link #registrar}）でだけ登録でき、{@link #freeze}の後は登録できない。並びは登録の順（本体が先）。
 * <p>
 * 断る登録（例外。黙って上書きしない）: 同じIDの2回目（カテゴリと技能は別の名前の集まり）、本体でない登録者の名前空間{@code academy}、
 * 本体が{@code academy}以外の名前空間で登録すること、知らないカテゴリへの技能、凍結の後の登録。技能の前提の技能は後から登録されてもよいので、
 * 凍結の時に、すべての前提が登録されていることを確かめる。
 */
public final class AbilityRegistry {
    /** 本体の登録者の名前。 */
    public static final String ACADEMY = AbilityId.ACADEMY;
    /** ゲームが使う登録簿。本体の定義は作った時点で入っている。 */
    public static final AbilityRegistry INSTANCE = BuiltinAbilities.register(new AbilityRegistry());

    private final Map<AbilityId, CategoryDefinition> categories = new LinkedHashMap<>();
    private final Map<AbilityId, SkillDefinition> skills = new LinkedHashMap<>();
    private final Map<AbilityId, String> categoryOwners = new HashMap<>(), skillOwners = new HashMap<>();
    private final List<CategoryDefinition> categoryList = new ArrayList<>();
    private final List<SkillDefinition> skillList = new ArrayList<>();
    private boolean frozen;
    /** 登録が変わるたびに増える数（読み取りの窓が、作り直しを知るため）。 */
    private int revision;

    /** 試験のための、本体の定義の入っていない新しい登録簿。ゲームは{@link #INSTANCE}を使う。 */
    public static AbilityRegistry empty() { return new AbilityRegistry(); }

    /** 登録の窓。窓は凍結まで使える。ownerはmodのID。 */
    public AbilityRegistrar registrar(String owner) {
        if (owner == null || owner.isEmpty()) throw new IllegalArgumentException("A registrar needs its owner's mod id");
        return new AbilityRegistrar() {
            @Override public String owner() { return owner; }
            @Override public void category(CategoryDefinition definition) { addCategory(owner, definition); }
            @Override public void skill(SkillDefinition definition) { addSkill(owner, definition); }
        };
    }

    private synchronized void checkOpen(String owner, AbilityId id) {
        if (frozen) throw new IllegalStateException(owner + " tried to register " + id + " after the ability registry was frozen");
        boolean academy = ACADEMY.equals(owner);
        if (academy != id.isAcademy())
            throw new IllegalArgumentException(academy ? "The built-in registration used another namespace: " + id
                    : owner + " may not register in the academy namespace: " + id);
    }

    private synchronized void addCategory(String owner, CategoryDefinition definition) {
        checkOpen(owner, definition.id());
        if (categories.containsKey(definition.id()))
            throw new IllegalArgumentException(owner + " registered the category " + definition.id() + " again (first by " + categoryOwners.get(definition.id()) + ")");
        categories.put(definition.id(), definition);
        categoryOwners.put(definition.id(), owner);
        categoryList.add(definition);
        revision++;
    }

    private synchronized void addSkill(String owner, SkillDefinition definition) {
        checkOpen(owner, definition.id());
        if (skills.containsKey(definition.id()))
            throw new IllegalArgumentException(owner + " registered the skill " + definition.id() + " again (first by " + skillOwners.get(definition.id()) + ")");
        if (definition.category() != null && !categories.containsKey(definition.category()))
            throw new IllegalArgumentException(owner + " registered the skill " + definition.id() + " in an unknown category " + definition.category());
        skills.put(definition.id(), definition);
        skillOwners.put(definition.id(), owner);
        skillList.add(definition);
        revision++;
    }

    /** 登録の窓を閉じる。すべての技能の前提が登録されていなければ例外（どの技能のどの前提か）。2回目以降は何もしない。 */
    public synchronized void freeze() {
        if (frozen) return;
        var missing = new ArrayList<String>();
        for (var skill : skillList)
            for (SkillRequirement requirement : skill.prerequisites())
                if (!skills.containsKey(requirement.skill())) missing.add(skill.id() + " needs " + requirement.skill() + " (" + skillOwners.get(skill.id()) + ")");
        if (!missing.isEmpty()) throw new IllegalStateException("Unknown prerequisite skills: " + missing);
        frozen = true;
    }

    public synchronized boolean frozen() { return frozen; }
    public synchronized int revision() { return revision; }
    /** 登録の順のカテゴリ（変えられない写し）。 */
    public synchronized List<CategoryDefinition> categories() { return Collections.unmodifiableList(new ArrayList<>(categoryList)); }
    /** 登録の順の技能（変えられない写し）。 */
    public synchronized List<SkillDefinition> skills() { return Collections.unmodifiableList(new ArrayList<>(skillList)); }
    public synchronized @Nullable CategoryDefinition category(AbilityId id) { return categories.get(id); }
    public synchronized @Nullable SkillDefinition skill(AbilityId id) { return skills.get(id); }
    public synchronized @Nullable String owner(AbilityId skill) { return skillOwners.get(skill); }
}
