package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * プレイヤーの能力の状態の参照（公開API、実験的。公開APIバージョン3、1.5.2で追加。サーバーのスレッドでだけ呼ぶ）。
 * どのメソッドも状態を変えない。
 * <p>
 * {@code AcademyApi.VERSION}は定数でアドオンに埋め込まれるため、古い版のRe:AcademyCraftにも対応するアドオンは、このクラスの呼び出しで
 * 起きる{@link LinkageError}を捕まえて、対応していない場合の処理を用意すること。
 */
public final class AbilityStatus {
    private AbilityStatus() { }

    /**
     * そのプレイヤーの、その技能の今の発動方式。
     * <ul>
     * <li>本体の切り替えられる技能（風の翼・ベクトル偏向・ベクトル反射・ジェット・エンジン・ライト・シールド・白翼自動防御・身体強化・エレクトロン・ミサイル・フラッシング・未元光線・天界発現）を、
     *     プレイヤーが能力設定でパッシブ（能力使用モードで自動で起動）に切り替えていれば{@link ActivationKind#PASSIVE}。</li>
     * <li>それ以外は、登録の既定の発動方式（{@code SkillDefinition#kind()}）。登録の既定はプレイヤーごとに変わらない。</li>
     * <li>登録されていない技能ならnull。プレイヤーがその技能を習得しているかは見ない。</li>
     * </ul>
     */
    public static @Nullable ActivationKind activationKind(ServerPlayer player, AbilityId skill) {
        var location = io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(skill);
        var kind = io.github.pinchan4273.reacademycraft.ability.RegistryViews.kind(location);
        if (kind == null) return null;
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        return data != null && !data.isReadOnly() && data.isPassiveSkill(location) ? ActivationKind.PASSIVE : kind;
    }

    /**
     * そのプレイヤーの、そのダメージに対するベクトル操作の解析度（0〜1。読み取り専用）。ベクトル偏向・ベクトル反射の守る割合に掛かる値で、
     * 1が解析しきった状態。バニラの物理のダメージ（飛び道具・爆発・火・落下など）はいつも1。ベクトル操作の系統でないプレイヤーにも、
     * 保存されている値（系統を失っても残る）を返す。解析度を変える入口は公開しない。
     */
    public static float analysis(ServerPlayer player, DamageSource source) {
        return io.github.pinchan4273.reacademycraft.skill.VectorAnalysis.analysisOf(player, source);
    }
}
