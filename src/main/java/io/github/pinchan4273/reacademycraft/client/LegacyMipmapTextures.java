package io.github.pinchan4273.reacademycraft.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;


/**
 * 原作ClientResources.preloadMipmapTexture（AcademyCraft 7b1401cd）: これらのテクスチャは、何かが描く前に、それぞれの名前で
 * MinecraftのTextureManagerへ登録されていた。Minecraft既定のnearest・繰り返しではなく、完全なmipmap列を持つtrilinear
 * （glGenerateMipmap、GL_LINEAR_MIPMAP_LINEAR / GL_LINEAR）で、端はclampする。画面マスクは画面全体へ引き伸ばされ、
 * 開発機・教程・Aboutの絵は元画像より大きくも小さくも描かれるので、nearestでは縁が段になる。同じ画像を同じ名前で登録するので、
 * これを描くすべての箇所が原作と同じテクスチャを使う。1.20のcore profileにGL_CLAMPは無いため、CLAMP_TO_EDGEとする。
 * リソース再読込のたびに登録する: 固定の15枚で、それぞれが前のものを置き換える（そして解放する）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class LegacyMipmapTextures {
    /** 原作のpreloadMipmapTexture呼び出しすべて: BackgroundMask、SkillTree、GuiTutorial、AppAbout、AppTutorial。 */
    public static final List<Identifier> TEXTURES = List.of("effects/screen_mask",
            "guis/developer/skill_back", "guis/developer/skill_radial_mask", "guis/developer/skill_outline", "guis/developer/line",
            "guis/developer/skill_view_outline", "guis/developer/skill_view_outline_glow",
            "guis/tutorial/logo0", "guis/tutorial/logo1", "guis/tutorial/logo2", "guis/tutorial/logo3",
            "guis/about/bg", "guis/apps/tutorial/icon_0", "guis/apps/tutorial/icon_1", "guis/apps/tutorial/icon_2")
            .stream().map(path -> Identifier.fromNamespaceAndPath("academy", "textures/" + path + ".png")).toList();
    private LegacyMipmapTextures() { }

    static final class Trilinear extends SimpleTexture {
        Trilinear(Identifier location) { super(location); }
        @Override public void apply(net.minecraft.client.renderer.texture.TextureContents contents) {
            super.apply(contents);
            sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE, com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR, com.mojang.blaze3d.textures.FilterMode.LINEAR, true);
        }
        @Override protected void doLoad(com.mojang.blaze3d.platform.NativeImage image) {
            var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
            close();
            int width = image.getWidth(), height = image.getHeight();
            // 原作のglGenerateMipmapは長い辺のfloor(log2)段まで作った。26.2 のテクスチャの段は辺を半分にし続けて 0 になると書けないので、短い辺の段までにする（これより小さく描くことは無い）。
            int levels = 31 - Integer.numberOfLeadingZeros(Math.min(width, height));
            // 用途: 書き込み・サンプル・読み出し（7）。読み出し（COPY_SRC）は試験が各段を読み返すため（1.21.1 の glGetTexImage）。描き方は変わらない。
            texture = device.createTexture(resourceId()::toString, 7, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, width, height, 1, levels + 1);
            textureView = device.createTextureView(texture);
            var encoder = device.createCommandEncoder();
            encoder.writeToTexture(texture, image);
            var previous = image;
            for (int level = 1; level <= levels; level++) {
                var next = halve(previous);
                encoder.writeToTexture(texture, next, level, 0, 0, 0);
                if (previous != image) previous.close();
                previous = next;
            }
            if (previous != image) previous.close();
        }
        /** glGenerateMipmap を使わず、自前で箱フィルターで縮小する（バックエンドに依存しない）。奇数の辺は端の画素の重みをそのまま平均する。 */
        private static com.mojang.blaze3d.platform.NativeImage halve(com.mojang.blaze3d.platform.NativeImage from) {
            int w = Math.max(1, from.getWidth() / 2), h = Math.max(1, from.getHeight() / 2);
            var to = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int y1 = Math.min(from.getHeight() - 1, y * 2 + 1), x1 = Math.min(from.getWidth() - 1, x * 2 + 1);
                int a = 0, r = 0, g = 0, b = 0;
                for (int sy : new int[] {y * 2, y1}) for (int sx : new int[] {x * 2, x1}) {
                    int c = from.getPixel(sx, sy);
                    a += c >>> 24; r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
                }
                to.setPixel(x, y, (a + 2) / 4 << 24 | (r + 2) / 4 << 16 | (g + 2) / 4 << 8 | (b + 2) / 4);
            }
            return to;
        }
    }

    static void register() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (var id : TEXTURES) textures.registerAndLoad(id, new Trilinear(id));
    }
    @SubscribeEvent public static void legacyMipmapReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "legacy_mipmap_textures"), (ResourceManagerReloadListener) manager -> register());
    }
}
