package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 能力設定の画面（1.4.0）が名前で読み書きする設定の一覧と、その入口（サーバー）。
 * <p>
 * 書き込みは、コマンドと同じ入口（{@link ElectronMissileSettings}・{@link LightShieldSettings}）を呼ぶだけで、値の保存・検証・
 * 動作中のセッションへの反映は、そちらのまま。ここは、画面から届いた「設定の名前と値の文字列」を、その入口の呼び出しへ置き換える。
 * 知らない名前や、許されない値は何も変えない。
 * <p>
 * 設定の名前は名前空間付きのID（例: {@code academy:electron_missile.aim}）。外部のアドオンが設定を登録できるようにするとき、
 * 同じ形の名前で並べられるようにしてある。
 */
public final class AbilitySettings {
    private AbilitySettings() { }

    public static final Identifier MISSILE_AIM = id("electron_missile.aim");
    public static final Identifier SHIELD_MODE = id("light_shield.mode");
    /** v1.5.0: CPセーフティのON/OFFと停止ライン（%）。全系統共通。 */
    public static final Identifier CP_SAFETY = id("cp_safety"), CP_SAFETY_PERCENT = id("cp_safety_percent");
    /** 1.5.2: 未元物質人間の形態の予約（最大CPに対する整数の%、0〜30）。 */
    public static final Identifier FORM_RESERVE = id("cp_safety_form_reserve");
    /** 1.5.2: プリセット（1〜4）のセーフティの並び（{@link SafetyPriority#encode}の文字列。空は既定の並びで、値は"-"）。 */
    public static Identifier safetyOrder(int preset) { return id("cp_safety_order." + (preset + 1)); }
    /** 並びの値の「既定の並び」の表記（能力設定の値は空にできないので）。 */
    public static final String ORDER_DEFAULT = "-";
    /** 並びの設定の名前から、プリセットのインデックス（0〜3。並びの設定でなければ −1）。 */
    public static int safetyOrderPreset(@Nullable Identifier id) {
        for (int i = 0; i < PlayerAbilityData.PRESET_COUNT; i++) if (safetyOrder(i).equals(id)) return i;
        return -1;
    }
    /** v1.5.0: 甲虫の接触ブロック破壊（on/off）、修復方式（auto/resummon）、攻撃方針（near/mid/far）、全個体の回収（値はrecallだけ）。 */
    public static final Identifier BEETLE_CONTACT_BREAK = id("dark_matter_beetle.contact_break"), BEETLE_REPAIR = id("dark_matter_beetle.repair"),
            BEETLE_STANCE = id("dark_matter_beetle.stance"), BEETLE_RECALL = id("dark_matter_beetle.recall");
    /** v1.5.0: 甲虫の追従開始距離。値は8〜48の4刻みの整数の文字列（例 "32"）。 */
    public static final Identifier BEETLE_FOLLOW = id("dark_matter_beetle.follow");
    /** 甲虫が自動で狙う1分類の設定の名前（値はon/off。分類はElectron Missileと同じ）。 */
    public static Identifier beetleTarget(ElectronMissileTargetKind kind) { return id("dark_matter_beetle.target." + kind.id()); }
    private static @Nullable ElectronMissileTargetKind beetleTargetKind(Identifier setting) {
        for (var kind : ElectronMissileTargetKind.values()) if (beetleTarget(kind).equals(setting)) return kind;
        return null;
    }
    /** v1.5.0: 死亡後の復活形態（body/human）。リスポーン時に参照し、今の形態は変えない。 */
    public static final Identifier DARK_BODY_RESPAWN = id("dark_matter_body.respawn");
    public static final String RESPAWN_BODY = "body", RESPAWN_HUMAN = "human";
    /** 白翼飛行の飛び立ちと停止の操作。holdは長押しで飛び立ちスニーク2回でやめる、doubleはジャンプ2回で飛び立ちジャンプ2回でやめる。 */
    public static final Identifier FLIGHT_TAKEOFF = id("white_wing_flight.takeoff");
    public static final String TAKEOFF_HOLD = "hold", TAKEOFF_DOUBLE = "double";
    /**
     * 1.5.2: ベクトル反射の反射方向（値は{@link ReflectionDirection#id()}: attacker・look・nearest・visible）と、自動の2方式で
     * PvPの相手プレイヤーを候補に含めるか（on/off）。能力OFF・過負荷・反射の動作中も変えられ、次に成立した反射から効く。
     */
    public static final Identifier REFLECTION_DIRECTION = id("vec_reflection.direction"), REFLECTION_PVP = id("vec_reflection.pvp");
    /**
     * 1.5.2: 技能のパッシブ／アクティブの切り替えの設定の名前（{@link PassiveSkills#setting}、例 {@code academy:passive.storm_wing}）。
     * 値は{@link PassiveSkills#PASSIVE}か{@link PassiveSkills#ACTIVE}。パッシブにするには、その系統で習得済みで熟練度100%であること（サーバーが確かめる）。
     */
    public static Identifier passive(Identifier skill) { return PassiveSkills.setting(skill); }
    /** 全個体の回収の入口に送る値。回収は保存する値ではなく操作なので、読むときもこの値を返す。 */
    public static final String RECALL = "recall";
    /** 値の文字列の上限（通信でも使う）。 */
    public static final int MAX_VALUE = 16;
    public static final String ON = "on", OFF = "off";

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", path); }

