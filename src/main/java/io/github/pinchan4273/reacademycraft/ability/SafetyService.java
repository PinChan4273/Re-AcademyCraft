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
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

/** 共通の安全のサービスの実装（1.4.0のS5、内部の実装。公開の説明は{@code api.mc.AcademySafety}）。 */
public final class SafetyService {
    private SafetyService() { }

    /** 帰属先が今ログインしている本物のプレイヤーか（試験だけが差し替える）。 */
    private static volatile Predicate<ServerPlayer> online = SafetyService::loggedIn;
    private static boolean loggedIn(ServerPlayer player) {
        var server = player.getServer();
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player && !player.hasDisconnected();
    }
    /** 試験のため、ログインの判定を差し替える（nullで元へ戻す）。治具のプレイヤーはログインしないので、本物のプレイヤーの代わりにする。 */
    public static void onlineForTest(@Nullable Predicate<ServerPlayer> check) { online = check == null ? SafetyService::loggedIn : check; }

    private static net.minecraft.resources.ResourceLocation loc(AbilityId id) { return RegistryViews.location(id); }
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
                    && player.serverLevel().getServer().isPvpAllowed() && player.canHarmPlayer(other);
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
        var holder = actor.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SkillDamageSource.TYPE);
        Entity causing = player != null ? player : actor.entity();
        return target.hurt(new SkillDamageSource(holder, actor.entity(), causing, loc(skill)), scaled);
    }

    public static boolean mayModifyBlock(AbilityActor actor, BlockPos pos, AbilityId skill) {
        requireServerThread(actor);
        var player = verifiedPlayer(actor);
        ServerLevel level = actor.level();
        if (player == null || pos == null || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        // 機械とAIは、その場の帰属先の本人の操作ではないので、ゲームルールmobGriefingにも従う（装備は着けた人の操作として扱う）。
        if ((actor.kind() == ActorKind.MACHINE || actor.kind() == ActorKind.AI) && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        return AcademyConfig.canDestroy(level, loc(skill)) && player.mayBuild() && level.mayInteract(player, pos);
    }

    public static boolean breakBlock(AbilityActor actor, BlockPos pos, AbilityId skill, boolean drops) {
        ServerLevel level = actor.level();
        // 爆発と同じ1ブロックの判定（地形の変更の条件と、帰属先としてのForgeの破壊のイベント）。
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
        if (old.isAir() || old.getDestroySpeed(level, pos) < 0) return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, old, verifiedPlayer(actor)))) return false;
        return placeChecked(actor, level, pos, state);
    }
    /** 置いてからForgeの設置のイベントを出し、取り消されたら元へ戻す（Forgeのアイテムの設置と同じ流れ）。 */
    private static boolean placeChecked(AbilityActor actor, ServerLevel level, BlockPos pos, BlockState state) {
        var snapshot = net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3)) return false;
        if (ForgeEventFactory.onBlockPlace(verifiedPlayer(actor), snapshot, net.minecraft.core.Direction.UP)) {
            snapshot.restore(true, false);
            return false;
        }
        return true;
    }

    /**
     * そのブロックを、その技能の地形の変更として壊してよいか: {@link #mayModifyBlock}に加え、確かめた帰属先のプレイヤーとしての
     * Forgeの破壊のイベント（BreakEvent）が取り消されないこと（{@link #breakBlock}と同じ判定。WORK-147 F141-S5-F01: 爆発は
     * このイベントを出しておらず、保護が断ったブロックも壊せた）。空気と壊せないブロックは壊さない。
     */
    private static boolean mayBreak(AbilityActor actor, ServerLevel level, BlockPos pos, AbilityId skill) {
        if (!mayModifyBlock(actor, pos, skill)) return false;
        var state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, verifiedPlayer(actor)));
    }

    /**
     * 爆発させる（1.4.0のS5。WORK-148 F141-S5-F01-R1で作り直した）。
     * <p>
     * Minecraft 1.20.1の爆発の手順（{@code Explosion.explode}: 光線で壊すブロックを選ぶ → ForgeのDetonateのイベント → 実体へのダメージと
     * 押し出し）を、ここでなぞる。こうすると、Detonateのイベントの配送がすべて終わった後で（リスナーの順番に頼らずに）、実際に壊すブロックと
     * 実際に当てる相手の最終の一覧を、その時点の条件で検査してから効果を当てられる。
     * <ul>
     * <li>ブロック: 光線の選択では{@link #mayModifyBlock}で絞り込むだけにし（破壊のイベントは出さない）、最終の一覧の1つずつに、その時点の
     * {@link #mayBreak}（地形の条件と、確かめた帰属先としてのForgeの破壊のイベント）を1回だけ当てる。前の判定の結果は使い回さない。</li>
     * <li>実体: 最終の一覧の1つずつに、その時点の{@link #mayAttack}を当て、許されない相手にはダメージも押し出しも当てない。</li>
     * </ul>
     * 壊すのは{@code finalizeExplosion}で、検査を通した一覧だけ。Forgeの爆発の開始のイベントが取り消せば、何もせずfalse。
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
        var explosion = new Explosion(level, actor.entity(), source, calculator, at.x, at.y, at.z, power, false, Explosion.BlockInteraction.DESTROY);
        if (ForgeEventFactory.onExplosionStart(level, explosion)) return false;
        level.gameEvent(actor.entity(), net.minecraft.world.level.gameevent.GameEvent.EXPLODE, at);
        // 1. 光線で壊すブロックを選ぶ（Explosion.explodeと同じ: 16×16×16の格子の外側の面から、強さが尽きるまで0.3ずつ進む）。
        var chosen = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) for (int k = 0; k < 16; k++) {
            if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
            double dx = i / 15f * 2 - 1, dy = j / 15f * 2 - 1, dz = k / 15f * 2 - 1, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length; dy /= length; dz /= length;
            float strength = power * (.7f + level.random.nextFloat() * .6f);
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
        explosion.getToBlow().addAll(chosen);
        // 2. 当てる相手を集め、ForgeのDetonateのイベントを出す（保護modが一覧を変えられる）。
        float diameter = power * 2;
        var area = new net.minecraft.world.phys.AABB(at.x - diameter - 1, at.y - diameter - 1, at.z - diameter - 1, at.x + diameter + 1, at.y + diameter + 1, at.z + diameter + 1);
        var entities = level.getEntities(actor.entity(), area);
        ForgeEventFactory.onExplosionDetonate(level, explosion, entities, diameter);
        // 3. イベントの配送の後の最終の一覧を、その時点の条件で検査する（WORK-148: 前の判定の使い回しと、後から足された位置の抜けを無くす）。
        explosion.getToBlow().removeIf(pos -> !mayBreak(actor, level, pos, skill));
        entities.removeIf(entity -> !mayAttack(actor, entity, skill));
        // 4. 実体へのダメージと押し出し（Explosion.explodeと同じ式）。
        for (var entity : entities) {
            if (entity.ignoreExplosion()) continue;
            double distance = Math.sqrt(entity.distanceToSqr(at)) / diameter;
            if (distance > 1) continue;
            double dx = entity.getX() - at.x, dy = (entity instanceof net.minecraft.world.entity.item.PrimedTnt ? entity.getY() : entity.getEyeY()) - at.y,
                    dz = entity.getZ() - at.z, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0) continue;
            dx /= length; dy /= length; dz /= length;
            double impact = (1 - distance) * Explosion.getSeenPercent(at, entity);
            entity.hurt(source, (float) (int) ((impact * impact + impact) / 2 * 7 * diameter + 1));
            double push = entity instanceof LivingEntity living
                    ? net.minecraft.world.item.enchantment.ProtectionEnchantment.getExplosionKnockbackAfterDampener(living, impact) : impact;
            var pushed = new Vec3(dx * push, dy * push, dz * push);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pushed));
            if (entity instanceof Player hit && !hit.isSpectator() && (!hit.isCreative() || !hit.getAbilities().flying))
                explosion.getHitPlayers().put(hit, new Vec3(dx * impact, dy * impact, dz * impact));
        }
        // 5. 検査を通したブロックだけを壊す（落とし物・音・粒子はMinecraftの処理）。
        explosion.finalizeExplosion(true);
        for (var viewer : level.players())
            if (viewer.distanceToSqr(at.x, at.y, at.z) < 4096)
                viewer.connection.send(new ClientboundExplodePacket(at.x, at.y, at.z, power, explosion.getToBlow(), explosion.getHitPlayers().get(viewer)));
        return true;
    }

    public static @Nullable Vec3 checkTeleport(AbilityActor actor, Entity mover, Vec3 to, AbilityId skill) {
        requireServerThread(actor);
        ServerLevel level = actor.level();
        if (mover == null || !mover.isAlive() || mover.level() != level) return null;
        if (!destination(level, to)) return null;
        // 主体の実体でない相手を動かすのは、攻撃と同じ判定（プレイヤーなら帰属先とPvPが要る）。
        if (mover != actor.entity() && !mayAttack(actor, mover, skill)) return null;
        var event = new net.minecraftforge.event.entity.EntityTeleportEvent(mover, to.x, to.y, to.z);
        if (MinecraftForge.EVENT_BUS.post(event)) return null;
        // リスナーが行き先を変えたときも、実際に使う行き先を同じ条件で検査し直す（WORK-147 F141-S5-F02: 変えた先を検査せずに使っていた）。
        var target = event.getTarget();
        return destination(level, target) ? target : null;
    }
    /** 行き先の条件: 有限の座標、読み込まれている、世界の境界の中、高さの中。 */
    private static boolean destination(ServerLevel level, @Nullable Vec3 to) {
        if (to == null || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)) return false;
        if (to.y < level.getMinBuildHeight() || to.y > level.getMaxBuildHeight()) return false;
        var target = BlockPos.containing(to);
        return level.getWorldBorder().isWithinBounds(target) && level.isLoaded(target);
    }

    public static boolean teleport(AbilityActor actor, Entity mover, Vec3 to, AbilityId skill) {
        var target = checkTeleport(actor, mover, to, skill);
        if (target == null) return false;
        var level = mover.level();
        if (mover.isPassenger()) mover.stopRiding();
        mover.teleportTo(target.x, target.y, target.z);
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
