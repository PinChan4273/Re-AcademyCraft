package io.github.pinchan4273.reacademycraft;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.AbilityCategory;
import io.github.pinchan4273.reacademycraft.terminal.TerminalState;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作のACAdvancementsとDispatcherAch: 原作の進捗ツリーの16個の進捗と、それを与える場面。
 *
 * 原作は進捗ごとに独自のcriterion triggerを登録し、イベントのdispatcherから発火する。ここでは各進捗に
 * 「それ自体では満たされないcriterion」を持たせ、同じ場面でこちらから直接与える。プレイヤーから見た結果は
 * 同じで、15個のtrigger classを持たずに済む。
 *
 * 原作がイベントで見ているものは、確実に観測できる場所で見る。製作と拾得はここでもイベントで扱い、
 * 能力の節目はプレイヤーのデータを1秒ごとに読む。こうすると、開発機・コマンド・datapackのどの経路で
 * 節目に達しても取りこぼさない（1か所から発火するイベントでは取りこぼす）。
 *
 * ForgeのPlayerAdvancements.awardはFakePlayerを常に拒否する。そのため、ある状態が何を得たかは
 * earned()で計算してテストから読めるようにし、実際の付与は実プレイヤーで確かめる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class AcademyAdvancements {
    /**
     * 原作には無い情報。「もうランダムじゃない」の進捗は系統を変えたことが条件なので、
     * プレイヤーが最初に持った系統を覚えておく必要がある。
     */
    private static final int EVERY = 20;
    private AcademyAdvancements() { }

    /** このMODの進捗を1つ与え、何か変わったかを返す。既に持っている進捗を与えても何もしない。 */
    public static boolean grant(ServerPlayer player, String name) {
        if (player == null || player.getServer() == null) return false;
        var advancement = player.getServer().getAdvancements()
                .getAdvancement(ResourceLocation.fromNamespaceAndPath("academy", name));
        if (advancement == null) return false;
        boolean changed = false;
        for (String criterion : advancement.getCriteria().keySet())
            changed |= player.getAdvancements().award(advancement, criterion);
        return changed;
    }
    public static boolean has(ServerPlayer player, String name) {
        var advancement = player.getServer().getAdvancements()
                .getAdvancement(ResourceLocation.fromNamespaceAndPath("academy", name));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** 原作rgItemCrafted: 原作が見ている4つの機械と開発機。 */
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String name = forCrafting(event.getCrafting().getItem());
            if (name != null) grant(player, name);
        }
    }
    /** このアイテムを製作して得られる進捗。無ければ何も返さない。 */
    @Nullable public static String forCrafting(Item item) {
        if (item == AcademyContent.DEVELOPER_PORTABLE.get()) return "ac_developer";
        if (item == AcademyContent.PHASE_GEN_ITEM.get()) return "phase_generator";
        if (item == AcademyContent.NODE_BASIC_ITEM.get()) return "ac_node";
        if (item == AcademyContent.MATRIX_ITEM.get()) return "ac_matrix";
        return null;
    }

    /** 原作rgPlayerPickup(induction_factor): この移植では系統ごとに因子のアイテムが1つある。 */
    @SubscribeEvent public static void pickedUp(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isFactor(event.getStack()))
            grant(player, "getting_factor");
    }
    public static boolean isFactor(ItemStack stack) {
        for (var category : AbilityCategory.REGISTERED) if (stack.is(category.factor().get())) return true;
        return false;
    }

    /** 原作MatterUnitHarvestEvent。 */
    @SubscribeEvent public static void harvested(io.github.pinchan4273.reacademycraft.world.event.MatterUnitHarvestEvent event) {
        if (event.collected) grant(event.player, "getting_phase");
    }

    /**
     * 原作DispatcherAch.onTransformCategory: これを発火するのは高級型開発機のリセットだけ。
     * /aim catは発火せずに系統を変えるので、この進捗は得られない。
     */
    @SubscribeEvent public static void transformed(io.github.pinchan4273.reacademycraft.event.AbilityEvent.TransformCategory event) {
        if (event.player() instanceof ServerPlayer player) grant(player, "convert_category");
    }

    /**
     * 原作がLevelChangeEvent・SkillLearnEvent・SkillExpAddedEvent・OverloadEventで発火するもの。
     * ここではプレイヤーのデータを1秒ごとに読んで判定する。
     */
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!player.isAlive() || player.tickCount % EVERY != 0) return;
        for (String name : earned(player)) grant(player, name);
    }
    /**
     * プレイヤーの状態がこれまでに得た進捗のすべて。付与とは分けて、単独で確かめられるようにしている。
     * Forgeはテスト用のプレイヤーには一切付与しないため、テストはこれを読めても、進捗が付く様子は見られない。
     */
    public static List<String> earned(ServerPlayer player) {
        var names = new ArrayList<String>();
        if (TerminalState.installed(player)) names.add("terminal_installed");
        // getting_phaseは原作のMatterUnitHarvestEventだけで得る（上の収穫の処理）。
        // クリエイティブタブや/giveで手に入れた満杯のユニットでは得られない。
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.hasAbility()) return names;
        // 原作は、レベル1へのLevelChangeEventでこれを与える。しかし最初の能力開発ではそのイベントが発火しない
        // （AbilityData.setCategoryがレベルを直接設定する）ため、原作では/aim level 1でしか得られなかった。
        // ここでは能力を得たときに与える。進捗の本来の意図はそちらにある。
        names.add("dev_category");
        if (data.getLevel() >= 3) names.add("ac_level_3");
        if (data.getLevel() >= 5) names.add("ac_level_5");
        if (!data.learnedSkills().isEmpty()) names.add("ac_learning_skill");
        for (float proficiency : data.learnedSkills().values())
            if (proficiency >= 1) { names.add("ac_exp_full"); break; }
        if (data.isOverloadLocked()) names.add("ac_overload");
        return names;
    }
}
