package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import io.github.pinchan4273.reacademycraft.develop.DeveloperMenu;
import io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable;
import io.github.pinchan4273.reacademycraft.world.item.DescribedItem;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** ブロック・アイテム・ブロックエンティティ・メニュー・エンティティの登録。 */
public final class AcademyContent {
    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, AcademyCraft.MODID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity>> MAGNETIC_BLOCK = ENTITIES.register("magnetic_block",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.98f, .98f).clientTrackingRange(10).updateInterval(1).build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "magnetic_block"))));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.CoinEntity>> COIN_ENTITY = ENTITIES.register("coin",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.CoinEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.CoinEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.2f, .2f).clientTrackingRange(10).updateInterval(1).build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "coin"))));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity>> SILBARN_ENTITY = ENTITIES.register("silbarn",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    // 原作EntitySilbarnのsetSize(.4f, .4f)。
                    .sized(.4f, .4f).clientTrackingRange(10).updateInterval(2).build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "silbarn"))));
    /** v1.5.0 未元物質の甲虫（§9）。当たり判定はユーザーの判断で見た目に合わせた（仕様の幅2.0・高さ2.2から変更）。技能からだけ生成する。 */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle>> DARK_MATTER_BEETLE = ENTITIES.register("dark_matter_beetle",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle>of(
                    io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(2f, 1.75f).clientTrackingRange(10).noSummon().noSave().build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "dark_matter_beetle"))));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity>> MAG_HOOK_ENTITY = ENTITIES.register("mag_hook",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.5f, .5f).clientTrackingRange(10).updateInterval(2).build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "mag_hook"))));
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, AcademyCraft.MODID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, AcademyCraft.MODID);
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> METAL_FORMER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "metal_former", io.github.pinchan4273.reacademycraft.world.block.MetalFormerBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity>> METAL_ENTITY = BLOCK_ENTITIES.register("metal_former",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity::new, METAL_FORMER.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> PHASE_GEN = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "phase_gen", io.github.pinchan4273.reacademycraft.world.block.PhaseGeneratorBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity>> PHASE_GEN_ENTITY = BLOCK_ENTITIES.register("phase_gen",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity::new, PHASE_GEN.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> IMAG_FUSOR = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "imag_fusor", io.github.pinchan4273.reacademycraft.world.block.ImagFusorBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity>> IMAG_FUSOR_ENTITY = BLOCK_ENTITIES.register("imag_fusor",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity::new, IMAG_FUSOR.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> INTERFERER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "ability_interferer", io.github.pinchan4273.reacademycraft.world.block.AbilityInterfererBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity>> INTERFERER_ENTITY = BLOCK_ENTITIES.register("ability_interferer",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity::new, INTERFERER.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> MATRIX = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "wireless_matrix", io.github.pinchan4273.reacademycraft.world.block.WirelessMatrixBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity>> MATRIX_ENTITY = BLOCK_ENTITIES.register("wireless_matrix",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity::new, MATRIX.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> NODE_BASIC = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "node_basic",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity.Kind.BASIC));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> NODE_STANDARD = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "node_standard",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity.Kind.STANDARD));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> NODE_ADVANCED = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "node_advanced",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity.Kind.ADVANCED));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity>> NODE_ENTITY = BLOCK_ENTITIES.register("wireless_node",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity::new,
                    NODE_BASIC.get(), NODE_STANDARD.get(), NODE_ADVANCED.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> CAT_ENGINE = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "cat_engine", io.github.pinchan4273.reacademycraft.world.block.CatEngineBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity>> CAT_ENGINE_ENTITY = BLOCK_ENTITIES.register("cat_engine",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity::new, CAT_ENGINE.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> SOLAR_GEN = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "solar_gen", io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock::new);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity>> SOLAR_ENTITY = BLOCK_ENTITIES.register("solar_gen",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity::new, SOLAR_GEN.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> DEV_NORMAL = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "dev_normal", () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock());
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity>> DEV_NORMAL_ENTITY = BLOCK_ENTITIES.register("dev_normal",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity::new, true, DEV_NORMAL.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> DEV_ADVANCED = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "dev_advanced",
            () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock(io.github.pinchan4273.reacademycraft.develop.DeveloperTier.ADVANCED));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity>> DEV_ADVANCED_ENTITY = BLOCK_ENTITIES.register("dev_advanced",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity::new, true, DEV_ADVANCED.get()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> WIND_BASE = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "windgen_base",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock(io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.Body.BASE));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> WIND_MAIN = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "windgen_main",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock(io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.Body.MAIN));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> WIND_PILLAR = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "windgen_pillar",
            () -> new Block(io.github.pinchan4273.reacademycraft.world.AcademyIds.block().strength(4)
                    .sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity>> WIND_BODY_ENTITY = BLOCK_ENTITIES.register("wind_body",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity::new, true, WIND_BASE.get(), WIND_MAIN.get()));
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, AcademyCraft.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AcademyCraft.MODID);

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, AcademyCraft.MODID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.MetalFormerMenu>> METAL_MENU = MENUS.register("metal_former",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.MetalFormerMenu::new));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> METAL_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "metal_former", () -> new BlockItem(METAL_FORMER.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.PhaseGeneratorMenu>> PHASE_GEN_MENU = MENUS.register("phase_gen",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.PhaseGeneratorMenu::new));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> PHASE_GEN_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "phase_gen", () -> new BlockItem(PHASE_GEN.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.ImagFusorMenu>> IMAG_FUSOR_MENU = MENUS.register("imag_fusor",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.ImagFusorMenu::new));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> IMAG_FUSOR_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "imag_fusor", () -> new BlockItem(IMAG_FUSOR.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu>> INTERFERER_MENU = MENUS.register("ability_interferer",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu::new));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> INTERFERER_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "ability_interferer", () -> new BlockItem(INTERFERER.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.WirelessMatrixMenu>> MATRIX_MENU = MENUS.register("wireless_matrix",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.WirelessMatrixMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.WirelessNodeMenu>> NODE_MENU = MENUS.register("wireless_node",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.WirelessNodeMenu::new));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> MATRIX_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "wireless_matrix", () -> new BlockItem(MATRIX.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> CAT_ENGINE_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "cat_engine", () -> new BlockItem(CAT_ENGINE.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> MAG_HOOK = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "mag_hook", io.github.pinchan4273.reacademycraft.world.item.MagHook::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> TERMINAL_INSTALLER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "terminal_installer",
            io.github.pinchan4273.reacademycraft.world.item.TerminalInstaller::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> APP_SKILL_TREE = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "app_skill_tree",
            () -> new io.github.pinchan4273.reacademycraft.world.item.AppItem(io.github.pinchan4273.reacademycraft.terminal.TerminalApps.SKILL_TREE));
    /** 原作ItemTutorial: ミサカクラウドの端末。 */
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> TUTORIAL = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "tutorial", io.github.pinchan4273.reacademycraft.world.item.TutorialItem::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> APP_MEDIA_PLAYER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "app_media_player",
            () -> new io.github.pinchan4273.reacademycraft.world.item.AppItem(io.github.pinchan4273.reacademycraft.terminal.TerminalApps.MEDIA_PLAYER));
    /** 原作media_item。ここのアイテムはダメージ値を持たないので、メディアごとに1つのアイテム。 */
    public static final java.util.List<DeferredHolder<net.minecraft.world.item.Item, Item>> MEDIA = io.github.pinchan4273.reacademycraft.media.MediaAcquired.INTERNAL.stream()
            .map(id -> io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "media_" + id, () -> (Item) new io.github.pinchan4273.reacademycraft.world.item.MediaItem(id))).toList();
    /** 原作のapp_freq_transmitter: 周波数送信機を端末へ入れるアプリアイテム。 */
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> FREQ_TRANSMITTER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "app_freq_transmitter",
            () -> new io.github.pinchan4273.reacademycraft.world.item.AppItem(io.github.pinchan4273.reacademycraft.terminal.TerminalApps.FREQ_TRANSMITTER));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> NODE_BASIC_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "node_basic", () -> new BlockItem(NODE_BASIC.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> NODE_STANDARD_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "node_standard", () -> new BlockItem(NODE_STANDARD.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> NODE_ADVANCED_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "node_advanced", () -> new BlockItem(NODE_ADVANCED.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.SolarGeneratorMenu>> SOLAR_MENU = MENUS.register("solar_gen",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.SolarGeneratorMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu>> WIND_BASE_MENU = MENUS.register("windgen_base",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu::base));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu>> WIND_MAIN_MENU = MENUS.register("windgen_main",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu::main));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> SOLAR_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "solar_gen", () -> new BlockItem(SOLAR_GEN.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, MenuType<DeveloperMenu>> DEVELOPER_MENU = MENUS.register("developer_portable",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inventory, extra) -> new DeveloperMenu(id, inventory, extra)));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> DEV_NORMAL_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "dev_normal",
            () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock.DeveloperItem(DEV_NORMAL.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> DEV_ADVANCED_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "dev_advanced",
            () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock.DeveloperItem(DEV_ADVANCED.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> WIND_BASE_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "windgen_base",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.WindBodyItem(WIND_BASE.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> WIND_MAIN_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "windgen_main",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.WindBodyItem(WIND_MAIN.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> WIND_PILLAR_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "windgen_pillar",
            () -> new io.github.pinchan4273.reacademycraft.world.item.WindPartItem(WIND_PILLAR.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> WIND_FAN = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "windgen_fan",
            () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item().durability(100)));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> DEVELOPER_PORTABLE = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "developer_portable", DeveloperPortable::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> FACTOR_ELECTROMASTER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "factor_electromaster",
            () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item().stacksTo(1)));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> FACTOR_MELTDOWNER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "factor_meltdowner",
            () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item().stacksTo(1)));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> FACTOR_TELEPORTER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "factor_teleporter",
            () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item().stacksTo(1)));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> FACTOR_VECMANIP = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "factor_vecmanip",
            () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item().stacksTo(1)));
    /** v1.5.0: 未元物質誘導因子（採用仕様・版41の§1）。既存4系統の因子と同じ扱い（専用のレシピは無い）。 */
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> FACTOR_DARK_MATTER = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "factor_dark_matter",
            () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item().stacksTo(1)));
    // 原作blocks.confのmachine_frame: Material.ROCK、硬さ4.0、つるはしの採掘レベル1（needs_stone_toolタグ）。
    // 不透明な立方体（原作は既定の不透明ブロック）。
    public static final DeferredHolder<net.minecraft.world.level.block.Block, Block> MACHINE_FRAME = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(BLOCKS, "machine_frame", () -> new Block(io.github.pinchan4273.reacademycraft.world.AcademyIds.block()
            .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE)
            .strength(4.0f).requiresCorrectToolForDrops()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> COIN = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "coin", io.github.pinchan4273.reacademycraft.world.item.Coin::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> SILBARN = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "silbarn", io.github.pinchan4273.reacademycraft.world.item.Silbarn::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> CALC_CHIP = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "calc_chip", () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> DATA_CHIP = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "data_chip", () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> MAGNETIC_COIL = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "magnetic_coil", io.github.pinchan4273.reacademycraft.world.item.MagneticCoil::new);
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> REINFORCED_IRON_PLATE = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "reinforced_iron_plate", () -> new DescribedItem(io.github.pinchan4273.reacademycraft.world.AcademyIds.item()));
    public static final DeferredHolder<net.minecraft.world.item.Item, Item> MACHINE_FRAME_ITEM = io.github.pinchan4273.reacademycraft.world.AcademyIds.register(ITEMS, "machine_frame",
            () -> new BlockItem(MACHINE_FRAME.get(), io.github.pinchan4273.reacademycraft.world.AcademyIds.blockItem()));
    public static final DeferredHolder<net.minecraft.world.item.CreativeModeTab, CreativeModeTab> TAB = TABS.register("academy", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.academy"))
            .icon(() -> new ItemStack(CALC_CHIP.get()))
            .displayItems((parameters, output) -> {
                output.accept(REINFORCED_IRON_PLATE.get());
                output.accept(DATA_CHIP.get());
                output.accept(CALC_CHIP.get());
                output.accept(MAGNETIC_COIL.get());
                output.accept(COIN.get());
                output.accept(MACHINE_FRAME_ITEM.get());
                output.accept(SOLAR_ITEM.get());
                output.accept(PHASE_GEN_ITEM.get());
                output.accept(METAL_ITEM.get());
                output.accept(IMAG_FUSOR_ITEM.get());
                output.accept(INTERFERER_ITEM.get());
                output.accept(MATRIX_ITEM.get());
                output.accept(NODE_BASIC_ITEM.get());
                output.accept(NODE_STANDARD_ITEM.get());
                output.accept(NODE_ADVANCED_ITEM.get());
                output.accept(FREQ_TRANSMITTER.get());
                output.accept(TERMINAL_INSTALLER.get());
                output.accept(APP_SKILL_TREE.get());
                output.accept(APP_MEDIA_PLAYER.get());
                output.accept(TUTORIAL.get());
                MEDIA.forEach(media -> output.accept(media.get()));
                output.accept(CAT_ENGINE_ITEM.get());
                output.accept(MAG_HOOK.get());
                output.accept(PhaseContent.EMPTY.get());
                output.accept(PhaseContent.FILLED.get());
                MaterialContent.creativeItems(output);
                output.accept(DEVELOPER_PORTABLE.get());
                output.accept(DEV_NORMAL_ITEM.get());
                output.accept(DEV_ADVANCED_ITEM.get());
                output.accept(WIND_BASE_ITEM.get());
                output.accept(WIND_PILLAR_ITEM.get());
                output.accept(WIND_MAIN_ITEM.get());
                output.accept(WIND_FAN.get());
                ItemStack charged = new ItemStack(DEVELOPER_PORTABLE.get());
                DeveloperPortable.setEnergy(charged, DeveloperPortable.MAX_ENERGY);
                output.accept(charged);
                output.accept(FACTOR_ELECTROMASTER.get());
                output.accept(FACTOR_MELTDOWNER.get());
                output.accept(FACTOR_TELEPORTER.get());
                output.accept(FACTOR_VECMANIP.get());
                output.accept(FACTOR_DARK_MATTER.get());
                output.accept(SILBARN.get());
            }).build());

    private AcademyContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        ENTITIES.register(bus);
    }
}
