package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作BackgroundMask（AcademyCraft commit 7b1401c）: 能力がオンの間、effects/screen_mask（白で、中央は透明、
 * 端ほど濃い）をカテゴリの色で画面全体に重ねる。オーバーロード中は赤(208, 20, 20, 170)。色とアルファは毎秒1の速さで
 * 目標へ近づくので、マスクはフェードイン・アウトする。目標が無いときは色を保ち、アルファだけが下がる。
 * カテゴリの色は原作のsetColorStyleの値。Vector Manipulationはアルファが0なので、マスクは無い。
 *
 * 原作がこれを最初のほうに登録しているので、このmodの他のHUDより先に描く。
 *
 * 原作はアルファテストを切って描いていた（glDisable(GL_ALPHA_TEST)）。画像のアルファは最大77/255で、色のアルファを
 * 掛けるとほとんどが0.1未満になる（Meltdownerの80では全部）。position-tex-colourシェーダーは0.1未満の断片を
 * すべて捨てるため、マスクがほとんど見えず、Meltdownerでは全く見えなかった。position-texシェーダーで描き、色を
 * シェーダーの色として渡すことで、原作の固定パイプラインと同じくテクセルに色を掛け、完全に透明なテクセルだけを飛ばす。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ScreenMaskHud {
    private static final ResourceLocation MASK = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/screen_mask.png");
    static final double CHANGE_PER_SEC = 1;
    private static final int[] OVERLOADED = {208, 20, 20, 170};
    private static double r, g, b, a;
    private static long lastFrame;
    private static long frames;
    private ScreenMaskHud() { }
    /** 4つのカテゴリそれぞれの原作Category.getColorStyle。 */
    static int[] colorStyle(ResourceLocation category) {
        return switch (category.getPath()) {
            case "electromaster" -> new int[]{20, 113, 208, 100};
            case "meltdowner" -> new int[]{126, 255, 132, 80};
            case "teleporter" -> new int[]{164, 164, 164, 145};
            default -> new int[]{0, 0, 0, 0};
        };
    }
    public static double alpha() { return a; }
    /** マスクを描いたフレーム数（テスト用）。実際の見え方は画像で判定する。 */
    public static long frames() { return frames; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null) return;
        long time = Util.getMillis();
        int[] color = null;
        if (data.hasAbility() && data.isOverloaded()) color = OVERLOADED;
        else if (data.hasAbility() && data.isActive() && data.getAbility() != null) color = colorStyle(data.getAbility());
        double cr, cg, cb, ca;
        if (color == null) { cr = r; cg = g; cb = b; ca = 0; }
        else { cr = color[0] / 255.0; cg = color[1] / 255.0; cb = color[2] / 255.0; ca = color[3] / 255.0; }
        if (ca != 0 || a != 0) {
            long dt = lastFrame == 0 ? 0 : time - lastFrame;
            r = balance(r, cr, dt); g = balance(g, cg, dt); b = balance(b, cb, dt); a = balance(a, ca, dt);
            var window = event.getWindow();
            drawMask(event.getGuiGraphics().pose(), MASK, window.getGuiScaledWidth(), window.getGuiScaledHeight(), (float) r, (float) g, (float) b, (float) a);
            frames++;
        } else {
            r = cr; g = cg; b = cb;
        }
        lastFrame = time;
    }
    /** 幅x高さの画面へ、この色でマスクを描く。テクスチャと共にテスト用に公開している。 */
    public static void drawMask(PoseStack pose, ResourceLocation texture, float width, float height, float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(m, 0, 0, 0).uv(0, 0).endVertex();
        buffer.vertex(m, 0, height, 0).uv(0, 1).endVertex();
        buffer.vertex(m, width, height, 0).uv(1, 1).endVertex();
        buffer.vertex(m, width, 0, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    public static final ResourceLocation MASK_TEXTURE = MASK;
    private static double balance(double from, double to, long dt) {
        double delta = to - from;
        return from + Math.signum(delta) * Math.min(Math.abs(delta), dt / 1000.0 * CHANGE_PER_SEC);
    }
}
