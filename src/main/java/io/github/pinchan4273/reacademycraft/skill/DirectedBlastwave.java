package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
 * 原作DirectedBlastwave: ベクトル操作のレベル3の爆風。
 *
 * 押し続けてから、Directed Shockと同じ窓（6tickより長く50tickより短い）の中で離す。lerp(160, 200, exp)のCPとlerp(50, 30, exp)の
 * オーバーロードを払い（払えなければ何も起きない）、クールダウンlerp(80, 50, exp)を設定する。爆風は視線に沿って4ブロック先に
 * 落ちる: 生き物の頭、当たったブロックの角、または届く範囲の終点。そこから3ブロック以内の術者以外のすべてのエンティティは
 * （生き物かどうかを問わず）lerp(10, 25, exp)のダメージを受け、0.1持ち上げられ、術者から離れる方向へ1tickあたり0.24で飛ばされる。
 * 周囲のブロック（中心は必ず、残りは半径sqrt(6)の球の中で確率lerp(0.5, 0.8, exp)）は、硬さが2.9、25、55以下
 * （25%未満、50%未満、それ以上）なら壊れ、確率lerp(0.4, 0.9, exp)でドロップする。熟練度がちょうど最大のときは、それぞれ
 * そのブロック自身としてドロップする。経験値はエンティティに当たれば0.0025、それ以外は0.0012。
 *
 * 書かれたとおりに移植している: 原作のノックバックは同じtickに単純な押し出しで上書きされるので、押し出しと持ち上げだけが残る。
 * ブロックのループは中心 - 3から中心 + 2までで、上側が1つ短い。中心は各座標のMath.roundで、ブロックに当たった場合は当たった点ではなく
 * ブロックの角になる。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class DirectedBlastwave {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "dir_blast");
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Level level; final int preset, slot; final float exp; long heartbeat; int ticks;
        Session(ServerPlayer player, PlayerAbilityData data, int slot) {
            level = player.level(); preset = data.getCurrentPreset(); this.slot = slot; heartbeat = level.getGameTime();
            exp = data.getProficiency(ID);
        }
    }

    private DirectedBlastwave() { }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.VECMANIP.id().equals(data.getAbility()) && data.hasLearned(ID)
                && player.containerMenu == player.inventoryMenu;
    }
    public static float consumption(float exp) { return ArcGen.lerp(160, 200, exp); }
    public static float overload(float exp) { return ArcGen.lerp(50, 30, exp); }
    public static float damage(float exp) { return ArcGen.lerp(10, 25, exp); }
    public static float breakChance(float exp) { return ArcGen.lerp(.5f, .8f, exp); }
    public static float dropRate(float exp) { return ArcGen.lerp(.4f, .9f, exp); }
    public static int cooldown(float exp) { return (int) ArcGen.lerp(80, 50, exp); }
    public static float breakHardness(float exp) { return exp < .25f ? 2.9f : exp < .5f ? 25 : 55; }

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
        release(player, slot, random::nextFloat);
    }
    /** 与えた乱数源から原作の確率を引いて、離す。 */
    public static void release(ServerPlayer player, int slot, DoubleSupplier roll) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null || session.slot != slot) return;
        ACTIVE.remove(player.getUUID());
        var data = data(player);
        if (!allowed(player, data) || session.level != player.level()) return;
        if (session.ticks > DirectedShock.MIN_TICKS && session.ticks < DirectedShock.MAX_ACCEPTED_TICKS) perform(player, data, session.exp, roll);
    }
    public static void cancel(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) ACTIVE.remove(player.getUUID());
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); }
    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }

    /** 原作の爆風の位置: 生き物の頭、ブロックの角、または届く範囲の終点。 */
    public static Vec3 position(ServerPlayer player) {
        var trace = TPSkillHelper.traceLiving(player, 4, TPSkillHelper::living);
        if (trace.entity() != null) return trace.entity().getEyePosition();
        if (trace.block() != null) return Vec3.atLowerCornerOf(trace.block().getBlockPos());
        return trace.position();
    }

    /** 原作s_perform。 */
    private static void perform(ServerPlayer player, PlayerAbilityData data, float exp, DoubleSupplier roll) {
        if (!data.consume(ID, consumption(exp), overload(exp), player.getAbilities().instabuild)) return;
        var level = (ServerLevel) player.level();
        var position = position(player);
        data.setCooldown(ID, cooldown(exp));
        // 原作effectAt: 爆風の位置で。
        level.playSound(null, position.x, position.y, position.z, io.github.pinchan4273.reacademycraft.world.AcademySounds.VM_DIRECTED_BLAST.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        // 原作effectAt: 術者の目から爆風までの70%の位置に、術者の視線から±20度・±10度ずれた向きのWaveEffect。輪はrangei(2, 3)で、
        // 常に2になる。
        var eyes = player.getEyePosition();
        io.github.pinchan4273.reacademycraft.network.VmWave.send(player, new io.github.pinchan4273.reacademycraft.network.VmWave(level.dimension().location(),
                eyes.lerp(position, .7), player.getYHeadRot(), player.getXRot(), 20, 10, 2, 1));
        boolean effective = false;
        for (var entity : level.getEntities(player, new AABB(position, position).inflate(3),
                e -> e.position().distanceToSqr(position) <= 9)) {
            SkillCombat.attack(player, entity, ID, damage(exp));
            // 原作のノックバックは0.1持ち上げて投げるが、その動きはすぐ後で置き換えられる。
            entity.setPos(entity.getX(), entity.getY() + .1, entity.getZ());
            entity.setDeltaMovement(entity.position().subtract(player.position()).normalize().scale(.24));
            entity.hurtMarked = true;
            effective = true;
        }
        int x = (int) Math.round(position.x), y = (int) Math.round(position.y), z = (int) Math.round(position.z);
        float hardnessLimit = breakHardness(exp), chance = breakChance(exp), drop = dropRate(exp);
        for (int i = x - 3; i < x + 3; i++) for (int j = y - 3; j < y + 3; j++) for (int k = z - 3; k < z + 3; k++) {
            int dx = i - x, dy = j - y, dz = k - z, distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 6 || (distSq != 0 && roll.getAsDouble() >= chance)) continue;
            var pos = new BlockPos(i, j, k);
            var state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > hardnessLimit || state.isAir() || !mayBreak(player, pos)) continue;
            if (exp == 1) level.addFreshEntity(new ItemEntity(level, i, j, k, new ItemStack(state.getBlock())));
            else if (roll.getAsDouble() < drop) Block.dropResources(state, level, pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        data.addProficiency(ID, effective ? .0025f : .0012f);
        AbilitySyncEvents.sync(player, true);
    }
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
                || now - session.heartbeat > 10
                || ++session.ticks >= DirectedShock.MAX_TOLERANT_TICKS) stop(player);
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
