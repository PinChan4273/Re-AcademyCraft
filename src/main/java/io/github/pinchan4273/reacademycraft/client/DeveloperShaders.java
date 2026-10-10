package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 開発機の技能の木で使う原作の2つのシェーダーを、core shaderとして登録する: skill_progbar（熟練度の輪。skill_radial_maskで切る）と、
 * LambdaLib2のShaderMono（未習得の技能を灰色で描く）。どちらも位置・テクスチャ・色を受け取る。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeveloperShaders {
    @Nullable private static ShaderInstance progbar, mono, plasmaBody;
    private DeveloperShaders() { }

    @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "skill_progbar"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> progbar = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "mono"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> mono = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "plasma_body"),
                DefaultVertexFormat.POSITION), shader -> plasmaBody = shader);
    }
    @Nullable public static ShaderInstance progbar() { return progbar; }
    @Nullable public static ShaderInstance mono() { return mono; }
    /** 原作plasma_body: Plasma Cannonのmetaball。 */
    @Nullable public static ShaderInstance plasmaBody() { return plasmaBody; }
}