    /** Electron Missileの攻撃対象の1分類の設定の名前。 */
    public static Identifier missileTarget(ElectronMissileTargetKind kind) { return id("electron_missile.target." + kind.id()); }

    /** 設定の名前と、その設定が属する技能。画面の並びはこの順。 */
    public record Entry(Identifier id, Identifier skill) { }

    /** 本体の設定のすべて。 */
    public static List<Entry> all() {
        var out = new ArrayList<Entry>();
        out.add(new Entry(MISSILE_AIM, ElectronMissile.ID));
        for (var kind : ElectronMissileTargetKind.values()) out.add(new Entry(missileTarget(kind), ElectronMissile.ID));
        out.add(new Entry(SHIELD_MODE, LightShield.ID));
        out.add(new Entry(CP_SAFETY, GenericSkills.BRAIN));
        out.add(new Entry(CP_SAFETY_PERCENT, GenericSkills.BRAIN));
        // 1.5.2: 形態の予約と、プリセットごとのセーフティの並び。
        out.add(new Entry(FORM_RESERVE, GenericSkills.BRAIN));
        for (int i = 0; i < PlayerAbilityData.PRESET_COUNT; i++) out.add(new Entry(safetyOrder(i), GenericSkills.BRAIN));
        out.add(new Entry(FLIGHT_TAKEOFF, DarkMatter.FLIGHT));
        out.add(new Entry(BEETLE_CONTACT_BREAK, DarkMatter.BEETLE));
        out.add(new Entry(BEETLE_REPAIR, DarkMatter.BEETLE));
        out.add(new Entry(BEETLE_STANCE, DarkMatter.BEETLE));
        out.add(new Entry(BEETLE_FOLLOW, DarkMatter.BEETLE));
        for (var kind : ElectronMissileTargetKind.values()) out.add(new Entry(beetleTarget(kind), DarkMatter.BEETLE));
        out.add(new Entry(BEETLE_RECALL, DarkMatter.BEETLE));
        out.add(new Entry(DARK_BODY_RESPAWN, DarkMatter.BODY));
        // 1.5.2: パッシブ／アクティブの切り替え（切り替えられる11の技能）。
        for (var skill : PassiveSkills.SWITCHABLE) out.add(new Entry(passive(skill), skill));
        // 1.5.2: ベクトル反射の反射方向とPvPの候補。
        out.add(new Entry(REFLECTION_DIRECTION, VecReflection.ID));
        out.add(new Entry(REFLECTION_PVP, VecReflection.ID));
        return List.copyOf(out);
    }

    public static boolean known(Identifier id) { return all().stream().anyMatch(e -> e.id().equals(id)); }

    /** 書き込みの結果。CHANGED・UNCHANGEDはコマンドの結果と同じ意味。 */
    public enum Outcome {
        CHANGED, UNCHANGED, NO_DATA, READ_ONLY, UNKNOWN_SETTING, INVALID_VALUE,
        /** 1.5.2: 条件（習得・熟練度100%）を満たさないので、適用しなかった（パッシブへの切り替え）。 */
        REFUSED_REQUIREMENT,
        /** 送り手が死んでいるか観戦者なので、適用しなかった（通知の受け取りが返す）。 */
        REFUSED_STATE,
        /** 1tickの上限を超えたので、適用しなかった（通知の受け取りが返す）。 */
        OVER_BUDGET
    }

