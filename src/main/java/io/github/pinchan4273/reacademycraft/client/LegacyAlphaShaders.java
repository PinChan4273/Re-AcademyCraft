package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
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
    /**
     * ワールドの中の効果に使うposition-tex-colour。ふだんはalphaテストなしの版（positionTexColor）。
     * Oculusがシェーダーパックを使っている間は、Minecraft自身のものを返す: Oculusはそれだけをシェーダーパックのプログラムへ
     * 置き換えるので、独自の版で描いたものは画面に出ない（ShaderPackCompat）。その間、淡いtexelの扱いはシェーダーパックに従う。
     */
    @Nullable public static ShaderInstance worldPositionTexColor() {
        return ShaderPackCompat.inUse() ? GameRenderer.getPositionTexColorShader() : positionTexColor;
    }
    /** Minecraftのparticleのシェーダー（alphaテストなし）。 */
    @Nullable public static ShaderInstance particle() { return particle; }
    /**
     * ワールドの粒子に使うparticleのシェーダー。ふだんはalphaテストなしの版。Oculusがシェーダーパックを使っている間は、独自の版では
     * 何も書かれないので（ShaderPackCompat）、Minecraft自身のもの（Oculusがシェーダーパックのプログラムへ置き換える）を返す。
     */
    @Nullable public static ShaderInstance worldParticle() { return ShaderPackCompat.inUse() ? GameRenderer.getParticleShader() : particle; }

    /**
     * Minecraftの半透明の粒子のシート（alphaテストなし）。
     * <p>
     * 1.5.2: 深度は書かない（読むだけ）。alphaテストが無いので、深度を書くと、透明な画素まで四角形全体が深度を埋め、後から描く
     * 半透明の粒子（別の煙など）に見えない四角い穴をあけていた。
     * 壁などの不透明な物には、今までどおり深度の比較で隠れる。
     */
    public static final ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType() {
        @Override public void begin(BufferBuilder buffer, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(LegacyAlphaShaders::worldParticle);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        // 次に描く物のために、深度を書く設定へ戻す（バニラの粒子のシートは begin でそれぞれ決め直すが、念のため）。
        @Override public void end(Tesselator tesselator) { tesselator.end(); RenderSystem.depthMask(true); }
        @Override public String toString() { return "ACADEMY_PARTICLE_SHEET_TRANSLUCENT_ALPHA_TEST_OFF"; }
    };
}
