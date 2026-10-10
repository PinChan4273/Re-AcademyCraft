package io.github.pinchan4273.reacademycraft.develop;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.skill.AbilityCategory;
import io.github.pinchan4273.reacademycraft.skill.ArcGen;
import io.github.pinchan4273.reacademycraft.skill.CurrentCharging;
import io.github.pinchan4273.reacademycraft.skill.MagneticMovement;
import io.github.pinchan4273.reacademycraft.skill.MagneticManipulation;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * サーバーが持つ一時的な学習アクション。原作DevelopData/DevelopActionLevel/DevelopActionSkill（WeAthFolD）の刺激の方式で、
 * 学習の可否はサーバーが確かめる。途中の学習は保存しない。確定した能力データと消費したエネルギーは保存する。
 */
public final class DevelopmentSession {
    public static final int ACQUIRE = 0, LEARN_ARC = 1, LEARN_CHARGING = 3, LEVEL_UP = 4, LEARN_MOVEMENT = 5, LEARN_MANIPULATION = 6, LEARN_INTENSIFY = 7, LEARN_MINE_DETECT = 8, LEARN_THUNDER_BOLT = 9, LEARN_RAILGUN = 10, LEARN_THUNDER_CLAP = 11, LEARN_BRAIN = 12, LEARN_ADVANCED_BRAIN = 13, LEARN_MIND = 14, LEARN_ELECTRON_BOMB = 15, LEARN_SCATTER_BOMB = 16, LEARN_LIGHT_SHIELD = 17, LEARN_MINE_RAY_BASIC = 18, LEARN_RAD_INTENSIFY = 19, LEARN_MINE_RAY_EXPERT = 20, LEARN_MINE_RAY_LUCK = 21, LEARN_JET_ENGINE = 22, LEARN_RAY_BARRAGE = 23, LEARN_ELECTRON_MISSILE = 24, LEARN_MELTDOWNER = 25, LEARN_THREATENING_TELEPORT = 26, LEARN_DIM_FOLDING = 27, LEARN_SPACE_FLUCT = 28, LEARN_PENETRATE_TELEPORT = 29, LEARN_MARK_TELEPORT = 30, LEARN_FLESH_RIPPING = 31, LEARN_LOCATION_TELEPORT = 32, LEARN_SHIFT_TP = 33, LEARN_FLASHING = 34, LEARN_DIR_SHOCK = 35, LEARN_GROUND_SHOCK = 36, LEARN_VEC_ACCEL = 37, LEARN_VEC_DEVIATION = 38, LEARN_DIR_BLAST = 39, LEARN_STORM_WING = 40, LEARN_BLOOD_RETRO = 41, LEARN_VEC_REFLECTION = 42, LEARN_PLASMA_CANNON = 43;
    /**
     * 原作DevelopActionReset: 上級開発機で、磁気コイルを手に持ち、レベル3以上のとき、誘導因子を通じて別のカテゴリへ変える。
     * レベルは1下がる。
     */
    public static final int RESET = 44;
    /** メニューのボタンが要求できる最大のアクション。 */
    public static final int MAX_ACTION = RESET;
    public static final int IDLE = 0, DEVELOPING = 1, DONE = 2, CANCELLED = 3, NO_ENERGY = 4, INVALID = 5;
    // 実際の1.12.2の++tickThisStim > 25の挙動を保つ: 26tick、1tickあたり30 IF。
    public static final int TICKS_PER_STIMULATION = 26, ENERGY_PER_TICK = 30; // 携帯型との互換のための定数。
    private final ServerPlayer owner;
    private final Level level;
    private final DevelopmentDevice device;
    private boolean closed;
    private ItemStack factor = ItemStack.EMPTY;
    private int action = -1, state = IDLE, elapsed, total;
    private int startingLevel;

