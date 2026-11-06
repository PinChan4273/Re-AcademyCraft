package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActorKind;
import io.github.pinchan4273.reacademycraft.api.TargetRelation;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import io.github.pinchan4273.reacademycraft.skill.SkillDamageSource;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/** 共通の安全のサービスの実装（1.4.0のS5、内部の実装。公開の説明は{@code api.mc.AcademySafety}）。 */
public final class SafetyService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private SafetyService() { }

    /** 帰属先が今ログインしている本物のプレイヤーか（試験だけが差し替える）。 */
    private static volatile Predicate<ServerPlayer> online = SafetyService::loggedIn;
    private static boolean loggedIn(ServerPlayer player) {
        var server = player.level().getServer();
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player && !player.hasDisconnected();
    }
    /** 試験のため、ログインの判定を差し替える（nullで元へ戻す）。治具のプレイヤーはログインしないので、本物のプレイヤーの代わりにする。 */
    public static void onlineForTest(@Nullable Predicate<ServerPlayer> check) { online = check == null ? SafetyService::loggedIn : check; }

    private static net.minecraft.resources.Identifier loc(AbilityId id) { return RegistryViews.location(id); }
    /** 公開APIのベクトル反射の状態。 */
    public static io.github.pinchan4273.reacademycraft.api.ReflectionState reflection(@javax.annotation.Nullable net.minecraft.world.entity.LivingEntity target) {
        if (target == null || target.level().isClientSide()) return io.github.pinchan4273.reacademycraft.api.ReflectionState.UNKNOWN;
        var server = target.level().getServer();
        if (server == null || !server.isSameThread()) return io.github.pinchan4273.reacademycraft.api.ReflectionState.UNKNOWN;
        if (!(target instanceof net.minecraft.server.level.ServerPlayer player)) return io.github.pinchan4273.reacademycraft.api.ReflectionState.NOT_REFLECTING;
        return io.github.pinchan4273.reacademycraft.skill.VecReflection.active(player) ? io.github.pinchan4273.reacademycraft.api.ReflectionState.REFLECTING
                : io.github.pinchan4273.reacademycraft.api.ReflectionState.NOT_REFLECTING;
    }
    /**
     * 公開APIのダメージの技能のID。技能の損傷の源（{@link io.github.pinchan4273.reacademycraft.skill.SkillDamageSource}。未元物質の刺突・光線・空気砲・甲虫・
     * 天界の接触もこれ）と、天界発現の領域の状態異常（燃焼・毒・衰弱。バニラの損傷の種類で与えるので、源の型で見分ける。1.5.1から）。
     */
    public static @javax.annotation.Nullable AbilityId skillOf(@javax.annotation.Nullable net.minecraft.world.damagesource.DamageSource source) {
        if (source instanceof io.github.pinchan4273.reacademycraft.skill.SkillDamageSource skill) return AbilityId.tryParse(skill.skill().toString());
        if (source instanceof io.github.pinchan4273.reacademycraft.skill.HeavenPhenomena.StatusSource status) return AbilityId.tryParse(status.skill().toString());
        return null;
    }
    /** 光線として当てる組み込みの技能。1.5.1で未元光線（{@code academy:dark_matter_ray}）を足した。 */
    private static final java.util.Set<AbilityId> BEAM_SKILLS = java.util.Set.of(AbilityId.academy("railgun"), AbilityId.academy("meltdowner"),
            AbilityId.academy("ray_barrage"), AbilityId.academy("electron_bomb"), AbilityId.academy("scatter_bomb"), AbilityId.academy("electron_missile"),
            AbilityId.academy("dark_matter_ray"));
    /** 公開APIの、光線として当てる組み込みの技能のダメージか。 */
    public static boolean isBeam(@javax.annotation.Nullable net.minecraft.world.damagesource.DamageSource source) {
        var skill = skillOf(source);
        return skill != null && BEAM_SKILLS.contains(skill);
    }

    private static void requireServerThread(AbilityActor actor) {
        var server = actor.level().getServer();
        if (server != null && !server.isSameThread()) throw new IllegalStateException("The safety service must run on the server thread");
    }

    public static @Nullable ServerPlayer verifiedPlayer(AbilityActor actor) {
        var player = actor.attributedPlayer();
        if (player == null || player instanceof FakePlayer || player.isSpectator() || !online.test(player)) return null;
        return player;
    }

    public static TargetRelation relation(AbilityActor actor, Entity target) {
        if (target == null || !target.isAlive() || target.isSpectator() || target.level() != actor.level()) return TargetRelation.NONE;
        var player = verifiedPlayer(actor);
        if (target == actor.entity() || (player != null && target == player)) return TargetRelation.SELF;
        if (target instanceof Player other) return player != null && player.isAlliedTo(other) ? TargetRelation.ALLY : TargetRelation.PLAYER;
        if (!(target instanceof LivingEntity)) return TargetRelation.NONE;
        if (player != null && io.github.pinchan4273.reacademycraft.skill.ElectronMissileTargeting.ally(player, target)) return TargetRelation.ALLY;
        if (target instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == actor.entity() || mob.getTarget() == player)) return TargetRelation.HOSTILE;
        if (target instanceof NeutralMob) return TargetRelation.NEUTRAL;
        if (target instanceof Enemy) return TargetRelation.HOSTILE;
        return TargetRelation.FRIENDLY;
    }

    public static boolean mayAttack(AbilityActor actor, Entity target, AbilityId skill) {
        requireServerThread(actor);
        if (target == null || !target.isAlive() || target.isSpectator() || target.level() != actor.level() || target == actor.entity()) return false;
        var player = verifiedPlayer(actor);
        if (target instanceof Player other) {
            // 司令室11の裁定D9: 帰属先を確かめられなければ、プレイヤーへの攻撃は断る。確かめられれば、その人の規則をすべて当てる。
            return player != null && other != player && AcademyConfig.ATTACK_PLAYERS.get()
                    && player.level().isPvpAllowed() && player.canHarmPlayer(other);
        }
        // 絵と額縁は地形と同じく扱う（裁定S5-01）。
        if (target instanceof Painting || target instanceof ItemFrame) return mayModifyBlock(actor, target.blockPosition(), skill);
        return true;
    }

    public static boolean attack(AbilityActor actor, Entity target, AbilityId skill, float damage) {
        if (!Float.isFinite(damage) || damage <= 0 || !mayAttack(actor, target, skill)) return false;
        float scaled = (float) (damage * AcademyConfig.DAMAGE_SCALE.get() * AcademyConfig.skillDamageScale(loc(skill)));
        if (!Float.isFinite(scaled) || scaled <= 0) return false;
        var player = verifiedPlayer(actor);
        var holder = actor.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SkillDamageSource.TYPE);
        Entity causing = player != null ? player : actor.entity();
        return target.hurtServer((net.minecraft.server.level.ServerLevel) target.level(), new SkillDamageSource(holder, actor.entity(), causing, loc(skill)), scaled);
    }

    public static boolean mayModifyBlock(AbilityActor actor, BlockPos pos, AbilityId skill) {
        requireServerThread(actor);
        var player = verifiedPlayer(actor);
        ServerLevel level = actor.level();
        if (player == null || pos == null || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        // 機械とAIは、その場の帰属先の本人の操作ではないので、ゲームルールmobGriefingにも従う（装備は着けた人の操作として扱う）。
        if ((actor.kind() == ActorKind.MACHINE || actor.kind() == ActorKind.AI) && !level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;
        return AcademyConfig.canDestroy(level, loc(skill)) && player.mayBuild() && level.mayInteract(player, pos);
    }

    public static boolean breakBlock(AbilityActor actor, BlockPos pos, AbilityId skill, boolean drops) {
        ServerLevel level = actor.level();
        // 爆発と同じ1ブロックの判定（地形の変更の条件と、帰属先としてのNeoForgeの破壊のイベント）。
        if (!mayBreak(actor, level, pos, skill)) return false;
        return level.destroyBlock(pos, drops, actor.entity());
    }

    public static boolean placeBlock(AbilityActor actor, BlockPos pos, BlockState state, AbilityId skill) {
        if (state == null || !mayModifyBlock(actor, pos, skill)) return false;
        ServerLevel level = actor.level();
        if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) return false;
        return placeChecked(actor, level, pos, state);
    }

    public static boolean replaceBlock(AbilityActor actor, BlockPos pos, BlockState state, AbilityId skill) {
        if (state == null || !mayModifyBlock(actor, pos, skill)) return false;
        ServerLevel level = actor.level();
        var old = level.getBlockState(pos);
        if (old.isAir() || old.getDestroySpeed(level, pos) < 0 || holdsBlockEntity(level, pos)) return false;
        if (NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(level, pos, old, verifiedPlayer(actor))).isCanceled()) return false;
        return placeChecked(actor, level, pos, state);
    }
    /** 置いてからNeoForgeの設置のイベントを出し、取り消されたら元へ戻す（NeoForgeのアイテムの設置と同じ流れ）。 */
    private static boolean placeChecked(AbilityActor actor, ServerLevel level, BlockPos pos, BlockState state) {
        // BlockEntityを持つブロックは変えない。中身がsetBlockで落ちてから、取り消しのNBTの復元で戻ると、アイテムが増える（C200-R01）。
        // 破壊のイベントのリスナーが置いた場合もあるので、変更の直前に確かめる。
        if (holdsBlockEntity(level, pos)) return false;
        var snapshot = net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3)) return false;
        if (EventHooks.onBlockPlace(verifiedPlayer(actor), snapshot, net.minecraft.core.Direction.UP)) {
            restoreCancelledPlacement(level, pos, snapshot);
            return false;
        }
        return true;
    }
    private static boolean holdsBlockEntity(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).hasBlockEntity() || level.getBlockEntity(pos) != null;
    }
    /**
     * 取り消された設置の位置を、写しの状態とBlockEntityのNBTへ戻す（フラグ2で、周りのブロックへ知らせない）。戻せなければ警告を残してfalseを返す。
     * <p>
     * NeoForge 26.2.0.93のBlockSnapshot.restoreToLocationは、setBlockがfalseならNBTを戻さずにfalseを返す（21.1.259と同じ）。setBlockは、
     * 取り消しのリスナーが先に同じ状態へ戻していると（LevelChunk.setBlockStateがnull）falseになる。Forge 47.4.10のrestore(true, false)は
     * setBlockの結果を見ずにNBTを読ませるので、この場合だけ、ここで同じ結果になるようにNBTを戻し、クライアントへ知らせる。
     * Forgeの本文とそろえたのは、この場合のNBTと通知だけ（1.21.1の公開版のed7dd619と同じ）。
     */
    public static boolean restoreCancelledPlacement(ServerLevel level, BlockPos pos, net.neoforged.neoforge.common.util.BlockSnapshot snapshot) {
        var wanted = snapshot.getState();
        boolean wrote = snapshot.restore(net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        boolean sameState = level.getBlockState(pos) == wanted;
        if (!wrote && sameState) {
            snapshot.restoreBlockEntity(level, pos);
            level.sendBlockUpdated(pos, wanted, wanted, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        if (sameState && (snapshot.getTag() == null || level.getBlockEntity(pos) != null)) return true;
        LOGGER.warn("Could not restore the cancelled placement at {} in {}: wanted {}, found {}, saved block entity data: {}",
                pos, level.dimension().identifier(), wanted, level.getBlockState(pos), snapshot.getTag() != null);
        return false;
    }

    /**
     * そのブロックを、その技能の地形の変更として壊してよいか: {@link #mayModifyBlock}に加え、確かめた帰属先のプレイヤーとしての
     * NeoForgeの破壊のイベント（BreakEvent）が取り消されないこと（{@link #breakBlock}と同じ判定。WORK-147 F141-S5-F01: 爆発は
     * このイベントを出しておらず、保護が断ったブロックも壊せた）。空気と壊せないブロックは壊さない。
     */
    private static boolean mayBreak(AbilityActor actor, ServerLevel level, BlockPos pos, AbilityId skill) {
        if (!mayModifyBlock(actor, pos, skill)) return false;
        var state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        return !NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(level, pos, state, verifiedPlayer(actor))).isCanceled();
    }

    /**
     * 爆発させる（1.4.0のS5。WORK-148 F141-S5-F01-R1で作り直した）。
     * <p>
     * Minecraft 1.21.1の爆発の手順（{@code Explosion.explode}: 光線で壊すブロックを選ぶ → NeoForgeのDetonateのイベント → 実体へのダメージと
     * 押し出し）を、ここでなぞる。こうすると、Detonateのイベントの配送がすべて終わった後で（リスナーの順番に頼らずに）、実際に壊すブロックと
     * 実際に当てる相手の最終の一覧を、その時点の条件で検査してから効果を当てられる。
     * <ul>
     * <li>ブロック: 光線の選択では{@link #mayModifyBlock}で絞り込むだけにし（破壊のイベントは出さない）、最終の一覧の1つずつに、その時点の
     * {@link #mayBreak}（地形の条件と、確かめた帰属先としてのNeoForgeの破壊のイベント）を1回だけ当てる。前の判定の結果は使い回さない。</li>
     * <li>実体: 最終の一覧の1つずつに、その時点の{@link #mayAttack}を当て、許されない相手にはダメージも押し出しも当てない。</li>
     * </ul>
     * 壊すのは{@code finalizeExplosion}で、検査を通した一覧だけ。NeoForgeの爆発の開始のイベントが取り消せば、何もせずfalse。
     */
    public static boolean explode(AbilityActor actor, Vec3 at, float power, AbilityId skill) {
        requireServerThread(actor);
        ServerLevel level = actor.level();
        if (at == null || !Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z) || !Float.isFinite(power) || power <= 0 || power > 16
                || !level.isLoaded(BlockPos.containing(at))) return false;
        var calculator = new ExplosionDamageCalculator() {
            @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter getter, BlockPos pos, BlockState state, float strength) {
                return mayModifyBlock(actor, pos, skill) && super.shouldBlockExplode(explosion, getter, pos, state, strength);
            }
        };
        var player = verifiedPlayer(actor);
        var source = level.damageSources().explosion(actor.entity(), player != null ? player : actor.entity());
        var explosion = new net.minecraft.world.level.ServerExplosion(level, actor.entity(), source, calculator, at, power, false, Explosion.BlockInteraction.DESTROY);
        if (EventHooks.onExplosionStart(level, explosion)) return false;
        level.gameEvent(actor.entity(), net.minecraft.world.level.gameevent.GameEvent.EXPLODE, at);
        // 1. 光線で壊すブロックを選ぶ（Explosion.explodeと同じ: 16×16×16の格子の外側の面から、強さが尽きるまで0.3ずつ進む）。
        var chosen = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) for (int k = 0; k < 16; k++) {
            if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
            double dx = i / 15f * 2 - 1, dy = j / 15f * 2 - 1, dz = k / 15f * 2 - 1, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length; dy /= length; dz /= length;
            float strength = power * (.7f + level.getRandom().nextFloat() * .6f);
            double x = at.x, y = at.y, z = at.z;
            for (; strength > 0; strength -= .22500001f) {
                var pos = BlockPos.containing(x, y, z);
                if (!level.isInWorldBounds(pos)) break;
                var state = level.getBlockState(pos);
                var resistance = calculator.getBlockExplosionResistance(explosion, level, pos, state, level.getFluidState(pos));
                if (resistance.isPresent()) strength -= (resistance.get() + .3f) * .3f;
                if (strength > 0 && calculator.shouldBlockExplode(explosion, level, pos, state, strength)) chosen.add(pos);
                x += dx * .3; y += dy * .3; z += dz * .3;
            }
        }
        var blocks = new java.util.ArrayList<BlockPos>(chosen);
        // 2. 当てる相手を集め、NeoForgeのDetonateのイベントを出す（保護modが一覧を変えられる）。
        float diameter = power * 2;
        var area = new net.minecraft.world.phys.AABB(at.x - diameter - 1, at.y - diameter - 1, at.z - diameter - 1, at.x + diameter + 1, at.y + diameter + 1, at.z + diameter + 1);
        var entities = level.getEntities(actor.entity(), area);
        EventHooks.onExplosionDetonate(level, explosion, entities, blocks);
        // 3. イベントの配送の後の最終の一覧を、その時点の条件で検査する（WORK-148: 前の判定の使い回しと、後から足された位置の抜けを無くす）。
        blocks.removeIf(pos -> !mayBreak(actor, level, pos, skill));
        entities.removeIf(entity -> !mayAttack(actor, entity, skill));
        // 4. 実体へのダメージと押し出し（Minecraft 1.21.1のExplosion.explodeと同じ式。ダメージと押し出しの倍率は1.21からcalculatorが持つ）。
        for (var entity : entities) {
            if (entity.ignoreExplosion(explosion)) continue;
            double distance = Math.sqrt(entity.distanceToSqr(at)) / diameter;
            if (distance > 1) continue;
            double dx = entity.getX() - at.x, dy = (entity instanceof net.minecraft.world.entity.item.PrimedTnt ? entity.getY() : entity.getEyeY()) - at.y,
                    dz = entity.getZ() - at.z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0) continue;
            dx /= length; dy /= length; dz /= length;
            boolean damages = calculator.shouldDamageEntity(explosion, entity);
            float knockback = calculator.getKnockbackMultiplier(entity), exposure = !damages && knockback == 0 ? 0 : net.minecraft.world.level.ServerExplosion.getSeenPercent(at, entity);
            if (damages) entity.hurtServer(level, source, calculator.getEntityDamageAmount(explosion, entity, exposure));
            double impact = (1 - distance) * exposure * knockback;
            double push = entity instanceof LivingEntity living
                    ? impact * (1 - living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)) : impact;
            var pushed = net.neoforged.neoforge.event.EventHooks.getExplosionKnockback(level, explosion, entity, new Vec3(dx * push, dy * push, dz * push), blocks);
            entity.push(pushed);
            if (entity instanceof Player hit && !hit.isSpectator() && (!hit.isCreative() || !hit.getAbilities().flying))
                explosion.getHitPlayers().put(hit, pushed);
            entity.onExplosionHit(actor.entity());
        }
        // 5. 検査を通したブロックだけを壊す（落とし物・音・粒子はMinecraftの処理）。
        breakExploded(level, explosion, blocks);
        for (var viewer : level.players())
            if (viewer.distanceToSqr(at.x, at.y, at.z) < 4096)
                sendExplosion(viewer, explosion, at, power, blocks.size());
        return true;
    }

    /** Minecraft 26.2 の ServerExplosion#interactWithBlocks と同じ（1.21.1 の finalizeExplosion のブロックの部分。本体は private）。 */
    private static void breakExploded(ServerLevel level, net.minecraft.world.level.ServerExplosion explosion, java.util.List<BlockPos> blocks) {
        record Drop(BlockPos pos, net.minecraft.world.item.ItemStack[] stack) { }
        var drops = new java.util.ArrayList<Drop>();
        net.minecraft.util.Util.shuffle(blocks, level.getRandom());
        for (BlockPos pos : blocks) {
            level.getBlockState(pos).onExplosionHit(level, pos, explosion, (stack, at) -> {
                for (var drop : drops) {
                    if (net.minecraft.world.entity.item.ItemEntity.areMergable(drop.stack()[0], stack)) drop.stack()[0] = net.minecraft.world.entity.item.ItemEntity.merge(drop.stack()[0], stack, 16);
                    if (stack.isEmpty()) return;
                }
                drops.add(new Drop(at, new net.minecraft.world.item.ItemStack[]{stack}));
            });
        }
        for (var drop : drops) net.minecraft.world.level.block.Block.popResource(level, drop.pos(), drop.stack()[0]);
    }

    /** Minecraft 26.2 の ServerLevel#explode の知らせと同じ（粒子と音は Level#explode の既定）。 */
    public static void sendExplosion(net.minecraft.server.level.ServerPlayer viewer, net.minecraft.world.level.ServerExplosion explosion, Vec3 at, float power, int blockCount) {
        viewer.connection.send(new ClientboundExplodePacket(at, power, blockCount, java.util.Optional.ofNullable(explosion.getHitPlayers().get(viewer)),
                explosion.isSmall() ? net.minecraft.core.particles.ParticleTypes.EXPLOSION : net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, EXPLOSION_BLOCK_PARTICLES));
    }
    private static final net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> EXPLOSION_BLOCK_PARTICLES =
            net.minecraft.util.random.WeightedList.<net.minecraft.core.particles.ExplosionParticleInfo>builder()
                    .add(new net.minecraft.core.particles.ExplosionParticleInfo(net.minecraft.core.particles.ParticleTypes.POOF, .5f, 1f))
                    .add(new net.minecraft.core.particles.ExplosionParticleInfo(net.minecraft.core.particles.ParticleTypes.SMOKE, 1f, 1f))
                    .build();

    public static @Nullable Vec3 checkTeleport(AbilityActor actor, Entity mover, Vec3 to, AbilityId skill) {
        requireServerThread(actor);
        ServerLevel level = actor.level();
        if (mover == null || !mover.isAlive() || mover.level() != level) return null;
        if (!destination(level, to)) return null;
        // 主体の実体でない相手を動かすのは、攻撃と同じ判定（プレイヤーなら帰属先とPvPが要る）。
        if (mover != actor.entity() && !mayAttack(actor, mover, skill)) return null;
        var event = new net.neoforged.neoforge.event.entity.EntityTeleportEvent(mover, level, to.x, to.y, to.z);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return null;
        // リスナーが行き先を変えたときも、実際に使う行き先を同じ条件で検査し直す（WORK-147 F141-S5-F02: 変えた先を検査せずに使っていた）。
        var target = event.getTarget();
        return destination(level, target) ? target : null;
    }
    /** 行き先の条件: 有限の座標、読み込まれている、世界の境界の中、高さの中。 */
    private static boolean destination(ServerLevel level, @Nullable Vec3 to) {
        if (to == null || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) return false;
        if (to.y < level.getMinY() || to.y > (level.getMaxY() + 1)) return false;
        var target = BlockPos.containing(to);
        return level.getWorldBorder().isWithinBounds(target) && level.isLoaded(target);
    }

    public static boolean teleport(AbilityActor actor, Entity mover, Vec3 to, AbilityId skill) {
        var target = checkTeleport(actor, mover, to, skill);
        if (target == null) return false;
        var level = mover.level();
        if (mover.isPassenger()) mover.stopRiding();
        io.github.pinchan4273.reacademycraft.skill.TPSkillHelper.move(mover, target);
        mover.fallDistance = 0;
        // 事後: 実際にその世界のその位置へ移ったか（別のリスナーや仕組みが動かした・断った場合はfalse）。
        return mover.level() == level && mover.position().distanceToSqr(target) < 1e-6;
    }

    public static boolean interfered(AbilityActor actor) {
        // 装備は、着けた人がクリエイティブなら自然能力者と同じく妨害を受けない。機械とAIはその場所で判定する。
        var player = actor.kind() == ActorKind.EQUIPMENT ? actor.attributedPlayer() : null;
        if (player != null && player.getAbilities().instabuild) return false;
        return io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity.covers(actor.level(), actor.position());
    }
}
