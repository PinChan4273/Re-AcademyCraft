package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.SkillModeState;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Target;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質Lv5 天界発現。使用者を中心に動くトグルの領域（1.5.2 からチャンクの境目に合わせず、使用者の位置について連続に動く）。
 * <p>
 * 範囲: 熟練度50%未満は一辺16ブロック、100%未満は48ブロック、100%は80ブロックの正方形（それぞれ1・3×3・5×5チャンクと同じ広さ）で、
 * 上下はそれぞれ16ブロック。走査は読み込み済みのエンティティだけで、チャンクを読み込まない。
 * 範囲の比較のために{@code /heaven_measure}で範囲を切り替え、サーバー側の処理時間の95パーセンタイルを読める（実測は未実施）。
 * <p>
 * 費用: 開始200CP＋過負荷20。維持は基本lerp(40,80)＋選んだ種類ごとの費用に、1.5.2から部類の外の切り替え型の共通の
 * 回復の割合（回復の lerp(100%, 25%)。{@link PlayerAbilityData.Upkeep#recoveryShare}）を足したもの。過負荷なし、回復待ちを作らない。
 * 実回復・修復などの費用は技能の倍率を掛けて払い、払った分だけ最大CPが伸びる。CPセーフティは毎tickの維持の精算（{@link PlayerAbilityData}）で判定する。
 * <p>
 * 終了: トグル、能力OFF、過負荷、干渉、セーフティ、真の死亡、ディメンション移動、ログアウト、系統や習得の喪失。プリセットの変更では続き、人間化の致死取消（取り消された死亡イベント）でも続く。
 * 設定だけが保存され、再起動・再ログインはOFFから始まる。
 * <p>
 * 対象の区分は{@link #classify}（優先順位で決める）。効果の解決と反映は{@link HeavenEffects}が行う。
 * <p>
 * 1.5.2: 能力設定でパッシブにすると、能力使用モードの間に{@link PassiveSkills}が自動で始める（スロットのインデックスは{@link PassiveSkills#SLOT}）。
 * 開始の費用・維持費はキーで始めるときと同じ。パッシブの領域は、アクティブに戻すと解除する。天界発現の設定の画面の「解除」は、パッシブの領域には効かない
 * （解除してもすぐ起動し直し、開始の費用を二度払うため）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class HeavenManifest {
    public static final ResourceLocation ID = DarkMatter.HEAVEN;
    public static final float START_CP = 200, START_OVERLOAD = 20;
    /** 役立った1秒の熟練度（＋0.1%）。 */
    public static final float GAIN = .001f;
    /** 実回復・修復の費用（1HPにつきCP）。 */
    public static final float RECOVER_CP_PER_HP = 2;
    /** 上下の範囲（ブロック）。 */
    public static final double VERTICAL = 16;
    /** 走査の間隔（tick）。変更・解除の直後は間隔を待たず次のtickに走査する。 */
    public static final int SCAN_TICKS = 5;
    /**
     * 人体生成の実装との接続点: このエンティティが有害な効果を受けない身体か（未元物質人間）。真なら弱体化・怪現象を受けず、HP回復もしない（費用も請求しない）。
     * 人体生成の実装がここへ判定を置く。既定は誰も該当しない。
     */
    public static Predicate<LivingEntity> harmImmune = DarkBody::immuneToDebuffs;

    /**
     * 範囲。1.5.2から、中心は使用者の位置（ブロック単位でなく連続の座標）で、チャンクの境目に合わせない。
     * shape は広さの段（1・9・25 は、それぞれ 1・3×3・5×5 チャンクと同じ一辺 16・48・80 ブロックの正方形）。5 は範囲の比較用で、
     * 以前の5チャンクの十字の代わりに、中心から |dx| ＋ |dz| ≦ 24 の菱形にした（十字の腕の長さと同じ）。
     */
    public record Area(double centerX, double centerZ, int shape, double centerY) {
        /** 中心から端までの水平の距離（ブロック）: 1 なら 8、9 と 5 なら 24、25 なら 40。 */
        public static double half(int shape) { return shape == 1 ? 8 : shape == 25 ? 40 : 24; }
        public double half() { return half(shape); }
        public boolean contains(double x, double y, double z) {
            if (Math.abs(y - centerY) > VERTICAL) return false;
            double dx = Math.abs(x - centerX), dz = Math.abs(z - centerZ), h = half();
            return shape == 5 ? dx + dz <= h : dx <= h && dz <= h;
        }
        public boolean contains(LivingEntity entity) { return contains(entity.getX(), entity.getY(), entity.getZ()); }
        AABB box() {
            double h = half();
            return new AABB(centerX - h, centerY - VERTICAL - 4, centerZ - h, centerX + h, centerY + VERTICAL + 1, centerZ + h);
        }
    }

    /** 動作中の1つの領域。idは再起動をまたいで重ならない（変異は恒久的で担当の領域を記録しないので、変異には使わない）。 */
    public static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player; final Level level; final PlayerAbilityData data;
        /** 始めたスロット（1.5.2。パッシブの自動の起動は{@link PassiveSkills#SLOT}）。領域はプリセットの変更で続くので、続ける判定には使わない。 */
        int slot;
        int age, usefulClock, drawIn; float proficiency; boolean useful;
        @Nullable HeavenPhenomena.Pending pending;
        PlayerAbilityData.Upkeep upkeep;
        Area area;
        @Nullable Area sentArea;
        List<LivingEntity> inside = List.of();
        Session(ServerPlayer player, PlayerAbilityData data) {
            this.player = player; this.data = data; level = player.level();
            proficiency = data.getProficiency(ID);
            area = areaOf(player, proficiency);
        }
        public UUID id() { return id; }
        public ServerPlayer player() { return player; }
        public Area area() { return area; }
        /** この領域の維持（テストと表示用。常時の効果の過負荷の確保を持つ）。 */
        public PlayerAbilityData.Upkeep upkeep() { return upkeep; }
        public float proficiency() { return proficiency; }
        public HeavenSettings settings() { return data.getHeavenSettings(); }
        /** この領域が今も動いているか。 */
        public boolean alive() { return ACTIVE.get(player.getUUID()) == this; }
        /** 費用を払えるなら払う。不足・セーフティなら何もせずfalse（作動させない: 見送りと修復の停止で、領域解除ではない）。クリエイティブは無料。 */
        boolean tryPay(float cost) {
            if (cost <= 0 || player.getAbilities().instabuild) return true;
            if (!canPay(cost)) return false;
            data.payWithoutWait(ID, cost);
            return true;
        }
        boolean canPay(float cost) {
            if (cost <= 0 || player.getAbilities().instabuild) return true;
            return data.getCp() >= PlayerAbilityData.skillCp(ID, cost) && data.safetyWouldPermit(ID, cost);
        }
    }
    private static final Map<UUID, Session> ACTIVE = new LinkedHashMap<>();
    /** 効果を今すぐ解決し直す（開始・解除・設定の変更）。 */
    private static boolean dirty;
    /** 比較用の範囲の広さの段（チャンク数で数えた 1・5・9・25。{@link Area}）。0は熟練度どおり。 */
    private static int areaOverride = Integer.getInteger("academy.heaven.area", 0);

    private HeavenManifest() { }
    private static @Nullable PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    // ---- 値 ----

    /** 熟練度に応じた範囲のチャンク数（50%と100%で切り替える）。 */
    public static int shape(float p) { return p < .5f ? 1 : p < 1 ? 9 : 25; }
    public static float baseCp(float p) { return ArcGen.lerp(40, 80, p); }
    public static float effectCp(float p) { return ArcGen.lerp(12, 6, p); }
    public static float dotCp(float p) { return ArcGen.lerp(24, 12, p); }
    /**
     * 1.5.2: 常時の効果は稼働ではなく維持費。効果がかかっているマス（{@link HeavenEffects#cells}）1つにつき、
     * この量の過負荷を確保する（防御の確保と同じ形。回復は止めない）。熟練度0%で20、100%で10。
     */
    public static float cellReserve(float p) { return ArcGen.lerp(20, 10, p); }
    /** 毎秒の維持費。効果は選んだ種類ごとに一度で、人数・対象区分の数で増えない。再生は実回復で別に払う。 */
    public static float upkeepPerSecond(HeavenSettings settings, float p) {
        float total = baseCp(p);
        for (var effect : HeavenSettings.Effect.values()) {
            if (effect == HeavenSettings.Effect.REGEN || !settings.effectSelected(effect)) continue;
            total += effect == HeavenSettings.Effect.DOT ? dotCp(p) : effectCp(p);
        }
        return total;
    }
    public static Area areaOf(Player player, float p) {
        return new Area(player.getX(), player.getZ(), areaOverride > 0 ? areaOverride : shape(p), player.getY());
    }

    // ---- 状態 ----

    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static @Nullable Session session(ServerPlayer player) { return ACTIVE.get(player.getUUID()); }
    public static @Nullable Session session(UUID id) {
        for (var session : ACTIVE.values()) if (session.id.equals(id)) return session;
        return null;
    }
    /** 範囲の測定用: その領域の中の対象の数。 */
    public static int insideCount(Session session) { return session.inside.size(); }
    public static int ageOn(ServerPlayer player, int slot) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.age; }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }
    /** 1.5.2: パッシブにした時に、スロットのキーで始めた領域が動いていたら、止めずにパッシブの領域にする（開始の費用を二度払わない）。 */
    public static void adoptPassive(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.slot = PassiveSkills.SLOT;
    }
    static Iterable<Session> sessions() { return ACTIVE.values(); }
    /** 効果を次のtickに解決し直す（設定の変更で呼ぶ）。 */
    public static void markDirty() { dirty = true; }

    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        // 1.5.2: slotが{@link PassiveSkills#SLOT}なら、パッシブの自動の起動（パッシブに切り替えていること）。
        if (!PassiveSkills.slotMatches(data, ID, slot) || !player.isAlive() || data.isReadOnly()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        if (!data.consumeQuiet(ID, START_CP, START_OVERLOAD, player.getAbilities().instabuild)) return "academy.cast.cp";
        // 開始の過負荷で使えなくなったら始めない（払った分はそのまま。他の技能と同じ）。
        if (!data.canUseAbility(ID)) { AbilitySyncEvents.sync(player, true); return "academy.cast.overload"; }
        var session = new Session(player, data);
        session.slot = slot;
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        if (session.upkeep == null) return "academy.cast.unlearned";
        session.upkeep.recoveryShare().custom(granted -> upkeepPerSecond(data.getHeavenSettings(), data.getProficiency(ID)) / 20.0);
        ACTIVE.put(player.getUUID(), session);
        session.drawIn = HeavenPhenomena.interval(session.proficiency);
        scan(session);
        dirty = true;
        SkillModeState.send(player, ID, true);
        HeavenVisuals.region(session, true);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    /** 領域を解除する。この領域の補正・状態異常・発光は次の処理で外れ、損傷・回復・死亡と変異（恒久的）は巻き戻さない。 */
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        session.data.endUpkeep(session.upkeep);
        dirty = true;
        SkillModeState.send(player, ID, false);
        HeavenVisuals.regionOff(session);
        if (player.connection != null && !session.data.isReadOnly()) AbilitySyncEvents.sync(player, true);
    }
    private static boolean continues(Session session) {
        var player = session.player; var data = session.data;
        // 1.5.2: パッシブの領域は、パッシブのままであること（アクティブへ戻すと{@link PassiveSkills#set}が止めるが、念のためここでも見る）。
        return player.isAlive() && !data.isReadOnly() && data.canUseAbility(ID) && DarkMatter.CATEGORY.equals(data.getAbility())
                && data.hasLearned(ID) && session.level == player.level() && data.upkeepHeld(session.upkeep)
                && (session.slot != PassiveSkills.SLOT || data.isPassiveSkill(ID));
    }

    // ---- 対象区分 ----

    /**
     * この領域の使用者から見たエンティティの区分。領域の効果から外れるならnull。優先順は個別除外、個別味方指定、チーム自動判定。サーバーのPvPとチームの保護（優先順の1番）は
     * 害を与える効果の時に{@link #mayHarm}が見る。他人のペット・召喚物は所有者が分かれば所有者の友好区分を引き継ぎ、分からなければ普通のモブの区分。
     * 自分のペットは味方の区分（対象の6区分に専用の区分が無いので実装で決めた）。変異は恒久的な普通の変換なので（{@link HeavenMutation}）、変異した個体も今の姿で判定する。
     */
    public static @Nullable Target classify(ServerPlayer owner, HeavenSettings settings, LivingEntity entity) {
        if (entity == owner) return Target.SELF;
        if (entity instanceof DarkMatterBeetle beetle) {
            if (beetle.isOwnedBy(owner)) return Target.BEETLE;
            var inherited = ownerClass(owner, settings, beetle.ownerId());
            return inherited != null ? inherited.target() : Target.HOSTILE;
        }
        if (entity instanceof Player player) return player.isSpectator() ? null : playerClass(owner, settings, player.getUUID(), player);
        if (!(entity instanceof Mob)) return null; // 防具立てなど。
        UUID tamer = entity instanceof OwnableEntity pet ? pet.getOwnerUUID() : null;
        var inherited = ownerClass(owner, settings, tamer);
        if (inherited != null) return inherited.target();
        if (entity instanceof Enemy) return Target.HOSTILE;
        if (entity instanceof NeutralMob neutral && neutral.isAngryAt(owner)) return Target.HOSTILE;
        // 1.5.2: いま使用者の側（自分・味方・自分のカブトムシ・自分や味方の飼い動物）を狙っているモブは敵対。
        // 怒り方の仕組み（NeutralMob か、ラマ・パンダ・ヤギなどの独自の反撃か、他 Mod のモブか）に依らず、バニラの狙い（getTarget）で決める。
        return targetsOwnerSide(owner, settings, ((Mob) entity).getTarget()) ? Target.HOSTILE : Target.NEUTRAL;
    }
    /** 狙われている相手が使用者の側（{@link #classify}で自分・カブトムシ・味方になる相手）か。モブ同士の狙いは数えない。 */
    static boolean targetsOwnerSide(ServerPlayer owner, HeavenSettings settings, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (target == owner) return true;
        if (target instanceof DarkMatterBeetle beetle) return beetle.isOwnedBy(owner);
        if (target instanceof Player player) return !player.isSpectator() && playerClass(owner, settings, player.getUUID(), player) == Target.ALLY;
        if (target instanceof OwnableEntity pet) {
            var inherited = ownerClass(owner, settings, pet.getOwnerUUID());
            return inherited != null && inherited.target() == Target.ALLY;
        }
        return false;
    }
    private record Inherited(@Nullable Target target) { }
    private static @Nullable Inherited ownerClass(ServerPlayer owner, HeavenSettings settings, @Nullable UUID id) {
        if (id == null) return null;
        if (id.equals(owner.getUUID())) return new Inherited(Target.ALLY);
        MinecraftServer server = owner.getServer();
        var player = server == null ? null : server.getPlayerList().getPlayer(id);
        var mode = settings.playerMode(id);
        // 所有者がオフラインでも、個別の登録があれば判別できる。
        if (player == null && mode == HeavenSettings.PlayerMode.AUTO) return null;
        return new Inherited(playerClass(owner, settings, id, player));
    }
    private static @Nullable Target playerClass(ServerPlayer owner, HeavenSettings settings, UUID id, @Nullable Player player) {
        return switch (settings.playerMode(id)) {
            case EXCLUDE -> null;
            case ALLY -> Target.ALLY;
            case AUTO -> player != null && owner.isAlliedTo(player) ? Target.ALLY : Target.OTHER;
        };
    }
    /** この領域がこのエンティティへ害を与えてよいか: PvP・チームの保護と自分の甲虫の保護は常に先に効く。有害な効果を受けない身体の無効も通す。自分への害は本人の選択なので通す。 */
    public static boolean mayHarm(Session session, LivingEntity entity) {
        if (harmImmune.test(entity)) return false;
        if (entity == session.player) return true;
        if (!SkillCombat.mayAttack(session.player, entity, ID)) return false;
        // 他人のペット・召喚物の所有者への誤射の保護（PvPが切られていれば、その持ち物にも害を与えない）。
        UUID tamer = entity instanceof DarkMatterBeetle beetle ? beetle.ownerId() : entity instanceof OwnableEntity pet ? pet.getOwnerUUID() : null;
        if (tamer == null || tamer.equals(session.player.getUUID())) return tamer == null;
        var server = session.player.getServer();
        var other = server == null ? null : server.getPlayerList().getPlayer(tamer);
        return other == null || SkillCombat.mayAttack(session.player, other, ID);
    }

    // ---- 毎tick ----

    private static void scan(Session session) {
        var found = new ArrayList<>(session.level.getEntitiesOfClass(LivingEntity.class, session.area.box(), e -> e.isAlive() && session.area.contains(e)));
        if (!found.contains(session.player) && session.player.isAlive()) found.add(session.player); // 世界に入っていないテストの使用者でも同じ結果にする。
        session.inside = found;
    }
    /** 全領域を1tick進める。 */
    private static void step(long time, boolean forceScan) {
        boolean anything = !ACTIVE.isEmpty() || HeavenEffects.busy() || HeavenPhenomena.busy() || HeavenDisplay.busy();
        if (!anything) { dirty = false; return; }
        long started = System.nanoTime();
        for (var session : List.copyOf(ACTIVE.values())) {
            if (!continues(session)) { stop(session.player); continue; }
            session.age++;
            session.proficiency = session.data.getProficiency(ID);
            session.area = areaOf(session.player, session.proficiency);
            HeavenVisuals.region(session, false);
            if (++session.usefulClock >= 20) {
                session.usefulClock = 0;
                if (session.useful) session.data.addProficiency(ID, GAIN);
                session.useful = false;
            }
        }
        boolean scanNow = forceScan || dirty || time % SCAN_TICKS == 0;
        dirty = false;
        if (scanNow) {
            for (var session : ACTIVE.values()) scan(session);
            // 1.5.2: 常時の効果は維持費。走査ごとに、効果がかかっているマスの数だけ過負荷を確保し直す（増えたら差を払い、
            // 減ったら確保を下げる）。稼働にはしないので、過負荷の回復は止まらない。
            for (var session : ACTIVE.values())
                session.data.setReservedOverload(session.upkeep, HeavenEffects.cells(session) * cellReserve(session.proficiency),
                        session.player.getAbilities().instabuild);
            HeavenEffects.resolve();
            // 範囲の中のプレイヤーへの表示「天界」（同じ走査の結果を使う）。
            HeavenDisplay.update();
        }
        HeavenEffects.tick(time);
        // 現象が使用者自身を倒して領域が解除されることがあるので、写しを回す。
        for (var session : List.copyOf(ACTIVE.values())) if (session.alive()) HeavenPhenomena.tick(session);
        HeavenPhenomena.tickStatuses();
        record(System.nanoTime() - started);
    }
    /** テスト用の入口: 今すぐ走査して1tick進める（効果の反映まで同じtickの中で行う）。 */
    public static void tickForTest(long time) { step(time, true); }

    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        step(e.getServer().overworld().getGameTime(), false);
    }
    /** 真の死亡で解除する。人間化・トーテム・他MODの救済による致死の取消では呼ばれない（{@link DeathOrder}が最後に判断して呼ぶ）。 */
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) stop(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /** プレイヤーのエンティティが入れ替わった（死亡・エンドからの帰還）: 新しいトグルは動いていないので解除し、右側の表示も消す。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        var session = ACTIVE.remove(player.getUUID());
        if (session != null) { session.data.endUpkeep(session.upkeep); dirty = true; HeavenVisuals.regionOff(session); }
        SkillModeState.send(player, ID, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); dirty = false; HeavenEffects.clear(); HeavenPhenomena.clear(); }

    // ---- 範囲の実測の入口 ----

    /** 直近の処理時間の標本（ナノ秒。60秒分）。 */
    private static final long[] SAMPLES = new long[1200];
    private static int sampleAt, sampleCount;
    private static void record(long nanos) {
        SAMPLES[sampleAt] = nanos;
        sampleAt = (sampleAt + 1) % SAMPLES.length;
        sampleCount = Math.min(SAMPLES.length, sampleCount + 1);
    }
    /** 直近の標本の95パーセンタイル（ミリ秒/tick）。 */
    public static double measuredMillisP95() {
        if (sampleCount == 0) return 0;
        long[] copy = Arrays.copyOf(SAMPLES, sampleCount);
        Arrays.sort(copy);
        return copy[Math.min(copy.length - 1, (int) Math.ceil(copy.length * .95) - 1)] / 1e6;
    }
    /** 範囲の比較の入口: 範囲の広さの段を固定する（チャンク数で数えた 1・5・9・25。5 は菱形。0で熟練度どおり）。製品の値は変えない（再起動で戻る）。 */
    public static boolean setAreaOverride(int chunks) {
        if (chunks != 0 && chunks != 1 && chunks != 5 && chunks != 9 && chunks != 25) return false;
        areaOverride = chunks;
        for (var session : ACTIVE.values()) session.area = areaOf(session.player, session.proficiency);
        dirty = true;
        return true;
    }
    public static int areaOverride() { return areaOverride; }
    /** {@code /heaven_measure [チャンク数]}: 今の領域の数、対象の数、処理時間のp95を出す。数を渡すと範囲を固定する。 */
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("heaven_measure").requires(source -> source.hasPermission(2))
                .executes(context -> { context.getSource().sendSuccess(HeavenManifest::measureText, false); return 1; })
                .then(Commands.argument("chunks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 25)).executes(context -> {
                    boolean ok = setAreaOverride(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "chunks"));
                    sampleCount = 0;
                    context.getSource().sendSuccess(HeavenManifest::measureText, false);
                    return ok ? 1 : 0;
                })));
    }
    private static Component measureText() {
        int targets = 0;
        for (var session : ACTIVE.values()) targets += session.inside.size();
        return Component.literal("heaven: sessions=" + ACTIVE.size() + " targets=" + targets + " areaOverride=" + areaOverride
                + " p95=" + String.format(java.util.Locale.ROOT, "%.3f", measuredMillisP95()) + "ms/tick samples=" + sampleCount);
    }
}
