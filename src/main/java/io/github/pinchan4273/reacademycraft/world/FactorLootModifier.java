package io.github.pinchan4273.reacademycraft.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 原作ACItemAdditionalRegistryのチェストの因子の抽選を、NeoForge（Forge版はForge）の戦利品データで組み立てたもの。
 * バニラや他のmodifierの内容を置き換えずに結果を追加する。1.21: codecはMapCodec、戦利品テーブルはResourceKeyで引く。
 */
public final class FactorLootModifier extends LootModifier {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "academy");
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<FactorLootModifier>> CODEC = TYPES.register("factor_loot", () -> RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).and(ResourceLocation.CODEC.fieldOf("table").forGetter(modifier -> modifier.table)).apply(instance, FactorLootModifier::new)));
    private final ResourceLocation table;
    public FactorLootModifier(LootItemCondition[] conditions, ResourceLocation table) { super(conditions); this.table = table; }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // rawを使い、この補助の抽選でグローバルなmodifierの連鎖全体へ再び入らないようにしている。
        // バニラのスタック分割は、データパックで上書きされたアイテムごとのスタック上限を引き続き守る。
        // Forge版のgetResolver().getLootTable(id)と同じく、無いテーブルは空のテーブルとして扱う。
        context.getLevel().getServer().reloadableRegistries()
                .getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, table))
                .getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
        return generatedLoot;
    }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC.get(); }
}
