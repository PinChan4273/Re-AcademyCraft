package io.github.pinchan4273.reacademycraft.world;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 26.2 は Item.Properties と BlockBehaviour.Properties に登録の ID（setId）が必須。
 * 構築子の引数を増やさないよう、登録の supplier を実行している間だけ、今の ID をここに持つ。
 */
public final class AcademyIds {
    private static final ThreadLocal<Identifier> CURRENT = new ThreadLocal<>();

    private AcademyIds() { }

    /** {@code register.register(id, supplier)} と同じ。supplier の中で {@link #item()} と {@link #block()} が使える。 */
    public static <R, T extends R> DeferredHolder<R, T> register(DeferredRegister<R> register, String id, Supplier<? extends T> supplier) {
        Identifier full = Identifier.fromNamespaceAndPath(register.getNamespace(), id);
        return register.register(id, () -> {
            CURRENT.set(full);
            try {
                return supplier.get();
            } finally {
                CURRENT.remove();
            }
        });
    }

    private static Identifier current() {
        Identifier id = CURRENT.get();
        if (id == null) throw new IllegalStateException("Properties made outside AcademyIds.register");
        return id;
    }

    public static ResourceKey<Item> itemKey() { return ResourceKey.create(Registries.ITEM, current()); }

    public static ResourceKey<Block> blockKey() { return ResourceKey.create(Registries.BLOCK, current()); }

    public static Item.Properties item() { return new Item.Properties().setId(itemKey()); }

    /** BlockItem 用。1.21.1 と同じくブロックの翻訳キー（block.academy.x）を使う。 */
    public static Item.Properties blockItem() { return item().useBlockDescriptionPrefix(); }

    public static BlockBehaviour.Properties block() { return BlockBehaviour.Properties.of().setId(blockKey()); }
}
