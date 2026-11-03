package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.DarkWingState;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 六翼の表示の状態。サーバーからは展開の有無だけを受け、動きはクライアントで作る。
 * <p>
 * 風の翼（{@link ClientStormWingEffect#render}）と同じ考え方で、根元は描画時のpartial tickで体に追従し（レイヤーが体の部品に付く）、
 * 中節・先端は胴の向きを少し遅れて追う。遅れはtickごとの値を描画でlerpするので、フレームレートに左右されず跳ねない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
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
        if (level != world) { STATES.clear(); world = level; }
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
        if (level != world) { STATES.clear(); world = level; }
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

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { STATES.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        STATES.entrySet().removeIf(entry -> {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity living) || living.isRemoved()) return true;
            return step(entry.getValue(), living);
        });
    }
    /** 高速飛行の姿勢: エリトラと同じく体を進行方向へ倒し、足をばたつかせない。当たり判定は変えない。 */
    @SubscribeEvent public static void renderPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
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
    @SubscribeEvent public static void renderPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
        if (leaning) { event.getPoseStack().popPose(); leaning = false; }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { STATES.clear(); world = null; }
}
