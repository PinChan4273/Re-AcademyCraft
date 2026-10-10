package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RenderImagPhaseLiquid: 液体の各ブロックの上に、effects/imag_proj_liquidを3枚重ねる。高さは
 * 1.2 * sqrt(液体の高さ)倍で、テクスチャはそれぞれの速さで流れる。完全に明るく、1 / (1 + 0.2 * 距離)で薄れ、
 * 0.1未満では描かない。深度テスト・深度書き込み・カリングはいずれも無し。TileImagPhaseはpass 1、つまり
 * 半透明ブロック（液体自身を含む）の後に描かれるので、層は黒い液体の上に見える。ここでも同じ段階で描く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PhaseLiquidRenderer {
    private static final RenderType[] TYPES = new RenderType[3];
    static {
        for (int i = 0; i < 3; i++)
            TYPES[i] = Types.layer(ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/imag_proj_liquid/" + i + ".png"));
    }
    /** drawLayerの引数: テクスチャ、高さの係数、毎秒のu・v方向の流れ、繰り返し回数。 */
    private static final double[][] LAYER = {{0, -.3, .3, .2, .7}, {1, .35, .3, .05, .7}, {2, .7, .1, .25, .7}};
    private static final long START = Util.getMillis();
    private static final AtomicLong DRAWN = new AtomicLong();
    private static final Set<PhaseLiquidBlock.Tile> TILES = Collections.newSetFromMap(new WeakHashMap<>());
    private PhaseLiquidRenderer() { }
    public static void track(PhaseLiquidBlock.Tile tile) { TILES.add(tile); }
    /** 描いたブロック数（テスト用）。実際の見え方は画像で判定する。 */
    public static long drawn() { return DRAWN.get(); }
    /** 距離による減衰。0.1未満の値なら原作は描かない。 */
    public static double alpha(double distance) { return 1 / (1 + .2 * distance); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TILES.isEmpty()) return;
        var client = Minecraft.getInstance(); var level = client.level; var player = client.player;
        if (level == null || player == null) return;
        TILES.removeIf(tile -> tile.isRemoved() || tile.getLevel() != level);
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = client.renderBuffers().bufferSource();
        double time = (Util.getMillis() - START) / 1000.0;
        for (var tile : TILES) {
            var at = tile.getBlockPos();
            double alpha = alpha(Math.sqrt(player.distanceToSqr(at.getX() + .5, at.getY() + .5, at.getZ() + .5)));
            if (alpha < .1) continue;
            // 原作は高さを液体の描画処理に問い合わせる。ここでは液体の状態自身の高さが同じものになる。
            double height = 1.2 * Math.sqrt(level.getFluidState(at).getOwnHeight());
            pose.pushPose();
            pose.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
            var m = pose.last().pose(); float a = (float) alpha;
            for (var layer : LAYER) {
                int index = (int) layer[0];
                if (index == 2 && height <= .5) continue;
                double y = layer[1] * height, du = (time * layer[2]) % 1, dv = (time * layer[3]) % 1, density = layer[4];
                var consumer = buffers.getBuffer(TYPES[index]);
                vertex(consumer, m, 0, y, 0, du, dv, a);
                vertex(consumer, m, 1, y, 0, du + density, dv, a);
                vertex(consumer, m, 1, y, 1, du + density, dv + density, a);
                vertex(consumer, m, 0, y, 1, du, dv + density, a);
            }
            pose.popPose();
            DRAWN.incrementAndGet();
        }
        for (var type : TYPES) buffers.endBatch(type);
    }
    private static void vertex(VertexConsumer consumer, org.joml.Matrix4f m, double x, double y, double z, double u, double v, float a) {
        consumer.vertex(m, (float) x, (float) y, (float) z).uv((float) u, (float) v).color(1f, 1f, 1f, a).endVertex();
    }

    /**
     * 原作のGL状態を持つRenderType: ブレンドあり、カリングなし、深度書き込みなし、深度テストなし。1.20.1の
     * NO_DEPTH_TEST shardは何も設定しないので、layering shardで深度テストを自分で切る。
     */
    private static final class Types extends RenderType {
        private Types() { super("", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 0, false, false, () -> { }, () -> { }); }
        static RenderType layer(ResourceLocation texture) {
            return create("academy_imag_proj_liquid", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLayeringState(new LayeringStateShard("academy_no_depth_test",
                                    RenderSystem::disableDepthTest, RenderSystem::enableDepthTest))
                            .createCompositeState(false));
        }
    }
}
