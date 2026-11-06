package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.crafting.MachineRecipes;
import io.github.pinchan4273.reacademycraft.tutorial.Tutorials;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;

/**
 * 原作ViewGroupsとRecipeHandler: 記事のプレビュー窓（guis/tutorial.xmlの134x134の領域）に表示できるもの。
 * グループは1つのタグボタン（原作のCRAFTまたはVIEWアイコン）と、矢印で切り替える1つ以上の表示から成る:
 *
 * - drawsBlock: 見る人の方へ傾けたブロック（原作はtimer / 80度回すが、timerは秒を数えるので、ほぼ止まって見える）。
 * - displayIcon: 中央のテクスチャ。
 * - recipes: そのアイテムを作るすべてのレシピを原作RecipeHandlerの順で: crafting_gridの上の作業台レシピ
 *   （材料は2秒ごとに切り替わり、種類をその上に表示）、次に虚像融合機、金属成形機、かまど。それぞれ
 *   tutorial_windows.xmlの専用の窓に描く。
 *
 * 原作はブロックとアイコンを独自の50度の透視で描くが、ここでは窓の上に平らに、原作で見える程度の大きさで描く。
 * スタックにマウスを載せると名前を表示する。
 */
public final class TutorialPreviews {
    static final float AREA = 134;
    private static final Identifier GRID = tex("guis/tutorial/crafting_grid"), SMELTING = tex("guis/tutorial_smelting"),
            FUSOR = tex("guis/tutorial_fusor"), FORMER = tex("guis/tutorial_metalformer");
    public static final Identifier ICON_CRAFT = tex("guis/icons/icon_craft"), ICON_VIEW = tex("guis/icons/icon_view");
    private TutorialPreviews() { }
    private static Identifier tex(String path) { return Identifier.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }

    /** 原作の表示の1つ: 領域自身の単位で描き、マウスの下のスタックを返す。 */
    public interface View {
        @Nullable ItemStack draw(GuiGraphicsExtractor g, float mx, float my, double time);
    }
    /** 原作ViewGroup: タグ、タグにマウスを載せている間に出す文、表示の並び。 */
    public record Group(Identifier tag, String text, List<View> views) { }

    public static List<Group> groups(Tutorials.Tutorial tutorial) {
        var out = new ArrayList<Group>();
        for (var p : tutorial.previews()) switch (p.kind()) {
            case BLOCK -> {
                Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(p.target());
                if (block != null) out.add(new Group(ICON_VIEW, "", List.of(blockView(block))));
            }
            case ICON -> out.add(new Group(ICON_VIEW, "", List.of(iconView(p.target()))));
            case RECIPES -> {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(p.target());
                if (item != null) out.add(new Group(ICON_CRAFT,
                        Component.translatable("academy.tutorial.crafting", new ItemStack(item).getHoverName()).getString(), recipes(item)));
            }
        }
        return out;
    }

