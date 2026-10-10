package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作Groundshock: ベクトル操作のレベル1の踏み鳴らし。
 *
 * 5tick以上押し続け、地面に立った状態で離す: 衝撃が水平の視線方向へ地面に沿って最大lerp(10, 25, exp)ブロック走り、
 * lerp(60, 120, exp)のエネルギーを使う。各段で経路の下のブロックと、原作の確率で周りのブロックを訪れる: 石は丸石に割れ、
 * 草ブロックは土になり、それ以外はエネルギーを使うだけ。それらのブロックの上に立つ生き物はlerp(4, 6, exp)のダメージを受けて
 * 打ち上げられ、経路上のブロックはそのまま壊れることがあり（30%）、経路の上の3ブロックはエネルギーが硬さを上回れば壊れる。
 * 熟練度最大では、術者の周り10 x 2 x 10の範囲の柔らかいブロック（硬さ0.6以下）もすべて壊れ、lerp(30%, 100%)の確率でドロップする。
 * lerp(80, 150, exp)のCPとlerp(15, 10, exp)のオーバーロード。クールダウンはlerp(80, 40, exp)。
 *
 * 書かれたとおりに移植している: 原作は横のブロックを横へ広げるつもりだったが、rotateYawは新しいベクトルを返してそれを捨てるので、
 * 横のブロックは経路に沿って並ぶ。破壊は訪れている横のブロックではなく経路の点を使う。真上や真下を見ると水平の方向が無く、
 * 原作のplotterは例外を投げて衝撃は何もしない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class Groundshock {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "ground_shock");
    public static final int MIN_TICKS = 5;
    private static final double GROUND_BREAK_CHANCE = .3;
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; long heartbeat; int ticks;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
        }
    }

    private Groundshock() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static double energy(float exp) { return ArcGen.lerp(60, 120, exp); }
    public static float damage(float exp) { return ArcGen.lerp(4, 6, exp); }
    public static float consumption(float exp) { return ArcGen.lerp(80, 150, exp); }
    public static float overload(float exp) { return ArcGen.lerp(15, 10, exp); }
    public static int steps(float exp) { return (int) ArcGen.lerp(10, 25, exp); }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(80, 40, exp); }
    public static float dropRate(float exp) { return ArcGen.lerp(.3f, 1, exp); }

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
        var random = player.getRandom();
        release(player, slot, random::nextDouble);
    }
    /** 与えた乱数源から原作の確率を引いて、離す。 */
    public static void release(ServerPlayer player, int slot, DoubleSupplier roll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level() || session.ticks < MIN_TICKS) return;
        perform(player, data, roll);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /** 原作s_perform。 */
    private static void perform(ServerPlayer player, PlayerAbilityData data, DoubleSupplier roll) {
        if (!player.onGround()) return;
        var look = player.getLookAngle().normalize();
        if (look.x == 0 && look.z == 0) return;
        float exp = data.getProficiency(ID);
        if (!data.consume(ID, consumption(exp), overload(exp), player.getAbilities().instabuild)) return;
        var level = (ServerLevel) player.level();
        var plotter = new LegacyPlotter(floor(player.getX()), floor(player.getY()) - 1, floor(player.getZ()), look.x, 0, look.z);
        // 原作はyの速さをコンテキストごとに1回計算する: rangef(0.6, 0.9) * lerp(0.8, 1.3, exp)。
        double ySpeed = (.6 + roll.getAsDouble() * .3) * ArcGen.lerp(.8f, 1.3f, exp);
        float damage = damage(exp), dropRate = dropRate(exp);
        double[] energy = { energy(exp) };
        Set<BlockPos> visited = new HashSet<>();
        Set<Entity> struck = new HashSet<>();
        // 原作のずれ: 横へ広げるつもりだが、経路に沿って並ぶ（クラスの説明を参照）。
        var along = new Vec3(look.x, look.y, look.z);
        List<Object[]> deltas = List.of(new Object[] { Vec3.ZERO, 1.0 }, new Object[] { along, .7 }, new Object[] { along.scale(-1), .7 },
                new Object[] { along.scale(2), .3 }, new Object[] { along.scale(-2), .3 });
        for (int iter = steps(exp); energy[0] > 0 && iter > 0; iter--) {
            int[] next = plotter.next();
            int x = next[0], y = next[1], z = next[2];
            for (var delta : deltas) {
                var d = (Vec3) delta[0]; double chance = (Double) delta[1];
                var pos = BlockPos.containing(x + d.x, y + d.y, z + d.z);
                var state = level.getBlockState(pos);
                if (roll.getAsDouble() < chance && !state.isAir() && visited.add(pos)) {
                    if (legacyStone(state.getBlock())) { level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState()); energy[0] -= .4; }
                    else if (state.is(Blocks.GRASS_BLOCK)) { level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()); energy[0] -= .2; }
                    else if (state.is(Blocks.FARMLAND)) energy[0] -= .1;
                    else energy[0] -= .5;
                    if (roll.getAsDouble() < GROUND_BREAK_CHANCE) breakWithForce(player, new BlockPos(x, y, z), false, energy, dropRate, roll);
                    var box = new AABB(pos.getX() - .2, pos.getY() - .2, pos.getZ() - .2, pos.getX() + 1.4, pos.getY() + 2.2, pos.getZ() + 1.4);
                    for (var entity : level.getEntities(player, box, e -> e.isAlive() && !e.isSpectator() && TPSkillHelper.living(e))) {
                        if (!struck.add(entity)) continue;
                        energy[0] -= 1;
                        SkillCombat.attack(player, entity, ID, damage);
                        var motion = entity.getDeltaMovement();
                        entity.setDeltaMovement(motion.x, ySpeed, motion.z);
                        entity.hurtMarked = true;
                        data.addProficiency(ID, .002f);
                    }
                }
                for (int up = 1; up <= 3; up++) breakWithForce(player, new BlockPos(x, y + up, z), false, energy, dropRate, roll);
            }
        }
        if (exp == 1) {
            double[] unlimited = { Double.MAX_VALUE };
            // 原作posX.toInt: xとzには原作の(int)キャストを保つ（1.12でも既に負になりえた）。yは切り捨てる。これは原作が見たすべてのyで
        // （0未満は無い）同じ結果になり、1.20のy < 0でも術者の下のブロックを保つ。
            int x0 = (int) player.getX(), y0 = net.minecraft.util.Mth.floor(player.getY()), z0 = (int) player.getZ();
            for (int x = x0 - 5; x < x0 + 5; x++) for (int y = y0 - 1; y < y0 + 1; y++) for (int z = z0 - 5; z < z0 + 5; z++) {
                var pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).getDestroySpeed(level, pos) <= .6f) breakWithForce(player, pos, true, unlimited, dropRate, roll);
            }
        }
        data.addProficiency(ID, .001f);
        data.setCooldown(ID, cooldown(exp));
        // 原作c_perform: 音量2で。
        player.level().playSound(null, player, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_GROUNDSHOCK.get(), net.minecraft.sounds.SoundSource.AMBIENT, 2f, 1f);
        // 各クライアントでの原作c_perform: 衝撃が通ったブロックの上に破片と煙。
        io.github.pinchan4273.reacademycraft.network.GroundshockEffect.send(player, visited);
        AbilitySyncEvents.sync(player, true);
    }
    private static int floor(double v) { return (int) Math.floor(v); }

    /** 1.12のBlocks.STONEはすべての石の変種をmetadataとして持っていた。いずれも丸石に割れる。 */
    private static boolean legacyStone(Block block) {
        return block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.POLISHED_GRANITE || block == Blocks.DIORITE
                || block == Blocks.POLISHED_DIORITE || block == Blocks.ANDESITE || block == Blocks.POLISHED_ANDESITE;
    }

    /**
     * 原作breakWithForce: 術者が壊してよい場所で、エネルギーが硬さを上回ればブロックは壊れる（耕地・液体・壊せないものは除く）。
     * 求められたときだけ、ドロップ率の確率でドロップする。
     */
    private static void breakWithForce(ServerPlayer player, BlockPos pos, boolean drop, double[] energy, float dropRate, DoubleSupplier roll) {
        var level = (ServerLevel) player.level();
        var state = level.getBlockState(pos);
        if (!AcademyConfig.canDestroy(player, pos, ID)) return;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || energy[0] < hardness || state.is(Blocks.FARMLAND) || !state.getFluidState().isEmpty()) return;
        if (state.isAir()) return;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) return;
        energy[0] -= hardness;
        if (drop && roll.getAsDouble() < dropRate) Block.dropResources(state, level, pos);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
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
        session.ticks++;
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
