package io.github.pinchan4273.reacademycraft.world.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 原作ItemMagneticCoil: 上級開発機でメインハンドに持つと、ページが能力を別のカテゴリへリセットするコンソールに変わる
 * （DevelopmentSession.RESET）。ツールチップは原作のもので、<br>ごとに1行。
 */
public final class MagneticCoil extends Item {
    public MagneticCoil() { super(new Properties()); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        for (var line : Component.translatable(getDescriptionId() + ".desc").getString().split("<br>"))
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, lines, flag);
    }
}
