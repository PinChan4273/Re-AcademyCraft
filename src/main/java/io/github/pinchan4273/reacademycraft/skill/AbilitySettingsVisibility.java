package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;

/**
 * 能力設定の画面の行の並びと、どの行を出すかの規則（v1.5.0）。クライアントのクラスを参照しない純粋な関数なので、
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
    public enum Kind { HEAD, MODE, TARGET, NOTE, GAP, STATUS, SAFETY_PERCENT, CP_DISPLAY, CP_IDLE_SHOW, CP_IDLE, CP_IDLE_CUSTOM, BEETLE_CHOICE, BEETLE_FOLLOW, BEETLE_RECALL, HEAVEN_OPEN, WING_GLOW,
        /** 1.5.2: 解析度のHUDの表示の形式（選んだ物だけ・すべて・出さない）。クライアントの個人設定。 */
        ANALYSIS_MODE,
        /** 1.5.2: 解析度の1項目（textは分類のキー。翻訳キーではない）。割合とHUDに出すかのチェックボックス。 */
        ANALYSIS,
        /**
         * 1.5.2: 技能1つのパッシブ／アクティブの切り替え（settingは切り替えの設定、textは技能の名前の翻訳キー）。系統の行なので、
         * 未習得・熟練度100%未満の技能も出す（画面は、切り替えられない行を理由と一緒に灰色で出す）。
         */
        PASSIVE,
        /**
         * 1.5.2: キーの案内に、パッシブの技能の行をどう出すか（非表示・アイコン・アイコン＋自動）。
         * クライアントの個人設定で、左右の矢印で選ぶ。パッシブにできる技能がある系統にだけ出す（系統の行）。
         */
        KEY_HINT_PASSIVE,
        /** 1.5.2: 未元物質人間の形態の予約（0〜30%）。未元物質人間でない・セーフティがオフの間は、灰色で動かせない。 */
        FORM_RESERVE,
        /** 1.5.2: セーフティの並びを編集するプリセット（1〜4。画面だけの選択で、サーバーへは送らない）。 */
        SAFETY_PRESET,
        /** 1.5.2: 形態の維持の行（最上位で固定。動かせない）。 */
        SAFETY_FORM,
        /** 1.5.2: セーフティの並びの1行（textは並びの中のインデックス。技能は選んだプリセットの今の並びで決まる）。上下のボタンで動かす。 */
        SAFETY_ORDER }

    /**
     * 一覧の1要素。settingは設定の名前（見出し・注記・余白ではnull）。categoryは行が属する系統、skillは属する技能（共通の行ではどちらもnull、
     * 系統の行ではskillだけnull）。textは翻訳キー。
     */
    public record Row(Kind kind, @Nullable Identifier setting, @Nullable Identifier category, @Nullable Identifier skill, String text) { }

    /** 何も出す物が無いときの案内の翻訳キー。 */
    public static final String NOTHING = "academy.ability_settings.nothing";
    /** 甲虫の追従開始距離の説明の翻訳キー。 */
    public static final String BEETLE_FOLLOW_NOTE = "academy.ability_settings.beetle.follow.note";
    /** 甲虫の攻撃対象の行の前の説明の翻訳キー。 */
    public static final String BEETLE_TARGETS_NOTE = "academy.ability_settings.beetle.targets";
    /** 解析度の説明・HUDへの出し方・まだ何も無いときの案内の翻訳キー。 */
    public static final String ANALYSIS_NOTE = "academy.ability_settings.analysis.note", ANALYSIS_HINT = "academy.ability_settings.analysis.hint",
            ANALYSIS_EMPTY = "academy.ability_settings.analysis.empty";
    /** 1.5.2: パッシブ／アクティブの切り替えの見出しと説明の翻訳キー。 */
    public static final String PASSIVE_HEAD = "academy.ability_settings.passive", PASSIVE_NOTE = "academy.ability_settings.passive.note";
    /** 1.5.2: キーの案内へのパッシブの行の出し方の行の翻訳キー。 */
    public static final String KEY_HINT_PASSIVE = "academy.ability_settings.passive.key_hint";
    /** 1.5.2: フラッシングをパッシブにすると、能力使用モードの間は移動キーがずっと瞬間移動になる、という注記の翻訳キー。 */
    public static final String PASSIVE_FLASHING_NOTE = "academy.ability_settings.passive.flashing.note";
    /**
     * 1.5.2: ベクトル反射の反射方向の行・説明と、PvPの相手を候補に含めるかの行・説明の翻訳キー。反射方向の値の名前は
     * 「行の翻訳キー + "." + 値」（{@link ReflectionDirection#translation()}）。
     */
    public static final String REFLECTION_DIRECTION = "academy.ability_settings.reflection.direction",
            REFLECTION_DIRECTION_NOTE = "academy.ability_settings.reflection.direction.note",
            REFLECTION_PVP = "academy.ability_settings.reflection.pvp", REFLECTION_PVP_NOTE = "academy.ability_settings.reflection.pvp.note";
    /** 1.5.2: 形態の予約と、セーフティの優先順位の欄の翻訳キー。 */
    public static final String FORM_RESERVE = "academy.ability_settings.form_reserve", FORM_RESERVE_NOTE = "academy.ability_settings.form_reserve.note",
            FORM_RESERVE_NOTE2 = "academy.ability_settings.form_reserve.note2",
            SAFETY_ORDER = "academy.ability_settings.safety_order", SAFETY_ORDER_NOTE = "academy.ability_settings.safety_order.note",
            SAFETY_ORDER_NOTE2 = "academy.ability_settings.safety_order.note2",
            SAFETY_ORDER_PRESET = "academy.ability_settings.safety_order.preset", SAFETY_ORDER_FORM = "academy.ability_settings.safety_order.form",
            SAFETY_ORDER_EMPTY = "academy.ability_settings.safety_order.empty";
    /** 翼の発光の、シェーダーパックの使用中の描き方の注の翻訳キー。 */
    public static final String WING_GLOW_SHADER_NOTE = "academy.ability_settings.wing_glow.shader_note";

    /** 見出しと、その下の行の組。 */
    private record Section(Row head, List<Row> items) { }

    /** 技能の属する系統。未元物質は登録の前でも分かるように直接、それ以外は技能の一覧から。分からなければnull。 */
    public static @Nullable Identifier categoryOf(Identifier skill) {
        if (DarkMatter.owns(skill)) return DarkMatter.CATEGORY;
        var definition = SkillCatalog.find(skill);
        return definition == null ? null : definition.category();
    }

    private static Row common(Kind kind, @Nullable Identifier setting, String text) { return new Row(kind, setting, null, null, text); }
    private static Row skill(Kind kind, @Nullable Identifier setting, Identifier skill, String text) { return new Row(kind, setting, categoryOf(skill), skill, text); }

    /**
     * 全部の見出しと行（出すかどうかを決める前）。並びは画面の順。共通の行は、どの系統の人にも見えるように先頭。analysisKeysは解析度の
     * 一覧に出す分類のキー（画面が並べた順。クライアントの値なので、呼ぶ側が渡す）。
     */
    private static List<Section> sections(List<String> analysisKeys, int orderCount) {
        var out = new ArrayList<Section>();
        out.add(new Section(common(Kind.HEAD, null, "academy.ability_settings.common"), List.of(
                common(Kind.TARGET, AbilitySettings.CP_SAFETY, "academy.ability_settings.cp_safety"),
                common(Kind.SAFETY_PERCENT, AbilitySettings.CP_SAFETY_PERCENT, "academy.ability_settings.cp_safety_percent"),
                // 1.5.2: 形態の予約（系統の行なので、未元物質の人に出す）。
                new Row(Kind.FORM_RESERVE, AbilitySettings.FORM_RESERVE, DarkMatter.CATEGORY, null, FORM_RESERVE),
                new Row(Kind.NOTE, null, DarkMatter.CATEGORY, null, FORM_RESERVE_NOTE),
                new Row(Kind.NOTE, null, DarkMatter.CATEGORY, null, FORM_RESERVE_NOTE2),
                common(Kind.CP_DISPLAY, null, "academy.ability_settings.cp_display"),
                common(Kind.CP_IDLE_SHOW, null, "academy.ability_settings.cp_idle_show"),
                common(Kind.CP_IDLE, null, "academy.ability_settings.cp_idle"),
                common(Kind.CP_IDLE_CUSTOM, null, "academy.ability_settings.cp_idle_custom"))));
        // 1.5.2: CPセーフティの優先順位（共通の行）。編集するプリセット、形態の行（未元物質の人だけ。最上位で固定）、
        // 今の系統の習得済みの、維持費のある技能の数だけの並びの行。
        var order = new ArrayList<Row>();
        order.add(common(Kind.NOTE, null, SAFETY_ORDER_NOTE));
        order.add(common(Kind.NOTE, null, SAFETY_ORDER_NOTE2));
        order.add(common(Kind.SAFETY_PRESET, null, SAFETY_ORDER_PRESET));
        order.add(new Row(Kind.SAFETY_FORM, null, DarkMatter.CATEGORY, null, SAFETY_ORDER_FORM));
        for (int i = 0; i < orderCount; i++) order.add(common(Kind.SAFETY_ORDER, null, Integer.toString(i)));
        if (orderCount == 0) order.add(common(Kind.NOTE, null, SAFETY_ORDER_EMPTY));
        out.add(new Section(common(Kind.HEAD, null, SAFETY_ORDER), order));
        // 1.5.2: パッシブ／アクティブの切り替え。系統ごとに、その系統で切り替えられる技能をすべて出す（系統の行）。
        // 飛行の技能がある系統（ベクトル操作・原子崩し）には、パッシブの飛行の離陸・停止の操作（白翼飛行と同じ設定）も出す。
        // 1.5.2: 切り替え型の5つを足したので、電撃使い（身体強化）と空間移動（フラッシング）にも欄が出る。
        for (var category : List.of(AbilityCategory.VECMANIP.id(), AbilityCategory.MELTDOWNER.id(), AbilityCategory.ELECTROMASTER.id(),
                AbilityCategory.TELEPORTER.id(), DarkMatter.CATEGORY)) {
            var passive = new ArrayList<Row>();
            passive.add(new Row(Kind.NOTE, null, category, null, PASSIVE_NOTE));
            boolean flight = false;
            for (var skill : PassiveSkills.switchableIn(category)) {
                passive.add(new Row(Kind.PASSIVE, PassiveSkills.setting(skill), category, null, "academy.skill." + skill.getPath()));
                // フラッシングの行の下に、パッシブの間の移動キーの注記（系統の行。習得に関わらず行と一緒に出す）。
                if (Flashing.ID.equals(skill)) passive.add(new Row(Kind.NOTE, null, category, null, PASSIVE_FLASHING_NOTE));
                flight |= PassiveSkills.flight(skill);
            }
            // キーの案内へのパッシブの行の出し方（クライアントの個人設定）。パッシブにできる技能がある系統にだけ出す。
            if (!PassiveSkills.switchableIn(category).isEmpty()) passive.add(new Row(Kind.KEY_HINT_PASSIVE, null, category, null, KEY_HINT_PASSIVE));
            if (flight) passive.add(new Row(Kind.BEETLE_CHOICE, AbilitySettings.FLIGHT_TAKEOFF, category, null, "academy.ability_settings.flight.takeoff"));
            out.add(new Section(new Row(Kind.HEAD, null, category, null, PASSIVE_HEAD), passive));
        }
        // 未元物質の翼の発光の強さ（各自の表示設定。シェーダーで光りすぎるときに下げる）。系統の行なので、未元物質の人に出す。
        // シェーダーパックの使用中は100%を90%として描く。その注は、シェーダーパックの有無にかかわらず常に出す
        // （シェーダーを入れる前に値を決める人にも前もって分かり、切り替えで行の並びが動かない。行の並びはクライアントの状態を見ない純粋な関数のまま保つ）。
        out.add(new Section(new Row(Kind.HEAD, null, DarkMatter.CATEGORY, null, "academy.ability.dark_matter"), List.of(
                new Row(Kind.WING_GLOW, null, DarkMatter.CATEGORY, null, "academy.ability_settings.wing_glow"),
                new Row(Kind.NOTE, null, DarkMatter.CATEGORY, null, "academy.ability_settings.wing_glow.note"),
                new Row(Kind.NOTE, null, DarkMatter.CATEGORY, null, WING_GLOW_SHADER_NOTE))));
        // 白翼飛行の飛び立ちと停止の操作。
        out.add(new Section(skill(Kind.HEAD, null, DarkMatter.FLIGHT, "academy.skill.white_wing_flight"), List.of(
                skill(Kind.BEETLE_CHOICE, AbilitySettings.FLIGHT_TAKEOFF, DarkMatter.FLIGHT, "academy.ability_settings.flight.takeoff"),
                skill(Kind.NOTE, null, DarkMatter.FLIGHT, "academy.ability_settings.flight.takeoff.note"))));
        var beetle = DarkMatter.BEETLE;
        var beetleRows = new ArrayList<Row>();
        beetleRows.add(skill(Kind.BEETLE_CHOICE, AbilitySettings.BEETLE_STANCE, beetle, "academy.ability_settings.beetle.stance"));
        beetleRows.add(skill(Kind.BEETLE_FOLLOW, AbilitySettings.BEETLE_FOLLOW, beetle, "academy.ability_settings.beetle.follow"));
        beetleRows.add(skill(Kind.NOTE, null, beetle, BEETLE_FOLLOW_NOTE));
        // 自動で狙う相手（分類はElectron Missileと同じ4つ）。
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
        // 1.5.2: ベクトル反射の反射方向と、自動の2方式でのPvPの相手の扱い（技能の行なので、反射を習得したベクトル操作の人に出す）。
        out.add(new Section(skill(Kind.HEAD, null, VecReflection.ID, "academy.skill.vec_reflection"), List.of(
                skill(Kind.BEETLE_CHOICE, AbilitySettings.REFLECTION_DIRECTION, VecReflection.ID, REFLECTION_DIRECTION),
                skill(Kind.NOTE, null, VecReflection.ID, REFLECTION_DIRECTION_NOTE),
                skill(Kind.TARGET, AbilitySettings.REFLECTION_PVP, VecReflection.ID, REFLECTION_PVP),
                skill(Kind.NOTE, null, VecReflection.ID, REFLECTION_PVP_NOTE))));
        // 1.5.2: ベクトル操作の解析度（系統の行なので、ベクトル操作の人に出す。偏向・反射を覚える前から、何も無いことの案内を出す）。
        var vector = AbilityCategory.VECMANIP.id();
        var analysis = new ArrayList<Row>();
        analysis.add(new Row(Kind.ANALYSIS_MODE, null, vector, null, "academy.ability_settings.analysis.mode"));
        analysis.add(new Row(Kind.NOTE, null, vector, null, ANALYSIS_NOTE));
        analysis.add(new Row(Kind.NOTE, null, vector, null, ANALYSIS_HINT));
        if (analysisKeys.isEmpty()) analysis.add(new Row(Kind.NOTE, null, vector, null, ANALYSIS_EMPTY));
        for (var key : analysisKeys) analysis.add(new Row(Kind.ANALYSIS, null, vector, null, key));
        out.add(new Section(new Row(Kind.HEAD, null, vector, null, "academy.ability_settings.analysis"), analysis));
        return out;
    }

    /**
     * その行を出すか（見出し・余白は{@link #rows}が詰めるので、ここでは行そのものの規則だけ）。categoryはプレイヤーの今の系統（無ければnull）、
     * learnedは技能の習得の有無。
     */
    public static boolean visible(Row row, @Nullable Identifier category, Predicate<Identifier> learned) {
        if (row.kind() == Kind.STATUS) return true;
        if (category == null) return false;
        if (row.skill() != null) return (row.category() == null || row.category().equals(category)) && learned.test(row.skill());
        return row.category() == null || row.category().equals(category);
    }

    /** 画面に出す行（見出しと余白を詰め、最後に状態の行）。解析度の項目は無いものとして並べる。 */
    public static List<Row> rows(@Nullable Identifier category, Predicate<Identifier> learned) {
        return rows(category, learned, List.of());
    }
    /** 画面に出す行。analysisKeysは解析度の一覧に出す分類のキー（ベクトル操作の人だけに出る）。 */
    public static List<Row> rows(@Nullable Identifier category, Predicate<Identifier> learned, List<String> analysisKeys) {
        var out = new ArrayList<Row>();
        int orderCount = SafetyPriority.candidates(category, learned, null).size();
        for (var section : sections(analysisKeys, orderCount)) {
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
    public static List<Row> rows(@Nullable PlayerAbilityData data) { return rows(data, List.of()); }
    /** プレイヤーのデータと、解析度の一覧に出す分類のキーから。 */
    public static List<Row> rows(@Nullable PlayerAbilityData data, List<String> analysisKeys) {
        if (data == null) return rows(null, skill -> false, analysisKeys);
        return rows(data.getAbility(), data::hasLearned, analysisKeys);
    }
}
