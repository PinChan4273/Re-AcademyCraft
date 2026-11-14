package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.AbilityCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.5.2: ベクトル操作の解析度のHUD。ベクトル操作の能力者が能力使用モードの間だけ、CPバーの下に右揃えで、分類の名前と
 * 割合（切り捨ての整数）とバーを並べる。何を出すかは{@link ClientSettings#analysisHudMode()}（選んだ物だけ・すべて・出さない）と、
 * 能力設定で選んだ分類（{@link ClientSettings#analysisHudSelected()}）で決める（決まりは{@link AnalysisHudStyle}）。
 * <ul>
 * <li>置き場所: CPバー（CustomizeUIで動かした位置も追う）の右端にそろえ、プリセットの四角・常に出す数値・キーの案内の下から始める。
 *     右上のバニラの状態効果のアイコン（2段、上から50まで）にも重ならない高さ。下は、右端の列のキーの案内（KeyHintHud）の手前で止め、
 *     入りきらなければ最後の行を「ほかN件」にする。</li>
 * <li>F1（HUDを隠す）では描かない。ほかのHUDと同じく、画面を開いていても描く。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class VectorAnalysisHud {
    /** 1行の高さ（文字9・バー2・すき間）、表示の幅、余白、CPバーとの間（GUIの画素）。 */
    static final float LINE_H = 14, WIDTH = 120, PAD = 3, GAP_BELOW_CP = 4;
    /** CPバーの部品の単位での、プリセットの四角・キーの案内（光の縁を含む）の下端と、常に出す数値の下端（{@link CpBarHud}と同じ数）。 */
    static final float CP_HINTS_BOTTOM = 197, CP_NUMBER_BOTTOM = 194 + 9 * 5;
    /** 直前のフレームで描いた項目の行の数（テスト用）。 */
    private static int lines;
    private VectorAnalysisHud() { }
    public static int lines() { return lines; }

    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        lines = 0;
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.hasAbility() || !data.isActive() || !AbilityCategory.VECMANIP.id().equals(data.getAbility())) return;
        var mode = ClientSettings.analysisHudMode();
        if (mode == AnalysisHudStyle.Mode.OFF) return;
        var shown = AnalysisHudStyle.shown(mode, ClientVectorAnalysis.entries(), ClientSettings.analysisHudSelected());
        if (shown.isEmpty()) return;
        int width = event.getWindow().getGuiScaledWidth(), height = event.getWindow().getGuiScaledHeight();
        // CPバーの右端と、その下の部品の下端（使用中なので大きさの倍率は1）。
        float right = HudLayout.left(HudLayout.Node.CPBAR, width) + CpBarHud.WIDTH * CpBarHud.SCALE;
        float cpBottom = Math.max(CP_HINTS_BOTTOM, ClientSettings.cpDisplay() == CpHudStyle.Display.BOTH ? CP_NUMBER_BOTTOM : 0) * CpBarHud.SCALE;
        float top = HudLayout.top(HudLayout.Node.CPBAR, height) + cpBottom + GAP_BELOW_CP;
        right = Math.max(WIDTH + PAD * 2, Math.min(width, right));
        top = Math.max(0, top);
        // 下は、キーの案内の上端の手前まで（キーの案内がこの高さより上にあるときは、画面の下まで）。
        float keyTop = HudLayout.top(HudLayout.Node.KEYHINT, height) - 4;
        float bottom = keyTop > top + LINE_H ? keyTop : height - 4;
        int count = AnalysisHudStyle.linesFor(shown.size(), bottom - top - PAD * 2, LINE_H);
        if (count <= 0) return;
        boolean more = count < shown.size();
        int rows = count + (more ? 1 : 0);
        var g = event.getGuiGraphics(); var font = client.font;
        float left = right - WIDTH - PAD * 2;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.fill(0, 0, (int) (WIDTH + PAD * 2), (int) (rows * LINE_H + PAD * 2 - 2), 0x50000000);
        for (int i = 0; i < count; i++) {
            var entry = shown.get(i);
            float y = PAD + i * LINE_H, value = entry.value();
            String percent = AnalysisHudStyle.percent(value);
            int percentWidth = font.width(percent);
            String name = ClientVectorAnalysis.name(entry).getString();
            int room = (int) WIDTH - percentWidth - 4;
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(0, room - font.width("…"))) + "…";
            boolean full = io.github.pinchan4273.reacademycraft.skill.VectorAnalysis.full(value);
            g.drawString(font, name, (int) PAD, (int) y, 0xffffffff, true);
            g.drawString(font, percent, (int) (PAD + WIDTH - percentWidth), (int) y, full ? 0xffffe680 : 0xff80ddff, true);
            // バー: 暗い下地に、解析した分を明るく（解析しきったら金色）。
            int barY = (int) (y + 10), barW = (int) WIDTH;
            g.fill((int) PAD, barY, (int) PAD + barW, barY + 2, 0x80303030);
            int filled = Math.round(barW * AnalysisHudStyle.fill(value));
            if (filled > 0) g.fill((int) PAD, barY, (int) PAD + filled, barY + 2, full ? 0xffffd040 : 0xff46b3ff);
        }
        if (more) {
            String text = Component.translatable("academy.analysis.hud.more", shown.size() - count).getString();
            g.drawString(font, text, (int) (PAD + WIDTH - font.width(text)), (int) (PAD + count * LINE_H), 0xffaaaaaa, true);
        }
        g.pose().popPose();
        lines = count;
    }
}
