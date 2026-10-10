package io.github.pinchan4273.reacademycraft.skill;

import static io.github.pinchan4273.reacademycraft.skill.AbilityDataSchema.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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

    private @Nullable ResourceLocation ability;
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
    private final Map<ResourceLocation, Float> skills = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> cooldowns = new LinkedHashMap<>();
    private final ResourceLocation[][] presets = new ResourceLocation[PRESET_COUNT][SLOT_COUNT];
    private int currentPreset;
    private int cpRecoveryDelay;
    private int overloadRecoveryDelay;
    private boolean overloadLocked;
    private float bonusCp;
    private float bonusOverload;
    private float levelExperience;
    /**
     * このデータが属するサーバーのプレイヤー（原作の能力イベント用）。どのプレイヤーにも属さないデータ（写し、テスト専用のもの）や
     * クライアントにあるデータではnullで、その場合は何も送らない。
     */
    private @Nullable net.minecraft.world.entity.player.Player owner;
    public void bindOwner(net.minecraft.world.entity.player.Player player) { owner = player; }
    private boolean posting() { return owner != null && !owner.level().isClientSide; }
    private void post(java.util.function.Function<net.minecraft.world.entity.player.Player, net.minecraftforge.eventbus.api.Event> event) {
        if (posting()) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event.apply(owner));
    }
    /** 原作CalcEvent.calc: リスナーが残した値。誰も聞いていなければ値そのもの。 */
    private float calc(java.util.function.BiFunction<net.minecraft.world.entity.player.Player, Float, io.github.pinchan4273.reacademycraft.event.AbilityEvent.Calc> event, float value) {
        if (!posting()) return value;
        var calc = event.apply(owner, value);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(calc);
        return Float.isFinite(calc.value) ? Math.max(0, calc.value) : value;
    }
    /** 原作getInitCP / getInitOverload: レベルの値にコースのボーナスを加え、イベントを通したもの。 */
    private float initialCp() { return calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.MaxCP::new, io.github.pinchan4273.reacademycraft.config.AcademyConfig.initCp(level) + GenericSkills.cpBonus(this)); }
    private float initialOverload() { return calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.MaxOverload::new, io.github.pinchan4273.reacademycraft.config.AcademyConfig.initOverload(level) + GenericSkills.overloadBonus(this)); }

    public @Nullable ResourceLocation getAbility() { return ability; }
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

    public void setAbility(@Nullable ResourceLocation value) {
        requireWritable();
        if (Objects.equals(ability, value)) return;
        boolean wasActive = active; int wasLevel = level;
        loads++; clearSkills();
        levelExperience = 0;
        ability = value;
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

    public void setMaxCp(float value) {
        requireWritable();
        maxCp = requireNonnegativeFinite(value);
        cp = Math.min(cp, maxCp);
    }

    public void setCp(float value) {
        requireWritable();
        cp = Math.min(requireNonnegativeFinite(value), maxCp);
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
        if (readOnly) return; // 未対応のデータをNBTの水準でバイト単位で保つ。
        active = false;
        cooldowns.clear(); cpRecoveryDelay = overloadRecoveryDelay = 0; overloadLocked = false;
        if (hasAbility()) {
            cp = maxCp;
            overload = 0;
        }
    }

    /**
     * 一晩寝た後の原作CPData.recoverAll: CPは満タン、オーバーロードは0。ただし回復の遅延は続き、overloadFineはfalseなので、
     * まだ数えているオーバーロードは能力をオフのまま保つ。
     */
    public void recoverAll() {
        if (readOnly || !hasAbility()) return;
        cp = maxCp; overload = 0; overloadLocked = true;
    }

    public boolean hasLearned(ResourceLocation skill) { return skills.containsKey(skill); }
    public float getProficiency(ResourceLocation skill) { return skills.getOrDefault(skill, 0f); }
    public Map<ResourceLocation, Float> learnedSkills() { return Map.copyOf(skills); }
    public int getCooldown(ResourceLocation skill) { return cooldowns.getOrDefault(skill, 0); }
    public boolean isOverloadLocked() { return overloadLocked; }
    /**
     * 原作CPData.isOverloaded: オーバーロード中で、まだ回復のクールダウン内。その後は原作のisOverloadRecoveringだけ
     * （ここではisOverloadLocked）が、オーバーロードが無くなるまで続く。
     */
    public boolean isOverloaded() { return overloadLocked && overloadRecoveryDelay > 0; }

    /**
     * 原作CPDataの妨害: いずれかの発生源がまだ妨害していると言う間、プレイヤーは能力を一切使えない。発生源はサーバーにだけあり、
     * 保存しない。原作は妨害しているものが作り直すmapにそれを持ち、クライアントへ届くのは印だけ。
     */
    private final java.util.Map<String, java.util.function.BooleanSupplier> interferences = new java.util.LinkedHashMap<>();
    private boolean interfering;
    public boolean isInterfering() { return interfering; }
    public void addInterference(String id, java.util.function.BooleanSupplier lasts) {
        if (readOnly || id == null || lasts == null) return;
        interferences.put(id, lasts); interfering = true;
    }
    public boolean hasInterference(String id) { return interferences.containsKey(id); }
    /** 原作は妨害をやめた最初のtickに発生源を外す。印が変わったらtrueを返す。 */
    public boolean tickInterference() {
        interferences.values().removeIf(source -> !source.getAsBoolean());
        boolean now = !interferences.isEmpty();
        if (now == interfering) return false;
        interfering = now; return true;
    }
    /** クライアント側: サーバーが最後に送った印。 */
    public void setInterfering(boolean value) { interfering = value; }
    /** 原作canUseAbility: 有効で、オーバーロードしておらず、妨害されていない。 */
    public boolean canUseAbility() { return isActive() && !isOverloadLocked() && !interfering; }
    public int getCurrentPreset() { return currentPreset; }
    public ResourceLocation getSlot(int preset, int slot) {
        checkSlot(preset, slot); return presets[preset][slot];
    }
    public void learn(ResourceLocation skill) {
        requireWritable();
        if (!hasAbility() || skill == null || skill.toString().length() > 256
                || (!skills.containsKey(skill) && skills.size() >= MAX_SKILLS))
            throw new IllegalArgumentException("Invalid learned skill");
        boolean newlyLearned = skills.putIfAbsent(skill, 0f) == null;
        if (newlyLearned) {
            maxCp = initialCp() + bonusCp;
            maxOverload = initialOverload() + bonusOverload;
            // 原作SkillLearnEventは能動技能の資源も更新する。
            // 消費の成長、タイマー、プリセット、既存の技能の進捗は保つ。
            cp = maxCp; overload = 0;
            post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillLearn(p, skill));
        }
    }
    public void setProficiency(ResourceLocation skill, float value) {
        requireWritable();
        if (!hasLearned(skill)) throw new IllegalArgumentException("Skill is not learned");
        skills.put(skill, Math.min(1, requireNonnegativeFinite(value)));
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpChanged(p, skill));
    }
    /**
     * 実際のゲームプレイでの獲得。原作addSkillExpは100%でも加工前の量を数える。
     * デバッグのsetProficiencyは、意図的にレベルの経験値を生まない。
     */
    public void addProficiency(ResourceLocation skill, float amount) {
        requireWritable(); requireNonnegativeFinite(amount);
        SkillCatalog.Definition definition = SkillCatalog.find(skill);
        if (definition == null || !definition.offeredTo(ability) || !hasLearned(skill) || GenericSkills.isPassive(skill))
            throw new IllegalArgumentException("Unknown or unlearned gameplay skill");
        amount = (float) (amount * io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillExpSpeed(skill));
        skills.put(skill, (float) Math.min(1, (double) getProficiency(skill) + amount));
        double progression = amount * io.github.pinchan4273.reacademycraft.config.AcademyConfig.progressionMultiplier(ability);
        levelExperience = (float) Math.min(Float.MAX_VALUE, (double) levelExperience + progression);
        final float added = amount;
        // 原作AbilityData.addSkillExpはSkillExpChangedEvent、次にSkillExpAddedEventを送る。
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpChanged(p, skill));
        post(p -> new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillExpAdded(p, skill, added));
    }
    /**
     * 原作AbilityData.addSkillExpそのもの。まだ習得していない技能は、レベルや前提条件を確かめず（カテゴリが提供するかだけを確かめ）、
     * まず習得させる。テレポーターのクリティカルはこれに頼る: Dimension Folding TheoremとSpace Fluctuationに経験値を与え、
     * その場で習得させる。それ以外はすべてaddProficiencyを通り、未習得や受動の技能は拒否される。
     */
    public void addSkillExperience(ResourceLocation skill, float amount) {
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
    public void unlearn(ResourceLocation skill) {
        requireWritable();
        if (skills.remove(Objects.requireNonNull(skill)) == null) return;
        cooldowns.remove(skill);
        for (var preset : presets) for (int i = 0; i < preset.length; i++)
            if (skill.equals(preset[i])) preset[i] = null;
        maxCp = hasAbility() ? initialCp() + bonusCp : DEFAULT_MAX_CP;
        maxOverload = hasAbility() ? initialOverload() + bonusOverload : DEFAULT_MAX_OVERLOAD;
        cp = Math.min(cp, maxCp); overload = Math.min(overload, maxOverload);
    }
    public void setCooldown(ResourceLocation skill, int ticks) {
        requireWritable();
        if (!hasLearned(skill) || ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Invalid cooldown");
        if (ticks == 0) cooldowns.remove(skill); else cooldowns.put(skill, ticks);
    }
    public void setSlot(int preset, int slot, @Nullable ResourceLocation skill) {
        requireWritable(); checkSlot(preset, slot);
        if (skill != null && (!hasLearned(skill) || GenericSkills.isPassive(skill))) throw new IllegalArgumentException("Skill is not a learned controllable skill");
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
        skills.clear(); cooldowns.clear(); currentPreset = 0;
        for (var preset : presets) java.util.Arrays.fill(preset, null);
    }
    /** 原作CPData.recalcMaxValue。求められれば、changedLevelと同じくaddMaxCP/addMaxOverloadを消す。 */
    private void recalculateLimits(boolean keepBonus) {
        if (!keepBonus) bonusCp = bonusOverload = 0;
        maxCp = hasAbility() ? initialCp() + bonusCp : DEFAULT_MAX_CP;
        maxOverload = hasAbility() ? initialOverload() + bonusOverload : DEFAULT_MAX_OVERLOAD;
        cp = hasAbility() ? maxCp : 0; overload = 0;
        cpRecoveryDelay = overloadRecoveryDelay = 0; overloadLocked = false;
        // 原作CalcEvent: MaxCPとMaxOverloadはinitialCp()/initialOverload()で、CPRecoverSpeedとOverloadRecoverSpeedはtick()で
        // 設定の倍率と並ぶ倍率として、SkillAttackはSkillCombat.attack()でAcademyConfig.DAMAGE_SCALE（calc_global.damage_scale）の前に使う。
    }

    /** 既定値と式は1.12.2のCPData/default.confと同じ。tickはサーバーでだけ行う。 */
    public void tick() {
        if (readOnly || !hasAbility()) return;
        cooldowns.replaceAll((id, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);
        float rawCp = Math.max(0, maxCp - bonusCp);
        float rawOverload = Math.max(0, maxOverload - bonusOverload);
        if (overload == 0 && overloadRecoveryDelay == 0) overloadLocked = false;
        boolean waiting = cpRecoveryDelay > 0;
        if (waiting) cpRecoveryDelay--;
        if (upkeeps.isEmpty()) { if (!waiting && rawCp > 0) cp = (float) Math.min(maxCp, cp + recovery(rawCp)); }
        else settleUpkeep(rawCp, waiting);
        if (overloadRecoveryDelay > 0) overloadRecoveryDelay--;
        else if (rawOverload > 0) {
            overload = (float) Math.max(0, overload - io.github.pinchan4273.reacademycraft.config.AcademyConfig.OVERLOAD_RECOVER_SPEED_MULTIPLIER.get()
                    * calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.OverloadRecoverSpeed::new, 1)
                    * Math.max(0.002f * rawOverload, 0.007f * rawOverload * (1 - 0.25f * overload / rawOverload)));
            if (overload == 0) overloadLocked = false;
        }
    }

    /** 最大値で切る前の、このtickの原作のCP回復: 待ちが無いときにもたらす量。 */
    private double recovery(float rawCp) {
        return io.github.pinchan4273.reacademycraft.config.AcademyConfig.CP_RECOVER_SPEED_MULTIPLIER.get()
                * calc(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CPRecoverSpeed::new, 1) * GenericSkills.recoveryMultiplier(this) * 0.0003f * rawCp * (1 + cp / rawCp);
    }

    /**
     * tick()が精算する維持コストへの、切り替え型技能の要求。技能はモードがオンの間これを保ち、保てなくなったら（upkeepHeld）
     * モードを終える。
     */
    public static final class Upkeep {
        private final ResourceLocation skill;
        private final java.util.function.BooleanSupplier exempt;
        private Upkeep(ResourceLocation skill, java.util.function.BooleanSupplier exempt) { this.skill = skill; this.exempt = exempt; }
        public ResourceLocation skill() { return skill; }
    }
    /** このデータが置き換えられた、または能力が変わった回数: リスナーがどちらかを行った精算は止まる。 */
    private int loads;
    /** サーバー側で、保存しない: モードはセッションより長く生きない。モードが始まった順。 */
    private final Map<ResourceLocation, Upkeep> upkeeps = new LinkedHashMap<>();
    /**
     * 次のtickからモードの維持コストを払い始める。免除（クリエイティブの術者）は、消費と同じく何も払わない。
     * 維持できない所（読み取り専用のデータ、能力無し、未習得の技能）ではnull。
     */
    public @Nullable Upkeep startUpkeep(ResourceLocation skill, java.util.function.BooleanSupplier exempt) {
        if (readOnly || !hasAbility() || skill == null || exempt == null || !hasLearned(skill)) return null;
        var upkeep = new Upkeep(skill, exempt);
        upkeeps.remove(skill); upkeeps.put(skill, upkeep);
        return upkeep;
    }
    /** この要求自体がまだ払われているか: 払えなくなるか、別のものが置き換えるとfalse。 */
    public boolean upkeepHeld(@Nullable Upkeep upkeep) { return upkeep != null && upkeeps.get(upkeep.skill) == upkeep; }
    /** この要求だけを終える: 古いセッションの遅れた停止は、新しいセッションの要求に手を出さない。 */
    public void endUpkeep(@Nullable Upkeep upkeep) { if (upkeepHeld(upkeep)) upkeeps.remove(upkeep.skill); }
    public int upkeepCount() { return upkeeps.size(); }

    /**
     * 維持コストの取り決め: このtickに待ち無しでもたらされる回復Rを1回求める。各モードはSkillPerformを通して
     * cp_consume_speed * (R + E * (1 - 熟練度))を払い、待ちが回復を止めている間もRを代わりに使う。CPと与えられた回復ですべてを
     * 払えないときは、払えるまで新しいモードから外れる。残りは1つの差分として精算するので、熟練度最大のモード1つだけならCPは
     * 満タンかどうかに関わらず元の値のまま。どちらの回復待ちも動かさない。
     */
    private void settleUpkeep(float rawCp, boolean waiting) {
        double recovered = rawCp > 0 ? recovery(rawCp) : 0;
        // リスナーの有限の倍率でも、回復がfloatの範囲を超えることがある。その場合は要求の無い回復と同じくCPを最大まで満たし、
        // コストは標準の割合を取る。
        boolean overflow = !(recovered <= Float.MAX_VALUE);
        float potential = overflow ? 0 : (float) recovered;
        float granted = waiting ? 0 : overflow ? maxCp : potential;
        // 回復が止まっている（倍率、イベント、またはCPがまったく無い）と熟練度最大がただになってしまう: 代わりに標準の回復を課し、
        // それも0なら1tickに1を課す。
        float basis = potential > 0 ? potential : rawCp > 0 ? (float) (0.0003f * rawCp * (1 + cp / rawCp)) : 1;
        int loads = this.loads;
        var order = new java.util.ArrayList<>(upkeeps.values());
        float[] cost = new float[order.size()];
        for (int i = 0; i < order.size(); i++) {
            var upkeep = order.get(i);
            if (!active || !hasLearned(upkeep.skill)) { endUpkeep(upkeep); continue; }
            if (upkeep.exempt.getAsBoolean()) continue;
            float extra = io.github.pinchan4273.reacademycraft.config.AcademyConfig.toggleUpkeep(upkeep.skill) * (1 - getProficiency(upkeep.skill));
            cost[i] = perform(cpFor(upkeep.skill, basis + extra), 0)[0];
            // SkillPerformは他の者が聞き、その間に何をしてもよい: 下を参照。
            if (loads != this.loads || readOnly || !hasAbility()) break;
        }
        // リスナーが残したもの: リスナーが読み込んだ、または能力を変えたデータは、この精算が書くものではない。リスナーが能力をオフにしたら、
        // すべての要求を払わずに終える。リスナーが終えた要求は課さない。
        if (loads != this.loads || readOnly || !hasAbility()) return;
        if (!active) upkeeps.clear();
        double total = 0;
        for (int i = 0; i < order.size(); i++) if (upkeepHeld(order.get(i))) total += cost[i];
        double available = (double) cp + granted;
        for (int i = order.size() - 1; i >= 0 && total > available; i--)
            if (cost[i] > 0 && upkeepHeld(order.get(i))) { endUpkeep(order.get(i)); total -= cost[i]; }
        double change = granted - total;
        if (Double.isFinite(change)) cp = (float) Math.max(0, Math.min(maxCp, cp + change));
    }

    /**
     * オンであることに対する切り替え型モード自身のオーバーロード（Vector Deviation、Storm Wing）: consumeがオーバーロードを課すのと同じく
     * 課す（回復待ち、オーバーロードのイベント、最大値の成長）が、モードの維持コストが触れないCP回復の待ちには触れない。
     */
    public void chargeUpkeepOverload(ResourceLocation skill, float overloadCost, boolean creative) {
        requireNonnegativeFinite(overloadCost);
        if (readOnly || !hasAbility()) return;
        float cost = perform(0, overloadFor(skill, overloadCost))[1];
        if (readOnly || !hasAbility()) return;
        if (!creative) {
            overload = Math.min(maxOverload, overload + cost);
            overloadRecoveryDelay = io.github.pinchan4273.reacademycraft.config.AcademyConfig.overloadRecoverCooldown();
            if (overload >= maxOverload && !overloadLocked) { overloadLocked = true; post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload::new); }
        }
        float overloadIncrease = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addOverload(level) - bonusOverload,
                Math.min(10, cost * io.github.pinchan4273.reacademycraft.config.AcademyConfig.maxOverloadIncrRate())));
        bonusOverload += overloadIncrease; maxOverload += overloadIncrease;
    }

    /** 原作CPData.canPerform: これだけのCPを今使えるか（使わずに確かめる）。 */
    public boolean canConsumeCp(float cpCost) {
        requireNonnegativeFinite(cpCost);
        return !readOnly && hasAbility() && active && !overloadLocked && cp >= cpCost;
    }

    // 原作default.confの技能ごとのcp_consume_speedとoverload_consume_speed。原作のAbilityContextが、技能の払うすべてのコストに
    // 適用したのと同じ。
    private static float cpFor(ResourceLocation skill, float cp) { return (float) (cp * io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillCpSpeed(skill)); }
    private static float overloadFor(ResourceLocation skill, float overload) { return (float) (overload * io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillOverloadSpeed(skill)); }
    /**
     * 原作AbilityContext.canConsumeCPは、与えられたコストのままCPData.canPerformに問う: consumeと違い、技能のcp_consume_speedを掛けない。
     */
    public boolean canConsumeCp(ResourceLocation skill, float cpCost) { return canConsumeCp(cpCost); }
    public boolean consume(ResourceLocation skill, float cpCost, float overloadCost, boolean creative) {
        return consume(cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative);
    }
    public boolean consumeInMode(ResourceLocation skill, float cpCost, float overloadCost, boolean creative) {
        return consumeInMode(cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative);
    }
    public void consumeWithForce(ResourceLocation skill, float cpCost, float overloadCost, boolean creative) {
        consumeWithForce(cpFor(skill, cpCost), overloadFor(skill, overloadCost), creative);
    }
    /** 不可分な資源の取引。原作と同じく、クリエイティブは消費を免除する。 */
    public boolean consume(float cpCost, float overloadCost, boolean creative) {
        requireNonnegativeFinite(cpCost); requireNonnegativeFinite(overloadCost);
        var perform = perform(cpCost, overloadCost); cpCost = perform[0]; overloadCost = perform[1];
        if (readOnly || !hasAbility() || !active || overloadLocked || (!creative && cp < cpCost)) return false;
        charge(cpCost, overloadCost, creative);
        return true;
    }
    /**
     * 原作CPData.performそのもの: オーバーロードや有効状態に関係なく、CP不足でだけ拒否する（クリエイティブは拒否されない）。
     * 原作の切り替え型モード（Vector Deviation、Storm Wing、Vector Reflection）は、オーバーロードで中断されるのは押し続けのキーだけ
     * だったので、オーバーロード中もこの方法で払い続けた。上のconsume()は、押し続けのキーの中断と同じくオーバーロードのロックで拒否する。
     */
    public boolean consumeInMode(float cpCost, float overloadCost, boolean creative) {
        requireNonnegativeFinite(cpCost); requireNonnegativeFinite(overloadCost);
        var perform = perform(cpCost, overloadCost); cpCost = perform[0]; overloadCost = perform[1];
        if (readOnly || !hasAbility() || (!creative && cp < cpCost)) return false;
        charge(cpCost, overloadCost, creative);
        return true;
    }
    /** 原作CPData.performWithForce: 足りるか確かめずに課し、CPは拒否せず0で止まる。テレポーターの移動技能は距離をこの方法で払う。 */
    public void consumeWithForce(float cpCost, float overloadCost, boolean creative) {
        requireNonnegativeFinite(cpCost); requireNonnegativeFinite(overloadCost);
        var perform = perform(cpCost, overloadCost); cpCost = perform[0]; overloadCost = perform[1];
        if (readOnly || !hasAbility()) return;
        charge(cpCost, overloadCost, creative);
    }
    /** 原作CalcEvent.SkillPerform: リスナーが残した[cp, overload]。負にはしない。 */
    private float[] perform(float cpCost, float overloadCost) {
        if (!posting() || readOnly || !hasAbility()) return new float[]{cpCost, overloadCost};
        var event = new io.github.pinchan4273.reacademycraft.event.AbilityEvent.SkillPerform(owner, overloadCost, cpCost);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        float cpAfter = Float.isFinite(event.cp) ? Math.max(0, event.cp) : cpCost;
        float overloadAfter = Float.isFinite(event.overload) ? Math.max(0, event.overload) : overloadCost;
        return new float[]{cpAfter, overloadAfter};
    }
    private void charge(float cpCost, float overloadCost, boolean creative) {
        if (!creative) {
            cp = Math.max(0, cp - cpCost); overload = Math.min(maxOverload, overload + overloadCost);
            cpRecoveryDelay = io.github.pinchan4273.reacademycraft.config.AcademyConfig.cpRecoverCooldown();
            overloadRecoveryDelay = io.github.pinchan4273.reacademycraft.config.AcademyConfig.overloadRecoverCooldown();
            if (overload >= maxOverload && !overloadLocked) { overloadLocked = true; post(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload::new); }
        }
        float cpIncrease = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level) - bonusCp,
                cpCost * io.github.pinchan4273.reacademycraft.config.AcademyConfig.maxCpIncrRate()));
        float overloadIncrease = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addOverload(level) - bonusOverload,
                Math.min(10, overloadCost * io.github.pinchan4273.reacademycraft.config.AcademyConfig.maxOverloadIncrRate())));
        bonusCp += cpIncrease; maxCp += cpIncrease;
        bonusOverload += overloadIncrease; maxOverload += overloadIncrease;
    }

    /** schema 2（{@link AbilityDataSchema}）で書く。読んだときにあった知らないキーは、根にも各区分にも残す。 */
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

        CompoundTag presetPart = section(tag, PRESET);
        presetPart.putInt(PRESET_ID, currentPreset);
        ListTag table = new ListTag();
        for (ResourceLocation[] row : presets) {
            ListTag line = new ListTag();
            for (ResourceLocation id : row) line.add(StringTag.valueOf(id == null ? "" : id.toString()));
            table.add(line);
        }
        presetPart.put(PRESETS, table);

        // 読んだときのCooldownDataの写しから、このデータが管理する技能（技能の一覧にある技能と、学んだ技能）の項目だけを除き、
        // 今のクールダウンを書き直す。知らないキー（拡張の付記、まだ知らない技能のクールダウン）はそのまま残る。
        CompoundTag cooling = section(tag, COOLDOWN);
        for (String key : java.util.List.copyOf(cooling.getAllKeys())) if (managesCooldown(key)) cooling.remove(key);
        skills.keySet().forEach(id -> { int ticks = getCooldown(id); if (ticks > 0) cooling.putInt(id.toString(), ticks); });
        return tag;
    }

    /**
     * 既存の状態を置き換える。古い値を加えたり、変更可能なNBTを呼び出し元と共有したりしない。
     * 以前の版の形（schema 1）は、{@link PlayerDataMigration}で新しい形へ移してから読む。新しすぎる版は読まずにそのまま保つ。
     */
    public void load(CompoundTag tag) {
        loads++; clearSkills(); cpRecoveryDelay = overloadRecoveryDelay = 0; upkeeps.clear();
        overloadLocked = false; bonusCp = bonusOverload = 0;
        ability = null;
        level = 0;
        levelExperience = 0;
        cp = overload = 0;
        maxCp = DEFAULT_MAX_CP;
        maxOverload = DEFAULT_MAX_OVERLOAD;
        active = false;
        CompoundTag source = PlayerDataMigration.needed(tag) ? PlayerDataMigration.fromSchemaOne(tag) : tag;
        preserved = source.copy();
        readOnly = !source.contains(VERSION_KEY, Tag.TAG_INT) || source.getInt(VERSION_KEY) != VERSION;
        if (readOnly) return;

        CompoundTag abilityPart = source.getCompound(ABILITY), cpPart = source.getCompound(CP);
        CompoundTag presetPart = source.getCompound(PRESET), cooling = source.getCompound(COOLDOWN);
        String category = abilityPart.getString(CATEGORY);
        if (!category.isEmpty()) ability = ResourceLocation.tryParse(category);
        level = hasAbility() ? Math.max(1, Math.min(5, abilityPart.getInt(LEVEL))) : 0;
        levelExperience = hasAbility() ? readNonnegativeFinite(abilityPart, LEVEL_EXP, 0) : 0;
        // 現在の値より先に最大値を戻し、現在の値をその範囲へ切り詰める。
        maxCp = readNonnegativeFinite(cpPart, MAX_CP, DEFAULT_MAX_CP);
        maxOverload = readNonnegativeFinite(cpPart, MAX_OVERLOAD, DEFAULT_MAX_OVERLOAD);
        cp = Math.min(readNonnegativeFinite(cpPart, CUR_CP, 0), maxCp);
        overload = Math.min(readNonnegativeFinite(cpPart, CUR_OVERLOAD, 0), maxOverload);
        active = hasAbility() && cpPart.getBoolean(ACTIVATED);
        overloadLocked = cpPart.contains(OVERLOAD_FINE, Tag.TAG_ANY_NUMERIC) && !cpPart.getBoolean(OVERLOAD_FINE);
        // 原作CPDataはuntilRecoverとuntilOverloadRecoverをそのまま保存する。より長く設定した待ちは再読込を越えて残らなければならないので、
        // 設定が決して生まない値だけを拒否する。
        cpRecoveryDelay = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.MAX_RECOVER_COOLDOWN, cpPart.getInt(UNTIL_RECOVER)));
        overloadRecoveryDelay = Math.max(0, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.MAX_RECOVER_COOLDOWN, cpPart.getInt(UNTIL_OVERLOAD_RECOVER)));
        bonusCp = Math.min(maxCp, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addCp(level), readNonnegativeFinite(cpPart, ADD_MAX_CP, 0)));
        bonusOverload = Math.min(maxOverload, Math.min(io.github.pinchan4273.reacademycraft.config.AcademyConfig.addOverload(level), readNonnegativeFinite(cpPart, ADD_MAX_OVERLOAD, 0)));
        currentPreset = Math.max(0, Math.min(PRESET_COUNT - 1, presetPart.getInt(PRESET_ID)));
        ListTag learned = abilityPart.getList(SKILLS, Tag.TAG_COMPOUND);
        for (int i = 0; hasAbility() && i < Math.min(MAX_SKILLS, learned.size()); i++) {
            CompoundTag entry = learned.getCompound(i); String id = entry.getString(SKILL);
            ResourceLocation skill = ResourceLocation.tryParse(id);
            if (skill == null || id.isEmpty() || id.length() > 256) continue;
            skills.put(skill, Math.min(1, readNonnegativeFinite(entry, EXP, 0)));
            int cooldown = Math.max(0, Math.min(72000, cooling.getInt(id)));
            if (cooldown > 0) cooldowns.put(skill, cooldown);
        }
        ListTag table = presetPart.getList(PRESETS, Tag.TAG_LIST);
        for (int p = 0; p < Math.min(PRESET_COUNT, table.size()); p++) {
            ListTag row = table.getList(p);
            for (int s = 0; s < Math.min(SLOT_COUNT, row.size()); s++) {
                String id = row.getElementType() == Tag.TAG_STRING ? row.getString(s) : "";
                ResourceLocation skill = id.isEmpty() ? null : ResourceLocation.tryParse(id);
                if (skill != null && skills.containsKey(skill) && !GenericSkills.isPassive(skill)) presets[p][s] = skill;
            }
        }
    }

    /**
     * CooldownDataのこのキーをこのデータが管理するか。今の技能の一覧にある技能か、学んだ技能（読んだときにクールダウンを
     * 取り込んだもの）なら管理する。IDとして読めるだけの知らない技能は管理せず、保存で消さない。
     */
    private boolean managesCooldown(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id != null && (SkillCatalog.find(id) != null || skills.containsKey(id));
    }

    /** 使用で最大CPに加わった分（原作CPData.addMaxCP）。 */
    public float getAddedMaxCp() { return bonusCp; }
    /** 使用で最大オーバーロードに加わった分（原作CPData.addMaxOverload）。 */
    public float getAddedMaxOverload() { return bonusOverload; }
    /** CPの回復が始まるまでの残りtick（原作CPData.untilRecover）。 */
    public int getCpRecoveryDelay() { return cpRecoveryDelay; }
    /** オーバーロードの回復が始まるまでの残りtick（原作CPData.untilOverloadRecover）。 */
    public int getOverloadRecoveryDelay() { return overloadRecoveryDelay; }

    public void copyFrom(PlayerAbilityData other) {
        load(other.save());
    }

    private void requireWritable() {
        if (readOnly) throw new IllegalStateException("Unsupported Academy ability data version; preserving original NBT");
    }

    private static float requireNonnegativeFinite(float value) {
        if (!Float.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Ability values must be finite and nonnegative");
        }
        return value;
    }

    private static float readNonnegativeFinite(CompoundTag tag, String key, float fallback) {
        if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) return fallback;
        float value = tag.getFloat(key);
        return Float.isFinite(value) && value >= 0 ? value : fallback;
    }
}
