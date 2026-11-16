package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.FlashingState;
import io.github.pinchan4273.reacademycraft.network.SelfNotice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作Flashing（WeAthFolD）: テレポーターのレベル5技能。
 *
 * キーで瞬間移動モードをオン・オフする。オンにするとlerp(80, 60, exp)のCPとlerp(100, 50, exp)のオーバーロード（
 * 原作lerp(250, 180, exp)）がかかる。1.5.2から、続く間もオーバーロードは回復する（起動の値に保たない）。
 * 原作はlerp(60, 150, exp)tick後にモードを終える。移植版は原作とは異なる仕様として時間制限を設けず、代わりに部類の外の切り替え型の
 * 共通の維持コストを払い、払えなくなると終える（1.5.2: 回復の lerp(100%, 25%) ＋ toggle_upkeep.flashing
 * （熟練度0%で4）× (1 - 熟練度)）。モード中は移動キーで狙う: どれかを押すと左・右・前・後のいずれかを選び、
 * 同じキーを離すと、術者の視線（上下を含む）を基準にその方向へlerp(12, 18, exp)ブロック瞬間移動する。1回ごとにlerp(14, 6, exp)のCP。
 * 各瞬間移動の後、術者自身のクライアントが2秒間重力のほとんどを打ち消す。モードの終了は、理由を問わずクールダウン
 * lerp(900, 400, exp)を始める。原作のterminate()は常にそれを設定するので、CP不足でオンにできなかったときも同じ。
 *
 * クライアントは4方向のどれかだけを送り、行き先はサーバーが自身の持つ術者の位置と視線から求める（原作のserverPerformと同じ）。
 *
 * 1.5.2: 能力設定でパッシブにすると、能力使用モードの間に{@link PassiveSkills}が自動で始める（スロットのインデックスは{@link PassiveSkills#SLOT}）。
 * 起動の費用・維持費・1回ごとの費用・終了のクールダウン（CP不足でオンにできなかったときを含む）はキーで始めるときと同じ。
 * パッシブの間は、能力使用モードの間ずっと移動キーが瞬間移動の狙いになる（能力設定の注記に書く）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class Flashing {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("academy", "flashing");
    /** 原作のキーid: 1は左（A）、2は右（D）、3は前（W）、4は後（S）。 */
    public static final int LEFT = 1, RIGHT = 2, FORWARD = 3, BACK = 4;
    private static final Vec3[] DIRECTIONS = { null, new Vec3(0, 0, -1), new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(-1, 0, 0) };
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset; int slot; final float exp; int ticks;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot, float exp) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; this.exp = exp;
        }
    }

    private Flashing() { }
    private static PlayerAbilityData data(ServerPlayer p) { return AbilityCapabilities.of(p).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID);
    }
    public static float consumption(float exp) { return ArcGen.lerp(14, 6, exp); }
    public static float startConsumption(float exp) { return ArcGen.lerp(80, 60, exp); }
    /** 起動のオーバーロード（原作lerp(250, 180)から）。 */
    public static float startOverload(float exp) { return ArcGen.lerp(100, 50, exp); }
    /** 1.5.2: 跳んだ後の稼働の長さ。1秒に1回跳び続ける間は、コースの待ちが短くても過負荷が回復しない。 */
    public static final int ACTION_TICKS = 21;
    /** 1.5.2: 1回跳ぶごとの過負荷（稼働の払い。1秒に1回跳び続けると熟練度100%・最大過負荷1,000で約90秒でロック）。 */
    public static float blinkOverload(float exp) { return ArcGen.lerp(22, 11, exp); }
    /** このスロットからオンにしたモードが動いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        return session != null && session.slot == slot ? session.ticks : -1;
    }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(900, 400, exp); }
    public static double distance(float exp) { return ArcGen.lerp(12, 18, exp); }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    /** 1.5.2: パッシブ（自動で起動）で動いているか（スロットのキーで始めたものはfalse）。 */
    public static boolean passive(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.slot == PassiveSkills.SLOT; }
    /** 1.5.2: パッシブにした時に、スロットのキーで始めたモードが動いていたら、止めずにパッシブのモードにする（起動の費用を二度払わない）。 */
    public static void adoptPassive(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.slot = PassiveSkills.SLOT;
    }

    /** キー: モードがオンならオフにし、そうでなければオンを試みる。 */
    public static String toggle(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (ACTIVE.containsKey(player.getUUID())) { stop(player); return ""; }
        // 1.5.2: slotが{@link PassiveSkills#SLOT}なら、パッシブの自動の起動（パッシブに切り替えていること）。
        if (!PassiveSkills.slotMatches(data, ID, slot)
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        // 1.5.2: ロック中は断る。ただしロック中の再稼働を許した技能（パッシブ。優先順位の順）は通す。
        if (data.lockBlocks(ID)) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        float exp = data.getProficiency(ID);
        // 原作serverMadeAlive: CPが無ければモードはすぐ終わり、その終了がクールダウンを設定する。
        if (!data.consume(ID, startConsumption(exp), startOverload(exp), player.getAbilities().instabuild)) {
            data.setCooldown(ID, cooldown(exp));
            AbilitySyncEvents.sync(player, true);
            return "academy.cast.cp";
        }
        // 支払いで術者がオーバーロードすることがあり、そのイベントは、このモードがまだ存在しないうちにすべてのモードを終える:
        // その場合は何も始めない。オーバーロードさせた分は、他のオーバーロードを起こすコストと同じく払ったまま。
        // 原作のオーバーロードは、生まれたばかりのモードを中止し、その終了が上と同じくクールダウンを設定する。
        if (!allowed(player, data) || data.lockBlocks(ID) || data.isInterfering() || !PassiveSkills.slotMatches(data, ID, slot)) {
            if (!data.isReadOnly() && data.hasLearned(ID)) data.setCooldown(ID, cooldown(exp));
            AbilitySyncEvents.sync(player, true);
            return "academy.cast.overload";
        }
        var session = new Session(player, data, slot, exp);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        if (session.upkeep != null) session.upkeep.recoveryShare();
        send(player, true, false);
        AbilitySyncEvents.sync(player, true);
        return "";
    }

    /** 原作serverPerform: 4方向のいずれかへの1回の瞬間移動。 */
    public static void perform(ServerPlayer player, int direction) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        // 記録が無いのに瞬間移動が頼まれたら、クライアントがモード中のつもりのまま食い違っているので、終了を返す（1.5.2）。
        if (session == null) { send(player, false, false); return; }
        if (direction < LEFT || direction > BACK || !allowed(player, data)) return;
        // 原作の方向キーも技能キーなので、妨害されている間はどれも効かない。ただしモード自体は続く。
        if (data.isInterfering()) return;
        var target = TPSkillHelper.permit(player, destination(player, direction, session.exp));
        if (target == null) return;
        // 1.5.2: 跳ぶのは稼働の払い。CPの回復待ちはやり直さず、過負荷を払う。拒否の条件は今までの consume と同じ。
        if (!data.payAction(ID, consumption(session.exp), blinkOverload(session.exp), player.getAbilities().instabuild, true, true, true, ACTION_TICKS)) return;
        // 瞬間移動したtickは待機ではない。
        data.upkeepActed(session.upkeep);
        TPSkillHelper.teleport(player, target);
        data.addProficiency(ID, .002f);
        TPSkillHelper.incrTPCount(player);
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_FLASHING.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, 1f);
        send(player, true, true);
        AbilitySyncEvents.sync(player, true);
    }

    /**
     * 原作getDest。方向をpitchでZ軸周りに、次にyawで回し、目から瞬間移動の届く距離まで伸ばす。ただし追跡は足元から始める。
     * 生き物（その位置を目の高さだけ上げた所）とブロックに当たる。Mark Teleportの面のオフセットを使うが、面の横では高さはブロックの
     * ものではなく、当たった点に1.7を足したもの。
     */
    public static Vec3 destination(net.minecraft.world.entity.player.Player player, int direction, float exp) {
        var end = player.getEyePosition().add(direction(direction, player.getYRot(), player.getXRot()).scale(distance(exp)));
        var trace = TPSkillHelper.trace(player, player.position(), end, TPSkillHelper::living);
        if (trace.entity() != null) return trace.position().add(0, trace.entity().getEyeHeight(), 0);
        var block = trace.block();
        if (block == null) return end;
        var hit = block.getLocation();
        double x = hit.x, y = hit.y, z = hit.z;
        switch (block.getDirection()) {
            case DOWN -> y -= 1;
            case UP -> y += 1.8;
            case NORTH -> { z -= .6; y = hit.y + 1.7; }
            case SOUTH -> { z += .6; y = hit.y + 1.7; }
            case WEST -> { x -= .6; y = hit.y + 1.7; }
            case EAST -> { x += .6; y = hit.y + 1.7; }
        }
        if (block.getDirection().getAxis() != Direction.Axis.Y
                && !player.level().isEmptyBlock(new BlockPos((int) x, (int) (y + 1), (int) z))) y -= 1.25;
        return new Vec3(x, y, z);
    }

    /**
     * 原作のキーの方向の回し方: LambdaLib2のrotateAroundZでpitchだけ回し、次に1.12のVec3d.rotateYawで-90 - yawだけ回す。
     * 原作と同じくラジアンとfloatの三角関数を使う。
     */
    public static Vec3 direction(int direction, float yawDegrees, float pitchDegrees) {
        var dir = DIRECTIONS[direction];
        float pitch = pitchDegrees * Mth.PI / 180, yaw = (-90 - yawDegrees) * Mth.PI / 180;
        float pc = Mth.cos(pitch), ps = Mth.sin(pitch);
        dir = new Vec3(dir.x * pc + dir.y * ps, dir.y * pc - dir.x * ps, dir.z);
        float yc = Mth.cos(yaw), ys = Mth.sin(yaw);
        return new Vec3(dir.x * yc + dir.z * ys, dir.y, dir.z * yc - dir.x * ys);
    }

    /** 理由を問わずモードを終え、原作のクールダウンを設定する。 */
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        // 習得していない技能（止める前にリスナーやカテゴリの変更が外した）にはクールダウンを書かない。
        if (data != null && !data.isReadOnly() && data.hasLearned(ID)) data.setCooldown(ID, cooldown(session.exp));
        send(player, false, false);
        if (data != null) AbilitySyncEvents.sync(player, true);
    }

    /** 本人のクライアントへのモードの状態（エンティティIDを含まないので、作り直しの最中にも送れる）。 */
    private static void send(ServerPlayer player, boolean active, boolean flashed) {
        SelfNotice.send(player, new FlashingState(active, flashed));
    }

    /** このtickもモードを続けてよいか（tickの始めと、熟練度のイベントの後に確かめる）。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        // 1.5.2: パッシブで動いているものは、プリセットとスロットを見ず、パッシブのままかを見る。
        return data != null && allowed(player, data) && !(session.level != player.level()
                || !PassiveSkills.sessionMatches(data, ID, session.preset, session.slot));
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        // 原作はmax_timeまで続いた。ここでは、このtickの始めに精算した維持コストを払えた間続く。原作serverTickはモードの間
        // オーバーロードを回復させなかったが、1.5.2から回復を止めない。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        data.upkeepEarn(session.upkeep);
        // 熟練度のイベントのリスナーが、モードを終えた・置き換えた・続けられなくした（能力、スロット、プリセット）かもしれない:
        // このtickを古いセッションで続けない。
        if (ACTIVE.get(player.getUUID()) != session) return;
        if (!continues(player, data, session) || !data.upkeepHeld(session.upkeep)) { stop(player); return; }
        session.ticks++;
    }
    /** テスト用の入口: モードのサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /** 死亡またはエンドからの帰還（作り直し）: 止めるときと同じ終了を本人へ送る（移動キーが照準のまま残らないように。1.5.2）。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (ACTIVE.remove(e.getEntity().getUUID()) != null && e.getEntity() instanceof ServerPlayer p) send(p, false, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
