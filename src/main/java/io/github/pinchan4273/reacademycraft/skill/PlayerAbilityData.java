package io.github.pinchan4273.reacademycraft.skill;

import static io.github.pinchan4273.reacademycraft.skill.AbilityDataSchema.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * プレイヤーの能力の状態。原作のAbilityData（カテゴリ・レベル・学んだ技能）、CPData（CP・オーバーロード・回復の待ち）、
 * PresetData、CooldownDataを1つにまとめたもの。保存の形は{@link AbilityDataSchema}。
 * 状態・学習・原作のCPの仕組みはサーバーを正とする。
 * ゲームプレイ上の正となる呼び出し元は、サーバーのプレイヤーのインスタンスを変更しなければならない。
 */
public final class PlayerAbilityData {
    public static final int SCHEMA_VERSION = AbilityDataSchema.VERSION;
    // 保存の既定値は1.12.2のCPDataのもの。
    public static final float DEFAULT_MAX_CP = 100.0f;
    public static final float DEFAULT_MAX_OVERLOAD = 100.0f;

    private @Nullable Identifier ability;
    private int level;
    private float cp;
    private float maxCp = DEFAULT_MAX_CP;
    private float overload;
    private float maxOverload = DEFAULT_MAX_OVERLOAD;
    private boolean active;
    private CompoundTag preserved = new CompoundTag();
    private boolean readOnly;
    public static final int MAX_SKILLS = 256;
    public static final int PRESET_COUNT = 4;
    public static final int SLOT_COUNT = 4;
    private final Map<Identifier, Float> skills = new LinkedHashMap<>();
    private final Map<Identifier, Integer> cooldowns = new LinkedHashMap<>();
    private final Identifier[][] presets = new Identifier[PRESET_COUNT][SLOT_COUNT];
    private int currentPreset;
    private int cpRecoveryDelay;
    private int overloadRecoveryDelay;
    /**
     * 1.5.2: 過負荷の回復待ちの間に残る回復の割合（{@link #overloadWaitRecovery}）。待ちを作った支払いの技能の熟練度で決まり、
     * 待ちが重なったときは低い方を使う。待ちが0なら使わない。保存しない（読み込んだ直後に待ちが残っていれば、割合を0として扱い、その待ちの間は回復しない）。
     */
    private float overloadWaitRate;
    /** 1.5.2: 能力の飛行で空中にいるか（サーバーが毎tickの精算の前に置く。保存しない）。 */
    private boolean airborneFlight;
    /**
     * 1.5.2（ロックからの戻し方）: ロック中に、優先順位の順で再稼働を許した技能（{@link PassiveSkills}が置く）。
     * この技能だけは、ロック中でも開始・継続できる（維持だけ。稼働の払いは{@link #payAction}・{@link #chargeActionOverload}が断る）。
     * ロックの始まりと解除で空にする。保存も同期もしない。
     */
    private final java.util.Set<Identifier> lockAllowed = new java.util.HashSet<>();
    /** 1.5.2: 能力の飛行が動いているか（地上を含む。ストームウィングの溜めは含まない）。動いている間は形態の維持を払わない。保存しない。 */
    private boolean abilityFlight;
    /**
     * 1.5.2: 技能ごとの稼働の残りtick。稼働の払い（{@link #payAction}・{@link #chargeActionOverload}）で置き、毎tick減らす。
     * 1つでも残っている間は過負荷が回復しない。保存も同期もしない（読み込み直後は稼働していない）。
     */
    private final Map<Identifier, Integer> actionLeft = new LinkedHashMap<>();
    /**
     * 1.5.2: 天界発現の再生を、未元物質人間は CP の回復の上乗せとして受ける。
     * 基準の回復（{@link #g0PerTick}）に対する割合と、残りtick。G0 には足さない別枠。保存も同期もしない（読み込み直後は無い）。
     */
    private float heavenCpShare;
    private int heavenCpLeft;
    private boolean overloadLocked;
    private float bonusCp;
    private float bonusOverload;
    private float levelExperience;
    /**
     * Light Shieldのモード。nullは「プレイヤーが選んでいない」で、{@link LightShieldMode#DEFAULT}として動く。選んでいないときは
     * 保存で何も書かないので、読んだときにあった知らない値（新しい版のモードなど）はそのまま残る。
     */
    private @Nullable LightShieldMode lightShieldMode;
    /** Electron Missileの照準のモード。nullは「選んでいない」で、{@link ElectronMissileAimMode#DEFAULT}（AUTO）として動く。 */
    private @Nullable ElectronMissileAimMode electronMissileAimMode;
    /** Electron Missileの攻撃対象のフィルターのうち、プレイヤーが選んだもの。無い分類は既定（{@link ElectronMissileTargetKind#defaultOn()}）。 */
    private final java.util.EnumMap<ElectronMissileTargetKind, Boolean> electronMissileTargets = new java.util.EnumMap<>(ElectronMissileTargetKind.class);
    /** v1.5.0: 甲虫の接触ブロック破壊（初期ON）、修復方式、攻撃方針。系統を失っても保持する。 */
    private boolean beetleContactBreak = true;
    private DarkMatterBeetles.Repair beetleRepair = DarkMatterBeetles.Repair.AUTO;
    private DarkMatterBeetles.Stance beetleStance = DarkMatterBeetles.Stance.MID;
    /** v1.5.0: 甲虫の追従開始距離（ブロック）。攻撃方針と同じく、系統を失っても保持する。 */
    private int beetleFollow = DarkMatterBeetles.FOLLOW_DEFAULT;
    /** v1.5.0: 甲虫が自動で狙う分類のうち、プレイヤーが選んだもの。無い分類は既定（敵対だけON）。系統を失っても保持する。 */
    private final java.util.EnumMap<ElectronMissileTargetKind, Boolean> beetleTargets = new java.util.EnumMap<>(ElectronMissileTargetKind.class);
    /** v1.5.0: 甲虫の個体の記録（{@link DarkMatterBeetles}が書く）。ログアウトと再起動を越えて残す。系統を失うと消える。 */
    private ListTag beetleRecords = new ListTag();
    /** v1.5.0: 未元物質人間か。系統を失うと解ける。保存する。 */
    private boolean darkBody;
    /** v1.5.0: 死亡後の復活形態（trueは未元物質人間で、初期値）。系統を失っても保持する。 */
    private boolean darkBodyRespawn = true;
    /** 白翼飛行の飛び立ちと停止の操作が連打か（既定は長押し）。系統を失っても保持する。 */
    private boolean flightDoubleTap;
    /** 1.5.2: ベクトル反射の反射方向（既定は攻撃者へ）。系統を失っても保持する。 */
    private ReflectionDirection reflectionDirection = ReflectionDirection.DEFAULT;
    /** 1.5.2: 反射方向の自動の2方式で、PvPの相手プレイヤーを候補に含めるか（既定は含めない）。系統を失っても保持する。 */
    private boolean reflectionPvp;
    /** 系統の喪失の直前のCPの残量率（{@code DarkBody#category}が受け取る）。無ければ負。保存しない。 */
    private float darkBodyLossRate = -1;
    /** v1.5.0: 天界発現の設定。系統を失っても保持する。領域そのものは保存しない。 */
    private HeavenSettings heavenSettings = new HeavenSettings();
    /**
     * 未元物質人間の未修復の身体損傷（CP）。身体に通った最終ダメージの分だけ増え、
     * 継続の収支が0以上の tick だけ、毎秒{@link #BODY_REPAIR_PER_SECOND}CPまで修復してCPへ戻す（1.5.2）。CP不足の内訳で、
     * 通常の自然回復はこの分を埋めない（上限は最大CP−損傷）。
     * 明示的なCP回復で損傷の分まで戻ったら、その分だけ損傷も減る（常に損傷 ≤ 最大CP−現在CP にそろえる）。
     */
    private float bodyDamage;
    /** 身体へ通った損傷の換算（1 HP あたりの CP。1.5.2）。 */
    public static final float BODY_CP_PER_HP = 10;
    /** 身体修復の速さの上限（CP/秒 ＝ 1 HP/秒。1.5.2）。 */
    public static final float BODY_REPAIR_PER_SECOND = 10;
    /**
     * 未元物質人間の形態の維持の、G0 に対する割合（1.5.2）。熟練度に依らない。
     * サーバーの設定の skill.dark_matter_body.cp_consume_speed を掛ける（{@link #formDue}）。
     */
    public static final float FORM_UPKEEP_SHARE = .3f;
    /** 形態の維持を払わない（クリエイティブ。サーバーが毎tickの精算の前に置く。保存しない）。 */
    private boolean formExempt;
    /**
     * 1.5.2: ベクトル操作の解析度。分類のキー（{@link VectorAnalysis.Category#key()}）→ 0〜1。0の分類と、いつも100%の
     * バニラの物理は持たない。保存する（系統を失っても残す）。作り直し（Clone）は{@link #copyFrom}の保存と読み込みで引き継ぐ。
     */
    private final Map<String, Float> analysis = new LinkedHashMap<>();
    /** 解析度の分類の表示の名前（未元物質の使い手の名前など。無い分類は持たない）。 */
    private final Map<String, String> analysisLabels = new LinkedHashMap<>();
    /** 解析度が変わった回数（同期の判定用。保存しない）。 */
    private int analysisVersion;
    /**
     * 1.5.2: パッシブ（能力使用モードで自動で起動）に切り替えた技能。習得済みの、切り替えられる技能（{@link PassiveSkills#switchable}）だけ。
     * 保存する。パッシブの間はどのプリセットのスロットにも置けない。系統を変える・技能を忘れると外れる。
     */
    private final java.util.Set<Identifier> passiveSkills = new java.util.LinkedHashSet<>();
    /** パッシブで起動している技能の数（{@link PassiveSkills}がサーバーで毎tick置く。保存しない）。 */
    private int passiveRunning;
    /** 持つ解析度の分類の上限。超えたら、いちばん低い分類から忘れる。 */
    public static final int MAX_ANALYSIS_ENTRIES = 256;
    /**
     * このデータが属するサーバーのプレイヤー（原作の能力イベント用）。どのプレイヤーにも属さないデータ（写し、テスト専用のもの）や
     * クライアントにあるデータではnullで、その場合は何も送らない。
     */
    private @Nullable net.minecraft.world.entity.player.Player owner;
    public void bindOwner(net.minecraft.world.entity.player.Player player) { owner = player; }
    private boolean posting() { return owner != null && !owner.level().isClientSide(); }
    private void post(java.util.function.Function<net.minecraft.world.entity.player.Player, net.neoforged.bus.api.Event> event) {
        if (posting()) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event.apply(owner));
    }
    /** 原作CalcEvent.calc: リスナーが残した値。誰も聞いていなければ値そのもの。 */
    private float calc(java.util.function.BiFunction<net.minecraft.world.entity.player.Player, Float, io.github.pinchan4273.reacademycraft.event.AbilityEvent.Calc> event, float value) {
        if (!posting()) return value;
        var calc = event.apply(owner, value);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(calc);
        return Float.isFinite(calc.value) ? Math.max(0, calc.value) : value;
    }
    /**
     * 原作getInitCP / getInitOverload: レベルの値（オーバーロードはコースのボーナスを加える）をイベントに通したもの。
     * 1.0.0の後の仕様では、コースは最大CPを増やさない。
     */
    private float initialCp() { return calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.MaxCP::new, io.github.pinchan4273.reacademycraft.config.AcademyConfig.initCp(level)); }
    private float initialOverload() { return calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.MaxOverload::new, io.github.pinchan4273.reacademycraft.config.AcademyConfig.initOverload(level) + GenericSkills.overloadBonus(this)); }

    public @Nullable Identifier getAbility() { return ability; }
    public boolean hasAbility() { return ability != null; }
    public int getLevel() { return level; }
    public float getLevelExperience() { return levelExperience; }
    /** 未移植の技能を含め、原作の操作可能な技能をすべて数える。 */
    public float getLevelProgress() {
        AbilityCategory category = AbilityCategory.find(ability);
        if (category == null || level < 1) return 0;
        float threshold = category.legacyControllable(level) * (level == 4 ? 1.333f : .666f);
        return threshold == 0 ? 1 : Math.min(1, levelExperience / threshold);
    }
    public boolean canLevelUp() { return !readOnly && level < 5 && getLevelProgress() == 1; }
    public float getCp() { return cp; }
    public float getMaxCp() { return maxCp; }
    /** このレベルのプレイヤーが最初に持つCP。原作の導出した熟練度はこれで割る。 */
    public static float initialCp(int level) { return io.github.pinchan4273.reacademycraft.config.AcademyConfig.initCp(level); }
    /** このレベルのプレイヤーが最初に持つオーバーロード。 */
    public static float initialOverload(int level) { return io.github.pinchan4273.reacademycraft.config.AcademyConfig.initOverload(level); }
    public float getOverload() { return overload; }
    public float getMaxOverload() { return maxOverload; }
    public boolean isActive() { return active; }
    public boolean isReadOnly() { return readOnly; }

    public void setAbility(@Nullable Identifier value) {
        requireWritable();
        if (Objects.equals(ability, value)) return;
        boolean wasActive = active; int wasLevel = level;
        loads++; clearSkills();
        levelExperience = 0;
        ability = value;
        // 形態を解く。下の再計算でCPが満たされるので、復帰HPのための残量率は今読む。
        if (darkBody && !DarkMatter.CATEGORY.equals(value)) { darkBody = false; bodyDamage = 0; darkBodyLossRate = maxCp > 0 ? cp / maxCp : 0; }
        if (!DarkMatter.CATEGORY.equals(value)) beetleRecords = new ListTag(); // 系統を失ったら収納個体も回収する。
        level = value == null ? 0 : Math.max(1, level);
        if (value == null) active = false;
        // 原作AbilityData.setCategoryはレベルを直接設定し、CategoryChangeEventだけを送る。CPDataはそれに応じて最大値を計算し直し、
        // 使用で加わった分を保つ: それを消すのはsetLevelのLevelChangeEventだけで、カテゴリの変更はそれを送らない。
        recalculateLimits(true);
        post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CategoryChange::new);
        if (wasActive && !active) post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Deactivate::new);
    }

    public void setLevel(int value) {
        requireWritable();
        int next = hasAbility() ? Math.max(1, Math.min(5, value)) : 0;
        if (level != next) { level = next; levelExperience = 0; recalculateLimits(false); post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.LevelChange::new); }
    }

    /**
     * 通常の昇級（能力開発機のレベルアップ）。育てた最大CPを昇級で減らさない: 昇級後の最大CP = max(新レベルの基礎, 昇級直前の最大CP)、
     * 新レベルの成長済みCP = 昇級後の最大CP − 新レベルの基礎（新レベルの成長枠まで）。成長分を新しい基礎へそのまま足すのではない。
     * CPを満たし、オーバーロードとその成長を戻すのは従来の昇級のまま。上げないとき（同じ・下げる）はsetLevelと同じで、
     * 降級・コマンドによるレベルの設定・カテゴリの変更にはこの規則を使わない。
     */
    public void advanceLevel(int value) {
        requireWritable();
        int next = hasAbility() ? Math.max(1, Math.min(5, value)) : 0;
        if (next <= level) { setLevel(value); return; }
        float before = maxCp;
        level = next; levelExperience = 0;
        recalculateLimits(false);
        float base = maxCp;
        bonusCp = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level), before - base));
        maxCp = base + bonusCp; cp = maxCp;
        // 明示的にCPが増えたら、その場で身体損傷を減らす（同じtickの後の支払いで損傷が戻らないように）。
        clampBodyDamage();
        post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.LevelChange::new);
    }

    public void setMaxCp(float value) {
        requireWritable();
        maxCp = requireNonnegativeFinite(value);
        cp = Math.min(cp, maxCp);
        clampBodyDamage();
    }

    public void setCp(float value) {
        requireWritable();
        cp = Math.min(requireNonnegativeFinite(value), maxCp);
        // 明示的にCPが増えたら、その場で身体損傷を減らす（同じtickの後の支払いで損傷が戻らないように）。
        clampBodyDamage();
    }

    public void setMaxOverload(float value) {
        requireWritable();
        maxOverload = requireNonnegativeFinite(value);
        overload = Math.min(overload, maxOverload);
    }

    public void setOverload(float value) {
        requireWritable();
        overload = Math.min(requireNonnegativeFinite(value), maxOverload);
    }

    public void setActive(boolean value) {
        requireWritable();
        boolean was = active;
        active = hasAbility() && value;
        if (active != was) post(active ? io.github.pinchan4273.reacademycraft.event.AbilityEvent.Activate::new : io.github.pinchan4273.reacademycraft.event.AbilityEvent.Deactivate::new);
    }

    /**
     * 原作CooldownDataはsetNBTStorageを呼ばないので、LambdaLib2のEntityDataはそれを保存も、複製したプレイヤーへの写しもしない:
     * クールダウンはログイン・死亡・エンドからの帰還の後に消える。
     */
    public void forgetCooldowns() {
        if (readOnly) return;
        cooldowns.clear();
    }
    /** 原作CPDataの死亡時の回復。死亡後の置き換えにだけ適用する。 */
    public void recoverAfterDeath() {
        if (readOnly) return; // 未対応のデータをNBTのレベルでバイト単位で保つ。
        active = false;
        cooldowns.clear(); cpRecoveryDelay = overloadRecoveryDelay = 0; overloadLocked = false; lockAllowed.clear();
        if (hasAbility()) {
            cp = maxCp;
            overload = 0;
        }
        bodyDamage = 0;
    }

    /**
     * 一晩寝た後の原作CPData.recoverAll: CPは満タン、オーバーロードは0。ただし回復の遅延は続き、overloadFineはfalseなので、
     * まだ数えているオーバーロードは能力をオフのまま保つ。
     */
    public void recoverAll() {
        if (readOnly || !hasAbility()) return;
        cp = maxCp; overload = 0; overloadLocked = true;
        clampBodyDamage();
    }

    public boolean hasLearned(Identifier skill) { return skills.containsKey(skill); }
    public float getProficiency(Identifier skill) { return skills.getOrDefault(skill, 0f); }
    public Map<Identifier, Float> learnedSkills() { return Map.copyOf(skills); }
    public int getCooldown(Identifier skill) { return cooldowns.getOrDefault(skill, 0); }
    public boolean isOverloadLocked() { return overloadLocked; }
    /**
     * 原作CPData.isOverloaded: オーバーロード中で、まだ回復のクールダウン内。その後は原作のisOverloadRecoveringだけ
     * （ここではisOverloadLocked）が、オーバーロードが無くなるまで続く。
     */
    public boolean isOverloaded() { return overloadLocked && overloadRecoveryDelay > 0; }

    /**
     * 原作CPDataの妨害: いずれかの発生源がまだ妨害していると言う間、プレイヤーは能力を一切使えない。発生源はサーバーにだけあり、
     * 保存しない。原作は妨害しているものが作り直すmapにそれを持ち、クライアントへ届くのはフラグだけ。
     */
    private final java.util.Map<String, java.util.function.BooleanSupplier> interferences = new java.util.LinkedHashMap<>();
    private boolean interfering;
    public boolean isInterfering() { return interfering; }
    public void addInterference(String id, java.util.function.BooleanSupplier lasts) {
        if (readOnly || id == null || lasts == null) return;
        interferences.put(id, lasts); interfering = true;
    }
    public boolean hasInterference(String id) { return interferences.containsKey(id); }
    /** 原作は妨害をやめた最初のtickに発生源を外す。フラグが変わったらtrueを返す。 */
    public boolean tickInterference() {
        interferences.values().removeIf(source -> !source.getAsBoolean());
        boolean now = !interferences.isEmpty();
        if (now == interfering) return false;
        interfering = now; return true;
    }
    /** クライアント側: サーバーが最後に送ったフラグ。 */
    public void setInterfering(boolean value) { interfering = value; }
    /** 原作canUseAbility: 有効で、オーバーロードしておらず、妨害されていない。 */
    public boolean canUseAbility() { return isActive() && !isOverloadLocked() && !interfering; }
    /** 1.5.2: その技能で能力を使えるか。ロック中でも、ロック中の再稼働を許した技能（{@link #lockBlocks}が false）なら使える。 */
    public boolean canUseAbility(@Nullable Identifier skill) { return isActive() && !lockBlocks(skill) && !interfering; }
    /** 1.5.2: その技能をロックが止めるか。ロック中で、ロック中の再稼働を許していなければ true。技能が分からなければロック中は true。 */
    public boolean lockBlocks(@Nullable Identifier skill) { return overloadLocked && (skill == null || !lockAllowed.contains(skill)); }
    /** 1.5.2: ロック中で、過負荷の回復の待ちが終わって回復している（減り始めた）か。 */
    public boolean isLockRecovering() { return overloadLocked && overloadRecoveryDelay == 0; }
    /** 1.5.2: ロック中の再稼働を許す（ロック中でなければ何もしない）。 */
    public void allowDuringLock(Identifier skill) { if (overloadLocked) lockAllowed.add(skill); }
    /** 1.5.2: ロック中の再稼働を許した技能か。 */
    public boolean allowedDuringLock(Identifier skill) { return overloadLocked && lockAllowed.contains(skill); }
    public int getCurrentPreset() { return currentPreset; }
    public Identifier getSlot(int preset, int slot) {
        checkSlot(preset, slot); return presets[preset][slot];
    }
    public void learn(Identifier skill) {
        requireWritable();
        if (!hasAbility() || skill == null || skill.toString().length() > 256
                || (!skills.containsKey(skill) && skills.size() >= MAX_SKILLS))
            throw new IllegalArgumentException("Invalid learned skill");
        boolean newlyLearned = skills.putIfAbsent(skill, 0f) == null;
        if (newlyLearned) {
            maxCp = initialCp() + bonusCp;
            maxOverload = initialOverload() + bonusOverload;
            // 原作SkillLearnEventはアクティブ技能の資源も更新する。
            // 消費の成長、タイマー、プリセット、既存の技能の進捗は保つ。
            cp = maxCp; overload = 0;
            clampBodyDamage();
            post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillLearn(p, skill));
        }
    }
    public void setProficiency(Identifier skill, float value) {
        requireWritable();
        if (!hasLearned(skill)) throw new IllegalArgumentException("Skill is not learned");
        skills.put(skill, Math.min(1, requireNonnegativeFinite(value)));
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpChanged(p, skill));
    }
    /**
     * 実際のゲームプレイでの獲得。原作addSkillExpは100%でも加工前の量を数える。
     * デバッグのsetProficiencyは、意図的にレベルの経験値を生まない。
     */
    public void addProficiency(Identifier skill, float amount) { gain(skill, amount, true); }
    /**
     * 熟練度だけの獲得（1.1.0）: モードを待機のまま維持している間の微量。技能の熟練度だけを増やし、能力レベルの経験値は
     * 増やさない（待機の放置でレベルが上がらないように）。それ以外（技能ごとの倍率、0〜1、イベント、読み取り専用・未習得・カテゴリの
     * 拒否）はaddProficiencyと同じ。
     */
    public void addProficiencyOnly(Identifier skill, float amount) { gain(skill, amount, false); }
    private void gain(Identifier skill, float amount, boolean progresses) {
        requireWritable(); requireNonnegativeFinite(amount);
        if (!gainable(skill)) throw new IllegalArgumentException("Unknown or unlearned gameplay skill");
        amount = (float) (amount * io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillExpSpeed(skill));
        skills.put(skill, (float) Math.min(1, (double) getProficiency(skill) + amount));
        if (progresses) {
            double progression = amount * io.github.pinchan4273.reacademycraft.config.AcademyConfig.progressionMultiplier(ability);
            levelExperience = (float) Math.min(Float.MAX_VALUE, (double) levelExperience + progression);
        }
        final float added = amount;
        // 原作AbilityData.addSkillExpはSkillExpChangedEvent、次にSkillExpAddedEventを送る。
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpChanged(p, skill));
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpAdded(p, skill, added));
    }
    private boolean gainable(Identifier skill) {
        SkillCatalog.Definition definition = SkillCatalog.find(skill);
        // 白翼飛行はパッシブだが、飛んで熟練度を得るので、パッシブ技能のうちこれだけは受け取る。
        return definition != null && definition.offeredTo(ability) && hasLearned(skill)
                && (!GenericSkills.isPassive(skill) || DarkMatter.passiveGains(skill));
    }
    /**
     * 原作AbilityData.addSkillExpそのもの。まだ習得していない技能は、レベルや前提条件を確かめず（カテゴリが提供するかだけを確かめ）、
     * まず習得させる。テレポーターのクリティカルはこれに頼る: Dimension Folding TheoremとSpace Fluctuationに経験値を与え、
     * その場で習得させる。それ以外はすべてaddProficiencyを通り、未習得やパッシブの技能は拒否される。
     */
    public void addSkillExperience(Identifier skill, float amount) {
        requireWritable(); requireNonnegativeFinite(amount);
        SkillCatalog.Definition definition = SkillCatalog.find(skill);
        if (definition == null || !definition.offeredTo(ability)) throw new IllegalArgumentException("Skill is not in this category");
        if (!hasLearned(skill)) learn(skill);
        // 原作は技能のexp_incr_speedをAbilityContext.addSkillExpでだけ掛ける。クリティカルはAbilityData.addSkillExpを直接呼ぶので、
        // この量は倍率を掛けない。
        skills.put(skill, (float) Math.min(1, (double) getProficiency(skill) + amount));
        double progression = amount * io.github.pinchan4273.reacademycraft.config.AcademyConfig.progressionMultiplier(ability);
        levelExperience = (float) Math.min(Float.MAX_VALUE, (double) levelExperience + progression);
        final float added = amount;
        // 原作AbilityData.addSkillExpはSkillExpChangedEvent、次にSkillExpAddedEventを送る。
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpChanged(p, skill));
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpAdded(p, skill, added));
    }
    /** 明示的なテストの準備。生の経験値を変えてもレベルは決して与えない。 */
    public void setLevelExperience(float value) {
        requireWritable(); levelExperience = requireNonnegativeFinite(value);
    }
    /** 技能1つとその装備の参照だけを取り除き、関係の無い進捗は保つ。 */
    public void unlearn(Identifier skill) {
        requireWritable();
        if (skills.remove(Objects.requireNonNull(skill)) == null) return;
        cooldowns.remove(skill);
        passiveSkills.remove(skill);
        for (var preset : presets) for (int i = 0; i < preset.length; i++)
            if (skill.equals(preset[i])) preset[i] = null;
        maxCp = hasAbility() ? initialCp() + bonusCp : DEFAULT_MAX_CP;
        maxOverload = hasAbility() ? initialOverload() + bonusOverload : DEFAULT_MAX_OVERLOAD;
        cp = Math.min(cp, maxCp); overload = Math.min(overload, maxOverload);
    }
    public void setCooldown(Identifier skill, int ticks) {
        requireWritable();
        if (!hasLearned(skill) || ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Invalid cooldown");
        if (ticks == 0) cooldowns.remove(skill); else cooldowns.put(skill, ticks);
    }
    public void setSlot(int preset, int slot, @Nullable Identifier skill) {
        requireWritable(); checkSlot(preset, slot);
        // 1.5.2: パッシブに切り替えた技能は、パッシブの間はスロットに置けない。
        if (skill != null && (!hasLearned(skill) || GenericSkills.isPassive(skill) || passiveSkills.contains(skill)))
            throw new IllegalArgumentException("Skill is not a learned controllable skill");
        boolean changed = !Objects.equals(presets[preset][slot], skill);
        presets[preset][slot] = skill;
        if (changed) post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.PresetUpdate::new);
    }
    public void setCurrentPreset(int preset) {
        requireWritable(); checkSlot(preset, 0);
        boolean changed = currentPreset != preset;
        currentPreset = preset;
        if (changed) post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.PresetSwitch::new);
    }
    private static void checkSlot(int preset, int slot) {
        if (preset < 0 || preset >= PRESET_COUNT || slot < 0 || slot >= SLOT_COUNT)
            throw new IllegalArgumentException("Invalid preset/slot");
    }
    private void clearSkills() {
        skills.clear(); cooldowns.clear(); currentPreset = 0; passiveSkills.clear();
        for (var preset : presets) java.util.Arrays.fill(preset, null);
    }
    /** 原作CPData.recalcMaxValue。求められれば、changedLevelと同じくaddMaxCP/addMaxOverloadを消す。 */
    private void recalculateLimits(boolean keepBonus) {
        if (!keepBonus) bonusCp = bonusOverload = 0;
        maxCp = hasAbility() ? initialCp() + bonusCp : DEFAULT_MAX_CP;
        maxOverload = hasAbility() ? initialOverload() + bonusOverload : DEFAULT_MAX_OVERLOAD;
        cp = hasAbility() ? maxCp : 0; overload = 0;
        cpRecoveryDelay = overloadRecoveryDelay = 0; overloadLocked = false; lockAllowed.clear();
        // 原作CalcEvent: MaxCPとMaxOverloadはinitialCp()/initialOverload()で、CPRecoverSpeedとOverloadRecoverSpeedはtick()で
        // 設定の倍率と並ぶ倍率として、SkillAttackはSkillCombat.attack()でAcademyConfig.DAMAGE_SCALE（calc_global.damage_scale）の前に使う。
    }

    /**
     * tickはサーバーでだけ行う。オーバーロードの式の形は1.12.2のCPData/default.confと同じだが、1.5.2からは
     * 伸びた分を含む今の上限で計算し、パッシブで起動している技能の数で遅くする（{@link #passiveOverloadFactor}）。CPの回復は1.0.0の後の
     * 定量（{@link #recovery}）。回復待ちの長さは習得した最上位のコースで決まる（熟練度では縮まず、過負荷の待ちの間は払った技能の熟練度に応じた割合だけ回復する。{@link #charge}）。
     */
    public void tick() {
        if (readOnly || !hasAbility()) return;
        // 未元物質人間のCPが0のときの死亡は、同じtickの自然回復より先に確定する（人体生成の処理が同じtickの終わりに死亡させる）。
        if (darkBody && cp <= 0) return;
        clampBodyDamage();
        cooldowns.replaceAll((id, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);
        // 1.5.2: 稼働の残りも同じく減らす。
        actionLeft.replaceAll((id, ticks) -> ticks - 1);
        actionLeft.values().removeIf(ticks -> ticks <= 0);
        if (safetyBlockedCost >= 0 && (!cpSafety || cp - safetyBlockedCost > safetyLine())) safetyBlockedCost = -1;
        // 被弾などでラインを割ったときも、動いている消費技能を止める。
        if (cpSafety && !upkeeps.isEmpty() && cp <= safetyLine()) tripSafety(0);
        // 1.5.2: 回復の式は、使って伸びた分を含む今の上限で計算する（伸びる前の上限ではない）。
        float limit = Math.max(0, maxOverload);
        // 1.5.2: パッシブで起動している技能が多いほど、過負荷の回復を遅くする（全部パッシブでも半分より遅くしない）。
        float passive = passiveOverloadFactor(passiveSkillCount());
        if (overload == 0 && overloadRecoveryDelay == 0) unlock();
        boolean waiting = cpRecoveryDelay > 0;
        if (waiting) cpRecoveryDelay--;
        // 通常の自然回復は、未修復の身体損傷の分を埋めない（上限は最大CP−損傷）。
        // 1.5.2: 未元物質人間は形態の維持 G0 × 30% を毎tick払う（回復待ちの間も）。
        float form = formDue();
        if (upkeeps.isEmpty() && !(form > 0)) {
            lastUpkeepPerTick = 0;
            if (!waiting && maxCp > 0) cp = (float) Math.max(cp, Math.min(maxCp - bodyDamage, cp + recovery()));
            // 払う維持が無いので、継続の収支は0以上: 身体を修復する。
            repairBody();
        }
        else settleUpkeep(waiting, form);
        // 天界発現の再生（未元物質人間）: 回復待ちの間も、能力の飛行中も、別枠で足す。
        applyHeavenCpRegen();
        // 1.5.2: Storm Wingの推進しない滞空で、過負荷の回復を遅い滞空の回復（2〜4/秒）に置き換える作りは無くした。
        // 1.5.2: 回復待ちの間も、待ちを作った技能の熟練度に応じた割合だけ回復する（熟練度0%なら回復しない）。
        // 能力の飛行で空中にいる間は回復が遅い。防御がオンの間は、その確保の分より下げない（ロック中は確保しない）。
        float share = 1;
        if (overloadRecoveryDelay > 0) { overloadRecoveryDelay--; share = overloadWaitRate; }
        // 1.5.2: どれかの技能が稼働中なら、過負荷は回復しない（単発の払いの後の待ちは、待ちの間の回復の割合のまま）。
        if (!actionLeft.isEmpty()) share = 0;
        if (limit > 0 && share > 0) {
            float air = airborneFlight ? io.github.pinchan4273.reacademycraft.config.AcademyConfig.airborneOverloadRecovery() : 1;
            float floor = overloadLocked ? 0 : reservedOverload();
            float fall = (float) (io.github.pinchan4273.reacademycraft.config.AcademyConfig.OVERLOAD_RECOVER_SPEED_MULTIPLIER.get()
                    * calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.OverloadRecoverSpeed::new, 1) * passive * air * share
                    * Math.max(0.002f * limit, 0.007f * limit * (1 - 0.25f * overload / limit)));
            overload = Math.max(Math.min(overload, floor), overload - fall);
            // 解除は tick の初めと同じく「OL 0 かつ待ち0」のときだけ（待ちの間の回復で OL が先に0になっても解かない）。
            if (overload == 0 && overloadRecoveryDelay == 0) unlock();
        }
    }
    /**
     * 1.5.2: ロックを解く（過負荷0かつ待ち0のとき）。ロック中に再稼働した防御などが確保しなかった分は、ここで確保する
     * （ロック中は確保しない。{@link #reserveOverload}）。許した技能は忘れる。
     */
    private void unlock() {
        if (!overloadLocked) return;
        overloadLocked = false;
        lockAllowed.clear();
        for (var upkeep : java.util.List.copyOf(upkeeps.values())) {
            float want = upkeep.pendingReserve;
            upkeep.pendingReserve = 0;
            if (want > 0 && upkeep.reservedOverload <= 0) reserveOverload(upkeep, want, false);
        }
    }
    /** 1.5.2: 能力の飛行で空中にいるかを置く（{@link AbilityGameplay}が毎tick）。 */
    public void setAirborneFlight(boolean airborne) { airborneFlight = airborne; }
    /** 1.5.2: 能力の飛行が動いているか。毎tick、{@link #tick}の前に{@link AbilityGameplay}が書く。 */
    public void setAbilityFlight(boolean flying) { abilityFlight = flying; }
    public boolean isAbilityFlight() { return abilityFlight; }
    public boolean isAirborneFlight() { return airborneFlight; }
    /** 今の回復待ちの間に残る回復の割合（テストと表示用。待ちが0なら1）。 */
    public float getOverloadWaitRate() { return overloadRecoveryDelay > 0 ? overloadWaitRate : 1; }
    /** 1.5.2: 動いている防御が確保している過負荷の合計。回復はこれより下げない。 */
    public float reservedOverload() {
        float sum = 0;
        for (var upkeep : upkeeps.values()) sum += upkeep.reservedOverload;
        return sum;
    }
    /**
     * 1.5.2: 防御の開始の過負荷を払い、オンの間それを確保する（1.5.1 の「開始の値より下がらない」を、確保の量だけの形にした物）。
     * ロック中・維持が外れている・払えないときは false。クリエイティブは払わず、確保もしない。確保は維持が外れると一緒に消える。
     */
    public boolean reserveOverload(@Nullable Upkeep upkeep, float amount, boolean creative) {
        if (!upkeepHeld(upkeep)) return false;
        if (!(amount > 0) || creative) return true;
        if (readOnly || !hasAbility()) return false;
        if (overloadLocked) {
            // 1.5.2: ロック中の再稼働を許した技能は、確保せずに始める（ロック中は確保しない）。解けたら確保する。
            if (!lockAllowed.contains(upkeep.skill)) return false;
            upkeep.reservedOverload = 0; upkeep.pendingReserve = amount;
            return true;
        }
        // CPは払わないので、CPセーフティは見ない（維持の精算がラインを見る）。技能の過負荷の倍率とイベントは通す。
        float before = overload;
        charge(upkeep.skill, 0, perform(0, overloadFor(upkeep.skill, amount))[1], false, false);
        upkeep.reservedOverload = Math.max(0, overload - before);
        return true;
    }

    /** 1.5.2: 確保の合計の上限（最大過負荷に対する割合）。確保だけではロックしない。 */
    public static final float RESERVE_CAP_SHARE = .99f;
    /**
     * 1.5.2（天界発現の常時の効果）: この維持の確保を target に決め直す。増えたら差を払って過負荷を上げ、減ったら確保だけを下げる
     * （下がった分は通常どおり回復する）。どちらも回復の待ちは作らず、稼働にもしない。確保の合計と、払って上がる過負荷は、
     * 最大過負荷の {@link #RESERVE_CAP_SHARE} で止める（確保だけではロックしない）。ロック中は確保しない（防御と同じ）。
     * クリエイティブは払わず、確保もしない。技能の過負荷の倍率を掛ける。確保は維持が外れると一緒に消え、保存しない。
     */
    public void setReservedOverload(@Nullable Upkeep upkeep, float target, boolean creative) {
        if (upkeep == null || !upkeepHeld(upkeep) || readOnly || !hasAbility()) return;
        if (creative || overloadLocked || !(target > 0)) { upkeep.reservedOverload = 0; return; }
        float cap = maxOverload * RESERVE_CAP_SHARE;
        float others = reservedOverload() - upkeep.reservedOverload;
        float wanted = Math.min(overloadFor(upkeep.skill, target), Math.max(0, cap - others));
        float delta = wanted - upkeep.reservedOverload;
        if (delta > 0) overload = Math.max(overload, Math.min(cap, overload + delta));
        upkeep.reservedOverload = wanted;
    }
    /** 1.5.2: この維持が今確保している過負荷（テストと表示用）。 */
    public float reservedOverload(@Nullable Upkeep upkeep) { return upkeep == null ? 0 : upkeep.reservedOverload; }

    /**
     * 1.5.2: パッシブで起動している技能の数nに対する、過負荷の回復の倍率 max(0.5, 1 − 0.15 × n)。
     * nが0以下なら1（遅くしない）。
     */
    public static float passiveOverloadFactor(int passiveCount) {
        return passiveCount <= 0 ? 1 : Math.max(0.5f, 1 - 0.15f * passiveCount);
    }
    /** テスト用に置いた、パッシブで起動している技能の数（負なら置いていない）。保存しない。 */
    private int passiveCountForTest = -1;
    /**
     * パッシブで起動している技能の数。数えるのは、切り替えでパッシブにして、今セッションが動いている技能だけ
     * （{@link PassiveSkills}がサーバーで毎tick数えて{@link #setPassiveRunningCount}で置く）。白翼飛行・Radiation Intensifyなどの
     * 元からのパッシブは数えない（1.5.2 で廃止した未元装甲も、元からのパッシブとして数えていなかった。翼の防御はパッシブに切り替えると数える）。パッシブの飛行（ストームウィング・ジェット・エンジン）は、離陸できる状態では数えず、飛んでいる間だけ数える。
     * テストが{@link #setPassiveSkillCountForTest}で置いた値があれば、それを返す。
     */
    public int passiveSkillCount() { return passiveCountForTest >= 0 ? passiveCountForTest : passiveRunning; }
    /** テスト用の入口: パッシブで起動している技能の数を置く。負の値で置いたものを外す。 */
    public void setPassiveSkillCountForTest(int count) { passiveCountForTest = count; }
    /** {@link PassiveSkills}が数えた、パッシブで起動している技能の数を置く（負は0）。 */
    public void setPassiveRunningCount(int count) { passiveRunning = Math.max(0, count); }

    /** 1.5.2: その技能をパッシブに切り替えているか。 */
    public boolean isPassiveSkill(@Nullable Identifier skill) { return skill != null && passiveSkills.contains(skill); }
    /** パッシブに切り替えている技能の写し（切り替えた順）。 */
    public java.util.Set<Identifier> passiveSkills() { return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(passiveSkills)); }
    /**
     * 技能をパッシブ（true）かアクティブ（false）にする。パッシブにするときは、すべてのプリセットのスロットからその技能を外す。
     * アクティブに戻してもスロットは戻さない。熟練度などの条件は呼ぶ側（{@link PassiveSkills#set}）が確かめる。
     * 切り替えられない技能・習得していない技能をパッシブにしようとすると例外。何か変わったらtrue。
     */
    public boolean setPassiveSkill(Identifier skill, boolean passive) {
        requireWritable();
        Objects.requireNonNull(skill);
        if (!passive) return passiveSkills.remove(skill);
        if (!PassiveSkills.switchable(skill) || !hasLearned(skill)) throw new IllegalArgumentException("Skill cannot be switched to passive: " + skill);
        boolean changed = passiveSkills.add(skill), slots = false;
        for (var preset : presets) for (int i = 0; i < preset.length; i++)
            if (skill.equals(preset[i])) { preset[i] = null; slots = true; }
        if (slots) post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.PresetUpdate::new);
        return changed || slots;
    }

    /**
     * 最大値で切る前の、このtickのCP回復: 待ちが無いときにもたらす量。定量で、CPの残量にも容量にもよらない:
     * (50 × 能力レベル + 50 × 習得したコース数) CP/秒 = 2.5 × (レベル + コース数) CP/tick（標準設定、20TPS）。
     * 管理者の倍率とCPRecoverSpeedイベントは、その後で1回ずつ掛ける。
     */
    private double recovery() {
        return io.github.pinchan4273.reacademycraft.config.AcademyConfig.CP_RECOVER_SPEED_MULTIPLIER.get()
                * calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CPRecoverSpeed::new, 1)
                * io.github.pinchan4273.reacademycraft.config.AcademyConfig.cpRecoveryPerTick(level, GenericSkills.courses(this));
    }

    /**
     * tick()が精算する維持コストへの、切り替え型技能の要求。技能はモードがオンの間これを保ち、保てなくなったら（upkeepHeld）
     * モードを終える。
     */
    public static final class Upkeep {
        private final Identifier skill;
        private final java.util.function.BooleanSupplier exempt;
        /** 前の精算から、このモードが行動した（攻撃・防御・反射・ワープ・推進）。次の精算で待機として扱わない。 */
        private boolean acted;
        /** 常に行動しているモード（Jet Engineは入力が無くても推進し、Body Intensifyのバフは常に効く）。待機にならない。 */
        private boolean alwaysActing;
        /**
         * 維持の熟練度の受け取り方だけを、CPの精算の区分（{@link #alwaysActing}）から分ける: 立てたモードは、精算で
         * 行動として払っても、維持の熟練度は待機と同じ{@link #HOLD_IDLE_PROFICIENCY}を、技能の熟練度だけに受け取る（能力レベルの経験値なし）。
         * 常に効果を出すモード（Body Intensify、Jet Engine）が、維持しているだけでレベルを上げないため。
         */
        private boolean skillOnlyHold;
        /** 費用をモードが自分で決める（飛行の部類: 白翼飛行・Storm Wing・Jet Engine）。与えられた回復から、このtickの費用を返す。払った後も回復待ちは動かない。 */
        private @Nullable java.util.function.DoubleUnaryOperator custom;
        /** このtickの精算が払い終えた維持の種類（{@link #upkeepEarn}が1回だけ受け取る）: 0は無し、1は待機、2は行動。 */
        private int earned;
        private Upkeep(Identifier skill, java.util.function.BooleanSupplier exempt) { this.skill = skill; this.exempt = exempt; }
        public Identifier skill() { return skill; }
        public Upkeep custom(java.util.function.DoubleUnaryOperator due) { custom = due; return this; }
        /** CPセーフティを自分で判定するモード（白翼飛行は外れずに滞空へ移る）。 */
        private boolean selfSafety;
        public Upkeep selfSafety() { selfSafety = true; return this; }
        /**
         * 部類の外の切り替え型の共通の形（1.5.2）: オンの間は毎tick G × lerp(1.0, 0.25, 熟練度) ＋
         * toggle_upkeepの値 × (1 − 熟練度) を払う（Gはそのtickに実際に入る回復で、回復待ちの間は0）。{@link #custom}もあれば、
         * その額（天界発現の基本の維持費など）を足す。待機と行動の区別、「1つだけのときの特典」は無く、いくつ同時に使っても
         * それぞれの割合で済む。
         */
        private boolean recoveryShare;
        public Upkeep recoveryShare() { recoveryShare = true; return this; }
        /** 1.5.2: この防御が開始で払い、オンの間確保している過負荷（{@link #reserveOverload}）。天界発現の常時の効果も使う（{@link #setReservedOverload}）。 */
        private float reservedOverload;
        public float reservedOverload() { return reservedOverload; }
        /** 1.5.2: ロック中の再稼働で確保しなかった分（ロックが解けたら確保する。{@link #unlock}）。 */
        private float pendingReserve;
        /**
         * 1.5.2: 基準の回復G0に対する割合の維持。1tickに G0 × lerp(熟練度0%の割合, 100%の割合, 熟練度) を払う。
         * G0は待ちの無いときの回復なので、回復待ちの間も同じ額を払う（そのtickの回復の有無に依らない）。{@link #custom}もあれば、
         * その額（G0を渡す）を足す。
         */
        private boolean g0Share;
        private float shareAtZero, shareAtFull;
        public Upkeep g0Share(float atZero, float atFull) { g0Share = true; shareAtZero = atZero; shareAtFull = atFull; return this; }
        /**
         * 1.5.2: 払えない・セーフティに掛かるとき、止める前に状態を1段下げる（飛行: 高速 → 通常 → 滞空）。下げたら true を返す。
         * 精算は下げた後の額で計算し直し、それでも足りなければもう1段、下げられなければ止める。
         */
        private @Nullable java.util.function.BooleanSupplier stepDown;
        public Upkeep stepDown(java.util.function.BooleanSupplier step) { stepDown = step; return this; }
        public Upkeep alwaysActing() { alwaysActing = true; return this; }
        /** 維持の熟練度を、待機と同じ量・技能の熟練度だけにする（{@link #skillOnlyHold}）。CPの精算は変えない。 */
        public Upkeep skillOnlyHold() { skillOnlyHold = true; return this; }
    }
    /** このデータが置き換えられた、または能力が変わった回数: リスナーがどちらかを行った精算は止まる。 */
    private int loads;
    /** サーバー側で、保存しない: モードはセッションより長く生きない。モードが始まった順。 */
    private final Map<Identifier, Upkeep> upkeeps = new LinkedHashMap<>();
    /**
     * 次のtickからモードの維持コストを払い始める。免除（クリエイティブの術者）は、消費と同じく何も払わない。
     * 維持できない所（読み取り専用のデータ、能力無し、未習得の技能）ではnull。
     */
    public @Nullable Upkeep startUpkeep(Identifier skill, java.util.function.BooleanSupplier exempt) {
        if (readOnly || !hasAbility() || skill == null || exempt == null || !hasLearned(skill)) return null;
        var upkeep = new Upkeep(skill, exempt);
        upkeeps.remove(skill); upkeeps.put(skill, upkeep);
        return upkeep;
    }
    /** この要求自体がまだ払われているか: 払えなくなるか、別のものが置き換えるとfalse。 */
    public boolean upkeepHeld(@Nullable Upkeep upkeep) { return upkeep != null && upkeeps.get(upkeep.skill) == upkeep; }
    /** この要求だけを終える: 古いセッションの遅れた停止は、新しいセッションの要求に手を出さない。 */
    public void endUpkeep(@Nullable Upkeep upkeep) { if (upkeepHeld(upkeep)) upkeeps.remove(upkeep.skill); }
    /** このモードがこのtickに行動した（攻撃・防御・反射・ワープ・推進）: 次の精算は待機の特典を与えない。 */
    public void upkeepActed(@Nullable Upkeep upkeep) { if (upkeepHeld(upkeep)) upkeep.acted = true; }
    public int upkeepCount() { return upkeeps.size(); }

    /**
     * 維持による熟練度（1.1.0の待機2e-6・行動1e-5から変更）。20 tick/秒・技能の倍率1で、
     * 待機は+0.1パーセントポイント/秒（5e-5/tick）、行動（防御・射撃・ワープ・停止・反射・推進などの効果を出したtick）は
     * +0.2パーセントポイント/秒（1e-4/tick）。技能ごとのexp_incr_speedは、これまでどおりgainが掛ける。
     * 常に効果を出すモード（Body Intensify、Jet Engine）は{@link Upkeep#skillOnlyHold}により、CPの精算では行動のまま、熟練度は
     * 待機と同じ量を技能だけに受け取る。効果ごとの成功の熟練度は各技能がこれとは別に与える。
     */
    public static final float HOLD_IDLE_PROFICIENCY = 5e-5f, HOLD_ACTING_PROFICIENCY = 1e-4f;
    /**
     * モードがこのtickも続くと確かめた後で技能が呼ぶ: このtickの始めの精算が払い終えた維持に応じて、熟練度を1回だけ与える。
     * 待機はaddProficiencyOnly（能力レベルの経験値なし）、行動はaddProficiency。精算されなかった（払えず外れた、まだ精算前、
     * 同じtickに2度目）ものや、能力がオフ・妨害中は何も与えない。技能が先に終わったtickの分は捨てる。オーバーロードは多くのモードを
     * 止めるので、その後は与えない。自身の起動のオーバーロードで止まらずに続くモード（原作どおりのVector Reflectionなど）は、
     * 効果を出し続けている間は与える。
     */
    public void upkeepEarn(@Nullable Upkeep upkeep) {
        if (!upkeepHeld(upkeep)) return;
        int earned = upkeep.earned; upkeep.earned = 0;
        if (earned == 0 || readOnly || !hasAbility() || !active || interfering || !gainable(upkeep.skill)) return;
        // 精算では行動として払ったモードでも、維持の熟練度は待機と同じ量を技能だけに（能力レベルの経験値なし）。
        if (upkeep.skillOnlyHold) { if (getProficiency(upkeep.skill) < 1) gain(upkeep.skill, HOLD_IDLE_PROFICIENCY, false); return; }
        if (earned == 2) gain(upkeep.skill, HOLD_ACTING_PROFICIENCY, true);
        else if (getProficiency(upkeep.skill) < 1) gain(upkeep.skill, HOLD_IDLE_PROFICIENCY, false);
    }

    /**
     * 維持コストの取り決め: このtickに待ち無しでもたらされる回復Rと、実際に与える回復G
     * （待ち中は0）を1回だけ求める。各モードはSkillPerformを通してcp_consume_speed倍の次の額を払う。
     * <ul>
     * <li>熟練度100%のモード1つだけで、前のtickに行動していない（待機）: (1 − idle_recovery_share) × G。正味で回復の半分が残る。</li>
     * <li>それ以外（行動中・100%未満・複数同時）: 1.0.0の契約のまま R + E × (1 − 熟練度)。待ちが回復を止めている間もRを使う。</li>
     * </ul>
     * 飛行の部類（白翼飛行・Storm Wing・Jet Engine。1.5.2）は{@link Upkeep#custom}で{@link FlightCost}の額を払う
     * （上のどれでもなく、ほかのモードと同時でも、それぞれの額で済む）。
     * 部類の外の切り替え型（{@link Upkeep#recoveryShare}。1.5.2）は、上のどれでもなく G × lerp(1.0, 0.25, 熟練度) ＋
     * E × (1 − 熟練度)（＋{@link Upkeep#custom}の額）を払う。熟練度100%で1つだけのときの特典は無く、いくつ同時でもそれぞれの割合で済む。
     * 防御の部類（1.5.2）は{@link Upkeep#g0Share}で G0 × 割合を払う（G0は待ちの無いときの回復の
     * {@code basis}で、回復待ちの間も同じ額）。割合は0。
     * 上の2つは、飛行・防御の部類と部類の外の切り替え型の形を使わないモードのための、今の形。
     * CPと与えられた回復ですべてを払えないときは、払えるまで新しいモードから外れる。残りは1つの差分として精算する。
     * どちらの回復待ちも動かさない。払った分だけ最大CPが伸びる。
     */
    private void settleUpkeep(boolean waiting, float form) {
        double recovered = maxCp > 0 ? recovery() : 0;
        // リスナーの有限の倍率でも、回復がfloatの範囲を超えることがある。その場合は要求の無い回復と同じくCPを最大まで満たし、
        // コストは標準の割合を取る。
        boolean overflow = !(recovered <= Float.MAX_VALUE);
        float potential = overflow ? 0 : (float) recovered;
        float granted = waiting ? 0 : overflow ? maxCp : potential;
        // 回復が止まっている（倍率、イベント）と、1.0.0の契約の熟練度最大がただになってしまう: 代わりに標準の回復を課し、
        // それも0なら1tickに1を課す。
        float standard = (float) io.github.pinchan4273.reacademycraft.config.AcademyConfig.cpRecoveryPerTick(level, GenericSkills.courses(this));
        float basis = potential > 0 ? potential : standard > 0 ? standard : 1;
        int loads = this.loads;
        var order = new java.util.ArrayList<>(upkeeps.values());
        boolean alone = order.size() == 1;
        float[] cost = new float[order.size()];
        boolean[] idles = new boolean[order.size()];
        for (int i = 0; i < order.size(); i++) {
            var upkeep = order.get(i);
            boolean idle = !upkeep.acted && !upkeep.alwaysActing;
            idles[i] = idle;
            upkeep.acted = false; upkeep.earned = 0;
            if (!active || !hasLearned(upkeep.skill)) { endUpkeep(upkeep); continue; }
            if (upkeep.exempt.getAsBoolean()) continue;
            cost[i] = perform(cpFor(upkeep.skill, due(upkeep, basis, granted, idle, alone)), 0)[0];
            // SkillPerformは他のリスナーが受け取り、その間に何をしてもよい: 下を参照。
            if (loads != this.loads || readOnly || !hasAbility()) break;
        }
        // リスナーが残したもの: リスナーが読み込んだ、または能力を変えたデータは、この精算が書くものではない。リスナーが能力をオフにしたら、
        // すべての要求を払わずに終える。リスナーが終えた要求は課さない。
        if (loads != this.loads || readOnly || !hasAbility()) return;
        if (!active) upkeeps.clear();
        double total = 0;
        for (int i = 0; i < order.size(); i++) if (upkeepHeld(order.get(i))) total += cost[i];
        double available = (double) cp + granted;
        // 形態の維持は外さない。払えない分はCPを0まで使い、CPが0のときの真の死亡（{@link DarkBody}）へ進む。
        double formCost = form > 0 && Float.isFinite(form) ? form : 0;
        // 1.5.2: セーフティがオンの間、止める順は今のプリセットのセーフティの並びの下から（並びに無い技能はすべての候補の下、
        // 始めた順の新しい物から）で、飛行は止める前に段を下げる（{@link Upkeep#stepDown}）。セーフティがオフの間は、今までどおり新しい物から止め、
        // 段は下げない（オフのときの決まりは変えない）。
        var ranked = SafetyPriority.resolve(getSafetyOrder(currentPreset));
        Integer[] stop = new Integer[order.size()];
        for (int i = 0; i < stop.length; i++) stop[i] = i;
        boolean ordered = cpSafety;
        java.util.Arrays.sort(stop, (a, b) -> {
            int ra = ordered ? SafetyPriority.rank(ranked, order.get(a).skill) : 0, rb = ordered ? SafetyPriority.rank(ranked, order.get(b).skill) : 0;
            return ra != rb ? Integer.compare(rb, ra) : Integer.compare(b, a);
        });
        for (int k = 0; k < stop.length && total + formCost > available; k++) {
            int i = stop[k]; var upkeep = order.get(i);
            if (!(cost[i] > 0) || !upkeepHeld(upkeep)) continue;
            while (ordered && total + formCost > available && upkeep.stepDown != null && upkeep.stepDown.getAsBoolean()) {
                float stepped = perform(cpFor(upkeep.skill, due(upkeep, basis, granted, idles[i], alone)), 0)[0];
                total += stepped - cost[i]; cost[i] = stepped;
            }
            if (total + formCost > available) { endUpkeep(upkeep); total -= cost[i]; safetyEnded.add(upkeep.skill); }
        }
        // CPセーフティ: 払った後がライン（＋未元物質人間の形態の予約）以下になる維持は、並びの下から段を下げ、それでも足りなければ外して作動させる
        // （回復の分はこのtickに実際に入る分だけ数える）。形態の維持はセーフティでは止めない（最上位）。
        if (cpSafety && total > 0) {
            double line = safetyLine();
            for (int k = 0; k < stop.length && total > 0 && available - total - formCost <= line; k++) {
                int i = stop[k]; var upkeep = order.get(i);
                if (!(cost[i] > 0) || !upkeepHeld(upkeep) || upkeep.selfSafety) continue;
                while (available - total - formCost <= line && upkeep.stepDown != null && upkeep.stepDown.getAsBoolean()) {
                    float stepped = perform(cpFor(upkeep.skill, due(upkeep, basis, granted, idles[i], alone)), 0)[0];
                    total += stepped - cost[i]; cost[i] = stepped;
                    tripSafety(0);
                }
                if (available - total - formCost <= line) { endUpkeep(upkeep); total -= cost[i]; tripSafety(cost[i]); safetyEnded.add(upkeep.skill); }
            }
        }
        if (loads != this.loads || readOnly || !hasAbility()) return;
        lastUpkeepPerTick = (float) Math.max(0, Math.min(Float.MAX_VALUE, total + formCost));
        // 1.5.2: 維持費の支払いでも最大CPが伸びる（払い終えた技能の維持の分だけ。形態の維持は技能ではないので伸ばさない）。
        if (total > 0 && total <= Float.MAX_VALUE) growMaxCp((float) total);
        double change = granted - total - formCost;
        // 回復の分は、未修復の身体損傷の分を埋めない（上限は最大CP−損傷。払う分は従来どおり）。
        if (Double.isFinite(change)) cp = (float) Math.max(0, Math.min(change > 0 ? Math.max(cp, maxCp - bodyDamage) : maxCp, cp + change));
        // 1.5.2: 継続の収支（実際に入る回復 − 払う維持の合計）が0以上の tick だけ、身体を修復する。
        if (change >= 0) repairBody();
        // 払い終えた維持だけが、このtickの維持の熟練度を受け取れる（技能が続くと確かめてからupkeepEarnで）。
        for (int i = 0; i < order.size(); i++) if (upkeepHeld(order.get(i))) order.get(i).earned = idles[i] ? 1 : 2;
    }

    /** 維持1つの1tickの額（技能の倍率の前）。割合の維持はすべて G0（basis）から決め、回復待ちの間も同じ額（1.5.2）。 */
    private float due(Upkeep upkeep, float basis, float granted, boolean idle, boolean alone) {
        // 熟練度100%は保存された値そのもので判定する（表示の丸めで100%に見える値は含めない）。
        float proficiency = getProficiency(upkeep.skill);
        if (upkeep.g0Share) return basis * ArcGen.lerp(upkeep.shareAtZero, upkeep.shareAtFull, Math.max(0, Math.min(1, proficiency)))
                + (upkeep.custom != null ? (float) upkeep.custom.applyAsDouble(basis) : 0);
        if (upkeep.recoveryShare) return recoveryShareDue(upkeep.skill, proficiency, basis, abilityFlight)
                + (upkeep.custom != null ? (float) upkeep.custom.applyAsDouble(basis) : 0);
        if (upkeep.custom != null) return (float) upkeep.custom.applyAsDouble(basis);
        if (idle && alone && proficiency >= 1) return (1 - io.github.pinchan4273.reacademycraft.config.AcademyConfig.idleRecoveryShare()) * granted;
        return basis + io.github.pinchan4273.reacademycraft.config.AcademyConfig.toggleUpkeep(upkeep.skill) * (1 - proficiency);
    }
    /** 前の精算で払った維持の合計（形態を含む、1tick）。入れ直しの閾値に使う（1.5.2）。 */
    private float lastUpkeepPerTick;
    /** 前の精算で払った維持の合計の1秒分（形態を含む）。 */
    public float upkeepPerSecond() { return lastUpkeepPerTick * 20; }
    /** 精算が、払えない・セーフティのために外した維持の技能（入れ直しのため。{@link #takeSafetyEnded}が1回だけ受け取る。保存しない）。 */
    private final java.util.Set<Identifier> safetyEnded = new java.util.HashSet<>();
    /** その技能の維持を、精算が払えない・セーフティのために外したか（受け取ったら忘れる）。本人の操作で止めた物は含まない。 */
    public boolean takeSafetyEnded(Identifier skill) { return safetyEnded.remove(skill); }

    /**
     * 1.5.2: G0（その人の待ちの無いときの1tickの回復）。設定の回復の倍率とCPRecoverSpeedのイベントを含む。それが0以下
     * （倍率0など）なら標準の回復（倍率の前）、それも0なら1。回復が float を超えるときは標準の回復。維持費の割合は、すべてこれに掛ける。
     */
    public float g0PerTick() {
        double recovered = maxCp > 0 ? recovery() : 0;
        float potential = !(recovered <= Float.MAX_VALUE) ? 0 : (float) recovered;
        float standard = (float) io.github.pinchan4273.reacademycraft.config.AcademyConfig.cpRecoveryPerTick(level, GenericSkills.courses(this));
        return potential > 0 ? potential : standard > 0 ? standard : 1;
    }
    /**
     * 形態の維持の1tickの額（未元物質人間で、免除されていなければ G0 × 30% × 未元物質・人体生成のCPの倍率。それ以外は0）。
     * 1.5.2: 能力の飛行が動いている間は0。飛行の費用も G0 に対する割合で決まるので、同じ回復を二重に払わせない
     * （形態の分は、飛行の費用に含めたと扱う）。
     */
    public float formDue() { return darkBody && hasAbility() && !formExempt && !abilityFlight ? cpFor(DarkMatter.BODY, g0PerTick() * FORM_UPKEEP_SHARE) : 0; }
    /** 形態の維持を払わない（クリエイティブ）か。サーバーが毎tickの精算の前に置く。 */
    public void setFormExempt(boolean exempt) { formExempt = exempt; }
    /** 身体修復の1tick: 未修復の損傷を、毎秒{@link #BODY_REPAIR_PER_SECOND}CP（端数も毎tick）までCPへ戻す。 */
    private void repairBody() {
        if (!(bodyDamage > 0)) return;
        float repair = Math.min(bodyDamage, BODY_REPAIR_PER_SECOND / 20f);
        bodyDamage -= repair;
        cp = Math.min(maxCp, cp + repair);
    }

    /** 共通の形の、回復の割合（熟練度0%で1.0、100%で0.25）。 */
    public static final float SHARE_DUE_AT_ZERO = 1f, SHARE_DUE_AT_FULL = .25f;
    /**
     * 部類の外の切り替え型が1tickに払う額（技能の倍率の前）: G0 × lerp(1.0, 0.25, 熟練度) ＋ toggle_upkeepの値 × (1 − 熟練度)。
     * 1.5.2: G0 は待ちの無いときの回復（{@link #g0PerTick}）で、回復待ちの間も同じ額を払う（その tick に
     * 実際に入る回復ではないので、待ちの間も0にならない）。
     */
    public static float recoveryShareDue(Identifier skill, float proficiency, float g0) {
        return recoveryShareDue(skill, proficiency, g0, false);
    }
    /**
     * 1.5.2: 能力の飛行が動いている間（flying）は、G0 の割合の分を払わない。飛行の費用も G0 に対する割合で決まるので、
     * 同じ回復を二重に払わせないため（形態の維持と同じ理由）。toggle_upkeep の分と技能ごとの額は払う。
     */
    public static float recoveryShareDue(Identifier skill, float proficiency, float g0, boolean flying) {
        float p = Math.max(0, Math.min(1, proficiency));
        return (flying ? 0 : g0 * ArcGen.lerp(SHARE_DUE_AT_ZERO, SHARE_DUE_AT_FULL, p))
                + io.github.pinchan4273.reacademycraft.config.AcademyConfig.toggleUpkeep(skill) * (1 - p);
    }

    /**
     * 1.5.2: 同時に稼働している技能の数 n に対する、稼働の過負荷の倍率 (1 ＋ 0.5 ×（n − 1））÷ n。
     * 2つなら各0.75（合わせて1.5倍）、3つなら各2/3（合わせて2倍）。同時に稼働を続けている間は払う順に依らない。稼働していなかった技能が同じtickに続けて稼働を始めると、先に払った技能はその時点の数で払う。n が1以下なら1。
     */
    public static float actionFactor(int running) { return running <= 1 ? 1 : (1 + .5f * (running - 1)) / running; }
    /** 1.5.2: 今稼働している技能の数。skill が稼働していなければ、それも足した数（これから払う技能を含めて数える）。 */
    public int actionCount(@Nullable Identifier skill) { return actionLeft.size() + (skill != null && !actionLeft.containsKey(skill) ? 1 : 0); }
    /**
     * 天界発現の再生を、未元物質人間の CP の回復として受ける（{@link HeavenEffects}が1秒ごとに置く）。share は基準の回復に対する割合、
     * ticks はこの割合で回復を続ける長さ。重なる領域は呼ぶ側が強い1つを選ぶ。
     */
    public void giveHeavenCpRegen(float share, int ticks) {
        if (readOnly || !(share > 0) || ticks <= 0) return;
        heavenCpShare = share; heavenCpLeft = Math.max(heavenCpLeft, ticks);
    }
    /** 今の1tickの天界発現の CP の回復（未元物質人間で、受けている間だけ。上限の前の額）。 */
    public float heavenCpRegenPerTick() { return darkBody && heavenCpLeft > 0 ? g0PerTick() * heavenCpShare : 0; }
    /**
     * 天界発現の CP の回復を1tick分入れる（{@link #tick}が精算の後に呼ぶ。テストからも呼ぶ）。上限は自然回復と同じ「最大CP − 未修復の身体損傷」で、
     * 損傷そのものは直さない。支払いではないので、最大CPは伸びない。
     */
    public void applyHeavenCpRegen() {
        if (heavenCpLeft <= 0) return;
        float amount = heavenCpRegenPerTick();
        heavenCpLeft--;
        if (heavenCpLeft == 0) heavenCpShare = 0;
        if (readOnly || !(amount > 0) || maxCp <= 0) return;
        float cap = maxCp - bodyDamage;
        if (cp < cap) cp = Math.min(cap, cp + amount);
    }
    /** 1.5.2: この技能が稼働中か。 */
    public boolean isActing(Identifier skill) { return actionLeft.containsKey(skill); }
    /** 1.5.2: どれかの技能が稼働中か（稼働中は過負荷が回復しない）。 */
    public boolean isActing() { return !actionLeft.isEmpty(); }
    /** 1.5.2: この技能の稼働の残りtick（稼働していなければ0）。 */
    public int actionTicksLeft(Identifier skill) { return actionLeft.getOrDefault(skill, 0); }
    /** 稼働の残りを置く。長さはコースの過負荷の待ちと minTicks の長いほう（既に長く残っていれば縮めない）。 */
    private void markAction(Identifier skill, int minTicks) {
        int ticks = Math.max(Math.max(1, minTicks), overloadWait(skill, GenericSkills.topCourse(this)));
        actionLeft.merge(skill, ticks, Math::max);
    }
    /**
     * 1.5.2: 稼働の払い（撃つ・防ぐ・跳ぶなど）。CPは払うが、CPの回復待ちはやり直さない。過負荷には同時稼働の倍率
     * （{@link #actionFactor}）と技能の倍率を掛け、過負荷の回復待ちをやり直し、技能を稼働中にする（minTicks: 稼働の最短の長さ。
     * 次の稼働までの間隔より長くして、続けて稼働している間に回復しないようにする）。拒否の条件は呼ぶ側の今の払い方に合わせて渡す。
     */
    public boolean payAction(Identifier skill, float cpCost, float overloadCost, boolean creative,
                             boolean needActive, boolean needCp, boolean refuseLocked, int minTicks) {
        requireNonnegativeFinite(cpCost); requireNonnegativeFinite(overloadCost);
        // 1.5.2（ロックからの戻し方）: ロック中は稼働しない（撃つ・防ぐ・跳ぶ。ロック中に再稼働した技能も維持だけ）。
        if (overloadLocked) return false;
        float ol = overloadFor(skill, overloadCost) * actionFactor(actionCount(skill));
        if (!pay(skill, cpFor(skill, cpCost), ol, creative, needActive, needCp, refuseLocked, false)) return false;
        markAction(skill, minTicks);
        return true;
    }
    /**
     * 1.5.2: 稼働の過負荷だけを課す（光線が当たった・天界の効果をかけた・現象が起きた・身体強化のバフを使った）。
     * 断らない（CPセーフティにも掛けない。働いた効果は取り消さない）。過負荷には同時稼働の倍率と技能の倍率を掛け、
     * 過負荷の回復待ちをやり直し、技能を稼働中にする。
     */
    public void chargeActionOverload(Identifier skill, float overloadCost, boolean creative, int minTicks) {
        requireNonnegativeFinite(overloadCost);
        // 1.5.2（ロックからの戻し方）: ロック中は稼働の過負荷を課さない（呼ぶ側も働きを止める）。
        if (readOnly || !hasAbility() || overloadLocked) return;
        float cost = perform(0, overloadFor(skill, overloadCost) * actionFactor(actionCount(skill)))[1];
        if (readOnly || !hasAbility()) return;
        charge(skill, 0, cost, creative, false);
        markAction(skill, minTicks);
    }

    /**
     * オンであることに対する切り替え型モード自身のオーバーロード（Vector Deviation、未元光線）: consumeがオーバーロードを課すのと同じく
     * 課す（オーバーロードのイベント、最大値の成長）が、どちらの回復待ちにも触れない。1.5.2から、モード中に
     * 過負荷の回復待ちをやり直し続ける作りはなくしたので、オンの間も過負荷は回復を続ける。
     */
    public void chargeUpkeepOverload(Identifier skill, float overloadCost, boolean creative) {
        requireNonnegativeFinite(overloadCost);
        if (readOnly || !hasAbility()) return;
        float cost = perform(0, overloadFor(skill, overloadCost))[1];
        if (readOnly || !hasAbility()) return;
        if (!creative) {
            overload = Math.min(maxOverload, overload + cost);
            if (overload >= maxOverload && !overloadLocked) { overloadLocked = true; lockAllowed.clear(); post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload::new); }
        }
        float overloadIncrease = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addOverload(level) - bonusOverload,
                Math.min(10, cost * io.github.pinchan4273.reacademycraft.config.AcademyConfig.maxOverloadIncrRate())));
        bonusOverload += overloadIncrease; maxOverload += overloadIncrease;
    }

    // ---- v1.5.0 CPセーフティ（全系統共通） ----
    /**
     * セーフティの既定。製品ではON。v1.4.1以前のテストはCPをちょうど使い切る形で書かれているので、テストの起動だけプロパティでOFFにできる
     * （build.gradleのgameTestServerとclient smoke。セーフティ自身の試験は値を明示して確かめる）。
     */
    private static final boolean DEFAULT_CP_SAFETY = !Boolean.getBoolean("academy.test.cpSafetyDefaultOff");
    public static final int CP_SAFETY_DEFAULT_PERCENT = 10, CP_SAFETY_MIN_PERCENT = 1, CP_SAFETY_MAX_PERCENT = 95;
    private boolean cpSafety = DEFAULT_CP_SAFETY;
    private int cpSafetyPercent = CP_SAFETY_DEFAULT_PERCENT;
    /** セーフティが止めた次の1回の費用（赤い点滅の解除の条件）。止めていなければ負。保存しない。 */
    private float safetyBlockedCost = -1;
    /** セーフティが作動した（次のtickで消費技能をまとめて止める）。保存しない。 */
    private boolean safetyTripped;
    /** クライアントが受けた、赤い点滅をするか。 */
    private boolean clientSafetyBlink;

    public boolean isCpSafety() { return cpSafety; }
    public int getCpSafetyPercent() { return cpSafetyPercent; }
    public void setCpSafety(boolean on) { cpSafety = on; if (!on) safetyBlockedCost = -1; }
    public void setCpSafetyPercent(int percent) {
        if (percent < CP_SAFETY_MIN_PERCENT || percent > CP_SAFETY_MAX_PERCENT) throw new IllegalArgumentException("CP safety percent out of range: " + percent);
        cpSafetyPercent = percent;
    }
    /** 停止ライン（最大CPの割合）。 */
    /**
     * セーフティのライン（最大CPに対する%）。1.5.2: 未元物質人間は、形態の予約の%を足した点で止める・断る
     * （例: ライン10%＋予約5%＝15%）。セーフティがオフの間は、ラインも予約も使わない（呼ぶ側がオンのときだけ見る）。
     */
    public float safetyLine() { return maxCp * (cpSafetyPercent + (darkBody ? formReservePercent : 0)) / 100f; }

    /** 形態の予約（最大CPに対する整数の%）: 既定5、範囲0〜30（1.5.2）。 */
    public static final int FORM_RESERVE_DEFAULT = 5, FORM_RESERVE_MIN = 0, FORM_RESERVE_MAX = 30;
    private int formReservePercent = FORM_RESERVE_DEFAULT;
    public int getFormReservePercent() { return formReservePercent; }
    public void setFormReservePercent(int percent) {
        if (percent < FORM_RESERVE_MIN || percent > FORM_RESERVE_MAX) throw new IllegalArgumentException("Form reserve percent out of range: " + percent);
        formReservePercent = percent;
    }
    /** プリセットごとのセーフティの並び（{@link SafetyPriority#encode}の文字列。空は既定の並び）。 */
    private final String[] safetyOrders = {"", "", "", ""};
    /** そのプリセットのセーフティの並びの文字列（範囲の外は空）。 */
    public String getSafetyOrder(int preset) { return preset >= 0 && preset < safetyOrders.length ? safetyOrders[preset] : ""; }
    /** そのプリセットのセーフティの並びを置く。読めない値は例外。 */
    public void setSafetyOrder(int preset, String code) {
        if (preset < 0 || preset >= safetyOrders.length) throw new IllegalArgumentException("Preset out of range: " + preset);
        if (!SafetyPriority.valid(code)) throw new IllegalArgumentException("Invalid safety order: " + code);
        safetyOrders[preset] = code;
    }
    /** この額（倍率の後の最終の費用）を払った後のCPがラインを超えるか。回復の予定は先取りしない。セーフティOFFなら常にtrue。 */
    public boolean safetyClears(float finalCost) { return !cpSafety || cp - finalCost > safetyLine(); }
    /** 技能の費用をセーフティが通すか（技能の倍率を掛けてから判定する）。止めるなら作動させる。クリエイティブは費用が無いので止めない。 */
    public boolean safetyPermits(Identifier skill, float cpCost, boolean creative) {
        float cost = cpFor(skill, cpCost);
        if (creative || safetyClears(cost)) return true;
        tripSafety(cost);
        return false;
    }
    /** 払わずに確かめるだけ（自動防御・修復の再開の判定）。作動させない。 */
    public boolean safetyWouldPermit(Identifier skill, float cpCost) { return safetyClears(cpFor(skill, cpCost)); }
    private void tripSafety(float cost) { safetyTripped = true; safetyBlockedCost = Math.max(safetyBlockedCost, cost); }
    /** 作動の知らせを受け取る（1回だけtrue）。 */
    public boolean takeSafetyTrip() { boolean tripped = safetyTripped; safetyTripped = false; return tripped; }
    /** 赤い点滅をするか: 今のCPから止めた次の1回分を引いた値が、ラインを厳密に上回るまで続く。 */
    public boolean safetyBlinking() {
        if (readOnly) return false;
        return clientSafetyBlink || (cpSafety && safetyBlockedCost >= 0 && cp - safetyBlockedCost <= safetyLine());
    }
    public void setClientSafetyBlink(boolean blink) { clientSafetyBlink = blink; }

    /** 原作CPData.canPerform: これだけのCPを今使えるか（使わずに確かめる）。v1.5.0: CPセーフティの停止ラインも見る。 */
    public boolean canConsumeCp(float cpCost) {
        requireNonnegativeFinite(cpCost);
        return !readOnly && hasAbility() && active && !overloadLocked && cp >= cpCost && safetyClears(cpCost);
    }

    // 原作default.confの技能ごとのcp_consume_speedとoverload_consume_speed。原作のAbilityContextが、技能の払うすべてのコストに
    // 適用したのと同じ。
    private static float cpFor(Identifier skill, float cp) { return (float) (cp * io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillCpSpeed(skill)); }
    private static float overloadFor(Identifier skill, float overload) { return (float) (overload * io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillOverloadSpeed(skill)); }
    /**
     * 原作AbilityContext.canConsumeCPは、与えられたコストのままCPData.canPerformに問う: consumeと違い、技能のcp_consume_speedを掛けない。
     */
    public boolean canConsumeCp(Identifier skill, float cpCost) { return canConsumeCp(cpCost); }
    /** 技能のcp_consume_speedを掛けた、実際に払うCP（1.5.2: 呼ぶ側が払えるかを確かめるときに使う）。 */
    public static float skillCp(Identifier skill, float cpCost) { return cpFor(skill, cpCost); }
    public boolean consume(Identifier skill, float cpCost, float overloadCost, boolean creative) {
        return pay(skill, cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative, true, true, true, true);
    }
    /**
     * consumeと同じだが、能力がオフでも払える。1.5.2から、オーバーロードのロック中は拒否する（ロック中はどの技能も
     * 始められない）。既に動いているモードの支払いには{@link #consumeRunning}を使う。
     */
    public boolean consumeInMode(Identifier skill, float cpCost, float overloadCost, boolean creative) {
        return pay(skill, cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative, false, true, true, true);
    }
    /** 原作CPData.performWithForceの形（{@link #consumeWithForce(float, float, boolean)}）。技能の倍率と熟練度を使う。 */
    public boolean consumeWithForce(Identifier skill, float cpCost, float overloadCost, boolean creative) {
        return pay(skill, cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative, false, false, true, true);
    }
    /**
     * 既に動いているモードの支払い（Vector Deviation・Vector Reflectionの止め・反射）。原作CPData.performと同じく、オーバーロードの
     * ロックや有効状態に関係なく、CP不足でだけ拒否する。防御の支払いなので、CPの回復待ちはやり直さない。
     * 新しく始めるときには使わない（ロック中の開始は{@link #consumeInMode}などが拒否する）。
     */
    public boolean consumeRunning(Identifier skill, float cpCost, float overloadCost, boolean creative) {
        return pay(skill, cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative, false, true, false, false);
    }
    /** {@link #consumeRunning}と同じだが、足りるか確かめずに課し、CPは0で止まる（原作CPData.performWithForce）。CPセーフティだけは止める。 */
    public boolean consumeRunningWithForce(Identifier skill, float cpCost, float overloadCost, boolean creative) {
        return pay(skill, cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative, false, false, false, false);
    }
    /** 回復待ちを作らず・延ばさずに払う開始の費用（光線・甲虫・領域）。他の技能が作った待ちは解除しない。 */
    public boolean consumeQuiet(Identifier skill, float cpCost, float overloadCost, boolean creative) {
        int wait = cpRecoveryDelay;
        boolean paid = consume(skill, cpCost, overloadCost, creative);
        cpRecoveryDelay = wait;
        return paid;
    }
    /**
     * 防御などの費用: CPだけを減らす。回復待ちと過負荷は動かさない。判定は呼ぶ側が済ませる。技能の倍率は掛けない
     * （掛けるなら{@link #payWithoutWait(Identifier, float)}）。1.5.2から、払った分だけ最大CPが伸びる。
     */
    public void payWithoutWait(float cpCost) {
        requireNonnegativeFinite(cpCost);
        if (readOnly) return;
        cp = Math.max(0, cp - cpCost);
        if (hasAbility()) growMaxCp(cpCost);
    }
    /**
     * {@link #payWithoutWait(float)}に、技能のcp_consume_speedを掛けて払う（1.5.2）。呼ぶ側は払えるかを
     * {@link #skillCp}の額で確かめる。
     */
    public void payWithoutWait(Identifier skill, float cpCost) {
        requireNonnegativeFinite(cpCost);
        payWithoutWait(cpFor(skill, cpCost));
    }
    /** アトミックな資源のトランザクション。原作と同じく、クリエイティブは消費を免除する。技能が分からないので、熟練度0%として扱う。 */
    public boolean consume(float cpCost, float overloadCost, boolean creative) {
        return pay(null, cpCost, overloadCost, creative, true, true, true, true);
    }
    /**
     * 原作CPData.performの形: 有効状態に関係なく、CP不足で拒否する（クリエイティブは拒否されない）。原作の切り替え型モード
     * （Vector Deviation、Storm Wing、Vector Reflection）はオーバーロード中もこの方法で払い続けたが、1.5.2から
     * オーバーロードのロック中は拒否する。動いているモードの支払いは{@link #consumeRunning}。
     */
    public boolean consumeInMode(float cpCost, float overloadCost, boolean creative) {
        return pay(null, cpCost, overloadCost, creative, false, true, true, true);
    }
    /**
     * 原作CPData.performWithForce: 足りるか確かめずに課し、CPは拒否せず0で止まる。テレポーターの移動技能は距離をこの方法で払う。
     * CPセーフティは止める（falseを返す。v1.5.0）。1.5.2から、オーバーロードのロック中も拒否する。
     */
    public boolean consumeWithForce(float cpCost, float overloadCost, boolean creative) {
        return pay(null, cpCost, overloadCost, creative, false, false, true, true);
    }
    /**
     * 支払いの共通の流れ。needActive: 能力がオフなら拒否。needCp: CP不足なら拒否（falseは強制で、0で止まる）。refuseLocked:
     * オーバーロードのロック中なら拒否。castWait: CPを払ったらCPの回復待ちをやり直す（技能の発動の支払い）。
     */
    private boolean pay(@Nullable Identifier skill, float cpCost, float overloadCost, boolean creative,
                        boolean needActive, boolean needCp, boolean refuseLocked, boolean castWait) {
        requireNonnegativeFinite(cpCost); requireNonnegativeFinite(overloadCost);
        var perform = perform(cpCost, overloadCost); cpCost = perform[0]; overloadCost = perform[1];
        if (readOnly || !hasAbility() || (needActive && !active) || (refuseLocked && lockBlocks(skill)) || (needCp && !creative && cp < cpCost)) return false;
        if (!creative && !safetyClears(cpCost)) { tripSafety(cpCost); return false; }
        charge(skill, cpCost, overloadCost, creative, castWait);
        return true;
    }
    /** 原作CalcEvent.SkillPerform: リスナーが残した[cp, overload]。負にはしない。 */
    private float[] perform(float cpCost, float overloadCost) {
        if (!posting() || readOnly || !hasAbility()) return new float[]{cpCost, overloadCost};
        var event = new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillPerform(owner, overloadCost, cpCost);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        float cpAfter = Float.isFinite(event.cp) ? Math.max(0, event.cp) : cpCost;
        float overloadAfter = Float.isFinite(event.overload) ? Math.max(0, event.overload) : overloadCost;
        return new float[]{cpAfter, overloadAfter};
    }
    /**
     * 資源を課す。1.5.2の回復待ち:
     * <ul>
     * <li>CPの回復待ちは、技能の発動でCPを実際に払った（0より多い）ときだけやり直す（castWait）。過負荷だけの起動では触れない。</li>
     * <li>過負荷の回復待ちは、過負荷が実際に増えた（0より多い）ときだけやり直す。長さはコースで決まる待ち（{@link #overloadWait}。
     * 熟練度では縮まない）。待ちの間の回復の割合は{@link #overloadWaitRecovery}。CPだけの支払いでは触れない。他の技能が作った、より長い待ちは縮めない。</li>
     * </ul>
     */
    private void charge(@Nullable Identifier skill, float cpCost, float overloadCost, boolean creative, boolean castWait) {
        if (!creative) {
            cp = Math.max(0, cp - cpCost); overload = Math.min(maxOverload, overload + overloadCost);
            int course = GenericSkills.topCourse(this);
            if (castWait && cpCost > 0) cpRecoveryDelay = io.github.pinchan4273.reacademycraft.config.AcademyConfig.cpRecoverCooldown(course);
            if (overloadCost > 0) {
                float rate = overloadWaitRecovery(skill, course);
                overloadWaitRate = overloadRecoveryDelay > 0 ? Math.min(overloadWaitRate, rate) : rate;
                overloadRecoveryDelay = Math.max(overloadRecoveryDelay, overloadWait(skill, course));
            }
            if (overload >= maxOverload && !overloadLocked) { overloadLocked = true; lockAllowed.clear(); post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload::new); }
        }
        growMaxCp(cpCost);
        float overloadIncrease = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addOverload(level) - bonusOverload,
                Math.min(10, overloadCost * io.github.pinchan4273.reacademycraft.config.AcademyConfig.maxOverloadIncrRate())));
        bonusOverload += overloadIncrease; maxOverload += overloadIncrease;
    }
    /**
     * 過負荷が増えたときの回復待ち（1.5.2）: コースで決まる待ち（1.5.1 と同じく熟練度では縮まない）。
     * 払った技能の熟練度は、待ちの間の回復の割合（{@link #overloadWaitRecovery}）にだけ使う。
     */
    public int overloadWait(@Nullable Identifier skill, int course) {
        return io.github.pinchan4273.reacademycraft.config.AcademyConfig.overloadRecoverCooldown(course);
    }
    /**
     * 回復待ちの間に残る回復の割合（1.5.2）: 払った技能の熟練度 × コースごとの熟練度100%の割合（既定 0.12/0.13/0.14/0.15）。
     * 熟練度0%は0（1.5.1 と同じく待ちの間は回復しない）。技能が分からない支払いは0%として扱う。
     */
    public float overloadWaitRecovery(@Nullable Identifier skill, int course) {
        float p = skill == null ? 0 : Math.max(0, Math.min(1, getProficiency(skill)));
        return p * io.github.pinchan4273.reacademycraft.config.AcademyConfig.overloadWaitRecoveryAtFull(course);
    }
    /** 払ったCPに応じて最大CPを伸ばす（成長の枠まで）。1.5.2から、維持費・防御などのどの支払いでも伸びる。 */
    private void growMaxCp(float paid) {
        if (!(paid > 0) || !hasAbility()) return;
        float cpIncrease = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level) - bonusCp,
                paid * io.github.pinchan4273.reacademycraft.config.AcademyConfig.maxCpIncrRate()));
        // 1.5.2: 小さい伸びを maxCp へ直接足し続けると、桁の大きい maxCp の方だけ float の丸めがたまり、
        // 伸びが上限に着いても最大CPが手前（例: 24000 に対して 23954.8）で止まった。伸びの外の部分（基礎）を double で持ち、
        // 最大CP＝基礎＋伸び を毎回1回の丸めで決める。最大CPを外から変えたとき（昇級・設定・コマンド・読み込み）は、その値を基礎にし直す。
        if (maxCp != grownMax) grownBase = (double) maxCp - bonusCp;
        bonusCp += cpIncrease;
        maxCp = (float) (grownBase + bonusCp);
        // 前の版で取り残された分（丸めの差。最大CPの0.5%まで）は、伸びが上限に着いているときに合わせる。大きく違う値（管理者・テストの
        // 設定・外部の特殊な値）は、丸めの差ではないので変えない。
        if (bonusCp >= io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level)) {
            float target = initialCp() + bonusCp, gap = target - maxCp;
            if (gap > 0 && gap <= target * .005f) { maxCp = target; grownBase = (double) maxCp - bonusCp; }
        }
        grownMax = maxCp;
    }
    /** growMaxCp が最後に決めた最大CPと、そのときの基礎（最大CP − 伸び）。保存しない（読み込み直後は決め直す）。 */
    private float grownMax = Float.NaN;
    private double grownBase;

    /** schema 2（{@link AbilityDataSchema}）で書く。読んだときにあった知らないキーは、ルートにも各区分にも残す。 */
    public CompoundTag save() {
        CompoundTag tag = preserved.copy();
        if (readOnly) return tag;
        tag.putInt(VERSION_KEY, VERSION);
        CompoundTag abilityPart = section(tag, ABILITY);
        if (ability == null) abilityPart.remove(CATEGORY);
        else abilityPart.putString(CATEGORY, ability.toString());
        abilityPart.putInt(LEVEL, level);
        abilityPart.putFloat(LEVEL_EXP, levelExperience);
        ListTag learned = new ListTag();
        skills.forEach((id, proficiency) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(SKILL, id.toString()); entry.putFloat(EXP, proficiency); learned.add(entry);
        });
        abilityPart.put(SKILLS, learned);

        CompoundTag cpPart = section(tag, CP);
        cpPart.putBoolean(ACTIVATED, active);
        cpPart.putFloat(CUR_CP, cp); cpPart.putFloat(MAX_CP, maxCp); cpPart.putFloat(ADD_MAX_CP, bonusCp);
        cpPart.putFloat(CUR_OVERLOAD, overload); cpPart.putFloat(MAX_OVERLOAD, maxOverload); cpPart.putFloat(ADD_MAX_OVERLOAD, bonusOverload);
        cpPart.putBoolean(OVERLOAD_FINE, !overloadLocked);
        cpPart.putInt(UNTIL_RECOVER, cpRecoveryDelay); cpPart.putInt(UNTIL_OVERLOAD_RECOVER, overloadRecoveryDelay);
        cpPart.putInt(CP_MODEL, CURRENT_CP_MODEL);
        if (darkBody) cpPart.putBoolean(DARK_BODY, true); else cpPart.remove(DARK_BODY);
        clampBodyDamage();
        if (bodyDamage > 0) cpPart.putFloat(BODY_DAMAGE, bodyDamage); else cpPart.remove(BODY_DAMAGE);

        CompoundTag presetPart = section(tag, PRESET);
        presetPart.putInt(PRESET_ID, currentPreset);
        ListTag table = new ListTag();
        for (Identifier[] row : presets) {
            ListTag line = new ListTag();
            for (Identifier id : row) line.add(StringTag.valueOf(id == null ? "" : id.toString()));
            table.add(line);
        }
        presetPart.put(PRESETS, table);

        // 読んだときのCooldownDataの写しから、このデータが管理する技能（技能の一覧にある技能と、学んだ技能）の項目だけを除き、
        // 今のクールダウンを書き直す。知らないキー（拡張の付記、まだ知らない技能のクールダウン）はそのまま残る。
        CompoundTag cooling = section(tag, COOLDOWN);
        for (String key : java.util.List.copyOf(cooling.keySet())) if (managesCooldown(key)) cooling.remove(key);
        skills.keySet().forEach(id -> { int ticks = getCooldown(id); if (ticks > 0) cooling.putInt(id.toString(), ticks); });
        // 選んだ設定だけを書く。選んでいなければ区分に触れない（区分が無ければ作らない）。
        if (lightShieldMode != null) section(tag, SETTINGS).putString(LIGHT_SHIELD_MODE, lightShieldMode.id());
        // v1.5.0の設定。既定の値なら書かず、読んだときの区分に残っていた古い値も外す（知らないキーには触れない）。
        if (cpSafety != DEFAULT_CP_SAFETY) section(tag, SETTINGS).putBoolean(CP_SAFETY, cpSafety);
        else removeSetting(tag, CP_SAFETY);
        if (cpSafetyPercent != CP_SAFETY_DEFAULT_PERCENT) section(tag, SETTINGS).putInt(CP_SAFETY_PERCENT, cpSafetyPercent);
        else removeSetting(tag, CP_SAFETY_PERCENT);
        // 1.5.2: セーフティの並び（プリセットごと）と形態の予約（既定なら書かない）。
        for (int i = 0; i < safetyOrders.length; i++) {
            if (!safetyOrders[i].isEmpty()) section(tag, SETTINGS).putString(AbilityDataSchema.CP_SAFETY_ORDER + i, safetyOrders[i]);
            else removeSetting(tag, AbilityDataSchema.CP_SAFETY_ORDER + i);
        }
        if (formReservePercent != FORM_RESERVE_DEFAULT) section(tag, SETTINGS).putInt(AbilityDataSchema.FORM_RESERVE, formReservePercent);
        else removeSetting(tag, AbilityDataSchema.FORM_RESERVE);
        if (!darkBodyRespawn) section(tag, SETTINGS).putBoolean(DARK_BODY_RESPAWN, false);
        else removeSetting(tag, DARK_BODY_RESPAWN);
        if (flightDoubleTap) section(tag, SETTINGS).putBoolean(FLIGHT_DOUBLE_TAP, true);
        else removeSetting(tag, FLIGHT_DOUBLE_TAP);
        // 1.5.2: 反射方向とPvPの候補（既定なら書かない）。
        if (reflectionDirection != ReflectionDirection.DEFAULT) section(tag, SETTINGS).putString(REFLECTION_DIRECTION, reflectionDirection.id());
        else removeSetting(tag, REFLECTION_DIRECTION);
        if (reflectionPvp) section(tag, SETTINGS).putBoolean(REFLECTION_PVP, true);
        else removeSetting(tag, REFLECTION_PVP);
        // 1.5.2: パッシブに切り替えた技能（空なら書かない）。
        if (!passiveSkills.isEmpty()) {
            ListTag passive = new ListTag();
            passiveSkills.forEach(id -> passive.add(StringTag.valueOf(id.toString())));
            section(tag, SETTINGS).put(PASSIVE_SKILLS, passive);
        } else removeSetting(tag, PASSIVE_SKILLS);
        if (!beetleContactBreak) section(tag, SETTINGS).putBoolean(BEETLE_CONTACT_BREAK, false);
        else removeSetting(tag, BEETLE_CONTACT_BREAK);
        if (beetleRepair != DarkMatterBeetles.Repair.AUTO) section(tag, SETTINGS).putString(BEETLE_REPAIR, beetleRepair.id());
        else removeSetting(tag, BEETLE_REPAIR);
        if (beetleStance != DarkMatterBeetles.Stance.MID) section(tag, SETTINGS).putString(BEETLE_STANCE, beetleStance.id());
        else removeSetting(tag, BEETLE_STANCE);
        if (beetleFollow != DarkMatterBeetles.FOLLOW_DEFAULT) section(tag, SETTINGS).putInt(BEETLE_FOLLOW, beetleFollow);
        else removeSetting(tag, BEETLE_FOLLOW);
        if (!beetleTargets.isEmpty()) {
            CompoundTag beetleTargetTag = section(section(tag, SETTINGS), BEETLE_TARGETS);
            beetleTargets.forEach((kind, on) -> beetleTargetTag.putBoolean(kind.id(), on));
        } else removeSetting(tag, BEETLE_TARGETS);
        CompoundTag heaven = heavenSettings.save();
        if (!heaven.isEmpty()) section(tag, SETTINGS).put(HEAVEN_SETTINGS, heaven);
        else removeSetting(tag, HEAVEN_SETTINGS);
        if (beetleRecords.isEmpty()) tag.remove(BEETLE_RECORDS);
        else tag.put(BEETLE_RECORDS, beetleRecords.copy());
        if (electronMissileAimMode != null) section(tag, SETTINGS).putString(ELECTRON_MISSILE_AIM, electronMissileAimMode.id());
        if (!electronMissileTargets.isEmpty()) {
            CompoundTag targets = section(section(tag, SETTINGS), ELECTRON_MISSILE_TARGETS);
            electronMissileTargets.forEach((kind, on) -> targets.putBoolean(kind.id(), on));
        }
        // 1.5.2: 解析度（空なら書かない）。
        if (analysis.isEmpty()) tag.remove(VECTOR_ANALYSIS);
        else {
            CompoundTag analysed = new CompoundTag();
            analysis.forEach((key, value) -> {
                CompoundTag entry = new CompoundTag();
                entry.putFloat(ANALYSIS_VALUE, value);
                String label = analysisLabels.get(key);
                if (label != null) entry.putString(ANALYSIS_LABEL, label);
                analysed.put(key, entry);
            });
            tag.put(VECTOR_ANALYSIS, analysed);
        }
        return tag;
    }
    /** SkillSettingsの区分からその設定を外す（区分が無ければ作らない）。1.20.1のgetCompound(...).remove(...)に当たる。 */
    private static void removeSetting(CompoundTag tag, String key) { tag.getCompound(SETTINGS).ifPresent(settings -> settings.remove(key)); }

    /**
     * 既存の状態を置き換える。古い値を加えたり、変更可能なNBTを呼び出し元と共有したりしない。
     * 以前の版の形（schema 1）は、{@link PlayerDataMigration}で新しい形へ移してから読む。新しすぎる版は読まずにそのまま保つ。
     */
    public void load(CompoundTag tag) {
        loads++; clearSkills(); cpRecoveryDelay = overloadRecoveryDelay = 0; upkeeps.clear();
        overloadLocked = false; lockAllowed.clear(); bonusCp = bonusOverload = 0;
        ability = null;
        level = 0;
        levelExperience = 0;
        cp = overload = 0;
        maxCp = DEFAULT_MAX_CP;
        maxOverload = DEFAULT_MAX_OVERLOAD;
        active = false;
        lightShieldMode = null;
        electronMissileAimMode = null;
        electronMissileTargets.clear();
        beetleContactBreak = true; beetleRepair = DarkMatterBeetles.Repair.AUTO; beetleStance = DarkMatterBeetles.Stance.MID; beetleFollow = DarkMatterBeetles.FOLLOW_DEFAULT; beetleRecords = new ListTag();
        beetleTargets.clear();
        heavenSettings = new HeavenSettings();
        darkBody = false; darkBodyRespawn = true; darkBodyLossRate = -1; flightDoubleTap = false; bodyDamage = 0; passiveRunning = 0;
        reflectionDirection = ReflectionDirection.DEFAULT; reflectionPvp = false;
        cpSafety = DEFAULT_CP_SAFETY; cpSafetyPercent = CP_SAFETY_DEFAULT_PERCENT; safetyBlockedCost = -1; safetyTripped = false;
        java.util.Arrays.fill(safetyOrders, ""); formReservePercent = FORM_RESERVE_DEFAULT; safetyEnded.clear(); lastUpkeepPerTick = 0;
        analysis.clear(); analysisLabels.clear(); analysisVersion++;
        CompoundTag source = PlayerDataMigration.needed(tag) ? PlayerDataMigration.fromSchemaOne(tag) : tag;
        preserved = source.copy();
        readOnly = !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(source, VERSION_KEY, Tag.TAG_INT) || source.getIntOr(VERSION_KEY, 0) != VERSION;
        if (readOnly) return;

        CompoundTag abilityPart = source.getCompoundOrEmpty(ABILITY), cpPart = source.getCompoundOrEmpty(CP);
        CompoundTag presetPart = source.getCompoundOrEmpty(PRESET), cooling = source.getCompoundOrEmpty(COOLDOWN);
        String category = abilityPart.getStringOr(CATEGORY, "");
        if (!category.isEmpty()) ability = Identifier.tryParse(category);
        // 1.4.0: 解析できないカテゴリIDを含む保存データは壊さない。能力が無いと読むと、保存のときに学んだ技能まで
        // 消えていたので、読み取り専用（休眠）にして、元のNBTをそのまま保つ（より新しい版のデータと同じ扱い）。
        if (!category.isEmpty() && ability == null) { readOnly = true; return; }
        level = hasAbility() ? Math.max(1, Math.min(5, abilityPart.getIntOr(LEVEL, 0))) : 0;
        levelExperience = hasAbility() ? readNonnegativeFinite(abilityPart, LEVEL_EXP, 0) : 0;
        // 現在の値より先に最大値を戻し、現在の値をその範囲へ切り詰める。
        maxCp = readNonnegativeFinite(cpPart, MAX_CP, DEFAULT_MAX_CP);
        maxOverload = readNonnegativeFinite(cpPart, MAX_OVERLOAD, DEFAULT_MAX_OVERLOAD);
        cp = Math.min(readNonnegativeFinite(cpPart, CUR_CP, 0), maxCp);
        overload = Math.min(readNonnegativeFinite(cpPart, CUR_OVERLOAD, 0), maxOverload);
        active = hasAbility() && cpPart.getBooleanOr(ACTIVATED, false);
        darkBody = DarkMatter.CATEGORY.equals(ability) && cpPart.getBooleanOr(DARK_BODY, false);
        // 旧セーブ（損傷の値なし）は0から。読み込んだ値は、形態・現在CP・最大CPにそろえる。
        bodyDamage = readNonnegativeFinite(cpPart, BODY_DAMAGE, 0);
        clampBodyDamage();
        overloadLocked = io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(cpPart, OVERLOAD_FINE, io.github.pinchan4273.reacademycraft.world.NbtCompat.TAG_ANY_NUMERIC) && !cpPart.getBooleanOr(OVERLOAD_FINE, false);
        // 原作CPDataはuntilRecoverとuntilOverloadRecoverをそのまま保存する。より長く設定した待ちは再読込を越えて残らなければならないので、
        // 設定が決して生まない値だけを拒否する。
        cpRecoveryDelay = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.MAX_RECOVER_COOLDOWN, cpPart.getIntOr(UNTIL_RECOVER, 0)));
        overloadRecoveryDelay = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.MAX_RECOVER_COOLDOWN, cpPart.getIntOr(UNTIL_OVERLOAD_RECOVER, 0)));
        // 1.5.2: 待ちの間の回復の割合は保存しない。読み込んだ待ちの間は0（回復しない）とし、前の値を残さない。
        overloadWaitRate = 0;
        // 1.5.2: 能力の飛行中かどうかも保存しない。読み込んだ直後は飛んでいないものとし、次のtickの始めに決め直す。
        abilityFlight = false;
        // 1.5.2: 稼働の残りも保存しない。読み込み直後は稼働していない。
        actionLeft.clear();
        // 天界発現の CP の回復（未元物質人間）も保存しない。
        heavenCpShare = 0; heavenCpLeft = 0;
        bonusCp = Math.min(maxCp, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level), readNonnegativeFinite(cpPart, ADD_MAX_CP, 0)));
        bonusOverload = Math.min(maxOverload, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addOverload(level), readNonnegativeFinite(cpPart, ADD_MAX_OVERLOAD, 0)));
        currentPreset = Math.max(0, Math.min(PRESET_COUNT - 1, presetPart.getIntOr(PRESET_ID, 0)));
        ListTag learned = abilityPart.getListOrEmpty(SKILLS);
        for (int i = 0; hasAbility() && i < Math.min(MAX_SKILLS, learned.size()); i++) {
            CompoundTag entry = learned.getCompoundOrEmpty(i); String id = entry.getStringOr(SKILL, "");
            Identifier skill = Identifier.tryParse(id);
            if (skill == null || id.isEmpty() || id.length() > 256) continue;
            skills.put(skill, Math.min(1, readNonnegativeFinite(entry, EXP, 0)));
            int cooldown = Math.max(0, Math.min(72000, cooling.getIntOr(id, 0)));
            if (cooldown > 0) cooldowns.put(skill, cooldown);
        }
        // 1.5.2: パッシブに切り替えた技能。習得済みで切り替えられる技能だけを読む（プリセットより先に読み、パッシブの技能をスロットに入れない）。
        ListTag passive = source.getCompoundOrEmpty(SETTINGS).getListOrEmpty(PASSIVE_SKILLS);
        for (int i = 0; hasAbility() && i < Math.min(PassiveSkills.SWITCHABLE.size(), passive.size()); i++) {
            Identifier skill = Identifier.tryParse(passive.getStringOr(i, ""));
            if (skill != null && skills.containsKey(skill) && PassiveSkills.switchable(skill)) passiveSkills.add(skill);
        }
        ListTag table = presetPart.getListOrEmpty(PRESETS);
        for (int p = 0; p < Math.min(PRESET_COUNT, table.size()); p++) {
            ListTag row = table.getListOrEmpty(p);
            for (int s = 0; s < Math.min(SLOT_COUNT, row.size()); s++) {
                String id = row.getStringOr(s, "");
                Identifier skill = id.isEmpty() ? null : Identifier.tryParse(id);
                // 1.5.2: 廃止した本体の技能の旧ID（未元装甲）は、習得の記録が残っていてもスロットへ戻さない（空きにする。再保存でも消える）。
                if (skill != null && skills.containsKey(skill) && !GenericSkills.isPassive(skill) && !passiveSkills.contains(skill)
                        && !DarkMatter.retired(skill)) presets[p][s] = skill;
            }
        }
        if (hasAbility() && !(io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(cpPart, CP_MODEL, Tag.TAG_INT) && cpPart.getIntOr(CP_MODEL, 0) >= CURRENT_CP_MODEL)) migrateCapacity(cpPart);
        // 設定は能力の有無に関わらず読む（能力を失っても、選んだモードは残る）。知らない値は読まず、保存でも書き換えない。
        lightShieldMode = LightShieldMode.parse(source.getCompoundOrEmpty(SETTINGS).getStringOr(LIGHT_SHIELD_MODE, ""));
        electronMissileAimMode = ElectronMissileAimMode.parse(source.getCompoundOrEmpty(SETTINGS).getStringOr(ELECTRON_MISSILE_AIM, ""));
        CompoundTag targets = source.getCompoundOrEmpty(SETTINGS).getCompoundOrEmpty(ELECTRON_MISSILE_TARGETS);
        for (var kind : ElectronMissileTargetKind.values())
            if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(targets, kind.id(), Tag.TAG_BYTE)) electronMissileTargets.put(kind, targets.getBooleanOr(kind.id(), false));
        CompoundTag settings = source.getCompoundOrEmpty(SETTINGS);
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, CP_SAFETY, Tag.TAG_BYTE)) cpSafety = settings.getBooleanOr(CP_SAFETY, DEFAULT_CP_SAFETY);
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, CP_SAFETY_PERCENT, Tag.TAG_INT))
            cpSafetyPercent = net.minecraft.util.Mth.clamp(settings.getIntOr(CP_SAFETY_PERCENT, CP_SAFETY_DEFAULT_PERCENT), CP_SAFETY_MIN_PERCENT, CP_SAFETY_MAX_PERCENT);
        // 1.5.2: 読めない並びと範囲の外の予約は既定に戻す。
        for (int i = 0; i < safetyOrders.length; i++) {
            String code = settings.getStringOr(AbilityDataSchema.CP_SAFETY_ORDER + i, "");
            safetyOrders[i] = SafetyPriority.valid(code) ? code : "";
        }
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, AbilityDataSchema.FORM_RESERVE, Tag.TAG_INT)) {
            int reserve = settings.getIntOr(AbilityDataSchema.FORM_RESERVE, FORM_RESERVE_DEFAULT);
            formReservePercent = reserve >= FORM_RESERVE_MIN && reserve <= FORM_RESERVE_MAX ? reserve : FORM_RESERVE_DEFAULT;
        }
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, DARK_BODY_RESPAWN, Tag.TAG_BYTE)) darkBodyRespawn = settings.getBooleanOr(DARK_BODY_RESPAWN, true);
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, FLIGHT_DOUBLE_TAP, Tag.TAG_BYTE)) flightDoubleTap = settings.getBooleanOr(FLIGHT_DOUBLE_TAP, false);
        // 1.5.2: 旧セーブ（キー無し）と知らない値は既定（攻撃者へ・PvPの相手を含めない）。
        var direction = ReflectionDirection.parse(settings.getStringOr(REFLECTION_DIRECTION, ""));
        if (direction != null) reflectionDirection = direction;
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, REFLECTION_PVP, Tag.TAG_BYTE)) reflectionPvp = settings.getBooleanOr(REFLECTION_PVP, false);
        heavenSettings = HeavenSettings.load(settings.getCompoundOrEmpty(HEAVEN_SETTINGS));
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, BEETLE_CONTACT_BREAK, Tag.TAG_BYTE)) beetleContactBreak = settings.getBooleanOr(BEETLE_CONTACT_BREAK, true);
        var repair = DarkMatterBeetles.Repair.parse(settings.getStringOr(BEETLE_REPAIR, ""));
        if (repair != null) beetleRepair = repair;
        var stance = DarkMatterBeetles.Stance.parse(settings.getStringOr(BEETLE_STANCE, ""));
        if (stance != null) beetleStance = stance;
        // 旧セーブ（キー無し）と、許されない値（範囲外・刻み外）は既定の32のまま。
        if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(settings, BEETLE_FOLLOW, Tag.TAG_INT) && DarkMatterBeetles.validFollow(settings.getIntOr(BEETLE_FOLLOW, 0))) beetleFollow = settings.getIntOr(BEETLE_FOLLOW, 0);
        CompoundTag beetleTargetTag = settings.getCompoundOrEmpty(BEETLE_TARGETS);
        for (var kind : ElectronMissileTargetKind.values())
            if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(beetleTargetTag, kind.id(), Tag.TAG_BYTE)) beetleTargets.put(kind, beetleTargetTag.getBooleanOr(kind.id(), false));
        // 1.5.2: 解析度は系統に関わらず読む。形の違う要素・範囲外の値は読まない（上限の数まで）。
        CompoundTag analysed = source.getCompoundOrEmpty(VECTOR_ANALYSIS);
        for (String key : analysed.keySet()) {
            if (analysis.size() >= MAX_ANALYSIS_ENTRIES) break;
            if (key.isEmpty() || key.length() > 256 || !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(analysed, key, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = analysed.getCompoundOrEmpty(key);
            float value = Math.min(1, readNonnegativeFinite(entry, ANALYSIS_VALUE, 0));
            if (value <= 0) continue;
            analysis.put(key, value);
            String label = entry.getStringOr(ANALYSIS_LABEL, "");
            if (!label.isEmpty()) analysisLabels.put(key, label.length() > 64 ? label.substring(0, 64) : label);
        }
        // 記録は系統が未元物質のときだけ読む。形の違う要素は読まない（最大で3体）。
        if (DarkMatter.CATEGORY.equals(ability)) {
            ListTag records = source.getListOrEmpty(BEETLE_RECORDS);
            for (int i = 0; i < Math.min(DarkMatterBeetles.MAX_BEETLES, records.size()); i++)
                if (records.get(i) instanceof CompoundTag record) beetleRecords.add(record.copy());
        }
    }

    /**
     * 1.0.0（と1.0.0の後の開発版）のセーブの最大CPを、新しい容量へ一度だけ移す（CPDataにCP_MODELが無いデータ）。
     * 1.0.0の標準の式（旧init_cp + コースのボーナス + 使用による成長）どおりのデータは、コースのボーナスを除いた容量（育てた容量）を
     * 昇級と同じ規則で引き継ぐ: 新しい最大 = 新しい基礎と新しい上限（基礎 + 成長枠）の間に収めた値、成長済み = 最大 − 基礎。
     * コースのボーナスは回復へ置き換わったので、容量には二重に残さない。式に合わない最大CP（外部mod・管理者・コマンドによる特殊な値）は
     * 標準のものとみなさず、そのまま保つ（次に再計算されるときの扱いは1.0.0と同じ）。現在のCPは新しい最大を超えない限り変えない。
     * 移すのは読み込んだ値からの純粋な計算なので、同じデータを何度読んでも同じ結果になる。保存するとCP_MODELが付き、二度と移さない。
     */
    private void migrateCapacity(CompoundTag cpPart) {
        float savedGrowth = readNonnegativeFinite(cpPart, ADD_MAX_CP, 0), courseBonus = GenericSkills.legacyCpBonus(this);
        float legacyMax = io.github.pinchan4273.reacademycraft.config.AcademyConfig.legacyInitCp(level) + courseBonus + savedGrowth;
        if (Math.abs(maxCp - legacyMax) > Math.max(.01f, legacyMax * 1e-5f)) return;
        float base = initialCp(), grown = maxCp - courseBonus;
        float carried = Math.max(base, Math.min(base + io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level), grown));
        bonusCp = carried - base; maxCp = carried; cp = Math.min(cp, maxCp);
    }

    /**
     * CooldownDataのこのキーをこのデータが管理するか。今の技能の一覧にある技能か、学んだ技能（読んだときにクールダウンを
     * 取り込んだもの）なら管理する。IDとして読めるだけの知らない技能は管理せず、保存で消さない。
     */
    private boolean managesCooldown(String key) {
        Identifier id = Identifier.tryParse(key);
        return id != null && (SkillCatalog.find(id) != null || skills.containsKey(id));
    }

    /** 使用で最大CPに加わった分（原作CPData.addMaxCP）。 */
    public float getAddedMaxCp() { return bonusCp; }
    /** 使用で最大オーバーロードに加わった分（原作CPData.addMaxOverload）。 */
    public float getAddedMaxOverload() { return bonusOverload; }
    /** CPの回復が始まるまでの残りtick（原作CPData.untilRecover）。 */
    public int getCpRecoveryDelay() { return cpRecoveryDelay; }
    /** テスト用の入口: CP回復の待ちを置く。 */
    public void setCpRecoveryDelay(int ticks) { cpRecoveryDelay = Math.max(0, ticks); }
    /** オーバーロードの回復が始まるまでの残りtick（原作CPData.untilOverloadRecover）。 */
    public int getOverloadRecoveryDelay() { return overloadRecoveryDelay; }

    /** Light Shieldのモード。選んでいなければ{@link LightShieldMode#DEFAULT}。 */
    public LightShieldMode getLightShieldMode() { return lightShieldMode == null ? LightShieldMode.DEFAULT : lightShieldMode; }
    /** プレイヤーがモードを選んだか（選んでいなければ既定で動き、保存にも書かない）。 */
    public boolean hasChosenLightShieldMode() { return lightShieldMode != null; }
    /** モードを選ぶ。読み取り専用のデータでは例外（呼び出し元の{@link LightShieldSettings}が先に断る）。 */
    public void setLightShieldMode(LightShieldMode mode) {
        requireWritable();
        lightShieldMode = java.util.Objects.requireNonNull(mode);
    }

    /** Electron Missileの照準のモード。選んでいなければ{@link ElectronMissileAimMode#DEFAULT}（AUTO）。 */
    public ElectronMissileAimMode getElectronMissileAimMode() {
        return electronMissileAimMode == null ? ElectronMissileAimMode.DEFAULT : electronMissileAimMode;
    }
    /** プレイヤーが照準のモードを選んだか（選んでいなければ既定で動き、保存にも書かない）。 */
    public boolean hasChosenElectronMissileAimMode() { return electronMissileAimMode != null; }
    /** 照準のモードを選ぶ。読み取り専用のデータでは例外（呼び出し元の{@link ElectronMissileSettings}が先に断る）。 */
    public void setElectronMissileAimMode(ElectronMissileAimMode mode) {
        requireWritable();
        electronMissileAimMode = java.util.Objects.requireNonNull(mode);
    }
    /** Electron Missileがこの分類を撃ってよいか。選んでいなければ既定。 */
    public boolean electronMissileTargets(ElectronMissileTargetKind kind) {
        return electronMissileTargets.getOrDefault(kind, kind.defaultOn());
    }
    /** プレイヤーがこの分類のON／OFFを選んだか。 */
    public boolean hasChosenElectronMissileTarget(ElectronMissileTargetKind kind) { return electronMissileTargets.containsKey(kind); }
    /** この分類のON／OFFを選ぶ。読み取り専用のデータでは例外（呼び出し元の{@link ElectronMissileSettings}が先に断る）。 */
    public void setElectronMissileTarget(ElectronMissileTargetKind kind, boolean on) {
        requireWritable();
        electronMissileTargets.put(java.util.Objects.requireNonNull(kind), on);
    }

    /** 甲虫の接触ブロック破壊の設定。 */
    public boolean isBeetleContactBreak() { return beetleContactBreak; }
    public void setBeetleContactBreak(boolean on) { requireWritable(); beetleContactBreak = on; }
    /** 甲虫の修復方式。 */
    public DarkMatterBeetles.Repair getBeetleRepair() { return beetleRepair; }
    public void setBeetleRepair(DarkMatterBeetles.Repair repair) { requireWritable(); beetleRepair = java.util.Objects.requireNonNull(repair); }
    /** 甲虫の攻撃方針。 */
    public DarkMatterBeetles.Stance getBeetleStance() { return beetleStance; }
    public void setBeetleStance(DarkMatterBeetles.Stance stance) { requireWritable(); beetleStance = java.util.Objects.requireNonNull(stance); }
    /** 甲虫がこの分類を自動で狙ってよいか。選んでいなければ既定（{@link ElectronMissileTargetKind#defaultOn()}）。 */
    public boolean beetleTargets(ElectronMissileTargetKind kind) { return beetleTargets.getOrDefault(kind, kind.defaultOn()); }
    /** この分類のON／OFFを選ぶ。読み取り専用のデータでは例外（呼び出し元の{@link AbilitySettings}が先に断る）。 */
    public void setBeetleTarget(ElectronMissileTargetKind kind, boolean on) { requireWritable(); beetleTargets.put(java.util.Objects.requireNonNull(kind), on); }
    /** 甲虫の追従開始距離（ブロック。{@link DarkMatterBeetles#validFollow}の値）。 */
    public int getBeetleFollow() { return beetleFollow; }
    /** 追従開始距離を置く。許されない値は例外（呼び出し元の{@link AbilitySettings}が先に断る）。 */
    public void setBeetleFollow(int blocks) {
        requireWritable();
        if (!DarkMatterBeetles.validFollow(blocks)) throw new IllegalArgumentException("Invalid beetle follow distance: " + blocks);
        beetleFollow = blocks;
    }
    /** 甲虫の個体の記録の写し。 */
    public ListTag getBeetleRecords() { return beetleRecords.copy(); }
    public void setBeetleRecords(ListTag records) { if (!readOnly) beetleRecords = records.copy(); }
    /** 天界発現の設定（領域は毎回ここを読む）。 */
    public HeavenSettings getHeavenSettings() { return heavenSettings; }

    /** 未元物質人間か。 */
    public boolean isDarkBody() { return darkBody; }
    /** 未元物質人間へ変わる。CPは全回復し、回復待ちとセーフティの点滅の条件を解除する。 */
    public void becomeDarkBody() { requireWritable(); darkBody = true; cp = maxCp; cpRecoveryDelay = 0; safetyBlockedCost = -1; bodyDamage = 0; }
    /**
     * 未元物質人間の身体に通った最終ダメージ（HP）を、1 HP＝{@link #BODY_CP_PER_HP} CP で一度だけCPから引き（1.5.2）、
     * 同額（実際に引けた分）を未修復の身体損傷（CP）に記録する。回復待ちは付けない。CPセーフティは見ない（生命へのダメージを肩代わりしない）。
     */
    public void takeBodyDamage(float amount) {
        requireNonnegativeFinite(amount);
        if (readOnly) return;
        float taken = Math.min(cp, amount * BODY_CP_PER_HP);
        cp -= taken;
        if (darkBody) bodyDamage = Math.min(maxCp, bodyDamage + taken);
    }
    /** 未修復の身体損傷（CP）。 */
    public float getBodyDamage() { clampBodyDamage(); return bodyDamage; }
    /**
     * 損傷 ≤ 最大CP−現在CP（明示的な回復で戻った分は損傷も減る）。未元物質人間でなければ0。
     * CPを明示的に上げる処理（setCp・recoverAll・習得・レベルアップ・最大CPの変更）は、その場でこれを呼ぶ。
     */
    private void clampBodyDamage() { bodyDamage = darkBody ? Math.max(0, Math.min(bodyDamage, maxCp - cp)) : 0; }
    /** 死亡後の復活形態（trueは未元物質人間）。今の形態は変えない。 */
    public boolean isDarkBodyRespawn() { return darkBodyRespawn; }
    public void setDarkBodyRespawn(boolean body) { requireWritable(); darkBodyRespawn = body; }
    /** 白翼飛行の飛び立ちと停止の操作が連打か（falseなら長押し）。 */
    public boolean isFlightDoubleTap() { return flightDoubleTap; }
    public void setFlightDoubleTap(boolean on) { requireWritable(); flightDoubleTap = on; }
    /** 1.5.2: ベクトル反射の反射方向。 */
    public ReflectionDirection getReflectionDirection() { return reflectionDirection; }
    public void setReflectionDirection(ReflectionDirection direction) { requireWritable(); reflectionDirection = java.util.Objects.requireNonNull(direction); }
    /** 1.5.2: 反射方向の自動の2方式で、PvPの相手プレイヤーを候補に含めるか。 */
    public boolean isReflectionPvp() { return reflectionPvp; }
    public void setReflectionPvp(boolean on) { requireWritable(); reflectionPvp = on; }
    /** 未元物質人間として死亡した後の復活に、設定の形態を適用する。通常人間のままの死亡では形態を変えない。 */
    public void applyRespawnForm() { if (!readOnly && darkBody && !darkBodyRespawn) { darkBody = false; bodyDamage = 0; } }
    /** 系統の喪失の直前の残量率を受け取る（1回だけ）。喪失していなければ負。 */
    public float takeDarkBodyLossRate() { float rate = darkBodyLossRate; darkBodyLossRate = -1; return rate; }

    // ---- 1.5.2: ベクトル操作の解析度（判定と分類は{@link VectorAnalysis}） ----
    /** 保存されたこの分類の解析度（0〜1。持っていなければ0）。バニラの物理がいつも100%であることは{@link VectorAnalysis}が扱う。 */
    public float getAnalysis(String key) { return analysis.getOrDefault(key, 0f); }
    /** この分類の表示の名前（未元物質の使い手の名前など）。無ければnull。 */
    public @Nullable String getAnalysisLabel(String key) { return analysisLabels.get(key); }
    /** 持っている解析度の写し（分類のキー → 0〜1、覚えた順）。 */
    public Map<String, Float> analysisEntries() { return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(analysis)); }
    /** 解析度が変わった回数（保存しない。同期の判定用）。 */
    public int analysisVersion() { return analysisVersion; }
    /**
     * この分類の解析度を置く（0〜1に収める。0なら忘れる）。labelがnullでなければ表示の名前も置き換える。読み取り専用のデータでは何もしない。
     * 上限（{@link #MAX_ANALYSIS_ENTRIES}）を超える新しい分類は、いちばん低い分類を忘れてから覚える。
     */
    public void setAnalysis(String key, float value, @Nullable String label) {
        if (readOnly || key == null || key.isEmpty() || key.length() > 256) return;
        float v = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        Float old = analysis.get(key);
        if (v <= 0) {
            if (old != null) { analysis.remove(key); analysisLabels.remove(key); analysisVersion++; }
            return;
        }
        if (old == null && analysis.size() >= MAX_ANALYSIS_ENTRIES) {
            String lowest = null; float min = Float.MAX_VALUE;
            for (var entry : analysis.entrySet()) if (entry.getValue() < min) { min = entry.getValue(); lowest = entry.getKey(); }
            if (lowest != null) { analysis.remove(lowest); analysisLabels.remove(lowest); }
        }
        boolean changed = old == null || old != v;
        analysis.put(key, v);
        if (label != null && !label.isEmpty()) {
            String trimmed = label.length() > 64 ? label.substring(0, 64) : label;
            changed |= !trimmed.equals(analysisLabels.put(key, trimmed));
        }
        if (changed) analysisVersion++;
    }
    /** キーがprefixで始まる分類の解析度を、それぞれamountだけ下げる（0になったら忘れる）。何か変わったらtrue。 */
    public boolean decayAnalysis(String prefix, float amount) {
        if (readOnly || !(amount > 0) || analysis.isEmpty()) return false;
        boolean changed = false;
        for (var it = analysis.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            float next = entry.getValue() - amount;
            if (next <= 1e-6f) { it.remove(); analysisLabels.remove(entry.getKey()); }
            else entry.setValue(next);
            changed = true;
        }
        if (changed) analysisVersion++;
        return changed;
    }
    /** すべての解析度を忘れる（テストと管理用）。 */
    public void clearAnalysis() {
        if (readOnly || analysis.isEmpty()) return;
        analysis.clear(); analysisLabels.clear(); analysisVersion++;
    }

    public void copyFrom(PlayerAbilityData other) {
        load(other.save());
    }

    private void requireWritable() {
        if (readOnly) throw new IllegalStateException("Unsupported Academy ability data (version or category id); preserving original NBT");
    }

    private static float requireNonnegativeFinite(float value) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Ability values must be finite and nonnegative");
        }
        return value;
    }

    private static float readNonnegativeFinite(CompoundTag tag, String key, float fallback) {
        if (!io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(tag, key, io.github.pinchan4273.reacademycraft.world.NbtCompat.TAG_ANY_NUMERIC)) return fallback;
        float value = tag.getFloatOr(key, 0f);
        return Float.isFinite(value) && value >= 0 ? value : fallback;
    }
}
