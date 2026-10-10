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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** ブロック・アイテム・ブロックエンティティ・メニュー・エンティティの登録。 */
public final class AcademyContent {
    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AcademyCraft.MODID);
    public static final RegistryObject<net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity>> MAGNETIC_BLOCK = ENTITIES.register("magnetic_block",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.98f, .98f).clientTrackingRange(10).updateInterval(1).build("academy:magnetic_block"));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.CoinEntity>> COIN_ENTITY = ENTITIES.register("coin",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.CoinEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.CoinEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.2f, .2f).clientTrackingRange(10).updateInterval(1).build("academy:coin"));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity>> SILBARN_ENTITY = ENTITIES.register("silbarn",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    // 原作EntitySilbarnのsetSize(.4f, .4f)。
                    .sized(.4f, .4f).clientTrackingRange(10).updateInterval(2).build("academy:silbarn"));
    public static final RegistryObject<net.minecraft.world.entity.EntityType<io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity>> MAG_HOOK_ENTITY = ENTITIES.register("mag_hook",
            () -> net.minecraft.world.entity.EntityType.Builder.<io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity>of(
                    io.github.pinchan4273.reacademycraft.world.entity.MagHookEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.5f, .5f).clientTrackingRange(10).updateInterval(2).build("academy:mag_hook"));
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AcademyCraft.MODID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AcademyCraft.MODID);
    public static final RegistryObject<Block> METAL_FORMER = BLOCKS.register("metal_former", io.github.pinchan4273.reacademycraft.world.block.MetalFormerBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity>> METAL_ENTITY = BLOCK_ENTITIES.register("metal_former",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity::new, METAL_FORMER.get()).build(null));
    public static final RegistryObject<Block> PHASE_GEN = BLOCKS.register("phase_gen", io.github.pinchan4273.reacademycraft.world.block.PhaseGeneratorBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity>> PHASE_GEN_ENTITY = BLOCK_ENTITIES.register("phase_gen",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity::new, PHASE_GEN.get()).build(null));
    public static final RegistryObject<Block> IMAG_FUSOR = BLOCKS.register("imag_fusor", io.github.pinchan4273.reacademycraft.world.block.ImagFusorBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity>> IMAG_FUSOR_ENTITY = BLOCK_ENTITIES.register("imag_fusor",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity::new, IMAG_FUSOR.get()).build(null));
    public static final RegistryObject<Block> INTERFERER = BLOCKS.register("ability_interferer", io.github.pinchan4273.reacademycraft.world.block.AbilityInterfererBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity>> INTERFERER_ENTITY = BLOCK_ENTITIES.register("ability_interferer",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity::new, INTERFERER.get()).build(null));
    public static final RegistryObject<Block> MATRIX = BLOCKS.register("wireless_matrix", io.github.pinchan4273.reacademycraft.world.block.WirelessMatrixBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity>> MATRIX_ENTITY = BLOCK_ENTITIES.register("wireless_matrix",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity::new, MATRIX.get()).build(null));
    public static final RegistryObject<Block> NODE_BASIC = BLOCKS.register("node_basic",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity.Kind.BASIC));
    public static final RegistryObject<Block> NODE_STANDARD = BLOCKS.register("node_standard",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity.Kind.STANDARD));
    public static final RegistryObject<Block> NODE_ADVANCED = BLOCKS.register("node_advanced",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity.Kind.ADVANCED));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity>> NODE_ENTITY = BLOCK_ENTITIES.register("wireless_node",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity::new,
                    NODE_BASIC.get(), NODE_STANDARD.get(), NODE_ADVANCED.get()).build(null));
    public static final RegistryObject<Block> CAT_ENGINE = BLOCKS.register("cat_engine", io.github.pinchan4273.reacademycraft.world.block.CatEngineBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity>> CAT_ENGINE_ENTITY = BLOCK_ENTITIES.register("cat_engine",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity::new, CAT_ENGINE.get()).build(null));
    public static final RegistryObject<Block> SOLAR_GEN = BLOCKS.register("solar_gen", io.github.pinchan4273.reacademycraft.world.block.SolarGeneratorBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity>> SOLAR_ENTITY = BLOCK_ENTITIES.register("solar_gen",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity::new, SOLAR_GEN.get()).build(null));
    public static final RegistryObject<Block> DEV_NORMAL = BLOCKS.register("dev_normal", io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity>> DEV_NORMAL_ENTITY = BLOCK_ENTITIES.register("dev_normal",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity::new, DEV_NORMAL.get()).build(null));
    public static final RegistryObject<Block> DEV_ADVANCED = BLOCKS.register("dev_advanced",
            () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock(io.github.pinchan4273.reacademycraft.develop.DeveloperTier.ADVANCED));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity>> DEV_ADVANCED_ENTITY = BLOCK_ENTITIES.register("dev_advanced",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity::new, DEV_ADVANCED.get()).build(null));
    public static final RegistryObject<Block> WIND_BASE = BLOCKS.register("windgen_base",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock(io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.Body.BASE));
    public static final RegistryObject<Block> WIND_MAIN = BLOCKS.register("windgen_main",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock(io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.Body.MAIN));
    public static final RegistryObject<Block> WIND_PILLAR = BLOCKS.register("windgen_pillar",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(4)
                    .sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity>> WIND_BODY_ENTITY = BLOCK_ENTITIES.register("wind_body",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity::new, WIND_BASE.get(), WIND_MAIN.get()).build(null));
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AcademyCraft.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AcademyCraft.MODID);

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AcademyCraft.MODID);
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.MetalFormerMenu>> METAL_MENU = MENUS.register("metal_former",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.MetalFormerMenu::new));
    public static final RegistryObject<Item> METAL_ITEM = ITEMS.register("metal_former", () -> new BlockItem(METAL_FORMER.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.PhaseGeneratorMenu>> PHASE_GEN_MENU = MENUS.register("phase_gen",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.PhaseGeneratorMenu::new));
    public static final RegistryObject<Item> PHASE_GEN_ITEM = ITEMS.register("phase_gen", () -> new BlockItem(PHASE_GEN.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.ImagFusorMenu>> IMAG_FUSOR_MENU = MENUS.register("imag_fusor",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.ImagFusorMenu::new));
    public static final RegistryObject<Item> IMAG_FUSOR_ITEM = ITEMS.register("imag_fusor", () -> new BlockItem(IMAG_FUSOR.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu>> INTERFERER_MENU = MENUS.register("ability_interferer",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.AbilityInterfererMenu::new));
    public static final RegistryObject<Item> INTERFERER_ITEM = ITEMS.register("ability_interferer", () -> new BlockItem(INTERFERER.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.WirelessMatrixMenu>> MATRIX_MENU = MENUS.register("wireless_matrix",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.WirelessMatrixMenu::new));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.WirelessNodeMenu>> NODE_MENU = MENUS.register("wireless_node",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.WirelessNodeMenu::new));
    public static final RegistryObject<Item> MATRIX_ITEM = ITEMS.register("wireless_matrix", () -> new BlockItem(MATRIX.get(), new Item.Properties()));
    public static final RegistryObject<Item> CAT_ENGINE_ITEM = ITEMS.register("cat_engine", () -> new BlockItem(CAT_ENGINE.get(), new Item.Properties()));
    public static final RegistryObject<Item> MAG_HOOK = ITEMS.register("mag_hook", io.github.pinchan4273.reacademycraft.world.item.MagHook::new);
    public static final RegistryObject<Item> TERMINAL_INSTALLER = ITEMS.register("terminal_installer",
            io.github.pinchan4273.reacademycraft.world.item.TerminalInstaller::new);
    public static final RegistryObject<Item> APP_SKILL_TREE = ITEMS.register("app_skill_tree",
            () -> new io.github.pinchan4273.reacademycraft.world.item.AppItem(io.github.pinchan4273.reacademycraft.terminal.TerminalApps.SKILL_TREE));
    /** 原作ItemTutorial: ミサカクラウドの端末。 */
    public static final RegistryObject<Item> TUTORIAL = ITEMS.register("tutorial", io.github.pinchan4273.reacademycraft.world.item.TutorialItem::new);
    public static final RegistryObject<Item> APP_MEDIA_PLAYER = ITEMS.register("app_media_player",
            () -> new io.github.pinchan4273.reacademycraft.world.item.AppItem(io.github.pinchan4273.reacademycraft.terminal.TerminalApps.MEDIA_PLAYER));
    /** 原作media_item。ここのアイテムはダメージ値を持たないので、メディアごとに1つのアイテム。 */
    public static final java.util.List<RegistryObject<Item>> MEDIA = io.github.pinchan4273.reacademycraft.media.MediaAcquired.INTERNAL.stream()
            .map(id -> ITEMS.register("media_" + id, () -> (Item) new io.github.pinchan4273.reacademycraft.world.item.MediaItem(id))).toList();
    /** 原作のapp_freq_transmitter: 周波数送信機を端末へ入れるアプリアイテム。 */
    public static final RegistryObject<Item> FREQ_TRANSMITTER = ITEMS.register("app_freq_transmitter",
            () -> new io.github.pinchan4273.reacademycraft.world.item.AppItem(io.github.pinchan4273.reacademycraft.terminal.TerminalApps.FREQ_TRANSMITTER));
    public static final RegistryObject<Item> NODE_BASIC_ITEM = ITEMS.register("node_basic", () -> new BlockItem(NODE_BASIC.get(), new Item.Properties()));
    public static final RegistryObject<Item> NODE_STANDARD_ITEM = ITEMS.register("node_standard", () -> new BlockItem(NODE_STANDARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> NODE_ADVANCED_ITEM = ITEMS.register("node_advanced", () -> new BlockItem(NODE_ADVANCED.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.SolarGeneratorMenu>> SOLAR_MENU = MENUS.register("solar_gen",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.SolarGeneratorMenu::new));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu>> WIND_BASE_MENU = MENUS.register("windgen_base",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu::base));
    public static final RegistryObject<MenuType<io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu>> WIND_MAIN_MENU = MENUS.register("windgen_main",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(io.github.pinchan4273.reacademycraft.world.menu.WindGeneratorMenu::main));
    public static final RegistryObject<Item> SOLAR_ITEM = ITEMS.register("solar_gen", () -> new BlockItem(SOLAR_GEN.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<DeveloperMenu>> DEVELOPER_MENU = MENUS.register("developer_portable",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create((id, inventory, extra) -> new DeveloperMenu(id, inventory, extra)));
    public static final RegistryObject<Item> DEV_NORMAL_ITEM = ITEMS.register("dev_normal",
            () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock.DeveloperItem(DEV_NORMAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> DEV_ADVANCED_ITEM = ITEMS.register("dev_advanced",
            () -> new io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock.DeveloperItem(DEV_ADVANCED.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIND_BASE_ITEM = ITEMS.register("windgen_base",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.WindBodyItem(WIND_BASE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIND_MAIN_ITEM = ITEMS.register("windgen_main",
            () -> new io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock.WindBodyItem(WIND_MAIN.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIND_PILLAR_ITEM = ITEMS.register("windgen_pillar",
            () -> new io.github.pinchan4273.reacademycraft.world.item.WindPartItem(WIND_PILLAR.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIND_FAN = ITEMS.register("windgen_fan",
            () -> new DescribedItem(new Item.Properties().durability(100)));
    public static final RegistryObject<Item> DEVELOPER_PORTABLE = ITEMS.register("developer_portable", DeveloperPortable::new);
    public static final RegistryObject<Item> FACTOR_ELECTROMASTER = ITEMS.register("factor_electromaster",
            () -> new DescribedItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FACTOR_MELTDOWNER = ITEMS.register("factor_meltdowner",
            () -> new DescribedItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FACTOR_TELEPORTER = ITEMS.register("factor_teleporter",
            () -> new DescribedItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FACTOR_VECMANIP = ITEMS.register("factor_vecmanip",
            () -> new DescribedItem(new Item.Properties().stacksTo(1)));
    // 原作blocks.confのmachine_frame: Material.ROCK、硬さ4.0、つるはしの採掘レベル1（needs_stone_toolタグ）。
    // 不透明な立方体（原作は既定の不透明ブロック）。
    public static final RegistryObject<Block> MACHINE_FRAME = BLOCKS.register("machine_frame", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE)
            .strength(4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> COIN = ITEMS.register("coin", io.github.pinchan4273.reacademycraft.world.item.Coin::new);
    public static final RegistryObject<Item> SILBARN = ITEMS.register("silbarn", io.github.pinchan4273.reacademycraft.world.item.Silbarn::new);
    public static final RegistryObject<Item> CALC_CHIP = ITEMS.register("calc_chip", () -> new DescribedItem(new Item.Properties()));
    public static final RegistryObject<Item> DATA_CHIP = ITEMS.register("data_chip", () -> new DescribedItem(new Item.Properties()));
    public static final RegistryObject<Item> MAGNETIC_COIL = ITEMS.register("magnetic_coil", io.github.pinchan4273.reacademycraft.world.item.MagneticCoil::new);
    public static final RegistryObject<Item> REINFORCED_IRON_PLATE = ITEMS.register("reinforced_iron_plate", () -> new DescribedItem(new Item.Properties()));
    public static final RegistryObject<Item> MACHINE_FRAME_ITEM = ITEMS.register("machine_frame",
            () -> new BlockItem(MACHINE_FRAME.get(), new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("academy", () -> CreativeModeTab.builder()
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
