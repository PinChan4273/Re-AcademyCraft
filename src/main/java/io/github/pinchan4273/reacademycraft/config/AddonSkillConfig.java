package io.github.pinchan4273.reacademycraft.config;

import io.github.pinchan4273.reacademycraft.ability.AbilityRegistry;
import io.github.pinchan4273.reacademycraft.ability.RegistryViews;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 外部の技能の設定（1.4.0のS4、司令室11の裁定D12）。名前空間ごとに別のファイル（インスタンスの{@code config/academy-addon-<名前空間>.toml}）で、
 * 本体の設定ファイルには混ぜない。本体の設定と同じく、ワールドごとではなくインスタンスの設定（docs/CONFIG_SCOPE_AUDIT_JA.md）。
 * 項目は本体の技能と同じ（{@code skill.<技能>}の下に、有効・ダメージの倍率・CPと過負荷の消費の速さ・熟練度の伸び・ブロックの破壊）。
 * <p>
 * Forgeは登録の窓より前にmodの設定を読み終えるので、このファイルは登録簿の凍結の時に、ここで読む（無ければ既定の値で作る）。
 * Forgeの再読込の監視は付けないので、ファイルの変更はゲームの次の起動で効く。値の範囲を外れた値は、読むときに既定の値へ直して書き戻す。
 * アドオンを外した後も、ファイルはそのまま残る（読まれないだけ）。
 */
public final class AddonSkillConfig {
    private AddonSkillConfig() { }

    /** 名前空間ごとの設定の仕様。 */
    private static final Map<String, ForgeConfigSpec> SPECS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, AcademyConfig.SkillValues> VALUES = new LinkedHashMap<>();
    private static final Map<String, com.electronwill.nightconfig.core.file.CommentedFileConfig> FILES = new LinkedHashMap<>();

    /** その名前空間の設定ファイルの名前（インスタンスの{@code config}の中）。 */
    public static String fileName(String namespace) { return "academy-addon-" + namespace + ".toml"; }

    /** 登録簿の外部の技能から設定を作り、本体のmodの設定として足す（凍結の時に1回）。 */
    public static synchronized void register(AbilityRegistry registry) {
        if (!SPECS.isEmpty()) return;
        var byNamespace = new LinkedHashMap<String, List<ResourceLocation>>();
        for (var skill : registry.skills())
            if (!skill.id().isAcademy())
                byNamespace.computeIfAbsent(skill.id().namespace(), ignored -> new java.util.ArrayList<>()).add(RegistryViews.location(skill.id()));
        for (var entry : byNamespace.entrySet()) {
            var b = new ForgeConfigSpec.Builder();
            b.comment("Re:AcademyCraft settings of the skills of " + entry.getKey() + " (experimental API).").push("skill");
            for (var skill : entry.getValue()) {
                // 技能の名前に「.」があっても1つの区分にする（push(String)は「.」で区切るので、一覧で渡す）。
                b.push(List.of(skill.getPath()));
                VALUES.put(skill, new AcademyConfig.SkillValues(
                        b.comment("Whether the skill can be learned and used.").define("enabled", true),
                        b.comment("A scale of this skill's damage to entities.").defineInRange("damage_scale", 1.0, 0.0, 100.0),
                        b.comment("How fast this skill consumes CP.").defineInRange("cp_consume_speed", 1.0, 0.0, 100.0),
                        b.comment("How fast this skill overloads.").defineInRange("overload_consume_speed", 1.0, 0.0, 100.0),
                        b.comment("How fast this skill's experience increases.").defineInRange("exp_incr_speed", 1.0, 0.0, 100.0),
                        b.comment("Whether this skill may break blocks.").define("destroy_blocks", true)));
                b.pop();
            }
            b.pop();
            var spec = b.build();
            SPECS.put(entry.getKey(), spec);
            var file = com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(
                            net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(fileName(entry.getKey())))
                    .sync().preserveInsertionOrder().writingMode(com.electronwill.nightconfig.core.io.WritingMode.REPLACE).build();
            file.load();
            if (!spec.isCorrect(file)) { spec.correct(file); file.save(); }
            spec.setConfig(file);
            FILES.put(entry.getKey(), file);
        }
    }

    /** その外部の技能の設定（読み込まれていない・外部の技能でなければnull）。 */
    public static @Nullable AcademyConfig.SkillValues values(@Nullable ResourceLocation skill) {
        if (skill == null) return null;
        var spec = SPECS.get(skill.getNamespace());
        return spec == null || !spec.isLoaded() ? null : VALUES.get(skill);
    }
    /** 設定のある名前空間（登録の順）。 */
    public static synchronized List<String> namespaces() { return List.copyOf(SPECS.keySet()); }
    /** その名前空間の設定の仕様（無ければnull。試験のため）。 */
    public static @Nullable ForgeConfigSpec spec(String namespace) { return SPECS.get(namespace); }
    /** その名前空間の、読み込んだ設定ファイル（無ければnull。試験のため）。 */
    public static @Nullable com.electronwill.nightconfig.core.file.CommentedFileConfig file(String namespace) { return FILES.get(namespace); }
}
