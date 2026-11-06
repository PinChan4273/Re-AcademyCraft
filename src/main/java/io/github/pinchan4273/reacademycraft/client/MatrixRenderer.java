package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock;
import io.github.pinchan4273.reacademycraft.world.block.WirelessMatrixBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作RenderMatrix（RenderBlockMulti経由）: 構造物の中央（pivotのoffsetに、置いた向きの回転中心(1, 0, 1)を足した位置）で、
 * BlockMultiのdrMapで回し、原作のmatrixモデルのMainとCoreを描く。板3枚とコアが入っている間はShieldを3回描く:
 * 毎秒50度で回り、互いに1/3回転ずれ、それぞれsin(t * 1.111 + 40i)で0.1上下する。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MatrixRenderer extends LegacyBlockEntityRenderer<WirelessMatrixBlockEntity> {
    public static final Identifier BODY = Identifier.fromNamespaceAndPath("academy", "block/matrix_body"),
            SHIELD = Identifier.fromNamespaceAndPath("academy", "block/matrix_shield");
    private static final long START = Util.getMillis();
    private static final AtomicLong BODIES = new AtomicLong(), SHIELDS = new AtomicLong();
    public MatrixRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.MATRIX_ENTITY.get(), MatrixRenderer::new);
    }
    /** 本体と盾の描画回数（テスト用）。実際の見え方は画像で判定する。 */
    public static long bodies() { return BODIES.get(); }
    public static long shields() { return SHIELDS.get(); }
    /** RenderBlockMulti: offsetMap[dir] + rotCenters[dir]。回転中心は(1, 0, 1)。 */
    public static double[] centre(Direction facing) {
        return switch (facing) {
            case SOUTH -> new double[] {0, 0};
            case WEST -> new double[] {1, 0};
            case EAST -> new double[] {0, 1};
            default -> new double[] {1, 1};
        };
    }
    @Override protected void render(WirelessMatrixBlockEntity tile, float partialTick, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        var state = tile.getBlockState();
        if (!state.hasProperty(WirelessMatrixBlock.FACING)) return;
        var facing = state.getValue(WirelessMatrixBlock.FACING);
        double[] c = centre(facing);
        pose.pushPose();
        pose.translate(c[0], 0, c[1]);
        // BlockMulti.drMap。太陽光発電機でも使っている。
        pose.mulPose(Axis.YP.rotationDegrees(SolarGeneratorBlock.legacyTurn(facing)));
        draw(BODY, pose, buffers, light, overlay);
        BODIES.incrementAndGet();
        if (tile.shieldsShown()) {
            double time = (Util.getMillis() - START) / 1000.0, phase = (time * 50) % 360;
            for (int i = 0; i < 3; i++) {
                pose.pushPose();
                pose.translate(0, .1 * Math.sin(time * 1.111 + 40.0 * i), 0);
                pose.mulPose(Axis.YP.rotationDegrees((float) (phase + 120 * i)));
                draw(SHIELD, pose, buffers, light, overlay);
                pose.popPose();
            }
            SHIELDS.incrementAndGet();
        }
        pose.popPose();
    }
    private static void draw(Identifier id, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        ObjModels.draw(id, RenderTypes.entityCutout(ObjModels.atlas()), pose, buffers, light, overlay);
    }
    /** モデルは原点のブロックからはみ出し、残り7マスにかかる。 */
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 96; }
}
