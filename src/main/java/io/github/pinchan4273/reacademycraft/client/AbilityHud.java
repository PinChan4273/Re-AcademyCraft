package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * この移植の以前の文字のHUDの、技能ごとの行。今は原作のCPBar（CpBarHud）とKeyHintUI（KeyHintHud）が
 * 同じ内容を表示するので、これは描かない。テストが、同期したプリセット・クールダウン・熟練度を
 * snapshotから追うために使う。
 */
public final class AbilityHud {
    public record Line(Component text, int color) { }
    private AbilityHud() { }

    /** 固定の技能や編集画面の選択ではなく、常に現在の正式なプリセットを読む。 */
    public static List<Line> skillLines(PlayerAbilityData data) {
        var lines = new ArrayList<Line>();
        int preset = data.getCurrentPreset();
        lines.add(new Line(Component.translatable("academy.hud.preset_label", preset + 1), 0xffffff));
        for (int slot = 0; slot < AbilityControls.SLOTS.length; slot++) {
            var id = data.getSlot(preset, slot);
            var definition = id == null ? null : SkillCatalog.find(id);
            Component name = id == null ? Component.translatable("academy.preset.empty")
                    : definition == null ? Component.literal(id.toString()) : Component.translatable(definition.translation());
            lines.add(new Line(Component.translatable("academy.preset.slot", slot + 1,
                    AbilityControls.SLOTS[slot].getTranslatedKeyMessage(), name), id == null ? 0xaaaaaa : 0xffffff));
            if (id != null) {
                int cooldown = data.getCooldown(id);
                // 0.1秒単位で切り上げる。サーバーのtickが1つでも残っていれば、使える（0.0s）と見せてはいけない。
                String seconds = String.format(Locale.ROOT, "%.1f", Math.ceil(cooldown / 2.0) / 10.0);
                lines.add(new Line(Component.translatable("academy.hud.skill_state",
                        String.format(Locale.ROOT, "%.1f", data.getProficiency(id) * 100), seconds),
                        cooldown > 0 ? 0xffbb55 : 0x80ddff));
            }
        }
        return List.copyOf(lines);
    }
}
