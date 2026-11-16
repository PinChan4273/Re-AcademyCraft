package io.github.pinchan4273.reacademycraft.develop;

import io.github.pinchan4273.reacademycraft.skill.AbilityCategory;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 開発機が使う誘導因子の探し方（1.5.2）。無能力者の初回の取得と、磁気コイルでの系統の変更で、同じ順に探す。
 * <ol>
 * <li>メインハンド（選んでいるホットバーの枠）</li>
 * <li>オフハンド</li>
 * <li>残りのホットバーを、画面の左から右へ（選んでいる枠は二重に数えない）</li>
 * <li>インベントリ本体の27枠を、左上から右へ、上段・中段・下段の順に</li>
 * </ol>
 * メインハンドとオフハンドは、利き手の設定に従う（バニラのメインハンドは、利き手が左でも右でも「選んでいるホットバーの枠」で、
 * 利き手の設定は左右の見た目だけを入れ替える。ここでは見た目の左右ではなく、このメイン／オフハンドで数える）。
 * 変更では、今の系統と同じ因子を飛ばして次を探す。飛ばした因子しか無ければ、{@link Result#sameOnly()}がtrueになる。
 */
public final class FactorSearch {
    private FactorSearch() { }

    /** 因子を見つけた場所。 */
    public enum Place {
        /** メインハンド。インデックスは選んでいるホットバーの枠（0〜8）。 */
        MAIN_HAND,
        /** オフハンド。インデックスは0。 */
        OFF_HAND,
        /** メインハンド以外のホットバー。インデックスは左からの枠（0〜8）。 */
        HOTBAR,
        /** インベントリ本体。インデックスは左上からの枠（0〜26。段はインデックス÷9、列はインデックス％9）。 */
        INVENTORY;

        /** この場所で使えるインデックスか。 */
        public boolean validIndex(int index) {
            return switch (this) {
                case MAIN_HAND, HOTBAR -> index >= 0 && index < Inventory.getSelectionSize();
                case OFF_HAND -> index == 0;
                case INVENTORY -> index >= 0 && index < 27;
            };
        }
    }

    /**
     * 選んだ因子。場所・インデックスと、スタックの参照を一緒に覚える。途中で失ったかは、その場所に同じスタック（同じ参照）があるかで見る。
     */
    public record Found(Place place, int index, ItemStack stack, AbilityCategory category) {
        /** その場所に、選んだときと同じスタックが今もあるか（空になっていれば失ったと扱う）。 */
        public boolean present(Player player) {
            return !stack.isEmpty() && at(player, place, index) == stack;
        }
        /**
         * その場所に同じスタックがあり、今も選んだときの系統の因子か（変更の完了で、消費の直前に確かめる）。
         * 同じスタックの物の種類は変わらないが、因子の登録（アドオンの系統）が変わっても、選んだ系統と違えば使わない。
         */
        public boolean holds(Player player) {
            var now = AbilityCategory.byFactor(stack);
            return present(player) && now != null && now.id().equals(category.id());
        }
        /**
         * その場所の因子を1個だけ減らす（変更の完了で消費する。1.5.2で、スタック全体から1個ずつに変えた）。
         * 同じスタックが無ければ何もしない。
         */
        public void consumeOne(Player player) {
            if (!present(player)) return;
            stack.shrink(1);
            player.getInventory().setChanged();
        }
        /** その場所の枠を空にする（探す順のテストが、見つけた因子を外して次を探すのに使う）。同じスタックが無ければ何もしない。 */
        public void clear(Player player) {
            if (!present(player)) return;
            var inventory = player.getInventory();
            switch (place) {
                case MAIN_HAND, HOTBAR -> inventory.items.set(index, ItemStack.EMPTY);
                case OFF_HAND -> inventory.offhand.set(0, ItemStack.EMPTY);
                case INVENTORY -> inventory.items.set(Inventory.getSelectionSize() + index, ItemStack.EMPTY);
            }
            inventory.setChanged();
        }
    }

    /** 探した結果。found が null で sameOnly が true なら、今の系統と同じ因子だけを持っている。 */
    public record Result(@Nullable Found found, boolean sameOnly) { }

    /**
     * 因子を探す。skip（変更では今の系統）と同じ系統の因子は飛ばす。null なら飛ばさない。
     */
    public static Result search(Player player, @Nullable ResourceLocation skip) {
        var inventory = player.getInventory();
        int selected = inventory.selected;
        boolean[] sawSame = {false};
        Found found = check(inventory.getSelected(), Place.MAIN_HAND, selected, skip, sawSame);
        if (found == null) found = check(inventory.offhand.get(0), Place.OFF_HAND, 0, skip, sawSame);
        for (int i = 0; found == null && i < Inventory.getSelectionSize(); i++)
            if (i != selected) found = check(inventory.items.get(i), Place.HOTBAR, i, skip, sawSame);
        for (int i = 0; found == null && i < 27; i++)
            found = check(inventory.items.get(Inventory.getSelectionSize() + i), Place.INVENTORY, i, skip, sawSame);
        return new Result(found, found == null && sawSame[0]);
    }

    private static @Nullable Found check(ItemStack stack, Place place, int index, @Nullable ResourceLocation skip, boolean[] sawSame) {
        var category = AbilityCategory.byFactor(stack);
        if (category == null) return null;
        if (category.id().equals(skip)) { sawSame[0] = true; return null; }
        return new Found(place, index, stack, category);
    }

    /** その場所・インデックスのスタック。メインハンドは、今も同じ枠を選んでいるときだけその枠のスタック（選び直していれば空）。 */
    public static ItemStack at(Player player, Place place, int index) {
        if (!place.validIndex(index)) return ItemStack.EMPTY;
        var inventory = player.getInventory();
        return switch (place) {
            case MAIN_HAND -> inventory.selected == index ? inventory.items.get(index) : ItemStack.EMPTY;
            case OFF_HAND -> inventory.offhand.get(0);
            case HOTBAR -> inventory.items.get(index);
            case INVENTORY -> inventory.items.get(Inventory.getSelectionSize() + index);
        };
    }
}
