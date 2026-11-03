package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.DarkBeetleShot;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * v1.5.0 未元物質Lv4 甲虫生成（採用仕様・版41の§9、当たり判定はユーザーの決定で幅2.0・高さ1.75）。単発の生成で、最大3体（収納中も枠に数える）。
 * <p>
 * 生成: 費用lerp(800,400)CP＋lerp(30,20)過負荷、クールダウンlerp(5,3)秒。回復待ちを作らず延ばさない（{@link PlayerAbilityData#consumeQuiet}）。
 * 判定全体が収まる位置を先に探し、無ければ何も払わず何も消さずに中止する。「再召喚優先」で3体いるときは、支払いが成立してからHP割合の最低の個体を置き換える。
 * <p>
 * 修復（自動修復のときだけ）: 実際に戻した1HPにつき2CP。所有者が能力を使える間だけ。CPセーフティが通さなければ止まり、通るようになれば再開する。
 * 維持費・自律攻撃の追加費用は無く、所有者のCP回復を止めない。
 * <p>
 * 保存: 個体はチャンクに残らない（実体の型が{@code noSave}）。所有者の能力データに、活動中の写しと収納中の個体を記録する。ログアウトで全個体を収納し、
 * ログインで費用なしに復帰する（HPは戻さない）。次元が変わったら収納して移す。64ブロック超が2秒続いたら近くへ転移し、空間が無ければ収納して、空間ができたら復帰する。
 * 所有者の真の死亡と系統の喪失で、活動中も収納中も消える（CPは返らない）。能力OFF・過負荷・干渉・プリセット変更では消えない。
 * <p>
 * 標的の優先順位の最後に、仕様§9に無い「近くの敵」を足した（ユーザーの試遊の指摘）。§9の3段階のどれにも当たらないとき、所有者から
 * {@link #nearbyRange}以内で、所有者か甲虫から見えている、自動で狙ってよい相手（{@link #wanted}）の最も近いものを狙う。
 * 狙う分類（敵対・中立・味方・プレイヤー）は所有者が能力設定で選ぶ（2026-11-03のユーザーの決定。既定は敵対だけ）。
 * <p>
 * 追従開始距離（ユーザーの決定、能力設定の{@link AbilitySettings#BEETLE_FOLLOW}）: 所有者からこの距離Dを超えた個体は「帰還中」になり、標的を外して
 * 所有者へ戻る。D−{@link #FOLLOW_RESUME}以内まで戻ったら通常の行動へ戻す。個体は毎tick所有者のデータを読むので、変更は召喚済みの全個体へ次のtickから効く。
 * <p>
 * 領域（§10）の再生は、{@link HeavenEffects}が自動修復との差分だけを足す。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class DarkMatterBeetles {
    public static final ResourceLocation ID = DarkMatter.BEETLE;
    public static final int MAX_BEETLES = 3;
    /** 所有者からの距離の規則（§9の表）。RETURN_DISTANCEは追従開始距離の既定の値（{@link #FOLLOW_DEFAULT}と同じ）。 */
    public static final double RETURN_DISTANCE = 32, TELEPORT_DISTANCE = 64;
    public static final int TELEPORT_AFTER = 40;
    /** 追従開始距離の範囲・刻み・既定（ブロック。ユーザーの決定）。 */
    public static final int FOLLOW_MIN = 8, FOLLOW_MAX = 48, FOLLOW_STEP = 4, FOLLOW_DEFAULT = 32;
    /** 帰還中の個体は、追従開始距離からこれだけ内側まで戻ったら通常の行動へ戻る。 */
    public static final int FOLLOW_RESUME = 4;
    /** 近接の間隔（1.5秒）。 */
    public static final int MELEE_INTERVAL = 30;
    /** 所有者の全個体で1tickに壊せるブロック。 */
    public static final int BREAKS_PER_TICK = 12;
    /** 1命中の熟練度（＋0.05%）と、全個体合計の毎秒の上限（＋0.1%）。 */
    public static final float GAIN = .0005f, GAIN_PER_SECOND = .001f;
    /** 修復の費用（実際の1HPにつきCP）。 */
    public static final float REPAIR_CP_PER_HP = 2;
    /** 空気弾の速さ（ブロック/tick）。§9に値は無いので実装で決めた。 */
    public static final double SHOT_SPEED = 3;
    /** 攻撃の記憶の長さ（15秒）と、覚える数の上限。§9に値は無いので実装で決めた。 */
    public static final int MEMORY_TICKS = 300, MEMORY_LIMIT = 16;
    /** 近くの敵対モブを狙い始める範囲と、狙い続ける範囲の上限（所有者から。ブロック）。実際の範囲は{@link #nearbyRange}・{@link #nearbyKeep}。 */
    public static final double NEARBY_RANGE = 16, NEARBY_KEEP = 24;
    /** 追従の席: 所有者の後ろの半径と、個体ごとの開き（度）。向きは{@link #SEAT_YAW_SLACK}度を超えて変わるまで固定する（振り向くたびに走り回らない）。 */
    public static final double SEAT_RADIUS = 6, SEAT_SPREAD = 55, SEAT_YAW_SLACK = 75;
    /** これより近ければ離れる距離（水平）。甲虫の中心から所有者の中心、個体同士の中心。 */
    public static final double OWNER_CLEARANCE = 3.5, SIBLING_CLEARANCE = 3;
    /** 収納個体の復帰を試す間隔。 */
    private static final int RETRY_TICKS = 20;

    /** §9の攻撃方針。 */
    public enum Stance {
        NEAR("near"), MID("mid"), FAR("far");
        private final String id;
        Stance(String id) { this.id = id; }
        public String id() { return id; }
        public static @Nullable Stance parse(String value) { for (var s : values()) if (s.id.equals(value)) return s; return null; }
    }
    /** §9の修復方式。 */
    public enum Repair {
        AUTO("auto"), RESUMMON("resummon");
        private final String id;
        Repair(String id) { this.id = id; }
        public String id() { return id; }
        public static @Nullable Repair parse(String value) { for (var r : values()) if (r.id.equals(value)) return r; return null; }
    }

    private static final class Shot {
        final int id; final DarkMatterBeetle beetle; final net.minecraft.world.level.Level level; final float damage; final Vec3 direction; Vec3 at; double left;
        Shot(int id, DarkMatterBeetle beetle, Vec3 at, Vec3 direction, double range, float damage) {
            this.id = id; this.beetle = beetle; level = beetle.level(); this.at = at; this.direction = direction; left = range; this.damage = damage;
        }
    }
    /** 1人の所有者の個体と記憶。 */
    public static final class Party {
        private ServerPlayer player;
        private final List<DarkMatterBeetle> live = new ArrayList<>(MAX_BEETLES);
        private final List<CompoundTag> stored = new ArrayList<>(MAX_BEETLES);
        private final List<Shot> shots = new ArrayList<>();
        private final Map<LivingEntity, Long> attacked = new LinkedHashMap<>(), attackers = new LinkedHashMap<>();
        private long breakTick = -1, gainWindow = -100;
        private int broken, retry;
        private float gained;
        private boolean repairHeld, adopted, nearbyEnemies = true;
        private float seatYaw = Float.NaN;
        private long nearbyAt = Long.MIN_VALUE / 2;
        private List<LivingEntity> nearby = List.of();
        Party(ServerPlayer player) { this.player = player; }
        public ServerPlayer player() { return player; }
        public int indexOf(DarkMatterBeetle beetle) { return live.indexOf(beetle); }
        /** 同じ所有者の活動中の個体。 */
        public List<DarkMatterBeetle> members() { return live; }
        /** 試験用: 近くの敵対モブを狙うかを切り替える。 */
        public void nearbyEnemiesForTest(boolean on) { nearbyEnemies = on; }
    }
    private static final Map<UUID, Party> PARTIES = new HashMap<>();
    private static int nextShot;
    /** 甲虫自身の攻撃の間はtrue（所有者の攻撃として記憶しない）。 */
    private static boolean striking;

    private DarkMatterBeetles() { }
    public static @Nullable PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }

    public static float maxHealth(float p) { return ArcGen.lerp(100, 200, p); }
    public static float spawnCp(float p) { return ArcGen.lerp(800, 400, p); }
    public static float spawnOverload(float p) { return ArcGen.lerp(30, 20, p); }
    public static int spawnCooldown(float p) { return Math.round(ArcGen.lerp(100, 60, p)); }
    public static float repairPerSecond(float p) { return ArcGen.lerp(2, 6, p); }
    public static float meleeDamage(float p) { return ArcGen.lerp(10, 20, p); }
    public static float dashDamage(float p) { return ArcGen.lerp(14, 28, p); }
    public static double groundSpeed(float p) { return ArcGen.lerp(5, 7, p); }
    public static double flightSpeed(float p) { return ArcGen.lerp(12, 24, p); }
    public static double dashSpeed(float p) { return ArcGen.lerp(16, 24, p); }
    public static double dashRange(float p) { return ArcGen.lerp(8, 12, p); }
    public static int dashCooldown(float p) { return Math.round(ArcGen.lerp(160, 80, p)); }
    public static float shotDamage(float p) { return ArcGen.lerp(8, 16, p); }
    public static double shotRange(float p) { return ArcGen.lerp(16, 32, p); }
    public static int shotInterval(float p) { return Math.round(ArcGen.lerp(60, 40, p)); }
    /**
     * 地上の速さ（ブロック/秒）になる移動速度の属性。経路探索の歩行は、属性aで加速が0.98a²（zzaとfriction補正の積）になり、地上の摩擦0.546で
     * 終端速度が1tickあたり0.98a²/0.454なので、ブロック/秒はおよそ43.17a²。
     */
    public static double groundAttribute(float p) { return Math.sqrt(groundSpeed(p) / 43.17); }

    /** 所有者の熟練度。読めなければ0。 */
    public static float proficiency(ServerPlayer owner) { var d = data(owner); return d == null ? 0 : d.getProficiency(ID); }
    /** 追従開始距離として許される値か（8〜48の4刻み）。 */
    public static boolean validFollow(int blocks) { return blocks >= FOLLOW_MIN && blocks <= FOLLOW_MAX && blocks % FOLLOW_STEP == 0; }
    /** 所有者の追従開始距離。データが読めなければ既定の値。毎tick呼ばれるので、変更は次のtickから効く。 */
    public static int followDistance(ServerPlayer owner) { var d = data(owner); return d == null ? FOLLOW_DEFAULT : d.getBeetleFollow(); }
    /**
     * 近くの敵対モブを新しく狙う範囲（所有者から）: min(16, D−4)。狙い続ける範囲は{@link #nearbyKeep}でmin(24, D)。
     * Dが小さいとき、帰還の境（D）の外の敵を選んでは帰還で外す往復になるので、選ぶのは帰還をやめる距離（D−4）の内側、追い続けるのはDの内側に限る（ユーザーの決定）。
     */
    public static double nearbyRange(int follow) { return Math.min(NEARBY_RANGE, follow - FOLLOW_RESUME); }
    /** 近くの敵対モブを狙い続ける範囲（所有者から）: min(24, D)。理由は{@link #nearbyRange}。 */
    public static double nearbyKeep(int follow) { return Math.min(NEARBY_KEEP, follow); }

    private static boolean usable(PlayerAbilityData data) { return !data.isReadOnly() && DarkMatter.CATEGORY.equals(data.getAbility()) && data.hasLearned(ID); }
    /** 活動中の個体（写し）。 */
    public static List<DarkMatterBeetle> live(ServerPlayer player) { var party = PARTIES.get(player.getUUID()); return party == null ? List.of() : List.copyOf(party.live); }
    /** 収納中の個体の数。 */
    public static int storedCount(ServerPlayer player) { var party = PARTIES.get(player.getUUID()); return party == null ? 0 : party.stored.size(); }
    /** 枠に数える個体の数（活動中＋収納中）。 */
    public static int count(ServerPlayer player) { return live(player).size() + storedCount(player); }

    /**
     * 技能の入力: 生成する。空きが無ければ、自動修復は断り、再召喚優先はHP割合の最低の個体を置き換える。費用が足りない・セーフティ・位置が無いときは既存の個体を消さない。
     */
    public static String cast(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot)) || !player.isAlive() || !usable(data)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        var party = PARTIES.computeIfAbsent(player.getUUID(), id -> new Party(player));
        party.player = player;
        adopt(party, data);
        prune(party);
        boolean full = party.live.size() + party.stored.size() >= MAX_BEETLES;
        if (full && data.getBeetleRepair() != Repair.RESUMMON) return "academy.cast.beetle_full";
        var at = findSpace(player);
        if (at == null) return "academy.cast.beetle_no_space";
        float p = data.getProficiency(ID);
        if (!data.consumeQuiet(ID, spawnCp(p), spawnOverload(p), player.getAbilities().instabuild)) return "academy.cast.cp";
        if (full) replaceLowest(party);
        data.setCooldown(ID, spawnCooldown(p));
        spawn(party, at, null, p);
        record(party, data);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    /** HP割合の最低の個体（活動中か収納中）を消す。同じなら先にあるもの。 */
    private static void replaceLowest(Party party) {
        DarkMatterBeetle lowestLive = null; CompoundTag lowestStored = null; float lowest = Float.MAX_VALUE;
        for (var beetle : party.live) {
            float ratio = beetle.getHealth() / beetle.getMaxHealth();
            if (ratio < lowest) { lowest = ratio; lowestLive = beetle; lowestStored = null; }
        }
        float max = maxHealth(proficiency(party.player));
        for (var tag : party.stored) {
            float ratio = tag.getFloat("health") / max;
            if (ratio < lowest) { lowest = ratio; lowestStored = tag; lowestLive = null; }
        }
        if (lowestLive != null) { party.live.remove(lowestLive); lowestLive.discard(); }
        else if (lowestStored != null) party.stored.remove(lowestStored);
    }
    private static DarkMatterBeetle spawn(Party party, Vec3 at, @Nullable CompoundTag state, float p) {
        var level = party.player.serverLevel();
        var beetle = AcademyContent.DARK_MATTER_BEETLE.get().create(level);
        beetle.bind(party, party.player.getUUID());
        beetle.moveTo(at.x, at.y, at.z, party.player.getYRot(), 0);
        beetle.applyMaxHealth(p);
        beetle.setHealth(beetle.getMaxHealth());
        if (state != null) beetle.readState(state);
        level.addFreshEntity(beetle);
        party.live.add(beetle);
        return beetle;
    }
    /**
     * 所有者の近くで、当たり判定全体が収まる足元の位置を探す。ブロックにも液体にも重ならず、読み込み済みで、ワールドの境界の中で、足元の下3ブロック以内に床があること
     * （所有者が飛行中なら床は問わない）。見つからなければnull。
     */
    public static @Nullable Vec3 findSpace(ServerPlayer owner) {
        var level = owner.serverLevel();
        var type = AcademyContent.DARK_MATTER_BEETLE.get();
        double base = Math.toRadians(owner.getYRot());
        boolean airborne = owner.getAbilities().flying;
        for (int radius : new int[] {5, 7, 4}) {
            for (int a = 0; a < 8; a++) {
                double angle = base + a * Math.PI / 4;
                double x = owner.getX() - Math.sin(angle) * radius, z = owner.getZ() + Math.cos(angle) * radius;
                for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                    double y = Math.floor(owner.getY()) + dy;
                    var pos = BlockPos.containing(x, y, z);
                    if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                    AABB box = type.getAABB(x, y, z);
                    if (!level.noCollision(box) || level.containsAnyLiquid(box)) continue;
                    if (!airborne && level.noCollision(new AABB(box.minX, box.minY - 3, box.minZ, box.maxX, box.minY, box.maxZ))) continue;
                    return new Vec3(x, y, z);
                }
            }
        }
        return null;
    }

    /** 能力データの記録を、この状態を作った最初の一度だけ、収納個体として受け取る（ログイン・再起動の後）。以後は記録を読まない（倒された個体が古い記録から戻らない）。 */
    private static void adopt(Party party, PlayerAbilityData data) {
        if (party.adopted || data.isReadOnly()) return;
        party.adopted = true;
        ListTag records = data.getBeetleRecords();
        for (int i = 0; i < Math.min(MAX_BEETLES, records.size()); i++) party.stored.add(records.getCompound(i));
    }
    /** 消えた個体を一覧から外す。チャンクごと読み込まれなくなった個体は状態を収納へ移す。死んだ個体と消去された個体は枠を空ける。 */
    private static void prune(Party party) {
        for (Iterator<DarkMatterBeetle> it = party.live.iterator(); it.hasNext(); ) {
            var beetle = it.next();
            if (beetle.isDeadOrDying()) { it.remove(); continue; }
            if (!beetle.isRemoved()) continue;
            var reason = beetle.getRemovalReason();
            if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) party.stored.add(beetle.writeState());
            it.remove();
        }
    }
    private static void record(Party party, PlayerAbilityData data) {
        var list = new ListTag();
        for (var beetle : party.live) list.add(beetle.writeState());
        for (var tag : party.stored) list.add(tag.copy());
        data.setBeetleRecords(list);
    }
    private static void store(Party party, DarkMatterBeetle beetle) {
        party.live.remove(beetle);
        party.stored.add(beetle.writeState());
        beetle.discard();
    }
    /** 全個体を一時収納する（ログアウト）。状態は能力データに残る。 */
    public static void stash(ServerPlayer player) {
        var party = PARTIES.remove(player.getUUID());
        if (party == null) return;
        prune(party);
        for (var beetle : List.copyOf(party.live)) store(party, beetle);
        var data = data(player);
        if (data != null) record(party, data);
    }
    /** ログインの復帰: 記録を収納個体として受け取る（実体は次のtickに、空間が見つかれば出る）。 */
    public static void restore(ServerPlayer player) {
        var data = data(player);
        if (data == null || data.isReadOnly() || data.getBeetleRecords().isEmpty()) return;
        var party = PARTIES.computeIfAbsent(player.getUUID(), id -> new Party(player));
        party.player = player;
        adopt(party, data);
    }
    /**
     * 全個体の回収（能力設定、§15の系統喪失、真の死亡）。活動中も収納中も消える。CPは返さない。誰かいたらtrue。
     */
    public static boolean recallAll(ServerPlayer player) {
        var party = PARTIES.remove(player.getUUID());
        var data = data(player);
        boolean any = data != null && !data.getBeetleRecords().isEmpty();
        if (party != null) {
            any |= !party.live.isEmpty() || !party.stored.isEmpty();
            for (var beetle : party.live) beetle.discard();
        }
        if (data != null) data.setBeetleRecords(new ListTag());
        return any;
    }

    // ---- 毎tickの処理 ----

    private static void tick(Party party) {
        var player = party.player;
        var data = data(player);
        if (data == null || data.isReadOnly()) return;
        if (!usable(data)) { recallAll(player); return; }
        prune(party);
        var level = player.serverLevel();
        float p = data.getProficiency(ID);
        for (var beetle : List.copyOf(party.live)) {
            // 別の次元に残った個体は収納し、下の復帰で所有者の次元へ出す。
            if (beetle.level() != level) { store(party, beetle); party.retry = 0; continue; }
            distance(party, beetle);
        }
        repair(party, data, p);
        tickShots(party);
        if (!party.stored.isEmpty() && player.isAlive() && --party.retry <= 0) {
            party.retry = RETRY_TICKS;
            for (Iterator<CompoundTag> it = party.stored.iterator(); it.hasNext(); ) {
                var at = findSpace(player);
                if (at == null) break;
                var state = it.next();
                it.remove();
                spawn(party, at, state, p);
            }
        }
        if (level.getGameTime() % 20 == 0) {
            for (var beetle : party.live) beetle.applyMaxHealth(p);
            forget(party.attacked, level.getGameTime());
            forget(party.attackers, level.getGameTime());
            record(party, data);
        }
    }
    private static void forget(Map<LivingEntity, Long> memory, long now) { memory.entrySet().removeIf(e -> now - e.getValue() > MEMORY_TICKS || !e.getKey().isAlive()); }
    /** 64ブロック超が2秒続いたら安全な近くへ転移する。空間が無ければ収納する（§9の表）。 */
    private static void distance(Party party, DarkMatterBeetle beetle) {
        if (beetle.distanceTo(party.player) <= TELEPORT_DISTANCE) { beetle.farTicks = 0; return; }
        if (++beetle.farTicks < TELEPORT_AFTER) return;
        beetle.farTicks = 0;
        var at = findSpace(party.player);
        if (at == null) { store(party, beetle); return; }
        beetle.getNavigation().stop();
        beetle.teleportTo(at.x, at.y, at.z);
    }
    /**
     * 追従の席（足元の位置）。1体目は所有者の真後ろ、2体目と3体目はその左右。飛行中は所有者より少し上。判定が収まらなければ少し近い席を試し、無ければnull。
     */
    public static @Nullable Vec3 seat(Party party, DarkMatterBeetle beetle, boolean airborne) {
        var owner = party.player;
        if (Float.isNaN(party.seatYaw) || net.minecraft.util.Mth.degreesDifferenceAbs(party.seatYaw, owner.yBodyRot) > SEAT_YAW_SLACK) party.seatYaw = owner.yBodyRot;
        int index = Math.max(0, party.indexOf(beetle));
        double angle = Math.toRadians(party.seatYaw + 180 + new int[] {0, 1, -1}[Math.min(index, 2)] * SEAT_SPREAD);
        var level = owner.level();
        var type = AcademyContent.DARK_MATTER_BEETLE.get();
        for (double radius : new double[] {SEAT_RADIUS, SEAT_RADIUS - 1.5}) {
            double x = owner.getX() - Math.sin(angle) * radius, z = owner.getZ() + Math.cos(angle) * radius;
            for (double dy : airborne ? new double[] {2.5, 1, 0, 4} : new double[] {0, 1, -1, 2, -2}) {
                double y = airborne ? owner.getY() + dy : Math.floor(owner.getY() + .01) + dy;
                AABB box = type.getAABB(x, y, z);
                if (!level.noCollision(box) || level.containsAnyLiquid(box)) continue;
                if (!airborne && level.noCollision(new AABB(box.minX, box.minY - 1, box.minZ, box.maxX, box.minY, box.maxZ))) continue;
                return new Vec3(x, y, z);
            }
        }
        return null;
    }
    /** 席を見つけられないときの、位置だけの席（判定は確かめない）。遠くから帰るための目印。 */
    public static Vec3 roughSeat(Party party, DarkMatterBeetle beetle) {
        var owner = party.player;
        double angle = Math.toRadians(owner.yBodyRot + 180 + new int[] {0, 1, -1}[Math.min(Math.max(0, party.indexOf(beetle)), 2)] * SEAT_SPREAD);
        return owner.position().add(-Math.sin(angle) * SEAT_RADIUS, 0, Math.cos(angle) * SEAT_RADIUS);
    }
    private static void repair(Party party, PlayerAbilityData data, float p) {
        if (data.getBeetleRepair() != Repair.AUTO || !data.canUseAbility()) return;
        boolean creative = party.player.getAbilities().instabuild;
        float each = repairPerSecond(p) / 20;
        for (var beetle : party.live) {
            if (beetle.isDeadOrDying()) continue;
            float healed = Math.min(each, beetle.getMaxHealth() - beetle.getHealth());
            if (healed <= 0) continue;
            float cost = healed * REPAIR_CP_PER_HP;
            if (!creative) {
                if (data.getCp() < cost) continue;
                // セーフティの作動は最初の1回だけ知らせ、止まっている間は確かめるだけ（通るようになったら自動で再開する。§14）。
                if (party.repairHeld ? !data.safetyWouldPermit(ID, cost) : !data.safetyPermits(ID, cost, false)) { party.repairHeld = true; return; }
                party.repairHeld = false;
                data.payWithoutWait(cost);
            }
            beetle.setHealth(beetle.getHealth() + healed);
        }
    }

    // ---- 攻撃 ----

    /** 1回の攻撃を当てる（角・突進・空気弾の命中）。有効な命中なら熟練度を上げる（全個体合計で毎秒の上限まで）。 */
    public static boolean strike(Party party, @Nullable DarkMatterBeetle beetle, LivingEntity target, float damage) {
        var owner = party.player;
        boolean hurt;
        striking = true;
        try {
            hurt = SkillCombat.attackVia(owner, beetle != null && beetle.isAlive() ? beetle : owner, target, ID, damage);
        } finally {
            striking = false;
        }
        if (hurt) gain(party);
        return hurt;
    }
    private static void gain(Party party) {
        var data = data(party.player);
        if (data == null || !usable(data)) return;
        long now = party.player.level().getGameTime();
        if (now - party.gainWindow >= 20) { party.gainWindow = now; party.gained = 0; }
        if (party.gained + GAIN > GAIN_PER_SECOND + 1e-6f) return;
        party.gained += GAIN;
        data.addProficiency(ID, GAIN);
    }
    /** 空気砲撃を放つ。直進・追尾なし（発射の後は{@link #tickShots}が進める。方針を変えても完了する）。 */
    public static void fire(Party party, DarkMatterBeetle beetle, Vec3 from, Vec3 toward, float p) {
        Vec3 to = toward.subtract(from);
        if (to.lengthSqr() < 1e-6) return;
        var shot = new Shot(++nextShot, beetle, from, to.normalize(), shotRange(p), shotDamage(p));
        party.shots.add(shot);
        send(beetle, new DarkBeetleShot(beetle.getId(), shot.id, DarkBeetleShot.FIRE, from, shot.direction, (float) shot.left, (float) SHOT_SPEED));
    }
    private static void send(DarkMatterBeetle beetle, DarkBeetleShot message) {
        AcademyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> beetle), message);
    }
    private static void tickShots(Party party) {
        for (Iterator<Shot> it = party.shots.iterator(); it.hasNext(); ) {
            var shot = it.next();
            // 所有者が次元を移ったら、前の次元の弾はそこで終わる。
            if (shot.level != party.player.level()) { it.remove(); continue; }
            var stopped = advance(party, shot);
            if (stopped == null) continue;
            it.remove();
            send(shot.beetle, new DarkBeetleShot(shot.beetle.getId(), shot.id, DarkBeetleShot.IMPACT, stopped, shot.direction, 0, 0));
        }
    }
    /** 弾を1tick進める。止まったらその点を返す。単体で、敵かブロックで止まり、地形を壊さない。 */
    private static @Nullable Vec3 advance(Party party, Shot shot) {
        var level = party.player.level();
        double length = Math.min(SHOT_SPEED, shot.left);
        Vec3 end = shot.at.add(shot.direction.scale(length));
        var block = level.clip(new ClipContext(shot.at, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, party.player));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        LivingEntity hit = null; double nearest = Double.MAX_VALUE; Vec3 point = reach;
        for (var candidate : level.getEntities(party.player, new AABB(shot.at, reach).inflate(1), e -> e instanceof LivingEntity l && isEnemy(party, shot.beetle, l))) {
            var at = candidate.getBoundingBox().inflate(.3).clip(shot.at, reach);
            if (at.isPresent() && shot.at.distanceToSqr(at.get()) < nearest) { nearest = shot.at.distanceToSqr(at.get()); hit = (LivingEntity) candidate; point = at.get(); }
        }
        if (hit != null) { strike(party, shot.beetle, hit, shot.damage); return point; }
        if (block.getType() != HitResult.Type.MISS) return reach;
        shot.at = end;
        shot.left -= length;
        return shot.left <= 1e-6 ? end : null;
    }

    // ---- 標的 ----

    /** この甲虫が攻撃してよい相手か（所有者・同じ所有者の個体は除く。PvPなどの可否は{@link SkillCombat#mayAttack}）。 */
    public static boolean validTarget(Party party, LivingEntity target) {
        if (target == party.player || target instanceof DarkMatterBeetle b && b.isOwnedBy(party.player)) return false;
        return target.level() == party.player.level() && ProjectileHitSeam.hittable(target) && SkillCombat.mayAttack(party.player, target, ID);
    }
    /** 突進や空気弾を止める「敵」か: 優先順位の表にいる相手か、敵対する生き物。通りがかりの動物や他のプレイヤーは巻き込まない。 */
    public static boolean isEnemy(Party party, @Nullable DarkMatterBeetle beetle, LivingEntity entity) {
        if (!validTarget(party, entity)) return false;
        return wanted(party, entity) || rank(party, beetle, entity, party.player.level().getGameTime()) < 3 || (beetle != null && beetle.currentTarget() == entity);
    }
    /**
     * 甲虫の見方での分類。Electron Missileと同じ{@link ElectronMissileTargeting#classify}（プレイヤー・味方・術者を狙う物・中立・{@link Enemy}・その他）に、
     * 所有者の甲虫を狙っているMobと、怒っている中立のMobを「敵対」として足す（怒った中立は以前から狙っていた）。対象にならない物はnull。
     */
    public static @Nullable ElectronMissileTargetKind classify(Party party, LivingEntity entity) {
        var kind = ElectronMissileTargeting.classify(party.player, entity);
        if (kind != ElectronMissileTargetKind.NEUTRAL && kind != ElectronMissileTargetKind.FRIENDLY) return kind;
        if (kind == ElectronMissileTargetKind.FRIENDLY && ElectronMissileTargeting.ally(party.player, entity)) return kind;
        if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() instanceof DarkMatterBeetle b && b.isOwnedBy(party.player)) return ElectronMissileTargetKind.HOSTILE;
        if (entity instanceof net.minecraft.world.entity.NeutralMob neutral && neutral.isAngry()) return ElectronMissileTargetKind.HOSTILE;
        return kind;
    }
    /** 自動で狙ってよい相手か: 所有者が能力設定でONにした分類（既定は敵対だけ）。攻撃の記憶による反撃（{@link #rank}）はこれに関係なく行う。 */
    public static boolean wanted(Party party, LivingEntity entity) {
        var data = data(party.player);
        var kind = classify(party, entity);
        return data != null && kind != null && data.beetleTargets(kind);
    }
    /** 近くの敵として狙えるか: 自動で狙ってよい分類で、所有者からrange以内で、所有者か甲虫から見えている。壁の向こうの敵を掘りに行かない。 */
    private static boolean nearbyUsable(Party party, DarkMatterBeetle beetle, LivingEntity entity, double range) {
        return party.nearbyEnemies && entity.isAlive() && wanted(party, entity) && validTarget(party, entity) && party.player.distanceTo(entity) <= range
                && (party.player.hasLineOfSight(entity) || beetle.hasLineOfSight(entity));
    }
    /** 所有者の周りの、自動で狙ってよい相手の候補（10tickごとに探し直す）。 */
    private static List<LivingEntity> nearby(Party party, long now) {
        if (now - party.nearbyAt >= 10) {
            party.nearbyAt = now;
            party.nearby = party.nearbyEnemies ? party.player.level().getEntitiesOfClass(LivingEntity.class, party.player.getBoundingBox().inflate(NEARBY_RANGE), e -> wanted(party, e)) : List.of();
        }
        return party.nearby;
    }
    /** 優先順位: 0 所有者が攻撃した敵、1 所有者を攻撃した敵、2 甲虫自身を攻撃した敵、3 どれでもない。 */
    public static int rank(Party party, @Nullable DarkMatterBeetle beetle, LivingEntity entity, long now) {
        if (fresh(party.attacked, entity, now)) return 0;
        if (fresh(party.attackers, entity, now)) return 1;
        if (beetle != null && fresh(beetle.attackers(), entity, now)) return 2;
        return 3;
    }
    private static boolean fresh(Map<LivingEntity, Long> memory, LivingEntity entity, long now) {
        var at = memory.get(entity);
        return at != null && now - at <= MEMORY_TICKS;
    }
    /**
     * 標的を選ぶ。今の標的が有効で、より高い順位の相手がいなければ維持する（同順位なら維持。§9）。どれにも当たらなければ近くの敵対モブの最も近い相手。
     * 近くの敵対モブを狙っている間も、毎回上位の記憶を先に見るので、上位の相手が現れればすぐ切り替える。帰還中の個体はこれを呼ばない（{@link DarkMatterBeetle}）。
     */
    public static @Nullable LivingEntity chooseTarget(Party party, DarkMatterBeetle beetle, @Nullable LivingEntity current) {
        var chosen = chooseTarget(party, beetle, current, null);
        // 2026-11-03の試遊の指摘: 3体すべてが同じ敵へ行かず、1体はなるべく別の敵を狙う。並びの後ろの個体は、前の2体がそろって同じ敵を狙っていれば、
        // その敵を除いて選び直す（優先順位はそのまま）。別の候補が無ければ同じ敵を狙う。並びで決めるので、毎tickの選び直しで行ったり来たりしない。
        if (chosen != null && crowdedBefore(party, beetle, chosen)) {
            var other = chooseTarget(party, beetle, current == chosen ? null : current, chosen);
            if (other != null) return other;
        }
        return chosen;
    }
    /** 同じ敵を同時に狙う個体の上限（これを超える個体は別の敵を探す）。 */
    public static final int SAME_TARGET_LIMIT = 2;
    /** 並びでこの個体より前の個体のうち、targetを狙っている数が上限に届いているか。 */
    private static boolean crowdedBefore(Party party, DarkMatterBeetle beetle, LivingEntity target) {
        int index = party.indexOf(beetle), count = 0;
        if (index < 0) return false;
        for (int i = 0; i < index && i < party.live.size(); i++) if (party.live.get(i).currentTarget() == target) count++;
        return count >= SAME_TARGET_LIMIT;
    }
    private static @Nullable LivingEntity chooseTarget(Party party, DarkMatterBeetle beetle, @Nullable LivingEntity current, @Nullable LivingEntity exclude) {
        long now = beetle.level().getGameTime();
        if (current != null && current == exclude) current = null;
        int follow = followDistance(party.player);
        boolean keep = current != null && current.isAlive() && validTarget(party, current);
        int rankNow = keep ? rank(party, beetle, current, now) : 3;
        LivingEntity best = null; int bestRank = rankNow; long bestAt = Long.MIN_VALUE;
        for (int r = 0; r < Math.min(rankNow, 3); r++) {
            var memory = r == 0 ? party.attacked : r == 1 ? party.attackers : beetle.attackers();
            for (var entry : memory.entrySet()) {
                if (now - entry.getValue() > MEMORY_TICKS || entry.getKey() == exclude || !entry.getKey().isAlive() || !validTarget(party, entry.getKey())) continue;
                if (r < bestRank || (r == bestRank && entry.getValue() >= bestAt)) { best = entry.getKey(); bestRank = r; bestAt = entry.getValue(); }
            }
            if (best != null) return best;
        }
        if (keep && rankNow < 3) return current;
        if (current != null && nearbyUsable(party, beetle, current, nearbyKeep(follow))) return current;
        LivingEntity closest = null; double nearest = Double.MAX_VALUE;
        double range = nearbyRange(follow);
        for (var candidate : nearby(party, now)) {
            double d = beetle.distanceToSqr(candidate);
            if (d < nearest && candidate != exclude && nearbyUsable(party, beetle, candidate, range)) { nearest = d; closest = candidate; }
        }
        return closest;
    }
    /** 箱の中で最初に当たる敵（突進の命中）。最も近いもの。 */
    public static @Nullable LivingEntity firstEnemy(Party party, DarkMatterBeetle beetle, AABB box) {
        LivingEntity best = null; double nearest = Double.MAX_VALUE;
        for (var candidate : beetle.level().getEntities(beetle, box, e -> e instanceof LivingEntity l && isEnemy(party, beetle, l))) {
            double d = beetle.distanceToSqr(candidate);
            if (d < nearest) { nearest = d; best = (LivingEntity) candidate; }
        }
        return best;
    }

    // ---- 接触ブロック破壊 ----

    /** 所有者の設定がONで、ゲームルール（mobGriefing。Forgeのイベントを含む）が許すか。毎回読むので切り替えは即時に効く。 */
    public static boolean contactBreakAllowed(Party party, DarkMatterBeetle beetle) {
        var data = data(party.player);
        return data != null && data.isBeetleContactBreak() && ForgeEventFactory.getMobGriefingEvent(beetle.level(), beetle);
    }
    /** このブロックを接触で壊してよいか: 破壊不能・ブロックエンティティ・保護（建築権限、スポーン保護、設定、Forgeの破壊のイベント）は対象外。 */
    public static boolean mayBreak(Party party, DarkMatterBeetle beetle, BlockPos pos, BlockState state) {
        var level = beetle.level();
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0 && !state.hasBlockEntity() && level.getWorldBorder().isWithinBounds(pos)
                && state.canEntityDestroy(level, pos, beetle) && ForgeEventFactory.onEntityDestroyBlock(beetle, pos, state)
                && AcademyConfig.canDestroy(party.player, pos, ID)
                && !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, party.player));
    }
    /** 今tickの破壊の枠を1つ取る（同じ所有者の全個体で12ブロック）。 */
    public static boolean takeBreak(Party party) {
        long now = party.player.level().getGameTime();
        if (party.breakTick != now) { party.breakTick = now; party.broken = 0; }
        if (party.broken >= BREAKS_PER_TICK) return false;
        party.broken++;
        return true;
    }

    // ---- イベント ----

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (var party : List.copyOf(PARTIES.values())) tick(party);
    }
    /** 試験用の入口: この所有者の処理を今すぐ1回行う。 */
    public static void tickForTest(ServerPlayer player) { var party = PARTIES.get(player.getUUID()); if (party != null) tick(party); }
    /** 試験用の入口: 甲虫が使う状態の本体。 */
    public static @Nullable Party partyForTest(ServerPlayer player) { return PARTIES.get(player.getUUID()); }

    /**
     * §9の優先順位の記憶。所有者が攻撃した相手、所有者を攻撃した相手、甲虫自身を攻撃した相手。損傷の有無によらず「攻撃した」を覚えるので、攻撃の入口のイベントを、
     * 他のリスナーや防御が取り消した場合も含めて受ける。
     */
    @SubscribeEvent(receiveCanceled = true) public static void attack(LivingAttackEvent e) {
        var victim = e.getEntity();
        if (!(e.getSource().getEntity() instanceof LivingEntity attacker) || victim.level().isClientSide) return;
        long now = victim.level().getGameTime();
        if (victim instanceof DarkMatterBeetle beetle) { if (!beetle.isFriendly(attacker)) beetle.noteAttacker(attacker); return; }
        if (attacker instanceof ServerPlayer player && !striking && victim != player) {
            var party = PARTIES.get(player.getUUID());
            if (party != null) note(party.attacked, victim, now);
        }
        if (victim instanceof ServerPlayer player && attacker != player) {
            var party = PARTIES.get(player.getUUID());
            if (party != null) note(party.attackers, attacker, now);
        }
    }
    private static void note(Map<LivingEntity, Long> memory, LivingEntity entity, long now) {
        memory.remove(entity);
        memory.put(entity, now);
        if (memory.size() > MEMORY_LIMIT) { var first = memory.keySet().iterator(); first.next(); first.remove(); }
    }
    /** §9: 召喚者本人の飛び道具は、自分の甲虫に当たらず通り抜ける。 */
    @SubscribeEvent public static void projectile(ProjectileImpactEvent e) {
        if (e.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof DarkMatterBeetle beetle && beetle.isFriendly(e.getProjectile().getOwner()))
            e.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
    }
    /** 所有者の真の死亡で全個体が消える。人間化・トーテム・他MODの救済による致死の取消では呼ばれない（{@link DeathOrder}が最後に判断して呼ぶ）。 */
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) recallAll(player);
    }
    @SubscribeEvent public static void category(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CategoryChange e) {
        if (!(e.player() instanceof ServerPlayer player)) return;
        var data = data(player);
        if (data != null && !usable(data)) recallAll(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) restore(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stash(p); }
    /** エンドからの帰還などで所有者の実体が新しくなったら付け替える（死亡の場合は既に回収済み）。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        var party = PARTIES.get(e.getEntity().getUUID());
        if (party != null && e.getEntity() instanceof ServerPlayer player) party.player = player;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { PARTIES.clear(); }
}
