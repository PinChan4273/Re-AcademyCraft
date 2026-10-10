package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.terminal.TerminalApp;
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

/** 原作ItemApp: 1回使うと、使った者の端末にそのアプリを入れる。入れる端末が無いとき、その端末が既にアプリを持つときはそう伝える。 */
public final class AppItem extends Item {
    private final TerminalApp app;
    public AppItem(TerminalApp app) { super(new Properties()); this.app = app; }
    public TerminalApp app() { return app; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        var title = Component.translatable(app.titleKey());
        // 原作ItemApp.onItemRightClick: メッセージはチャットへ出し、何が起きてもSUCCESSを返す。
        if (!TerminalState.installed(player)) {
            player.sendSystemMessage(Component.translatable("academy.terminal.none"));
            return InteractionResultHolder.success(stack);
        }
        if (TerminalState.has(player, app)) {
            player.sendSystemMessage(Component.translatable("academy.terminal.app_already_installed", title));
            return InteractionResultHolder.success(stack);
        }
        TerminalState.installApp(player, app);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.sendSystemMessage(Component.translatable("academy.terminal.app_installed", title));
        if (player instanceof ServerPlayer owner) io.github.pinchan4273.reacademycraft.network.TerminalSnapshot.send(owner);
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(app.titleKey()));
    }
}
