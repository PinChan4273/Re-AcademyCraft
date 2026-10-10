package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.media.MediaPlayer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作MediaAuxGui（guis/media_player_aux.xml）: メディアの再生中または一時停止中、右下の角から6内側に145x36の
 * ウィジェットを置き、タイトル、薄い暗いバーの上に再生位置を示す細いバー、再生時間を表示する。
 * 原作は0.5秒ごとに表示を更新する。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MediaHud {
    static final float WIDTH = 145, HEIGHT = 36;
    private static double lastTest;
    private static String title = "", time = "";
    private static float progress;
    private static long frames;
    private MediaHud() { }
    /** 描いたフレーム数（テスト用）。実際の見え方は画像で判定する。 */
    public static long frames() { return frames; }

    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        var playing = MediaPlayer.currentPlaying();
        if (playing.isEmpty()) { lastTest = 0; return; }
        double now = Util.getMillis() / 1000.0;
        if (now - lastTest > .5) {
            lastTest = now;
            var info = playing.get();
            progress = info.time() / info.media().lengthSecs();
            title = info.media().name();
            time = info.displayTime();
        }
        var g = event.getGuiGraphics(); var pose = g.pose();
        // 原作ACHudのノード"media": CustomizeUIで動かさない限り、右下から(-6, -6)。
        float x = HudLayout.left(HudLayout.Node.MEDIA, event.getWindow().getGuiScaledWidth()), y = HudLayout.top(HudLayout.Node.MEDIA, event.getWindow().getGuiScaledHeight());
        pose.pushPose();
        pose.translate(x, y, 0);
        widget(g, title, progress, time);
        pose.popPose();
        frames++;
    }
    /**
     * guis/media_player_aux.xmlの本体: タイトル、バー、時間。CustomizeUIはxml自身の内容
     * （Only My Railgun、半分、04:30）でプレビューとして描く。
     */
    static void widget(GuiGraphics g, String title, float progress, String time) {
        var font = Minecraft.getInstance().font; var pose = g.pose();
        MediaScreen.text(g, font, title, 13, 17, 100, 10, 10, 0, 2, 0xffffffff);
        DeveloperScreen.rect(pose, 14, 27.2f, 120, 1.1f, 0, 0, 0, 51 / 255f);
        DeveloperScreen.rect(pose, 14, 27, 120 * Math.max(0, Math.min(1, progress)), 1.5f, 1, 1, 1, 204 / 255f);
        MediaScreen.text(g, font, time, 117, 27, 25, 10, 8.5f, 0, 2, 0xffffffff);
    }
}
