package io.github.pinchan4273.reacademycraft.world.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;

/**
 * 原作ItemTutorial（ミサカクラウドの端末）: 使うとクライアントでミサカクラウドを開き、サーバーが原作のopen_misaka_cloudの進捗
 * （"First Contact"）を与える。
 */
public final class TutorialItem extends Item {
    public TutorialItem() { super(new Properties()); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) io.github.pinchan4273.reacademycraft.client.TutorialScreen.open();
        } else if (player instanceof ServerPlayer owner) {
            io.github.pinchan4273.reacademycraft.AcademyAdvancements.grant(owner, "open_misaka_cloud");
        }
        return InteractionResultHolder.success(stack);
    }
}
