package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 物質ユニット（原作ItemMatterUnit、WeAthFolD）。1ユニットずつの変換と、クリエイティブでの返却の規則。中身ごとに別のアイテムにしている。
 * ワールドとインベントリは、許可と受け渡しの確認の後でサーバーでだけ確定する。
 */
public final class MatterUnit extends Item {
    private static final Set<UUID> ACTIVE = new HashSet<>(); // サーバースレッドでの再入の防止。
    private final boolean filled;
    public MatterUnit(boolean filled) { super(new Properties().stacksTo(16)); this.filled = filled; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        var held = player.getItemInHand(hand);
        var hit = itemRay(level, player, filled ? ClipContext.Fluid.NONE : ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(held);
        if (!filled && !isSource(level, hit.getBlockPos())) return InteractionResultHolder.pass(held);
        if (level.isClientSide) return InteractionResultHolder.success(held); // 予測のみ。ローカルのアイテム・ブロックは変更しない。
        var result = player instanceof ServerPlayer server ? interact(server, hand, hit) : InteractionResult.FAIL;
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
    private static boolean isSource(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(PhaseContent.BLOCK.get()) && level.getFluidState(pos).isSource();
    }
    private static boolean allowed(ServerPlayer p, BlockPos pos, Direction face, ItemStack stack) {
        var l = p.serverLevel();
        return p.isAlive() && !p.isSpectator() && p.mayBuild() && !l.isOutsideBuildHeight(pos)
                && l.hasChunkAt(pos) && l.getWorldBorder().isWithinBounds(pos) && l.mayInteract(p, pos) && p.mayUseItemAt(pos, face, stack);
    }
    /** 通常のアイテム使用の経路は、サーバーで独自に光線を求める。独自のクライアントの位置パケットは使わない。 */
    public InteractionResult interact(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
        var l = p.serverLevel(); var stack = p.getItemInHand(hand); var clicked = hit.getBlockPos(); var face = hit.getDirection();
        if (!stack.is(this) || hit.getType() != HitResult.Type.BLOCK || l.captureBlockSnapshots || l.restoringBlockSnapshots
                || !l.capturedBlockSnapshots.isEmpty() || !allowed(p, clicked, face, stack)
                || itemReach(p) <= 0 || !(p.getEyePosition().distanceToSqr(hit.getLocation()) <= itemReach(p) * itemReach(p))
                || !ACTIVE.add(p.getUUID())) return InteractionResult.FAIL;
        try { return transfer(p, hand, hit, stack); }
        finally { ACTIVE.remove(p.getUUID()); }
    }
    private InteractionResult transfer(ServerPlayer p, InteractionHand hand, BlockHitResult hit, ItemStack stack) {
        var l = p.serverLevel(); var clicked = hit.getBlockPos(); var face = hit.getDirection();
        var pos = filled && !l.getBlockState(clicked).canBeReplaced() ? clicked.relative(face) : clicked;
        if (!allowed(p, pos, face, stack)) return InteractionResult.FAIL;
        var old = l.getBlockState(pos);
        if (filled ? (!old.canBeReplaced() || old.hasBlockEntity() || !old.getFluidState().isEmpty()) : !isSource(l, pos)) return InteractionResult.FAIL;
        var heldBefore = stack.copy(); boolean creative = p.isCreative();
        // 源だけを対象にするのも原作のゲームプレイ: Forge 1.12のBlockFluidClassic.canCollideCheckは流れているブロックを拒む。
        if (!filled && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l, pos, old, p)).isCanceled()) return InteractionResult.FAIL;
        if (!unchanged(p, hand, stack, heldBefore, creative) || l.getBlockState(pos) != old || !contextValid(p, l, hit, pos, stack)) return InteractionResult.FAIL;
        var replacement = filled ? PhaseContent.BLOCK.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
        var before = BlockSnapshot.create(l.dimension(), l, pos);
        boolean committed = false; ItemEntity dropped = null;
        l.captureBlockSnapshots = true;
        try {
            if (!l.setBlock(pos, replacement, Block.UPDATE_ALL)) return InteractionResult.FAIL;
            if (filled && EventHooks.onBlockPlace(p, before, face)) return InteractionResult.FAIL;
            if (!unchanged(p, hand, stack, heldBefore, creative) || l.getBlockState(pos) != replacement || !contextValid(p, l, hit, pos, stack)) return InteractionResult.FAIL;
            var result = new ItemStack(filled ? PhaseContent.EMPTY.get() : PhaseContent.FILLED.get());
            // 原作PlayerUtils.mergeStackable: ユニットはメインインベントリのどこかにある空きのある同種のスタックに加わり、無ければ最初の空きスロット、
            // それも無ければドロップする。その後で手に持ったものを使い切る。そのため1個のユニットの結果は別のスロットに入り、その手は空になる。
            int slot = -1;
            for (int i = 0; i < 36 && slot < 0; i++) {
                var target = p.getInventory().getItem(i);
                if (ItemStack.isSameItemSameComponents(target, result) && target.getCount() < target.getMaxStackSize()) slot = i;
            }
            if (slot < 0) slot = p.getInventory().getFreeSlot();
            if (slot < 0) {
                dropped = new ItemEntity(l, p.getX(), p.getY() + .5, p.getZ(), result.copy());
                dropped.setDefaultPickUpDelay();
                if (!l.addFreshEntity(dropped) || dropped.isRemoved() || !ItemStack.matches(dropped.getItem(), result)
                        || !unchanged(p, hand, stack, heldBefore, creative) || l.getBlockState(pos) != replacement
                        || !contextValid(p, l, hit, pos, stack)) return InteractionResult.FAIL;
            }
            if (!creative) stack.shrink(1);
            if (slot >= 0) {
                var target = p.getInventory().getItem(slot);
                if (target.isEmpty()) p.getInventory().setItem(slot, result); else target.grow(1);
            }
            p.getInventory().setChanged(); committed = true;
        } finally {
            l.captureBlockSnapshots = false;
            if (!committed) {
                if (dropped != null) dropped.discard();
                l.restoringBlockSnapshots = true;
                try { for (int i = l.capturedBlockSnapshots.size() - 1; i >= 0; i--) l.capturedBlockSnapshots.get(i).restore(net.minecraft.world.level.block.Block.UPDATE_CLIENTS); } // Forge版のrestore(true, false)（強制、フラグ2）
                finally { l.restoringBlockSnapshots = false; }
            }
            l.capturedBlockSnapshots.clear();
        }
        replacement.onPlace(l, pos, old, false);
        l.markAndNotifyBlock(pos, l.getChunkAt(pos), old, l.getBlockState(pos), Block.UPDATE_ALL, 512);
        // 原作ItemMatterUnitは置き換え可能なブロックの上に液体を置き、それについては何もドロップしない。
        l.playSound(null, pos, filled ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1, 1);
        NeoForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.world.event.MatterUnitHarvestEvent(p, pos, !filled));
        return InteractionResult.CONSUME;
    }
    /** 1.21.1がクリエイティブのプレイヤーのBLOCK_INTERACTION_RANGEに付ける+0.5の修飾子（ServerPlayer.updatePlayerAttributes）。 */
    private static final net.minecraft.resources.ResourceLocation CREATIVE_RANGE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("creative_mode_block_range");
    /**
     * アイテムの光線の長さ。Forge 1.20.1版はItem.getPlayerPOVHitResultの光線（ForgeのパッチでBLOCK_REACHの値 + 0.5）に合わせ、
     * 既定ではどちらのゲームモードでも5.0だった（ForgeはクリエイティブのBLOCK_REACHに何も足さない）。1.21.1のgetPlayerPOVHitResultは
     * blockInteractionRange()（サバイバル4.5、クリエイティブは修飾子で5.0）なので、そのままではサバイバルで0.5短くなる。
     * Forge版の長さを保つため、BLOCK_INTERACTION_RANGE（ForgeのBLOCK_REACHに当たる）からクリエイティブの修飾子を除き、0.5を足す。
     * 修飾子は加算なので、掛け算の修飾子（ほかのmod）が同時にあるときだけ、除いた値がForge版の値とずれうる。
     */
    private static double itemReach(net.minecraft.world.entity.player.Player p) {
        var range = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        if (range == null) return 0;
        double raw = range.getValue();
        var creative = range.getModifier(CREATIVE_RANGE);
        if (creative != null && creative.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) raw -= creative.amount();
        return raw > 0 ? raw + .5 : 0; // 射程0での無効化の取り決めを守る（Forge版と同じ）。
    }
    /** Item.getPlayerPOVHitResultと同じ光線で、長さだけForge版（itemReach）。 */
    private static BlockHitResult itemRay(Level level, net.minecraft.world.entity.player.Player p, ClipContext.Fluid fluid) {
        var eye = p.getEyePosition();
        var end = eye.add(p.calculateViewVector(p.getXRot(), p.getYRot()).scale(itemReach(p)));
        return level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, p));
    }
    // Forgeのリスナーはプレイヤーを動かし、ディメンションや射程を変え、編集の権限を取り消せる。
    // 取り消し可能・外部のコールバックの後には毎回、操作の文脈全体を検証し直す。
    private static boolean contextValid(ServerPlayer p, Level originalLevel, BlockHitResult hit, BlockPos destination, ItemStack stack) {
        return p.serverLevel() == originalLevel && itemReach(p) > 0
                && p.getEyePosition().distanceToSqr(hit.getLocation()) <= itemReach(p) * itemReach(p)
                && allowed(p, hit.getBlockPos(), hit.getDirection(), stack) && allowed(p, destination, hit.getDirection(), stack);
    }
    private static boolean unchanged(ServerPlayer p, InteractionHand hand, ItemStack reference, ItemStack before, boolean creative) {
        return p.getItemInHand(hand) == reference && ItemStack.matches(reference, before) && p.isCreative() == creative && p.isAlive() && !p.isSpectator();
    }
}
