package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.TutorialSnapshot;
import io.github.pinchan4273.reacademycraft.tutorial.Tutorials;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作NotifyUI（AcademyCraft commit 7b1401c）: ミサカクラウドの記事が解放されると、0.25倍の517x170のカードを
 * 左上の15下に6秒間表示する: 背景、減速しながら(420, 42)から(34, 42)へ滑るupdate_notifyのアイコン、記事のタイトルの上に
 * "Cloud Terminal updated"。最後の0.3秒でフェードアウトする。
 *
 * 原作の最初の0.5秒は秒とミリ秒の定数を比べている（drawBack(dt / 300)、アイコンの(dt - 200) / 300）ため、
 * 滑り始めるまでカードはほとんど見えない。これはそのまま残す。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TutorialNotifyHud {
    static final double KEEP_TIME = 6, BLEND_IN_TIME = .5, SCAN_TIME = .5, BLEND_OUT_TIME = .3;
    static final float SCALE = .25f, WIDTH = 517, HEIGHT = 170;
    private static final ResourceLocation BACK = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/notification/back.png"),
            ICON = ResourceLocation.fromNamespaceAndPath("academy", "textures/tutorial/update_notify.png");
    @Nullable private static String title;
    private static double received;
    private static long frames;
    private TutorialNotifyHud() { }
    public static long frames() { return frames; }
    @Nullable public static String shownTitle() { return title; }

    /** 教程のスナップショットのクライアント処理: 保存し、新しいものを知らせる。 */
    public static void receive(TutorialSnapshot packet) {
        var player = Minecraft.getInstance().player;
        packet.handle(player);
        for (var id : packet.fresh()) {
            var tutorial = Tutorials.byId(id);
            if (tutorial != null) notify(TutorialScreen.title(tutorial));
        }
    }
    public static void notify(String articleTitle) {
        title = articleTitle; received = Util.getMillis() / 1000.0;
    }

    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (title == null || client.options.hideGui) return;
        double dt = Util.getMillis() / 1000.0 - received;
        var g = event.getGuiGraphics(); var pose = g.pose();
        pose.pushPose();
        // 原作ACHudのノード"notification": CustomizeUIで動かさない限り、左上の(0, 15)。
        pose.translate(HudLayout.left(HudLayout.Node.NOTIFICATION, event.getWindow().getGuiScaledWidth()),
                HudLayout.top(HudLayout.Node.NOTIFICATION, event.getWindow().getGuiScaledHeight()), 0); pose.scale(SCALE, SCALE, 1);
        if (dt < BLEND_IN_TIME) {
            back(g, Math.min(dt / 300.0, 1));
            icon(g, ICON, 420, Math.max(0, Math.min(1, (dt - 200) / 300.0)));
        } else if (dt < SCAN_TIME + BLEND_IN_TIME) {
            float scan = Mth.sin((float) ((dt - BLEND_IN_TIME) / SCAN_TIME) * Mth.PI / 2);
            back(g, 1); icon(g, ICON, Mth.lerp(scan, 420, 34), 1); text(g, header(), title, scan);
        } else if (dt < KEEP_TIME - BLEND_OUT_TIME) {
            back(g, 1); icon(g, ICON, 34, 1); text(g, header(), title, 1);
        } else if (dt < KEEP_TIME) {
            float alpha = 1 - (float) ((dt - (KEEP_TIME - BLEND_OUT_TIME)) / BLEND_OUT_TIME);
            back(g, alpha); icon(g, ICON, 34, alpha); text(g, header(), title, alpha);
        } else {
            title = null;
        }
        pose.popPose();
        frames++;
    }
    private static void back(GuiGraphics g, double alpha) {
        DeveloperScreen.quad(g.pose(), BACK, 0, 0, WIDTH, HEIGHT, 1, 1, 1, (float) alpha);
    }
    private static void icon(GuiGraphics g, ResourceLocation icon, double x, double alpha) {
        DeveloperScreen.quad(g.pose(), icon, (float) x, 42, 83, 83, 1, 1, 1, (float) alpha);
    }
    private static String header() { return Component.translatable("academy.tutorial.update").getString(); }
    /** 原作drawText: 通知のタイトルを38に、内容を54に描く。 */
    private static void text(GuiGraphics g, String head, @Nullable String content, float alpha) {
        if (alpha < .1f) alpha = .1f;
        int a = (int) (alpha * 255);
        var font = Minecraft.getInstance().font;
        TerminalHud.text(g, font, head, 137, 32, 360, 38, 38, 0, 0, a);
        TerminalHud.text(g, font, content == null ? "" : content, 137, 81, 360, 54, 54, 0, 0, a);
    }
    private static final ResourceLocation PREVIEW_ICON = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/edit_preview/notify_logo.png");
    /** 原作NotifyUIのCustomizeUIプレビュー: 止まった状態のカードと、ダミーの通知。 */
    static void preview(GuiGraphics g) {
        back(g, 1); icon(g, PREVIEW_ICON, 34, 1); text(g, net.minecraft.client.resources.language.I18n.get("academy.tutorial.preview.title"),
                net.minecraft.client.resources.language.I18n.get("academy.tutorial.preview.content"), 1);
    }
}
