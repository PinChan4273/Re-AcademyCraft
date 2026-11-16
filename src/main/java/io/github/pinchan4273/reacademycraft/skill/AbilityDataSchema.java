package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * プレイヤーの能力データの保存形式（schema 2）の名前。ルートの下に、原作のデータ部品ごとの区分を置く:
 * AbilityData（カテゴリ・レベル・学んだ技能と熟練度）、CPData（CPとオーバーロード）、PresetData（4つのプリセット）、
 * CooldownData（技能ごとのクールダウン）。区分の中の名前は原作の各部品のフィールド名に合わせる。
 * 学んだ技能はIDと熟練度の組のリスト、プリセットは4×4の技能IDの表（空きは""）で、これはこのmodの形。
 * 区分の中の知らないキーも、ルートの知らないキーも、保存のたびにそのまま残す。
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
    /** 原作CPData.overloadFine: オーバーロードのロックが掛かっていないこと。 */
    public static final String OVERLOAD_FINE = "overloadFine";
    public static final String UNTIL_RECOVER = "untilRecover";
    public static final String UNTIL_OVERLOAD_RECOVER = "untilOverloadRecover";
    /**
     * CPの容量の規則の版（このmodの付記）。無いデータは1.0.0の規則（コースが最大CPを増やす）で保存されたもので、読むときに一度だけ
     * 新しい容量へ移す（PlayerAbilityData.migrateCapacity）。schemaの版とは別: 1.0.0が読んでも知らないキーとして残る。
     */
    public static final String CP_MODEL = "capacityModel";
    public static final int CURRENT_CP_MODEL = 2;

    public static final String PRESET = "PresetData";
    public static final String PRESET_ID = "presetID";
    public static final String PRESETS = "presets";

    public static final String COOLDOWN = "CooldownData";

    /**
     * プレイヤーが選ぶ技能の設定（このmodの付記）。schemaの版は変えない: これより前の版は知らない区分として残し、
     * 知らない設定のキーや知らない値も、この版は書き換えずに残す（プレイヤーが同じ設定を変えたときだけ、その値を書く）。
     */
    public static final String SETTINGS = "SkillSettings";
    /** v1.5.0 CPセーフティ（SETTINGSの中。全系統共通で、系統の変更でも残る）。 */
    public static final String CP_SAFETY = "CpSafety", CP_SAFETY_PERCENT = "CpSafetyPercent";
    /** v1.5.0: 甲虫の個体の状態（活動中の写しと収納中のもの）。ルートに置くリスト。無ければ書かない。 */
    public static final String BEETLE_RECORDS = "DarkMatterBeetles";
    /** v1.5.0: 甲虫の設定（SkillSettings区分の中）。既定の値なら書かない。 */
    public static final String BEETLE_CONTACT_BREAK = "beetleContactBreak", BEETLE_REPAIR = "beetleRepair", BEETLE_STANCE = "beetleStance";
    /** v1.5.0: 甲虫の追従開始距離（ブロック、int。SkillSettings区分の中）。既定の32なら書かない。 */
    public static final String BEETLE_FOLLOW = "beetleFollow";
    /** v1.5.0: 甲虫が自動で狙う分類（{@link ElectronMissileTargetKind}の名前→真偽）。無い分類は既定。 */
    public static final String BEETLE_TARGETS = "beetleTargets";
    /** v1.5.0: 天界発現の設定（SkillSettings区分の中の区分。{@link HeavenSettings#save}）。全部既定なら書かない。 */
    public static final String HEAVEN_SETTINGS = "heaven";
    /** v1.5.0: 未元物質人間か（CPDataの中。通常人間では書かない）。CPDataに置くので、残CPと一緒に保存・同期する。 */
    public static final String DARK_BODY = "darkBody";
    /** 未元物質人間の未修復の身体損傷（CP。0なら書かない）。 */
    public static final String BODY_DAMAGE = "bodyDamage";
    /** v1.5.0: 死亡後の復活形態が通常人間か（SkillSettings区分の中。既定の未元物質人間なら書かない）。 */
    public static final String DARK_BODY_RESPAWN = "darkBodyRespawn";
    /** 白翼飛行の飛び立ちと停止の操作が連打か（真偽。無ければ長押し）。 */
    public static final String FLIGHT_DOUBLE_TAP = "flightDoubleTap";
    /**
     * 1.5.2: CPセーフティの優先順位（SETTINGSの中。プリセットのインデックス0〜3を後ろに付けた名前ごとに、{@link SafetyPriority#encode}の
     * 文字列。既定の並びなら書かない）と、未元物質人間の形態の予約（最大CPに対する整数の%。既定なら書かない）。
     */
    public static final String CP_SAFETY_ORDER = "CpSafetyOrder", FORM_RESERVE = "FormReservePercent";
    /** Light Shieldの盾の向きの選び方（{@link LightShieldMode#id()}、"auto"か"manual"）。無ければ{@link LightShieldMode#DEFAULT}。 */
    public static final String LIGHT_SHIELD_MODE = "lightShieldMode";
    /** Electron Missileの照準のモード（{@link ElectronMissileAimMode#id()}、"auto"・"semi"）。無ければ{@link ElectronMissileAimMode#DEFAULT}。 */
    public static final String ELECTRON_MISSILE_AIM = "electronMissileAim";
    /**
     * Electron Missileの攻撃対象のフィルター: 区分の中に、プレイヤーが選んだ分類（{@link ElectronMissileTargetKind#id()}）だけを
     * 真偽（byte）で書く。無い分類は{@link ElectronMissileTargetKind#defaultOn()}。知らないキーはそのまま残す。
     */
    public static final String ELECTRON_MISSILE_TARGETS = "electronMissileTargets";
    /**
     * 1.5.2: ベクトル操作の解析度。ルートに置く区分で、分類のキー（{@link VectorAnalysis}）→ 区分{v: 解析度(float), n: 表示の名前(任意)}。
     * 空なら書かない。系統を失っても残す。schemaの版は変えない（これより前の版は知らない区分として残す）。
     */
    public static final String VECTOR_ANALYSIS = "VectorAnalysis";
    public static final String ANALYSIS_VALUE = "v", ANALYSIS_LABEL = "n";
    /**
     * 1.5.2: パッシブ（自動で起動）に切り替えた技能のIDの一覧（SkillSettings区分の中の文字列のリスト）。空なら書かない。
     * 読むときは、習得済みで切り替えられる技能（{@link PassiveSkills#switchable}）だけを受け取る。schemaの版は変えない。
     */
    public static final String PASSIVE_SKILLS = "passiveSkills";
    /**
     * 1.5.2: ベクトル反射の反射方向（{@link ReflectionDirection#id()}）と、自動の2方式でPvPの相手プレイヤーを候補に含めるか（真偽）。
     * どちらもSkillSettings区分の中で、既定の値（攻撃者へ・含めない）なら書かない。旧セーブ（キー無し）と知らない値は既定として読む。
     * 系統を失っても保持する。schemaの版は変えない。
     */
    public static final String REFLECTION_DIRECTION = "reflectionDirection", REFLECTION_PVP = "reflectionPvp";

    private AbilityDataSchema() { }

    /** ルートの区分。無い（または区分でない値がある）ときは空の区分を置いて返す。返した区分への変更はルートに残る。 */
    public static CompoundTag section(CompoundTag root, String name) {
        if (!io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(root, name, Tag.TAG_COMPOUND)) root.put(name, new CompoundTag());
        return root.getCompoundOrEmpty(name);
    }
}
