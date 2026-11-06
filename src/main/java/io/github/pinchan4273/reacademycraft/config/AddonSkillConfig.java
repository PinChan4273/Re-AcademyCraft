package io.github.pinchan4273.reacademycraft.config;

import io.github.pinchan4273.reacademycraft.ability.AbilityRegistry;
import io.github.pinchan4273.reacademycraft.ability.RegistryViews;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 外部の技能の設定（1.4.0のS4、司令室11の裁定D12）。名前空間ごとに別のファイル（インスタンスの{@code config/academy-addon-<名前空間>.toml}）で、
 * 本体の設定ファイルには混ぜない。本体の設定と同じく、ワールドごとではなくインスタンスの設定（docs/CONFIG_SCOPE_AUDIT_JA.md）。
 * 項目は本体の技能と同じ（{@code skill.<技能>}の下に、有効・ダメージの倍率・CPと過負荷の消費の速さ・熟練度の伸び・ブロックの破壊）。
 * <p>
 * NeoForgeは登録の窓より前にmodの設定を読み終えるので、このファイルは登録簿の凍結の時に、STARTUPの設定として登録して読む（無ければ既定の値で作る。
 * NeoForgeはSTARTUPの設定を登録の時に読む。読み込んだ設定の型{@code ILoadedConfig}はsealedで、Forge版のように自分で開いたファイルを渡せない）。
 * NeoForgeはファイルを監視するが、項目を{@code gameRestart}にしてあるので、一度読んだ値は変わらず、ファイルの変更はゲームまたは専用サーバーの再起動で効く（ワールドの入り直しでは効かない）。
 * 値の範囲を外れた値は、読むときに既定の値へ直して書き戻す（NeoForgeは、その前のファイルの写しも残す）。
 * アドオンを外した後も、ファイルはそのまま残る（読まれないだけ）。
 */
public final class AddonSkillConfig {
    private AddonSkillConfig() { }

    /** 名前空間ごとの設定の仕様。 */
    private static final Map<String, ModConfigSpec> SPECS = new LinkedHashMap<>();
    private static final Map<Identifier, AcademyConfig.SkillValues> VALUES = new LinkedHashMap<>();
    private static final Map<String, net.neoforged.fml.config.ModConfig> FILES = new LinkedHashMap<>();

    /** その名前空間の設定ファイルの名前（インスタンスの{@code config}の中）。 */
    public static String fileName(String namespace) { return "academy-addon-" + namespace + ".toml"; }

    /** 登録簿の外部の技能から設定を作り、本体のmodの設定として足す（凍結の時に1回）。 */
    public static synchronized void register(AbilityRegistry registry) {
        if (!SPECS.isEmpty()) return;
        var byNamespace = new LinkedHashMap<String, List<Identifier>>();
        for (var skill : registry.skills())
            if (!skill.id().isAcademy())
                byNamespace.computeIfAbsent(skill.id().namespace(), ignored -> new java.util.ArrayList<>()).add(RegistryViews.location(skill.id()));
        for (var entry : byNamespace.entrySet()) {
            var b = new ModConfigSpec.Builder();
            b.comment("Re:AcademyCraft settings of the skills of " + entry.getKey() + " (experimental API).").push("skill");
            for (var skill : entry.getValue()) {
                // 技能の名前に「.」があっても1つの区分にする（push(String)は「.」で区切るので、一覧で渡す）。
                b.push(List.of(skill.getPath()));
                VALUES.put(skill, new AcademyConfig.SkillValues(
                        b.gameRestart().comment("Whether the skill can be learned and used.").define("enabled", true),
                        b.gameRestart().comment("A scale of this skill's damage to entities.").defineInRange("damage_scale", 1.0, 0.0, 100.0),
                        b.gameRestart().comment("How fast this skill consumes CP.").defineInRange("cp_consume_speed", 1.0, 0.0, 100.0),
                        b.gameRestart().comment("How fast this skill overloads.").defineInRange("overload_consume_speed", 1.0, 0.0, 100.0),
                        b.gameRestart().comment("How fast this skill's experience increases.").defineInRange("exp_incr_speed", 1.0, 0.0, 100.0),
                        b.gameRestart().comment("Whether this skill may break blocks.").define("destroy_blocks", true)));
                b.pop();
            }
            b.pop();
            var spec = b.build();
            SPECS.put(entry.getKey(), spec);
            // 本体のmodの設定として足す。STARTUPは、登録したこの時点で、インスタンスのconfigの中のファイルを読む（無ければ作り、誤りを直す）。
            FILES.put(entry.getKey(), net.neoforged.fml.config.ConfigTracker.INSTANCE.registerConfig(net.neoforged.fml.config.ModConfig.Type.STARTUP, spec,
                    net.neoforged.fml.ModList.get().getModContainerById(io.github.pinchan4273.reacademycraft.AcademyCraft.MODID).orElseThrow(), fileName(entry.getKey())));
        }
    }

    /** その外部の技能の設定（読み込まれていない・外部の技能でなければnull）。 */
    public static @Nullable AcademyConfig.SkillValues values(@Nullable Identifier skill) {
        if (skill == null) return null;
        var spec = SPECS.get(skill.getNamespace());
        return spec == null || !spec.isLoaded() ? null : VALUES.get(skill);
    }
    /** 設定のある名前空間（登録の順）。 */
    public static synchronized List<String> namespaces() { return List.copyOf(SPECS.keySet()); }
    /** その名前空間の設定の仕様（無ければnull。試験のため）。 */
    public static @Nullable ModConfigSpec spec(String namespace) { return SPECS.get(namespace); }
    /** その名前空間の、登録した設定（ファイルのパスと読み込んだ中身を持つ。無ければnull。試験のため）。 */
    public static @Nullable net.neoforged.fml.config.ModConfig file(String namespace) { return FILES.get(namespace); }
}
