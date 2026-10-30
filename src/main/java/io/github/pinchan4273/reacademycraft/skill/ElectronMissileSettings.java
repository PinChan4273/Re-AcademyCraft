package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * Electron Missileの照準のモードと攻撃対象のフィルターを読む・変える、サーバーの唯一の入口（F9-02）。コマンド（{@link MissileCommands}）は
 * ここを呼び、将来のGUIも同じ入口を使う。設定の正本はサーバーの能力データ（保存はSkillSettings区分）で、呼び出した本人のデータだけを変える。
 * <p>
 * モードがONの間に変えても、セッションは続いたまま次の発射のtickから新しい設定で相手を選ぶ（起動のやり直し・費用・クールダウンは無い）。
 */
public final class ElectronMissileSettings {
    private ElectronMissileSettings() { }

    /** 変えた結果。メッセージの選び方に使う。 */
    public enum Outcome {
        /** 変えた。 */
        CHANGED,
        /** 既にその値だった（何も変えていない）。 */
        UNCHANGED,
        /** 能力データが無い（読めない）。 */
        NO_DATA,
        /** 保存データが読み取り専用（新しすぎる版など）。元のNBTを守るため変えない。 */
        READ_ONLY
    }

    /**
     * 結果と、そのときの状態。chosenは照準のモードを選んだことがあるか。targetsは4分類の今のON／OFF。learnedは「Electron Missileを習得していて、
     * 能力のカテゴリが合う」（そうでなくても設定は保存し、習得した後にその設定で動く）。activeはモードがONで、新しい設定が次の発射から効くこと。
     */
    public record Result(Outcome outcome, ElectronMissileAimMode mode, boolean chosen, Map<ElectronMissileTargetKind, Boolean> targets,
                         boolean learned, boolean active) { }

    private static @Nullable PlayerAbilityData data(ServerPlayer player) {
        return AbilityCapabilities.of(player).orElse(null);
    }
    private static boolean usable(PlayerAbilityData data) {
        return !data.isReadOnly() && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ElectronMissile.ID);
    }
    private static Map<ElectronMissileTargetKind, Boolean> targets(@Nullable PlayerAbilityData data) {
        var out = new EnumMap<ElectronMissileTargetKind, Boolean>(ElectronMissileTargetKind.class);
        for (var kind : ElectronMissileTargetKind.values()) out.put(kind, data == null ? kind.defaultOn() : data.electronMissileTargets(kind));
        return out;
    }
    private static Result refused(Outcome outcome) {
        return new Result(outcome, ElectronMissileAimMode.DEFAULT, false, targets(null), false, false);
    }
    private static Result state(ServerPlayer player, PlayerAbilityData data, Outcome outcome) {
        return new Result(outcome, data.getElectronMissileAimMode(), data.hasChosenElectronMissileAimMode(), targets(data), usable(data),
                ElectronMissile.holding(player));
    }

    /** 今の設定（変えない）。 */
    public static Result get(ServerPlayer player) {
        var data = data(player);
        if (data == null) return refused(Outcome.NO_DATA);
        if (data.isReadOnly()) return refused(Outcome.READ_ONLY);
        return state(player, data, Outcome.UNCHANGED);
    }

    /** 本人の照準のモードを変える。 */
    public static Result setMode(ServerPlayer player, ElectronMissileAimMode mode) {
        var data = data(player);
        if (data == null) return refused(Outcome.NO_DATA);
        if (data.isReadOnly()) return refused(Outcome.READ_ONLY);
        boolean same = data.hasChosenElectronMissileAimMode() && data.getElectronMissileAimMode() == mode;
        if (!same) data.setElectronMissileAimMode(mode);
        return state(player, data, same ? Outcome.UNCHANGED : Outcome.CHANGED);
    }

    /** 本人の攻撃対象のフィルターの1つを変える。 */
    public static Result setTarget(ServerPlayer player, ElectronMissileTargetKind kind, boolean on) {
        var data = data(player);
        if (data == null) return refused(Outcome.NO_DATA);
        if (data.isReadOnly()) return refused(Outcome.READ_ONLY);
        boolean same = data.hasChosenElectronMissileTarget(kind) && data.electronMissileTargets(kind) == on;
        if (!same) data.setElectronMissileTarget(kind, on);
        return state(player, data, same ? Outcome.UNCHANGED : Outcome.CHANGED);
    }
}
