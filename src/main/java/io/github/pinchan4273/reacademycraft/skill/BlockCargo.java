package io.github.pinchan4273.reacademycraft.skill;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

/**
 * ワールドから取り除いたブロックの、サーバースレッドでの所有権トークン。Magnetic Manipulationが使う。所有するエンティティは
 * このトークンを保存し、設置やスポーンに失敗したときも保持しなければならない。トークンの直列化は、所有者を増やす許可ではない。
 * インベントリのNBTはクライアントへ送らない。原作の意図: WeAthFolD/PaindarのEntityBlock。
 */
public final class BlockCargo {
    private final CompoundTag envelope;
    private final BlockPayload payload;
    private final boolean supported;
    private BlockCargo(CompoundTag tag) {
        envelope = tag.copy(); payload = BlockPayload.load(envelope.getCompound("payload"));
        supported = envelope.contains("schema_version", Tag.TAG_INT) && envelope.getInt("schema_version") == 1
                && envelope.contains("spent", Tag.TAG_BYTE) && envelope.contains("payload", Tag.TAG_COMPOUND) && payload.isSupported()
                && validSourceItem();
    }
    public static BlockCargo load(CompoundTag tag) { return new BlockCargo(tag); }
    public CompoundTag save() { return envelope.copy(); }
    public BlockPayload payload() { return payload; }
    public boolean isAvailable() { return supported && !envelope.getBoolean("spent"); }
    /** 完全に理解できたトークンだけを、確実に使用済みとみなしてよい。 */
    public boolean isSpent() { return supported && envelope.getBoolean("spent"); }
    /** 空のインベントリスロットが1つ必要。失敗したらトークン全体を保つ。 */
    public boolean recover(ServerPlayer player) {
        if (!isAvailable() || !player.isAlive() || player.isSpectator()) return false;
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) return false;
        var item = recoveryItem(); if (item.isEmpty()) return false;
        player.getInventory().setItem(slot, item); envelope.putBoolean("spent", true);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); return true;
    }
    private boolean validSourceItem() {
        if (!envelope.contains("source_item")) return true;
        if (!envelope.contains("source_item", Tag.TAG_COMPOUND)) return false;
        var stack = net.minecraft.world.item.ItemStack.of(envelope.getCompound("source_item"));
        return stack.getCount() == 1 && payload.state().isPresent() && stack.is(payload.state().orElseThrow().getBlock().asItem());
    }
    private net.minecraft.world.item.ItemStack recoveryItem() {
        return envelope.contains("source_item", Tag.TAG_COMPOUND)
                ? net.minecraft.world.item.ItemStack.of(envelope.getCompound("source_item")) : payload.recoveryItem();
    }
    /** 保存されるエンティティがスポーン許可のイベントを通過した後にだけ呼ぶ。 */
    public static Optional<BlockCargo> takeHand(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()) return Optional.empty();
        var stack = player.getMainHandItem(); var candidate = BlockPayload.fromItem(stack);
        if (candidate.isEmpty() || !movable(player, player.blockPosition(), candidate.get().state().orElseThrow())) return Optional.empty();
        var original = stack.copy(); original.setCount(1);
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putBoolean("spent", false);
        tag.put("payload", candidate.get().save()); tag.put("source_item", original.save(new CompoundTag()));
        var cargo = new BlockCargo(tag); if (!cargo.isAvailable()) return Optional.empty();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); return Optional.of(cargo);
    }
    private static boolean movable(ServerPlayer player, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && key.getNamespace().equals("minecraft") && MagneticTargets.isMetal(state)
                && !(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                && !(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) && state.getDestroySpeed(player.level(), pos) >= 0
                && (!(state.getBlock() instanceof PistonBaseBlock) || !state.getValue(PistonBaseBlock.EXTENDED));
    }

    /** 元のインベントリを移す。採掘やドロップはしない。呼び出し元が唯一の所有者になる。 */
    public static Optional<BlockCargo> take(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!allowed(player, pos) || player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 100) return Optional.empty();
        var state = level.getBlockState(pos);
        // バニラの原作の金属は単一ブロック。伸びたピストンは2つ目のブロックを持つ。
        // TODO: タグ付きのmodブロックを有効にする前に、他modの移動可能・マルチブロックの取り決めを明示する。
        if (!movable(player, pos, state)) return Optional.empty();
        var payload = BlockPayload.capture(level, pos);
        if (!payload.isSupported()) return Optional.empty();
        // 許可のリスナーがワールド自体を変えることがある。その場合は新しい状態に手を出さない。
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))
                || !payload.save().equals(BlockPayload.capture(level, pos).save())) return Optional.empty();
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putBoolean("spent", false); tag.put("payload", payload.save());
        var cargo = new BlockCargo(tag);
        // 先にエンティティを除くことで、Container.onRemoveが取り込んだインベントリをこぼすのを防ぐ。
        // chunkのメソッドを使い、出力側の隣のコールバックが途中のコンテナを見られないようにする。
        level.getChunkAt(pos).removeBlockEntity(pos);
        if (!level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL)) {
            var restored = payload.createBlockEntity(pos); if (restored != null) level.setBlockEntity(restored);
            return Optional.empty();
        }
        return Optional.of(cargo);
    }

    /**
     * この状態の位置へブロックを置けるか: 空のマス、または通常の設置が置き換えるもの（草、1層の雪）を持つマス（ディスペンサーと
     * 同じ判定）。液体には置かない: 水の中で止まったブロックは、従来どおり所有者が回収できるよう保つ。
     */
    public boolean fits(net.minecraft.world.level.Level level, BlockPos pos) {
        var there = level.getBlockState(pos);
        if (there.isAir()) return true;
        return there.getFluidState().isEmpty()
                && there.canBeReplaced(new net.minecraft.world.item.context.DirectionalPlaceContext(level, pos, Direction.DOWN, recoveryItem(), Direction.UP));
    }

    /**
     * 空いた位置に置く。取り消しや失敗では、再試行のためにこの積荷を保つ。
     * 使用済みのトークン（NBTから読み直したものを含む）は2回置けない。
     */
    public boolean place(ServerPlayer player, BlockPos pos, Direction face) {
        var level = player.serverLevel();
        if (!isAvailable() || !allowed(player, pos) || !fits(level, pos)) return false;
        var state = payload.state().orElseThrow();
        if (!state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) return false;
        // ワールドを変える前に、復元を検証する。
        var entity = payload.createBlockEntity(pos);
        var before = BlockSnapshot.create(level.dimension(), level, pos);
        boolean placed = false;
        level.captureBlockSnapshots = true;
        try {
            if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return false;
            if (entity != null) level.setBlockEntity(entity);
            state.getBlock().setPlacedBy(level, pos, state, player, recoveryItem());
            // Forgeの設置のタイミングに合わせる: 提案する状態を保護のリスナーに見せるが、通常のonPlace・クライアント・隣への通知は
            // 受け入れられるまで遅らせる。
            if (ForgeEventFactory.onBlockPlace(player, before, face)) return false;
            if (!level.getBlockState(pos).equals(state)) return false;
            envelope.putBoolean("spent", true); placed = true;
        } finally {
            level.captureBlockSnapshots = false;
            if (!placed) {
                // 戻すときに、提案したコンテナの写したアイテムをドロップしない。
                level.getChunkAt(pos).removeBlockEntity(pos);
                level.restoringBlockSnapshots = true;
                try {
                    for (int i = level.capturedBlockSnapshots.size() - 1; i >= 0; i--)
                        level.capturedBlockSnapshots.get(i).restore(true, false);
                } finally { level.restoringBlockSnapshots = false; }
            }
            level.capturedBlockSnapshots.clear();
        }
        state.onPlace(level, pos, before.getReplacedBlock(), false);
        level.markAndNotifyBlock(pos, level.getChunkAt(pos), before.getReplacedBlock(), level.getBlockState(pos), Block.UPDATE_ALL, 512);
        if (entity != null) entity.setChanged();
        return true;
    }
    private static boolean allowed(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return player.isAlive() && !player.isSpectator() && player.mayBuild() && !level.isOutsideBuildHeight(pos)
                && level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                && !level.captureBlockSnapshots && !level.restoringBlockSnapshots && level.capturedBlockSnapshots.isEmpty();
    }
}
