package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作ShiftTeleport（WeAthFolD、KSkun）: テレポーターのレベル4技能。
 *
 * メインハンドにブロックを持って押し続け、離すとその1つを視線に沿って最大lerp(25, 35, exp)ブロック先へ送る: 視線が当たった面の上、
 * 何にも当たらなければ届く範囲の終点のすぐ上。ブロックがそこへ行けない場合（または術者が狙ったブロックを壊してはならない場合）は、
 * 代わりにアイテムとして1つそこへ落とす。術者の足からその地点までの線が箱を通るすべての生き物は、テレポーターのクリティカル判定を
 * 通してlerp(15, 35, exp)のダメージを受ける。
 *
 * 原作の通常のconsumeで払う: lerp(260, 320, exp)のCPとlerp(40, 30, exp)のオーバーロード。払えなければ何も起きない。
 * 経験値は(1 + 対象数) * 0.002、クールダウンはlerp(100, 60, exp)。サーバーがどのワールドでもブロック破壊を許さない場合、原作は
 * この技能を熟練度最大として扱う。習得するとSpace Fluctuationが開発可能になる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ShiftTeleport {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "shift_tp");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; long heartbeat;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = experience(data); heartbeat = level.getGameTime();
        }
    }

    /** ブロックが行く所、狙ったブロックの位置、落としたものが着く所、当たる生き物を探す線の遠い端。 */
    public record Aim(BlockPos place, BlockPos aimed, Direction face, Vec3 drop, Vec3 lineEnd) { }

    private ShiftTeleport() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.TELEPORTER.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    /** 原作のgetSkillExpの上書き: どのワールドもブロックを壊せない所では熟練度最大。 */
    public static float experience(PlayerAbilityData data) {
        return experience(!AcademyConfig.DESTROY_BLOCKS.get() && AcademyConfig.DESTRUCTION_DIMENSIONS.get().isEmpty(), data.getProficiency(ID));
    }
    public static float experience(boolean noWorldDestroysBlocks, float proficiency) { return noWorldDestroysBlocks ? 1 : proficiency; }
    public static double range(float exp) { return ArcGen.lerp(25, 35, exp); }
    public static float damage(float exp) { return ArcGen.lerp(15, 35, exp); }
    public static float consumption(float exp) { return ArcGen.lerp(260, 320, exp); }
    public static float overload(float exp) { return ArcGen.lerp(40, 30, exp); }
    /** 原作isHandValid: ブロックが空気でないブロックアイテム。 */
    public static boolean handValid(ServerPlayer player) {
        var stack = player.getMainHandItem();
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && !item.getBlock().defaultBlockState().isAir();
    }

    public static String start(ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        // 原作s_madeAlive: 手にブロックが無ければすぐ終わる。
        if (!handValid(player)) return "academy.shift_tp.no_block";
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
    /** 与えた乱数源から、クリティカルの段の判定を引いて、離す。 */
    public static void release(ServerPlayer player, int slot, DoubleSupplier roll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || !handValid(player)) return;
        execute(player, data, session.exp, roll);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /**
     * 原作getTracePositionとgetTraceDest: 目からブロックだけに対する追跡。面に当たると、ブロックをそこから1つ外へ置く。
     * 外れは原作のRaytraceの外れの結果: 届く範囲の終点のブロックを(int)の切り捨てで求め、上向きとするので、ブロックはそのすぐ上に行く。
     */
    public static Aim aim(net.minecraft.world.entity.player.Player player, float exp) {
        var trace = TPSkillHelper.traceLiving(player, range(exp), e -> false);
        BlockPos aimed; Direction face; Vec3 hit;
        if (trace.block() != null) { aimed = trace.block().getBlockPos(); face = trace.block().getDirection(); hit = trace.block().getLocation(); }
        else { var end = trace.position(); aimed = PenetrateTeleport.legacyBlock(end.x, end.y, end.z); face = Direction.UP; hit = end; }
        var place = aimed.relative(face);
        return new Aim(place, aimed, face, hit.add(face.getStepX(), face.getStepY(), face.getStepZ()), Vec3.atCenterOf(place));
    }
    /** 原作getTargetsInLine: 術者の足から行き先のブロックの中央までの線が箱を通る、術者以外の生き物。 */
    public static List<Entity> targets(net.minecraft.world.entity.player.Player player, Aim aim) {
        var from = player.position(); var to = aim.lineEnd();
        return player.level().getEntities(player, new AABB(from, to).inflate(.5), e -> e.isAlive() && !e.isSpectator()
                && TPSkillHelper.living(e) && (e.getBoundingBox().contains(from) || e.getBoundingBox().clip(from, to).isPresent()));
    }

    private static void execute(ServerPlayer player, PlayerAbilityData data, float exp, DoubleSupplier roll) {
        var stack = player.getMainHandItem();
        var item = (BlockItem) stack.getItem();
        var aim = aim(player, exp);
        // 原作ctx.consume: CPとオーバーロードが無ければ何も起きない。
        if (!data.consume(ID, consumption(exp), overload(exp), player.getAbilities().instabuild)) return;
        var level = player.level();
        boolean placed = false;
        if (mayBreak(player, aim.aimed())) {
            var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(aim.drop(), aim.face(), aim.place(), false));
            // 原作canPlaceBlockAt: 行き先は置き換え可能でなければならない。その後BlockItem.placeがプレイヤーと同じように置き、
            // クリエイティブ以外ではスタック自身から1つ取る。
            placed = level.getBlockState(aim.place()).canBeReplaced(context) && item.place(context).consumesAction();
        }
        if (!placed) {
            var drop = stack.copyWithCount(1);
            level.addFreshEntity(new ItemEntity(level, aim.drop().x, aim.drop().y, aim.drop().z, drop));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        var targets = targets(player, aim);
        // 各対象への原作TPSkillHelper.attack: クリティカルはその対象の位置に式を描く。
        for (var target : targets)
            SkillCombat.attack(player, target, ID, damage(exp) * TPSkillHelper.critical(player, data, target, roll));
        // 原作world.playSound(player, player.getPosition(), ...): 術者のブロックの中央で、1.12のサーバー側playSoundが術者を除いたのと同じく、
        // 術者以外の全員が聞く。
        var at = player.blockPosition();
        level.playSound(player, at.getX() + .5, at.getY() + .5, at.getZ() + .5,
                io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_SHIFT.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        // 原作c_end: 術者の半ブロック下から置いたブロックの中央までのtp粒子の線。技能が通ったときは必ず描く。
        io.github.pinchan4273.reacademycraft.network.SkillTrail.show(player, player.position().add(0, -.5, 0),
                net.minecraft.world.phys.Vec3.atCenterOf(aim.place()), io.github.pinchan4273.reacademycraft.network.SkillTrail.SHIFT);
        data.addProficiency(ID, (1 + targets.size()) * .002f);
        data.setCooldown(ID, (int) ArcGen.lerp(100, 60, exp));
        player.inventoryMenu.broadcastChanges();
        AbilitySyncEvents.sync(player, true);
    }
    /** 狙ったブロックへの原作ctx.canBreakBlock: 破壊の設定、次に原作独自のBlockDestroyEventの代わりにForgeの破壊イベント。 */
    private static boolean mayBreak(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        return AcademyConfig.canDestroy(player, pos, ID)
                && !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !ID.equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10) stop(player);
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
