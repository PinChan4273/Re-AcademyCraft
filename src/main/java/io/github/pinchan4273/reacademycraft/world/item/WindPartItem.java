package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.world.WindGeneratorRules;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** 設置前の組み立ての説明。ワールドやスタックのデータを読み書きしない。 */
public class WindPartItem extends BlockItem {
    public WindPartItem(Block block, Properties properties) { super(block, properties); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("academy.wind.assembly.order", WindGeneratorRules.MIN_PILLARS,
                WindGeneratorRules.MAX_PILLARS).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("academy.wind.assembly.fan").withStyle(ChatFormatting.GRAY));
    }
}
