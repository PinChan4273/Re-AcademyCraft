package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.RailgunHandEffect;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RailgunHandEffect（LambdaLib2のDummyRenderDataでプレイヤーに付けるもの）: arc_burstの40フレームを、
 * 各40msで、原作のRenderDummyと同じ位置に描く。術者自身の目から見ると、右手の小さな絵（目から右0.26、下0.15、
 * 前0.24、大きさ0.4）。それ以外から見ると、胸の前の2ブロックの絵（ViewOptimizeの三人称のずれと、原作の1.8上・1前）で、
 * ピッチに合わせて傾ける。原作はこの傾きに見る人自身のピッチ（Minecraft.player）を使うので、見る人が術者のときだけ合う。
 * ここでは常に術者のピッチを使う。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientRailgunHand {
    static final int COUNT = 40;
    static final long PER_FRAME = 40;
    private static final ResourceLocation[] FRAMES = new ResourceLocation[COUNT];
    static {
        for (int i = 0; i < COUNT; i++) FRAMES[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/arc_burst/" + i + ".png");
    }
    private record Burst(int player, long started) { }
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static long received, drawn;
    private ClientRailgunHand() { }

    public static void receive(RailgunHandEffect effect) {
        received++;
        BURSTS.add(new Burst(effect.player(), Util.getMillis()));
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || BURSTS.isEmpty()) return;
        var client = Minecraft.getInstance();
        long now = Util.getMillis();
        BURSTS.removeIf(burst -> now - burst.started >= PER_FRAME * COUNT);
        if (client.level == null || BURSTS.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack(); float partial = event.getPartialTick();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
        try {
            for (var burst : BURSTS) {
                if (!(client.level.getEntity(burst.player) instanceof Player player)) continue;
                RenderSystem.setShaderTexture(0, FRAMES[(int) ((now - burst.started) / PER_FRAME)]);
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
                var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buffer.vertex(m, -1, -1, 0).uv(0, 1).endVertex();
                buffer.vertex(m, 1, -1, 0).uv(1, 1).endVertex();
                buffer.vertex(m, 1, 1, 0).uv(1, 0).endVertex();
                buffer.vertex(m, -1, 1, 0).uv(0, 0).endVertex();
                BufferUploader.drawWithShader(buffer.end());
                pose.popPose();
                drawn++;
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
    }
    @SubscribeEvent public static void railgunHandLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { BURSTS.clear(); }

    // テスト用。
    public static long received() { return received; }
    public static long drawn() { return drawn; }
    public static int active() { return BURSTS.size(); }
}
