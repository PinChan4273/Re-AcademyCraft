package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * 原作ClientResources.preloadMipmapTexture（AcademyCraft 7b1401cd）: これらのテクスチャは、何かが描く前に、それぞれの名前で
 * MinecraftのTextureManagerへ登録されていた。Minecraft既定のnearest・繰り返しではなく、完全なmipmap列を持つtrilinear
 * （glGenerateMipmap、GL_LINEAR_MIPMAP_LINEAR / GL_LINEAR）で、端はclampする。画面マスクは画面全体へ引き伸ばされ、
 * 開発機・教程・Aboutの絵は元画像より大きくも小さくも描かれるので、nearestでは縁が段になる。同じ画像を同じ名前で登録するので、
 * これを描くすべての箇所が原作と同じテクスチャを使う。1.20のcore profileにGL_CLAMPは無いため、CLAMP_TO_EDGEとする。
 * リソース再読込のたびに登録する: 固定の15枚で、それぞれが前のものを置き換える（そして解放する）。
 */
@Mod.EventBusSubscriber(modid = "academy", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LegacyMipmapTextures {
    /** 原作のpreloadMipmapTexture呼び出しすべて: BackgroundMask、SkillTree、GuiTutorial、AppAbout、AppTutorial。 */
    public static final List<ResourceLocation> TEXTURES = List.of("effects/screen_mask",
            "guis/developer/skill_back", "guis/developer/skill_radial_mask", "guis/developer/skill_outline", "guis/developer/line",
            "guis/developer/skill_view_outline", "guis/developer/skill_view_outline_glow",
            "guis/tutorial/logo0", "guis/tutorial/logo1", "guis/tutorial/logo2", "guis/tutorial/logo3",
            "guis/about/bg", "guis/apps/tutorial/icon_0", "guis/apps/tutorial/icon_1", "guis/apps/tutorial/icon_2")
            .stream().map(path -> ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png")).toList();
    private LegacyMipmapTextures() { }

    static final class Trilinear extends SimpleTexture {
        Trilinear(ResourceLocation location) { super(location); }
        @Override public void load(ResourceManager manager) throws IOException {
            var image = getTextureImage(manager);
            image.throwIfError();
            NativeImage pixels = image.getImage();
            if (!RenderSystem.isOnRenderThreadOrInit()) RenderSystem.recordRenderCall(() -> upload(pixels));
            else upload(pixels);
        }
        private void upload(NativeImage pixels) {
            int width = pixels.getWidth(), height = pixels.getHeight();
            // 原作の呼び出しが作ったglGenerateMipmapの全段: 各段は前段の半分、辺は1ピクセル未満にならず、1x1まで続く
            // （長い辺のfloor(log2)段）。TextureUtil.prepareImageは段iを幅>>i・高さ>>iで確保するため、短い辺が先に0になる
            // （899x236では8段目）。そこでprepareImageと同じ方法で確保しつつ、1ピクセル未満にはしない。
            int levels = 31 - Integer.numberOfLeadingZeros(Math.max(width, height));
            GlStateManager._bindTexture(getId());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, levels);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0f);
            for (int level = 0; level <= levels; level++)
                GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, Math.max(1, width >> level), Math.max(1, height >> level),
                        0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
            pixels.upload(0, 0, 0, 0, 0, width, height, true, true, levels > 0, true);
            GlStateManager._bindTexture(getId());
            if (levels > 0) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
    }

    static void register() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (var id : TEXTURES) textures.register(id, new Trilinear(id));
    }
    @SubscribeEvent public static void legacyMipmapReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> register());
    }
}
