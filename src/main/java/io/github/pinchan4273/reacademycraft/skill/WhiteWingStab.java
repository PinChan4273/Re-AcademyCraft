package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質Lv1 白翼刺突（採用仕様・版41の§4、R08）。押し続けで連撃する。
 * <p>
 * 最初の1撃はキーを押したときに即時。以後、押し続けている間は技能全体の間隔lerp(12, 8, p)tick（0.6〜0.5〜0.4秒）ごとに1撃。
 * 左右の翼は交互に伸びるが、翼ごとの連射の数えは持たない。各打撃は、その打撃を始めたときの照準で方向を決め、発射後は追尾しない。
 * <p>
 * 1撃ごとの値（pは熟練度）: 射程lerp(8, 16)ブロック（射程内の減衰なし）、ダメージ6＋14p、CP lerp(80, 40)、過負荷lerp(12, 8)。
 * 費用は空振りでも払い、払えなければ連撃を終える（その打撃は出さない）。支払いが成立した打撃ごとに、習得コース別のCP回復待ちを
 * 既存の{@link PlayerAbilityData#consume}が更新する。有効な命中（損傷が実際に入った）だけ熟練度＋0.1%（と能力レベル経験値）。
 * <p>
 * 熟練度50%未満: 照準上で最初の攻撃可能な対象1体へ。ブロックで止まる（止まった所に突き刺す演出だけ）。損傷の種類は
 * {@link SkillDamageSource#STAB}（被ダメージ後の待ちを飛ばす。防御は通常どおり）。
 * <p>
 * 熟練度50%以上: 射程内・照準上に攻撃可能な対象がいれば、途中のブロックで止まらずに届く（ブロックは壊さない）。損傷の種類は
 * {@link SkillDamageSource#PIERCE}で、盾・防具・防護エンチャント・耐性（バニラと直接効果）・吸収・ライト・シールド・ベクトル偏向・
 * ベクトル反射・ジェットの前面防御を通る。攻撃できるかの判定（PvP・チーム・設定）は先に{@link SkillCombat#mayAttack}で行い、
 * 防御無視は他の技能へ引き継がない。創造モードや独自の無敵（{@code isInvulnerableTo}）は通らない（R08: 特殊な無敵は実測で決める）。
 * <p>
 * キーを離す、能力OFF、過負荷、干渉、画面、プリセットの変更、スロットの変更、未習得、系統の変更、死亡、ログアウト、次元の移動で
 * 新しい打撃を止める（打撃は即時なので、開始済みの1撃はその場で完了している）。
 * <p>
 * 連打（2026-11-03のユーザーの指摘）: 次に刺せる時刻と直前の翼は、キーを離しても残す（{@link #LAST}）。間隔の途中で押されたら1撃を予約し、
 * 間隔が明けたときに出す（その前に離しても出す）。連打でも押し続けより速くならず、翼も直前の反対側の3枚から選び続ける。
 * <p>
 * §13: 放出中の空気砲の間に押されたら、使えることを確かめて完了の後へ回す（{@link DarkMatterAirCannon#queueNext}）。光線は、最初の1撃の費用が払えたときだけ止める。
 * 未元物質人間の1HP＝1CP（§15）は{@link DarkBody}が行う。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class WhiteWingStab {
    public static final ResourceLocation ID = DarkMatter.STAB;
    /** 押し続けの心拍が途切れたとみなすtick（DirectedShockと同じ）。 */
    public static final int HEARTBEAT_TICKS = 10;
    /** 選択的な透過が始まる熟練度。 */
    public static final float PIERCE_FROM = .5f;
    /** 有効な命中1回の熟練度（＋0.1%）。 */
    public static final float GAIN = .001f;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; long heartbeat; int wait; int wing = -1; int strikes;
        /** 予約の1撃を待つ間にキーが離された（その1撃を出して終える）。 */
        boolean released;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
        }
    }
    /** 1撃の結果（試験と演出のため）。targetは損傷が入った対象（無ければnull）、endは翼の先端が届いた点。 */
    public record Strike(boolean paid, @Nullable Entity target, Vec3 end, boolean pierced) { }

    /** 次に刺せる時刻（ゲーム時刻）と直前の翼。キーを離しても残す。 */
    private record Last(long readyAt, int wing) { }
    private static final Map<UUID, Last> LAST = new HashMap<>();
    /** 刺せるようになってから、この間（tick）に押せば連撃の続きとみなし、翼を直前の反対側から選ぶ。過ぎたら6枚から選び直す。 */
    public static final int CHAIN_TICKS = 20;
    private WhiteWingStab() { }
    private static PlayerAbilityData data(ServerPlayer p) { return io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(p).orElse(null); }

    public static double range(float p) { return ArcGen.lerp(8, 16, p); }
    public static float damage(float p) { return 6 + 14 * p; }
    public static float consumption(float p) { return ArcGen.lerp(80, 40, p); }
    public static float overload(float p) { return ArcGen.lerp(12, 8, p); }
    /** 打撃の間隔（tick）。0%で12（0.6秒）、50%で10（0.5秒）、100%で8（0.4秒）。 */
    public static int interval(float p) { return Math.round(ArcGen.lerp(12, 8, p)); }
    public static boolean pierces(float p) { return p >= PIERCE_FROM; }
    /** 六翼の数。番号は段×2＋（右なら1）で、0が左上、5が右下。 */
    public static final int WINGS = 6;
    /**
     * 次に刺す翼: 直前に刺した翼の反対側の3枚からランダム（2026-11-03のユーザーの指示。右中段で刺したら左の3枚から）。
     * 反対側から選ぶので、同じ翼が続くことはない。最初は6枚から。見た目だけで、命中や費用には関係しない。
     */
    public static int nextWing(int last, net.minecraft.util.RandomSource random) {
        if (last < 0 || last >= WINGS) return random.nextInt(WINGS);
        int otherSide = 1 - last % 2;
        return random.nextInt(WINGS / 2) * 2 + otherSide;
    }

    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.canUseAbility()
                && DarkMatter.CATEGORY.equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }

    /** キーを押した: 最初の1撃を即時に出し、押し続けの連撃を始める。 */
    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !DarkMatter.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.isInterfering()) return "academy.cast.interfered";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        var existing = ACTIVE.get(player.getUUID());
        if (existing != null) {
            // 予約の1撃を待つ間に押し直した: 押し続けに戻る。
            if (existing.released && existing.slot == slot) { existing.released = false; existing.heartbeat = player.level().getGameTime(); }
            return "";
        }
        // §13: 光線・刺突・空気砲は排他。放出中の空気砲は完了させ、その後でこの連撃へ移る。
        if (DarkMatterAirCannon.active(player)) return DarkMatterAirCannon.queueNext(player, data, ID, slot, consumption(data.getProficiency(ID)));
        var session = new Session(player, data, slot);
        long now = player.level().getGameTime();
        var last = LAST.get(player.getUUID());
        if (last != null && now <= last.readyAt() + CHAIN_TICKS) session.wing = last.wing();
        if (last != null && now < last.readyAt()) {
            // 間隔の途中の押下: 払えることだけ今確かめ、1撃を予約する（払うのは出すとき）。
            float cost = consumption(data.getProficiency(ID));
            boolean creative = player.getAbilities().instabuild;
            if ((!creative && data.getCp() < cost) || !data.safetyPermits(ID, cost, creative)) return "academy.cast.cp";
            session.wait = (int) (last.readyAt() - now);
            ACTIVE.put(player.getUUID(), session);
            return "";
        }
        ACTIVE.put(player.getUUID(), session);
        if (!strike(player, data, session).paid()) { ACTIVE.remove(player.getUUID()); return "academy.cast.cp"; }
        return "";
    }
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }
    /** キーを離した: 新しい打撃を止める。 */
    public static void release(ServerPlayer player, int slot) {
        DarkMatterAirCannon.dropQueued(player, ID, slot);
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        // まだ1撃も出していない押下（予約）は、離しても予約の1撃を出してから終える。
        if (session.strikes == 0) session.released = true;
        else ACTIVE.remove(player.getUUID());
    }
    public static void cancel(ServerPlayer player, int slot) { release(player, slot); }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && !s.released; }
    /** 試験用: 直前に刺した翼（無ければ-1）。 */
    public static int lastWing(ServerPlayer player) { var last = LAST.get(player.getUUID()); return last == null ? -1 : last.wing(); }
    /** 試験用: この連撃で出した打撃の数（無ければ0）。 */
    public static int strikes(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? 0 : s.strikes; }

    /** 1撃。費用を払えなければ何もしない（paid=false）。 */
    private static Strike strike(ServerPlayer player, PlayerAbilityData data, Session session) {
        float p = data.getProficiency(ID);
        if (!data.consume(ID, consumption(p), overload(p), player.getAbilities().instabuild)) return new Strike(false, null, player.getEyePosition(), false);
        session.wait = interval(p);
        session.wing = nextWing(session.wing, player.getRandom());
        session.strikes++;
        LAST.put(player.getUUID(), new Last(player.level().getGameTime() + session.wait, session.wing));
        // §13: 使えないなら現在の攻撃を続けるので、光線を止めるのは最初の1撃の費用を払えた後。
        if (session.strikes == 1) DarkMatterRay.stop(player);
        var result = hit(player, p);
        if (result.target() != null) data.addProficiency(ID, GAIN);
        // 翼の描画へ伝える（どちらの翼か、届いた距離）。
        io.github.pinchan4273.reacademycraft.network.DarkWingAction.send(player, io.github.pinchan4273.reacademycraft.network.DarkWingAction.STAB,
                session.wing, (float) player.getEyePosition().distanceTo(result.end()));
        player.level().playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .6f, 1.4f);
        AbilitySyncEvents.sync(player, true);
        return result;
    }

    /**
     * 照準で1撃を当てる（費用は払った後）。50%以上なら途中のブロックを無視して、射程内で最初の攻撃可能な対象へ。50%未満ならブロックで止まる。
     * 試験からも呼ぶ（費用・熟練度は扱わない）。
     */
    public static Strike hit(ServerPlayer player, float p) {
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(range(p)));
        boolean pierce = pierces(p);
        Vec3 reach = end;
        if (!pierce) {
            var block = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) reach = block.getLocation();
        }
        var target = firstTarget(player, start, reach);
        if (target == null) return new Strike(true, null, reach, false);
        boolean hurt = SkillCombat.attackStab(player, target, ID, damage(p), pierce);
        var at = target.getBoundingBox().inflate(.3).clip(start, reach).orElse(target.position());
        return new Strike(true, hurt ? target : null, at, pierce);
    }
    /** 光線上で術者に最も近い、攻撃可能な生き物（術者は除く）。 */
    private static @Nullable Entity firstTarget(ServerPlayer player, Vec3 start, Vec3 end) {
        Entity best = null; double nearest = Double.MAX_VALUE;
        for (var candidate : player.level().getEntities(player, new AABB(start, end).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e)
                        && SkillCombat.mayAttack(player, e, ID))) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) { nearest = start.distanceToSqr(intercept.get()); best = candidate; }
        }
        return best;
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || (!session.released && now - session.heartbeat > HEARTBEAT_TICKS)) { stop(player); return; }
        if (--session.wait > 0) return;
        if (!strike(player, data, session).paid() || session.released) stop(player);
    }
    /** テスト用の入口: サーバーのtickループを待たずに押し続けのセッションを1tick進める（心拍も送ったことにする）。 */
    public static void tickForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.heartbeat = player.level().getGameTime();
        tick(player);
    }

    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) { stop(p); LAST.remove(p.getUUID()); } }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); LAST.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); LAST.clear(); }
}
