package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.ability.AddonSessions;
import java.util.function.Consumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * v1.5.0 §15: 死亡の最終判断と、Reの死亡時の後始末（2026-11-03のユーザーの決定「通常人間ではトーテムと他MODの死亡回避を人体生成より優先する」）。
 * <p>
 * <b>順序の作り方。</b> Forgeのイベントは、同じ優先度の中では登録した順に呼ばれる。{@link #register}を全MODの読み込みの完了（{@code FMLLoadCompleteEvent}）の後に呼び、
 * {@link LivingDeathEvent}の最低優先度の最後へ1つだけ登録する。注釈（{@code @SubscribeEvent}）や構築・初期化の間に登録した他MODのリスナーは、最低優先度のものも
 * すべてこれより先に動くので、外部の救済（取消）が成立したかどうかを見てから判断できる。
 * <ol>
 * <li>{@link DarkBody#decide}: 他MODの救済が成立すれば通常の人間のまま生存し、人体生成もCP全回復も発生しない。致死が成立し、発動条件を満たした場合は未元物質人間に変化する。
 * 未元物質人間のCPが0のときと管理用の強制死亡は、取消を打ち消して真の死亡にする。</li>
 * <li>死亡時の後処理（直接効果・アドオンのセッション・甲虫・天界発現）は、死亡が確定した場合だけ行う。</li>
 * </ol>
 * 外部の救済が成立したときは、このリスナーは取り消されたイベントを受けて何もしないので、人体生成が二重に発動することは無い。
 * <p>
 * <b>保証の外（個別の互換が要る範囲）。</b> (1) ゲームの実行中に後から登録されたリスナー（これより後に動く）。(2) 取り消されたイベントも受け取り
 * （{@code receiveCanceled}）、取消を確かめずに自分でも救済するリスナー。(3) イベントの外（mixin、{@code die}の上書き、HPの直接操作、死亡画面の後の復活）で死亡を
 * 取り消すMOD。(4) トーテム以外の死亡回避を{@code LivingUseTotemEvent}など別の入口で行うMOD（その入口の結果しだい）。
 */
public final class DeathOrder {
    private static final Consumer<LivingDeathEvent> LISTENER = DeathOrder::decide;
    private static boolean registered;

    private DeathOrder() { }

    /** 最低優先度の最後へ登録する（1度だけ）。全MODの読み込みの完了の後、主スレッドで呼ぶ。 */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDeathEvent.class, LISTENER);
    }
    /** 試験用の入口: 登録し直して最後へ回す（試験の中で後から登録した「他MODの救済」より後にする）。 */
    public static synchronized void moveLastForTest() {
        MinecraftForge.EVENT_BUS.unregister(LISTENER);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDeathEvent.class, LISTENER);
        registered = true;
    }

    private static void decide(LivingDeathEvent event) {
        DarkBody.decide(event);
        if (event.isCanceled()) return;
        DirectEffects.death(event);
        AddonSessions.addonSessionsOnDeath(event);
        DarkMatterBeetles.death(event);
        HeavenManifest.death(event);
    }
}
