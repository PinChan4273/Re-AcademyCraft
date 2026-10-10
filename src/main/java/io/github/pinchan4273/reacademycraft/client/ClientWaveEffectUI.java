package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.skill.VecDeviation;
import io.github.pinchan4273.reacademycraft.skill.VecReflection;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作WaveEffectUI: Vector Deviation（最大alpha 0.2、大きさ100、毎秒1.4個）またはVector Reflection（0.4、110、1.6）が
 * ONの間、術者の画面にglow_circleの波紋を出す。1.5〜2.5秒の寿命の最初の5分の1でフェードインし、保ち、後半でフェードアウトし、
 * 毎秒20ずつ大きくなる。原作の癖を2つ残している: vm_wave.glslは波紋をclip空間のpos / screenSize - 0.5に置くので、
 * 波紋は画面の中央の半分に、半分の大きさで出る。また原作は照準のoverlayのPreとPostの両方で処理していたので、
 * 1フレームに2回更新し（2回目はほとんど時間が進まない）、すべての波紋を2回描く。
 * 原作はLambdaLib2のRenderPassで描き、そのRenderStatesはalphaテストをGL_ALWAYSにする（vm_wave.glslは指定せず、
 * WaveEffectUIの後のGL_GREATER 0.1はMinecraftの設定へ戻すためのもの）。そのため、淡いtexelも捨てられなかった。
 * Minecraft 1.20.1のposition_tex_colorはalpha 0.1未満を捨て、波紋の柔らかい縁が段のある輪郭で切れ、フェードも短くなる。
 * そこで、alpha 0だけを捨てるシェーダー（LegacyAlphaShaders）で描く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientWaveEffectUI {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_circle.png");
    /**
     * 原作は、これらの波紋をglow_circleの専用の写しで、バイリニア・CLAMPで描いた（LambdaLib2 Texture2D.loadの
     * FilterMode.BlinearとWrapMode.Clamp）。一方、ワールドの輪（ClientVmWaves）はMinecraftの補間なしのテクスチャを使う。
     * 補間なしで描くと、GUIの倍率による大きさでtexelが見え、縁が段になる。同じ画像を別のテクスチャとして読み込むので、
     * ワールドの輪は元の設定のまま。resourceの再読込でも、Minecraftが補間ありのまま読み直す。
     */
    public static final ResourceLocation UI_TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "wave_effect_ui/glow_circle");
    static final class BilinearTexture extends SimpleTexture {
        BilinearTexture() { super(TEXTURE); }
        @Override protected TextureImage getTextureImage(ResourceManager manager) {
            var image = TextureImage.load(manager, location);
            try { return new TextureImage(new TextureMetadataSection(true, true), image.getImage()); }
            catch (java.io.IOException missing) { return image; }
        }
    }
    static ResourceLocation uiTexture() {
        var textures = Minecraft.getInstance().getTextureManager();
        if (textures.getTexture(UI_TEXTURE, null) == null) textures.register(UI_TEXTURE, new BilinearTexture());
        return UI_TEXTURE;
    }

    static final class Ui {
        final float maxAlpha, avgSize, intensity;
        final List<float[]> ripples = new ArrayList<>(); // 寿命、x、y、経過時間、大きさ
        double lastFrame = now();
        Ui(float maxAlpha, float avgSize, float intensity) { this.maxAlpha = maxAlpha; this.avgSize = avgSize; this.intensity = intensity; }
        void frame(GuiGraphics g, float width, float height, RandomSource random) {
            double time = now(), delta = time - lastFrame;
            for (Iterator<float[]> it = ripples.iterator(); it.hasNext(); ) {
                var r = it.next(); r[3] += (float) delta;
                if (r[3] >= r[0]) it.remove();
            }
            if (random.nextFloat() < delta * intensity)
                ripples.add(new float[] {1.5f + random.nextFloat(), random.nextFloat() * width, random.nextFloat() * height, 0,
                        (.8f + random.nextFloat() * .4f) * avgSize});
            draw(g, width, height);
            lastFrame = time;
        }
        static float alpha(float[] r) {
            float progress = r[3] / r[0];
            return progress < .2f ? progress / .2f : progress < .5f ? 1 : 1 - (progress - .5f) / .5f;
        }
        void draw(GuiGraphics g, float width, float height) { draw(g, width, height, uiTexture(), LegacyAlphaShaders::positionTexColor); }
        void draw(GuiGraphics g, float width, float height, ResourceLocation texture, Supplier<ShaderInstance> shader) {
            if (ripples.isEmpty()) return;
            RenderSystem.setShader(shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest();
            var m = g.pose().last().pose(); var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (var r : ripples) {
                float size = r[4] + r[3] * 20, a = maxAlpha * alpha(r);
                // clipのx = x / w - 0.5は、画面ではx / 2 + w / 4に当たる。一辺はsize / 2。
                float cx = r[1] / 2 + width / 4, cy = height * .75f - r[2] / 2, half = size / 4;
                // vm_waveは、texel全体（色も含む）にalphaを掛ける。
                buffer.vertex(m, cx - half, cy - half, 0).uv(0, 0).color(a, a, a, a).endVertex();
                buffer.vertex(m, cx - half, cy + half, 0).uv(0, 1).color(a, a, a, a).endVertex();
                buffer.vertex(m, cx + half, cy + half, 0).uv(1, 1).color(a, a, a, a).endVertex();
                buffer.vertex(m, cx + half, cy - half, 0).uv(1, 0).color(a, a, a, a).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
            RenderSystem.enableDepthTest();
            drawn += ripples.size();
        }
    }
    @Nullable private static Ui deviation, reflection;
    private static long drawn;
    private static final RandomSource RANDOM = RandomSource.create();
    private ClientWaveEffectUI() { }
    static double now() { return Util.getMillis() / 1000.0; }

    private static void frame(GuiGraphics g, float width, float height) {
        // contextの画面の表示は、モードがONの間だけ続く。次に発動したときは新しく始める。
        if (ClientSkillModes.active(VecDeviation.ID)) { if (deviation == null) deviation = new Ui(.2f, 100, 1.4f); deviation.frame(g, width, height, RANDOM); }
        else deviation = null;
        if (ClientSkillModes.active(VecReflection.ID)) { if (reflection == null) reflection = new Ui(.4f, 110, 1.6f); reflection.frame(g, width, height, RANDOM); }
        else reflection = null;
    }
    @SubscribeEvent public static void pre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()))
            frame(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }
    @SubscribeEvent public static void post(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()))
            frame(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }
    /** テスト用の入口。 */
    public static long drawnRipples() { return drawn; }
    /**
     * 指定した波紋（それぞれ寿命、x、y、経過時間、大きさ）を、この最大alphaのモードと同じく、指定したテクスチャで1回描く。
     * 実際の乱数の波紋とは別の、画像を比べるための固定の場面。
     */
    public static void drawForTest(GuiGraphics g, float width, float height, float maxAlpha, ResourceLocation texture, List<float[]> ripples) {
        drawForTest(g, width, height, maxAlpha, texture, LegacyAlphaShaders::positionTexColor, ripples);
    }
    /** 同じことを、指定したシェーダーで描く。Minecraftのposition_tex_colorを渡すと、alpha 0.1未満を捨てる描き方になる（比較用）。 */
    public static void drawForTest(GuiGraphics g, float width, float height, float maxAlpha, ResourceLocation texture,
                                   Supplier<ShaderInstance> shader, List<float[]> ripples) {
        var ui = new Ui(maxAlpha, 0, 0);
        for (var r : ripples) ui.ripples.add(r.clone());
        ui.draw(g, width, height, texture == null ? uiTexture() : texture, shader);
    }
    public static int liveRipples() {
        return (deviation == null ? 0 : deviation.ripples.size()) + (reflection == null ? 0 : reflection.ripples.size());
    }
}
