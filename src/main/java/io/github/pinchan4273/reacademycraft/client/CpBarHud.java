package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.visual.LegacyCubicCurve;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作CPBar（AcademyCraft commit 7b1401c）: 右上の斜めのCPバー。964×147を0.2倍し、角から12内側に置く。
 * 能力をONにすると0.2秒でフェードインし、OFFにすると0.2秒でフェードアウトする。CPバーの後ろで、過負荷が右から
 * 灰色から赤への色で伸びる。過負荷状態になると、バーは原作の赤い背景（流れるテクスチャと脈打つ光）に変わる。
 * CPバー自体は斜めに切られ、減り具合に応じて赤・橙・白へ色が変わり、系統のoverlayのアイコンの形に抜かれる。
 * どちらのバーも、実際の値へ毎秒バーの長さの2倍の速さで追い付く。干渉されている間は、原作のkeyframeで
 * バー全体が震えてちらつき、CPバーが暗くなる。プリセットを切り替えると、どのプリセットがONかを示す4つの番号付きの
 * 四角を2秒表示する。activateのキーを押し続けると数値を表示する。特殊な技能のモードがONの間は、その下にキーの案内
 * （End Special Skill Mode）を表示する。
 *
 * 発動前に充電する技能を押している間は、原作と同じく消費するCPを表示する: 今のバーを、消費後のバーの下で点滅させる
 * （consumptionHint）。
 *
 * 原作のフォントは使わず、ゲームのフォントを原作の大きさで描く。原作と同じく、キーは0.3秒以内に離したときに能力を
 * 切り替え（AbilityControls）、押している間は数値を表示する。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CpBarHud {
    static final float WIDTH = 964, HEIGHT = 147, SCALE = .2f;
    static final float CP_BALANCE_SPEED = 2, O_BALANCE_SPEED = 2;
    private static final double SIN41 = Math.sin(Math.toRadians(44.0));
    private static final ResourceLocation BACK_NORMAL = tex("back_normal"), BACK_OVERLOAD = tex("back_overload"), CP = tex("cp"),
            FRONT_OVERLOAD = tex("front_overload"), HIGHLIGHT = tex("highlight_overload"), MASK = tex("mask");
    /** テクスチャの大きさ。原作の「画素÷大きさ」のテクスチャ座標に使う。 */
    private static final float TEX_W = 964, TEX_H = 147, FRONT_W = 974;
    private static final double[][] CP_COLORS = {{0, 0xf0, 0x67, 0x67, 0xff}, {.35, 0xff, 0xae, 0x44, 0xff}, {1, 0xff, 0xff, 0xff, 0xff}};
    private static final double[][] OVERLOAD_COLORS = {{0, 0xdf, 0xdf, 0xdf, 0x0a}, {.55, 0xf0, 0xd4, 0x9d, 0x23}, {1, 0xf5, 0x64, 0x64, 0x50}};
    @Nullable private static ShaderInstance cpShader, overloadShader;

    private static long presetChangeTime, lastPresetTime, lastDrawTime, showTime, lastShowValueChange;
    private static int lastPreset = -1;
    private static boolean lastFrameActive, showingNumbers;
    private static float mAlpha, bufferedCp, bufferedOverload;
    private static long frames;
    // 原作の干渉のkeyframe: 80〜400msおきの60個のずれと、それを通るalphaの曲線。
    private static final long[] FRAME_TIME = new long[60];
    private static final double[][] FRAME_OFFSET = new double[60][2];
    private static final LegacyCubicCurve ALPHA_CURVE = new LegacyCubicCurve();
    private static final long MAX_TIME;
    static {
        var random = new Random();
        double aspect = WIDTH / HEIGHT, offsetMax = 9;
        ALPHA_CURVE.add(0, .2 + random.nextDouble() * .6);
        long sum = 0;
        for (int i = 0; i < 60; i++) {
            int time = 80 + random.nextInt(320);
            float norm = random.nextFloat();
            float theta = random.nextFloat() * Mth.TWO_PI;
            norm = norm * norm * norm;
            sum += time;
            FRAME_TIME[i] = sum;
            FRAME_OFFSET[i][0] = Math.sin(theta) * norm * offsetMax * aspect;
            FRAME_OFFSET[i][1] = Math.cos(theta) * norm * offsetMax;
            ALPHA_CURVE.add(sum, .4 + random.nextDouble() * .3);
        }
        MAX_TIME = sum;
    }
    private CpBarHud() { }
    private static ResourceLocation tex(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/cpbar/" + name + ".png"); }
    /** 原作Category.getOverlayIcon。 */
    static ResourceLocation overlay(ResourceLocation category) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/abilities/" + category.getPath() + "/icon_overlay.png");
    }
    /** バーを表示して描いたフレーム数（テスト用）。実際の見え方は画像で判定する。 */
    public static long frames() { return frames; }
    public static float alpha() { return mAlpha; }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void register(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "cpbar_cp"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> cpShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("academy", "cpbar_overload"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> overloadShader = shader);
        }
    }

    /** 原作ClientHandler: activateのキーを押し続けると数値を表示し、離すと隠す。 */
    static void keyState(boolean down) {
        long time = Util.getMillis();
        if (down && !showingNumbers) { showingNumbers = true; lastShowValueChange = time; }
        else if (!down && showingNumbers) {
            showingNumbers = false;
            lastShowValueChange = time - lastShowValueChange > 400 ? time : 0;
        }
    }

    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.hasAbility()) return;
        keyState(AbilityControls.TOGGLE.isDown());
        long time = Util.getMillis();
        if (lastPreset != data.getCurrentPreset()) {
            // 原作のPresetSwitchEvent。最初に見たプリセットは切り替えとして扱わない。
            if (lastPreset >= 0) { lastPresetTime = presetChangeTime; presetChangeTime = time; }
            lastPreset = data.getCurrentPreset();
        }
        boolean active = data.isActive();
        if (!lastFrameActive && active) showTime = time;
        long deltaTime = Math.min(100L, time - lastDrawTime);
        mAlpha = time - showTime < 200 ? (time - showTime) / 200f : active ? 1 : Math.max(0, 1 - (time - lastDrawTime) / 200f);
        var g = event.getGuiGraphics(); var pose = g.pose();
        pose.pushPose();
        // 原作ACHudの"cpbar": 右揃え、CustomizeUIで動かさなければ(-12, 12)。
        pose.translate(HudLayout.left(HudLayout.Node.CPBAR, event.getWindow().getGuiScaledWidth()),
                HudLayout.top(HudLayout.Node.CPBAR, event.getWindow().getGuiScaledHeight()), 0);
        pose.scale(SCALE, SCALE, 1);
        boolean interfering = data.isInterfering();
        if (interfering) {
            long input = time % MAX_TIME;
            int frame = 0;
            while (frame < 59 && FRAME_TIME[frame] <= input) frame++;
            pose.translate(FRAME_OFFSET[frame][0], FRAME_OFFSET[frame][1], 0);
            // 精度を下げることで、ちらつきがぎざぎざに見える。
            mAlpha *= (float) ALPHA_CURVE.valueAt(input / 10 * 10);
        }
        float pOverload = mAlpha > 0 ? data.getOverload() / Math.max(1e-6f, data.getMaxOverload()) : 0;
        bufferedOverload = balance(bufferedOverload, pOverload, deltaTime * 1e-3f * O_BALANCE_SPEED);
        float pCp = mAlpha > 0 ? data.getCp() / Math.max(1e-6f, data.getMaxCp()) : 0;
        bufferedCp = balance(bufferedCp, pCp, deltaTime * 1e-3f * CP_BALANCE_SPEED);
        if (mAlpha > 0) {
            var category = data.getAbility();
            // 原作CPBar: 過負荷の絵は、isOverloaded（回復の待ち時間）の間だけ。
            if (!data.isOverloaded()) drawNormal(pose, bufferedOverload); else drawOverload(pose, time);
            // 原作は、干渉されている間と過負荷の回復中にバーを暗くする。
            boolean low = interfering || data.isOverloadLocked();
            var icon = category == null ? null : overlay(category);
            float estimate = consumptionHint(data);
            if (estimate != 0) {
                // 原作: 今のCPを点滅させ、技能が消費した後のCPの下に描く。
                float saved = mAlpha;
                mAlpha *= (float) (.2f + .1f * (1 + Math.sin(time / 80.0)));
                drawCpBar(pose, pCp, low, icon);
                mAlpha = saved;
                drawCpBar(pose, Math.max(0, data.getCp() - estimate) / Math.max(1e-6f, data.getMaxCp()), low, icon);
            } else {
                drawCpBar(pose, bufferedCp, low, icon);
            }
            if (time - presetChangeTime < 2000L) drawPresetHint(g, client, data, (time - presetChangeTime) / 2000.0, time - lastPresetTime);
            drawNumbers(g, client, data, time);
            drawActivateKeyHint(g, client);
            frames++;
        }
        if (active) lastDrawTime = time;
        lastFrameActive = active;
        pose.popPose();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private static float balance(float from, float to, float max) {
        float delta = to - from;
        return from + Math.signum(delta) * Math.min(max, Math.abs(delta));
    }
    /** 原作autoLerp: 並べた色の間で、この進み具合の色を求める。最初の色は、原作と同じく直接使うので、全体のalphaを掛けない。 */
    private static float[] lerp(double[][] colors, double progress) {
        for (int i = 0; i < colors.length; i++) {
            if (colors[i][0] >= progress) {
                if (i == 0) return new float[]{(float) colors[0][1] / 255, (float) colors[0][2] / 255, (float) colors[0][3] / 255, (float) colors[0][4] / 255};
                double[] a = colors[i - 1], b = colors[i];
                double f = (progress - a[0]) / (b[0] - a[0]);
                return new float[]{(float) (a[1] + (b[1] - a[1]) * f) / 255, (float) (a[2] + (b[2] - a[2]) * f) / 255,
                        (float) (a[3] + (b[3] - a[3]) * f) / 255, (float) ((a[4] + (b[4] - a[4]) * f) / 255 * mAlpha)};
            }
        }
        return new float[]{1, 1, 1, mAlpha};
    }
    private static void drawNormal(PoseStack pose, float overload) {
        DeveloperScreen.quad(pose, BACK_NORMAL, 0, 0, WIDTH, HEIGHT, 1, 1, 1, .8f * mAlpha);
        // 過負荷。マスクの上で右から伸びる。
        float x0 = 0, y0 = 21, w = 943, h = 104;
        var c = lerp(OVERLOAD_COLORS, overload);
        float len = overload * w;
        if (len > 0) texel(pose, MASK, x0 + w - len, y0, len, h, TEX_W, TEX_H, c);
    }
    private static void drawOverload(PoseStack pose, long time) {
        DeveloperScreen.quad(pose, BACK_OVERLOAD, 0, 0, WIDTH, HEIGHT, 1, 1, 1, .8f * mAlpha);
        var shader = overloadShader;
        float x0 = 30, width2 = WIDTH - x0 - 20;
        if (shader != null) {
            // 原作: ((float) GameTimer.getTime() % 10) / 10000。単位が秒なので、ほとんど動かない。
            shader.safeGetUniform("TexOffset").set((float) ((time / 1000.0) % 10) / 10000f);
            RenderSystem.setShaderTexture(1, MASK);
            RenderSystem.setShader(() -> shader);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        }
        // HudUtils.rect(x0, 0, 0, 0, width2, H, width2, H): 前面のテクスチャ自身の大きさに対する画素。
        rawQuad(pose, FRONT_OVERLOAD, x0, 0, width2, HEIGHT, 0, 0, width2 / FRONT_W, 1, new float[]{1, 1, 1, mAlpha});
        float highlight = (float) (.3 + .35 * (Math.sin(time / 1000.0 / 200.0) + 1));
        DeveloperScreen.quad(pose, HIGHLIGHT, 0, 0, WIDTH, HEIGHT, 1, 1, 1, highlight * mAlpha);
    }
    /**
     * 原作getConsumptionHint: このclientでcontextが生きている技能のCP。原作では、発動前に充電するVector Manipulationの
     * 3つの技能（Vector Acceleration・Groundshock・Directed Blastwave）の、キーを押している間。これらのcontextは、
     * ここでのサーバーと同じく、技能の経験から消費量を決める。
     */
    public static float consumptionHint(io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData data) {
        for (int i = 0; i < 4; i++) {
            if (!AbilityControls.held(i)) continue;
            var skill = data.getSlot(data.getCurrentPreset(), i);
            if (skill == null || data.getCooldown(skill) > 0) continue;
            float exp = data.getProficiency(skill);
            if (skill.equals(io.github.pinchan4273.reacademycraft.skill.VecAccel.ID)) return io.github.pinchan4273.reacademycraft.skill.VecAccel.consumption(exp);
            if (skill.equals(io.github.pinchan4273.reacademycraft.skill.Groundshock.ID)) return io.github.pinchan4273.reacademycraft.skill.Groundshock.consumption(exp);
            if (skill.equals(io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.ID)) return io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.consumption(exp);
        }
        return 0;
    }
    private static void drawCpBar(PoseStack pose, float progress, boolean cantUse, @Nullable ResourceLocation icon) {
        float saved = mAlpha;
        if (cantUse) mAlpha *= .3f;
        var c = lerp(CP_COLORS, progress);
        progress = .16f + progress * .8f;
        double off = 103 * SIN41;
        float x0 = 47, y0 = 30, w = 883, h = 84;
        float len = w * progress, len2 = (float) (len - off);
        var shader = cpShader;
        if (shader != null && icon != null) {
            RenderSystem.setShaderTexture(1, icon);
            RenderSystem.setShader(() -> shader);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        }
        RenderSystem.setShaderTexture(0, CP);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(buffer, m, x0 + (w - len), y0, c);
        vertex(buffer, m, x0 + (w - len2), y0 + h, c);
        vertex(buffer, m, x0 + w, y0 + h, c);
        vertex(buffer, m, x0 + w, y0, c);
        BufferUploader.drawWithShader(buffer.end());
        mAlpha = saved;
    }
    private static void vertex(com.mojang.blaze3d.vertex.BufferBuilder buffer, org.joml.Matrix4f m, float x, float y, float[] c) {
        buffer.vertex(m, x, y, 0).uv(x / TEX_W, y / TEX_H).color(c[0], c[1], c[2], c[3]).endVertex();
    }
    /** 原作subHud: テクスチャ座標が「自身の位置÷テクスチャの大きさ」になる四角。 */
    private static void texel(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h, float texW, float texH, float[] c) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        rawQuad(pose, texture, x, y, w, h, x / texW, y / texH, (x + w) / texW, (y + h) / texH, c);
    }
    private static void rawQuad(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h,
                                float u0, float v0, float u1, float v1, float[] c) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(m, x, y, 0).uv(u0, v0).color(c[0], c[1], c[2], c[3]).endVertex();
        buffer.vertex(m, x, y + h, 0).uv(u0, v1).color(c[0], c[1], c[2], c[3]).endVertex();
        buffer.vertex(m, x + w, y + h, 0).uv(u1, v1).color(c[0], c[1], c[2], c[3]).endVertex();
        buffer.vertex(m, x + w, y, 0).uv(u1, v0).color(c[0], c[1], c[2], c[3]).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
    /** 原作drawPresetHint: 幅52の四角4つ。現在のものが光る。 */
    private static void drawPresetHint(GuiGraphics g, Minecraft client, PlayerAbilityData data, double progress, long untilLast) {
        double alpha = untilLast > 3000 && progress < .2 ? progress / .2 : progress > .8 ? (1 - progress) / .2 : 1;
        alpha *= .75;
        float x = 580, y = 136, size = 52, step = size + 10;
        for (int i = 0; i < 4; i++) {
            DeveloperScreen.rect(g.pose(), x, y, size, size, 48 / 255f, 48 / 255f, 48 / 255f, (float) alpha);
            TerminalHud.text(g, client.font, String.valueOf(i + 1), x, y + 5, size, 46, 46, 1, 0, (int) (Math.max(.05, alpha * .8) * 255));
            if (i == data.getCurrentPreset()) KeyHintHud.glow(g.pose(), x, y, size, size, 5, 0xffffff, 200 / 255f);
            x += step;
        }
    }
    /** 原作のCPと過負荷の数値。activateのキーを押している間に表示する。 */
    private static void drawNumbers(GuiGraphics g, Minecraft client, PlayerAbilityData data, long time) {
        long dt = lastShowValueChange == 0 ? Long.MAX_VALUE : time - lastShowValueChange;
        float alpha;
        if (data.isOverloaded()) alpha = 0;
        else if (showingNumbers) alpha = Mth.clamp((dt - 200) / 400f, 0, 1);
        else if (dt < 300) alpha = 1 - dt / 300f;
        else alpha = 0;
        if (alpha <= 0) return;
        var font = client.font; float x0 = 110, s = 40 / 9f;
        int argb = (int) (.6f * mAlpha * alpha * 255);
        String s10 = "CP ", s11 = String.format(Locale.ROOT, "%.0f", data.getCp()), s12 = String.format(Locale.ROOT, "/%.0f", data.getMaxCp());
        String s20 = "OL ", s21 = String.format(Locale.ROOT, "%.0f", data.getOverload()), s22 = String.format(Locale.ROOT, "/%.0f", data.getMaxOverload());
        float len0 = Math.max(font.width(s10), font.width(s20)) * s;
        float len1 = len0 + Math.max(font.width(s11), font.width(s21)) * s;
        drawAt(g, font, s10, x0, 55, s, argb); drawAt(g, font, s12, x0 + len1, 55, s, argb);
        drawAt(g, font, s20, x0, 85, s, argb); drawAt(g, font, s22, x0 + len1, 85, s, argb);
        drawAt(g, font, s11, x0 + len1 - font.width(s11) * s, 55, s, argb);
        drawAt(g, font, s21, x0 + len1 - font.width(s21) * s, 85, s, argb);
    }
    private static void drawAt(GuiGraphics g, net.minecraft.client.gui.Font font, String text, float x, float y, float s, int alpha) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(s, s, 1);
        g.drawString(font, text, 0, 0, Math.max(4, Math.min(255, alpha)) << 24 | 0xffffff, false);
        g.pose().popPose();
    }
    /** 原作drawActivateKeyHint: "[キー]: 今キーがすること"を、(500, 140)に右揃えで描く。 */
    private static void drawActivateKeyHint(GuiGraphics g, Minecraft client) {
        String hint = AbilityControls.activateHint(client);
        if (hint == null) return;
        String text = "[" + AbilityControls.TOGGLE.getTranslatedKeyMessage().getString() + "]: "
                + Component.translatable("academy.activate_key." + hint + ".desc").getString();
        var font = client.font; float s = 44 / 9f, margin = 8, x0 = 500, y0 = 140;
        float len = font.width(text) * s;
        DeveloperScreen.rect(g.pose(), x0 - margin - len, y0 - margin, len + margin * 2, 44 + margin * 2, 65 / 255f, 65 / 255f, 65 / 255f, 70 / 255f);
        KeyHintHud.glow(g.pose(), x0 - margin - len, y0 - margin, len + margin * 2, 44 + margin * 2, 5, 0xffffff, 40 / 255f);
        drawAt(g, font, text, x0 - len, y0, s, 0xa0);
    }
    /** テスト用の入口: バーが追い付こうとしている値。 */
    public static float bufferedCp() { return bufferedCp; }
    public static float bufferedOverload() { return bufferedOverload; }
}
