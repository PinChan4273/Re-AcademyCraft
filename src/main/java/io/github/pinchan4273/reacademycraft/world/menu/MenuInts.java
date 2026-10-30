package io.github.pinchan4273.reacademycraft.world.menu;

/**
 * GUI-ENERGY-001: メニューのデータスロットで32bitの値を運ぶ。1.20.1のClientboundContainerSetDataPacketは値をshortで送るので、
 * 16bitを超える値は上位・下位の2つのスロットに分けて送り、受け取った側は両方を{@code & 0xffff}で戻してから組み立てる。
 * Minecraftのクラスに依存しない（オフラインの試験で確かめる）。
 */
public final class MenuInts {
    private MenuInts() { }

    /** 下位16bit（送るスロットの値）。 */
    public static int low(int value) { return value & 0xffff; }

    /** 上位16bit（送るスロットの値）。 */
    public static int high(int value) { return value >>> 16; }

    /** 受け取った2つのスロットの値（shortで届き、符号が付いていてもよい）から、元の32bitの値を組み立てる。 */
    public static int join(int low, int high) { return (low & 0xffff) | ((high & 0xffff) << 16); }
}
