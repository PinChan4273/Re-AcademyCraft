package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.MdBallEffect;
import io.github.pinchan4273.reacademycraft.network.MdRayEffect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作ElectronMissile（WeAthFolD/KSkun）: meltdownerのレベル5。
 *
 * 術者の横に最大5つのオーブが集まり、8tickごとにランダムに選んだ1つが、広がっていく範囲内で最も近い生き物へ発射する。
 * ElectronBombやScatterBombと同じオーブ・光線の表示を、一度にではなく少しずつ消費する。
 *
 * 原作はキーを押し続け、lerp(80, 200, exp)tick後に終わる。移植版は原作とは異なる仕様として、1回押すとオン、次に押すとオフになり、
 * 時間制限は無い: 毎tickのCPを払える間続き、能力のオフ、プリセットやカテゴリの変更、技能がスロットから外れる、妨害、死亡、
 * ログアウト、ディメンションの移動でも終わる。オーブにはもう決まった寿命が無い: それぞれORB_LIFE tickで送り、モードが続く間は
 * RENEW_INTERVALごとに更新し（近づいてきた観測者は更新でオーブを見る）、終了で退かせる。オンの間は切り替え型技能の維持コストを
 * 払う。これはlerp(12, 5)をconsumeで払うのではなく、能力データが回復を精算する所で払い、熟練度100%で回復と釣り合う。
 * 各攻撃は従来どおり自身のコストを払う。
 *
 * 原作自身のクールダウンの式はMathUtils.clampi(700, 400, exp.toInt)。clampiは(min, max, value)を取るので、ほぼ常に0になる値
 * （expは0〜1の熟練度をintへ切り捨てたもの）を、最小が最大より大きい[700, 400]の範囲へ収めることになる: Math.max(700,
 * Math.min(400, 0))は常に700。したがってクールダウンは熟練度によらず一定の700で、他のmeltdownerのクールダウンと同じく
 * lerpfで練習に応じて下がる意図だったのはほぼ確実。書かれたとおりに移植し、導出が見えるよう定数ではなく同じ方法で計算する。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ElectronMissile {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "electron_missile");
    /** 原作MAX_HOLD、生成の間隔、攻撃の間隔。 */
    private static final int MAX_ORBS = 5, SPAWN_INTERVAL = 10, ATTACK_INTERVAL = 8;
    /** 原作overload_keep: ここの毎tickや攻撃ごとのコストと違い、lerpではなく固定。 */
    private static final float OVERLOAD_KEEP = 200;
    private static final double PRESENTATION_RANGE = 20;
    /** 送るときのオーブの寿命、動作中のモードが更新する間隔、退かせるときの寿命。 */
    public static final int ORB_LIFE = 40, RENEW_INTERVAL = 20, RETIRE_LIFE = 2;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private record Orb(long session, double subX, double subY, double subZ) { }

    private static final class Session {
        final Level level; final int preset, slot; final float exp, overloadFloor;
        final List<Orb> orbs = new ArrayList<>();
        int ticks;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); overloadFloor = data.getOverload();
        }
        float attackOverload() { return ArcGen.lerp(9, 4, exp); }
        float attackConsumption() { return ArcGen.lerp(60, 25, exp); }
        float damage() { return ArcGen.lerp(10, 18, exp); }
        double range() { return ArcGen.lerp(5, 13, exp); }
        /** 原作自身の壊れたclampi(700, 400, exp.toInt): クラスの説明を参照。 */
        int cooldown() { return Math.max(700, Math.min(400, (int) exp)); }
    }

    private ElectronMissile() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        if (!data.consume(ID, 0, OVERLOAD_KEEP, player.getAbilities().instabuild)) return "academy.cast.cp";
        // 支払いで術者がオーバーロードすることがあり、そのイベントは、このモードがまだ存在しないうちにすべてのモードを終える:
        // その場合は何も始めない。オーバーロードさせた分は、他のオーバーロードを起こすコストと同じく払ったまま。
        if (!startable(player, data, slot)) { io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents.sync(player, true); return "academy.cast.overload"; }
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        return "";
    }

    /** このスロットから今モードを始められるか: コストを払った後に、キーが受ける確認。 */
    private static boolean startable(ServerPlayer player, PlayerAbilityData data, int slot) {
        return allowed(player, data) && !data.isOverloadLocked() && !data.isInterfering() && ID.equals(data.getSlot(data.getCurrentPreset(), slot));
    }
    /**
     * このセッション自体がまだ術者の動作中のものか: 支払いの際に発したイベント（オーバーロード）が止めたか、新しいものが
     * 置き換えたかもしれない。
     */
    private static boolean running(ServerPlayer player, Session session) { return ACTIVE.get(player.getUUID()) == session; }

    /** このスロットからオンにしたモードが動いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        return session != null && session.slot == slot ? session.ticks : -1;
    }

    /**
     * キーアップまたは中断: 原作はそれを自身のキーが始めたコンテキストにだけ渡すので、別のスロットのキーではこれは止まらない。
     * すべてのセッションを終える処理は、引き続きstop(player)を呼ぶ。
     */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        // 残ったオーブは、最後の更新が切れるのを待たずに今フェードアウトさせる。
        for (var orb : session.orbs) broadcast(player, orbEffect(player, session, orb, RETIRE_LIFE));
        session.orbs.clear();
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        if (data == null || data.isReadOnly() || session.level != player.level()) return;
        data.setCooldown(ID, session.cooldown());
    }
    private static MdBallEffect orbEffect(ServerPlayer player, Session session, Orb orb, int life) {
        return new MdBallEffect(session.level.dimension().location(), player.getUUID(), player.getId(),
                orb.session(), orb.subX(), orb.subY(), orb.subZ(), life);
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int orbCount(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.orbs.size();
    }

    private static void spawn(ServerPlayer player, Session session) {
        float theta = (float) (-player.getYRot() / 180 * Math.PI)
                + Mth.randomBetween(player.getRandom(), -(float) Math.PI * .45f, (float) Math.PI * .45f);
        float radius = Mth.randomBetween(player.getRandom(), .8f, 1.3f);
        var orb = new Orb(io.github.pinchan4273.reacademycraft.network.MdSessions.next(),
                Mth.sin(theta) * radius, Mth.randomBetween(player.getRandom(), -1.2f, .2f), Mth.cos(theta) * radius);
        session.orbs.add(orb);
        broadcast(player, orbEffect(player, session, orb, ORB_LIFE));
    }

    /** 原作は攻撃自身のコストを、対象を選ぶ前の関門として払う。そのため有効な対象が無いtickでも支払い可能かを確かめ、何もしない。 */
    private static void attack(ServerPlayer player, PlayerAbilityData data, Session session) {
        if (session.orbs.isEmpty()) return;
        var level = player.serverLevel();
        List<Entity> targets = level.getEntities(player, player.getBoundingBox().inflate(session.range()),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        if (targets.isEmpty()) return;
        if (!data.consume(ID, session.attackConsumption(), session.attackOverload(), player.getAbilities().instabuild)) return;
        // コストで術者がオーバーロードすることがあり、オーバーロード自身のイベントは、consumeが戻る前にすべてのセッション
        // （このセッションも含み、オーブも退く）を止める。止まったセッションはそれ以上撃たない: オーバーロードさせたコストは払われ、
        // ミサイルは飛ばず、経験値も与えない。
        if (!running(player, session) || session.orbs.isEmpty()) return;
        Entity nearest = null; double closest = Double.MAX_VALUE;
        for (var candidate : targets) {
            double distance = candidate.distanceToSqr(player);
            if (distance < closest) { closest = distance; nearest = candidate; }
        }
        var orb = session.orbs.remove(player.getRandom().nextInt(session.orbs.size()));
        var origin = player.position().add(orb.subX(), orb.subY() + player.getEyeHeight(), orb.subZ());
        var target = new net.minecraft.world.phys.Vec3(nearest.getX(), nearest.getY() + nearest.getEyeHeight(), nearest.getZ());
        broadcast(player, new MdRayEffect(session.level.dimension().location(), player.getUUID(), player.getId(),
                orb.session(), origin, target));
        player.level().playSound(null, origin.x, origin.y, origin.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_RAY_SMALL.get(), net.minecraft.sounds.SoundSource.AMBIENT, .8f, 1f);
        // 原作は当たりのクールダウンを消すので、ミサイルは必ず当たる。
        nearest.invulnerableTime = -1;
        MDDamageHelper.attack(player, data, nearest, ID, session.damage());
        data.addProficiency(ID, .001f);
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                // 押されているキーがもう無いのは、妨害が始まったことを示す: 攻撃を続けずに終える。
                || data.isInterfering()
                // オーバーロードでも終える: 維持コストの導入前は、毎tickのconsumeがこれを拒否して終わらせていた。
                || data.isOverloadLocked()) { stop(player); return; }
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
        // 維持コストはこのtickの始めに精算済み。払えなかった要求は外れている。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        // 原作はtickを増やす前に判定と動作を行うので、オーブは10tick待たずに最初のtickで生成される。
        if (session.ticks % SPAWN_INTERVAL == 0 && session.orbs.size() < MAX_ORBS) spawn(player, session);
        if (session.ticks != 0 && session.ticks % ATTACK_INTERVAL == 0) attack(player, data, session);
        if (!running(player, session)) return;
        if (session.ticks != 0 && session.ticks % RENEW_INTERVAL == 0)
            for (var orb : session.orbs) broadcast(player, orbEffect(player, session, orb, ORB_LIFE));
        session.ticks++;
    }

    /** テスト用の入口: サーバーのtickループを待たずにセッションを進める。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }
    /** テスト用の入口: オーブが1つも残っていない動作中のセッション。生成と攻撃の間隔だけではこの状態にならない。 */
    public static void discardOrbsForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.orbs.clear();
    }

    private static List<ServerPlayer> listeners(ServerPlayer player) {
        var out = new java.util.LinkedHashSet<ServerPlayer>(); out.add(player);
        for (var observer : player.serverLevel().players())
            if (observer.distanceToSqr(player) <= PRESENTATION_RANGE * PRESENTATION_RANGE) out.add(observer);
        out.removeIf(listener -> listener.level() != player.level());
        return List.copyOf(out);
    }
    private static void broadcast(ServerPlayer player, MdBallEffect orb) {
        for (var listener : listeners(player)) AcademyNetwork.send(listener, orb);
    }
    private static void broadcast(ServerPlayer player, MdRayEffect beam) {
        for (var listener : listeners(player)) AcademyNetwork.send(listener, beam);
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.remove(p.getUUID()) != null)
            io.github.pinchan4273.reacademycraft.network.SkillModeState.send(p, ID, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