    /**
     * 本人の設定を1つ変える。値は、照準とLight Shieldのモードなら{@code auto}・{@code semi}、攻撃対象なら{@code on}・{@code off}。
     * Light Shieldの{@code manual}は、コマンドと同じく{@code semi}の別名として受ける。
     */
    public static Outcome set(ServerPlayer player, Identifier id, String value) {
        if (CP_SAFETY.equals(id) || CP_SAFETY_PERCENT.equals(id) || FORM_RESERVE.equals(id) || safetyOrderPreset(id) >= 0) return safety(player, id, value);
        if (BEETLE_CONTACT_BREAK.equals(id) || BEETLE_REPAIR.equals(id) || BEETLE_STANCE.equals(id) || BEETLE_FOLLOW.equals(id) || BEETLE_RECALL.equals(id)
                || beetleTargetKind(id) != null) return beetle(player, id, value);
        if (DARK_BODY_RESPAWN.equals(id)) return respawn(player, value);
        if (FLIGHT_TAKEOFF.equals(id)) return flightTakeoff(player, value);
        if (REFLECTION_DIRECTION.equals(id) || REFLECTION_PVP.equals(id)) return reflection(player, id, value);
        var passiveSkill = PassiveSkills.skillOfSetting(id);
        if (passiveSkill != null) return passiveMode(player, passiveSkill, value);
        if (MISSILE_AIM.equals(id)) {
            var mode = ElectronMissileAimMode.parse(value);
            return mode == null ? Outcome.INVALID_VALUE : missile(ElectronMissileSettings.setMode(player, mode).outcome());
        }
        if (SHIELD_MODE.equals(id)) {
            var mode = LightShieldMode.parse(value);
            return mode == null ? Outcome.INVALID_VALUE : shield(LightShieldSettings.set(player, mode).outcome());
        }
        for (var kind : ElectronMissileTargetKind.values()) {
            if (!missileTarget(kind).equals(id)) continue;
            Boolean on = ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
            return on == null ? Outcome.INVALID_VALUE : missile(ElectronMissileSettings.setTarget(player, kind, on).outcome());
        }
        return Outcome.UNKNOWN_SETTING;
    }

