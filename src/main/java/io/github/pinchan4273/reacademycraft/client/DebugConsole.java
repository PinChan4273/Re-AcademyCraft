package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 原作DebugConsole: F4で、OFF・能力の数値・各技能の経験を順に切り替え、左上に、他のHUDより後ろに描く。
 * 原作と同じく、半画素右下に暗い写しを重ねる。文言は原作のもので、翻訳しない。
 *
 * 原作は、最大CPと最大過負荷を「元の値＋追加分」で表示する。追加分はこのclientへ送られてこない。元の値は原作と同じく
 * レベルの初期値で、ここでの追加分は最大値の残り。そのため、汎用の技能による増加分もここに含まれる。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class DebugConsole {
    public static final KeyMapping KEY = new KeyMapping("key.academy.debug_console", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.academy");
    public enum State { NONE, NORMAL, SHOW_EXP }
    private static State state = State.NONE;
    private DebugConsole() { }

    /**
     * Registrationという名前にしない: AbilityControls.Registration.keysとクラス名・メソッド名・イベント名が同じになり、
     * Forgeのイベントバスはその片方しか呼ばなくなる。
     */
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class DebugKeyRegistration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(KEY); }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (KEY.consumeClick()) next();
    }
    /** 原作のキーの処理: 次の状態。最後の次はOFFに戻る。 */
    public static void next() { state = State.values()[(state.ordinal() + 1) % State.values().length]; }
    public static State state() { return state; }

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
                if (data.hasAbility()) {
                    for (var skill : SkillCatalog.IMPLEMENTED) {
                        if (!data.getAbility().equals(skill.category())) continue;
                        var sb = new StringBuilder(skill.id().getPath());
                        for (int i = 0; i < 30 - skill.id().getPath().length(); ++i) sb.append(' ');
                        if (data.hasLearned(skill.id())) sb.append(String.format(Locale.ROOT, "%.1f", data.getProficiency(skill.id()) * 100)).append('%');
                        else sb.append("[not learned]");
                        texts.add(sb.toString());
                    }
                }
            }
            case NONE -> { }
        }
        return texts;
    }

    // 原作: foreground = falseなので、他のHUDより先に描く。
    @SubscribeEvent public static void render(RenderGuiEvent.Pre event) {
        var client = Minecraft.getInstance();
        if (state == State.NONE || client.player == null || client.options.hideGui) return;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null) return;
        var texts = lines(data);
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
