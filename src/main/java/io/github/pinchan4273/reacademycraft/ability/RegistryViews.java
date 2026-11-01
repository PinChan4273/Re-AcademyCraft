package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * 登録簿の読み取りの窓（1.4.0のS2の互換の層）。既存の定数（SkillCatalog.IMPLEMENTED、AbilityCategory.REGISTERED、AbilityAction.HELD_SKILLSなど）は、
 * 名前を残したまま、ここで作る変えられない窓になる。窓は、登録簿の版（{@link AbilityRegistry#revision}）が変わったときだけ作り直す
 * （凍結の後は変わらない）。要素の変換は登録の順のまま。
 */
public final class RegistryViews {
    private RegistryViews() { }

    /** 名前空間付きの名前をMinecraftの名前へ（読み取りの窓の中だけで使う。公開の型には出さない）。 */
    public static ResourceLocation location(AbilityId id) { return ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()); }
    public static AbilityId id(ResourceLocation location) { return new AbilityId(location.getNamespace(), location.getPath()); }

    /** 登録簿の要素を変換した、変えられない一覧の窓。 */
    public static <S, T> List<T> list(Supplier<List<S>> source, Function<S, T> convert) {
        return new AbstractList<>() {
            private int revision = -1;
            private List<T> cache = List.of();

            private synchronized List<T> current() {
                int now = AbilityRegistry.INSTANCE.revision();
                if (now != revision) {
                    var out = new ArrayList<T>();
                    for (S s : source.get()) out.add(convert.apply(s));
                    cache = List.copyOf(out);
                    revision = now;
                }
                return cache;
            }
            @Override public T get(int index) { return current().get(index); }
            @Override public int size() { return current().size(); }
        };
    }

    /** その発動の種類の技能の名前の集まり（登録の順）。 */
    public static Set<ResourceLocation> skillsOfKind(ActivationKind kind) {
        return new AbstractSet<>() {
            private int revision = -1;
            private Set<ResourceLocation> cache = Set.of();

            private synchronized Set<ResourceLocation> current() {
                int now = AbilityRegistry.INSTANCE.revision();
                if (now != revision) {
                    var out = new LinkedHashSet<ResourceLocation>();
                    for (SkillDefinition skill : AbilityRegistry.INSTANCE.skills()) if (skill.kind() == kind) out.add(location(skill.id()));
                    cache = java.util.Collections.unmodifiableSet(out);
                    revision = now;
                }
                return cache;
            }
            @Override public boolean contains(Object o) { return current().contains(o); }
            @Override public Iterator<ResourceLocation> iterator() { return current().iterator(); }
            @Override public int size() { return current().size(); }
        };
    }

    /**
     * 技能の名前の翻訳キー。登録されていれば、その表示の情報の名前。登録されていなければ、名前空間を保った既定の形
     * （{@code academy}なら1.3.0と同じ{@code academy.skill.<path>}、ほかは{@code <namespace>.skill.<path>}）。
     */
    public static String skillNameKey(ResourceLocation skill) {
        var definition = registered(skill);
        return definition != null ? definition.presentation().nameKey() : skill.getNamespace() + ".skill." + skill.getPath();
    }
    /** 技能の説明の翻訳キー（開発機の画面）。登録された説明が無ければ、{@code <namespace>.developer.desc.<path>}（academyは1.3.0と同じ）。 */
    public static String skillDescriptionKey(ResourceLocation skill) {
        var definition = registered(skill);
        return definition != null && definition.presentation().descriptionKey() != null ? definition.presentation().descriptionKey()
                : skill.getNamespace() + ".developer.desc." + skill.getPath();
    }
    /** カテゴリの名前の翻訳キー。登録されていなければ{@code <namespace>.ability.<path>}（academyは1.3.0と同じ）。 */
    public static String categoryNameKey(ResourceLocation category) {
        io.github.pinchan4273.reacademycraft.api.CategoryDefinition definition = null;
        try {
            definition = AbilityRegistry.INSTANCE.category(id(category));
        } catch (IllegalArgumentException ignored) { }
        return definition != null ? definition.presentation().nameKey() : category.getNamespace() + ".ability." + category.getPath();
    }
    /**
     * カテゴリの表示の名前。登録されていればその翻訳、登録されていなければ（アドオンを外した後の保存データなど）
     * 「導入されていない系統（名前）」（1.4.0のS4。手動確認 2026-10-25: 翻訳のキーがそのまま出ていた）。
     */
    public static net.minecraft.network.chat.Component categoryName(ResourceLocation category) {
        io.github.pinchan4273.reacademycraft.api.CategoryDefinition definition = null;
        try {
            definition = AbilityRegistry.INSTANCE.category(id(category));
        } catch (IllegalArgumentException ignored) { }
        return definition != null ? net.minecraft.network.chat.Component.translatable(definition.presentation().nameKey())
                : net.minecraft.network.chat.Component.translatable("academy.ability.not_installed", category.toString());
    }
    /** そのカテゴリが登録されているか（公開のIDの書式に合わない名前は、登録されていない）。 */
    public static boolean categoryRegistered(ResourceLocation category) {
        try {
            return AbilityRegistry.INSTANCE.category(id(category)) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    private static SkillDefinition registered(ResourceLocation skill) {
        try {
            return AbilityRegistry.INSTANCE.skill(id(skill));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** その技能の発動の種類。登録されていなければnull。 */
    public static ActivationKind kind(ResourceLocation skill) {
        if (skill == null) return null;
        AbilityId id;
        try {
            id = id(skill);
        } catch (IllegalArgumentException e) {
            return null; // 保存データなどの、公開のIDの書式に合わない名前は、登録されていない
        }
        var definition = AbilityRegistry.INSTANCE.skill(id);
        return definition == null ? null : definition.kind();
    }
}
