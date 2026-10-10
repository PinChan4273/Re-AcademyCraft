package io.github.pinchan4273.reacademycraft.tutorial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * 原作TutorialInitとTutorialRegistry: ミサカクラウドの記事。原作の順。アイテムを持つ記事は、プレイヤーがそのどれかを初めて
 * 作業台で作る・精錬する・拾うと解放される（原作のitemObtainedの条件をORで結んだもの）。持たない記事は最初からある
 * （原作のisDefaultInstalled）。原作はenergy_bridge.mdも同梱するが登録していないので、ここでも登録しない。
 *
 * 端末の記事のアイテムは、原作がアプリのレジストリをたどって求めるのと同じく、端末インストーラーと、プリインストールでない
 * すべてのアプリのアイテム。
 */
public final class Tutorials {
    /** 記事1つ: id、それを解放するアイテム、プレビュー窓に表示するもの。 */
    public record Tutorial(String id, List<ResourceLocation> items, List<Preview> previews) {
        public boolean defaultInstalled() { return items.isEmpty(); }
    }
    /** 原作ViewGroups: プレビュー窓で回るブロック、アイテムの作業台レシピ、またはアイコン。 */
    public record Preview(Kind kind, ResourceLocation target) {
        public enum Kind { BLOCK, RECIPES, ICON }
    }
    private static final Map<String, Tutorial> ALL = new LinkedHashMap<>();
    static {
        add("welcome", List.of(), List.of());
        add("ores", ids("constraint_metal", "imagsil_ore", "crystal_ore", "reso_ore"), List.of(
                block("constraint_metal"), block("imagsil_ore"), block("crystal_ore"), block("reso_ore"),
                new Preview(Preview.Kind.ICON, id("textures/item/phase_liquid_mat.png")),
                recipes("constraint_plate"), recipes("imag_silicon_ingot"), recipes("wafer"), recipes("imag_silicon_piece")));
        add("phase_generator", ids("phase_gen"), List.of(recipes("phase_gen")));
        add("solar_generator", ids("solar_gen"), List.of(recipes("solar_gen")));
        add("wind_generator", ids("windgen_base", "windgen_fan", "windgen_main", "windgen_pillar"),
                List.of(recipes("windgen_base"), recipes("windgen_pillar"), recipes("windgen_main"), recipes("windgen_fan")));
        add("metal_former", ids("metal_former"), List.of(recipes("metal_former")));
        add("imag_fusor", ids("imag_fusor"), List.of(recipes("imag_fusor")));
        add("terminal", ids("terminal_installer", "app_skill_tree", "app_media_player", "app_freq_transmitter"),
                List.of(recipes("terminal_installer"), recipes("app_skill_tree"), recipes("app_media_player"), recipes("app_freq_transmitter")));
        add("ability_developer", ids("developer_portable", "dev_normal", "dev_advanced"),
                List.of(recipes("developer_portable"), recipes("dev_normal"), recipes("dev_advanced")));
        add("ability_basis", List.of(), List.of());
        add("misc", List.of(), List.of());
        add("develop_ability", List.of(), List.of());
        add("wireless_network", List.of(), List.of());
    }
    private Tutorials() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }
    private static List<ResourceLocation> ids(String... paths) { return java.util.Arrays.stream(paths).map(Tutorials::id).toList(); }
    private static Preview block(String path) { return new Preview(Preview.Kind.BLOCK, id(path)); }
    private static Preview recipes(String path) { return new Preview(Preview.Kind.RECIPES, id(path)); }
    private static void add(String id, List<ResourceLocation> items, List<Preview> previews) {
        ALL.put(id, new Tutorial(id, List.copyOf(items), List.copyOf(previews)));
    }
    public static List<Tutorial> all() { return List.copyOf(ALL.values()); }
    @Nullable public static Tutorial byId(String id) { return ALL.get(id); }
    /** アイテムが解放する記事。 */
    public static List<Tutorial> activatedBy(ResourceLocation item) {
        return ALL.values().stream().filter(t -> t.items().contains(item)).toList();
    }
}
