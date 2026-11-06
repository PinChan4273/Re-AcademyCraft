package io.github.pinchan4273.reacademycraft.world.item;

import net.minecraft.world.InteractionResult;
import io.github.pinchan4273.reacademycraft.terminal.TerminalApp;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 原作ItemApp: 1回使うと、使った者の端末にそのアプリを入れる。入れる端末が無いとき、その端末が既にアプリを持つときはそう伝える。 */
public final class AppItem extends Item {
    private final TerminalApp app;
    public AppItem(TerminalApp app) { super(io.github.pinchan4273.reacademycraft.world.AcademyIds.item()); this.app = app; }
    public TerminalApp app() { return app; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var title = Component.translatable(app.titleKey());
        // 原作ItemApp.onItemRightClick: メッセージはチャットへ出し、何が起きてもSUCCESSを返す。
        if (!TerminalState.installed(player)) {
            player.sendSystemMessage(Component.translatable("academy.terminal.none"));
            return InteractionResult.SUCCESS;
        }
        if (TerminalState.has(player, app)) {
            player.sendSystemMessage(Component.translatable("academy.terminal.app_already_installed", title));
            return InteractionResult.SUCCESS;
        }
        TerminalState.installApp(player, app);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.sendSystemMessage(Component.translatable("academy.terminal.app_installed", title));
        if (player instanceof ServerPlayer owner) io.github.pinchan4273.reacademycraft.network.TerminalSnapshot.send(owner);
        return InteractionResult.SUCCESS;
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable(app.titleKey()));
    }
}
