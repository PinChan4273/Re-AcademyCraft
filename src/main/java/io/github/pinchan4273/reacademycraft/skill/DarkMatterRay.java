package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質Lv2 未元光線。トグルの照射で、{@link #CHARGE_TICKS}tickのチャージの後に撃ち始める。照準に追従し、最初の有効な対象1体に作用してブロックで止まる。
 * 10tickごとに基礎lerp(4,8)×明るさの倍率(0.5＋L/15)。Lは目の位置の、昼夜・天候を反映した天空光とブロック光の高い方。
 * 開始20CP＋過負荷10。維持は部類の外の切り替え型の共通の形（1.5.2: 回復の lerp(100%, 25%) ＋ 毎tick lerp(4, 0)CP。
 * 1.5.1の毎秒 lerp(80, 40)CPから）。開始・維持ともCPの回復待ちを作らない。空振りでも維持費（CP）を払う。
 * 1.5.2: 維持の過負荷は払わず、相手に当たるごとに{@link #hitOverload}を「稼働」の払いとして課す
 * （当て続けると熟練度100%・最大過負荷1,000で約90秒でロック。当てている間は過負荷が回復しない）。
 * 通常の防御・軽減は効く。ベクトル偏向・反射は、1.5.1までは反応しなかったが、1.5.2から未元物質で変化した物の攻撃として
 * 解析度に応じて軽減する（解析度100%なら反射で無効。{@link VectorAnalysis}）。
 * 1.5.2: 能力設定でパッシブにすると、能力使用モードの間に{@link PassiveSkills}が自動で始める（スロットのインデックスは{@link PassiveSkills#SLOT}）。
 * 開始の費用・チャージ・維持費はキーで始めるときと同じ。空気砲・白翼刺突が動いている間は始めず（技能どうしの排他）、それらが終わったら始める。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class DarkMatterRay {
    public static final Identifier ID = DarkMatter.RAY;
    public static final float START_CP = 20, START_OVERLOAD = 10, GAIN = .0005f;
    public static final int HIT_INTERVAL = 10;
    /** 発射までのチャージ（tick）。費用はチャージの間も払う。 */
    public static final int CHARGE_TICKS = 20;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset; int slot; int age; PlayerAbilityData.Upkeep upkeep;
        /** チャージ音と照射音の{@link io.github.pinchan4273.reacademycraft.network.SkillSounds}のID（無ければ0）。 */
        long chargeSound, fireSound;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) { level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; }
    }
    private DarkMatterRay() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static double range(float p) { return ArcGen.lerp(24, 40, p); }
    public static float baseHit(float p) { return ArcGen.lerp(4, 8, p); }
    /** 回復の割合に足す維持費（毎秒。熟練度0%で80、100%で0。設定toggle_upkeep.dark_matter_rayの毎tick 4 × (1 − 熟練度)）。 */
    public static float cpPerSecond(float p) { return io.github.pinchan4273.reacademycraft.config.AcademyConfig.toggleUpkeep(ID) * (1 - Math.max(0, Math.min(1, p))) * 20; }
    /** 1.5.2: 相手に当たった1回の過負荷（稼働の払い）。 */
    public static float hitOverload(float p) { return ArcGen.lerp(11, 5.5f, p); }
    /** 明るさL（0〜15）の倍率。 */
    public static float lightMultiplier(int light) { return .5f + light / 15f; }
    /** 目の位置の明るさ。暗視・ガンマ・演出の光は数えない（サーバーの光だけを見る）。 */
    public static int light(ServerPlayer player) {
        var pos = BlockPos.containing(player.getEyePosition());
        var level = player.level();
        return Math.max(level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken(), level.getBrightness(LightLayer.BLOCK, pos));
    }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    /** チャージが終わって撃っているか。 */
    public static boolean firing(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.age >= CHARGE_TICKS; }
    public static int ageOn(ServerPlayer player, int slot) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.age; }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }
    /** 1.5.2: パッシブにした時に、スロットのキーで始めた照射が動いていたら、止めずにパッシブの照射にする（開始の費用を二度払わない）。 */
    public static void adoptPassive(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.slot = PassiveSkills.SLOT;
    }

    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        // 1.5.2: slotが{@link PassiveSkills#SLOT}なら、パッシブの自動の起動（パッシブに切り替えていること）。
        if (!PassiveSkills.slotMatches(data, ID, slot) || !player.isAlive() || data.isReadOnly()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        // 光線・刺突・空気砲は排他。放出中の空気砲は完了させる。パッシブの自動の起動は、刺突の間も始めない（刺突を止めない）。
        if (slot == PassiveSkills.SLOT && WhiteWingStab.active(player)) return "academy.cast.cooldown";
        if (DarkMatterAirCannon.active(player)) return "academy.cast.cooldown";
        if (!data.consumeQuiet(ID, START_CP, START_OVERLOAD, player.getAbilities().instabuild)) return "academy.cast.cp";
        WhiteWingStab.stop(player);
        var session = new Session(player, data, slot);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        if (session.upkeep == null) return "academy.cast.unlearned";
        session.upkeep.recoveryShare();
        ACTIVE.put(player.getUUID(), session);
        DarkWingAction.send(player, DarkWingAction.RAY_ON, 0, (float) range(data.getProficiency(ID)));
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        // チャージ音（3.7秒の素材）は、チャージの間だけ鳴らす。撃ち始め・途中の停止で止め、照射音と重ねない。
        session.chargeSound = io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MD_CHARGE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, false, () -> ACTIVE.get(player.getUUID()) == session && session.age < CHARGE_TICKS);
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        DarkWingAction.send(player, DarkWingAction.RAY_OFF, 0, 0);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
    }
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        // 1.5.2: パッシブで動いているものは、プリセットとスロットを見ず、パッシブのままかを見る。
        return player.isAlive() && data != null && !data.isReadOnly() && data.canUseAbility(ID) && DarkMatter.CATEGORY.equals(data.getAbility())
                && data.hasLearned(ID) && session.level == player.level()
                && PassiveSkills.sessionMatches(data, ID, session.preset, session.slot) && data.upkeepHeld(session.upkeep);
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        float p = data.getProficiency(ID);
        session.age++;
        // 撃ち始め: 撃っている間だけ、原子崩しの採掘レーザーの音が術者に付いて行く。
        if (session.age == CHARGE_TICKS) session.fireSound = io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MINE_LOOP.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, true, () -> ACTIVE.get(player.getUUID()) == session);
        // 1.5.2（ロックからの戻し方）: ロック中に再稼働した光線は維持だけで、当てない（稼働しない）。
        if (session.age >= CHARGE_TICKS && (session.age - CHARGE_TICKS) % HIT_INTERVAL == 0 && !data.isOverloadLocked()) {
            if (hit(player, p) != null) {
                data.addProficiency(ID, GAIN);
                // 1.5.2: 当たったときだけ過負荷を払う（稼働）。次に当てるまで（10tick後）稼働中にする。
                data.chargeActionOverload(ID, hitOverload(p), player.getAbilities().instabuild, HIT_INTERVAL + 1);
                if (ACTIVE.get(player.getUUID()) != session) return;
            }
        }
        AbilitySyncEvents.sync(player, false);
    }
    /** 照準上の最初の対象へ1回当てる。損傷が入った対象を返す（テストからも呼ぶ）。 */
    public static @Nullable Entity hit(ServerPlayer player, float p) {
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(range(p)));
        var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        Entity best = null; double nearest = Double.MAX_VALUE;
        for (var candidate : player.level().getEntities(player, new AABB(start, end).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e) && SkillCombat.mayAttack(player, e, ID))) {
            var at = candidate.getBoundingBox().inflate(.3).clip(start, end);
            if (at.isPresent() && start.distanceToSqr(at.get()) < nearest) { nearest = start.distanceToSqr(at.get()); best = candidate; }
        }
        if (best == null) return null;
        return SkillCombat.attack(player, best, ID, baseHit(p) * lightMultiplier(light(player))) ? best : null;
    }
    /** テスト用の入口。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }
    /** テスト用の入口: 今の照射のチャージ音と照射音のID（照射していなければnull）。 */
    public static @Nullable long[] soundsForTest(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? null : new long[] {s.chargeSound, s.fireSound}; }

    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    /** 系統を失ったら、次のtickを待たずに止める。 */
    @SubscribeEvent public static void category(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CategoryChange e) {
        if (e.player() instanceof ServerPlayer p && active(p)) stop(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (ACTIVE.remove(e.getEntity().getUUID()) != null && e.getEntity() instanceof ServerPlayer p) io.github.pinchan4273.reacademycraft.network.SkillModeState.send(p, ID, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
