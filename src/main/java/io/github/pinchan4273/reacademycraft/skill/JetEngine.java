package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作JetEngine（WeAthFolD/KSkun）: meltdownerのレベル4。
 *
 * 原作はキーを押し続けて12ブロック以内に印を狙い、離すと術者を8tickかけてそこへ投げ、途中の生き物を焼く。移植版は原作とは異なる
 * 仕様として、推進にしている: 1回押すとオン、次に押すとオフ。オンにすると原作の跳躍の代価を払う。原作のconsumeは
 * (overload, cp)を取るので、原作が"consumption"と呼ぶ値をオーバーロードとして、"overload"と呼ぶ値をCPとして払う。
 * そして跳躍の経験値を与える。オンの間、術者は視線の方向へ運ばれる: 毎tick速度が視線に沿ったspeed()へ4分の1ずつ近づくので、
 * 視点と共に曲がり、その速さに落ち着く。speed()と開始時に術者が既に持っていた速さの大きい方を超えては増えない: 落下中や
 * 強く吹き飛ばされている間にオンにした術者は、最初はその速さを保ち、そこからspeed()へ下がっていく（切り詰めない）。
 * クライアントはいつもどおり術者を動かすので、壁で止まる。オンの間は切り替え型技能の維持コストを払う（止めている回復に、
 * toggle_upkeep.jet_engine × (1 - 熟練度)を加えた量。熟練度100%で回復と釣り合う）。払えなくなる、能力のオフ、プリセットやカテゴリの
 * 変更、技能がスロットから外れる、妨害、コンテナの画面、死亡、ログアウト、ディメンションの移動で終わる。術者自身のクライアントも、
 * 画面が開くかウィンドウがフォーカスを失うと終える。終了で原作のクールダウンが始まる。通り抜けた生き物は原作のダメージで焼かれ、
 * それぞれHIT_INTERVAL tickに最大1回。
 *
 * オンの間は落下を数えず（原作の飛行中と同じ）、浮いているプレイヤーに対するサーバーの確認の上では術者は飛んでよい: mayflyを
 * 持たない術者にだけ貸し、ゲームモードが飛行を与える場合を除き、終了時に返してもらう（lendFlightとgiveBackFlightを参照）。
 * 終了後、術者は速さを保ち、誰とも同じように落ちる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class JetEngine {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "jet_engine");
    /** 原作ではなく、試遊で調整した値: 推進の速さ（1tickあたりのブロック数）、それへ向かう速さ、1tickあたりのCP、同じ生き物を焼く間隔。 */
    public static final float TURN = .25f;
    public static final int HIT_INTERVAL = 10;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp;
        final Map<UUID, Integer> lastHit = new HashMap<>();
        int ticks; Vec3 velocity;
        /** この推進が術者にmayflyを貸したか（そのとき術者は自分のものを持っていなかった）。 */
        boolean lent;
        /** 能力データが毎tick精算する維持コストへの、この技能の要求。 */
        PlayerAbilityData.Upkeep upkeep;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(ID); velocity = player.getDeltaMovement();
        }
        /** 原作はこれを"consumption"と呼び、consumeのoverload引数へ渡す。 */
        float overloadCost() { return ArcGen.lerp(170, 140, exp); }
        /** 原作はこれを"overload"と呼び、consumeのCP引数へ渡す。 */
        float cpCost() { return ArcGen.lerp(60, 50, exp); }
        float damage() { return ArcGen.lerp(7, 20, exp); }
        int cooldown() { return (int) ArcGen.lerp(60, 30, exp); }
    }

    /**
     * 原作の照準: 12ブロック以内で見ているブロック、または12ブロック先。推進はもう照準の印を知らせない。
     * ClientJetEngineEffectの印の描画は、印を表示することがあればこれを読む。
     */
    public static Vec3 destination(net.minecraft.world.entity.player.Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(12));
        if (!player.level().hasChunkAt(net.minecraft.core.BlockPos.containing(end))) return end;
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
    }

    public static float speed(float exp) { return ArcGen.lerp(.7f, 1f, exp); }

    private JetEngine() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }

    /** 推進をオンにする。オンの間の押下は、AbilityActionがオフとして扱う。 */
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
        var session = new Session(player, data, slot);
        if (!data.consume(ID, session.cpCost(), session.overloadCost(), player.getAbilities().instabuild)) return "academy.cast.cp";
        // 支払いで術者がオーバーロードすることがあり、そのイベントは、このモードがまだ存在しないうちにすべてのモードを終える:
        // その場合は何も始めない。オーバーロードさせた分は、他のオーバーロードを起こすコストと同じく払ったまま。
        if (!allowed(player, data) || data.isOverloadLocked() || data.isInterfering() || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))) {
            AbilitySyncEvents.sync(player, true); return "academy.cast.overload";
        }
        ACTIVE.put(player.getUUID(), session);
        session.upkeep = data.startUpkeep(ID, () -> player.getAbilities().instabuild);
        lendFlight(player, session);
        data.addProficiency(ID, .004f);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, true);
        // 原作はコンテキストを持つすべてのクライアントへMSG_TRIGGERを送るので、ダイヤ形の盾とその粒子は術者だけでなく近くの全員に見える。
        // ここでは推進している間ずっと。
        io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, ID, 0, () -> ACTIVE.get(player.getUUID()) == session);
        AbilitySyncEvents.sync(player, true);
        return "";
    }

    /** このスロットからオンにした推進が動いたtick数。そこからオンでなければ-1。 */
    public static int ageOn(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        return session != null && session.slot == slot ? session.ticks : -1;
    }

    /** このスロットの推進だけの安全な停止: クライアントの画面かフォーカスが外れた。 */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        giveBackFlight(player, session);
        io.github.pinchan4273.reacademycraft.network.SkillModeState.send(player, ID, false);
        var data = data(player);
        if (data != null) data.endUpkeep(session.upkeep);
        if (data != null && !data.isReadOnly()) {
            data.setCooldown(ID, session.cooldown());
            AbilitySyncEvents.sync(player, true);
        }
    }
    /**
     * 推進は、浮いているプレイヤーに対するサーバーの確認のためにmayflyを要する。術者が持っていないときだけ貸し（動作中に失ったとき、
     * つまりサバイバルへの変更や他の発生源による取り上げでも再び貸す）、貸したことを覚えておく。
     */
    private static void lendFlight(ServerPlayer player, Session session) {
        var abilities = player.getAbilities();
        if (abilities.mayfly) return;
        abilities.mayfly = true; session.lent = true;
        player.onUpdateAbilities();
    }
    /**
     * この推進が貸したものだけを、術者のゲームモード自体が飛行を与えない（クリエイティブ、スペクテイター）ときだけ返してもらう。
     * Minecraftはmayflyを所有者の無い1つのフラグとして持つので、推進が貸している間に他のmodが与えた飛行は貸したものと区別できず、
     * 一緒に失われる。これはこの処理の限界であり、保証ではない。開始時に術者が既に持っていた飛行には手を出さない。
     */
    private static void giveBackFlight(ServerPlayer player, Session session) {
        if (!session.lent || player.isCreative() || player.isSpectator()) return;
        var abilities = player.getAbilities();
        abilities.mayfly = false; abilities.flying = false;
        player.onUpdateAbilities();
    }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static boolean flying(ServerPlayer player) { return holding(player); }
    public static int flightTicks(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.ticks;
    }
    /** この推進が最近焼いた生き物。HIT_INTERVAL tick後に忘れる。 */
    public static int remembered(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? 0 : session.lastHit.size();
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || data.isInterfering()) { stop(player); return; }
        // 維持コストはこのtickの始めに精算済み。払えなかった要求は外れている。
        if (!data.upkeepHeld(session.upkeep)) { stop(player); return; }
        lendFlight(player, session);
        session.ticks++;
        session.lastHit.values().removeIf(at -> session.ticks - at >= HIT_INTERVAL);
        if (player.isPassenger()) player.stopRiding();
        burnWhatWePassed(player, data, session);
        var target = player.getLookAngle().scale(speed(session.exp));
        session.velocity = session.velocity.add(target.subtract(session.velocity).scale(TURN));
        player.setDeltaMovement(session.velocity);
        player.hurtMarked = true;
        player.fallDistance = 0;
        AbilitySyncEvents.sync(player, false);
    }

    /** 原作は術者が前のtickに通った線分を光線で調べ、生き物を焼く。 */
    private static void burnWhatWePassed(ServerPlayer player, PlayerAbilityData data, Session session) {
        Vec3 from = new Vec3(player.xo, player.yo, player.zo), to = player.position();
        if (from.distanceToSqr(to) < 1e-6) return;
        double nearest = Double.MAX_VALUE;
        Entity hit = null;
        for (Entity entity : player.serverLevel().getEntities(player,
                player.getBoundingBox().expandTowards(from.subtract(to)).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e)
                        && !session.lastHit.containsKey(e.getUUID()))) {
            var box = entity.getBoundingBox().inflate(.3);
            var intercept = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, to);
            if (intercept.isPresent() && from.distanceToSqr(intercept.get()) < nearest) {
                nearest = from.distanceToSqr(intercept.get()); hit = entity;
            }
        }
        if (hit == null) return;
        session.lastHit.put(hit.getUUID(), session.ticks);
        MDDamageHelper.attack(player, data, hit, ID, session.damage());
    }

    /** テスト用の入口: 推進のサーバー1tick分。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stop(p); }
    /** 死亡またはエンドからの帰還: 新しいプレイヤーは推進も、それが貸した飛行も持たない。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        var session = ACTIVE.remove(e.getEntity().getUUID());
        if (session != null && e.getEntity() instanceof ServerPlayer p) {
            giveBackFlight(p, session);
            io.github.pinchan4273.reacademycraft.network.SkillModeState.send(p, ID, false);
        }
    }
    /** プレイヤーを保存する前に: 推進が貸した飛行を誰にも残さない。 */
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) {
        for (var player : e.getServer().getPlayerList().getPlayers()) stop(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
