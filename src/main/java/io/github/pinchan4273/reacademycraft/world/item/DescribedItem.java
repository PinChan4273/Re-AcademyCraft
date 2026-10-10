package io.github.pinchan4273.reacademycraft.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 説明文を持てるアイテム。原作は因子・開発機・磁気コイルなどが、それぞれのaddInformationで
 * 「item.ac_xxx.desc」の説明を出していた。ここではアイテムの説明ID（item.academy.xxx）に「.desc」を
 * 付けたキーが言語ファイルにあれば、その説明をツールチップに灰色で1行加える。キーが無ければ何も加えない。
 */
public class DescribedItem extends Item {
    public DescribedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        String descriptionKey = getDescriptionId() + ".desc";
        if (Language.getInstance().has(descriptionKey)) {
            lines.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        }
    }
}
