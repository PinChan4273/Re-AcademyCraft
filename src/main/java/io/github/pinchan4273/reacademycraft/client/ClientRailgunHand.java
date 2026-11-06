package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.RailgunHandEffect;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RailgunHandEffect（LambdaLib2のDummyRenderDataでプレイヤーに付けるもの）: arc_burstの40フレームを、
 * 各40msで、原作のRenderDummyと同じ位置に描く。術者自身の目から見ると、右手の小さな絵（目から右0.26、下0.15、
 * 前0.24、大きさ0.4）。それ以外から見ると、胸の前の2ブロックの絵（ViewOptimizeの三人称のずれと、原作の1.8上・1前）で、
 * ピッチに合わせて傾ける。原作はこの傾きに見る人自身のピッチ（Minecraft.player）を使うので、見る人が術者のときだけ合う。
 * ここでは常に術者のピッチを使う。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientRailgunHand {
    static final int COUNT = 40;
    static final long PER_FRAME = 40;
    private static final Identifier[] FRAMES = new Identifier[COUNT];
    static {
        for (int i = 0; i < COUNT; i++) FRAMES[i] = Identifier.fromNamespaceAndPath("academy", "textures/effects/arc_burst/" + i + ".png");
    }
    private record Burst(int player, long started) { }
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static long received, drawn;
    private ClientRailgunHand() { }

    /** 外部の演出を足し、取り除くときに渡す印を返す（自然能力の数には入れない）。 */
    static Object startExternal(int player) {
        var burst = new Burst(player, Util.getMillis());
        BURSTS.add(burst);
        return burst;
    }
    /** その外部の演出が、まだ描く一覧にあるか。 */
    static boolean hasExternal(Object burst) { return BURSTS.stream().anyMatch(other -> other == burst); }
    /** 外部の演出をすぐ取り除く。同じ値の自然能力の演出を消さないよう、印そのもので探す。 */
    static void endExternal(Object burst) { BURSTS.removeIf(other -> other == burst); }
    public static void receive(RailgunHandEffect effect) {
        received++;
        BURSTS.add(new Burst(effect.player(), Util.getMillis()));
    }

    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent extract) { io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.on(extract, io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.Stage.AFTER_TRANSLUCENT_TERRAIN, ClientRailgunHand::renderFrame); }
    private static void renderFrame(io.github.pinchan4273.reacademycraft.client.render.WorldFrame event) {
        if (BURSTS.isEmpty()) return;
        var client = Minecraft.getInstance();
        long now = Util.getMillis();
        BURSTS.removeIf(burst -> now - burst.started >= PER_FRAME * COUNT);
        if (client.level == null || BURSTS.isEmpty()) return;
        var camera = event.getCamera().position(); var pose = event.getPoseStack(); float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        io.github.pinchan4273.reacademycraft.client.render.Immediate.setShader(io.github.pinchan4273.reacademycraft.client.render.Immediate.Shader.TEX_COLOR_KEEP);
        io.github.pinchan4273.reacademycraft.client.render.Immediate.enableBlend(); io.github.pinchan4273.reacademycraft.client.render.Immediate.defaultBlendFunc(); io.github.pinchan4273.reacademycraft.client.render.Immediate.disableCull();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.enableDepthTest(); io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(false);
        try {
            for (var burst : BURSTS) {
                if (!(client.level.getEntity(burst.player) instanceof Player player)) continue;
                io.github.pinchan4273.reacademycraft.client.render.Immediate.setShaderTexture(0, FRAMES[(int) ((now - burst.started) / PER_FRAME)]);
                float pitch = player.getViewXRot(partial);
                pose.pushPose();
                var at = player.getPosition(partial).subtract(camera);
                pose.translate(at.x, at.y, at.z);
                // 原作ViewOptimize.isFirstPerson: 見る人自身の効果で、カメラが頭の中にあるとき。
                if (player == client.player && client.options.getCameraType().isFirstPerson()) {
                    pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot)));
                    pose.mulPose(Axis.XP.rotationDegrees(-pitch));
                    double eye = player.getEyeHeight(), rad = Math.toRadians(pitch);
                    pose.translate(0, Math.cos(rad) * eye, Math.sin(rad) * eye);
                    pose.translate(.26, -.15, -.24);
                    pose.scale(.4f, .4f, 1f);
                } else {
                    pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot)));
                    pose.translate(.15, -.8, .23);
                    pose.translate(0, 1.8, -1);
                    pose.mulPose(Axis.XP.rotationDegrees(-pitch));
                }
                var m = pose.last().pose();
                var buffer = io.github.pinchan4273.reacademycraft.client.render.Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
                buffer.addVertex(m, -1, -1, 0).setUv(0, 1);
                buffer.addVertex(m, 1, -1, 0).setUv(1, 1);
                buffer.addVertex(m, 1, 1, 0).setUv(1, 0);
                buffer.addVertex(m, -1, 1, 0).setUv(0, 0);
                buffer.end();
                pose.popPose();
                drawn++;
            }
        } finally { io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(true); io.github.pinchan4273.reacademycraft.client.render.Immediate.enableCull(); }
    }
    @SubscribeEvent public static void railgunHandLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { BURSTS.clear(); }

    // テスト用。
    public static long received() { return received; }
    public static long drawn() { return drawn; }
    public static int active() { return BURSTS.size(); }
}
