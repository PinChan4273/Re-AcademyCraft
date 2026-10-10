package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作BloodRetrograde: ベクトル操作のレベル4の一撃。
 *
 * 押し続けてから離すか、30tick後に自動で離れたとき、視線の2ブロック以内にあるもの: それが生き物なら、lerp(280, 350, exp)のCPと
 * lerp(55, 40, exp)のオーバーロードでlerp(30, 60, exp)のダメージを受け、クールダウンはlerp(90, 40, exp)、経験値は0.002。
 * それ以外か何も無ければ、コスト無しで終わる。
 *
 * 原作はクライアントが対象を選んで送り、サーバーはどこにあっても送られたものを打った。ここではサーバー自身が原作の2ブロック以内で
 * 対象を探す。音はここで鳴らし、溜め中の減速と血はClientBloodRetroが担う。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class BloodRetrograde {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "blood_retro");
    /** 原作l_tickは30tickで自動で離れる。 */
    public static final int AUTO_RELEASE_TICKS = 30;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float damage; long heartbeat; int ticks;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
            damage = damage(data.getProficiency(ID));
        }
    }

    private BloodRetrograde() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static float damage(float exp) { return ArcGen.lerp(30, 60, exp); }
    public static float consumption(float exp) { return ArcGen.lerp(280, 350, exp); }
    public static float overload(float exp) { return ArcGen.lerp(55, 40, exp); }

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
        perform(player, data, session.damage);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /** 原作l_keyUpとs_perform: 原作のtraceLivingは2ブロック以内のどのエンティティも取り、打つのは生き物だけ。 */
    private static void perform(ServerPlayer player, PlayerAbilityData data, float damage) {
        var target = TPSkillHelper.traceLiving(player, 2, e -> true).entity();
        if (!(target instanceof LivingEntity living)) return;
        float exp = data.getProficiency(ID);
        if (!data.consume(ID, consumption(exp), overload(exp), player.getAbilities().instabuild)) return;
        data.setCooldown(ID, (int) ArcGen.lerp(90, 40, exp));
        SkillCombat.attack(player, living, ID, damage);
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_BLOOD_RETRO.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, 1f);
        // 原作の各クライアントのc_perform: 血（ClientBloodRetro）。
        io.github.pinchan4273.reacademycraft.network.BloodRetroEffect.send(player, living);
        data.addProficiency(ID, .002f);
        AbilitySyncEvents.sync(player, true);
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10) { stop(player); return; }
        if (++session.ticks >= AUTO_RELEASE_TICKS) release(player, session.slot);
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
