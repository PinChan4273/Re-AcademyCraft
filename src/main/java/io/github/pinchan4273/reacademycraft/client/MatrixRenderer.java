package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock;
import io.github.pinchan4273.reacademycraft.world.block.WirelessMatrixBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作RenderMatrix（RenderBlockMulti経由）: 構造物の中央（pivotのoffsetに、置いた向きの回転中心(1, 0, 1)を足した位置）で、
 * BlockMultiのdrMapで回し、原作のmatrixモデルのMainとCoreを描く。板3枚とコアが入っている間はShieldを3回描く:
 * 毎秒50度で回り、互いに1/3回転ずれ、それぞれsin(t * 1.111 + 40i)で0.1上下する。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MatrixRenderer implements BlockEntityRenderer<WirelessMatrixBlockEntity> {
    public static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath("academy", "block/matrix_body"),
            SHIELD = ResourceLocation.fromNamespaceAndPath("academy", "block/matrix_shield");
    private static final RandomSource RANDOM = RandomSource.create(42);
    private static final long START = Util.getMillis();
    private static final AtomicLong BODIES = new AtomicLong(), SHIELDS = new AtomicLong();
    public MatrixRenderer(BlockEntityRendererProvider.Context context) { }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.MATRIX_ENTITY.get(), MatrixRenderer::new);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { event.register(BODY); event.register(SHIELD); }
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
    @Override public void render(WirelessMatrixBlockEntity tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
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
    private static void draw(ResourceLocation id, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var model = Minecraft.getInstance().getModelManager().getModel(id);
        var consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        for (int side = 0; side <= 6; side++) {
            RANDOM.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], RANDOM, ModelData.EMPTY, null))
                consumer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay, true);
        }
    }
    /** モデルは原点のブロックからはみ出し、残り7マスにかかる。 */
    @Override public boolean shouldRenderOffScreen(WirelessMatrixBlockEntity tile) { return true; }
    @Override public int getViewDistance() { return 96; }
}
