package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RenderImagPhaseLiquid: 液体の各ブロックの上に、effects/imag_proj_liquidを3枚重ねる。高さは
 * 1.2 * sqrt(液体の高さ)倍で、テクスチャはそれぞれの速さで流れる。完全に明るく、1 / (1 + 0.2 * 距離)で薄れ、
 * 0.1未満では描かない。深度テスト・深度書き込み・カリングはいずれも無し。TileImagPhaseはpass 1、つまり
 * 半透明ブロック（液体自身を含む）の後に描かれるので、層は黒い液体の上に見える。ここでも同じ段階で描く。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PhaseLiquidRenderer {
    private static final Identifier[] TYPES = new Identifier[3];
    static {
        for (int i = 0; i < 3; i++)
            TYPES[i] = Identifier.fromNamespaceAndPath("academy", "textures/effects/imag_proj_liquid/" + i + ".png");
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

    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent extract) { io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.on(extract, io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.Stage.AFTER_TRANSLUCENT_TERRAIN, PhaseLiquidRenderer::renderFrame); }
    private static void renderFrame(io.github.pinchan4273.reacademycraft.client.render.WorldFrame event) {
        if (TILES.isEmpty()) return;
        var client = Minecraft.getInstance(); var level = client.level; var player = client.player;
        if (level == null || player == null) return;
        TILES.removeIf(tile -> tile.isRemoved() || tile.getLevel() != level);
        var camera = event.getCamera().position();
        var pose = event.getPoseStack();
        // 原作のGL状態: ブレンドあり、カリングなし、深度書き込みなし、深度テストなし。
        io.github.pinchan4273.reacademycraft.client.render.Immediate.setShader(io.github.pinchan4273.reacademycraft.client.render.Immediate.Shader.TEX_COLOR_CUT);io.github.pinchan4273.reacademycraft.client.render.Immediate.enableBlend();io.github.pinchan4273.reacademycraft.client.render.Immediate.disableCull();io.github.pinchan4273.reacademycraft.client.render.Immediate.disableDepthTest();io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(false);
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
                io.github.pinchan4273.reacademycraft.client.render.Immediate.setShaderTexture(0, TYPES[index]);
                var consumer = io.github.pinchan4273.reacademycraft.client.render.Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
                vertex(consumer, m, 0, y, 0, du, dv, a);
                vertex(consumer, m, 1, y, 0, du + density, dv, a);
                vertex(consumer, m, 1, y, 1, du + density, dv + density, a);
                vertex(consumer, m, 0, y, 1, du, dv + density, a);
                consumer.end();
            }
            pose.popPose();
            DRAWN.incrementAndGet();
        }
    }
    private static void vertex(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices consumer, org.joml.Matrix4f m, double x, double y, double z, double u, double v, float a) {
        consumer.addVertex(m, (float) x, (float) y, (float) z).setUv((float) u, (float) v).setColor(1f, 1f, 1f, a);
    }

}
