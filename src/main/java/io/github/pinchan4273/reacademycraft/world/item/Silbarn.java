package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 原作ItemSilbarn（WeAthFolD）: 右クリックで1つ投げ、クリエイティブ以外では消費する。生成したSilbarnを読むのはRay Barrageだけで、
 * 他にこのアイテムを必要とするものは無い。
 */
public final class Silbarn extends DescribedItem {
    public Silbarn() { super(new Properties()); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        // 原作の投げる音（SoundEvents.ENTITY_EGG_THROW）。砕ける音はここでは鳴らさず、エンティティ側で鳴らす。
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW,
                SoundSource.PLAYERS, .5f, .4f / (level.getRandom().nextFloat() * .4f + .8f));
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            var entity = SilbarnEntity.throwFrom(server);
            entity.setInvulnerable(true);
            level.addFreshEntity(entity);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    // 原作は手と地面で独自のモデルで描く。その描画（クライアント側の拡張）は、client/ClientExtensionsRegistrationで登録する。
}
