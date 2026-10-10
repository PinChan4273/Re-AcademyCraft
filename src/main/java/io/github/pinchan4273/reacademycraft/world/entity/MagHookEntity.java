package io.github.pinchan4273.reacademycraft.world.entity;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 原作EntityMagHook: 視線の2倍で投げ、1tickに0.05落ちる。最初に当たったものが動作を決める: 生き物なら4ダメージを受け、フックは
 * アイテムとして落ちる。ブロックならフックを留め、そこで静止し、そのブロックが無くなるか誰かが打つまで上に立てる。
 */
public final class MagHookEntity extends Entity {
    private static final EntityDataAccessor<Boolean> HIT = SynchedEntityData.defineId(MagHookEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> HOOKED = SynchedEntityData.defineId(MagHookEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(MagHookEntity.class, EntityDataSerializers.INT);
    /** 原作自身の数値: rigidbodyの重力、投げる速さ、与える4ダメージ。 */
    public static final double GRAVITY = .05, THROW_SPEED = 2;
    public static final float DAMAGE = 4;
    /** 原作には無い: 原作は投げたフックを何かに当たるまで飛ばす。何にも当たらないフックは、Silbarnの移植と同じく、さまよわせずにドロップする。 */
    private static final int MAX_AGE = 600;
    private int age;
    private java.util.UUID thrower;
    /** 原作はプレイヤー自体を保持する。再読込の後は、探す手がかりはUUIDだけになる。 */
    @javax.annotation.Nullable private Player throwerEntity;

    public MagHookEntity(EntityType<? extends MagHookEntity> type, Level level) { super(type, level); }
    public static MagHookEntity throwFrom(Player player) {
        var entity = new MagHookEntity(AcademyContent.MAG_HOOK_ENTITY.get(), player.level());
        entity.setPos(player.getX(), player.getEyeY(), player.getZ());
        entity.setDeltaMovement(player.getLookAngle().scale(THROW_SPEED));
        entity.setYRot(player.getYRot()); entity.setXRot(player.getXRot());
        entity.thrower = player.getUUID(); entity.throwerEntity = player;
        return entity;
    }
    public boolean isHit() { return entityData.get(HIT); }
    public BlockPos hooked() { return entityData.get(HOOKED); }
    public Direction hitSide() { return Direction.from3DDataValue(entityData.get(SIDE)); }
    @Override protected void defineSynchedData() {
        entityData.define(HIT, false); entityData.define(HOOKED, BlockPos.ZERO); entityData.define(SIDE, Direction.DOWN.get3DDataValue());
    }
    /** 原作: 立てたり打たれたりするのは、留まったフックだけ。 */
    @Override public boolean isPickable() { return isHit(); }
    @Override public boolean canBeCollidedWith() { return isHit(); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isHit() && source.getEntity() instanceof Player) dropAsItem();
        return true;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (isHit()) {
            // 原作は、フックが留まったブロックが無くなった瞬間にフックをドロップする。
            if (level().isEmptyBlock(hooked())) dropAsItem();
            return;
        }
        if (++age > MAX_AGE) { dropAsItem(); return; }
        // rigidbodyは動く前に下へ引く。この移植版のSilbarnの飛び方と同じ。
        Vec3 from = position(), motion = getDeltaMovement().subtract(0, GRAVITY, 0), to = from.add(motion);
        var block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        var struck = firstEntity(from, block.getType() == HitResult.Type.MISS ? to : block.getLocation());
        if (struck != null) {
            // 原作CollideHandler: 留まったフックにも当たり（そのcanBeCollidedWithはisHit）、傷つかない。どちらの場合も飛んでいるフックはアイテムとして落ちる。
            // 原作DamageSource.causePlayerDamage(player): 発射物ではなく、投げた者自身の攻撃。
            if (!(struck.getEntity() instanceof MagHookEntity)) struck.getEntity().hurt(damageSource(), DAMAGE);
            dropAsItem(); return;
        }
        if (block.getType() != HitResult.Type.MISS) { take(block.getBlockPos(), block.getDirection()); return; }
        setDeltaMovement(motion);
        setPos(to);
    }
    private EntityHitResult firstEntity(Vec3 from, Vec3 to) {
        var box = new AABB(from, to).inflate(.5);
        EntityHitResult nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : level().getEntities(this, box, e -> e.isAlive() && e.isPickable()
                && !e.getUUID().equals(thrower))) {
            var hit = entity.getBoundingBox().inflate(.3).clip(from, to);
            if (hit.isEmpty()) continue;
            double distance = from.distanceToSqr(hit.get());
            if (distance < best) { best = distance; nearest = new EntityHitResult(entity, hit.get()); }
        }
        return nearest;
    }
    public net.minecraft.world.damagesource.DamageSource damageSource() {
        var owner = thrower();
        return owner != null ? damageSources().playerAttack(owner) : damageSources().thrown(this, null);
    }
    private Player thrower() {
        if (throwerEntity != null && !throwerEntity.isRemoved()) return throwerEntity;
        return thrower == null ? null : level().getPlayerByUUID(thrower);
    }
    /**
     * 原作realSetStillとpreRenderを合わせたもの: その場で止まり、半ブロックから1ブロックへ大きくなり、当たった面の中央に、原作自身の0.51だけ
     * 外へ押し出されて留まる。光線が止まった所に半分の大きさで留まると、見た目の位置とも違い、上に立つにも小さすぎる。
     */
    private void take(BlockPos pos, Direction side) {
        entityData.set(HIT, true); entityData.set(HOOKED, pos.immutable()); entityData.set(SIDE, side.get3DDataValue());
        setDeltaMovement(Vec3.ZERO);
        refreshDimensions();
        setPos(pos.getX() + .5 + side.getStepX() * .51,
                pos.getY() + .5 + side.getStepY() * .51,
                pos.getZ() + .5 + side.getStepZ() * .51);
        // 原作はここでResources.sound("maghook_land")を鳴らすが、原作自身のリソースにそのファイルは無い（原作の参照切れ）ので、何も鳴らさない。
    }
    /** 原作は留まったらsetSize(1f, 1f)。飛んでいる間はその半分。 */
    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return isHit() ? net.minecraft.world.entity.EntityDimensions.fixed(1, 1) : super.getDimensions(pose);
    }
    /** クライアントはデータから留まったことを知るので、そこでも大きくなる。 */
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (HIT.equals(key)) refreshDimensions();
    }
    private void dropAsItem() {
        if (level().isClientSide) return;
        level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), new ItemStack(AcademyContent.MAG_HOOK.get())));
        discard();
    }
    /** テスト用の入口: ワールドのtickを待たずに、投げたフックが何をしたか。 */
    public void tickForTest() { tick(); }
    public void tickForTest(ServerPlayer ignored) { tick(); }
    /** テスト用の入口: そこへ飛んだものとして、この面に留まったフック。 */
    public void hookForTest(BlockPos pos, Direction side) { take(pos, side); }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("thrower")) thrower = tag.getUUID("thrower");
        entityData.set(HIT, tag.getBoolean("hit"));
        if (tag.contains("hooked")) entityData.set(HOOKED, net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("hooked")));
        entityData.set(SIDE, Math.max(0, Math.min(5, tag.getInt("side"))));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (thrower != null) tag.putUUID("thrower", thrower);
        tag.putBoolean("hit", isHit());
        tag.put("hooked", net.minecraft.nbt.NbtUtils.writeBlockPos(hooked()));
        tag.putInt("side", entityData.get(SIDE));
    }
}
