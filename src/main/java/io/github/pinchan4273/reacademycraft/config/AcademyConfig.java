package io.github.pinchan4273.reacademycraft.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 能力の設定をForgeのサーバー設定にしたもの。項目と既定値は原作のAbilityPipeline/Skill（default.conf）のもの。
 * WeAthFolDの「全体またはディメンションの許可リスト」のOR条件を保ち、技能ごとの禁止は最終判断のまま。
 *
 * <p>ワールドごとではなく、Minecraft/Forgeのインスタンス全体でconfig/academy-common.tomlに保存する。ここでのゲームプレイ上の
 * 読み取りはすべて論理サーバーのもの: 専用サーバーは自身のファイルを、シングルプレイのワールドはそのインスタンスのファイルを読む。
 * サーバーへ参加したクライアントもこのファイルを持つが、そこでは何も決めない: クライアントはサーバーが送ったもの
 * （{@link SyncedAcademyRules}）を読む。
 */
public final class AcademyConfig {
    /** インスタンス全体のファイル。academy-server.tomlはもう無い。 */
    public static final String FILE = "academy-common.toml";
    public static final ForgeConfigSpec SPEC;
    /** 原作generic.giveCloudTerminal。 */
    public static final ForgeConfigSpec.BooleanValue GIVE_CLOUD_TERMINAL;
    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS, ATTACK_PLAYERS, RAILGUN_DESTROY_BLOCKS, MELTDOWNER_DESTROY_BLOCKS,
            GENERATE_ORES, GENERATE_PHASE_LIQUID;
    public static final ForgeConfigSpec.DoubleValue SKILL_EXPERIENCE_MULTIPLIER, ELECTROMASTER_EXPERIENCE_MULTIPLIER,
            CP_RECOVER_SPEED_MULTIPLIER, OVERLOAD_RECOVER_SPEED_MULTIPLIER, DAMAGE_SCALE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DESTRUCTION_DIMENSIONS;
    // 原作default.confのac.ability.data。
    public static final ForgeConfigSpec.IntValue CP_RECOVER_COOLDOWN, OVERLOAD_RECOVER_COOLDOWN;
    /** 2つの回復待ちに設定できる最長の値。したがってセーブが保持できる最長の値でもある。 */
    public static final int MAX_RECOVER_COOLDOWN = 72000;
    public static final ForgeConfigSpec.DoubleValue MAXCP_INCR_RATE, MAXO_INCR_RATE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> INIT_CP, ADD_CP, INIT_OVERLOAD, ADD_OVERLOAD;
    /**
     * 原作default.confのac.ability.category.<category>.common.prog_incr_rate。ただし電撃使いは従来どおり
     * skills.electromaster.experience_multiplier。
     */
    public static final java.util.Map<String, ForgeConfigSpec.DoubleValue> CATEGORY_PROGRESS = new java.util.LinkedHashMap<>();
    /** 原作default.confのac.ability.category.vecmanip.common.affected_entities。 */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> AFFECTED_DIFFICULTIES, AFFECTED_EXCLUDED;
    public static final List<String> LEGACY_AFFECTED_DIFFICULTIES = List.of("minecraft:arrow=1.0", "minecraft:potion=1.4", "minecraft:snowball=0.1"),
            LEGACY_AFFECTED_EXCLUDED = List.of("item", "xp_bottle", "living", "mob");
    /** 原作default.confの技能ごとの共通項目。 */
    public record SkillValues(ForgeConfigSpec.BooleanValue enabled, ForgeConfigSpec.DoubleValue damageScale,
                              ForgeConfigSpec.DoubleValue cpConsumeSpeed, ForgeConfigSpec.DoubleValue overloadConsumeSpeed,
                              ForgeConfigSpec.DoubleValue expIncrSpeed, ForgeConfigSpec.BooleanValue destroyBlocks) { }
    public static final java.util.Map<String, SkillValues> SKILLS = new java.util.LinkedHashMap<>();
    /**
     * 切り替え型の各技能の、熟練度0%での回復を上回る維持コスト: 回復を完全に止めるモードがあったときの技能自身の0%の維持コスト。
     * それを持たなかったBody Intensifyは10。
     */
    public static final java.util.Map<String, Double> DEFAULT_TOGGLE_UPKEEP = defaults("body_intensify", 10d, "electron_missile", 12d,
            "jet_engine", 8d, "flashing", 4d, "vec_deviation", 18d, "vec_reflection", 15d, "storm_wing", 40d);
    public static final java.util.Map<String, ForgeConfigSpec.DoubleValue> TOGGLE_UPKEEP = new java.util.LinkedHashMap<>();
    public static final List<Integer> DEFAULT_BODY_WARMUP = List.of(20, 15, 10, 5, 2);
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> BODY_WARMUP_TICKS;
    public static final ForgeConfigSpec.DoubleValue BODY_EXTRA_EXHAUSTION;
    private static java.util.Map<String, Double> defaults(Object... pairs) {
        var map = new java.util.LinkedHashMap<String, Double>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Double) pairs[i + 1]);
        return java.util.Collections.unmodifiableMap(map);
    }
    /** 原作default.confが名前で挙げる技能。その名前は移植版のidでもある。 */
    public static final List<String> SKILL_NAMES = List.of(
            "brain_course", "brain_course_advanced", "mind_course",
            "arc_gen", "charging", "body_intensify", "mine_detect", "mag_movement", "thunder_bolt", "railgun", "thunder_clap", "mag_manip",
            "electron_bomb", "rad_intensify", "scatter_bomb", "light_shield", "meltdowner", "mine_ray_basic", "ray_barrage", "jet_engine",
            "mine_ray_expert", "mine_ray_luck", "electron_missile",
            "threatening_teleport", "dim_folding_theorem", "penetrate_teleport", "mark_teleport", "flesh_ripping", "location_teleport",
            "shift_tp", "space_fluct", "flashing",
            "dir_shock", "ground_shock", "vec_accel", "vec_deviation", "dir_blast", "storm_wing", "blood_retro", "vec_reflection", "plasma_cannon");
    static final List<Double> LEGACY_INIT_CP = List.of(1800d, 1800d, 2800d, 4000d, 5800d, 8000d),
            LEGACY_ADD_CP = List.of(0d, 900d, 1000d, 1500d, 1700d, 12000d),
            LEGACY_INIT_OVERLOAD = List.of(100d, 100d, 150d, 240d, 350d, 500d),
            LEGACY_ADD_OVERLOAD = List.of(0d, 40d, 70d, 80d, 100d, 500d);
    static {
        var b = new ForgeConfigSpec.Builder();
        b.push("generic");
        GENERATE_ORES = b.comment("Generate AcademyCraft ores in new overworld terrain. Does not remove existing ores.")
                .define("genOres", true);
        GENERATE_PHASE_LIQUID = b.comment("Generate original-style phase liquid pools in new overworld terrain. Existing pools remain unchanged.")
                .define("genPhaseLiquid", true);
        DESTROY_BLOCKS = b.comment("Allow ability terrain destruction. Default matches AcademyCraft 1.12.2.")
                .define("destroyBlocks", true);
        DESTRUCTION_DIMENSIONS = b.comment("Dimension IDs allowed to destroy blocks even when destroyBlocks=false.",
                        "Example: minecraft:the_nether. Empty permits no exceptions. Legacy numeric/folder IDs are not supported.")
                .defineListAllowEmpty(List.of("worldsWhitelistedDestroyingBlocks"), List.of(),
                        value -> value instanceof String s && s.length() <= 256 && ResourceLocation.tryParse(s) != null && s.contains(":"));
        ATTACK_PLAYERS = b.comment("Allow skill damage against players, still respecting server PvP and friendly-fire rules.")
                .define("attackPlayer", true);
        GIVE_CLOUD_TERMINAL = b.comment("Whether the player will be given MisakaCloud Terminal on first spawn.")
                .define("giveCloudTerminal", true);
        SKILL_EXPERIENCE_MULTIPLIER = b.comment("Global multiplier for level experience gained by using controllable skills.",
                        "The AcademyCraft 1.12.2 default is 1.0. Skill proficiency gain is not multiplied.")
                .defineInRange("skillExperienceMultiplier", 1.0, 0.0, 100.0);
        CP_RECOVER_SPEED_MULTIPLIER = b.comment("Global multiplier for CP recovery speed.",
                        "1.12.2 default.conf: ac.ability.data.cp_recover_speed, default 1.0. The original per-tick",
                        "cooldown (15 ticks after use) is unaffected; this only scales the recovery rate afterward.")
                .defineInRange("cpRecoverSpeedMultiplier", 1.0, 0.0, 100.0);
        OVERLOAD_RECOVER_SPEED_MULTIPLIER = b.comment("Global multiplier for Overload recovery speed.",
                        "1.12.2 default.conf: ac.ability.data.overload_recover_speed, default 1.0. The original",
                        "per-tick cooldown (32 ticks after use) is unaffected; this only scales the decay rate afterward.")
                .defineInRange("overloadRecoverSpeedMultiplier", 1.0, 0.0, 100.0);
        DAMAGE_SCALE = b.comment("Global multiplier for skill damage, applied once in SkillCombat.attack, the single",
                        "shared entry point every migrated damaging skill already uses.",
                        "1.12.2 default.conf: ac.ability.calc_global.damage_scale, default 1.0.")
                .defineInRange("damageScale", 1.0, 0.0, 100.0);
        b.pop(); b.push("skills").push("electromaster");
        ELECTROMASTER_EXPERIENCE_MULTIPLIER = b.comment("Electromaster category multiplier for level experience.",
                        "This is multiplied by generic.skillExperienceMultiplier; the legacy default is 1.0.")
                .defineInRange("experience_multiplier", 1.0, 0.0, 100.0);
        b.pop(); b.push("railgun");
        RAILGUN_DESTROY_BLOCKS = b.comment("Per-skill terrain switch. False also overrides a dimension allowlist entry.")
                .define("destroy_blocks", true);
        b.pop(); b.push("meltdowner");
        MELTDOWNER_DESTROY_BLOCKS = b.comment("Per-skill terrain switch for the Meltdowner skill itself (the category's",
                        "namesake beam). False also overrides a dimension allowlist entry. Legacy default: true.")
                .define("destroy_blocks", true);
        b.pop(2);
        b.comment("AcademyCraft 1.12.2's default.conf values; the defaults here are legacy's.").push("ability");
        b.push("data");
        CP_RECOVER_COOLDOWN = b.comment("Ticks after spending CP before it starts to come back.").defineInRange("cp_recover_cooldown", 15, 0, MAX_RECOVER_COOLDOWN);
        OVERLOAD_RECOVER_COOLDOWN = b.comment("Ticks after overloading before overload starts to fall.").defineInRange("overload_recover_cooldown", 32, 0, MAX_RECOVER_COOLDOWN);
        MAXCP_INCR_RATE = b.comment("Maximum CP gained per CP spent, up to add_cp for the level.").defineInRange("maxcp_incr_rate", 0.0025, 0.0, 10.0);
        MAXO_INCR_RATE = b.comment("Maximum overload gained per overload taken, up to add_overload for the level.").defineInRange("maxo_incr_rate", 0.0058, 0.0, 10.0);
        INIT_CP = b.comment("Initial maximum CP for levels 0 to 5.").defineList("init_cp", LEGACY_INIT_CP, AcademyConfig::levelValue);
        ADD_CP = b.comment("Most maximum CP use can add, for levels 0 to 5.").defineList("add_cp", LEGACY_ADD_CP, AcademyConfig::levelValue);
        INIT_OVERLOAD = b.comment("Initial maximum overload for levels 0 to 5.").defineList("init_overload", LEGACY_INIT_OVERLOAD, AcademyConfig::levelValue);
        ADD_OVERLOAD = b.comment("Most maximum overload use can add, for levels 0 to 5.").defineList("add_overload", LEGACY_ADD_OVERLOAD, AcademyConfig::levelValue);
        b.pop().push("category");
        for (var category : List.of("meltdowner", "teleporter", "vecmanip")) {
            b.push(category);
            CATEGORY_PROGRESS.put(category, b.comment("Level progress rate for this category.").defineInRange("prog_incr_rate", 1.0, 0.0, 100.0));
            b.pop();
        }
        b.comment("Which entities Vector Deviation and Vector Reflection affect.").push(List.of("vecmanip", "affected_entities"));
        AFFECTED_DIFFICULTIES = b.comment("Entity difficulties as name=difficulty; the difficulty scales the CP and experience of deflecting",
                        "it, and is 1.0 for entities not named. As in 1.12.2, only the first entry naming a known entity is used.")
                .defineListAllowEmpty(List.of("difficulties"), LEGACY_AFFECTED_DIFFICULTIES, AcademyConfig::difficultyEntry);
        AFFECTED_EXCLUDED = b.comment("Entities never affected, by name. \"living\" is every living entity and \"mob\" every monster.",
                        "1.12.2 names such as xp_bottle are accepted.")
                .defineListAllowEmpty(List.of("excluded"), LEGACY_AFFECTED_EXCLUDED, value -> value instanceof String s && !s.isBlank() && s.length() <= 256);
        b.pop(2);
        b.pop().comment("Each toggled skill's maintenance, per docs/TOGGLE_MAINTENANCE_CONTRACT_JA.md (not legacy's).",
                "While on, a skill pays each tick the CP the caster's natural recovery would bring that tick, plus",
                "this value times (1 - proficiency): at full proficiency and default settings one mode alone",
                "holds CP exactly level. Several modes add up; the recovery is counted once. The skill's",
                "cp_consume_speed scales the whole of it, so values other than 1.0 leave the balance; where",
                "recovery is 0 the standard recovery rate is charged instead. 0 charges the recovery alone.")
                .push("toggle_upkeep");
        for (var entry : DEFAULT_TOGGLE_UPKEEP.entrySet())
            TOGGLE_UPKEEP.put(entry.getKey(), b.comment("CP a tick at 0% proficiency, above the recovery.")
                    .defineInRange(entry.getKey(), entry.getValue(), 0.0, 1000.0));
        b.pop().comment("Body Intensify as a toggle (user-approved, not legacy's; docs/BODY_INTENSIFY_MASTERY_SPEC_JA.md).")
                .push("body_intensify");
        BODY_WARMUP_TICKS = b.comment("Ticks from the key to the buffs for proficiency below 25%, 50%, 75%, 100% and at 100%,",
                        "each paid lerp(20, 15, proficiency) CP. Fixed when the key is accepted. At least 1.")
                .defineList("warmup_ticks", DEFAULT_BODY_WARMUP, value -> value instanceof Integer i && i >= 1 && i <= 200);
        BODY_EXTRA_EXHAUSTION = b.comment("Food exhaustion added each tick while the buffs are on (0.005 = 0.1 a second at 20 TPS;",
                        "creative and spectator players are exempt as from all exhaustion). 0 adds none.")
                .defineInRange("extra_exhaustion", 0.005, 0.0, 1.0);
        b.pop().push("skill");
        for (var name : SKILL_NAMES) {
            b.push(name);
            SKILLS.put(name, new SkillValues(
                    b.comment("Whether the skill can be learned.").define("enabled", true),
                    b.comment("A scale of this skill's damage to entities.").defineInRange("damage_scale", 1.0, 0.0, 100.0),
                    b.comment("How fast this skill consumes CP.").defineInRange("cp_consume_speed", 1.0, 0.0, 100.0),
                    b.comment("How fast this skill overloads.").defineInRange("overload_consume_speed", 1.0, 0.0, 100.0),
                    b.comment("How fast this skill's experience increases.").defineInRange("exp_incr_speed", 1.0, 0.0, 100.0),
                    // 原作Skill.shouldDestroyBlocks: すべての技能でgetOptionalBool("destroy_blocks", true)。
                    b.comment("Whether this skill may break blocks. False also overrides a dimension allowlist entry.").define("destroy_blocks", true)));
            b.pop();
        }
        b.pop(2);
        SPEC = b.build();
    }
    private AcademyConfig() { }
    public static boolean canDestroy(Level level, ResourceLocation skill) {
        return (!skill.equals(io.github.pinchan4273.reacademycraft.skill.Railgun.ID) || RAILGUN_DESTROY_BLOCKS.get())
                && (!skill.equals(io.github.pinchan4273.reacademycraft.skill.Meltdowner.ID) || MELTDOWNER_DESTROY_BLOCKS.get())
                && skillDestroys(skill)
                && (DESTROY_BLOCKS.get() || DESTRUCTION_DIMENSIONS.get().contains(level.dimension().location().toString()));
    }
    public static double progressionMultiplier(ResourceLocation category) {
        if (!loaded()) return 1;
        double categoryRate = io.github.pinchan4273.reacademycraft.skill.ArcGen.CATEGORY.equals(category) ? ELECTROMASTER_EXPERIENCE_MULTIPLIER.get()
                : category == null || !CATEGORY_PROGRESS.containsKey(category.getPath()) ? 1.0 : CATEGORY_PROGRESS.get(category.getPath()).get();
        return SKILL_EXPERIENCE_MULTIPLIER.get() * categoryRate;
    }
    /**
     * Forgeはmodの読み込み中、どのワールドよりも先にcommon設定を読む。それまでは原作の既定値が使われる。
     * （これがサーバー設定だった頃は、サーバーへ参加したクライアントにログインのhandshakeでサーバーのファイルが送られていた。
     * common設定は送られないので、クライアントは代わりにSyncedAcademyRulesを読む。）
     */
    public static boolean loaded() { return SPEC.isLoaded(); }
    private static boolean difficultyEntry(Object value) {
        if (!(value instanceof String s) || s.length() > 256) return false;
        int at = s.lastIndexOf('=');
        if (at <= 0) return false;
        try { double d = Double.parseDouble(s.substring(at + 1).trim()); return Double.isFinite(d) && d >= 0; }
        catch (NumberFormatException e) { return false; }
    }
    public static List<? extends String> affectedDifficulties() { return loaded() ? AFFECTED_DIFFICULTIES.get() : LEGACY_AFFECTED_DIFFICULTIES; }
    public static List<? extends String> affectedExcluded() { return loaded() ? AFFECTED_EXCLUDED.get() : LEGACY_AFFECTED_EXCLUDED; }
    private static boolean levelValue(Object value) { return value instanceof Double d && Double.isFinite(d) && d >= 0; }
    private static float level(ForgeConfigSpec.ConfigValue<List<? extends Double>> list, List<Double> legacy, int level) {
        var values = loaded() ? list.get() : legacy;
        if (values.size() < 6) values = legacy;
        return values.get(Math.max(0, Math.min(5, level))).floatValue();
    }
    public static float initCp(int level) { return level(INIT_CP, LEGACY_INIT_CP, level); }
    public static float addCp(int level) { return level(ADD_CP, LEGACY_ADD_CP, level); }
    public static float initOverload(int level) { return level(INIT_OVERLOAD, LEGACY_INIT_OVERLOAD, level); }
    public static float addOverload(int level) { return level(ADD_OVERLOAD, LEGACY_ADD_OVERLOAD, level); }
    public static int cpRecoverCooldown() { return loaded() ? CP_RECOVER_COOLDOWN.get() : 15; }
    public static int overloadRecoverCooldown() { return loaded() ? OVERLOAD_RECOVER_COOLDOWN.get() : 32; }
    public static float maxCpIncrRate() { return loaded() ? MAXCP_INCR_RATE.get().floatValue() : .0025f; }
    public static float maxOverloadIncrRate() { return loaded() ? MAXO_INCR_RATE.get().floatValue() : .0058f; }
    @javax.annotation.Nullable private static SkillValues skill(ResourceLocation id) {
        return id == null || !loaded() || !"academy".equals(id.getNamespace()) ? null : SKILLS.get(id.getPath());
    }
    public static boolean skillEnabled(ResourceLocation id) { var s = skill(id); return s == null || s.enabled().get(); }
    public static double skillDamageScale(ResourceLocation id) { var s = skill(id); return s == null ? 1 : s.damageScale().get(); }
    public static double skillExpSpeed(ResourceLocation id) { var s = skill(id); return s == null ? 1 : s.expIncrSpeed().get(); }
    public static double skillCpSpeed(ResourceLocation id) { var s = skill(id); return s == null ? 1 : s.cpConsumeSpeed().get(); }
    public static boolean skillDestroys(ResourceLocation id) { var s = skill(id); return s == null || s.destroyBlocks().get(); }
    /**
     * 術者にとってそのブロックでcanDestroyか: 地形を壊す技能がすべて従うスポーン保護と建築権限も含む
     * （RailgunとMineRayの削りは以前から従っていた）。
     */
    public static boolean canDestroy(net.minecraft.server.level.ServerPlayer caster, net.minecraft.core.BlockPos pos, ResourceLocation skill) {
        var level = caster.level();
        return canDestroy(level, skill) && caster.mayBuild() && level.mayInteract(caster, pos);
    }
    public static double skillOverloadSpeed(ResourceLocation id) { var s = skill(id); return s == null ? 1 : s.overloadConsumeSpeed().get(); }
    /** 切り替え型技能の、熟練度0%での回復を上回る維持コスト。無い技能は0。 */
    public static float toggleUpkeep(ResourceLocation id) {
        if (id == null || !"academy".equals(id.getNamespace())) return 0;
        var fallback = DEFAULT_TOGGLE_UPKEEP.get(id.getPath());
        if (fallback == null) return 0;
        var value = loaded() ? TOGGLE_UPKEEP.get(id.getPath()) : null;
        return (value == null ? fallback : value.get()).floatValue();
    }
    /** 熟練度帯0（25%未満）〜4（100%）ごとのBody Intensifyの準備時間。 */
    public static int bodyWarmupTicks(int band) {
        List<? extends Integer> values = loaded() ? BODY_WARMUP_TICKS.get() : DEFAULT_BODY_WARMUP;
        if (values.size() != 5) values = DEFAULT_BODY_WARMUP;
        return Math.max(1, Math.min(200, values.get(Math.max(0, Math.min(4, band)))));
    }
    public static float bodyExtraExhaustion() { return loaded() ? BODY_EXTRA_EXHAUSTION.get().floatValue() : .005f; }
}
