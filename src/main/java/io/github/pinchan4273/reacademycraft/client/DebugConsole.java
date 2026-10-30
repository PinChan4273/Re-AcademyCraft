package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillStatusLines;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 原作DebugConsole: F4で、OFF・能力の数値・各技能の経験を順に切り替え、左上に、他のHUDより後ろに描く。
 * 原作と同じく、半画素右下に暗い写しを重ねる。文言は原作のもので、翻訳しない。
 *
 * 原作は、最大CPと最大過負荷を「元の値＋追加分」で表示する。追加分はこのclientへ送られてこない。元の値は原作と同じく
 * レベルの初期値で、ここでの追加分は最大値の残り。そのため、汎用の技能による増加分もここに含まれる。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DebugConsole {
    public static final KeyMapping KEY = new KeyMapping("key.academy.debug_console", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.academy");
    /**
     * 2ページ目（技能）が画面に収まらないときのページ送り（QAの所見FQ-03）。F4の3つの状態の切り替えは変えない。
     * 技能のページが2つ以上のときだけ効き、最後のページの次は最初に戻る。端末の設定画面には加えない（画面を変えない）。
     */
    public static final KeyMapping PAGE = new KeyMapping("key.academy.debug_console_page", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.academy");
    public enum State { NONE, NORMAL, SHOW_EXP }
    private static State state = State.NONE;
    /** 技能のページの今の番号（0から）と、最後に描いたときのページの数。 */
    private static int page, pageCount = 1;
    private DebugConsole() { }

    /**
     * Registrationという名前にしない: AbilityControls.Registration.keysとクラス名・メソッド名・イベント名が同じになり、
     * Forgeのイベントバスはその片方しか呼ばなくなる。
     */
    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class DebugKeyRegistration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(KEY); event.register(PAGE); }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        while (KEY.consumeClick()) next();
        while (PAGE.consumeClick()) nextPage();
    }
    /** 原作のキーの処理: 次の状態。最後の次はOFFに戻る。状態が変わると技能のページは最初に戻る。 */
    public static void next() { state = State.values()[(state.ordinal() + 1) % State.values().length]; page = 0; }
    public static State state() { return state; }
    /** 技能のページを次へ（2ページ以上のときだけ。最後の次は最初）。 */
    public static void nextPage() { if (state == State.SHOW_EXP && pageCount > 1) page = (page + 1) % pageCount; }
    /** 今の技能のページの番号（0から）と、ページの数。 */
    public static int page() { return page; }
    public static int pageCount() { return pageCount; }
    /** 最後に並べた技能のページが、F4より後に描かれるHUDを避けて並べられたか（falseは避けない並べ方に戻した。DebugConsoleLayout.Layout）。 */
    private static boolean clearOfHud = true;
    private static String lastFallback;
    public static boolean clearOfHud() { return clearOfHud; }

    /**
     * この画面の大きさで今描く行: 技能のページは、幅で折り返し、高さでページに分けたうち今のページ（DebugConsoleLayout）。
     * 空の文字列の行には何も描かない（F4より後に描かれるHUDを避けた行）。数値のページは原作どおりそのまま。
     */
    public static List<String> drawn(PlayerAbilityData data, int guiWidth, int guiHeight) {
        var texts = lines(data);
        if (state != State.SHOW_EXP) return texts;
        var font = Minecraft.getInstance().font;
        var avoid = avoided(data, guiWidth, guiHeight);
        var layout = DebugConsoleLayout.layout(texts, guiWidth, guiHeight, font::width,
                (at, count) -> String.format(Locale.ROOT, "Page %d/%d - %s: next page", at, count, PAGE.getTranslatedKeyMessage().getString()),
                avoid);
        clearOfHud = layout.avoided();
        // 避けて並べられない画面（極小の画面や、HUDを動かして左端を塞いだ場合）では、文字を隠さず避けない並べ方で描く。
        // HUDと重なりうることを、画面の大きさかHUDの範囲が変わったときに1回だけログに残す（毎フレームは出さない）。
        String key = guiWidth + "x" + guiHeight + " " + avoid;
        if (!clearOfHud && !key.equals(lastFallback)) {
            lastFallback = key;
            LogUtils.getLogger().warn("F4 skills page: no room to keep clear of the HUD at GUI {}x{} (HUD areas {}); drawn without avoiding it, nothing hidden",
                    guiWidth, guiHeight, avoid);
        }
        var pages = layout.pages();
        pageCount = pages.size();
        if (page >= pageCount) page = 0;
        return pages.get(page);
    }

    /**
     * 技能のページが避ける、F4より後に描かれるHUDの今の範囲（WORK-045のFQ-03）: 能力ONのCPバーとキーのヒント、教程の通知。
     * それぞれのHUDの位置（HudLayout、CustomizeUIで動かした位置を含む）と描き方から求める。
     */
    public static List<DebugConsoleLayout.Area> avoided(PlayerAbilityData data, int guiWidth, int guiHeight) {
        var client = Minecraft.getInstance();
        var out = new ArrayList<DebugConsoleLayout.Area>();
        for (var area : new DebugConsoleLayout.Area[] {CpBarHud.area(client, data, guiWidth, guiHeight), KeyHintHud.area(client, data, guiWidth, guiHeight),
                TutorialNotifyHud.area(guiWidth, guiHeight)})
            if (area != null) out.add(area);
        return out;
    }

    /** このプレイヤーのデータについて、状態ごとの原作の行。 */
    public static List<String> lines(PlayerAbilityData data) {
        var texts = new ArrayList<String>();
        texts.add("AcademyCraft developer info");
        switch (state) {
            case NORMAL -> {
                if (!data.hasAbility()) {
                    texts.add("Ability not acquired");
                } else {
                    texts.add(data.getAbility().getPath());
                    texts.add("Level " + data.getLevel());
                    // 参加中のサーバーが送ってきた初期値。このclientの設定ファイルの値ではない。
                    float rawCp = io.github.pinchan4273.reacademycraft.config.SyncedAcademyRules.initCp(data.getLevel()),
                            rawOverload = io.github.pinchan4273.reacademycraft.config.SyncedAcademyRules.initOverload(data.getLevel());
                    texts.add(String.format(Locale.ROOT, "CP:       %.0f/%.0f(%.1f+%.1f)", data.getCp(), data.getMaxCp(), rawCp, data.getMaxCp() - rawCp));
                    texts.add(String.format(Locale.ROOT, "Overload: %.0f/%.0f(%.1f+%.1f)", data.getOverload(), data.getMaxOverload(), rawOverload, data.getMaxOverload() - rawOverload));
                    texts.add("CPData.canUseAbility: " + data.canUseAbility());
                    texts.add("CPData.activated: " + data.isActive());
                    texts.add("CPData.addMaxCP: " + (data.getMaxCp() - rawCp));
                    texts.add("CPData.interfering: " + data.isInterfering());
                    texts.add(String.format(Locale.ROOT, " AData.levelProgress: %.2f%%", data.getLevelProgress() * 100));
                }
            }
            case SHOW_EXP -> {
                texts.add("Skill status");
                // 能力カテゴリの技能、Radiation Intensifyの実効値、汎用コース（2026-10-16の追加指示）。
                // Radiation Intensifyの基準は、参加中のサーバーが送ったLv5の初期CP（このclientの設定ファイルではない）。
                // まだ届いていなければ、標準の値で計算せずnullを渡す（QAの所見FQ-01）。
                texts.addAll(SkillStatusLines.of(data, io.github.pinchan4273.reacademycraft.config.SyncedAcademyRules.received()
                        ? Float.valueOf(io.github.pinchan4273.reacademycraft.config.SyncedAcademyRules.initCp(5)) : null));
            }
            case NONE -> { }
        }
        return texts;
    }

    // 原作: foreground = falseなので、他のHUDより先に描く。
    @SubscribeEvent public static void render(RenderGuiEvent.Pre event) {
        var client = Minecraft.getInstance();
        if (state == State.NONE || client.player == null || client.options.hideGui) return;
        var data = AbilityCapabilities.of(client.player).orElse(null);
        if (data == null) return;
        // Forge版はRenderGuiOverlayEventのgetWindow()。NeoForgeのRenderGuiEventには無いので、同じウィンドウをMinecraftから読む。
        var window = client.getWindow();
        var texts = drawn(data, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        draw(event.getGuiGraphics(), texts, 10.5f, 10.5f, .2);
        draw(event.getGuiGraphics(), texts, 10, 10, 1);
    }
    /** 原作iter: 大きさ10のフォント、行の間隔10、色の明るさにlumMulを掛ける。 */
    private static void draw(GuiGraphics g, List<String> texts, float x, float y, double lumMul) {
        int c = (int) (255 * lumMul);
        int color = 0xff000000 | c << 16 | c << 8 | c;
        var font = Minecraft.getInstance().font;
        var pose = g.pose();
        for (var text : texts) {
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(10 / 9f, 10 / 9f, 1);
            g.drawString(font, text, 0, 0, color, false);
            pose.popPose();
            y += 10;
        }
    }
}
