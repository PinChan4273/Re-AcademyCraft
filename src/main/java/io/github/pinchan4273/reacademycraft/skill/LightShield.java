package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作LightShield（WeAthFolD）: meltdownerのもう1つのレベル2技能。
 *
 * 盾が術者の前にあり、前方60度の弧に入ったものを焼き、18tickごとに受ける攻撃1回の大部分を吸収する。
 * Scatter Bombと同じく、オーバーロードは開始時に1回払ってそこを下限にするので、続けている間は回復しない。
 *
 * <p>原作は押し続け型で、熟練度に応じて6〜9秒で切れた。1.0.0の後に承認した案B（docs/CP_RECOVERY_TOGGLE_SPEC_JA.md 9章）で、
 * 1回押すとON、もう一度押すとOFFのトグルにし、固定の時間制限を外した。キーを離しても切れない。続く間の費用は切り替え型技能の
 * 維持の経路（toggle_upkeep.light_shield、熟練度0%で9 CP/tick。原作の1tickあたりlerp(9, 4)の0%値）で払い、払えなくなると終わる。
 * 熟練度100%で単独・待機（防御も接触攻撃も無い）なら、通常の回復の半分が正味で残る。実際に防いだとき・焼いたときの費用は原作のまま残す。
 *
 * 終えると5秒の鈍化と、続けた長さに比例するクールダウン（熟練度最大で半分）がかかる。長さは原作の上限lerp(120, 180)tickで打ち切るので、
 * クールダウンは原作で起こりえた最大（0%で240tick、100%で180tick）を超えない。起動費・吸収と接触の費用・鈍化をどうするかは未決定で、今は原作のまま。
 *
 * 原作は吸収のコストを、盾が敵に触れたときは(overload, cp)、攻撃を吸収したときは(cp, overload)として渡しており、
 * 両方が意図どおりということはありえない。どちらかを選ばず、両方とも書かれたとおりに移植している。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class LightShield {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "light_shield");
    /** 原作ACTION_INTERVAL、届く距離、覆う前方の弧。 */
    private static final int ABSORB_INTERVAL = 18;
    private static final double REACH = 3, ARC_DEGREES = 60;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp, overloadFloor, maxTicks;
        int ticks; int lastAbsorb = -1;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); overloadFloor = data.getOverload();
            maxTicks = ArcGen.lerp(120, 180, exp);
        }
        float absorbDamage() { return ArcGen.lerp(15, 50, exp); }
        float touchDamage() { return ArcGen.lerp(2, 6, exp); }
        float absorbOverload() { return ArcGen.lerp(5, 3, exp); }
        float absorbConsumption() { return ArcGen.lerp(50, 30, exp); }
        /** 続けた長さに比例するクールダウン。長さは原作の時間制限で打ち切る（有限で、保存できる範囲に収まる）。 */
        int cooldown() { float held = Math.min(ticks, maxTicks); return (int) ArcGen.lerp(2 * held, held, exp); }
    }

    private LightShield() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    /**
     * 盾を続けてよいか。妨害（能力妨害器）も含む: 押し続け型だった1.0.0では、妨害されたクライアントが中断を送り心拍も届かなくなって
     * 終わっていた。トグルではキーの経路に頼れないので、サーバーが毎tick確かめ、妨害が始まったtickに通常の終了（維持・音・盾を外し、
     * 鈍化とクールダウン）をする。
     */
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive() && !data.isInterfering()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        if (!data.consume(ID, 0, ArcGen.lerp(110, 60, data.getProficiency(ID)), player.getAbilities().instabuild))
            return "academy.cast.cp";
        // 起動のオーバーロードで術者がオーバーロードした: そのイベントが全セッションを止めた後なので、何も始めない。
        if (data.isOverloadLocked()) return "academy.cast.overload";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        // コンテキストを持つ各クライアントでの原作c_spawn: 押している間のEntityMdShield。
        io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, ID, 0, () -> ACTIVE.get(player.getUUID()) == session);
        // 原作c_start: 起動音と、盾を押している間のループ音。
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_SHIELD_STARTUP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_SHIELD_LOOP.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, true, () -> holding(player));
        return "";
    }

    /** このスロットからオンにした盾が続いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        return session != null && session.slot == slot ? session.ticks : -1;
    }

    /**
     * このスロットの盾だけを終える（自身のキーの2度目の押下、またはクライアントの安全な停止）。別のスロットのキーではこれは止まらない。
     * すべてのセッションを終える処理は、引き続きstop(player)を呼ぶ。
     */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        if (data == null || data.isReadOnly() || session.level != player.level()) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        // 習得していない技能（止める前にリスナーやカテゴリの変更が外した）にはクールダウンを書かない（M-110-02）。
        if (data.hasLearned(ID)) data.setCooldown(ID, session.cooldown());
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int heldTicks(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.ticks;
    }

    /** 原作は術者の前方60度の弧を、yawだけで測って覆う。 */
    private static boolean reachable(ServerPlayer player, Entity target) {
        double yaw = -Math.toDegrees(Math.atan2(target.getX() - player.getX(), target.getZ() - player.getZ()));
        return Math.abs(yaw - player.getYRot()) % 360 < ARC_DEGREES;
    }

    /** このtickもモードを続けてよいか（tickの始めと、熟練度のイベントの後に確かめる）。 */
    private static boolean continues(ServerPlayer player, PlayerAbilityData data, Session session) {
        return data != null && allowed(player, data) && !(session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot)));
    }
    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!continues(player, data, session)) { stop(player); return; }
        // 続ける費用は、このtickの始めに精算した維持コスト。払えなかった要求は外れている。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        // 維持の熟練度（1.1.0、T178-10）。1.0.0の毎tick 1e-6はこれへ統合した。
        data.upkeepEarn(session.upkeep);
        // 熟練度のイベントのリスナーが、モードを終えた・置き換えた・続けられなくした（能力、スロット、プリセット）かもしれない:
        // このtickを古いセッションで続けない（M-110-01）。
        if (ACTIVE.get(player.getUUID()) != session) return;
        if (!continues(player, data, session) || !data.upkeepHeld(session.upkeep)) { stop(player); return; }
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
        session.ticks++;
        var level = player.serverLevel();
        // 原作basicSelectorはEntitySelectors.everything(): ドロップしたアイテムや経験値オーブも焼く。
        for (var target : level.getEntities(player, player.getBoundingBox().inflate(REACH),
                e -> e.isAlive() && !e.isSpectator())) {
            if (target.invulnerableTime > 0 || !reachable(player, target)) continue;
            // 原作はここで接触を(overload, cp)として課す。
            if (!data.consume(ID, session.absorbConsumption(), session.absorbOverload(), player.getAbilities().instabuild)) continue;
            // 接触攻撃のtickは待機ではない。
            data.upkeepActed(session.upkeep);
            if (ACTIVE.get(player.getUUID()) != session) return;
            MDDamageHelper.attack(player, data, target, ID, session.touchDamage());
            data.addProficiency(ID, .001f);
        }
    }

    /** テスト用の入口: サーバーのtickループを待たずにセッションを1tick進める。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    /** 盾が自身の分を食べた後に残るダメージを返す。 */
    public static float absorb(ServerPlayer player, DamageSource source, float damage) {
        var session = ACTIVE.get(player.getUUID());
        var data = data(player);
        // 技能が外された（次のtickでモードが終わる前の被弾）なら何もしない。熟練度の加算は未習得を拒否する（M-110-02）。
        if (session == null || data == null || data.isReadOnly() || !data.hasLearned(ID) || damage == 0) return damage;
        // 妨害が始まってから盾自身のtickで終わるまでの間に届いた攻撃も、盾は食べない。
        if (data.isInterfering()) return damage;
        if (session.lastAbsorb != -1 && session.ticks - session.lastAbsorb <= ABSORB_INTERVAL) return damage;
        var attacker = source.getDirectEntity();
        // 範囲外では吸収を飛ばすだけ（間隔もやり直さない）。原作handleAttackedは範囲外でも.001を得たが、1.1.0（T178-10）は効果を
        // 出していない被弾に熟練度を与えない。
        if (attacker != null && !reachable(player, attacker)) return damage;
        session.lastAbsorb = session.ticks;
        float result = damage;
        // 原作は吸収を、上の接触とは逆に(cp, overload)として課す。
        if (data.consume(ID, session.absorbOverload(), session.absorbConsumption(), player.getAbilities().instabuild)) {
            result -= Math.min(damage, session.absorbDamage());
            // 防御吸収のtickは待機ではない。
            data.upkeepActed(session.upkeep);
            // 吸収できたときだけ（1.1.0、T178-10）。払えず吸収しなかった被弾には与えない（原作は払えなくても.001を得た）。
            data.addProficiency(ID, .001f);
        }
        return Math.max(0, result);
    }

    @SubscribeEvent public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ACTIVE.containsKey(player.getUUID())) return;
        float left = absorb(player, event.getSource(), event.getAmount());
        event.setAmount(left);
        if (left == 0) event.setCanceled(true);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.remove(p.getUUID()) != null)
            io.github.pinchan4273.reacademycraft.network.SkillModeState.send(p, ID, false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
