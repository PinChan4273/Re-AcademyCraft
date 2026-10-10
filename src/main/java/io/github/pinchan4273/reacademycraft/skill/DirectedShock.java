package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作DirectedShock: ベクトル操作のレベル1の打撃。
 *
 * 押し続けてから、6tickより長く50tickより短い時間で離す（原作MIN_TICKSとMAX_ACCEPTED_TICKS）。それ以外の離し方では何もせず、
 * 200tick押し続けると終わる。窓の中で離すと、何かがあってもなくてもlerp(50, 100, exp)のCPとlerp(18, 12, exp)のオーバーロードを払い、
 * 視線の3ブロック以内の生き物にlerp(7, 15, exp)のダメージを与えて押し出す: 熟練度25%からは原作のノックバックで上と後ろへ投げ、
 * 常に術者から離れる方向へ0.24の速さを加える。当たれば経験値0.0035とクールダウンlerp(60, 20, exp)、外れれば0.001でクールダウン無し。
 *
 * 原作のノックバックは対象のzの速さをyの方向から設定する（motionZ = delta.y * -0.7）。これは書かれたとおりに移植している。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class DirectedShock {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "dir_shock");
    /** 原作MIN_TICKS、MAX_ACCEPTED_TICKS、MAX_TOLERANT_TICKS。 */
    public static final int MIN_TICKS = 6, MAX_ACCEPTED_TICKS = 50, MAX_TOLERANT_TICKS = 200;
    public static final double RANGE = 3;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float damage; long heartbeat; int ticks;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
            damage = damage(data.getProficiency(ID));
        }
    }

    private DirectedShock() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    /** 原作のダメージ。コンテキストを作るときに固定する。 */
    public static float damage(float exp) { return ArcGen.lerp(7, 15, exp); }
    public static float consumption(float exp) { return ArcGen.lerp(50, 100, exp); }
    public static float overload(float exp) { return ArcGen.lerp(18, 12, exp); }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.VECMANIP.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        ACTIVE.put(player.getUUID(), new Session(player, data, slot));
        return "";
    }
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }
    public static void release(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()) return;
        // 原作l_keyUp: 窓の中での離しだけが実行する。
        if (session.ticks > MIN_TICKS && session.ticks < MAX_ACCEPTED_TICKS) perform(player, data, session.damage);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /** 原作s_perform。 */
    private static void perform(ServerPlayer player, PlayerAbilityData data, float damage) {
        float exp = data.getProficiency(ID);
        if (!data.consume(ID, consumption(exp), overload(exp), player.getAbilities().instabuild)) return;
        var target = TPSkillHelper.traceLiving(player, RANGE, TPSkillHelper::living).entity();
        if (target != null) {
            SkillCombat.attack(player, target, ID, damage);
            knockback(player, target, data.getProficiency(ID));
            data.setCooldown(ID, (int) ArcGen.lerp(60, 20, data.getProficiency(ID)));
            // 当たったときの原作c_effect。
            player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_DIRECTED_SHOCK.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
            var away = target.position().subtract(player.position()).normalize().scale(.24);
            target.setDeltaMovement(target.getDeltaMovement().add(away));
            target.hurtMarked = true;
            data.addProficiency(ID, .0035f);
        } else {
            data.addProficiency(ID, .001f);
        }
        AbilitySyncEvents.sync(player, true);
    }
    /**
     * 原作のノックバック: 熟練度25%から、対象を0.1持ち上げ、術者の頭から離れる方向へ0.6上向きに傾けて投げる。zはyから取る
     * （原作の書いたとおり）。
     */
    public static void knockback(ServerPlayer player, Entity target, float exp) {
        if (exp < .25f) return;
        var delta = player.getEyePosition().subtract(target.getEyePosition()).normalize();
        delta = new Vec3(delta.x, delta.y - .6f, delta.z).normalize();
        target.setPos(target.getX(), target.getY() + .1, target.getZ());
        target.setDeltaMovement(delta.x * -.7f, delta.y * -.7f, delta.y * -.7f);
        target.hurtMarked = true;
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
                || ++session.ticks >= MAX_TOLERANT_TICKS) stop(player);
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
