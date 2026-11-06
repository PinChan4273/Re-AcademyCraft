package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.DarkWingState;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 六翼の表示の状態。サーバーからは展開の有無だけを受け、動きはクライアントで作る。
 * <p>
 * 風の翼（{@link ClientStormWingEffect#render}）と同じ考え方で、根元は描画時のpartial tickで体に追従し（レイヤーが体の部品に付く）、
 * 中節・先端は胴の向きを少し遅れて追う。遅れはtickごとの値を描画でlerpするので、フレームレートに左右されず跳ねない。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkWings {
    /** 展開・収納にかかるtick。 */
    static final float OPEN_TICKS = 12;
    /** 中節・先端が胴の向きへ近づく割合（tickごと）。 */
    static final float MID_FOLLOW = .42f, TIP_FOLLOW = .26f;
    /** 遅れの上限（度）。これ以上は先端が体から離れて見える。 */
    static final float MAX_LAG = 38;

    public static final class State {
        boolean deployed;
        float open, openO;
        float yawMid, yawTip;
        float lagMid, lagMidO, lagTip, lagTipO;
        float liftO, lift;
        float sweepO, sweep;
        /** 高速飛行の姿勢の傾き（0〜1）。 */
        float leanO, lean;
        int flight, rayTicks;
        boolean seeded;
        // 動作（サーバーから種類だけを受ける）。
        int stabTicks = -1, waveTicks = -1, guardTicks = -1, guardDir;
        int stabWing;
        boolean rayOn;
        float stabReach, rayRange, waveRange;
        /** 外部の演出（公開APIの{@code DARK_WING_STAB}）の刺突の届く点（ワールドの座標）。自然能力の刺突ではnull（頭の向きと距離で描く）。 */
        @Nullable net.minecraft.world.phys.Vec3 stabTarget;
        /** 外部の演出の刺突をしたことがあるか（最初の1撃は、本体と同じく翼を自由に選ぶ）。 */
        boolean stabbedBefore;
        /** 外部の刺突の届く点。自然能力の状態と、外部の刺突でないときはnull。 */
        @Nullable public net.minecraft.world.phys.Vec3 stabTarget() { return stabTarget; }
        /** 展開の割合（滑らかにした0〜1）。 */
        public float open(float partial) {
            float t = Mth.clamp(Mth.lerp(partial, openO, open), 0, 1);
            return t * t * (3 - 2 * t);
        }
        public float lagMid(float partial) { return Mth.lerp(partial, lagMidO, lagMid); }
        public float lagTip(float partial) { return Mth.lerp(partial, lagTipO, lagTip); }
        public float lift(float partial) { return Mth.lerp(partial, liftO, lift); }
        public float sweep(float partial) { return Mth.lerp(partial, sweepO, sweep); }
        public boolean rayOn() { return rayOn; }
        public int rayTicks() { return rayTicks; }
        public float rayRange() { return rayRange; }
        /** 刺している翼（段×2＋右なら1）。 */
        public int stabWing() { return stabWing; }
        public boolean stabLeft() { return stabWing % 2 == 0; }
        public float stabReach() { return stabReach; }
        public float waveRange() { return waveRange; }
        public int guardDir() { return guardDir; }
        /** 刺突の伸び（0〜1）: 2tickで伸び、6tickで戻る。 */
        public float stab(float partial) {
            if (stabTicks < 0) return 0;
            float t = stabTicks + partial;
            return t < 2 ? t / 2 : Math.max(0, 1 - (t - 2) / 6);
        }
        /** 空気砲の予備動作（0〜1、振りかぶり）。 */
        public float windup(float partial) { if (waveTicks < 0) return 0; float t = waveTicks + partial; return t < 4 ? t / 4 : Math.max(0, 1 - (t - 4) / 2); }
        /** 空気砲の振り（0〜1）。 */
        public float swing(float partial) { if (waveTicks < 4) return 0; float t = waveTicks + partial - 4; return t < 2 ? t / 2 : Math.max(0, 1 - (t - 2) / 6); }
        /** 風の帯の進み（0〜1）。出ていなければ負。 */
        public float waveAge(float partial) { if (waveTicks < 4) return -1; return Math.min(1, (waveTicks + partial - 4) / 8); }
        /** 防御の構え（0〜1）。 */
        public float guard(float partial) {
            if (guardTicks < 0) return 0;
            float t = guardTicks + partial;
            float k = t < 5 ? t / 5 : Math.max(0, 1 - (t - 10) / 8);
            return k * k * (3 - 2 * k);
        }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    @Nullable private static ClientLevel world;

    private ClientDarkWings() { }

    public static void receive(DarkWingState packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { STATES.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; }
        var state = STATES.computeIfAbsent(packet.entityId(), id -> new State());
        state.deployed = packet.deployed();
    }
    public static void receive(io.github.pinchan4273.reacademycraft.network.DarkWingAction packet) {
        // 形態の合図は翼の状態ではないので、外観の受け口へ渡す。
        if (packet.kind() == io.github.pinchan4273.reacademycraft.network.DarkWingAction.BODY || packet.kind() == io.github.pinchan4273.reacademycraft.network.DarkWingAction.BODY_FLASH) {
            ClientDarkBody.receive(packet);
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level != world) { STATES.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; }
        var s = STATES.computeIfAbsent(packet.entityId(), id -> new State());
        switch (packet.kind()) {
            case io.github.pinchan4273.reacademycraft.network.DarkWingAction.STAB -> { s.stabTicks = 0; s.stabWing = packet.value(); s.stabReach = packet.reach(); }
            case io.github.pinchan4273.reacademycraft.network.DarkWingAction.RAY_ON -> { s.rayOn = true; s.rayTicks = 0; s.rayRange = packet.reach(); }
            case io.github.pinchan4273.reacademycraft.network.DarkWingAction.FLIGHT -> s.flight = packet.value();
            case io.github.pinchan4273.reacademycraft.network.DarkWingAction.RAY_OFF -> s.rayOn = false;
            case io.github.pinchan4273.reacademycraft.network.DarkWingAction.CANNON -> { s.waveTicks = 0; s.waveRange = packet.reach(); }
            case io.github.pinchan4273.reacademycraft.network.DarkWingAction.GUARD -> {
                s.guardTicks = 0; s.guardDir = packet.value();
                // 翼の防御は攻撃を止める。見た目も止める。
                s.rayOn = false; s.stabTicks = -1; s.waveTicks = -1;
            }
            default -> { }
        }
    }
    /** 今の実体の番号の写し（描画用）。 */
    public static java.util.List<Integer> ids() { return java.util.List.copyOf(STATES.keySet()); }
    @Nullable public static State state(int entityId) { return STATES.get(entityId); }
    /**
     * 体の描画が描く状態: 自然能力の状態があればそれ、無ければ外部の演出の状態（演出API版3）。自然能力の六翼が出ている実体では、
     * 外部の翼を重ねて描かない（二重の翼にしない。自然能力の状態は書き換えない）。
     */
    @Nullable public static State drawnState(int entityId) {
        var own = STATES.get(entityId);
        return own != null ? own : EXTERNAL.get(entityId);
    }

    // ---- 外部の演出（公開APIのDARK_WINGS・DARK_WING_STAB・DARK_FEATHER_BALL、演出API版3） --------------------------------

    /** 外部の演出の六翼の状態（実体の番号ごと）。自然能力の{@link #STATES}とは別に持ち、同じ{@link #step}で動かす。 */
    private static final Map<Integer, State> EXTERNAL = new HashMap<>();
    /** 外部の翼を出したままにしている演出の札（実体の番号ごと）。札が1つでもあれば翼を出し、無くなれば本体と同じ速さで畳む。 */
    private static final Map<Integer, java.util.Set<Object>> HOLDS = new HashMap<>();

    private static State external(int entityId) {
        var level = Minecraft.getInstance().level;
        if (level != world) { STATES.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; }
        return EXTERNAL.computeIfAbsent(entityId, id -> new State());
    }
    /** 札で外部の翼を出したままにする。{@code instant}なら、畳んでいる翼をすぐ開いた形にする（刺突と防御は動作の間だけ翼を出すので）。 */
    static void holdExternal(int entityId, Object token, boolean instant) {
        var state = external(entityId);
        HOLDS.computeIfAbsent(entityId, id -> new java.util.HashSet<>()).add(token);
        state.deployed = true;
        if (instant) { state.open = Math.max(state.open, 1); state.openO = Math.max(state.openO, 1); }
    }
    /** 札を離す。札が無くなれば翼を畳み始める。 */
    static void releaseExternal(int entityId, Object token) {
        var holds = HOLDS.get(entityId);
        if (holds != null && holds.remove(token) && holds.isEmpty()) HOLDS.remove(entityId);
        var state = EXTERNAL.get(entityId);
        if (state != null && !HOLDS.containsKey(entityId)) state.deployed = false;
    }
    /** 札を離し、ほかの札が無ければ畳むのを待たずにすぐ消す（ログアウト・世界の切り替え・資源の読み直し・実体の入れ替わり）。 */
    static void dropExternal(int entityId, Object token) {
        releaseExternal(entityId, token);
        if (!HOLDS.containsKey(entityId)) EXTERNAL.remove(entityId);
    }
    /** 外部の翼が畳んでいる途中か（札が無く、まだ開いている）。札を離した演出は、これがfalseになるまで上限の枠を使う。 */
    static boolean externalFolding(int entityId) {
        var state = EXTERNAL.get(entityId);
        return state != null && !state.deployed && (state.open > 0 || state.openO > 0);
    }
    /** 外部の翼で刺す（自然能力の{@link io.github.pinchan4273.reacademycraft.network.DarkWingAction#STAB}と同じ動き）。翼は本体と同じ決まりで選ぶ。 */
    static void stabExternal(int entityId, net.minecraft.world.phys.Vec3 target, net.minecraft.util.RandomSource random) {
        var s = external(entityId);
        s.stabWing = io.github.pinchan4273.reacademycraft.skill.WhiteWingStab.nextWing(s.stabbedBefore ? s.stabWing : -1, random);
        s.stabbedBefore = true;
        s.stabTicks = 0; s.stabTarget = target;
    }
    /** 外部の翼で防ぐ（自然能力の{@link io.github.pinchan4273.reacademycraft.network.DarkWingAction#GUARD}と同じ動き）。 */
    static void guardExternal(int entityId, int direction) {
        var s = external(entityId);
        s.guardTicks = 0; s.guardDir = direction;
        // 自然能力と同じく、防御は刺突の見た目を止める。
        s.stabTicks = -1;
    }
    /** 外部の刺突・防御の動作を止める（演出を早めに終えたとき）。 */
    static void cancelExternal(int entityId, boolean stab) {
        var s = EXTERNAL.get(entityId);
        if (s == null) return;
        if (stab) s.stabTicks = -1; else s.guardTicks = -1;
    }
    /** 外部の刺突・防御が動いている途中か。 */
    static boolean externalActing(int entityId, boolean stab) {
        var s = EXTERNAL.get(entityId);
        return s != null && (stab ? s.stabTicks >= 0 : s.guardTicks >= 0);
    }
    /**
     * 攻撃の来た位置から、防御の向き（{@link io.github.pinchan4273.reacademycraft.network.DarkWingAction#GUARD_ALL}など）。サーバーの
     * {@code skill.DarkDefense.direction}と同じ決まり（体の向きの右・左の成分が0.5より大きければ半球）。
     */
    static int guardDirection(LivingEntity entity, @Nullable net.minecraft.world.phys.Vec3 from) {
        if (from == null) return io.github.pinchan4273.reacademycraft.network.DarkWingAction.GUARD_ALL;
        var to = from.subtract(entity.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) return io.github.pinchan4273.reacademycraft.network.DarkWingAction.GUARD_ALL;
        double yaw = Math.toRadians(entity.yBodyRot);
        double right = to.normalize().dot(new net.minecraft.world.phys.Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)));
        return right > .5 ? io.github.pinchan4273.reacademycraft.network.DarkWingAction.GUARD_RIGHT
                : right < -.5 ? io.github.pinchan4273.reacademycraft.network.DarkWingAction.GUARD_LEFT : io.github.pinchan4273.reacademycraft.network.DarkWingAction.GUARD_ALL;
    }
    /** 試験用の入口: その実体の外部の翼が出ている（出つつある）か。 */
    public static boolean externalDeployed(int entityId) { var s = EXTERNAL.get(entityId); return s != null && s.deployed; }
    /** 試験用の入口: 外部の翼の状態の数。 */
    public static int externalStates() { return EXTERNAL.size(); }
    /** テスト用の入口: その実体の翼が出ている（出つつある）か。 */
    public static boolean deployed(int entityId) { var s = STATES.get(entityId); return s != null && s.deployed; }

    private static boolean step(State s, LivingEntity entity) {
        s.openO = s.open;
        s.open = Mth.clamp(s.open + (s.deployed ? 1 : -1) / OPEN_TICKS, 0, 1);
        float yaw = entity.yBodyRot;
        if (!s.seeded) { s.yawMid = s.yawTip = yaw; s.seeded = true; }
        s.yawMid += Mth.wrapDegrees(yaw - s.yawMid) * MID_FOLLOW;
        s.yawTip += Mth.wrapDegrees(yaw - s.yawTip) * TIP_FOLLOW;
        s.lagMidO = s.lagMid; s.lagTipO = s.lagTip;
        s.lagMid = Mth.clamp(Mth.wrapDegrees(yaw - s.yawMid), -MAX_LAG, MAX_LAG);
        s.lagTip = Mth.clamp(Mth.wrapDegrees(yaw - s.yawTip), -MAX_LAG, MAX_LAG);
        // 上下の動きで翼がわずかにしなる（上昇で下へ、下降で上へ）。
        s.liftO = s.lift;
        float vertical = (float) (entity.getY() - entity.yo);
        s.lift += (Mth.clamp(vertical * 45, -14, 14) - s.lift) * .22f;
        // 進む速さで翼が後ろへ流れる。
        s.sweepO = s.sweep;
        double speed = new net.minecraft.world.phys.Vec3(entity.getX() - entity.xo, 0, entity.getZ() - entity.zo).length();
        // 高速飛行: 水平なら翼を左右に広げ、上下を向くほど後ろへ流す。
        boolean fast = s.flight == io.github.pinchan4273.reacademycraft.network.DarkWingAction.FLIGHT_FAST;
        float flow = fast ? Mth.clamp(Math.abs(entity.getXRot()) / 60, 0, 1) * 35 : (float) Mth.clamp(speed * 55, 0, 42);
        s.sweep += (flow - s.sweep) * .18f;
        s.leanO = s.lean;
        s.lean += ((fast ? 1 : 0) - s.lean) * .18f;
        if (s.rayOn) s.rayTicks++;
        if (s.stabTicks >= 0 && ++s.stabTicks > 8) s.stabTicks = -1;
        if (s.waveTicks >= 0 && ++s.waveTicks > 12) s.waveTicks = -1;
        if (s.waveTicks == 4) ClientDarkEffects.gust(entity, s.waveRange);
        if (s.guardTicks >= 0 && ++s.guardTicks > 18) s.guardTicks = -1;
        return !s.deployed && s.open <= 0 && s.flight == 0;
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { STATES.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        STATES.entrySet().removeIf(entry -> {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity living) || living.isRemoved()) return true;
            return step(entry.getValue(), living);
        });
        // 外部の翼も同じ動きで進める。札が残っている間は消さない（実体を失った演出は、自分で札を離す）。
        EXTERNAL.entrySet().removeIf(entry -> {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity living) || living.isRemoved()) { HOLDS.remove(entry.getKey()); return true; }
            return step(entry.getValue(), living) && !HOLDS.containsKey(entry.getKey());
        });
    }
    /** 高速飛行の姿勢: エリトラと同じく体を進行方向へ倒し、足をばたつかせない。当たり判定は変えない。 */
    @SubscribeEvent public static void renderPre(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre event) {
        var entity = event.getEntity();
        var state = STATES.get(entity.getId());
        if (state == null) return;
        if (state.flight == io.github.pinchan4273.reacademycraft.network.DarkWingAction.FLIGHT_FAST) {
            // 速さの前後の値を両方0にする（描画は2つの値を補間する）。歩きの進みは動かない。
            entity.walkAnimation.update(0, 1);
            entity.walkAnimation.update(0, 1);
        }
        float partial = event.getPartialTick();
        float t = Mth.lerp(partial, state.leanO, state.lean);
        if (t < .01f) return;
        float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, .9, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(t * (90 + entity.getViewXRot(partial))));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.translate(0, -.9, 0);
        leaning = true;
    }
    private static boolean leaning;
    @SubscribeEvent public static void renderPost(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post event) {
        if (leaning) { event.getPoseStack().popPose(); leaning = false; }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { STATES.clear(); EXTERNAL.clear(); HOLDS.clear(); world = null; }
}
