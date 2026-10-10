package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 原作ItemTerminalInstaller: 1回使うと、使った者にデータ端末をインストールし、既にあるときはそう伝える。 */
public final class TerminalInstaller extends Item {
    public TerminalInstaller() { super(new Properties().stacksTo(1)); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        // 原作ItemTerminalInstaller.onItemRightClick: メッセージはチャットへ出し、何が起きてもSUCCESSを返す。
        if (TerminalState.installed(player)) {
            player.sendSystemMessage(Component.translatable("academy.terminal.already_installed"));
            return InteractionResultHolder.success(stack);
        }
        TerminalState.install(player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (player instanceof ServerPlayer owner) {
            io.github.pinchan4273.reacademycraft.network.TerminalSnapshot.send(owner);
            // 原作はここで何も言わない: クライアントがインストールの演出を再生し、端末を開き、チャットでそのキーを伝える。
            io.github.pinchan4273.reacademycraft.network.AcademyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> owner),
                    new io.github.pinchan4273.reacademycraft.network.TerminalInstalled());
        }
        return InteractionResultHolder.success(stack);
    }
}
