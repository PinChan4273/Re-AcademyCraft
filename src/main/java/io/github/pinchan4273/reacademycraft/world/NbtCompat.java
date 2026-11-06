package io.github.pinchan4273.reacademycraft.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 1.21.1 の CompoundTag で書いた保存の処理を、形を変えずに 26.2 の入口へつなぐ補助。
 * 保存の互換のため、BE と実体の本体は CompoundTag のまま読み書きする（キーも形も 1.21.1 と同じ）。
 */
public final class NbtCompat {
    /** 1.21.1 の Tag.TAG_ANY_NUMERIC（数値の型ならどれでもよい）。 */
    public static final int TAG_ANY_NUMERIC = 99;
    /** 入力の全部を 1 つの CompoundTag として読む。 */
    @SuppressWarnings("deprecation")
    private static final MapCodec<CompoundTag> WHOLE = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);

    private NbtCompat() { }

    /** 1.21.1 の CompoundTag#contains(名前, 型) と同じ。 */
    public static boolean contains(CompoundTag tag, String name, int type) {
        Tag value = tag.get(name);
        if (value == null) return false;
        return type == TAG_ANY_NUMERIC ? value instanceof NumericTag : value.getId() == type;
    }

    /** ValueInput の全部のキーを CompoundTag として取り出す（1.21.1 の loadAdditional が受け取った tag と同じ内容）。 */
    @SuppressWarnings("deprecation")
    public static CompoundTag readAll(ValueInput input) {
        return input.read(WHOLE).orElseGet(CompoundTag::new);
    }

    /** 書き出しの時の registries。ValueOutput は持たないので BE の世界から取る。世界の無い BE はサーバーのもの。 */
    public static HolderLookup.Provider registries(BlockEntity blockEntity) {
        if (blockEntity.getLevel() != null) return blockEntity.getLevel().registryAccess();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException("No registries to save " + blockEntity.getNameForReporting());
        return server.registryAccess();
    }

    /** 1.21.1 の serializeNBT(registries) に当たる（ItemStackHandler の Items と Size の形はそのまま）。 */
    public static CompoundTag save(ValueIOSerializable value, HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        value.serialize(output);
        return output.buildResult();
    }

    /** 1.21.1 の deserializeNBT(registries, tag) に当たる。 */
    public static void load(ValueIOSerializable value, HolderLookup.Provider registries, CompoundTag tag) {
        value.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    /** 1.21.1 の Entity#saveWithoutId(new CompoundTag()) に当たる。 */
    public static CompoundTag saveEntity(net.minecraft.world.entity.Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    /** 1.21.1 の BlockEntity#saveToItem に当たる（本体は同じ手順）。 */
    public static void saveToItem(BlockEntity entity, ItemStack stack, HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        entity.saveCustomOnly(output);
        entity.removeComponentsFromTag(output);
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, entity.getType(), output);
        stack.applyComponents(entity.collectComponents());
    }

    /** 1.21.1 の ItemStack#save(registries) に当たる。空の stack は呼ばない前提は 1.21.1 と同じ。 */
    public static CompoundTag saveStack(ItemStack stack, HolderLookup.Provider registries) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    /** 1.21.1 の ItemStack#parseOptional(registries, tag) に当たる（読めなければ空）。 */
    public static ItemStack parseStack(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
    }
}
