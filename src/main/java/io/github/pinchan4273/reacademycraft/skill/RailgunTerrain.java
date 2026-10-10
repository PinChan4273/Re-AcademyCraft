package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

/**
 * 原作RangedRayDamageのエネルギーに限りのある地形の光線（WeAthFolD）。ブロックはForgeの破壊イベントを通して壊す。
 * 呼び出し元はサーバーだけで、クライアントが渡した位置は使わない。RangedRayを通してMeltdownerと共有する。RangedRayが断面の半径を
 * 渡し、その技能ごとの地形のスイッチが適用される。6引数のperform()とtraceLine()はRailgun専用で、変更していない。
 */
public final class RailgunTerrain {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    /** 1回の射撃が出す破壊音の数。どれだけ壊しても変わらない（原作ではなく、試遊での判断）。 */
    public static final int SOUNDS_PER_SHOT = 4;
    /** 壊したブロックごとに送る破壊の粒子。 */
    static final int PARTICLES_PER_BLOCK = 6;
    private static int soundsPlayed;
    private RailgunTerrain() { }
    public record Result(int visited, int destroyed, float remainingEnergy) { }
    /** テスト用の入口: サーバー開始以降に地形の光線が要求した破壊音の数。 */
    public static int soundsPlayed() { return soundsPlayed; }

    /**
     * 原作のエネルギーの分割と光線ごとの±5%の変動。光線1本あたり主セル51（Railgunの半径2で最大25本。より広いRangedRayは本数が多く、
     * それぞれのエネルギーの取り分は小さい）。maxDistanceSquaredは反射した者で止まる距離で、無限大は反射した者が無いことを示す。
     */
    public static Result perform(ServerPlayer player, Vec3 start, Vec3 look, float energy,
                                 double maxDistanceSquared, RandomSource random) {
        return perform(player, RangedRay.RAILGUN, start, look, energy, maxDistanceSquared, random);
    }

    public static Result perform(ServerPlayer player, RangedRay ray, Vec3 start, Vec3 look, float energy,
                                 double maxDistanceSquared, RandomSource random) {
        validate(energy, 2000, maxDistanceSquared);
        var origins = RailgunGeometry.rayOrigins(start, look, ray.radius(), random);
        if (origins.isEmpty() || energy == 0 || !ACTIVE.add(player.getUUID())) return new Result(0, 0, energy);
        try {
            var beam = new Beam(player, ray.skill(), random);
            int visited = 0, destroyed = 0; float remaining = 0;
            for (var origin : origins) {
                var result = beam.line(origin, look, RailgunGeometry.MAX_INCREMENTS,
                        energy / origins.size() * (.95f + random.nextFloat() * .1f), maxDistanceSquared);
                visited += result.visited(); destroyed += result.destroyed(); remaining += result.remainingEnergy();
            }
            beam.sounds();
            return new Result(visited, destroyed, remaining);
        } finally { ACTIVE.remove(player.getUUID()); }
    }

    /** 範囲を限った個別の光線。ワールド内での決定的な取り決めのテストにも使える。 */
    public static Result traceLine(ServerPlayer player, BlockPos origin, Vec3 look, int increments,
                                   float energy, double maxDistanceSquared, RandomSource random) {
        validate(energy, 2100, maxDistanceSquared);
        RailgunGeometry.blockRay(origin, look, increments); // ワールドを変える前に検証する。
        if (!ACTIVE.add(player.getUUID())) return new Result(0, 0, energy);
        try {
            var beam = new Beam(player, RangedRay.RAILGUN.skill(), random);
            var result = beam.line(origin, look, increments, energy, maxDistanceSquared);
            beam.sounds();
            return result;
        } finally { ACTIVE.remove(player.getUUID()); }
    }

    private static void validate(float energy, float maximum, double distance) {
        if (!Float.isFinite(energy) || energy < 0 || energy > maximum
                || Double.isNaN(distance) || distance < 0) throw new IllegalArgumentException("Invalid terrain budget");
    }

    private static final class Beam {
        final ServerPlayer player;
        final net.minecraft.resources.ResourceLocation skill;
        final RandomSource random;
        final Set<BlockPos> denied = new HashSet<>();
        /** この射撃が壊したもの（順に）。少数の破壊音に使う。 */
        final List<BlockPos> broken = new ArrayList<>();
        final List<BlockState> brokenStates = new ArrayList<>();
        int destroyed;
        Beam(ServerPlayer player, net.minecraft.resources.ResourceLocation skill, RandomSource random) {
            this.player = player; this.skill = skill; this.random = random;
        }

