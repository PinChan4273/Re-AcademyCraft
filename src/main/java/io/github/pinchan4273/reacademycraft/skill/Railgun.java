package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import io.github.pinchan4273.reacademycraft.network.MdRayEffect;
import io.github.pinchan4273.reacademycraft.world.CoinLedger;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import io.github.pinchan4273.reacademycraft.world.entity.CoinEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作Railgun（WeAthFolD/KSkun）: 20tickの鉄の溜め、投げたコインのQTE、コスト・クールダウン・反射。溜めと発射の判定は
 * サーバーで行う。クライアントが弾のエンティティや対象を渡すことはできない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class Railgun {
    public static final ResourceLocation ID = RailgunAttack.ID;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    /** 原作自身の光線の長さ、反射光線の長さ、伝える20ブロック。 */
    public static final double BEAM_LENGTH = 45, REFLECT_LENGTH = 15, PRESENTATION_RANGE = 20;
    private static final class Session {
        final ServerPlayer owner; final Level level; final int preset, slot, inventorySlot;
        final ItemStack stack, original;
        int ticks; long heartbeat, lastTick = Long.MIN_VALUE;
        Session(ServerPlayer p, PlayerAbilityData d, int slot) {
            owner = p; level = p.level(); preset = d.getCurrentPreset(); this.slot = slot;
            inventorySlot = p.getInventory().selected; stack = p.getMainHandItem(); original = stack.copy();
            heartbeat = level.getGameTime();
        }
    }
    private Railgun() { }
    public static boolean accepted(ItemStack stack) { return !stack.isEmpty() && (stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_BLOCK)); }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer p, PlayerAbilityData d) {
        return d != null && !d.isReadOnly() && p.isAlive() && d.isActive() && !d.isOverloadLocked()
                && ArcGen.CATEGORY.equals(d.getAbility()) && d.hasLearned(ID) && p.containerMenu == p.inventoryMenu;
    }
    private static CoinEntity coin(ServerPlayer p) {
        var token = CoinLedger.get(p.serverLevel()).token(p.getUUID()).orElse(null);
        return token != null && p.serverLevel().getEntity(token) instanceof CoinEntity c && c.liveForRailgun(p) ? c : null;
    }
    public static boolean isCharging(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    public static String start(ServerPlayer p, PlayerAbilityData d, int slot) {
        if (slot < 0 || slot >= 4 || d == null || d.isReadOnly() || !p.isAlive()
                || !ArcGen.CATEGORY.equals(d.getAbility()) || !d.hasLearned(ID)
                || !ID.equals(d.getSlot(d.getCurrentPreset(), slot))) return "academy.cast.unlearned";
        if (!d.isActive()) return "academy.cast.inactive";
        if (d.isOverloadLocked()) return "academy.cast.overload";
        if (p.containerMenu != p.inventoryMenu) return "academy.cast.unlearned";
        if (d.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (!p.getAbilities().instabuild && d.getCp() < ArcGen.lerp(200, 450, d.getProficiency(ID))) return "academy.cast.cp";
        var coin = coin(p);
        if (coin != null) {
            stop(p);
            if (!coin.beginRailgunAttempt(p) || coin.progress() <= .7f) return "academy.cast.railgun_qte";
            return fire(p, d, coin);
        }
        if (isCharging(p)) return "";
        if (!accepted(p.getMainHandItem())) return "academy.cast.railgun_ammo";
        ACTIVE.put(p.getUUID(), new Session(p, d, slot));
        // 原作Delegate.onKeyDown: 鉄の溜めは、手の火花を術者だけに見せる。
        io.github.pinchan4273.reacademycraft.network.RailgunHandEffect.send(p, false);
        return "";
    }
    /**
     * 原作onThrowCoin: 能力を使えるプレイヤー（CPData.canUseAbility）が、現在のプリセットにRailgunを持ってコインを投げると、
     * 30ブロック以内の全員にその手の火花を見せる。
     */
    @SubscribeEvent public static void railgunCoinThrown(io.github.pinchan4273.reacademycraft.event.AcademyEvent.CoinThrow event) {
        if (event.player() instanceof ServerPlayer p && coinSparks(data(p))) io.github.pinchan4273.reacademycraft.network.RailgunHandEffect.send(p, true);
    }
    public static boolean coinSparks(PlayerAbilityData d) {
        if (d == null || !d.isActive() || d.isOverloadLocked() || d.isInterfering()) return false;
        for (int slot = 0; slot < 4; slot++) if (ID.equals(d.getSlot(d.getCurrentPreset(), slot))) return true;
        return false;
    }
    public static void renew(ServerPlayer p, int slot) {
        var s = ACTIVE.get(p.getUUID()); if (s != null && s.owner == p && s.slot == slot) s.heartbeat = p.level().getGameTime();
    }
    public static void stop(ServerPlayer p) { ACTIVE.remove(p.getUUID()); }
    public static void release(ServerPlayer p, int slot) { var s = ACTIVE.get(p.getUUID()); if (s != null && s.slot == slot) stop(p); }
    public static void tick(ServerPlayer p) {
        var s = ACTIVE.get(p.getUUID()); if (s == null) return; var d = data(p); long now = p.level().getGameTime();
        if (!allowed(p, d) || s.owner != p || s.level != p.level() || now < s.heartbeat || now - s.heartbeat >= 40
                || d.getCurrentPreset() != s.preset || !ID.equals(d.getSlot(s.preset, s.slot)) || d.getCooldown(ID) > 0
                || p.getInventory().selected != s.inventorySlot || p.getMainHandItem() != s.stack
                || !ItemStack.matches(s.stack, s.original) || coin(p) != null) { stop(p); return; }
        if (s.lastTick == now) return; s.lastTick = now;
        if (++s.ticks < 20) return;
        // 原作MSG_ITEM_PERFORMは、撃てないときは何もせず何も言わない。
        stop(p); fire(p, d, null);
    }
    private static String fire(ServerPlayer p, PlayerAbilityData d, CoinEntity coin) {
        if (!allowed(p, d) || d.getCooldown(ID) > 0) return "academy.cast.unlearned";
        float exp = d.getProficiency(ID), cp = ArcGen.lerp(200, 450, exp), overload = ArcGen.lerp(180, 120, exp);
        if (!p.getAbilities().instabuild && d.getCp() < cp) return "academy.cast.cp";
        var held = p.getMainHandItem();
        if (coin == null && !accepted(held)) return "academy.cast.railgun_ammo";
        // 支払い可能かの確認はすべて保管物の引き落としより先に行う。これらの操作にはイベントのコールバックが無く、論理サーバーの
        // スレッドでまとめて実行する。
        if (coin != null && !coin.consumeForRailgun(p)) return "academy.cast.railgun_qte";
        if (!d.consume(ID, cp, overload, p.getAbilities().instabuild)) throw new IllegalStateException("Railgun resource preflight changed");
        if (coin == null && !p.getAbilities().instabuild) held.shrink(1);
        // ダメージや保護のリスナーを呼ぶ前にクールダウンを設定する（再入の防止）。
        d.setCooldown(ID, (int) ArcGen.lerp(300, 160, exp));
        p.getInventory().setChanged(); p.inventoryMenu.broadcastChanges();
        var start = p.position(); var look = p.getLookAngle();
        // 原作Railgunは発射した位置そのもので鳴らす。Arcと違い術者には付いて行かない。
        // 地形より先に送り、撃った音自体が破壊音より先にクライアントへ届くようにする。
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), AcademySounds.EM_RAILGUN.get(), SoundSource.AMBIENT, .5f, 1f);
        var attack = RailgunAttack.perform(p, start, look, ArcGen.lerp(60, 110, exp));
        RailgunTerrain.perform(p, start, look, ArcGen.lerp(900, 2000, exp), attack.terrainLimitSquared(), p.getRandom());
        d.addProficiency(ID, attack.hit() ? .01f : .005f);
        AbilitySyncEvents.sync(p, true);
        beams(p, look, attack);
        return "";
    }
    /**
     * 20ブロック以内の各クライアントでの原作EntityRailgunFX: 光線そのもの。常に目から視線に沿って原作の45ブロックで、地形で
     * 短くならない。反射されたときは反射した者までに短くし、その点から反射した者の見る方向へ15の2本目の光線を続ける。
     */
    private static void beams(ServerPlayer p, net.minecraft.world.phys.Vec3 look, RailgunAttack.Result attack) {
        var eye = p.getEyePosition();
        double length = BEAM_LENGTH;
        var reflector = attack.reflector();
        if (reflector != null) length = Math.min(length, reflector.distanceTo(p));
        broadcast(p, new MdRayEffect(p.level().dimension().location(), p.getUUID(), p.getId(),
                nextSession(), eye, eye.add(look.scale(length)), MdRayEffect.RAILGUN));
        if (reflector == null) return;
        // 原作は反射光線を、反射した者自身ではなく、術者が反射した者の距離で見ていた所に置き、反射した者の頭の向きで回す。
        var at = eye.add(look.scale(reflector.distanceTo(p)));
        var away = net.minecraft.world.phys.Vec3.directionFromRotation(reflector.getXRot(), reflector.getYHeadRot());
        broadcast(p, new MdRayEffect(p.level().dimension().location(), p.getUUID(), p.getId(),
                nextSession(), at, at.add(away.scale(REFLECT_LENGTH)), MdRayEffect.RAILGUN));
    }
    private static void broadcast(ServerPlayer player, MdRayEffect beam) {
        if (player.connection != null) io.github.pinchan4273.reacademycraft.network.AcademyNetwork.send(player, beam);
        for (var observer : player.serverLevel().players())
            if (observer != player && observer.distanceToSqr(player) <= PRESENTATION_RANGE * PRESENTATION_RANGE)
                io.github.pinchan4273.reacademycraft.network.AcademyNetwork.send(observer, beam);
    }
    private static long nextSession() { return io.github.pinchan4273.reacademycraft.network.MdSessions.next(); }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) { if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
