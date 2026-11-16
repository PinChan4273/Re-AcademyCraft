package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingAttackEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 未元物質Lv5 未元物質・人体生成。パッシブ技能で、形態そのものは{@link PlayerAbilityData#isDarkBody()}に保存する。
 * <p>
 * <b>発動の順。</b> 致死はバニラの順にトーテム、他MODの救済と進み、それでも{@link LivingDeathEvent}が
 * 取り消されなかったときだけ、{@link #decide}が取り消して未元物質人間へ変える。判断は{@link DeathOrder}が全MODの読み込みの後に最低優先度の最後へ登録した
 * リスナーで行うので、最低優先度で救済する他MODより後になる。トーテムが働くとこのイベントは起きず、他MODが救済（取消）すると変化しない。
 * 変化の前のCPは条件にしない（0でも発動する）。1.5.2から、管理用の強制死亡（{@code generic_kill}、{@code /kill}）でも変化する。
 * <p>
 * <b>生命力。</b> 通常の防御の後の残損傷（{@link AcademyLivingDamageEvent}の最後）を1HP＝10CP（1.5.2。1.5.1 までは1HP＝1CP）でCPへ一度だけ反映し、HPは減らさない。
 * 形態の維持として毎tick G0 × 30% を払い（{@link PlayerAbilityData#formDue}。回復待ちの間も）、未修復の損傷は継続の収支が0以上の tick だけ、
 * 毎秒10CP（＝1HP）まで修復する。回復待ち・最大CPの成長・
 * 経験を作らない。CPが0になると残りの損傷をHPへ通し、バニラの死亡の流れで真の死亡にする。このときトーテムは使わせず（{@link LivingUseTotemEvent}）、
 * 他MODの死亡の取消も打ち消す（{@link #trueDeath}）。技能の費用でCPが0になったときは、次の自然回復より先に{@link #tick}が死亡させる
 * （{@link PlayerAbilityData#tick}はCPが0の未元物質人間を回復させない）。
 * <p>
 * <b>免疫。</b> 火炎系（{@code is_fire}）・溺水・飢餓は攻撃の時点で無効（CPも減らさず、防御の費用も生まない）。1.5.2からは、
 * 攻撃した者・飛び道具のいない損傷（原因のエンティティも直接のエンティティも無い損傷。凍結・落雷・他MODの気候の損傷など）も無効にする。ただし体に物理的に当たる損傷
 * （{@link #physicalImpact}: 落下・壁への衝突・サボテン・スイートベリーの茂み・つらら・落ちてくるブロック・壁の中の窒息・押し合い）と、
 * 甲虫の耐性と同じ除外（{@link io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle#environmental}: 無敵を貫く損傷・爆発・魔法・
 * 間接の魔法・衰弱）は今のまま受ける。判定はダメージの種類・タグとエンティティの有無だけで、MODの名前やIDは見ない。有害に分類された状態効果と、
 * タグ{@code academy:dark_body_harmful}で個別に指定した効果は付かず、付いても次のtickで消す。Reの直接弱体化は{@link DirectEffects}が断る。
 * 領域は{@link #immuneToDebuffs}を見て弱体化を掛けない。HPの回復をCPへ変換しないので、HPは満タンのまま保つだけで、回復の費用も生まない。
 * <p>
 * <b>系統の喪失。</b> {@link PlayerAbilityData#setAbility}が喪失の直前の残量率を記録し、{@link #category}がそれを最大HPに掛ける（最低1HP）。
 * 発動中の技能の停止は{@code SkillSessions}、甲虫の回収は{@code DarkMatterBeetles}、翼の収納は{@code DarkWings}が同じイベントと次のtickで行う。
 * <p>
 * 限界: 他MODが{@code setHealth}で直接HPを0にしたり、死亡の処理を独自に呼んだりする場合は止めない。順序の保証の外の救済は{@link DeathOrder}の説明のとおり。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class DarkBody {
    public static final ResourceLocation ID = DarkMatter.BODY;
    /** 分類は有害でないが有害と確認できた他MODの効果を、データパックで足すためのタグ。 */
    public static final TagKey<MobEffect> EXTRA_HARMFUL = TagKey.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath("academy", "dark_body_harmful"));

    private DarkBody() { }

    private static @Nullable PlayerAbilityData data(LivingEntity entity) {
        return entity instanceof ServerPlayer player ? io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null) : null;
    }
    /** 未元物質人間か（サーバーのプレイヤーだけ）。 */
    public static boolean isBody(LivingEntity entity) {
        var data = data(entity);
        return data != null && !data.isReadOnly() && data.isDarkBody();
    }
    /** 有害な効果と領域の直接弱体化を受けないか。領域の弱体化・状態異常はこれを見て掛けない。 */
    public static boolean immuneToDebuffs(LivingEntity entity) { return isBody(entity); }
    /**
     * 管理用の強制死亡か（{@code /kill}）。すでに未元物質人間の人の、他MODに取り消された死亡では、CPが残っていても真の死亡にする（{@link #decide}）。
     * 1.5.2から、人体生成の除外と、CPで受ける精算の除外には使わない（すべての死に人体生成が働き、強制死の損傷もCPから引く）。
     */
    public static boolean adminKill(DamageSource source) { return source.is(DamageTypes.GENERIC_KILL); }
    /**
     * 形態が無効にする損傷か（費用もCPの減少も生まない）。火炎系・溺水・飢餓に加えて、原因のエンティティの無い環境の損傷（甲虫の
     * {@link io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle#environmental}と同じ判定）のうち、体に物理的に当たる物を除いた物。
     */
    public static boolean immuneDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.STARVE)) return true;
        // 世界の境界の外の損傷は、奈落と同じく世界の決まりなので受ける。
        return io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle.environmental(source) && !physicalImpact(source)
                && !source.is(DamageTypes.OUTSIDE_BORDER);
    }
    /**
     * 体に物理的に当たる損傷か（原因のエンティティが無くても受ける）。落下（{@code is_fall}のタグを含む）・壁への衝突（白翼飛行の高速飛行の衝突を含む）・
     * サボテン・スイートベリーの茂み・つらら・落ちてくるブロック（金床・落ちるつららと、タグ{@code damages_helmet}）・壁の中の窒息・押し合い。
     */
    public static boolean physicalImpact(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.DAMAGES_HELMET)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_BLOCK)
                || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING);
    }
    /** 形態が受け付けない効果か。中立・分類不明はタグで指定しない限り通す。 */
    public static boolean harmfulEffect(MobEffect effect) {
        return effect.getCategory() == MobEffectCategory.HARMFUL || BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect).is(EXTRA_HARMFUL);
    }
    /** この致死で変化できるか。 */
    public static boolean canTransform(ServerPlayer player, DamageSource source) {
        var data = data(player);
        // 1.5.2: 管理用の強制死（/kill）・無敵を貫く物（奈落など）も含め、すべての死に働く。
        return data != null && !data.isReadOnly() && !data.isDarkBody() && DarkMatter.CATEGORY.equals(data.getAbility()) && data.hasLearned(ID);
    }
    /** 未元物質人間へ変える。致死の損傷は全体を取り消し、CPは全回復して回復待ちを解除し、変化の前からの有害な効果を外す。 */
    public static void transform(ServerPlayer player) {
        var data = data(player);
        if (data == null) return;
        data.becomeDarkBody();
        player.setHealth(player.getMaxHealth());
        player.fallDistance = 0;
        removeHarmful(player);
        DirectEffects.clearHarmful(player);
        sustain(player);
        // 変化の瞬間は、体と装備の白いシルエットを1回だけ出す。その後は頭上の輪が残る。
        DarkWingAction.send(player, DarkWingAction.BODY_FLASH, 1, 0);
        MARKED.add(player.getUUID());
        player.level().playSound(null, player, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .8f, 1.4f);
        AbilitySyncEvents.sync(player, true);
    }
    private static void removeHarmful(ServerPlayer player) {
        if (player.getActiveEffects().isEmpty()) return;
        for (var instance : new ArrayList<>(player.getActiveEffects()))
            if (harmfulEffect(instance.getEffect().value())) player.removeEffect(instance.getEffect());
    }
    /** 身体の維持: 呼吸・食事を要らなくし（食料による移動の制限も無くなる）、内部のHPを満タンに保つ。HPは生命力ではなく、回復もCPへ変換しない。 */
    private static void sustain(ServerPlayer player) {
        player.setAirSupply(player.getMaxAirSupply());
        var food = player.getFoodData();
        food.setFoodLevel(20); food.setSaturation(5); food.setExhaustion(0);
        if (player.getHealth() > 0 && player.getHealth() < player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        if (player.isOnFire()) player.clearFire();
    }
    /**
     * 残損傷をCPへ反映する（テストからも呼ぶ）。戻り値はHPへ通す損傷: 普段は0で、CPが0に届いたら確実に致死になる量。管理用の強制死亡と通常人間はそのまま返す。
     */
    public static float settle(ServerPlayer player, DamageSource source, float amount) {
        var data = data(player);
        // 1.5.2: 管理用の強制死の損傷も、ほかの損傷と同じくCPから引く（CPが0になれば致死の量を通す）。
        if (data == null || data.isReadOnly() || !data.isDarkBody()) return amount;
        if (!(amount > 0)) return 0;
        // 身体に通った分だけ（防御で防いだ分とその費用は含まない）を、1HP＝10CPで未修復の身体損傷として記録する（収支が0以上の tick に毎秒10CPまで修復）。
        data.takeBodyDamage(Float.isFinite(amount) ? amount : data.getCp());
        AbilitySyncEvents.sync(player, false);
        return data.getCp() <= 0 ? player.getHealth() + player.getMaxHealth() : 0;
    }

    /** 頭上の輪を出している（見ている人へ形態を送り済みの）プレイヤー。 */
    private static final java.util.Set<java.util.UUID> MARKED = new java.util.HashSet<>();
    /** 形態が変わったら、本人と見ている人へ送る（ログイン・ディメンション移動・復活では送り直す。白いシルエットは出さない）。 */
    private static void mark(ServerPlayer player) {
        boolean want = isBody(player) && player.isAlive();
        if (want == MARKED.contains(player.getUUID())) return;
        if (want) MARKED.add(player.getUUID()); else MARKED.remove(player.getUUID());
        DarkWingAction.send(player, DarkWingAction.BODY, want ? 1 : 0, 0);
    }
    /** テスト用の入口: 頭上の輪の合図を送り済みか。 */
    public static boolean markedForTest(ServerPlayer player) { return MARKED.contains(player.getUUID()); }
    @SubscribeEvent public static void startTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer watcher && watcher.connection != null && MARKED.contains(target.getUUID()))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(watcher,
                    new DarkWingAction(target.getId(), DarkWingAction.BODY, 1, 0));
    }
    @SubscribeEvent public static void markLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { MARKED.remove(e.getEntity().getUUID()); }
    // ディメンションの移動と復活ではクライアントのエンティティが作り直されるので、次のtickで送り直す。
    @SubscribeEvent public static void markDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) { MARKED.remove(e.getEntity().getUUID()); }
    /**
     * 復活では送り直す。輪を出していたのに普通の人間として戻ったとき（すぐのリスポーンで、死亡の後のtickが動かなかった場合）は、輪を消す合図を送る
     * （1.5.2: クライアントはエンティティIDで輪を持ち、同じディメンションでの復活では新しいエンティティも同じIDを使うので、合図が無いと輪が残る）。
     * このイベントの時点では、新しいプレイヤーのIDは本当のものになっている。
     */
    @SubscribeEvent public static void markRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
        boolean was = MARKED.remove(e.getEntity().getUUID());
        if (was && e.getEntity() instanceof ServerPlayer player && !(isBody(player) && player.isAlive())) DarkWingAction.send(player, DarkWingAction.BODY, 0, 0);
    }
    @SubscribeEvent public static void markStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) { MARKED.clear(); }

    private static void tick(ServerPlayer player) {
        mark(player);
        var data = data(player);
        if (data == null || data.isReadOnly() || !data.isDarkBody() || !player.isAlive()) return;
        if (data.getCp() <= 0) { die(player, player.damageSources().generic()); return; }
        sustain(player);
        removeHarmful(player);
    }
    private static void die(ServerPlayer player, DamageSource source) {
        player.setHealth(0);
        player.die(source);
    }
    /** テスト用の入口。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) tick(p);
    }
    /**
     * 死亡の最終判断（{@link DeathOrder}が、他MODの救済がすべて済んだ後に呼ぶ）。
     * <ul>
     * <li>取り消された死亡: 未元物質人間のCPが0のときと管理用の強制死亡だけ、取消を打ち消して真の死亡にする。通常人間の救済（トーテムの後の他MODの救済）はそのまま尊重し、
     * 人体生成は発動しない（CPの全回復も無い）。</li>
     * <li>取り消されていない死亡（外部の救済が無かったか、成立しなかった）: 人体生成の条件を満たせば、直前のCPが0でも取り消して変化させる。</li>
     * </ul>
     */
    static void decide(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (e.isCanceled()) {
            var data = data(player);
            if (data == null || data.isReadOnly() || !data.isDarkBody() || (data.getCp() > 0 && !adminKill(e.getSource()))) return;
            e.setCanceled(false);
            player.setHealth(0);
            return;
        }
        if (canTransform(player, e.getSource())) { e.setCanceled(true); transform(player); }
    }
    /** 残損傷の精算は全部の防御の後（白翼自動防御はLOW）。他MODが取り消した損傷はここへ来ない。 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true) public static void damage(AcademyLivingDamageEvent e) {
        PierceHits.damageStage(e, ev -> { if (ev.getEntity() instanceof ServerPlayer player) ev.setAmount(settle(player, ev.getSource(), ev.getAmount())); });
    }
    /** 無効条件は防御より先（共通の防御順）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true) public static void attacked(AcademyLivingAttackEvent e) {
        PierceHits.attack(e, ev -> { if (immuneDamage(ev.getSource()) && isBody(ev.getEntity())) ev.setCanceled(true); });
    }
    /** 未元物質人間はトーテムを使わない（無駄に消費しない）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void totem(LivingUseTotemEvent e) {
        if (isBody(e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent public static void effect(MobEffectEvent.Applicable e) {
        if (isBody(e.getEntity()) && harmfulEffect(e.getEffectInstance().getEffect().value())) e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    /** 系統の喪失で通常の生命力へ戻す。CPが0で死亡が確定していたら死亡を優先する。 */
    @SubscribeEvent public static void category(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CategoryChange e) {
        if (!(e.player() instanceof ServerPlayer player)) return;
        var data = data(player);
        if (data == null) return;
        float rate = data.takeDarkBodyLossRate();
        if (rate < 0) return;
        if (rate == 0) { die(player, player.damageSources().generic()); return; }
        player.setHealth(Math.max(1, rate * player.getMaxHealth()));
        AbilitySyncEvents.sync(player, true);
    }
}
