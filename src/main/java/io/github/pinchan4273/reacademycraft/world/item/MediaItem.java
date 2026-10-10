package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.media.MediaAcquired;
import io.github.pinchan4273.reacademycraft.terminal.TerminalApps;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 原作MediaItem: modに同梱のメディアの1つ。使うと、そのメディアをプレイヤーのメディアプレイヤーに入れ、クリエイティブ以外では使い切る。
 * プレイヤーがメディアプレイヤーのアプリを持たないとき、既にこのメディアを持つときは、原作と同じくチャットでそう伝える。
 *
 * 原作はダメージ値でメディアを選ぶ1つのアイテム。ここのアイテムはダメージ値を持たないので、各メディアを別のアイテムとし、
 * 原作の命名どおりメディアの名前を付ける。
 */
public final class MediaItem extends Item {
    private final String media;
    public MediaItem(String media) {
        super(new Properties().stacksTo(1));
        if (!MediaAcquired.INTERNAL.contains(media)) throw new IllegalArgumentException("Unknown media " + media);
        this.media = media;
    }
    public String media() { return media; }
    public static String nameKey(String media) { return "academy.media." + media + ".name"; }
    public static String descKey(String media) { return "academy.media." + media + ".desc"; }
    @Override public Component getName(ItemStack stack) { return Component.translatable(nameKey(media)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        var name = Component.translatable(nameKey(media));
        if (!TerminalState.has(player, TerminalApps.MEDIA_PLAYER)) {
            player.sendSystemMessage(Component.translatable("academy.media.notinstalled"));
        } else if (MediaAcquired.has(player, media)) {
            player.sendSystemMessage(Component.translatable("academy.media.haveone", name));
        } else {
            MediaAcquired.install(player, media);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.sendSystemMessage(Component.translatable("academy.media.acquired", name));
            if (player instanceof ServerPlayer owner) io.github.pinchan4273.reacademycraft.network.MediaSnapshot.send(owner);
        }
        // 原作は何が起きてもSUCCESSを返す。
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(descKey(media)));
        // 原作の楽曲は同梱しない。何も再生しないアイテムを故障と誤解されないよう、ここでもそう伝える。
        if (level != null && level.isClientSide && !net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> io.github.pinchan4273.reacademycraft.client.media.MediaLibrary.hasSound(media)))
            lines.add(Component.translatable("academy.media.missing_tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
