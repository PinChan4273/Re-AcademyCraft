package io.github.pinchan4273.reacademycraft.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 原作TerminalInstallEffectとguis/terminal_installing.xml: "Installing..."の札が付いた細い進捗バー。4秒で満ち、
 * 0.2秒でフェードイン・アウトする。満ちてから0.7秒後に消え、端末キーと同じように端末が開き、チャットでそのキーを伝える。
 * ウィジェットの位置・大きさ・色はXMLのもので、左上からの拡大縮小済みGUI単位。
 */
public final class TerminalInstallEffect {
    static final double ANIM_LENGTH = 4, WAIT = .7, BLEND_IN = .2, BLEND_OUT = .2;
    private static long started = -1;
    private static long frames;
    private TerminalInstallEffect() { }
    public static void start() { started = Util.getMillis(); }
    public static boolean active() { return started >= 0; }
    /** 描いたフレーム数（テスト用）。実際の見え方は画像で判定する。 */
    public static long frames() { return frames; }
    static double seconds() { return (Util.getMillis() - started) / 1000.0; }

    /** XMLのblender: 0.2秒で現れ、4秒の後0.2秒で消える。 */
    static double alpha(double t) {
        if (t < BLEND_IN) return t / BLEND_IN;
        if (t > ANIM_LENGTH) return Math.max(0, 1 - (t - ANIM_LENGTH) / BLEND_OUT);
        return 1;
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class Overlay {
        // NeoForge 21.1: HUDのレイヤー（Forge版はRegisterGuiOverlaysEventのオーバーレイ）。IDはForge版の"terminal_installing"と同じ名前。
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "terminal_installing"), Overlay::draw);
        }
        private static void draw(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
            if (!active()) return;
            double t = seconds(), a = alpha(t), progress = Math.min(1, t / ANIM_LENGTH);
            // main: (164, 203)に150 x 9、(60, 60, 60, 120)。
            float x = 164, y = 203;
            fill(g, x, y, 150, 9, 60, 60, 60, (int) (120 * a));
            // outline 147 x 6、cover 146 x 5、バー145 x 4。いずれもmainの中央。
            fill(g, x + (150 - 147) / 2f, y + (9 - 6) / 2f, 147, 6, 255, 255, 255, (int) (150 * a));
            fill(g, x + (150 - 146) / 2f, y + (9 - 5) / 2f, 146, 5, 30, 30, 30, (int) (200 * a));
            fill(g, x + (150 - 145) / 2f, y + (9 - 4) / 2f, (float) (145 * progress), 4, 255, 255, 255, (int) (200 * a));
            // tag: mainの(0, -8)に40 x 8。文字は大きさ10、縦に中央揃え。
            fill(g, x, y - 8, 40, 8, 60, 60, 60, (int) (120 * a));
            var font = Minecraft.getInstance().font;
            int textAlpha = (int) (.1f * 255 + .9 * 255 * a);
            g.pose().pushPose();
            float scale = 10f / font.lineHeight;
            g.pose().translate(x, y - 8 + (8 - 10) / 2f, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, Component.translatable("academy.terminal.installing"), 0, 0, textAlpha << 24 | 0xffffff, false);
            g.pose().popPose();
            frames++;
        }
        private static void fill(GuiGraphics g, float x, float y, float w, float h, int r, int gr, int b, int alpha) {
            if (alpha <= 0 || w <= 0) return;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(w, h, 1);
            g.fill(0, 0, 1, 1, alpha << 24 | r << 16 | gr << 8 | b);
            g.pose().popPose();
        }
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class Ticker {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            if (!active() || seconds() < ANIM_LENGTH + WAIT) return;
            started = -1;
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            // 原作: TerminalUI.keyHandler.onKeyUp()、次にチャットでキーの案内。
            if (client.screen == null && !TerminalHud.isOpen()) TerminalHud.open();
            client.player.displayClientMessage(Component.translatable("academy.terminal.key_hint",
                    AbilityControls.TERMINAL.getTranslatedKeyMessage()), false);
        }
    }
}
