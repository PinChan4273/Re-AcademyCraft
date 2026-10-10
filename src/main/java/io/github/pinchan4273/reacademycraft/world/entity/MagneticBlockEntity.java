package io.github.pinchan4273.reacademycraft.world.entity;

import io.github.pinchan4273.reacademycraft.skill.BlockCargo;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.MoverType;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 原作EntityBlockの空の保存・読み込み時の破棄を置き換える、永続的な積荷の所有者。
 * サーバーの動きは原作MagManipEntityBlockに従う。回転と電弧はクライアントが描く。
 */
public final class MagneticBlockEntity extends Entity {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MagneticBlockEntity.class, EntityDataSerializers.INT);
    private @Nullable UUID owner;
    private @Nullable BlockCargo cargo;
    // 将来の、または壊れたcompoundでない包みは、空にせず不透明で不活性のまま保つ。
    private @Nullable Tag opaqueCargo;
    private static final int REST = 0, HELD = 1, FLIGHT = 2, DROPPED = 3;
    private int motionMode;
    private Vec3 heldTarget = Vec3.ZERO;
    /**
     * 持たれている間と落とされた後の原作自身の動き: そのRigidbodyが次のtickにブロックを動かした量。ブロック自体は、原作の2回の移動の合計だけ
     * 1tickに1回動く。
     */
    private Vec3 legacyMotion = Vec3.ZERO;
    public MagneticBlockEntity(EntityType<? extends MagneticBlockEntity> type, Level level) {
        super(type, level); setNoGravity(true); setInvulnerable(true);
    }
    public static Optional<MagneticBlockEntity> take(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos) || player.serverLevel().isOutsideBuildHeight(pos)
                || player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 100) return Optional.empty();
        var entity = new MagneticBlockEntity(AcademyContent.MAGNETIC_BLOCK.get(), player.level());
        entity.owner = player.getUUID(); entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        // スポーンの許可は元がまだある間に解決する。取り消されたスポーンが、永続するエンティティの所有者の無い切り離された積荷を残してはならない。
        if (!player.serverLevel().addFreshEntity(entity)) return Optional.empty();
        if (entity.isRemoved()) return Optional.empty();
        var transfer = BlockCargo.take(player, pos);
        if (transfer.isEmpty()) { entity.discard(); return Optional.empty(); }
        entity.cargo = transfer.get(); entity.syncState(); return Optional.of(entity);
    }
    public BlockState blockState() { return Block.stateById(entityData.get(STATE)); }
    public static Optional<MagneticBlockEntity> takeHand(ServerPlayer player) {
        var entity = new MagneticBlockEntity(AcademyContent.MAGNETIC_BLOCK.get(), player.level());
        entity.owner = player.getUUID(); entity.setPos(player.getEyePosition());
        if (!player.serverLevel().addFreshEntity(entity) || entity.isRemoved()) return Optional.empty();
        var transfer = BlockCargo.takeHand(player);
        if (transfer.isEmpty()) { entity.discard(); return Optional.empty(); }
        entity.cargo = transfer.get(); entity.syncState(); return Optional.of(entity);
    }
    public Optional<CompoundTag> cargoTag() { return cargo == null ? Optional.empty() : Optional.of(cargo.save()); }
    public boolean isCarrying() { return !isRemoved() && cargo != null && cargo.isAvailable(); }
    public boolean isOwnedBy(ServerPlayer player) { return player.getUUID().equals(owner); }
    public boolean hold(ServerPlayer player) {
        if (!isCarrying() || !player.getUUID().equals(owner) || player.level() != level()) return false;
        motionMode = HELD; heldTarget = player.getEyePosition().add(player.getLookAngle().scale(2)).add(0, -.1, 0); return true;
    }
    /** 手を離す: 原作のブロックと同じく、持っていたときに最後に与えた動きで落ちていく。 */
    public void drop() { if (isCarrying()) motionMode = DROPPED; }
    /** テスト用の入口: この原作の動きで手を離す。 */
    public void dropMovingAt(Vec3 motion) { if (isCarrying()) { legacyMotion = motion; motionMode = DROPPED; } }
    private void halt() { setDeltaMovement(Vec3.ZERO); legacyMotion = Vec3.ZERO; }
    /**
     * 原作の投げが実際に動いた量: 毎tick、そのRigidbodyが速度だけブロックを動かし、次にMagManipEntityBlockが縦から0.04引いて再び速度だけ動かした。
     * n tick目は2v - 0.04 - 0.08n動く。衝突ありで1tickに1回動かす場合、2v + 0.04を保ちFLIGHT_GRAVITYを毎tick失えば、同じ距離を進む。
     * velocityは原作のもので、最大1。
     */
    public boolean throwFrom(ServerPlayer player, Vec3 velocity) {
        if (!isCarrying() || !player.getUUID().equals(owner) || player.level() != level()
                || !Double.isFinite(velocity.lengthSqr()) || velocity.lengthSqr() > 1.0001) return false;
        motionMode = FLIGHT; setDeltaMovement(velocity.scale(2).add(0, LEGACY_GRAVITY, 0)); hurtMarked = true; return true;
    }
    /**
     * 原作自身の重力と、原作の1tick2回の移動に対してここで投げたブロックが1tickに失う量。落としたブロックは原作自身の動き
     * （1tickに0.04失う）を保ち、それで2回動く（tickMovementを参照）。
     */
    public static final double LEGACY_GRAVITY = .04, FLIGHT_GRAVITY = 2 * LEGACY_GRAVITY;
    public boolean recover(ServerPlayer player) {
        if (level().isClientSide || cargo == null || !player.getUUID().equals(owner) || player.level() != level()
                || distanceToSqr(player) > 36 || !cargo.recover(player)) return false;
        discard(); return true;
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        return player instanceof ServerPlayer server && recover(server) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public void tick() {
        super.tick();
        // 外部から召喚した空のエンティティは何も持たない。未知の積荷は置き換えも消去もせず保つ。所有者のログアウトは破棄の理由にならない。
        if (!level().isClientSide && ((cargo == null && opaqueCargo == null) || (cargo != null && cargo.isSpent()))) discard();
        if (level().isClientSide || !isCarrying() || motionMode == REST) return;
        var server = (net.minecraft.server.level.ServerLevel) level();
        var player = owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level()) {
            motionMode = DROPPED; halt(); return; // 所有者が居ない間も積荷を保つ。
        }
        tickMovement(player);
    }
    /** 論理サーバーの1段。決定的なワールド内のテストのため、プレイヤーの検索から分けている。 */
    public void tickMovement(ServerPlayer player) {
        if (level().isClientSide || !isCarrying() || motionMode == REST || !player.getUUID().equals(owner)) return;
        if (!player.isAlive() || player.isSpectator() || player.level() != level()) { motionMode = DROPPED; halt(); return; }
        Vec3 motion;
        if (motionMode == HELD) {
            // 原作: Rigidbodyが前のtickから残った動きだけ動かし、次にActMoveToがそこから改めて狙い（目標へ0.2 * min(距離^2 / 4, 1)）、その分も動かした。
            var first = legacyMotion;
            var delta = heldTarget.subtract(position().add(first)); double length = delta.lengthSqr();
            legacyMotion = length < 1.0e-8 ? Vec3.ZERO : delta.normalize().scale(.2 * Math.min(length / 4, 1));
            motion = first.add(legacyMotion);
        } else if (motionMode == FLIGHT) motion = getDeltaMovement().add(0, -FLIGHT_GRAVITY, 0);
        else {
            // 原作ActNothing: 動きだけ動かし、次に同じものから上向きに0.04引いた分だけ動かし、それを保った。
            var first = legacyMotion;
            legacyMotion = legacyMotion.add(0, -LEGACY_GRAVITY, 0);
            motion = first.add(legacyMotion);
        }
        Vec3 next = position().add(motion);
        // 新しいchunkを強制したり、ワールドの下で積荷を失ったりしない。回収できるまま残る。
        if (next.y < level().getMinBuildHeight() || next.y >= level().getMaxBuildHeight()) { halt(); motionMode = REST; return; }
        for (double x : new double[] {-.5, .5}) for (double z : new double[] {-.5, .5})
            if (!level().hasChunkAt(BlockPos.containing(next.add(x, 0, z)))) { halt(); return; }
        damageAlong(player, motion);
        setDeltaMovement(motion); move(MoverType.SELF, motion); hurtMarked = true;
        if (motionMode != HELD && (onGround() || horizontalCollision || verticalCollision)) {
            halt();
            if (cargo.place(player, restingCell(), net.minecraft.core.Direction.UP)) discard();
        }
    }
    /**
     * 止まったセル（それが無いと、村の道の上でエンティティのまま残ってしまう）。足のセルではない。足は立っている土の道・耕地・ソウルサンド・
     * ハーフブロックのセルに沈むので、下半分の中央を含むセルを使う。そのセルに置き換えてはならないブロック（カーペット、花、より深い雪）が
     * あれば、その上のセル。それ以外の理由でそこに置けない場合（保護、誰かが立っている）は、再試行のために保つ。
     */
    private BlockPos restingCell() {
        var cell = BlockPos.containing(getX(), getY() + .5, getZ());
        // 水の中で止まったブロックは、水がどれほど浅くても保つ: 上の空気へ持ち上げない。
        return cargo.fits(level(), cell) || !level().getFluidState(cell).isEmpty() ? cell : cell.above();
    }
    private void damageAlong(ServerPlayer player, Vec3 motion) {
        Vec3 from = position().add(0, .49, 0), to = from.add(motion);
        var wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double nearest = wall.getType() == HitResult.Type.MISS ? motion.lengthSqr() + .0001 : from.distanceToSqr(wall.getLocation());
        Entity target = null;
        for (var entity : level().getEntities(this, getBoundingBox().expandTowards(motion).inflate(.05),
                e -> e != player && !(e instanceof MagneticBlockEntity) && e.isAlive() && e.isPickable() && !e.isSpectator())) {
            var box = entity.getBoundingBox().inflate(.49); var hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) <= nearest) { nearest = from.distanceToSqr(hit.get()); target = entity; }
        }
        // 実際の原作のコンストラクタは固定の10を渡す。持っている間の接触も含む。
        if (target != null) io.github.pinchan4273.reacademycraft.skill.SkillCombat.attack(player, target, io.github.pinchan4273.reacademycraft.skill.MagneticManipulation.ID, 10);
    }
    @Override protected void defineSynchedData() { entityData.define(STATE, Block.getId(Blocks.AIR.defaultBlockState())); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        cargo = tag.contains("Cargo", Tag.TAG_COMPOUND) ? BlockCargo.load(tag.getCompound("Cargo")) : null;
        var rawCargo = tag.get("Cargo");
        opaqueCargo = rawCargo != null && cargo == null ? rawCargo.copy() : null;
        int savedMode = tag.getInt("MotionMode");
        // キーを押し続けるセッションは再起動を越えられない。積荷を保ち、安全に落とす。
        motionMode = savedMode == HELD ? DROPPED : savedMode == FLIGHT || savedMode == DROPPED ? savedMode : REST;
        // 再読込した落下はそのまま落ち続け、再読込した保持は静止から落ちる（原作は再読込でブロックを取り除いた）。
        var saved = tag.getList("LegacyMotion", Tag.TAG_DOUBLE);
        legacyMotion = savedMode == DROPPED && saved.size() == 3 && Double.isFinite(saved.getDouble(0) + saved.getDouble(1) + saved.getDouble(2))
                ? new Vec3(saved.getDouble(0), saved.getDouble(1), saved.getDouble(2)) : Vec3.ZERO;
        setNoGravity(true); setInvulnerable(true); syncState();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        if (cargo != null) tag.put("Cargo", cargo.save());
        else if (opaqueCargo != null) tag.put("Cargo", opaqueCargo.copy());
        tag.putInt("MotionMode", motionMode);
        var motion = new net.minecraft.nbt.ListTag();
        for (double c : new double[] {legacyMotion.x, legacyMotion.y, legacyMotion.z}) motion.add(net.minecraft.nbt.DoubleTag.valueOf(c));
        tag.put("LegacyMotion", motion);
    }
    private void syncState() {
        entityData.set(STATE, Block.getId(cargo == null ? (opaqueCargo == null ? Blocks.AIR : Blocks.IRON_BLOCK).defaultBlockState()
                : cargo.payload().state().orElse(Blocks.IRON_BLOCK.defaultBlockState())));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
