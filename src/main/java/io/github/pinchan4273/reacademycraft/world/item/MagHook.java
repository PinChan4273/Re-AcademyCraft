package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 原作ItemMagHook: 右クリックで投げ、クリエイティブ以外では消費する。 */
public final class MagHook extends Item {
    public MagHook() { super(new Properties()); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        // 原作はバニラの卵を投げる音を半分の音量で、バニラが投擲に付けるピッチで鳴らす。
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW,
                SoundSource.PLAYERS, .5f, .4f / (level.getRandom().nextFloat() * .4f + .8f));
        if (!level.isClientSide) {
            level.addFreshEntity(MagHookEntity.throwFrom(player));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    /** 原作は手に持ったフックをアイコンではなく独自のモデルで、手と地面で描く。 */
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new io.github.pinchan4273.reacademycraft.client.MagHookRenderer.Held();
                return renderer;
            }
        });
    }
}
