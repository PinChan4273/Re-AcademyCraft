package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作は、一部の効果をalphaテストなし（glDisable(GL_ALPHA_TEST)）で描いた。Storm WingとPlasma Cannonの竜巻の輪、
 * Vector Accelerationの放物線、Jet Engineの菱形の盾、Ground Shockの煙。そのため、淡いtexelもフェードもすべて見えていた。
 * Minecraftのposition-tex-colourとparticleのシェーダーは、alpha 0.1未満の画素をすべて捨てるので、それらのtexelが欠け、
 * フェードが早く終わる。ここで登録するのは、同じ2つのシェーダーの、完全に透明な画素だけを捨てる版。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LegacyAlphaShaders {
    @Nullable private static ShaderInstance positionTexColor, particle;
    private LegacyAlphaShaders() { }

    @SubscribeEvent public static void registerLegacyAlphaShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "position_tex_color_keep"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> positionTexColor = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "particle_keep"),
                DefaultVertexFormat.PARTICLE), shader -> particle = shader);
    }
    /** position-tex-colour（alphaテストなし）。 */
    @Nullable public static ShaderInstance positionTexColor() { return positionTexColor; }
    /** Minecraftのparticleのシェーダー（alphaテストなし）。 */
    @Nullable public static ShaderInstance particle() { return particle; }

    /** Minecraftの半透明の粒子のシート（alphaテストなし）。 */
    public static final ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType() {
        @Override public void begin(BufferBuilder buffer, TextureManager textures) {
            RenderSystem.depthMask(true);
            RenderSystem.setShader(LegacyAlphaShaders::particle);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        @Override public void end(Tesselator tesselator) { tesselator.end(); }
        @Override public String toString() { return "ACADEMY_PARTICLE_SHEET_TRANSLUCENT_ALPHA_TEST_OFF"; }
    };
}
