package io.github.pinchan4273.reacademycraft.develop;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * OpenDevGuiPacket/LearnSkillPacketをForgeのメニューで置き換える。バニラはクリックを所有者の現在のコンテナIDで送る。
 * 状態・エネルギー・進捗はサーバーからクライアントへだけ流れる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class DeveloperMenu extends AbstractContainerMenu {
    private final Player owner;
    private final DevelopmentSession session;
    private final ContainerData display;
    /**
     * 背後に開発機の無いメニュー。端末の技能の木アプリが開く: プレイヤーの持つものを表示するだけで、何もできない
     * （どの操作もセッションを要求するため）。
     */
    private final boolean viewer;
    private long lastStartTick = Long.MIN_VALUE;
    public DeveloperMenu(int id, Inventory inventory) { this(id, inventory, (DevelopmentDevice)null); }
    /** クライアントのメニュー。背後に開発機があるかはサーバーが伝える。 */
    public DeveloperMenu(int id, Inventory inventory, net.minecraft.network.FriendlyByteBuf extra) {
        this(id, inventory, null, extra != null && extra.readableBytes() > 0 && extra.readBoolean());
    }
    /** 原作SkillTreeのdeveloper == null: 技能の木アプリの読み取り専用ページ。 */
    public boolean viewer() { return viewer; }
    /** 原作の技能の木アプリの読み取り専用メニュー。 */
    public static DeveloperMenu viewer(int id, Inventory inventory) { return new DeveloperMenu(id, inventory, null, true); }
    public DeveloperMenu(int id, Inventory inventory, InteractionHand hand) {
        this(id, inventory, inventory.player instanceof ServerPlayer server && hand != null
                ? DevelopmentDevice.handheld(server, hand) : null);
    }
    public DeveloperMenu(int id, Inventory inventory, DevelopmentDevice device) { this(id, inventory, device, false); }
    private DeveloperMenu(int id, Inventory inventory, DevelopmentDevice device, boolean viewer) {
        super(AcademyContent.DEVELOPER_MENU.get(), id);
        this.viewer = viewer;
        owner = inventory.player;
        session = owner instanceof ServerPlayer server && device != null ? new DevelopmentSession(server, device) : null;
        display = session == null ? new SimpleContainerData(DATA_SLOTS) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> session.state(); case 1 -> session.elapsed(); case 2 -> session.total();
                    case 3 -> session.energy() & 0xffff; case 4 -> availabilityPart(session.availability(), 0);
                    case 5 -> session.energy() >>> 16; case 6 -> session.tier().ordinal();
                    case 7 -> availabilityPart(session.availability(), 1); case 8 -> availabilityPart(session.availability(), 2);
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { /* サーバーの状態にクライアント側のsetterは無い */ }
            @Override public int getCount() { return DATA_SLOTS; }
        };
        // バニラのメニューデータは符号付きshortで送られるので、エネルギーと可用性はどちらも分割する。
        addDataSlots(display);
    }
    public int state() { return display.get(0); }
    public int elapsed() { return display.get(1); }
    public int total() { return display.get(2); }
    public int energy() { return (display.get(3) & 0xffff) | ((display.get(5) & 0xffff) << 16); }
    public DeveloperTier tier() { return DeveloperTier.fromId(display.get(6)); }
    public long availability() { return joinAvailability(display.get(4), display.get(7), display.get(8)); }

    /**
     * バニラはメニューのデータスロットを符号付き16ビットshortで送る（ClientboundContainerSetDataPacket）ので、それより
     * 幅の広い値は分割する必要がある。可用性は以前は1スロットだったが、meltdownerの学習アクションでビットが15を超えると、
     * それより上のビットはクライアントへの途中で黙って落ち、ビット15は符号拡張されて戻り、学習ボタンが信用できなくなった。
     * 3スロットで48ビットとし、今後のカテゴリの余地も残す。
     */
    private static final int DATA_SLOTS = 9;
    public static int availabilityPart(long availability, int part) { return (int) ((availability >>> (16 * part)) & 0xffff); }
    public static long joinAvailability(int low, int middle, int high) {
        return (low & 0xffffL) | ((middle & 0xffffL) << 16) | ((high & 0xffffL) << 32);
    }
    public boolean usesDevice(DevelopmentDevice device) { return session != null && session.usesDevice(device); }
    @Override public boolean stillValid(Player player) {
        // 閲覧用には近くに居るべき開発機が無いので、プレイヤーが見ている間だけ続く。
        if (viewer) return player == owner && player.isAlive() && !player.isSpectator();
        return player == owner && (player.level().isClientSide() || (session != null && session.valid()));
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player != owner || player.containerMenu != this || session == null || !stillValid(player)) return false;
        if (button == 2) { session.cancel(); return true; }
        // 最後のアクションまでのすべて: この上限は以前汎用コース（14）で止まっていたため、画面からmeltdowner・teleporter・
        // vectorの技能を学べなかった。
        if (button < 0 || button > DevelopmentSession.MAX_ACTION || lastStartTick == player.level().getGameTime()) return false;
        lastStartTick = player.level().getGameTime(); return session.start(button);
    }
    public void serverTick() {
        if (session != null && owner.containerMenu == this) session.tick();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player
                && player.containerMenu instanceof DeveloperMenu menu) menu.serverTick();
    }
    @Override public void removed(Player player) {
        if (session != null) session.close();
        super.removed(player);
    }
}
