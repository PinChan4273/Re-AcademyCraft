package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillTreeLayout;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作KeyHintUI（AcademyCraft commit 7b1401c）: 能力がONの間、画面の右端の列に、プリセットの各技能のキーとアイコンを表示する。
 * 能力を使えない間や技能のクールダウン中は灰色、アイコンの上をクールダウンが埋め、技能の使用中は光が脈打つ。
 * 位置・大きさ・色は原作のもので、部品の0.23倍の下での部品の単位。部品は右揃え、高さの中央から30下。
 *
 * 原作のDelegateStateは技能の生きているcontextから来る: IDLE（alpha 0.7、光なし）、ACTIVE（青い光が脈打つ）、
 * そして充電を報告する4つの技能ではCHARGE（橙）。このclientは、押し続け型の技能が押されていることと、モードの技能が
 * ONであることを知っていて、それらをACTIVEとして表示する。CHARGEの色は区別しない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class KeyHintHud {
    static final float SCALE = .23f, WIDTH = 140, HEIGHT = 210;
    private static final ResourceLocation BACK = tex("back"), ICON_BACK = tex("icon_back"), KEY_LONG = tex("key_long"),
            KEY_SHORT = tex("key_short"), MOUSE_L = tex("mouse_left"), MOUSE_R = tex("mouse_right"), MOUSE_GENERIC = tex("mouse_generic");
    private static final ResourceLocation[] GLOW = {glow("left"), glow("right"), glow("up"), glow("down"),
            glow("ru"), glow("rd"), glow("lu"), glow("ld")};
    /** DelegateStateの光の色: ACTIVEは0xff46b3ff。IDLEは完全に透明。 */
    private static final int ACTIVE_GLOW = 0x46b3ff;
    private static double lastFrame, shown;
    private static long frames;
    /** SkillCooldown.getMaxTick: クールダウンが始まったときの長さ。このclientが保つ。 */
    private static final Map<ResourceLocation, Integer> COOLDOWN_MAX = new HashMap<>();
    private KeyHintHud() { }
    private static ResourceLocation tex(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/key_hint/" + name + ".png"); }
    private static ResourceLocation glow(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/glow_" + name + ".png"); }
    /** 描いたフレーム数（テスト用）。実際の見え方は画像で判定する。 */
    public static long frames() { return frames; }
    private static int columns;
    /** テスト用: 直前のフレームで描いたキーの列の数。 */
    public static int columns() { return columns; }

    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        // 原作のHUDは、どの画面の下でもゲームのHUDと一緒に描かれ、半透明の開発機の画面から透けて見える。
        if (client.player == null || client.options.hideGui) return;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.hasAbility() || !data.isActive()) return;
        double time = Util.getMillis() / 1000.0;
        // 原作: 0.3秒描かれなかった後は、0.3秒でフェードインする。
        if (time - lastFrame > .3) shown = time;
        float alpha = (float) Math.min(1, (time - shown) / .3);
        lastFrame = time;
        float sin = .6f + (1 + Mth.sin((float) (time % 100) / 50f)) * .2f;
        var g = event.getGuiGraphics(); var pose = g.pose();
        int width = event.getWindow().getGuiScaledWidth(), height = event.getWindow().getGuiScaledHeight();
        pose.pushPose();
        // 原作ACHudのnode"keyhint"、CGui: x = W - width * scale（RIGHT）、y = (H - height * scale) / 2 + 30（CENTER）。
        // CustomizeUIで動かした場合を除く。
        pose.translate(HudLayout.left(HudLayout.Node.KEYHINT, width), HudLayout.top(HudLayout.Node.KEYHINT, height), 0);
        pose.scale(SCALE, SCALE, 1);
        float y = 0;
        for (int slot = 0; slot < 4; slot++) {
            var skill = data.getSlot(data.getCurrentPreset(), slot);
            var node = skill == null ? null : SkillTreeLayout.node(skill);
            if (node == null) continue;
            pose.pushPose();
            // 原作の既定のグループ: x = -200、行の間隔92。
            pose.translate(-200, y, 0);
            boolean inUse = AbilityControls.held(slot) || ClientSkillModes.active(skill)
                    || (io.github.pinchan4273.reacademycraft.skill.Flashing.ID.equals(skill) && ClientFlashing.active())
                    || (io.github.pinchan4273.reacademycraft.skill.StormWing.ID.equals(skill) && ClientStormWing.state() >= 0);
            single(g, data, AbilityControls.SLOTS[slot], skill, node.icon(), inUse, alpha, sin);
            pose.popPose();
            y += 92;
        }
        // 原作の他のキーのグループ。既定のグループの後、名前の順に、1列ずつ左へ: Flashingの"TP_Flashing"（ONの間）、
        // Storm Wingの"vm_storm_wing"（飛び始めてから）。
        int column = y > 0 ? 1 : 0;
        int drawnColumns = column;
        var options = client.options;
        if (ClientFlashing.active()) {
            KeyMapping[] keys = { options.keyLeft, options.keyRight, options.keyUp, options.keyDown };
            String[] icons = { "a", "d", "w", "s" };
            for (int i = 0; i < 4; i++) {
                pose.pushPose(); pose.translate(-200 - column * 200, i * 92, 0);
                single(g, data, keys[i], null, ResourceLocation.fromNamespaceAndPath("academy", "textures/abilities/teleporter/flashing/" + icons[i] + ".png"), false, alpha, sin);
                pose.popPose();
            }
            column++; drawnColumns++;
        }
        var wing = SkillTreeLayout.node(io.github.pinchan4273.reacademycraft.skill.StormWing.ID);
        if (ClientStormWing.state() == io.github.pinchan4273.reacademycraft.skill.StormWing.ACTIVE_STATE && wing != null) {
            KeyMapping[] keys = { options.keyUp, options.keyDown, options.keyLeft, options.keyRight };
            for (int i = 0; i < 4; i++) {
                pose.pushPose(); pose.translate(-200 - column * 200, i * 92, 0);
                // 飛んでいる方向のキーを、原作DelegateState.ACTIVEにする。
                single(g, data, keys[i], null, wing.icon(), ClientStormWing.held() == keys[i], alpha, sin);
                pose.popPose();
            }
            drawnColumns++;
        }
        columns = drawnColumns;
        pose.popPose();
        frames++;
    }

    /**
     * 1つのキーの原作drawSingle: 背景、キー、アイコン、状態。自身の技能を持たないキー（他のグループのもの）には、
     * ここでは自身のクールダウンが無い。
     */
    private static void single(GuiGraphics g, PlayerAbilityData data, KeyMapping mapping, @javax.annotation.Nullable ResourceLocation skill,
                               ResourceLocation icon, boolean inUse, float alpha, float sin) {
        var pose = g.pose();
        DeveloperScreen.quad(pose, BACK, 122, 0, 185, 83, 1, 1, 1, alpha);
        int left = skill == null ? 0 : data.getCooldown(skill);
        if (skill != null) { if (left <= 0) COOLDOWN_MAX.remove(skill); else COOLDOWN_MAX.merge(skill, left, Math::max); }
        boolean grey = !data.canUseAbility() || left > 0;
        // キー: 原作の短いキー・長いキー・マウス。使えないときはShaderMonoで灰色にする。
        var key = mapping.getKey();
        String name = null; ResourceLocation back;
        if (key.getType() == InputConstants.Type.MOUSE) {
            back = key.getValue() == 0 ? MOUSE_L : key.getValue() == 1 ? MOUSE_R : MOUSE_GENERIC;
            if (back == MOUSE_GENERIC) name = String.valueOf(key.getValue());
        } else {
            name = mapping.getTranslatedKeyMessage().getString();
            back = name.length() <= 2 ? KEY_SHORT : KEY_LONG;
        }
        draw(pose, back, 146, 10, 70, 70, grey, alpha);
        if (name != null) {
            var font = Minecraft.getInstance().font;
            float s = 32f / font.lineHeight;
            pose.pushPose();
            pose.translate(180, 27, 0); pose.scale(s, s, 1); pose.translate(-font.width(name) / 2f, 0, 0);
            int a = Math.max(4, (int) (255 * alpha));
            // FontOption(32, CENTER, 0xff194246)。原作は灰色のときもこの色で名前を描く。
            g.drawString(font, name, 0, 0, a << 24 | 0x194246, false);
            pose.popPose();
        }
        draw(pose, ICON_BACK, 216, 5, 72, 72, grey, alpha);
        int max = skill == null ? 0 : COOLDOWN_MAX.getOrDefault(skill, 0);
        float progress = left > 0 && max > 0 ? (float) left / max : 0;
        float pulse = inUse ? sin : 1;
        float iconAlpha = progress == 0 ? (inUse ? 1f : .7f) * (.4f + pulse * .6f) : .4f;
        draw(pose, icon, 221, 10, 62, 62, grey, iconAlpha * alpha);
        if (inUse) glow(pose, 221, 10, 62, 62, 5, ACTIVE_GLOW, pulse * alpha);
        if (progress != 0) DeveloperScreen.rect(pose, 221, 10 + 62 * (1 - progress), 62, 62 * progress, .6f, .6f, .6f, .3f * alpha);
    }
    private static void draw(PoseStack pose, ResourceLocation texture, float x, float y, float w, float h, boolean grey, float alpha) {
        if (grey) DeveloperScreen.mono(pose, texture, x, y, w, h, alpha);
        else DeveloperScreen.quad(pose, texture, x, y, w, h, 1, 1, 1, alpha);
    }
    /** ACRenderingHelper.drawGlow: 四角の周りの8つの光の部品。 */
    static void glow(PoseStack pose, float x, float y, float w, float h, float s, int rgb, float alpha) {
        float r = (rgb >> 16 & 255) / 255f, gr = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        DeveloperScreen.quad(pose, GLOW[0], x - s, y, s, h, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[1], x + w, y, s, h, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[2], x, y - s, w, s, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[3], x, y + h, w, s, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[4], x + w, y - s, s, s, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[5], x + w, y + h, s, s, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[6], x - s, y - s, s, s, r, gr, b, alpha);
        DeveloperScreen.quad(pose, GLOW[7], x - s, y + h, s, s, r, gr, b, alpha);
    }
}
