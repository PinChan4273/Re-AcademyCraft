package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 現行のbake済みOBJアダプタ。WeAthFolDの原作のルート変換と、毎秒60度のファンの動きを持つ。
 * モデル・テクスチャの作者は原作側。画面はTechUIの風力発電機の画面で、原作は音を鳴らさない。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WindGeneratorRenderer extends LegacyBlockEntityRenderer<WindBodyBlockEntity> {
    /** Forge 1.20.1版はブロックエンティティのgetRenderBoundingBoxだった。 */
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(WindBodyBlockEntity body) { return body.renderBoundingBox(); }
    public static final Identifier BASE = model("windgen_base_render"), BASE_DISABLED = model("windgen_base_disabled_render"),
            MAIN = model("windgen_main_render"), FAN = model("windgen_fan_render");
    public WindGeneratorRenderer(BlockEntityRendererProvider.Context context) { }
    private static Identifier model(String name) { return Identifier.fromNamespaceAndPath("academy", "block/" + name); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AcademyContent.WIND_BODY_ENTITY.get(), WindGeneratorRenderer::new);
    }
    /** 位相はフレーム数に依存せず範囲内に収める。アニメーションの状態は正となるNBTに入れない。 */
    public static float angle(long gameTime, float partialTick) {
        return (Math.floorMod(gameTime, 120) + Math.max(0, Math.min(1, partialTick))) * 3f;
    }
    @Override protected void render(WindBodyBlockEntity tile, float partialTick, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
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
    private void draw(Identifier id, PoseStack pose, SubmitNodeCollector buffers, int light, int overlay) {
        // 毎回現在のbake済みモデルを引く。リソース再読込で古いspriteが残らないようにするため。
        ObjModels.draw(id, RenderTypes.cutoutMovingBlock(), pose, buffers, light, overlay);
    }
    @Override public int getViewDistance() { return 96; }
}
