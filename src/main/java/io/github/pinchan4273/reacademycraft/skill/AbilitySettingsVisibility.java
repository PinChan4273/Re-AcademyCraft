package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * 能力設定の画面の行の並びと、どの行を出すかの規則（v1.5.0、ユーザーの決定）。クライアントのクラスを参照しない純粋な関数なので、
 * 画面（{@code client/AbilitySettingsScreen}）と、専用サーバーでも動くGameTestの両方から使う。
 * <ul>
 * <li>共通の設定（CPセーフティ・CP表示）: 能力の系統を持つプレイヤーに出す。</li>
 * <li>系統の設定（行に系統だけがある）: 今の系統と一致するときだけ。</li>
 * <li>技能の設定（行に技能がある）: その技能が今の系統に属し、習得済みのときだけ。</li>
 * </ul>
 * 能力OFF・過負荷・干渉・今のプリセットに未配置のような一時的な状態は見ない（習得済みなら操作できる）。出す行の無い見出しと、余った余白は詰める。
 * 何も出す物が無ければ、短い案内を1行出す。画面の一番下の状態の行（{@link Kind#STATUS}）は常に出す。
 * 出さない設定のサーバーの値は、変えも消しもしない（ここは表示だけを決める）。
 */
public final class AbilitySettingsVisibility {
    private AbilitySettingsVisibility() { }

    /** 行の種類（画面の描き方と操作を決める）。 */
    public enum Kind { HEAD, MODE, TARGET, NOTE, GAP, STATUS, SAFETY_PERCENT, CP_DISPLAY, CP_IDLE_SHOW, CP_IDLE, CP_IDLE_CUSTOM, BEETLE_CHOICE, BEETLE_FOLLOW, BEETLE_RECALL, HEAVEN_OPEN, WING_GLOW }

    /**
     * 一覧の1要素。settingは設定の名前（見出し・注記・余白ではnull）。categoryは行が属する系統、skillは属する技能（共通の行ではどちらもnull、
     * 系統の行ではskillだけnull）。textは翻訳キー。
     */
    public record Row(Kind kind, @Nullable ResourceLocation setting, @Nullable ResourceLocation category, @Nullable ResourceLocation skill, String text) { }

    /** 何も出す物が無いときの案内の翻訳キー。 */
    public static final String NOTHING = "academy.ability_settings.nothing";
    /** 甲虫の追従開始距離の説明の翻訳キー。 */
    public static final String BEETLE_FOLLOW_NOTE = "academy.ability_settings.beetle.follow.note";
    /** 甲虫の攻撃対象の行の前の説明の翻訳キー。 */
    public static final String BEETLE_TARGETS_NOTE = "academy.ability_settings.beetle.targets";

    /** 見出しと、その下の行の組。 */
    private record Section(Row head, List<Row> items) { }

    /** 技能の属する系統。未元物質は登録の前でも分かるように直接、それ以外は技能の一覧から。分からなければnull。 */
    public static @Nullable ResourceLocation categoryOf(ResourceLocation skill) {
        if (DarkMatter.owns(skill)) return DarkMatter.CATEGORY;
        var definition = SkillCatalog.find(skill);
        return definition == null ? null : definition.category();
    }

    private static Row common(Kind kind, @Nullable ResourceLocation setting, String text) { return new Row(kind, setting, null, null, text); }
    private static Row skill(Kind kind, @Nullable ResourceLocation setting, ResourceLocation skill, String text) { return new Row(kind, setting, categoryOf(skill), skill, text); }

    /** 全部の見出しと行（出すかどうかを決める前）。並びは画面の順。共通の行は、どの系統の人にも見えるように先頭。 */
    private static List<Section> sections() {
        var out = new ArrayList<Section>();
        out.add(new Section(common(Kind.HEAD, null, "academy.ability_settings.common"), List.of(
                common(Kind.TARGET, AbilitySettings.CP_SAFETY, "academy.ability_settings.cp_safety"),
                common(Kind.SAFETY_PERCENT, AbilitySettings.CP_SAFETY_PERCENT, "academy.ability_settings.cp_safety_percent"),
                common(Kind.CP_DISPLAY, null, "academy.ability_settings.cp_display"),
                common(Kind.CP_IDLE_SHOW, null, "academy.ability_settings.cp_idle_show"),
                common(Kind.CP_IDLE, null, "academy.ability_settings.cp_idle"),
                common(Kind.CP_IDLE_CUSTOM, null, "academy.ability_settings.cp_idle_custom"))));
        // 未元物質の翼の発光の強さ（各自の表示設定。2026-11-03のユーザーの決定: シェーダーで光りすぎるときに下げる）。系統の行なので、未元物質の人に出す。
        out.add(new Section(new Row(Kind.HEAD, null, DarkMatter.CATEGORY, null, "academy.ability.dark_matter"), List.of(
                new Row(Kind.WING_GLOW, null, DarkMatter.CATEGORY, null, "academy.ability_settings.wing_glow"),
                new Row(Kind.NOTE, null, DarkMatter.CATEGORY, null, "academy.ability_settings.wing_glow.note"))));
        // 白翼飛行の飛び立ちと停止の操作（2026-11-03のユーザーの決定）。
        out.add(new Section(skill(Kind.HEAD, null, DarkMatter.FLIGHT, "academy.skill.white_wing_flight"), List.of(
                skill(Kind.BEETLE_CHOICE, AbilitySettings.FLIGHT_TAKEOFF, DarkMatter.FLIGHT, "academy.ability_settings.flight.takeoff"),
                skill(Kind.NOTE, null, DarkMatter.FLIGHT, "academy.ability_settings.flight.takeoff.note"))));
        var beetle = DarkMatter.BEETLE;
        var beetleRows = new ArrayList<Row>();
        beetleRows.add(skill(Kind.BEETLE_CHOICE, AbilitySettings.BEETLE_STANCE, beetle, "academy.ability_settings.beetle.stance"));
        beetleRows.add(skill(Kind.BEETLE_FOLLOW, AbilitySettings.BEETLE_FOLLOW, beetle, "academy.ability_settings.beetle.follow"));
        beetleRows.add(skill(Kind.NOTE, null, beetle, BEETLE_FOLLOW_NOTE));
        // 自動で狙う相手（ユーザーの決定。分類はElectron Missileと同じ4つ）。
        beetleRows.add(skill(Kind.NOTE, null, beetle, BEETLE_TARGETS_NOTE));
        for (var kind : ElectronMissileTargetKind.values()) beetleRows.add(skill(Kind.TARGET, AbilitySettings.beetleTarget(kind), beetle, kind.translation()));
        beetleRows.add(skill(Kind.NOTE, null, beetle, "academy.electron_missile.target.player_rules"));
        beetleRows.add(skill(Kind.BEETLE_CHOICE, AbilitySettings.BEETLE_REPAIR, beetle, "academy.ability_settings.beetle.repair"));
        beetleRows.add(skill(Kind.TARGET, AbilitySettings.BEETLE_CONTACT_BREAK, beetle, "academy.ability_settings.beetle.contact_break"));
        beetleRows.add(skill(Kind.BEETLE_RECALL, AbilitySettings.BEETLE_RECALL, beetle, "academy.ability_settings.beetle.recall"));
        out.add(new Section(skill(Kind.HEAD, null, beetle, "academy.skill.dark_matter_beetle"), beetleRows));
        out.add(new Section(skill(Kind.HEAD, null, DarkMatter.BODY, "academy.skill.dark_matter_body"), List.of(
                skill(Kind.BEETLE_CHOICE, AbilitySettings.DARK_BODY_RESPAWN, DarkMatter.BODY, "academy.ability_settings.dark_body.respawn"))));
        out.add(new Section(skill(Kind.HEAD, null, DarkMatter.HEAVEN, "academy.skill.heaven_manifest"), List.of(
                skill(Kind.HEAVEN_OPEN, null, DarkMatter.HEAVEN, "academy.ability_settings.heaven.open"))));
        var missile = new ArrayList<Row>();
        missile.add(skill(Kind.MODE, AbilitySettings.MISSILE_AIM, ElectronMissile.ID, "academy.ability_settings.aim"));
        for (var kind : ElectronMissileTargetKind.values()) missile.add(skill(Kind.TARGET, AbilitySettings.missileTarget(kind), ElectronMissile.ID, kind.translation()));
        missile.add(skill(Kind.NOTE, null, ElectronMissile.ID, "academy.electron_missile.target.player_rules"));
        out.add(new Section(skill(Kind.HEAD, null, ElectronMissile.ID, "academy.skill.electron_missile"), missile));
        out.add(new Section(skill(Kind.HEAD, null, LightShield.ID, "academy.skill.light_shield"), List.of(
                skill(Kind.MODE, AbilitySettings.SHIELD_MODE, LightShield.ID, "academy.ability_settings.mode"))));
        return out;
    }

    /**
     * その行を出すか（見出し・余白は{@link #rows}が詰めるので、ここでは行そのものの規則だけ）。categoryはプレイヤーの今の系統（無ければnull）、
     * learnedは技能の習得の有無。
     */
    public static boolean visible(Row row, @Nullable ResourceLocation category, Predicate<ResourceLocation> learned) {
        if (row.kind() == Kind.STATUS) return true;
        if (category == null) return false;
        if (row.skill() != null) return (row.category() == null || row.category().equals(category)) && learned.test(row.skill());
        return row.category() == null || row.category().equals(category);
    }

    /** 画面に出す行（見出しと余白を詰め、最後に状態の行）。 */
    public static List<Row> rows(@Nullable ResourceLocation category, Predicate<ResourceLocation> learned) {
        var out = new ArrayList<Row>();
        for (var section : sections()) {
            var shown = section.items().stream().filter(r -> visible(r, category, learned)).toList();
            if (shown.isEmpty()) continue;
            if (!out.isEmpty()) out.add(common(Kind.GAP, null, ""));
            out.add(section.head());
            out.addAll(shown);
        }
        if (out.isEmpty()) out.add(common(Kind.NOTE, null, NOTHING));
        out.add(common(Kind.STATUS, null, ""));
        return List.copyOf(out);
    }

    /** プレイヤーのデータから（読めないデータは系統の無い人と同じ）。読み取り専用のデータは系統を持たないので、案内と状態の行だけになる。 */
    public static List<Row> rows(@Nullable PlayerAbilityData data) {
        if (data == null) return rows(null, skill -> false);
        return rows(data.getAbility(), data::hasLearned);
    }
}
