package io.github.pinchan4273.reacademycraft.world.block.entity;

import net.minecraft.world.item.ItemStack;

/**
 * 風力のメニューのための、不可分な1スロットの所有権の移動。拒否されたら呼び出し元は入力を保ち、成功したら写しではなく実際の以前のアイテムを持つ。
 * 呼び出し元はaccepted()の後でだけ入力を消費し、previous()を複製してはならない。まだ所有されている機械のスタックを公開するgetterは無い。
 */
public record WindItemExchange(boolean accepted, ItemStack previous) {
    public static WindItemExchange rejected() { return new WindItemExchange(false, ItemStack.EMPTY); }
}
