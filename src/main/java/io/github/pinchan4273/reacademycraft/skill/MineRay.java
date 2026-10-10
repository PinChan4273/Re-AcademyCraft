package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作MineRaysBase（WeAthFolD、KSkun）と、数値だけが異なる3つの変種。
 *
 * 押し続ける。光線は術者が見ているブロックを1つずつ削る: 新しいブロックを見ると削りをやり直し、同じブロックを見続けると
 * 硬さがすり減って壊れ、採掘したのと同じくドロップする。オーバーロードは開始時に1回払って押している間の下限にし、CPは毎tick、
 * 離すとクールダウンがかかる。
 *
 * 変種の採掘レベルを超えるブロックは削らずに拒む。壊せないブロックは負の硬さを返し、原作はそれをただで壊すのではなく
 * 実質的に終わらない削りにする。
 *
 * ドロップが違うのはLuckだけ: 原作は他がfortuneを渡さないところで、fortune 3を渡す。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class MineRay {
    /**
     * 1つの変種の原作setRange、setHarvestLevel、setSpeed、setConsumption、setOverload、setCooldown、setExpIncrと、
     * 破壊時に渡すfortune。
     */
    public record Variant(ResourceLocation id, int level, double range, int harvestLevel,
                          float speedFrom, float speedTo, float cpFrom, float cpTo,
                          float overloadFrom, float overloadTo, float cooldownFrom, float cooldownTo,
                          float experience, int fortune) {
        public float speed(float exp) { return ArcGen.lerp(speedFrom, speedTo, exp); }
        public float cp(float exp) { return ArcGen.lerp(cpFrom, cpTo, exp); }
        public float overload(float exp) { return ArcGen.lerp(overloadFrom, overloadTo, exp); }
        public int cooldown(float exp) { return (int) ArcGen.lerp(cooldownFrom, cooldownTo, exp); }
    }

    public static final Variant BASIC = new Variant(
            ResourceLocation.fromNamespaceAndPath("academy", "mine_ray_basic"), 3,
            10, 2, .2f, .4f, 12, 7, 200, 150, 40, 20, .0005f, 0);
    public static final Variant EXPERT = new Variant(
            ResourceLocation.fromNamespaceAndPath("academy", "mine_ray_expert"), 4,
            20, 5, .5f, 1, 25, 15, 300, 200, 60, 30, .0003f, 0);
    public static final Variant LUCK = new Variant(
            ResourceLocation.fromNamespaceAndPath("academy", "mine_ray_luck"), 5,
            20, 5, .5f, 1, 50, 35, 350, 300, 60, 30, .0003f, 3);
    public static final java.util.List<Variant> VARIANTS = java.util.List.of(BASIC, EXPERT, LUCK);

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    private static final class Session {
        final Variant variant; final Level level; final int preset, slot; final float exp, overloadFloor;
        BlockPos target; float hardnessLeft = Float.MAX_VALUE; long heartbeat, visual;
        Session(Variant variant, ServerPlayer player, PlayerAbilityData data, int slot) {
            this.variant = variant; level = player.level(); preset = data.getCurrentPreset(); this.slot = slot;
            exp = data.getProficiency(variant.id()); overloadFloor = data.getOverload(); heartbeat = level.getGameTime();
        }
    }

    private MineRay() { }
    public static Variant of(ResourceLocation id) {
        return VARIANTS.stream().filter(v -> v.id().equals(id)).findFirst().orElse(null);
    }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }
    private static boolean allowed(ServerPlayer player, PlayerAbilityData data, Variant variant) {
        return player.isAlive() && data != null && !data.isReadOnly() && data.isActive()
                && AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) && data.hasLearned(variant.id())
                && player.containerMenu == player.inventoryMenu;
    }

    public static String start(Variant variant, ServerPlayer player, PlayerAbilityData data, int slot) {
        if (slot < 0 || slot >= 4 || !variant.id().equals(data.getSlot(data.getCurrentPreset(), slot))
                || !player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(variant.id()))
            return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(variant.id()) > 0) return "academy.cast.cooldown";
        if (player.containerMenu != player.inventoryMenu) return "academy.cast.unlearned";
        if (ACTIVE.containsKey(player.getUUID())) return "";
        if (!data.consume(variant.id(), 0, variant.overload(data.getProficiency(variant.id())), player.getAbilities().instabuild))
            return "academy.cast.cp";
        var session = new Session(variant, player, data, slot);
        ACTIVE.put(player.getUUID(), session);
        // コンテキストを持つ各クライアントでの原作c_start: 目からの光線と、その粒子。
        session.visual = io.github.pinchan4273.reacademycraft.network.SkillVisual.show(player, variant.id(), 0, () -> ACTIVE.get(player.getUUID()) == session);
        // 原作c_start: md.mine_<variant>_startupと、押している間のPLAYERSカテゴリ・0.3のmd.mine_loop。
        var startup = variant == BASIC ? io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MINE_BASIC_STARTUP : variant == EXPERT ? io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MINE_EXPERT_STARTUP : io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MINE_LUCK_STARTUP;
        player.level().playSound(null, player, startup.get(), net.minecraft.sounds.SoundSource.AMBIENT, .4f, 1f);
        io.github.pinchan4273.reacademycraft.network.SkillSounds.follow(player, io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_MINE_LOOP.get(), net.minecraft.sounds.SoundSource.PLAYERS, .3f, true, () -> holding(player));
        return "";
    }

    public static void renew(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) session.heartbeat = player.level().getGameTime();
    }

    /**
     * キーアップまたは中断: 原作はそれを自身のキーが始めたコンテキストにだけ渡すので、別のスロットのキーではこれは止まらない。
     * すべてのセッションを終える処理は、引き続きstop(player)を呼ぶ。
     */
    public static void stop(ServerPlayer player, int slot) {
        var session = ACTIVE.get(player.getUUID());
        if (session != null && session.slot == slot) stop(player);
    }
    public static void stop(ServerPlayer player) {
        var session = ACTIVE.remove(player.getUUID());
        if (session == null) return;
        var data = data(player);
        if (data == null || data.isReadOnly()) return;
        data.setCooldown(session.variant.id(), session.variant.cooldown(session.exp));
    }

    public static boolean holding(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static BlockPos target(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        return session == null ? null : session.target;
    }

    private static void tick(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        if (session == null) return;
        var variant = session.variant;
        var data = data(player);
        long now = player.level().getGameTime();
        if (!allowed(player, data, variant) || session.level != player.level()
                || data.getCurrentPreset() != session.preset
                || !variant.id().equals(data.getSlot(session.preset, session.slot))
                || now - session.heartbeat > 10) { stop(player); return; }
        if (data.getOverload() < session.overloadFloor) data.setOverload(session.overloadFloor);
        if (!data.consume(variant.id(), variant.cp(session.exp), 0, player.getAbilities().instabuild)) { stop(player); return; }
        var level = player.serverLevel();
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(variant.range()));
        if (!level.hasChunkAt(BlockPos.containing(end))) { session.target = null; return; }
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) { session.target = null; return; }
        var pos = hit.getBlockPos();
        if (!pos.equals(session.target)) { aim(player, level, session, pos); return; }
        session.hardnessLeft -= variant.speed(session.exp);
        // 続いている削りの各tickの原作MSG_PARTICLES（壊れたtickのものはブロックを示さない）。
        if (session.hardnessLeft > 0) { io.github.pinchan4273.reacademycraft.network.SkillVisual.move(session.visual, net.minecraft.world.phys.Vec3.atLowerCornerOf(pos), 0); return; }
        shatter(player, level, session, pos);
        data.addProficiency(variant.id(), variant.experience());
    }

    /** 新しい所を見ると削りをやり直し、採掘レベルを超えるならそのまま拒む。 */
    private static void aim(ServerPlayer player, ServerLevel level, Session session, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir() || !io.github.pinchan4273.reacademycraft.config.AcademyConfig.canDestroy(player, pos, session.variant.id())
                || harvestLevel(state) > session.variant.harvestLevel()) {
            session.target = null; return;
        }
        float hardness = state.getDestroySpeed(level, pos);
        // 原作は壊せないブロックの負の硬さを、ただで壊すのではなく終わらない削りとして扱う。
        session.hardnessLeft = hardness < 0 ? Float.MAX_VALUE : hardness;
        session.target = pos;
    }

    private static void shatter(ServerPlayer player, ServerLevel level, Session session, BlockPos pos) {
        var state = level.getBlockState(pos);
        level.playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                state.getSoundType(level, pos, player).getBreakSound(), SoundSource.BLOCKS, .5f, 1);
        // fortuneを渡すのは原作のLuckの変種だけで、3を渡す。
        var tool = net.minecraft.world.item.ItemStack.EMPTY;
        if (session.variant.fortune() > 0) {
            tool = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
            tool.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, session.variant.fortune());
        }
        net.minecraft.world.level.block.Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, tool);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        session.target = null;
    }

    /** 1.20.1では採掘レベルが道具のタグへ移ったので、ブロックが実際に必要とする段階を読む。 */
    private static int harvestLevel(net.minecraft.world.level.block.state.BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return 0;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0;
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
