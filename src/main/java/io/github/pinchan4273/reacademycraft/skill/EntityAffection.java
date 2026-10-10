package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

/**
 * 原作EntityAffection: Vector DeviationとVector Reflectionが触れられるエンティティと、逸らしたエンティティに残す印。
 *
 * どちらのリストも、原作がdefault.confから読んだのと同じく、サーバーの設定（vecmanip.affected_entities）から来る。既定では
 * アイテム、エンチャントの瓶、すべての生き物とすべてのモンスターを除外し、矢（1.0）、ポーション（1.4）、雪玉（0.1）に難しさを与える。
 * 原作の読み込みは、リストにfilterではなくfindを呼ぶので、最初に解決した難しさだけを保つ。そのため矢の1.0だけが適用され、
 * 影響を受けるエンティティの難しさはすべて1になる。これを保つ。
 * 原作はクラスで照合したので、"living"と"mob"はEntityLivingBaseとEntityMobを意味した。
 */
public final class EntityAffection {
    /** 一度止めたエンティティを示す、原作のentity dataのキー。 */
    public static final String MARK = "ac_vm_deviated";
    /** 1.13で名前が変わった1.12.2のエンティティ名。原作のリストが同じエンティティを指し続けるようにする。 */
    private static final Map<String, String> RENAMED = Map.of("xp_bottle", "experience_bottle", "xp_orb", "experience_orb",
            "fireworks_rocket", "firework_rocket", "ender_crystal", "end_crystal", "evocation_fangs", "evoker_fangs");
    private EntityAffection() { }

    /** 除外なら負、そうでなければ難しさ。 */
    public static float difficulty(Entity entity) {
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (String name : AcademyConfig.affectedExcluded()) {
            if (name.equalsIgnoreCase("living") ? entity instanceof LivingEntity
                    : name.equalsIgnoreCase("mob") ? entity instanceof Monster
                    : type.equals(entity(name))) return -1;
        }
        for (String entry : AcademyConfig.affectedDifficulties()) {
            int at = entry.lastIndexOf('=');
            if (at <= 0) continue;
            var named = entity(entry.substring(0, at));
            if (named == null) continue;
            if (!named.equals(type)) return 1;
            try { return Float.parseFloat(entry.substring(at + 1).trim()); }
            catch (NumberFormatException e) { return 1; }
        }
        return 1;
    }
    /** 設定の名前に対する登録済みエンティティ型のid。無ければnull。 */
    private static ResourceLocation entity(String name) {
        name = name.trim().toLowerCase(java.util.Locale.ROOT);
        var id = ResourceLocation.tryParse(name);
        if (id == null) return null;
        if ("minecraft".equals(id.getNamespace()) && RENAMED.containsKey(id.getPath()))
            id = ResourceLocation.fromNamespaceAndPath("minecraft", RENAMED.get(id.getPath()));
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? id : null;
    }
    public static boolean excluded(Entity entity) { return difficulty(entity) < 0; }
    public static void mark(Entity entity) { entity.getPersistentData().putBoolean(MARK, true); }
    public static boolean marked(Entity entity) { return entity.getPersistentData().getBoolean(MARK); }
}