    // ---- レシピ ----
    static List<View> recipes(Item item) {
        var level = Minecraft.getInstance().level;
        var out = new ArrayList<View>();
        if (level == null) return out;
        // 26.2: クライアントのレシピは MachineRecipes.sendToClients で頼んだ種類だけ届く。作業台と炉は RecipeDisplay で読む。
        var recipes = io.github.pinchan4273.reacademycraft.crafting.MachineRecipes.recipes(level);
        var context = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        for (var held : recipes.byType(RecipeType.CRAFTING)) for (var display : held.value().display()) {
            if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) {
                var result = shaped.result().resolveForFirstStack(context);
                if (!result.is(item)) continue;
                @SuppressWarnings("unchecked") List<ItemStack>[] grid = new List[9];
                for (int i = 0; i < shaped.ingredients().size() && i < 9; i++) {
                    int row = i / shaped.width(), col = i % shaped.width();
                    grid[col + row * 3] = shaped.ingredients().get(i).resolveForStacks(context);
                }
                out.add(craftingView(result, grid, "shaped"));
            } else if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
                var result = shapeless.result().resolveForFirstStack(context);
                if (!result.is(item)) continue;
                @SuppressWarnings("unchecked") List<ItemStack>[] grid = new List[9];
                for (int i = 0; i < shapeless.ingredients().size() && i < 9; i++) grid[i] = shapeless.ingredients().get(i).resolveForStacks(context);
                out.add(craftingView(result, grid, "shapeless"));
            }
        }
        for (var held : recipes.byType(MachineRecipes.FUSION_TYPE.get())) { var recipe = held.value();
            var result = recipe.result();
            if (result.is(item)) out.add(fusorView(stacks(recipe.ingredient()), result, recipe.liquid()));
        }
        for (var held : recipes.byType(MachineRecipes.FORMING_TYPE.get())) { var recipe = held.value();
            var result = recipe.result();
            if (result.is(item)) out.add(formerView(stacks(recipe.ingredient()), result, recipe.operation()));
        }
        for (var held : recipes.byType(RecipeType.SMELTING)) for (var display : held.value().display()) {
            if (!(display instanceof net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace)) continue;
            var result = furnace.result().resolveForFirstStack(context);
            if (result.is(item)) out.add(smeltingView(furnace.ingredient().resolveForStacks(context), result));
        }
        return out;
    }
    private static List<ItemStack> stacks(Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty() ? List.of() : ingredient.items().map(ItemStack::new).toList();
    }
    /** 原作StackDisplay: 32x34、スタックは2秒ごとに切り替わり、アイテムの2倍の大きさ。 */
    @Nullable private static ItemStack stack(GuiGraphicsExtractor g, List<ItemStack> stacks, float x, float y, float mx, float my, double time) {
        if (stacks == null || stacks.isEmpty()) return null;
        var stack = stacks.get((int) (time / 2) % stacks.size());
        boolean hover = mx >= x && mx < x + 32 && my >= y && my < y + 34;
        if (hover) DeveloperScreen.rect(g, x, y, 32, 34, 1, 1, 1, .15f);
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(2, 2);
        g.item(stack, 0, 0);
        g.itemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        g.pose().popMatrix();
        return hover ? stack : null;
    }
    /** 指定の大きさ・倍率の窓を領域の中央に置き、その単位で描く。 */
    private interface Window { @Nullable ItemStack draw(GuiGraphicsExtractor g, float mx, float my, double time); }
    private static View window(Identifier texture, float w, float h, float scale, Window content) {
        return (g, mx, my, time) -> {
            float x0 = (AREA - w * scale) / 2, y0 = (AREA - h * scale) / 2;
            
            g.pose().pushMatrix(); g.pose().translate(x0, y0); g.pose().scale(scale, scale);
            DeveloperScreen.quad(g, texture, 0, 0, w, h, 1, 1, 1, 1);
            var hovered = content.draw(g, (mx - x0) / scale, (my - y0) / scale, time);
            g.pose().popMatrix();
            return hovered;
        };
    }
    private static View craftingView(ItemStack output, List<ItemStack>[] grid, String kind) {
        return window(GRID, 196, 128, .6f, (g, mx, my, time) -> {
            ItemStack hovered = null;
            for (int i = 0; i < 9; i++) {
                var h = stack(g, grid[i], 5 + i % 3 * 43, 5 + i / 3 * 43, mx, my, time);
                if (h != null) hovered = h;
            }
            var h = stack(g, List.of(output), 148 + 5, 44 + 5, mx, my, time);
            if (h != null) hovered = h;
            // 原作: グリッドの上にレシピの種類、大きさ24、width / 2 - 30を中心に。
            var text = Component.translatable("academy.gui.crafttype." + kind).getString();
            var font = Minecraft.getInstance().font; float s = 24 / 9f;
            g.pose().pushMatrix(); g.pose().translate(196 / 2f - 30 - font.width(text) * s / 2, -28); g.pose().scale(s, s);
            g.text(font, text, 0, 0, 0xffffffff, false);
            g.pose().popMatrix();
            return hovered;
        });
    }
    private static View smeltingView(List<ItemStack> in, ItemStack out) {
        return window(SMELTING, 192, 128, .6f, (g, mx, my, time) -> {
            var a = stack(g, in, 30, 43.16667f, mx, my, time);
            var b = stack(g, List.of(out), 123.33333f, 43.16667f, mx, my, time);
            return a != null ? a : b;
        });
    }
    private static View fusorView(List<ItemStack> in, ItemStack out, int liquid) {
        return window(FUSOR, 196, 128, .6f, (g, mx, my, time) -> {
            var a = stack(g, in, 19, 62.5f, mx, my, time);
            var b = stack(g, List.of(out), 147, 62.5f, mx, my, time);
            var font = Minecraft.getInstance().font; float s = 14 / 9f; var text = String.valueOf(liquid);
            g.pose().pushMatrix(); g.pose().translate(81 + (52 - font.width(text) * s) / 2, 14.5f); g.pose().scale(s, s);
            g.text(font, text, 0, 0, 0xffffffff, false);
            g.pose().popMatrix();
            return a != null ? a : b;
        });
    }
    private static View formerView(List<ItemStack> in, ItemStack out, io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity.Mode mode) {
        var modeIcon = tex("guis/icons/icon_former_" + mode.name().toLowerCase(java.util.Locale.ROOT));
        return window(FORMER, 192, 192, .5f, (g, mx, my, time) -> {
            DeveloperScreen.quad(g, modeIcon, 82.66667f, 22.7f, 25, 25, 1, 1, 1, 1);
            // 原作のスロットはここでは幅25。その上に幅32の表示を中央揃えで置く。
            var a = stack(g, in, 11.33333f + 12.5f - 16, 88.5f + 12.5f - 17, mx, my, time);
            var b = stack(g, List.of(out), 155.33333f + 12.5f - 16, 88.5f + 12.5f - 17, mx, my, time);
            return a != null ? a : b;
        });
    }

    // ---- 物の表示 ----
    static View blockView(Block block) {
        return (g, mx, my, time) -> {
            // 仮: 1.21.1 はブロックを大きさ 52、前へ 20 度傾けて 3D で描いた。26.2 ではアイテムの GUI の表示で同じ大きさに見せる。
            // 向きは原作と違う（等角）。原作どおりの向きにするには PictureInPicture の描画が要る。
            float s = 3.7f;
            g.pose().pushMatrix();
            g.pose().translate(AREA / 2 - 8 * s, AREA / 2 + 4 - 8 * s);
            g.pose().scale(s, s);
            g.item(new ItemStack(block), 0, 0);
            g.pose().popMatrix();
            return null;
        };
    }
    static View iconView(Identifier texture) {
        return (g, mx, my, time) -> {
            float size = 48;
            DeveloperScreen.quad(g, texture, (AREA - size) / 2, (AREA - size) / 2, size, size, 1, 1, 1, 1);
            return null;
        };
    }
}
