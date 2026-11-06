package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.skill.VecDeviation;
import io.github.pinchan4273.reacademycraft.skill.VecReflection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;

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
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientWaveEffectUI {
    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("academy", "textures/effects/glow_circle.png");
    /**
     * 原作は、これらの波紋をglow_circleの専用の写しで、バイリニア・CLAMPで描いた（LambdaLib2 Texture2D.loadの
     * FilterMode.BlinearとWrapMode.Clamp）。一方、ワールドの輪（ClientVmWaves）はMinecraftの補間なしのテクスチャを使う。
     * 補間なしで描くと、GUIの倍率による大きさでtexelが見え、縁が段になる。同じ画像を別のテクスチャとして読み込むので、
     * ワールドの輪は元の設定のまま。resourceの再読込でも、Minecraftが補間ありのまま読み直す。
     */
    public static final Identifier UI_TEXTURE = Identifier.fromNamespaceAndPath("academy", "wave_effect_ui/glow_circle");
    static final class BilinearTexture extends SimpleTexture {
        BilinearTexture() { super(TEXTURE); }
        @Override public net.minecraft.client.renderer.texture.TextureContents loadContents(ResourceManager manager) throws java.io.IOException {
            var contents = super.loadContents(manager);
            return new net.minecraft.client.renderer.texture.TextureContents(contents.image(),
                    new TextureMetadataSection(true, true, net.minecraft.client.renderer.texture.MipmapStrategy.AUTO, 0));
        }
    }
    private static boolean uiTextureRegistered;
    public static Identifier uiTexture() {
        var textures = Minecraft.getInstance().getTextureManager();
        if (!uiTextureRegistered) { textures.registerAndLoad(UI_TEXTURE, new BilinearTexture()); uiTextureRegistered = true; }
        return UI_TEXTURE;
    }

    static final class Ui {
        final float maxAlpha, avgSize, intensity;
        final List<float[]> ripples = new ArrayList<>(); // 寿命、x、y、経過時間、大きさ
        double lastFrame = now();
        Ui(float maxAlpha, float avgSize, float intensity) { this.maxAlpha = maxAlpha; this.avgSize = avgSize; this.intensity = intensity; }
        void frame(GuiGraphicsExtractor g, float width, float height, RandomSource random) {
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
        void draw(GuiGraphicsExtractor g, float width, float height) { draw(g, width, height, uiTexture(), io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.Shader.TEX_COLOR_KEEP); }
        void draw(GuiGraphicsExtractor g, float width, float height, Identifier texture, io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.Shader shader) {
            if (ripples.isEmpty()) return;
            io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShader(shader);
            
            io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShaderTexture(0, texture);
            
            var m = g.pose(); 
            var buffer = io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.begin(g);
            for (var r : ripples) {
                float size = r[4] + r[3] * 20, a = maxAlpha * alpha(r);
                // clipのx = x / w - 0.5は、画面ではx / 2 + w / 4に当たる。一辺はsize / 2。
                float cx = r[1] / 2 + width / 4, cy = height * .75f - r[2] / 2, half = size / 4;
                // vm_waveは、texel全体（色も含む）にalphaを掛ける。
                buffer.addVertex(m, cx - half, cy - half, 0).setUv(0, 0).setColor(a, a, a, a);
                buffer.addVertex(m, cx - half, cy + half, 0).setUv(0, 1).setColor(a, a, a, a);
                buffer.addVertex(m, cx + half, cy + half, 0).setUv(1, 1).setColor(a, a, a, a);
                buffer.addVertex(m, cx + half, cy - half, 0).setUv(1, 0).setColor(a, a, a, a);
            }
            buffer.end();
            
            drawn += ripples.size();
        }
    }
    @Nullable private static Ui deviation, reflection;
    private static long drawn;
    private static final RandomSource RANDOM = RandomSource.create();
    private ClientWaveEffectUI() { }
    static double now() { return Util.getMillis() / 1000.0; }

    private static void frame(GuiGraphicsExtractor g, float width, float height) {
        // contextの画面の表示は、モードがONの間だけ続く。次に発動したときは新しく始める。
        if (ClientSkillModes.active(VecDeviation.ID)) { if (deviation == null) deviation = new Ui(.2f, 100, 1.4f); deviation.frame(g, width, height, RANDOM); }
        else deviation = null;
        if (ClientSkillModes.active(VecReflection.ID)) { if (reflection == null) reflection = new Ui(.4f, 110, 1.6f); reflection.frame(g, width, height, RANDOM); }
        else reflection = null;
    }
    // NeoForge 21.1: HUDの重ね描きはレイヤー（Forge版はRenderGuiOverlayEventとVanillaGuiOverlay）。
    @SubscribeEvent public static void pre(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR))
            frame(event.getGuiGraphics(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }
    @SubscribeEvent public static void post(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR))
            frame(event.getGuiGraphics(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }
    /** テスト用の入口。 */
    public static long drawnRipples() { return drawn; }
    /**
     * 指定した波紋（それぞれ寿命、x、y、経過時間、大きさ）を、この最大alphaのモードと同じく、指定したテクスチャで1回描く。
     * 実際の乱数の波紋とは別の、画像を比べるための固定の場面。
     */
    public static void drawForTest(GuiGraphicsExtractor g, float width, float height, float maxAlpha, Identifier texture, List<float[]> ripples) {
        drawForTest(g, width, height, maxAlpha, texture, io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.Shader.TEX_COLOR_KEEP, ripples);
    }
    /** 同じことを、指定したシェーダーで描く。Minecraftのposition_tex_colorを渡すと、alpha 0.1未満を捨てる描き方になる（比較用）。 */
    public static void drawForTest(GuiGraphicsExtractor g, float width, float height, float maxAlpha, Identifier texture,
                                   io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.Shader shader, List<float[]> ripples) {
        var ui = new Ui(maxAlpha, 0, 0);
        for (var r : ripples) ui.ripples.add(r.clone());
        ui.draw(g, width, height, texture == null ? uiTexture() : texture, shader);
    }
    public static int liveRipples() {
        return (deviation == null ? 0 : deviation.ripples.size()) + (reflection == null ? 0 : reflection.ripples.size());
    }
}