        Result line(BlockPos origin, Vec3 look, int increments, float energy, double maximumDistance) {
            int visited = 0, before = destroyed;
            for (var pos : RailgunGeometry.blockRay(origin, look, increments)) {
                if (energy <= 0 || pos.distSqr(origin) > maximumDistance) break;
                ++visited;
                energy = destroy(pos, energy);
                if (energy > 0 && random.nextFloat() < .05f) {
                    var side = pos.relative(Direction.values()[random.nextInt(6)]);
                    // 横の欠けも、反射した者で止まる境界を越えてはならない。
                    if (side.distSqr(origin) <= maximumDistance) energy = destroy(side, energy);
                }
            }
            return new Result(visited, destroyed - before, energy);
        }

        float destroy(BlockPos pos, float energy) {
            var level = player.serverLevel();
            if (denied.contains(pos) || !allowed(pos)) return 0;
            var state = level.getBlockState(pos);
            if (state.isAir()) return energy;
            float hardness = state.getDestroySpeed(level, pos);
            if (!Float.isFinite(hardness) || hardness < 0 || energy < hardness) return 0;
            var entity = level.getBlockEntity(pos);
            var saved = snapshot(entity);
            boolean canceled = MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
            // 保護のコールバックは権限・ブロック・そのインベントリを変えることがある。
            // 古い状態への承認を、置き換わったものやコンテナの編集へ決して適用しない。
            if (canceled || !allowed(pos) || !state.equals(level.getBlockState(pos))
                    || entity != level.getBlockEntity(pos) || !Objects.equals(saved, snapshot(entity))) {
                denied.add(pos.immutable()); return 0;
            }
            // バニラはBlockEntityの中身とonRemoveをちょうど1回扱う。5%の確率は通常のブロックの戦利品のためのもので、
            // コンテナの中身を黙って消すためではない。
            if (!quietlyDestroy(level, pos, state, random.nextFloat() < .05f)) return 0;
            ++destroyed;
            broken.add(pos.immutable()); brokenStates.add(state);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(pos),
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, PARTICLES_PER_BLOCK, .25, .25, .25, 0);
            return energy - hardness;
        }

        /**
         * 1.20.1のLevel.destroyBlock(pos, drop, player)と同じ処理（ドロップ、同じフラグでの液体への置き換え、BLOCK_DESTROYのゲームイベント）
         * から、levelEvent 2001だけを除いたもの。それはブロックごとに破壊音（と粒子）を出し、一度に数百も出ると射撃自体の音がかき消された。
         * 粒子は上で送り、音はsounds()で少数だけ出す。
         */
        boolean quietlyDestroy(ServerLevel level, BlockPos pos, BlockState state, boolean drop) {
            var fluid = level.getFluidState(pos);
            if (drop) Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null, player, ItemStack.EMPTY);
            boolean placed = level.setBlock(pos, fluid.createLegacyBlock(), Block.UPDATE_ALL, 512);
            if (placed) level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
            return placed;
        }

        /**
         * 射撃の破壊音: 最大SOUNDS_PER_SHOTを、壊したものに均等に散らす。それぞれブロック自身の破壊音を、バニラの(volume + 1) / 2と
         * ピッチ0.8で、少し小さく鳴らす。
         */
        void sounds() {
            int count = Math.min(SOUNDS_PER_SHOT, broken.size());
            var level = player.serverLevel();
            for (int i = 0; i < count; i++) {
                int at = (int) ((long) i * broken.size() / count);
                var pos = broken.get(at); var state = brokenStates.get(at);
                var type = state.getSoundType(level, pos, player);
                level.playSound(null, pos, type.getBreakSound(), SoundSource.BLOCKS,
                        (type.getVolume() + 1) / 2 * .8f, type.getPitch() * .8f);
            }
            soundsPlayed += count;
        }

        boolean allowed(BlockPos pos) {
            var level = player.serverLevel();
            return io.github.pinchan4273.reacademycraft.config.AcademyConfig.canDestroy(level, skill)
                    && player.isAlive() && !player.isSpectator() && player.mayBuild()
                    && !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                    && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                    && !level.captureBlockSnapshots && !level.restoringBlockSnapshots && level.capturedBlockSnapshots.isEmpty();
        }
    }
    private static CompoundTag snapshot(BlockEntity entity) { return entity == null ? null : entity.saveWithFullMetadata(); }
}
