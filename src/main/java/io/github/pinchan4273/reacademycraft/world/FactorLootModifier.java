package io.github.pinchan4273.reacademycraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 原作ACItemAdditionalRegistryのチェストの因子の抽選を、Forgeの戦利品データで組み立てたもの。
 * バニラや他のmodifierの内容を置き換えずに結果を追加する。
 */
public final class FactorLootModifier extends LootModifier {
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> TYPES = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "academy");
    public static final RegistryObject<Codec<FactorLootModifier>> CODEC = TYPES.register("factor_loot", () -> RecordCodecBuilder.create(
            instance -> codecStart(instance).and(ResourceLocation.CODEC.fieldOf("table").forGetter(modifier -> modifier.table)).apply(instance, FactorLootModifier::new)));
    private final ResourceLocation table;
    public FactorLootModifier(LootItemCondition[] conditions, ResourceLocation table) { super(conditions); this.table = table; }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // rawを使い、この補助の抽選でグローバルなmodifierの連鎖全体へ再び入らないようにしている。
        // バニラのスタック分割は、データパックで上書きされたアイテムごとのスタック上限を引き続き守る。
        context.getResolver().getLootTable(table).getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
        return generatedLoot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC.get(); }
}
