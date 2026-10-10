package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.crafting.MachineRecipes;
import io.github.pinchan4273.reacademycraft.tutorial.Tutorials;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

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
    private static final ResourceLocation GRID = tex("guis/tutorial/crafting_grid"), SMELTING = tex("guis/tutorial_smelting"),
            FUSOR = tex("guis/tutorial_fusor"), FORMER = tex("guis/tutorial_metalformer");
    public static final ResourceLocation ICON_CRAFT = tex("guis/icons/icon_craft"), ICON_VIEW = tex("guis/icons/icon_view");
    private TutorialPreviews() { }
    private static ResourceLocation tex(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }

    /** 原作の表示の1つ: 領域自身の単位で描き、マウスの下のスタックを返す。 */
    public interface View {
        @Nullable ItemStack draw(GuiGraphics g, float mx, float my, double time);
    }
    /** 原作ViewGroup: タグ、タグにマウスを載せている間に出す文、表示の並び。 */
    public record Group(ResourceLocation tag, String text, List<View> views) { }

    public static List<Group> groups(Tutorials.Tutorial tutorial) {
        var out = new ArrayList<Group>();
        for (var p : tutorial.previews()) switch (p.kind()) {
            case BLOCK -> {
                Block block = ForgeRegistries.BLOCKS.getValue(p.target());
                if (block != null) out.add(new Group(ICON_VIEW, "", List.of(blockView(block))));
            }
            case ICON -> out.add(new Group(ICON_VIEW, "", List.of(iconView(p.target()))));
            case RECIPES -> {
                Item item = ForgeRegistries.ITEMS.getValue(p.target());
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
        var access = level.registryAccess(); var manager = level.getRecipeManager();
        for (var recipe : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            var result = recipe.getResultItem(access);
            if (!result.is(item)) continue;
            var slots = new List[9];
            var ingredients = recipe.getIngredients();
            boolean shaped = recipe instanceof ShapedRecipe;
            int width = recipe instanceof ShapedRecipe s ? s.getWidth() : 3;
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                int row = i / width, col = i % width;
                slots[shaped ? col + row * 3 : i] = stacks(ingredients.get(i));
            }
            @SuppressWarnings("unchecked") List<ItemStack>[] grid = slots;
            out.add(craftingView(result, grid, shaped ? "shaped" : "shapeless"));
        }
        for (var recipe : manager.getAllRecipesFor(MachineRecipes.FUSION_TYPE.get())) {
            var result = recipe.getResultItem(access);
            if (result.is(item)) out.add(fusorView(stacks(recipe.getIngredients().get(0)), result, recipe.liquid()));
        }
        for (var recipe : manager.getAllRecipesFor(MachineRecipes.FORMING_TYPE.get())) {
            var result = recipe.getResultItem(access);
            if (result.is(item)) out.add(formerView(stacks(recipe.getIngredients().get(0)), result, recipe.operation()));
        }
        for (var recipe : manager.getAllRecipesFor(RecipeType.SMELTING)) {
            var result = recipe.getResultItem(access);
            if (result.is(item)) out.add(smeltingView(stacks(recipe.getIngredients().get(0)), result));
        }
        return out;
    }
    private static List<ItemStack> stacks(Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty() ? List.of() : List.of(ingredient.getItems());
    }
    /** 原作StackDisplay: 32x34、スタックは2秒ごとに切り替わり、アイテムの2倍の大きさ。 */
    @Nullable private static ItemStack stack(GuiGraphics g, List<ItemStack> stacks, float x, float y, float mx, float my, double time) {
        if (stacks == null || stacks.isEmpty()) return null;
        var stack = stacks.get((int) (time / 2) % stacks.size());
        boolean hover = mx >= x && mx < x + 32 && my >= y && my < y + 34;
        if (hover) DeveloperScreen.rect(g.pose(), x, y, 32, 34, 1, 1, 1, .15f);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(2, 2, 1);
        g.renderItem(stack, 0, 0);
        g.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        g.pose().popPose();
        return hover ? stack : null;
    }
    /** 指定の大きさ・倍率の窓を領域の中央に置き、その単位で描く。 */
    private interface Window { @Nullable ItemStack draw(GuiGraphics g, float mx, float my, double time); }
    private static View window(ResourceLocation texture, float w, float h, float scale, Window content) {
        return (g, mx, my, time) -> {
            float x0 = (AREA - w * scale) / 2, y0 = (AREA - h * scale) / 2;
            var pose = g.pose();
            pose.pushPose(); pose.translate(x0, y0, 0); pose.scale(scale, scale, 1);
            DeveloperScreen.quad(pose, texture, 0, 0, w, h, 1, 1, 1, 1);
            var hovered = content.draw(g, (mx - x0) / scale, (my - y0) / scale, time);
            pose.popPose();
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
            g.pose().pushPose(); g.pose().translate(196 / 2f - 30 - font.width(text) * s / 2, -28, 0); g.pose().scale(s, s, 1);
            g.drawString(font, text, 0, 0, 0xffffffff, false);
            g.pose().popPose();
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
            g.pose().pushPose(); g.pose().translate(81 + (52 - font.width(text) * s) / 2, 14.5f, 0); g.pose().scale(s, s, 1);
            g.drawString(font, text, 0, 0, 0xffffffff, false);
            g.pose().popPose();
            return a != null ? a : b;
        });
    }
    private static View formerView(List<ItemStack> in, ItemStack out, io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity.Mode mode) {
        var modeIcon = tex("guis/icons/icon_former_" + mode.name().toLowerCase(java.util.Locale.ROOT));
        return window(FORMER, 192, 192, .5f, (g, mx, my, time) -> {
            DeveloperScreen.quad(g.pose(), modeIcon, 82.66667f, 22.7f, 25, 25, 1, 1, 1, 1);
            // 原作のスロットはここでは幅25。その上に幅32の表示を中央揃えで置く。
            var a = stack(g, in, 11.33333f + 12.5f - 16, 88.5f + 12.5f - 17, mx, my, time);
            var b = stack(g, List.of(out), 155.33333f + 12.5f - 16, 88.5f + 12.5f - 17, mx, my, time);
            return a != null ? a : b;
        });
    }

    // ---- 物の表示 ----
    static View blockView(Block block) {
        return (g, mx, my, time) -> {
            var client = Minecraft.getInstance();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(AREA / 2, AREA / 2 + 4, 100);
            pose.scale(52, -52, 52);
            pose.mulPose(Axis.XP.rotationDegrees(-20));
            // 原作: (GameTimer.getAbsTime() / 80) % 360度。LambdaLib2のtimerは秒を数える（スタックの2秒ごとの切り替えも同じ単位）
            // ので、ブロックは80秒で1度回り、止まって見える。これはそのまま残す。
            pose.mulPose(Axis.YP.rotationDegrees((float) ((Util.getMillis() / 1000.0 / 80.0) % 360.0)));
            pose.translate(-.5, -.5, -.5);
            Lighting.setupForFlatItems();
            var buffers = client.renderBuffers().bufferSource();
            client.getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            buffers.endBatch();
            Lighting.setupFor3DItems();
            pose.popPose();
            return null;
        };
    }
    static View iconView(ResourceLocation texture) {
        return (g, mx, my, time) -> {
            float size = 48;
            DeveloperScreen.quad(g.pose(), texture, (AREA - size) / 2, (AREA - size) / 2, size, size, 1, 1, 1, 1);
            return null;
        };
    }
}
