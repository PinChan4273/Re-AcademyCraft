package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.StormWingState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作StormWing: ベクトル操作のレベル3の飛行。
 *
 * キーでモードをオン・オフする。続く間、術者は飛んでよく（原作はサーバーでallowFlyingを設定した）、落下ダメージを受けない。
 * lerp(70, 30, exp)tick溜め、その後有効になる: 移動キーが術者を動かし、それは原作と同じく術者自身のクライアントの仕事
 * （ClientStormWing）。有効な間は毎tick 0.00005の経験値を与える。
 *
 * <p>資源は1.0.0の後に承認した仕様（docs/CP_RECOVERY_TOGGLE_SPEC_JA.md 8章）で、滞空（待機）と推進（実行）を分ける:
 * <ul>
 * <li>推進しない滞空: CPは正味で40 × (1 − 熟練度)/秒ずつ減る。熟練度100%で単独なら、回復待ちの後に通常の回復の半分が正味で残る
 * （無期限に滞空できる）。オーバーロードは待ちの後、通常の回復の代わりに2〜4/秒で下がる。</li>
 * <li>推進: 120〜40 CP/秒と3〜1 オーバーロード/秒（滞空の分を含む全体）。払うと回復待ちが始まり、離すと待ちの後で回復へ移る。
 * 払えない（CP不足・オーバーロードのロック）ときは推進を止め、クライアントに滞空だけを許す（stalled）。</li>
 * </ul>
 * 推進しているかはクライアントの入力（{@link io.github.pinchan4273.reacademycraft.network.StormWingInput}）で、落下・ノックバック・他の能力による
 * 位置の変化からは推測しない。入力はモードの番号（epoch）が今のモードと一致するときだけ受け取る。熟練度15%未満では、風が
 * 術者の周りの柔らかいブロック（硬さ0.3以下）も壊す: 10ブロック以内で1tickに40回のランダムな試行。熟練度がちょうど最大のとき、
 * 有効になると6ブロック以内のすべてのエンティティを吹き飛ばす（術者も真上へ）。モードの終了は理由を問わず、術者自身の飛行の許可を
 * 戻し、クールダウンlerp(30, 10, exp)を設定する。
 *
 * 原作はクライアントが溜めの完了を決めてサーバーへ伝えた。ここではサーバーが数えてクライアントへ伝える。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class StormWing {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "storm_wing");
    public static final int CHARGE = 0, ACTIVE_STATE = 1;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static int epochs;

    private static final class Session {
        final Level level; final int preset, slot, epoch = ++epochs; final float exp; final boolean mayfly; int state = CHARGE, ticks;
        /** クライアントが最後に伝えた推進の入力と、それを払えずに滞空だけを許しているか。 */
        boolean thrustInput, stalled;
        /** 溜めの終わりからの、能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; exp = data.getProficiency(ID);
            mayfly = player.getAbilities().mayfly;
        }
    }

    private StormWing() { }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    /** 飛び続けてよいか。妨害（能力妨害器）が始まったtickにも終える（指示の停止条件。1.0.0は妨害で終わらなかった）。 */
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive() && !data.isInterfering()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    public static float chargeTicks(float exp) { return ArcGen.lerp(70, 30, exp); }
    /** 原作の速さ: 熟練度45%未満では遅い。 */
    public static double speed(float exp) { return (exp < .45f ? .7f : 1.2f) * ArcGen.lerp(2, 3, exp); }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(30, 10, exp); }
    /** 術者のクライアント向けの原作worldSpace: 頭のyawから、1.12の回転でrotatePitch(-pitch)、次にrotateYaw(-yaw)。 */
    public static net.minecraft.world.phys.Vec3 worldSpace(net.minecraft.world.phys.Vec3 local, float yawHeadDegrees, float pitchDegrees) {
        float pitch = -pitchDegrees * net.minecraft.util.Mth.DEG_TO_RAD, yaw = -yawHeadDegrees * net.minecraft.util.Mth.DEG_TO_RAD;
        float pc = net.minecraft.util.Mth.cos(pitch), ps = net.minecraft.util.Mth.sin(pitch);
        var v = new net.minecraft.world.phys.Vec3(local.x, local.y * pc + local.z * ps, local.z * pc - local.y * ps);
        float yc = net.minecraft.util.Mth.cos(yaw), ys = net.minecraft.util.Mth.sin(yaw);
        return new net.minecraft.world.phys.Vec3(v.x * yc + v.z * ys, v.y, v.z * yc - v.x * ys);
    }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int state(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.state; }

    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.VECMANIP.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // コンテキストを持つ全員への原作c_makealive: StormWingEffectの竜巻と塵。溜めの間にフェードインし（原作のchargeTime）、
        // このセッションが続く間表示する。
        io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, ID, (int) chargeTicks(session.exp), () -> ACTIVE.get(player.getUUID()) == session);
        // 原作s_makeAlive: モードが続く間allowFlying。
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        send(player, session, CHARGE);
        // 原作c_makealive: 風のループ音がモードの間ずっと術者に付いて行く。
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_STORM_WING.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, true, () -> active(player));
        return "";
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        player.getAbilities().mayfly = session.mayfly;
        if (!session.mayfly) player.getAbilities().flying = false;
        player.onUpdateAbilities();
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        // 習得していない技能（止める前にリスナーやカテゴリの変更が外した）にはクールダウンを書かない（M-110-02）。
        if (data != null && !data.isReadOnly()) { if (data.hasLearned(ID)) data.setCooldown(ID, cooldown(session.exp)); AbilitySyncEvents.sync(player, true); }
        send(player, session, -1);
    }
    private static void send(ServerPlayer player, Session session, int state) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new StormWingState(state, session.epoch, session.stalled));
    }
    /**
     * 術者のクライアントからの推進の入力。今のモードの番号で、有効（溜め終わり）の間だけ受け取る。古いモードの遅れた入力、
     * 溜めの間の入力、モードが無いときの入力は捨てる。
     */
    public static void input(ServerPlayer player, int epoch, boolean thrust) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.epoch != epoch || session.state != ACTIVE_STATE) return;
        session.thrustInput = thrust;
    }
    /** テスト用: 今のモードの番号（無ければ-1）。 */
    public static int epoch(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? -1 : s.epoch; }
    /** 推進を求めているのに払えず、滞空だけを許しているか。 */
    public static boolean stalled(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.stalled; }

    /** このtickもモードを続けてよいか（tickの始めと、熟練度のイベントの後に確かめる）。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        return data != null && allowed(player, data) && !(session.level != player.level() || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot)));
    }
    private static void tick(ServerPlayer player, DoubleSupplier roll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        player.fallDistance = 0;
        if (session.exp < .15f) breakAround(player, roll);
        if (session.state == ACTIVE_STATE) {
            // 滞空のCPは、このtickの始めに精算した維持コスト。払えなかった要求は外れている。
            if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
            // 滞空は待機、推進を払ったtickは行動として、維持の熟練度を得る（1.1.0、T178-10）。1.0.0の毎tick 0.00005は廃止した。
            data.upkeepEarn(session.upkeep);
            // 熟練度のイベントのリスナーが、モードを終えた・置き換えた・続けられなくした（能力、スロット、プリセット）かもしれない:
            // このtickを古いセッションで続けない（M-110-01）。
            if (ACTIVE.get(player.getUUID()) != session) return;
            if (!continues(player, data, session) || !data.upkeepHeld(session.upkeep)) { stop(player); return; }
            boolean stalled = false;
            if (session.thrustInput) {
                // 推進: 滞空の分を含む全体の費用。払えば回復待ちが始まり、次の精算は待機として扱わない。
                float proficiency = data.getProficiency(ID);
                boolean paid = data.consume(ID, AcademyConfig.stormWingThrustCp(proficiency), AcademyConfig.stormWingThrustOverload(proficiency),
                        player.getAbilities().instabuild);
                // オーバーロードはconsumeが戻る前に全セッションを止めることがある。
                if (ACTIVE.get(player.getUUID()) != session) return;
                if (paid) data.upkeepActed(session.upkeep); else stalled = true;
            }
            if (stalled != session.stalled) { session.stalled = stalled; send(player, session, ACTIVE_STATE); }
            AbilitySyncEvents.sync(player, false);
        }
        if (session.state == CHARGE && ++session.ticks > chargeTicks(session.exp)) {
            session.state = ACTIVE_STATE;
            session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
            if (session.upkeep != null) session.upkeep.flight(p -> AcademyConfig.stormWingIdleCp((float) p), p -> AcademyConfig.stormWingIdleOverloadRecovery((float) p));
            if (session.exp == 1) fling(player, roll);
            send(player, session, ACTIVE_STATE);
        }
    }
    /** テスト用の入口: 与えた乱数源から原作の乱数を引いて、モードのサーバー1tick分を進める。 */
    public static void tickForTest(ServerPlayer player, DoubleSupplier roll) { tick(player, roll); }

    /** 熟練度15%未満での原作s_tick: 10ブロック以内で40回のランダムな試行（(int)で切り捨て）を、硬さ0.3以下のブロックに対して行う。 */
    private static void breakAround(ServerPlayer player, DoubleSupplier roll) {
        var level = (ServerLevel) player.level();
        if (!AcademyConfig.canDestroy(level, ID)) return;
        for (int i = 0; i < 40; i++) {
            // 原作.toInt: xとzには原作の(int)キャストを保つ（1.12でも既に負になりえた）。yは切り捨てで、原作が見たすべてのy（0未満は無い）で
            // 同じ結果になり、1.20のy < 0でも術者の下のブロックを保つ。
            var pos = new BlockPos((int) (player.getX() + (roll.getAsDouble() * 20 - 10)),
                    net.minecraft.util.Mth.floor(player.getY() + (roll.getAsDouble() * 20 - 10)), (int) (player.getZ() + (roll.getAsDouble() * 20 - 10)));
            var state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || hardness < 0 || hardness > .3f || !AcademyConfig.canDestroy(player, pos, ID)) continue;
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) continue;
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
    /** 熟練度最大での原作syncState: 術者も含めて6ブロック以内のすべてを、術者の足からそれ自身の頭へ向けてrangef(0.5, 1.0)で飛ばす。 */
    private static void fling(ServerPlayer player, DoubleSupplier roll) {
        var origin = player.position();
        var targets = new java.util.ArrayList<Entity>(player.level().getEntities(player, new AABB(origin, origin).inflate(6),
                e -> e.position().distanceToSqr(origin) <= 36));
        targets.add(player);
        for (var entity : targets) {
            double modifier = .9 + roll.getAsDouble() * .3;
            var delta = entity.getEyePosition().subtract(origin).scale(modifier);
            entity.setDeltaMovement(delta.normalize().scale(.5 + roll.getAsDouble() * .5));
            entity.hurtMarked = true;
        }
    }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            var random = p.getRandom();
            tick(p, random::nextDouble);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
