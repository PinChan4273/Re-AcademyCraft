package io.github.pinchan4273.reacademycraft.skill;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 今後のMagManipエンティティのための、サーバー専用の損失の無い積荷。取り込みでブロックを取り除くことはない。
 * 未知の版やレジストリ項目は、生の包みを読み取り専用で保つ。
 * ブロックエンティティのインベントリを送ったり、クライアントからサーバーへのメッセージで積荷のNBTを受け取ったりしない。
 *
 * <p>1.21: ブロックアイテムの状態と中身は、Forge 1.20.1版のNBT（BlockStateTag・BlockEntityTag）でなくData Component
 * （BLOCK_STATE・BLOCK_ENTITY_DATA）にある。さらに容器の中身・名前・鍵はBLOCK_ENTITY_DATAでなく、それぞれの部品
 * （CONTAINER・CUSTOM_NAME・LOCK）にあり、vanillaの設置はデータを読んだ後でアイテムの部品を当てる（無い部品は空・無しとして
 * 当てる）。アイテムからの取り込みと回収アイテムは、この1.21の形に合わせる。ブロックエンティティの保存と読み込みにはレジストリを渡す。
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
        if (entity != null) tag.put("block_entity", entity.saveWithFullMetadata(level.registryAccess()));
        return new BlockPayload(tag);
    }
    public static BlockPayload load(CompoundTag tag) { return new BlockPayload(tag); }
    /** アイテムの状態とコンテナのタグはサーバーでだけ解釈する。所有する積荷は、アイテムを正確に返すために元のアイテムNBTを別に保つ。 */
    public static Optional<BlockPayload> fromItem(net.minecraft.world.item.ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return Optional.empty();
        var stateTag = NbtUtils.writeBlockState(item.getBlock().defaultBlockState());
        var itemState = stack.get(DataComponents.BLOCK_STATE);
        var itemEntity = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (itemState != null && !itemState.isEmpty()) {
            var properties = stateTag.getCompoundOrEmpty("Properties").copy();
            itemState.properties().forEach(properties::putString); if (!properties.isEmpty()) stateTag.put("Properties", properties);
        }
        var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, stateTag);
        if (!NbtUtils.writeBlockState(state).equals(stateTag)) return Optional.empty();
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.put("state", stateTag);
        if (state.hasBlockEntity() && state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock block) {
            var entity = block.newBlockEntity(BlockPos.ZERO, state); if (entity == null) return Optional.empty();
            var data = entity.saveWithFullMetadata(registries);
            if (itemEntity != null) {
                // 1.21.1 の CustomData は id を含んでいた。下の種類の照合のため、同じ形で重ねる。
                var itemData = itemEntity.copyTagWithoutId();
                itemData.putString("id", String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(itemEntity.type())));
                data.merge(itemData);
            }
            // vanillaのBlockItem.placeと同じ順: データを読み、アイテムの部品（中身・名前など）を当てる。データが別の種類の
            // ブロックエンティティを名指すときは当てずに残し、下のresolveが拒む。
            if (entity.getType() == net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.tryParse(data.getStringOr("id", "")))) {
                entity.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, data));
                entity.applyComponentsFromItemStack(stack);
                data = entity.saveWithFullMetadata(registries);
            }
            tag.put("block_entity", data);
        } else if (itemEntity != null) return Optional.empty();
        var payload = new BlockPayload(tag); return payload.isSupported() ? Optional.of(payload) : Optional.empty();
    }
    public CompoundTag save() { return envelope.copy(); }
    public boolean isSupported() { return state != null; }
    public Optional<BlockState> state() { return Optional.ofNullable(state); }
    public Optional<CompoundTag> blockEntityData() {
        return io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(envelope, "block_entity", Tag.TAG_COMPOUND)
                ? Optional.of(envelope.getCompoundOrEmpty("block_entity").copy()) : Optional.empty();
    }
    /**
     * 設置できないときも、標準のブロックアイテムの形で積荷を回収できるようにする。1.21ではvanillaのsaveToItem（データ付きで
     * 拾ったブロックと同じ形）: 中身・名前は部品、残りはBLOCK_ENTITY_DATA。普通に置き直すと、そのまま元に戻る。
     */
    public net.minecraft.world.item.ItemStack recoveryItem(HolderLookup.Provider registries) {
        if (state == null) return net.minecraft.world.item.ItemStack.EMPTY;
        var item = new net.minecraft.world.item.ItemStack(state.getBlock());
        if (item.isEmpty()) return item;
        var properties = envelope.getCompoundOrEmpty("state").getCompoundOrEmpty("Properties");
        if (!properties.isEmpty()) {
            var values = new java.util.LinkedHashMap<String, String>();
            for (var key : properties.keySet()) values.put(key, properties.getStringOr(key, ""));
            item.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(values));
        }
        if (blockEntityData().isPresent()) {
            BlockEntity entity = null;
            try { entity = createBlockEntity(BlockPos.ZERO, registries); } catch (RuntimeException e) { entity = null; }
            if (entity != null) io.github.pinchan4273.reacademycraft.world.NbtCompat.saveToItem(entity, item, registries);
            else {
                var unknown = blockEntityData().orElseThrow();
                var unknownType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.tryParse(unknown.getStringOr("id", "")));
                if (unknownType != null) item.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.TypedEntityData.of(unknownType, unknown));
            }
        }
        return item;
    }
    /** 設置もワールドの変更もせずに、独立した置き先のインスタンスを作る。 */
    public @Nullable BlockEntity createBlockEntity(BlockPos destination, HolderLookup.Provider registries) {
        if (state == null) throw new IllegalStateException("Unsupported block cargo must remain preserved");
        if (!state.hasBlockEntity()) return null;
        var tag = envelope.getCompoundOrEmpty("block_entity").copy();
        tag.putInt("x", destination.getX()); tag.putInt("y", destination.getY()); tag.putInt("z", destination.getZ());
        var entity = BlockEntity.loadStatic(destination, state, tag, registries);
        if (entity == null) throw new IllegalStateException("Block entity could not be restored; retain original cargo");
        return entity;
    }
    private static @Nullable BlockState resolve(CompoundTag tag) {
        if (!io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(tag, "schema_version", Tag.TAG_INT) || tag.getIntOr("schema_version", 0) != 1
                || !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(tag, "state", Tag.TAG_COMPOUND)) return null;
        var rawState = tag.getCompoundOrEmpty("state"); var id = Identifier.tryParse(rawState.getStringOr("Name", ""));
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)) return null;
        var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, rawState);
        // 既定のプロパティや空気への損失のある代替を認めず、古い積荷を書き換えない。
        if (state.isAir() || !NbtUtils.writeBlockState(state).equals(rawState)) return null;
        boolean hasData = io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(tag, "block_entity", Tag.TAG_COMPOUND);
        if (state.hasBlockEntity() != hasData || (tag.contains("block_entity") && !hasData)) return null;
        if (hasData) {
            var entityId = Identifier.tryParse(tag.getCompoundOrEmpty("block_entity").getStringOr("id", ""));
            var type = entityId == null ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(entityId);
            if (type == null || !type.isValid(state)) return null;
        }
        return state;
    }
}
