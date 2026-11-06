package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/** 機械と実体の描画が使う、ブロックの位置に属さないモデル（1.21.1 の ModelResourceLocation.standalone に当たる）。 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ObjModels {
    private static final List<String> IDS = List.of("entity/maghook", "entity/maghook_open", "entity/silbarn", "item/developer_portable_render",
            "block/windgen_base_render", "block/windgen_base_disabled_render", "block/windgen_main_render", "block/windgen_fan_render",
            "block/matrix_body", "block/matrix_shield", "block/developer_normal_render", "block/developer_advanced_render",
            "block/ip_gen0", "block/ip_gen1", "block/ip_gen2", "block/ip_gen3", "block/ip_gen4", "block/solar_render");
    private static final Map<Identifier, StandaloneModelKey<BlockStateModelPart>> KEYS = new HashMap<>();
    static {
        for (String id : IDS) {
            var location = Identifier.fromNamespaceAndPath("academy", id);
            KEYS.put(location, new StandaloneModelKey<>(location::toString));
        }
    }
    private static final Direction[] DIRECTIONS = Direction.values();

    private ObjModels() { }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterStandalone event) {
        KEYS.forEach((id, key) -> event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(id)));
    }

    /** ブロックの図集のテクスチャで描くモデル（1.21.1 の、毎回今の bake 済みモデルを引いて四角形を送る処理）。描いた四角形の数を返す。 */
    public static int draw(Identifier id, RenderType type, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        var part = Minecraft.getInstance().getModelManager().getStandaloneModel(KEYS.get(id));
        if (part == null) return 0;
        int[] count = {part.getQuads(null).size()};
        for (var direction : DIRECTIONS) count[0] += part.getQuads(direction).size();
        collector.submitCustomGeometry(pose, type, (last, buffer) -> {
            var instance = new QuadInstance();
            instance.setLightCoords(light);
            instance.setOverlayCoords(overlay);
            for (var quad : part.getQuads(null)) buffer.putBakedQuad(last, quad, instance);
            for (var direction : DIRECTIONS) for (var quad : part.getQuads(direction)) buffer.putBakedQuad(last, quad, instance);
        });
        return count[0];
    }

    public static Identifier atlas() { return TextureAtlas.LOCATION_BLOCKS; }
}
