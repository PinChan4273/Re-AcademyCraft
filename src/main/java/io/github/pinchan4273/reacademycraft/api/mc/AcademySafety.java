package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.TargetRelation;
import io.github.pinchan4273.reacademycraft.ability.SafetyService;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 共通の安全のサービス（公開API、実験的。1.4.0のS5。サーバーのスレッドでだけ呼ぶ）。外部の技能の振る舞いは、攻撃・地形の変更・
 * 空間移動をここを通して行う。<b>ここを通した処理だけが判定される。</b>アドオンが{@code Entity.hurt}や{@code Level.setBlock}を直接
 * 呼べば、Re:AcademyCraftには止められず、そのような呼び出しを見つける手段も保証しない。
 * <p>
 * 帰属: 主体の帰属先のプレイヤー（{@link AbilityActor#attributedPlayer}）は、今このサーバーにログインしている本物のプレイヤー
 * （FakePlayerでない、観戦者でない）のときだけ確かめられたとする（{@link #verifiedPlayer}）。
 * <ul>
 * <li>プレイヤーへの攻撃: 確かめられた帰属先が要る。その人のPvP・チーム・サーバーの設定（攻撃の許可・PvP）をすべて当てる。
 * 帰属先が無い・確かめられなければ断る。帰属先本人と主体の実体は攻撃しない。</li>
 * <li>地形の変更（壊す・置く・置き換える・爆発）: 確かめられた帰属先が要り、その人として保護の領域・建築の権限・Forgeの破壊と設置の
 * イベントを確かめる。技能の地形の設定（壊してよい次元など）も当てる。機械とAIの主体は、ゲームルールmobGriefingも要る。
 * 帰属先が無ければ断る（FakePlayerでの迂回はしない）。</li>
 * <li>空間移動: 行き先が読み込まれ、世界の境界の中で、Forgeのテレポートのイベントが許すこと（事前）。主体の実体でない相手を動かす
 * のは攻撃と同じ判定。移した後に、実際の位置を確かめる（事後）。</li>
 * </ul>
 */
public final class AcademySafety {
    private AcademySafety() { }

    /** 確かめられた帰属先のプレイヤー（無ければnull）。 */
    public static @Nullable ServerPlayer verifiedPlayer(AbilityActor actor) { return SafetyService.verifiedPlayer(actor); }
    /** 主体から見た相手の関係。 */
    public static TargetRelation relation(AbilityActor actor, Entity target) { return SafetyService.relation(actor, target); }
    /** その相手を、その技能で攻撃してよいか（ダメージは与えない）。 */
    public static boolean mayAttack(AbilityActor actor, Entity target, AbilityId skill) { return SafetyService.mayAttack(actor, target, skill); }
    /**
     * 攻撃する（許されなければ何もせずfalse）。ダメージには、全体と技能の設定の倍率を掛ける。ダメージ源は本体の技能のダメージの型で、
     * 直接の加害は主体の実体、帰属は確かめられた帰属先（無ければ主体の実体）。
     */
    public static boolean attack(AbilityActor actor, Entity target, AbilityId skill, float damage) { return SafetyService.attack(actor, target, skill, damage); }
    /** その位置の地形を、その技能で変えてよいか（変えない）。 */
    public static boolean mayModifyBlock(AbilityActor actor, BlockPos pos, AbilityId skill) { return SafetyService.mayModifyBlock(actor, pos, skill); }
    /** ブロックを壊す（Forgeの破壊のイベントを通す）。dropsなら落とし物を出す。 */
    public static boolean breakBlock(AbilityActor actor, BlockPos pos, AbilityId skill, boolean drops) { return SafetyService.breakBlock(actor, pos, skill, drops); }
    /** 置き換えられる場所（空気・草など）へブロックを置く（Forgeの設置のイベントを通す）。 */
    public static boolean placeBlock(AbilityActor actor, BlockPos pos, BlockState state, AbilityId skill) { return SafetyService.placeBlock(actor, pos, state, skill); }
    /** ブロックを別のブロックへ置き換える（破壊と設置の両方のイベントを通す。落とし物は出さない）。 */
    public static boolean replaceBlock(AbilityActor actor, BlockPos pos, BlockState state, AbilityId skill) { return SafetyService.replaceBlock(actor, pos, state, skill); }
    /**
     * 爆発させる。壊すブロックは1つずつ{@link #mayModifyBlock}で確かめ、傷つける相手は1つずつ{@link #mayAttack}で確かめる
     * （許されないものは爆発から外す）。Forgeの爆発の開始のイベントが取り消せば、何もせずfalse。
     */
    public static boolean explode(AbilityActor actor, Vec3 at, float power, AbilityId skill) { return SafetyService.explode(actor, at, power, skill); }
    /** 相手をそこへ移してよいか（事前）。使う行き先を返す（許されなければnull。Forgeのイベントが行き先を変えることがある）。 */
    public static @Nullable Vec3 checkTeleport(AbilityActor actor, Entity mover, Vec3 to, AbilityId skill) { return SafetyService.checkTeleport(actor, mover, to, skill); }
    /** 相手を同じ世界の中で移す（事前の判定、移動、事後の確かめ）。実際にその位置へ移ったらtrue。 */
    public static boolean teleport(AbilityActor actor, Entity mover, Vec3 to, AbilityId skill) { return SafetyService.teleport(actor, mover, to, skill); }
    /** 主体が今、能力の妨害の範囲の中か。 */
    public static boolean interfered(AbilityActor actor) { return SafetyService.interfered(actor); }
}
