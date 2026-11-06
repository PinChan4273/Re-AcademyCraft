package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * Light Shieldのモードを読む・変える、サーバーの唯一の入口（工程2）。コマンド（{@link ShieldCommands}）も、将来の能力調整GUIの
 * パケットも、ここを呼ぶ。設定の正本はサーバーの能力データ（{@link PlayerAbilityData#getLightShieldMode()}、保存はSkillSettings区分）で、
 * 呼び出した本人のデータだけを変える。
 * <p>
 * 盾がONの間に変えても、セッションは続いたまま次のtickから新しいモードで標的を選ぶ（起動のやり直し・費用・クールダウンは無い）。
 * 変えたときは、古い標的と追従の状態を捨て、今の盾の状態を送り直す（{@link LightShield#modeChanged}）。
 */
public final class LightShieldSettings {
    private LightShieldSettings() { }

    /** 変えた結果。メッセージの選び方に使う。 */
    public enum Outcome {
        /** 変えた。 */
        CHANGED,
        /** 既にそのモードだった（何も変えていない）。 */
        UNCHANGED,
        /** 能力データが無い（読めない）。 */
        NO_DATA,
        /** 保存データが読み取り専用（新しすぎる版など）。元のNBTを守るため変えない。 */
        READ_ONLY
    }

    /**
     * 結果と、そのときの状態。learnedは「Light Shieldを習得していて、能力のカテゴリが合う」（そうでなくても設定は保存し、
     * 習得した後にそのモードで動く）。activeは盾がONで、新しいモードがすぐに効くこと。
     */
    public record Result(Outcome outcome, LightShieldMode mode, boolean chosen, boolean learned, boolean active) { }

    private static @Nullable PlayerAbilityData data(ServerPlayer player) {
        return AbilityCapabilities.of(player).orElse(null);
    }
    private static boolean usable(PlayerAbilityData data) {
        return !data.isReadOnly() && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(LightShield.ID);
    }

    /** 今のモード（変えない）。 */
    public static Result get(ServerPlayer player) {
        var data = data(player);
        if (data == null) return new Result(Outcome.NO_DATA, LightShieldMode.DEFAULT, false, false, false);
        if (data.isReadOnly()) return new Result(Outcome.READ_ONLY, LightShieldMode.DEFAULT, false, false, false);
        return new Result(Outcome.UNCHANGED, data.getLightShieldMode(), data.hasChosenLightShieldMode(), usable(data), LightShield.holding(player));
    }

    /** 本人のモードを変える。 */
    public static Result set(ServerPlayer player, LightShieldMode mode) {
        var data = data(player);
        if (data == null) return new Result(Outcome.NO_DATA, mode, false, false, false);
        if (data.isReadOnly()) return new Result(Outcome.READ_ONLY, mode, false, false, false);
        boolean same = data.hasChosenLightShieldMode() && data.getLightShieldMode() == mode;
        if (!same) {
            data.setLightShieldMode(mode);
            LightShield.modeChanged(player);
        }
        return new Result(same ? Outcome.UNCHANGED : Outcome.CHANGED, mode, true, usable(data), LightShield.holding(player));
    }
}