    /**
     * 本人の今の値（名前の順は{@link #all()}）。データが読めないか読み取り専用ならnull（画面は値を出さない）。
     * 値は、選んだことが無ければ既定の値（コマンドの表示と同じ）。
     */
    public static @Nullable Map<Identifier, String> values(ServerPlayer player) {
        var missile = ElectronMissileSettings.get(player);
        var shield = LightShieldSettings.get(player);
        if (missile.outcome() != ElectronMissileSettings.Outcome.UNCHANGED || shield.outcome() != LightShieldSettings.Outcome.UNCHANGED) return null;
        var out = new java.util.LinkedHashMap<Identifier, String>();
        out.put(MISSILE_AIM, missile.mode().id());
        for (var kind : ElectronMissileTargetKind.values()) out.put(missileTarget(kind), missile.targets().get(kind) ? ON : OFF);
        out.put(SHIELD_MODE, shield.mode().command());
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null) return null;
        out.put(CP_SAFETY, data.isCpSafety() ? ON : OFF);
        out.put(CP_SAFETY_PERCENT, Integer.toString(data.getCpSafetyPercent()));
        out.put(FORM_RESERVE, Integer.toString(data.getFormReservePercent()));
        for (int i = 0; i < PlayerAbilityData.PRESET_COUNT; i++) {
            String code = data.getSafetyOrder(i);
            out.put(safetyOrder(i), code.isEmpty() ? ORDER_DEFAULT : code);
        }
        out.put(FLIGHT_TAKEOFF, data.isFlightDoubleTap() ? TAKEOFF_DOUBLE : TAKEOFF_HOLD);
        out.put(BEETLE_CONTACT_BREAK, data.isBeetleContactBreak() ? ON : OFF);
        out.put(BEETLE_REPAIR, data.getBeetleRepair().id());
        out.put(BEETLE_STANCE, data.getBeetleStance().id());
        out.put(BEETLE_FOLLOW, Integer.toString(data.getBeetleFollow()));
        for (var kind : ElectronMissileTargetKind.values()) out.put(beetleTarget(kind), data.beetleTargets(kind) ? ON : OFF);
        out.put(BEETLE_RECALL, RECALL);
        out.put(DARK_BODY_RESPAWN, data.isDarkBodyRespawn() ? RESPAWN_BODY : RESPAWN_HUMAN);
        for (var skill : PassiveSkills.SWITCHABLE) out.put(passive(skill), data.isPassiveSkill(skill) ? PassiveSkills.PASSIVE : PassiveSkills.ACTIVE);
        out.put(REFLECTION_DIRECTION, data.getReflectionDirection().id());
        out.put(REFLECTION_PVP, data.isReflectionPvp() ? ON : OFF);
        return out;
    }

    /**
     * 甲虫の設定。能力OFF・過負荷・干渉の間も変えられる。値は所有者のデータにあり、甲虫は毎tickそれを読むので、召喚済みの全個体へ
     * 追加の費用なしで即時に効く（クールダウンは戻さない）。回収はCPを返さない。
     */
    private static Outcome beetle(ServerPlayer player, Identifier id, String value) {
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null) return Outcome.NO_DATA;
        if (data.isReadOnly()) return Outcome.READ_ONLY;
        if (BEETLE_CONTACT_BREAK.equals(id)) {
            Boolean on = ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
            if (on == null) return Outcome.INVALID_VALUE;
            if (on == data.isBeetleContactBreak()) return Outcome.UNCHANGED;
            data.setBeetleContactBreak(on);
            return Outcome.CHANGED;
        }
        if (BEETLE_REPAIR.equals(id)) {
            var repair = DarkMatterBeetles.Repair.parse(value);
            if (repair == null) return Outcome.INVALID_VALUE;
            if (repair == data.getBeetleRepair()) return Outcome.UNCHANGED;
            data.setBeetleRepair(repair);
            return Outcome.CHANGED;
        }
        if (BEETLE_STANCE.equals(id)) {
            var stance = DarkMatterBeetles.Stance.parse(value);
            if (stance == null) return Outcome.INVALID_VALUE;
            if (stance == data.getBeetleStance()) return Outcome.UNCHANGED;
            data.setBeetleStance(stance);
            return Outcome.CHANGED;
        }
        if (BEETLE_FOLLOW.equals(id)) {
            // 整数の10進の表記だけを受ける（"+32"や"032"のような別の書き方は断る）。
            int blocks;
            try { blocks = Integer.parseInt(value); } catch (NumberFormatException e) { return Outcome.INVALID_VALUE; }
            if (!DarkMatterBeetles.validFollow(blocks) || !Integer.toString(blocks).equals(value)) return Outcome.INVALID_VALUE;
            if (blocks == data.getBeetleFollow()) return Outcome.UNCHANGED;
            data.setBeetleFollow(blocks);
            return Outcome.CHANGED;
        }
        var target = beetleTargetKind(id);
        if (target != null) {
            Boolean on = ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
            if (on == null) return Outcome.INVALID_VALUE;
            if (on == data.beetleTargets(target)) return Outcome.UNCHANGED;
            data.setBeetleTarget(target, on);
            return Outcome.CHANGED;
        }
        if (!RECALL.equals(value)) return Outcome.INVALID_VALUE;
        return DarkMatterBeetles.recallAll(player) ? Outcome.CHANGED : Outcome.UNCHANGED;
    }
    /** 白翼飛行の飛び立ちと停止の操作。クライアントが同期された値を読んで、入力の見方を変える（サーバーは停止と飛び立ちの依頼を受けるだけ）。 */
    private static Outcome flightTakeoff(ServerPlayer player, String value) {
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null) return Outcome.NO_DATA;
        if (data.isReadOnly()) return Outcome.READ_ONLY;
        Boolean doubleTap = TAKEOFF_DOUBLE.equals(value) ? Boolean.TRUE : TAKEOFF_HOLD.equals(value) ? Boolean.FALSE : null;
        if (doubleTap == null) return Outcome.INVALID_VALUE;
        if (doubleTap == data.isFlightDoubleTap()) return Outcome.UNCHANGED;
        data.setFlightDoubleTap(doubleTap);
        return Outcome.CHANGED;
    }
    /**
     * 1.5.2: ベクトル反射の反射方向とPvPの候補。値は本人のデータにあり、反射が成立した瞬間にサーバーが読むので、
     * 動作中の反射にも次の反射から効く（モードを止めない）。
     */
    private static Outcome reflection(ServerPlayer player, Identifier id, String value) {
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null) return Outcome.NO_DATA;
        if (data.isReadOnly()) return Outcome.READ_ONLY;
        if (REFLECTION_DIRECTION.equals(id)) {
            var direction = ReflectionDirection.parse(value);
            if (direction == null) return Outcome.INVALID_VALUE;
            if (direction == data.getReflectionDirection()) return Outcome.UNCHANGED;
            data.setReflectionDirection(direction);
            return Outcome.CHANGED;
        }
        Boolean on = ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
        if (on == null) return Outcome.INVALID_VALUE;
        if (on == data.isReflectionPvp()) return Outcome.UNCHANGED;
        data.setReflectionPvp(on);
        return Outcome.CHANGED;
    }
    /** 1.5.2: パッシブ／アクティブの切り替え。検証と適用は{@link PassiveSkills#set}。 */
    private static Outcome passiveMode(ServerPlayer player, Identifier skill, String value) {
        Boolean passive = PassiveSkills.PASSIVE.equals(value) ? Boolean.TRUE : PassiveSkills.ACTIVE.equals(value) ? Boolean.FALSE : null;
        if (passive == null) return Outcome.INVALID_VALUE;
        return switch (PassiveSkills.set(player, skill, passive)) {
            case CHANGED -> Outcome.CHANGED;
            case UNCHANGED -> Outcome.UNCHANGED;
            case REFUSED -> Outcome.REFUSED_REQUIREMENT;
            case NO_DATA -> Outcome.NO_DATA;
            case READ_ONLY -> Outcome.READ_ONLY;
        };
    }
    /** 死亡後の復活形態。系統を失っても保持する。 */
    private static Outcome respawn(ServerPlayer player, String value) {
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null) return Outcome.NO_DATA;
        if (data.isReadOnly()) return Outcome.READ_ONLY;
        Boolean body = RESPAWN_BODY.equals(value) ? Boolean.TRUE : RESPAWN_HUMAN.equals(value) ? Boolean.FALSE : null;
        if (body == null) return Outcome.INVALID_VALUE;
        if (body == data.isDarkBodyRespawn()) return Outcome.UNCHANGED;
        data.setDarkBodyRespawn(body);
        return Outcome.CHANGED;
    }
    private static Outcome safety(ServerPlayer player, Identifier id, String value) {
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null) return Outcome.NO_DATA;
        if (data.isReadOnly()) return Outcome.READ_ONLY;
        if (CP_SAFETY.equals(id)) {
            Boolean on = ON.equals(value) ? Boolean.TRUE : OFF.equals(value) ? Boolean.FALSE : null;
            if (on == null) return Outcome.INVALID_VALUE;
            if (on == data.isCpSafety()) return Outcome.UNCHANGED;
            data.setCpSafety(on);
            return Outcome.CHANGED;
        }
        int preset = safetyOrderPreset(id);
        if (preset >= 0) {
            // 1.5.2: 並び。"-" は既定の並び。知らない文字・重ねて書いた文字は断る。
            String code = ORDER_DEFAULT.equals(value) ? "" : value;
            if (!SafetyPriority.valid(code)) return Outcome.INVALID_VALUE;
            if (code.equals(data.getSafetyOrder(preset))) return Outcome.UNCHANGED;
            data.setSafetyOrder(preset, code);
            return Outcome.CHANGED;
        }
        int percent;
        try { percent = Integer.parseInt(value); } catch (NumberFormatException e) { return Outcome.INVALID_VALUE; }
        if (!Integer.toString(percent).equals(value)) return Outcome.INVALID_VALUE;
        if (FORM_RESERVE.equals(id)) {
            // 1.5.2: 形態の予約は 0〜30% の整数（1%刻み）。値は未元物質人間でなくても保存する（効くのは未元物質人間だけ）。
            if (percent < PlayerAbilityData.FORM_RESERVE_MIN || percent > PlayerAbilityData.FORM_RESERVE_MAX) return Outcome.INVALID_VALUE;
            if (percent == data.getFormReservePercent()) return Outcome.UNCHANGED;
            data.setFormReservePercent(percent);
            return Outcome.CHANGED;
        }
        if (percent < PlayerAbilityData.CP_SAFETY_MIN_PERCENT || percent > PlayerAbilityData.CP_SAFETY_MAX_PERCENT) return Outcome.INVALID_VALUE;
        if (percent == data.getCpSafetyPercent()) return Outcome.UNCHANGED;
        data.setCpSafetyPercent(percent);
        return Outcome.CHANGED;
    }
    private static Outcome missile(ElectronMissileSettings.Outcome outcome) {
        return switch (outcome) {
            case CHANGED -> Outcome.CHANGED;
            case UNCHANGED -> Outcome.UNCHANGED;
            case NO_DATA -> Outcome.NO_DATA;
            case READ_ONLY -> Outcome.READ_ONLY;
        };
    }
    private static Outcome shield(LightShieldSettings.Outcome outcome) {
        return switch (outcome) {
            case CHANGED -> Outcome.CHANGED;
            case UNCHANGED -> Outcome.UNCHANGED;
            case NO_DATA -> Outcome.NO_DATA;
            case READ_ONLY -> Outcome.READ_ONLY;
        };
    }
}
