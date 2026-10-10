package io.github.pinchan4273.reacademycraft.skill;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 今後のMagManipエンティティのための、サーバー専用の損失の無い積荷。取り込みでブロックを取り除くことはない。
 * 未知の版やレジストリ項目は、生の包みを読み取り専用で保つ。
 * ブロックエンティティのインベントリを送ったり、クライアントからサーバーへのメッセージで積荷のNBTを受け取ったりしない。
 */
public final class BlockPayload {
    private final CompoundTag envelope;
    private final @Nullable BlockState state;
    private BlockPayload(CompoundTag tag) {
        envelope = tag.copy(); state = resolve(envelope);
    }
    public static BlockPayload capture(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) throw new IllegalArgumentException("Cannot capture an unloaded block");
        var tag = new CompoundTag(); tag.putInt("schema_version", 1);
        tag.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
        var entity = level.getBlockEntity(pos);
        if (entity != null) tag.put("block_entity", entity.saveWithFullMetadata());
        return new BlockPayload(tag);
    }
    public static BlockPayload load(CompoundTag tag) { return new BlockPayload(tag); }
    /** アイテムの状態とコンテナのタグはサーバーでだけ解釈する。所有する積荷は、アイテムを正確に返すために元のアイテムNBTを別に保つ。 */
    public static Optional<BlockPayload> fromItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return Optional.empty();
        var stateTag = NbtUtils.writeBlockState(item.getBlock().defaultBlockState());
        var itemTag = stack.getTag();
        if (itemTag != null && itemTag.contains("BlockStateTag")) {
            if (!itemTag.contains("BlockStateTag", Tag.TAG_COMPOUND)) return Optional.empty();
            var properties = stateTag.getCompound("Properties").copy();
            properties.merge(itemTag.getCompound("BlockStateTag")); if (!properties.isEmpty()) stateTag.put("Properties", properties);
        }
        var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), stateTag);
        if (!NbtUtils.writeBlockState(state).equals(stateTag)) return Optional.empty();
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.put("state", stateTag);
        if (state.hasBlockEntity() && state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock block) {
            var entity = block.newBlockEntity(BlockPos.ZERO, state); if (entity == null) return Optional.empty();
            var data = entity.saveWithFullMetadata();
            if (itemTag != null && itemTag.contains("BlockEntityTag")) {
                if (!itemTag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) return Optional.empty();
                data.merge(itemTag.getCompound("BlockEntityTag"));
            }
            tag.put("block_entity", data);
        } else if (itemTag != null && itemTag.contains("BlockEntityTag")) return Optional.empty();
        var payload = new BlockPayload(tag); return payload.isSupported() ? Optional.of(payload) : Optional.empty();
    }
    public CompoundTag save() { return envelope.copy(); }
    public boolean isSupported() { return state != null; }
    public Optional<BlockState> state() { return Optional.ofNullable(state); }
    public Optional<CompoundTag> blockEntityData() {
        return envelope.contains("block_entity", Tag.TAG_COMPOUND)
                ? Optional.of(envelope.getCompound("block_entity").copy()) : Optional.empty();
    }
    /** 設置できないときも、標準のブロックアイテムのNBTで積荷を回収できるようにする。 */
    public net.minecraft.world.item.ItemStack recoveryItem() {
        if (state == null) return net.minecraft.world.item.ItemStack.EMPTY;
        var item = new net.minecraft.world.item.ItemStack(state.getBlock());
        if (item.isEmpty()) return item;
        var properties = envelope.getCompound("state").getCompound("Properties");
        if (!properties.isEmpty()) item.getOrCreateTag().put("BlockStateTag", properties.copy());
        blockEntityData().ifPresent(data -> item.getOrCreateTag().put("BlockEntityTag", data));
        return item;
    }
    /** 設置もワールドの変更もせずに、独立した置き先のインスタンスを作る。 */
    public @Nullable BlockEntity createBlockEntity(BlockPos destination) {
        if (state == null) throw new IllegalStateException("Unsupported block cargo must remain preserved");
        if (!state.hasBlockEntity()) return null;
        var tag = envelope.getCompound("block_entity").copy();
        tag.putInt("x", destination.getX()); tag.putInt("y", destination.getY()); tag.putInt("z", destination.getZ());
        var entity = BlockEntity.loadStatic(destination, state, tag);
        if (entity == null) throw new IllegalStateException("Block entity could not be restored; retain original cargo");
        return entity;
    }
    private static @Nullable BlockState resolve(CompoundTag tag) {
        if (!tag.contains("schema_version", Tag.TAG_INT) || tag.getInt("schema_version") != 1
                || !tag.contains("state", Tag.TAG_COMPOUND)) return null;
        var rawState = tag.getCompound("state"); var id = ResourceLocation.tryParse(rawState.getString("Name"));
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) return null;
        var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), rawState);
        // 既定のプロパティや空気への損失のある代替を認めず、古い積荷を書き換えない。
        if (state.isAir() || !NbtUtils.writeBlockState(state).equals(rawState)) return null;
        boolean hasData = tag.contains("block_entity", Tag.TAG_COMPOUND);
        if (state.hasBlockEntity() != hasData || (tag.contains("block_entity") && !hasData)) return null;
        if (hasData) {
            var entityId = ResourceLocation.tryParse(tag.getCompound("block_entity").getString("id"));
            var type = entityId == null ? null : ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(entityId);
            if (type == null || !type.isValid(state)) return null;
        }
        return state;
    }
}
