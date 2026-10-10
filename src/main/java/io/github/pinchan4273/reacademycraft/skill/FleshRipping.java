package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作FleshRipping（WeAthFolD、KSkun）: テレポーターのレベル3技能。
 *
 * 押し続けてlerp(6, 14, exp)ブロック以内の生き物を狙い、離すとその一部を引き裂く: 防具を無視するlerp(5, 12, exp)のダメージで、
 * テレポーターのクリティカル判定を通す。狙いの毎tick対象を改めて取り、術者がlerp(130, 270, exp)のCPを払えなくなると狙いは終わる。
 * 離すと最後のtickに取ったものを打ち、それが無ければコストはかからない。一撃は原作の強制consumeでlerp(130, 270, exp)のCPと
 * lerp(60, 50, exp)のオーバーロードを払い、20分の1の確率で術者に5秒間の吐き気を与える。クールダウンはlerp(90, 40, exp)。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class FleshRipping {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "flesh_ripping");
    /** 原作getDisgustProbと、それが与える吐き気。 */
    public static final float NAUSEA_CHANCE = .05f;
    public static final int NAUSEA_TICKS = 100;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; long heartbeat; Entity target;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); heartbeat = level.getGameTime();
        }
    }

    private FleshRipping() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static double range(float exp) { return ArcGen.lerp(6, 14, exp); }
    public static float damage(float exp) { return ArcGen.lerp(5, 12, exp); }
    public static float consumption(float exp) { return ArcGen.lerp(130, 270, exp); }
    public static float overload(float exp) { return ArcGen.lerp(60, 50, exp); }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        var session = new Session(player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // 術者のクライアントへの原作MSG_MADEALIVE: このセッションが生きている間、照準の印を表示する。
        io.github.pinchan4273.reacademycraft.network.AimState.announce(player, ID, () -> ACTIVE.get(player.getUUID()) == session);
        return "";
    }
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }
    public static void release(ServerPlayer player, int slot) { release(player, slot, () -> player.getRandom().nextFloat()); }
    /** 与えた乱数源から、クリティカルの段の判定と吐き気の判定を引いて、離す。 */
    public static void release(ServerPlayer player, int slot, DoubleSupplier roll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()) return;
        // 原作s_end: 最後のtickに何も取っていなければ中断で、コストはかからない。
        if (session.target == null) return;
        float exp = session.exp;
        data.consumeWithForce(ID, consumption(exp), overload(exp), player.getAbilities().instabuild);
        SkillCombat.attackIgnoreArmor(player, session.target, ID, damage(exp) * TPSkillHelper.critical(player, data, session.target, roll));
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_GUTS.get(), net.minecraft.sounds.SoundSource.AMBIENT, .6f, 1f);
        // 原作c_endEffect: 血はその音と共に、コンテキストを持っていたすべてのクライアントで出る。
        io.github.pinchan4273.reacademycraft.network.SkillBurst.showNear(player, session.target, io.github.pinchan4273.reacademycraft.network.SkillBurst.BLOOD, 0);
        if (roll.getAsDouble() < NAUSEA_CHANCE)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS));
        data.setCooldown(ID, (int) ArcGen.lerp(90, 40, exp));
        data.addProficiency(ID, .005f);
        AbilitySyncEvents.sync(player, true);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static Entity target(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? null : s.target; }

    /** 原作getAttackTarget: ブロックの方が近くなければ、視線が最初に届く生き物。TPSkillHelper.traceLivingを参照。 */
    public static Entity aim(net.minecraft.world.entity.player.Player player, float exp) {
        return TPSkillHelper.traceLiving(player, range(exp), TPSkillHelper::living).entity();
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10
                // 原作s_tick: 一撃の代価を払えなくなると狙いを終える（クリエイティブは常に払える）。
                || !(player.getAbilities().instabuild || data.canConsumeCp(ID, consumption(session.exp)))) { stop(player); return; }
        session.target = aim(player, session.exp);
    }
    /** テスト用の入口: サーバーのtickループを待たずに押し続けのセッションを進める。 */
    public static void tickForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null) session.heartbeat = player.level().getGameTime();
        tick(player);
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
