package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 現行のbake済みOBJアダプタ。WeAthFolDの原作のルート変換と、毎秒60度のファンの動きを持つ。
 * モデル・テクスチャの作者は原作側。画面はTechUIの風力発電機の画面で、原作は音を鳴らさない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WindGeneratorRenderer implements BlockEntityRenderer<WindBodyBlockEntity> {
    public static final ResourceLocation BASE = model("windgen_base_render"), BASE_DISABLED = model("windgen_base_disabled_render"),
            MAIN = model("windgen_main_render"), FAN = model("windgen_fan_render");
    private final RandomSource random = RandomSource.create(42);
    public WindGeneratorRenderer(BlockEntityRendererProvider.Context context) { }
    private static ResourceLocation model(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "block/" + name); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.WIND_BODY_ENTITY.get(), WindGeneratorRenderer::new);
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for (var model : new ResourceLocation[]{BASE, BASE_DISABLED, MAIN, FAN}) event.register(model);
    }
    /** 位相はフレーム数に依存せず範囲内に収める。アニメーションの状態は正となるNBTに入れない。 */
    public static float angle(long gameTime, float partialTick) {
        return (Math.floorMod(gameTime, 120) + Math.max(0, Math.min(1, partialTick))) * 3f;
    }
    @Override public void render(WindBodyBlockEntity tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = tile.getBlockState();
        if (tile.isRemoved() || tile.getLevel() == null || !(state.getBlock() instanceof WindBodyBlock block)
                || state.getValue(WindBodyBlock.PART) != 0) return;
        Direction facing = state.getValue(WindBodyBlock.FACING);
        boolean main = block.body() == WindBodyBlock.Body.MAIN;
        pose.pushPose();
        // RenderBlockMultiのpivotOffset + rotCenters。本体はZ方向0.4のpivotを使う。
        double x = .5, z = .5;
        if (main) {
            if (facing == Direction.NORTH) z = .4; else if (facing == Direction.SOUTH) z = .6;
            else if (facing == Direction.WEST) x = .4; else if (facing == Direction.EAST) x = .6;
        }
        // モデルは原作のOBJの座標のまま（tools/generate_wind_models.py）。原点はブロックの底面の中心。
        pose.translate(x, 0, z); pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        draw(main ? MAIN : tile.visualState().structureComplete() ? BASE : BASE_DISABLED, pose, buffers, light, overlay);
        if (main && tile.visualState().running()) {
            // 原作RenderWindGenMain: glTranslated(0, 0.5, 0.82)、Z軸の負の向きに回して、ファンを描く。
            pose.translate(0, .5, .82); pose.mulPose(Axis.ZN.rotationDegrees(angle(tile.getLevel().getGameTime(), partialTick)));
            draw(FAN, pose, buffers, light, overlay);
        }
        pose.popPose();
    }
    private void draw(ResourceLocation id, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // 毎回現在のbake済みモデルを引く。リソース再読込で古いspriteが残らないようにするため。
        var model = Minecraft.getInstance().getModelManager().getModel(id); var type = RenderType.cutout();
        var consumer = buffers.getBuffer(type);
        for (int side = 0; side <= 6; side++) {
            random.setSeed(42);
            for (var quad : model.getQuads(null, side == 6 ? null : Direction.values()[side], random, ModelData.EMPTY, type))
                consumer.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay, true);
        }
    }
    @Override public int getViewDistance() { return 96; }
}
