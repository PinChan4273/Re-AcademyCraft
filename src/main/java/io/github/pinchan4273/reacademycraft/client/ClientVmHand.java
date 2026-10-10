package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave;
import io.github.pinchan4273.reacademycraft.skill.DirectedShock;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作Directed ShockとDirected Blastwaveの一人称の拳（HandRenderOverrideData・VanillaHandRenderer・AnimPresets）:
 * キーを押している間、手はmin(2, 秒 / 0.15)にわたってAnimPresetsのprepareの姿勢をとる。曲線は最後の点1を過ぎて2まで
 * 直線で続き、それで拳がパンチの始点に来る。原作の6〜50tickの間に離すと、原作のPUNCH_ANIM_TICKS（6tick）、
 * 秒 / 0.3でパンチし、すべての曲線が0へ戻る。200tick押し続けるか、その範囲の外で離すと、手をそのまま戻す。
 *
 * 原作のサーバーは打撃の直後にcontextを終えるので、実際のサーバーではメッセージの遅れの分だけパンチが短くなる。
 * この移植では、client側で数える6tickを再生する。変換は、ForgeのRenderHandEventが渡す手のposeに、
 * 視点の揺れの後で掛ける（原作は揺れの前に掛けていた）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientVmHand {
    static final int MIN_TICKS = 6, MAX_ACCEPTED_TICKS = 50, MAX_TOLERANT_TICKS = 200, PUNCH_ANIM_TICKS = 6;

    /** LambdaLib2 CubicCurve: 点を通るHermiteスプライン。両端の外は直線。 */
    static final class Curve {
        private final List<double[]> points = new ArrayList<>();
        Curve add(double x, double y) { points.add(new double[]{x, y}); points.sort((a, b) -> Double.compare(a[0], b[0])); return this; }
        private double ik(int i1, int i2) { var p1 = points.get(i1); var p2 = points.get(i2); return (p2[1] - p1[1]) / (p2[0] - p1[0]); }
        private double k(int i, double l) {
            double ret;
            if (i == 0) ret = points.size() == 1 ? 0 : ik(i, i + 1);
            else if (i == points.size() - 1) ret = ik(i, i - 1);
            else ret = .5 * (ik(i + 1, i) + ik(i, i - 1));
            return ret * l;
        }
        double valueAt(double x) {
            int n = points.size();
            if (n == 0) return 0;
            int index = 0;
            while (index < n && points.get(index)[0] < x) index++;
            if (index == n) {
                var p2 = points.get(n - 1);
                double k = n >= 2 ? ik(index - 1, index - 2) : 0;
                return p2[1] + (x - p2[0]) * k;
            }
            if (index == 0) { var p0 = points.get(0); return p0[1] + k(0, 1) * (x - p0[0]); }
            var p0 = points.get(index - 1); var p1 = points.get(index);
            double l = p1[0] - p0[0], t = (x - p0[0]) / l, t2 = t * t, t3 = t2 * t;
            double y0 = p0[1], y1 = p1[1], m0 = k(index - 1, l), m1 = k(index, l);
            return t3 * (m0 + m1 + 2 * y0 - 2 * y1) + t2 * (-2 * m0 - m1 - 3 * y0 + 3 * y1) + t * m0 + y0;
        }
    }
    /** AnimPresetsのCompTransformAnim: 平行移動と回転の曲線。無い曲線は0のまま。 */
    record Anim(@Nullable Curve tx, @Nullable Curve ty, @Nullable Curve tz, @Nullable Curve rx, @Nullable Curve ry) {
        float[] at(double t) {
            return new float[]{v(tx, t), v(ty, t), v(tz, t), v(rx, t), v(ry, t)};
        }
        private static float v(@Nullable Curve c, double t) { return c == null ? 0 : (float) c.valueAt(t); }
    }
    static final Anim PREPARE = new Anim(new Curve().add(0, 0).add(1, -.02), new Curve().add(0, 0).add(.5, .2).add(1, .4),
            new Curve().add(0, 0).add(1, -.05), new Curve().add(0, 0).add(1, -20), null);
    static final Anim PUNCH = new Anim(new Curve().add(0, -.04).add(.5, -.04).add(1, 0), new Curve().add(0, .8).add(.5, .75).add(1, 0),
            new Curve().add(0, -0).add(.3, -.4).add(1, 0), new Curve().add(0, -40).add(.5, -45).add(1, 0),
            new Curve().add(0, 0).add(.3, 10).add(1, 0));

    private static int heldSlot = -1, ticker, punchTicker = -1;
    private static long start;
    private static long preparedFrames, punchedFrames;
    private ClientVmHand() { }

    private static boolean fist(@Nullable net.minecraft.resources.ResourceLocation skill) {
        return DirectedShock.ID.equals(skill) || DirectedBlastwave.ID.equals(skill);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var player = Minecraft.getInstance().player;
        var data = player == null ? null : player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null) { heldSlot = -1; punchTicker = -1; return; }
        if (punchTicker >= 0) {
            if (++punchTicker > PUNCH_ANIM_TICKS) punchTicker = -1;
            return;
        }
        int held = -1;
        for (int i = 0; i < 4; i++) if (fist(data.getSlot(data.getCurrentPreset(), i)) && AbilityControls.held(i)) held = i;
        if (heldSlot < 0 && held >= 0) { heldSlot = held; ticker = 0; start = Util.getMillis(); }
        if (heldSlot < 0) return;
        if (held == heldSlot) {
            if (++ticker >= MAX_TOLERANT_TICKS) heldSlot = -1;
            return;
        }
        // 原作l_keyUp: 範囲内の押し時間なら打撃し、拳がパンチする。それ以外はただ終わる。
        boolean strikes = ticker > MIN_TICKS && ticker < MAX_ACCEPTED_TICKS;
        heldSlot = -1;
        if (strikes) { punchTicker = 0; start = Util.getMillis(); }
    }
    /** 今の姿勢: [x, y, z, x軸の回転, y軸の回転]。ゲーム本来の手のときはnull。 */
    @Nullable public static float[] pose() {
        double seconds = (Util.getMillis() - start) / 1000.0;
        if (punchTicker >= 0) return PUNCH.at(seconds / .3);
        if (heldSlot >= 0) return PREPARE.at(Math.min(2, seconds / .15));
        return null;
    }
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var pose = pose();
        if (pose == null) return;
        if (punchTicker >= 0) punchedFrames++; else preparedFrames++;
        // CompTransform.doTransform: 平行移動、次にx・y・zの周りに回転（支点0、拡大1）。
        var stack = event.getPoseStack();
        stack.translate(pose[0], pose[1], pose[2]);
        stack.mulPose(Axis.XP.rotationDegrees(pose[3]));
        stack.mulPose(Axis.YP.rotationDegrees(pose[4]));
    }
    // テスト用。
    public static long preparedFrames() { return preparedFrames; }
    public static long punchedFrames() { return punchedFrames; }
    public static boolean posing() { return pose() != null; }
}
