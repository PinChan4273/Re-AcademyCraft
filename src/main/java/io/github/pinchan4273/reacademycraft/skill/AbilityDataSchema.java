package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * プレイヤーの能力データの保存形式（schema 2）の名前。根の下に、原作のデータ部品ごとの区分を置く:
 * AbilityData（カテゴリ・レベル・学んだ技能と熟練度）、CPData（CPとオーバーロード）、PresetData（4つのプリセット）、
 * CooldownData（技能ごとのクールダウン）。区分の中の名前は原作の各部品のフィールド名に合わせる。
 * 学んだ技能はIDと熟練度の組のリスト、プリセットは4×4の技能IDの表（空きは""）で、これはこのmodの形。
 * 区分の中の知らないキーも、根の知らないキーも、保存のたびにそのまま残す。
 */
public final class AbilityDataSchema {
    public static final int VERSION = 2;
    public static final String VERSION_KEY = "schema_version";

    public static final String ABILITY = "AbilityData";
    public static final String CATEGORY = "category";
    public static final String LEVEL = "level";
    public static final String LEVEL_EXP = "expAddedThisLevel";
    public static final String SKILLS = "skills";
    public static final String SKILL = "skill";
    public static final String EXP = "exp";

    public static final String CP = "CPData";
    public static final String ACTIVATED = "activated";
    public static final String CUR_CP = "curCP";
    public static final String MAX_CP = "maxCP";
    public static final String ADD_MAX_CP = "addMaxCP";
    public static final String CUR_OVERLOAD = "curOverload";
    public static final String MAX_OVERLOAD = "maxOverload";
    public static final String ADD_MAX_OVERLOAD = "addMaxOverload";
    /** 原作CPData.overloadFine: オーバーロードの錠が掛かっていないこと。 */
    public static final String OVERLOAD_FINE = "overloadFine";
    public static final String UNTIL_RECOVER = "untilRecover";
    public static final String UNTIL_OVERLOAD_RECOVER = "untilOverloadRecover";

    public static final String PRESET = "PresetData";
    public static final String PRESET_ID = "presetID";
    public static final String PRESETS = "presets";

    public static final String COOLDOWN = "CooldownData";

    private AbilityDataSchema() { }

    /** 根の区分。無い（または区分でない値がある）ときは空の区分を置いて返す。返した区分への変更は根に残る。 */
    public static CompoundTag section(CompoundTag root, String name) {
        if (!root.contains(name, Tag.TAG_COMPOUND)) root.put(name, new CompoundTag());
        return root.getCompound(name);
    }
}
