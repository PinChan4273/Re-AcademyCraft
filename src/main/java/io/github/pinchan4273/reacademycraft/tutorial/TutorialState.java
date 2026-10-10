package io.github.pinchan4273.reacademycraft.tutorial;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.TutorialSnapshot;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 原作TutorialData: このプレイヤーが解放した記事、ミサカクラウドの端末を受け取ったか、ミサカ番号（原作RandUtils.rangei(1000, 19000)、
 * 最初に固定）。原作はこれを同期するデータ部分として持つ。移植版はプレイヤーの永続データに置き、原作と同じく死亡を越えて残り、
 * TutorialSnapshotでクライアントへ伝える。
 *
 * 原作は各条件をビットとして覚え、そこから記事を求める。どの記事の条件もORで結ばれているので、記事そのものを覚えても同じになる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class TutorialState {
    private static final String KEY = "academy_tutorial";
    private TutorialState() { }
    private static CompoundTag tag(Player player) {
        var data = player.getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) data.put(KEY, new CompoundTag());
        return data.getCompound(KEY);
    }
    public static Set<String> activated(Player player) {
        var out = new LinkedHashSet<String>();
        if (player == null) return out;
        for (Tag t : tag(player).getList("activated", Tag.TAG_STRING))
            if (Tutorials.byId(t.getAsString()) != null) out.add(t.getAsString());
        return out;
    }
    /** 原作ACTutorial.isActivated。 */
    public static boolean isActivated(Player player, Tutorials.Tutorial tutorial) {
        return tutorial.defaultInstalled() || activated(player).contains(tutorial.id());
    }
    /** 原作getMisakaID: 1000〜18999の間で1回選ぶ。 */
    public static int misakaId(Player player) {
        var tag = tag(player);
        if (!tag.contains("misaka")) tag.putInt("misaka", 1000 + player.getRandom().nextInt(18000));
        return tag.getInt("misaka");
    }
    /** サーバー: このアイテムが解放するものを解放し、新しい記事ごとに所有者へ伝える。 */
    public static void obtained(ServerPlayer player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;
        var now = activated(player);
        var fresh = Tutorials.activatedBy(id).stream().filter(t -> !now.contains(t.id())).toList();
        if (fresh.isEmpty()) return;
        for (var t : fresh) now.add(t.id());
        write(player, List.copyOf(now));
        TutorialSnapshot.send(player, fresh.stream().map(Tutorials.Tutorial::id).toList());
        for (var t : fresh) net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.AcademyEvent.TutorialActivated(player, t.id()));
    }
    /** クライアント: サーバーが最後に伝えた内容。 */
    public static void accept(Player player, List<String> ids, int misaka) {
        if (player == null) return;
        write(player, ids);
        tag(player).putInt("misaka", misaka);
    }
    private static void write(Player player, List<String> ids) {
        var list = new ListTag();
        for (var t : Tutorials.all()) if (ids.contains(t.id())) list.add(StringTag.valueOf(t.id()));
        tag(player).put("activated", list);
    }
    public static String key() { return KEY; }

    // 原作Conditionsの3つの契機。
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) obtained(p, event.getCrafting());
    }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) obtained(p, event.getSmelting());
    }
    @SubscribeEvent public static void pickedUp(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) obtained(p, event.getStack());
    }
    /** 原作: giveCloudTerminalがオン（設定で変えない限りオン）なら、ミサカクラウドの端末をプレイヤーの足元へ1回だけ落とす。 */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        misakaId(player);
        var tag = tag(player);
        if (!tag.getBoolean("given") && AcademyConfig.GIVE_CLOUD_TERMINAL.get()) {
            tag.putBoolean("given", true);
            var level = player.serverLevel();
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 1, player.getZ(),
                    new ItemStack(io.github.pinchan4273.reacademycraft.world.AcademyContent.TUTORIAL.get())));
        }
        TutorialSnapshot.send(player, List.of());
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TutorialSnapshot.send(p, List.of());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TutorialSnapshot.send(p, List.of());
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        // 原作LambdaLib2のEntityData.onPlayerCloneは、死亡かどうかを問わずすべてのデータ部分を写すので、エンドからの帰還でも残る
        // （Forge自身はPlayerPersistedしか引き継がない）。
        var old = event.getOriginal().getPersistentData();
        if (old.contains(KEY)) event.getEntity().getPersistentData().put(KEY, old.get(KEY).copy());
    }
}
