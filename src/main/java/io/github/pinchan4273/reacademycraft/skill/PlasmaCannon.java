package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作PlasmaCannon: ベクトル操作のレベル5技能。
 *
 * 押し続けて、術者が立っていた所の15ブロック上にプラズマ体を溜める: オンにするとlerp(500, 400, exp)のオーバーロードが加わり、
 * それは下がらなくなる。lerp(60, 30, exp)までの溜めの各tickにlerp(18, 25, exp)のCP。溜め終えて離すと、対象は100ブロック以内で
 * 視線が生き物（目の高さの60%だけ上げた所）かブロックに当たる所になる。プラズマ体は1tickに1ブロックそこへ飛び、到達したとき、途中で
 * ブロックに当たったとき、または240tick後に弾ける。爆発は常に対象の位置で起きる: 10ブロック以内のすべて（術者も）がlerp(80, 150, exp)の
 * ダメージを受け、ワールドが技能によるブロック破壊を許す所ではlerp(12, 15, exp)の威力の爆発が続く。経験値0.008、クールダウンは
 * 離したときからlerp(1000, 600, exp)。
 *
 * 書かれたとおりに移植している: プラズマ体がブロックに当たったり、遠くで時間切れになったりしても爆発は対象の位置で起きる。
 * ブロックを壊せない所では、原作は爆発の前半を丸ごと飛ばすので、そのとき爆発は何も傷つけず見えるだけになる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class PlasmaCannon {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "plasma_cannon");
    public static final int MAX_FLIGHT_TICKS = 240;
    public static final double RANGE = 100, BURST_RADIUS = 10;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; long heartbeat; int ticks; float overloadKeep; long visual;
        Vec3 body, target;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
            exp = data.getProficiency(ID); body = player.position().add(0, 15, 0);
        }
        boolean flying() { return target != null; }
    }

    private PlasmaCannon() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static float chargeTicks(float exp) { return ArcGen.lerp(60, 30, exp); }
    public static float startOverload(float exp) { return ArcGen.lerp(500, 400, exp); }
    public static float chargeConsumption(float exp) { return ArcGen.lerp(18, 25, exp); }
    public static float damage(float exp) { return ArcGen.lerp(80, 150, exp); }
    public static float power(float exp) { return ArcGen.lerp(12, 15, exp); }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(1000, 600, exp); }
    public static boolean active(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static boolean flying(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s != null && s.flying(); }
    public static Vec3 body(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? null : s.body; }
    public static Vec3 target(ServerPlayer player) { var s = ACTIVE.get(player.getUUID()); return s == null ? null : s.target; }

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
        var session = new Session(player, data, slot);
        // 原作s_madeAlive: オーバーロードは収まるかどうかに関係なく加え、その後保つ。
        data.consumeInMode(ID, 0, startOverload(session.exp), player.getAbilities().instabuild);
        session.overloadKeep = data.getOverload();
        ACTIVE.put(player.getUUID(), session);
        // コンテキストを持つ各クライアントでの原作c_begin: 溜めている場所のプラズマ体と、その下の竜巻。このセッションが続く間表示し、
        // プラズマ体の位置は毎tick付いて行く。
        session.visual = io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, ID, 0, () -> ACTIVE.get(player.getUUID()) == session);
        io.github.pinchan4273.reacademycraft.network.SkillVisual.move(session.visual, session.body, 0);
        // 原作c_begin: 砲の音はコンテキストが終わるまで、1回だけ術者に付いて行く。
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_PLASMA_CANNON.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, false, () -> active(player));
        AbilitySyncEvents.sync(player, true);
        return "";
    }
    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot && !session.flying()) session.heartbeat = player.level().getGameTime();
    }
    /** 原作l_keyUpとs_perform: 溜め終わる前に離すと終わり、溜め終えた後なら飛ぶ。 */
    public static void release(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot || session.flying()) return;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || session.ticks < chargeTicks(session.exp)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        data.addProficiency(ID, .008f);
        // 原作Raytrace.getLookingPos(player, 100, living)。
        var hit = TPSkillHelper.traceLiving(player, RANGE, TPSkillHelper::living);
        session.target = hit.entity() == null ? hit.position() : hit.position().add(0, hit.entity().getEyeHeight() * .6, 0);
        session.ticks = 0;
        data.setCooldown(ID, cooldown(data.getProficiency(ID)));
        AbilitySyncEvents.sync(player, true);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot && !session.flying()) ACTIVE.remove(player.getUUID());
    }
    /** 溜めを終える。既に飛んでいるプラズマ体はキーで止めるものではない。 */
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && !session.flying()) ACTIVE.remove(player.getUUID());
    }

    private static void tick(ServerPlayer player, boolean explosion) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!player.isAlive() || session.level != player.level() || data == null || data.isReadOnly()) { ACTIVE.remove(player.getUUID()); return; }
        if (data.getOverload() < session.overloadKeep) data.setOverload(session.overloadKeep);
        session.ticks++;
        // 原作l_tick: 溜めの終わりは、完了したtickに術者だけが聞く。
        if (!session.flying() && session.ticks == (int) chargeTicks(session.exp))
            player.playNotifySound(io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_PLASMA_CANNON_T.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        if (!session.flying()) {
            long now = player.level().getGameTime();
            if (!allowed(player, data) || data.getCurrentPreset() != session.preset
                    || !ID.equals(data.getSlot(session.preset, session.slot)) || now - session.heartbeat > 10) {
                ACTIVE.remove(player.getUUID()); return;
            }
            // 原作tryConsume（溜めのtickだけ）。押し続けのキーのオーバーロードによる中断は、consume()と同じくこれを終える。
            if (session.ticks < chargeTicks(session.exp) && !data.consume(ID, chargeConsumption(session.exp), 0, player.getAbilities().instabuild))
                ACTIVE.remove(player.getUUID());
            return;
        }
        var last = session.body;
        var delta = session.target.subtract(session.body);
        if (delta.length() >= 1) session.body = session.body.add(delta.normalize());
        io.github.pinchan4273.reacademycraft.network.SkillVisual.move(session.visual, session.body, 1);
        // 選別無しの原作Raytrace.perform(world, last, body): このtickの区間にあるブロック、または当たりうるどのエンティティ（術者も）でも起爆する。
        boolean struck = player.level().clip(new ClipContext(last, session.body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() != HitResult.Type.MISS || ProjectileHitSeam.firstEntity(player.level(), null, last, session.body) != null;
        if (struck || session.ticks >= MAX_FLIGHT_TICKS || session.body.distanceTo(session.target) < 1.5) {
            ACTIVE.remove(player.getUUID());
            burst(player, data, session.target, explosion);
        }
    }
    /** テスト用の入口: サーバー1tick分。テストのプロットの隣が壊れないよう、爆発は起こさない。 */
    public static void tickForTest(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && !session.flying()) session.heartbeat = player.level().getGameTime();
        tick(player, false);
    }

    /** 原作explode。 */
    private static void burst(ServerPlayer player, PlayerAbilityData data, Vec3 at, boolean explosion) {
        float exp = data.getProficiency(ID);
        var level = (ServerLevel) player.level();
        for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(BURST_RADIUS),
                e -> e.position().distanceToSqr(at) <= BURST_RADIUS * BURST_RADIUS)) {
            SkillCombat.attack(player, entity, ID, damage(exp));
            entity.invulnerableTime = -1;
        }
        if (!explosion) return;
        boolean breaks = AcademyConfig.canDestroy(level, ID);
        var blast = new Explosion(level, player, null, null, at.x, at.y, at.z, power(exp), false,
                breaks ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP);
        if (ForgeEventFactory.onExplosionStart(level, blast)) return;
        // 原作doExplosionA（ブロックと爆発自身のダメージ）は、ブロックを壊せる所でだけ。
        if (breaks) blast.explode();
        blast.finalizeExplosion(false);
        for (var viewer : level.players())
            if (viewer.distanceToSqr(at) < 4096)
                viewer.connection.send(new ClientboundExplodePacket(at.x, at.y, at.z, power(exp), blast.getToBlow(), blast.getHitPlayers().get(viewer)));
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p, true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) ACTIVE.remove(p.getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) ACTIVE.remove(p.getUUID()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