    public DevelopmentSession(ServerPlayer owner, InteractionHand hand) {
        this(owner, DevelopmentDevice.handheld(owner, hand));
    }
    public DevelopmentSession(ServerPlayer owner, DevelopmentDevice device) {
        this.owner = owner; this.level = owner.level(); this.device = device;
    }
    private PlayerAbilityData data() {
        return owner.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
    }
    public boolean valid() {
        var data = data();
        return !closed && owner.isAlive() && !owner.isSpectator() && owner.level() == level
                && device.valid(owner) && data != null && !data.isReadOnly();
    }
    /** 原作DevelopActionReset.getFactor: メインインベントリにある、他のいずれかのカテゴリの誘導因子。 */
    private ItemStack findResetFactor() {
        var current = data().getAbility();
        for (var stack : owner.getInventory().items) {
            var category = AbilityCategory.byFactor(stack);
            if (category != null && !category.id().equals(current)) return stack;
        }
        return ItemStack.EMPTY;
    }
    /** 原作DevelopActionReset.canReset。 */
    private boolean canReset() {
        var data = data();
        return data.hasAbility() && data.getLevel() >= 3 && device.tier() == DeveloperTier.ADVANCED
                && owner.getMainHandItem().is(io.github.pinchan4273.reacademycraft.world.AcademyContent.MAGNETIC_COIL.get())
                && !findResetFactor().isEmpty();
    }
    private ItemStack findFactor() {
        // 原作の取得はメインインベントリを探す。オフハンドの因子は代わりにならない。
        for (var stack : owner.getInventory().items)
            if (AbilityCategory.byFactor(stack) != null) return stack;
        return ItemStack.EMPTY;
    }
    public boolean eligible(int requested) {
        if (!valid()) return false;
        var data = data();
        // 取得は、プレイヤーが実際に持っている登録済みの因子に従う。ランダムな取得は残りの原作カテゴリを待っている。
        // 黙って抽選を強制しない。
        if (requested == ACQUIRE) return !data.hasAbility() && !findFactor().isEmpty();
        if (requested == LEVEL_UP) return data.canLevelUp();
        if (requested == RESET) return canReset();
        if (requested == LEARN_ARC || requested == LEARN_CHARGING || requested == LEARN_MOVEMENT || requested == LEARN_MANIPULATION || requested == LEARN_INTENSIFY || requested == LEARN_MINE_DETECT || requested == LEARN_THUNDER_BOLT || requested == LEARN_RAILGUN || requested == LEARN_THUNDER_CLAP || requested == LEARN_BRAIN || requested == LEARN_ADVANCED_BRAIN || requested == LEARN_MIND || requested == LEARN_ELECTRON_BOMB || requested == LEARN_SCATTER_BOMB || requested == LEARN_LIGHT_SHIELD || requested == LEARN_MINE_RAY_BASIC || requested == LEARN_RAD_INTENSIFY || requested == LEARN_MINE_RAY_EXPERT || requested == LEARN_MINE_RAY_LUCK || requested == LEARN_JET_ENGINE || requested == LEARN_RAY_BARRAGE || requested == LEARN_ELECTRON_MISSILE || requested == LEARN_MELTDOWNER || requested == LEARN_THREATENING_TELEPORT || requested == LEARN_DIM_FOLDING || requested == LEARN_SPACE_FLUCT || requested == LEARN_PENETRATE_TELEPORT || requested == LEARN_MARK_TELEPORT || requested == LEARN_FLESH_RIPPING || requested == LEARN_LOCATION_TELEPORT || requested == LEARN_SHIFT_TP || requested == LEARN_FLASHING || requested == LEARN_DIR_SHOCK || requested == LEARN_GROUND_SHOCK || requested == LEARN_VEC_ACCEL || requested == LEARN_VEC_DEVIATION || requested == LEARN_DIR_BLAST || requested == LEARN_STORM_WING || requested == LEARN_BLOOD_RETRO || requested == LEARN_VEC_REFLECTION || requested == LEARN_PLASMA_CANNON)
            return device.tier().supportsSkill(SkillCatalog.find(skillFor(requested)).level())
                    && SkillCatalog.find(skillFor(requested)).canLearn(data);
        return false;
    }
    public boolean start(int requested) {
        if (state == DEVELOPING || !eligible(requested)) return false;
        action = requested; elapsed = 0;
        startingLevel = data().getLevel();
        // 原作DevelopActionReset.getStimulations: 1レベルにつき10。
        total = (action == ACQUIRE ? 5 : action == LEVEL_UP ? 5 * (startingLevel + 1) : action == RESET ? 10 * startingLevel
                : DeveloperTier.skillStimulations(SkillCatalog.find(skillFor(action)).level())) * device.tier().ticksPerStimulation;
        factor = action == ACQUIRE ? findFactor() : action == RESET ? findResetFactor() : ItemStack.EMPTY;
        state = DEVELOPING; return true;
    }
    public void tick() {
        if (state != DEVELOPING) return;
        if (!eligible(action) || (action == LEVEL_UP && data().getLevel() != startingLevel)
                || (action == ACQUIRE && (AbilityCategory.byFactor(factor) == null
                || owner.getInventory().items.stream().noneMatch(stack -> stack == factor)))
                || (action == RESET && (data().getLevel() != startingLevel || AbilityCategory.byFactor(factor) == null
                || owner.getInventory().items.stream().noneMatch(stack -> stack == factor)))) {
            state = INVALID; return;
        }
        if (!device.consume(device.tier().energyPerTick)) { state = NO_ENERGY; return; }
        if (++elapsed < total) return;
        // 確認と消費はすべてサーバースレッドで行う。クライアントが送った進捗は使わない。
        if (action == ACQUIRE) {
            // 減らす前に解決する: 空になったスタックからはカテゴリが分からない。
            data().setAbility(AbilityCategory.byFactor(factor).id()); data().setLevel(1); data().recoverAfterDeath();
            factor.shrink(1); // 原作の完了はクリエイティブでも因子を消費する。
        } else if (action == LEVEL_UP) data().setLevel(startingLevel + 1);
        else if (action == RESET) {
            // 原作onLearnedはまずTransformCategoryEventを送り、何も取り消さなかったときだけ、新しいカテゴリ（技能はすべて忘れる）、
            // 1つ下のレベル、手に持ったコイルと因子のスタック全体の消費を行う。
            var category = AbilityCategory.byFactor(factor).id();
            if (!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new io.github.pinchan4273.reacademycraft.event.AbilityEvent.TransformCategory(owner, category, startingLevel - 1))) {
                data().setAbility(category); data().setLevel(startingLevel - 1);
                owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                int slot = owner.getInventory().items.indexOf(factor);
                if (slot >= 0) owner.getInventory().items.set(slot, ItemStack.EMPTY);
            }
        }
        else data().learn(skillFor(action));
        state = DONE; AbilitySyncEvents.sync(owner, true);
    }
    public void cancel() { if (state == DEVELOPING) state = CANCELLED; }
    public int state() { return state; }
    public int elapsed() { return elapsed; }
    public int total() { return total; }
    public int energy() { return device.energy(); }
    public DeveloperTier tier() { return device.tier(); }
    public boolean usesDevice(DevelopmentDevice candidate) { return !closed && device == candidate; }
    public void close() { if (!closed) { cancel(); closed = true; device.close(); } }
    public long availability() {
        long bits = (eligible(ACQUIRE) ? availabilityBit(ACQUIRE) : 0) | (eligible(LEVEL_UP) ? availabilityBit(LEVEL_UP) : 0)
                | (eligible(RESET) ? availabilityBit(RESET) : 0);
        for (int action : LEARN_ACTIONS) if (eligible(action)) bits |= availabilityBit(action);
        return bits;
    }
    /** skillForの逆。開発機の画面がカタログ順に依存しないために必要。 */
    public static int actionFor(net.minecraft.resources.ResourceLocation skill) {
        for (int action : LEARN_ACTIONS) if (skillFor(action).equals(skill)) return action;
        return -1;
    }
    /** availability()がこの学習アクションを報告するビット。 */
    public static long availabilityBit(int action) {
        return switch (action) {
            case LEARN_ARC -> 2; case LEARN_CHARGING -> 4; case LEVEL_UP -> 8; case LEARN_MOVEMENT -> 16;
            case LEARN_MANIPULATION -> 32; case LEARN_INTENSIFY -> 64; case LEARN_MINE_DETECT -> 128;
            case LEARN_THUNDER_BOLT -> 256; case LEARN_RAILGUN -> 512; case LEARN_THUNDER_CLAP -> 1024;
            case LEARN_BRAIN -> 2048; case LEARN_ADVANCED_BRAIN -> 4096; case LEARN_MIND -> 8192;
            case LEARN_ELECTRON_BOMB -> 16384; case LEARN_SCATTER_BOMB -> 32768; case LEARN_LIGHT_SHIELD -> 65536; case LEARN_MINE_RAY_BASIC -> 131072; case LEARN_RAD_INTENSIFY -> 262144; case LEARN_MINE_RAY_EXPERT -> 524288; case LEARN_MINE_RAY_LUCK -> 1048576; case LEARN_JET_ENGINE -> 2097152; case LEARN_RAY_BARRAGE -> 4194304; case LEARN_ELECTRON_MISSILE -> 8388608; case LEARN_MELTDOWNER -> 16777216; case LEARN_THREATENING_TELEPORT -> 33554432; case LEARN_DIM_FOLDING -> 67108864; case LEARN_SPACE_FLUCT -> 134217728; case LEARN_PENETRATE_TELEPORT -> 268435456; case LEARN_MARK_TELEPORT -> 536870912; case LEARN_FLESH_RIPPING -> 1073741824; case LEARN_LOCATION_TELEPORT -> 2147483648L; case LEARN_SHIFT_TP -> 4294967296L; case LEARN_FLASHING -> 8589934592L; case LEARN_DIR_SHOCK -> 17179869184L; case LEARN_GROUND_SHOCK -> 34359738368L; case LEARN_VEC_ACCEL -> 68719476736L; case LEARN_VEC_DEVIATION -> 137438953472L; case LEARN_DIR_BLAST -> 274877906944L; case LEARN_STORM_WING -> 549755813888L; case LEARN_BLOOD_RETRO -> 1099511627776L; case LEARN_VEC_REFLECTION -> 2199023255552L; case LEARN_PLASMA_CANNON -> 4398046511104L;
            case RESET -> 17592186044416L;
            case ACQUIRE -> 1; default -> 0;
        };
    }
    private static final int[] LEARN_ACTIONS = {LEARN_ARC, LEARN_CHARGING, LEARN_MOVEMENT, LEARN_MANIPULATION,
            LEARN_INTENSIFY, LEARN_MINE_DETECT, LEARN_THUNDER_BOLT, LEARN_RAILGUN, LEARN_THUNDER_CLAP,
            LEARN_BRAIN, LEARN_ADVANCED_BRAIN, LEARN_MIND, LEARN_ELECTRON_BOMB, LEARN_SCATTER_BOMB, LEARN_LIGHT_SHIELD, LEARN_MINE_RAY_BASIC, LEARN_RAD_INTENSIFY, LEARN_MINE_RAY_EXPERT, LEARN_MINE_RAY_LUCK, LEARN_JET_ENGINE, LEARN_RAY_BARRAGE, LEARN_ELECTRON_MISSILE, LEARN_MELTDOWNER, LEARN_THREATENING_TELEPORT, LEARN_DIM_FOLDING, LEARN_SPACE_FLUCT, LEARN_PENETRATE_TELEPORT, LEARN_MARK_TELEPORT, LEARN_FLESH_RIPPING, LEARN_LOCATION_TELEPORT, LEARN_SHIFT_TP, LEARN_FLASHING, LEARN_DIR_SHOCK, LEARN_GROUND_SHOCK, LEARN_VEC_ACCEL, LEARN_VEC_DEVIATION, LEARN_DIR_BLAST, LEARN_STORM_WING, LEARN_BLOOD_RETRO, LEARN_VEC_REFLECTION, LEARN_PLASMA_CANNON};
    private static net.minecraft.resources.ResourceLocation skillFor(int action) {
        return action == LEARN_ARC ? ArcGen.ID : action == LEARN_CHARGING ? CurrentCharging.ID
                : action == LEARN_MOVEMENT ? MagneticMovement.ID : action == LEARN_MANIPULATION ? MagneticManipulation.ID : action == LEARN_INTENSIFY ? io.github.pinchan4273.reacademycraft.skill.BodyIntensify.ID : action == LEARN_MINE_DETECT ? io.github.pinchan4273.reacademycraft.skill.MineDetect.ID : action == LEARN_THUNDER_BOLT ? io.github.pinchan4273.reacademycraft.skill.ThunderBolt.ID : action == LEARN_RAILGUN ? io.github.pinchan4273.reacademycraft.skill.Railgun.ID : action == LEARN_THUNDER_CLAP ? io.github.pinchan4273.reacademycraft.skill.ThunderClap.ID
                : action == LEARN_BRAIN ? io.github.pinchan4273.reacademycraft.skill.GenericSkills.BRAIN : action == LEARN_ADVANCED_BRAIN ? io.github.pinchan4273.reacademycraft.skill.GenericSkills.ADVANCED_BRAIN : action == LEARN_ELECTRON_BOMB ? io.github.pinchan4273.reacademycraft.skill.ElectronBomb.ID : action == LEARN_SCATTER_BOMB ? io.github.pinchan4273.reacademycraft.skill.ScatterBomb.ID : action == LEARN_LIGHT_SHIELD ? io.github.pinchan4273.reacademycraft.skill.LightShield.ID : action == LEARN_MINE_RAY_BASIC ? io.github.pinchan4273.reacademycraft.skill.MineRay.BASIC.id() : action == LEARN_MINE_RAY_EXPERT ? io.github.pinchan4273.reacademycraft.skill.MineRay.EXPERT.id() : action == LEARN_MINE_RAY_LUCK ? io.github.pinchan4273.reacademycraft.skill.MineRay.LUCK.id() : action == LEARN_RAD_INTENSIFY ? io.github.pinchan4273.reacademycraft.skill.RadiationIntensify.ID : action == LEARN_JET_ENGINE ? io.github.pinchan4273.reacademycraft.skill.JetEngine.ID : action == LEARN_RAY_BARRAGE ? io.github.pinchan4273.reacademycraft.skill.RayBarrage.ID : action == LEARN_ELECTRON_MISSILE ? io.github.pinchan4273.reacademycraft.skill.ElectronMissile.ID : action == LEARN_MELTDOWNER ? io.github.pinchan4273.reacademycraft.skill.Meltdowner.ID : action == LEARN_THREATENING_TELEPORT ? io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.ID : action == LEARN_DIM_FOLDING ? io.github.pinchan4273.reacademycraft.skill.DimFoldingTheorem.ID : action == LEARN_SPACE_FLUCT ? io.github.pinchan4273.reacademycraft.skill.SpaceFluctuation.ID : action == LEARN_PENETRATE_TELEPORT ? io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.ID : action == LEARN_MARK_TELEPORT ? io.github.pinchan4273.reacademycraft.skill.MarkTeleport.ID : action == LEARN_FLESH_RIPPING ? io.github.pinchan4273.reacademycraft.skill.FleshRipping.ID : action == LEARN_LOCATION_TELEPORT ? io.github.pinchan4273.reacademycraft.skill.LocationTeleport.ID : action == LEARN_SHIFT_TP ? io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.ID : action == LEARN_FLASHING ? io.github.pinchan4273.reacademycraft.skill.Flashing.ID : action == LEARN_DIR_SHOCK ? io.github.pinchan4273.reacademycraft.skill.DirectedShock.ID : action == LEARN_GROUND_SHOCK ? io.github.pinchan4273.reacademycraft.skill.Groundshock.ID : action == LEARN_VEC_ACCEL ? io.github.pinchan4273.reacademycraft.skill.VecAccel.ID : action == LEARN_VEC_DEVIATION ? io.github.pinchan4273.reacademycraft.skill.VecDeviation.ID : action == LEARN_DIR_BLAST ? io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.ID : action == LEARN_STORM_WING ? io.github.pinchan4273.reacademycraft.skill.StormWing.ID : action == LEARN_BLOOD_RETRO ? io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.ID : action == LEARN_VEC_REFLECTION ? io.github.pinchan4273.reacademycraft.skill.VecReflection.ID : action == LEARN_PLASMA_CANNON ? io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.ID : io.github.pinchan4273.reacademycraft.skill.GenericSkills.MIND;
    }
}
