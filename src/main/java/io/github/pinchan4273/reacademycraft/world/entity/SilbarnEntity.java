package io.github.pinchan4273.reacademycraft.world.entity;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 原作EntitySilbarn（WeAthFolD）: プレイヤーが投げる小さな標的。原作のRigidbodyの物理（毎tick linearDrag 0.8、最初の50tickは重力0、
 * その後0.12）で飛び、地形に当たった瞬間に砕ける。Ray Barrageは20ブロック以内でまだ飛んでいるものを探し、見つけると自分で砕いて
 * 散る一斉射のダメージを起こす。
 *
 * 投げたSilbarnのrigidbodyには原作自身のEntitySelectors.nothing()が付いているので、飛行中はブロックとだけ衝突し、他のエンティティとは
 * 衝突しない: mobの横を通っても自然には砕けない。
 */
public final class SilbarnEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Boolean> HIT = SynchedEntityData.defineId(SilbarnEntity.class, EntityDataSerializers.BOOLEAN);
    /** 原作自身のexecuteAfter(rigidbody.gravity = 0.12, 50)とexecuteAfter(setDead, 10)。 */
    private static final int GRAVITY_DELAY = 50, DEATH_DELAY = 10;
    /**
     * 原作には無い: このエンティティは保存されない（readEntityFromNBTはすぐ破棄する。ここでも同じ）ので、上限が無いと、再起動しない
     * サーバーで永遠にさまよえてしまう。原作では実際にそこまで長く動くものが無いので不要だった。
     */
    private static final int MAX_AGE = 400;
    private int age, hitTicks = -1;
    public SilbarnEntity(EntityType<? extends SilbarnEntity> type, Level level) { super(type, level); }
    /** 原作: 術者の目の位置から、術者の視線のベクトルへ単位速度で投げる。 */
    public static SilbarnEntity throwFrom(ServerPlayer player) {
        var entity = new SilbarnEntity(AcademyContent.SILBARN_ENTITY.get(), player.level());
        entity.setPos(player.getX(), player.getEyeY(), player.getZ());
        var look = player.getLookAngle();
        entity.setDeltaMovement(look);
        entity.setYRot(player.getYHeadRot());
        return entity;
    }
    public boolean isHit() { return entityData.get(HIT); }
    @Override protected void defineSynchedData() { entityData.define(HIT, false); }
    @Override public ItemStack getItem() { return new ItemStack(AcademyContent.SILBARN.get()); }
    /** クライアント: このクライアントがこのSilbarnの破片を既に飛ばしたか。 */
    private boolean fragmented;
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            // 原作sync(): クライアントが当たりを最初に見たとき、その位置から18〜26個の破片が飛ぶ。
            if (isHit() && !fragmented) { fragmented = true; fragments(); }
            return;
        }
        if (isHit()) {
            if (hitTicks >= 0 && ++hitTicks > DEATH_DELAY) discard();
            return;
        }
        if (++age > MAX_AGE) { discard(); return; }
        Vec3 from = position(), to = from.add(getDeltaMovement());
        var block = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) {
            // 原作の衝突処理: 別のSilbarnならsilbarn_heavy（ただしrigidbodyは決してそれと衝突しない。entitySelがnothing）、それ以外はsilbarn_light。
            // つまり地形ではlight。
            level().playSound(null, getX(), getY(), getZ(), io.github.pinchan4273.reacademycraft.world.AcademySounds.SILBARN_LIGHT.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, .5f, 1f);
            shatter(); return;
        }
        double gravity = age > GRAVITY_DELAY ? .12 : 0;
        Vec3 motion = new Vec3(getDeltaMovement().x, getDeltaMovement().y - gravity, getDeltaMovement().z).scale(.8);
        setDeltaMovement(motion);
        setPos(from.add(motion));
    }
    /** Ray Barrageは生きたSilbarnを狙うと自分でこれを起こす。飛行も地形に達した瞬間に自然にこれを起こし、原作の衝突音を鳴らす。 */
    public void shatter() {
        if (isHit()) return;
        entityData.set(HIT, true); hitTicks = 0;
    }
    /**
     * 原作spawnEffects: 原作は衝突点を求めた上で、ここと同じくSilbarn自身の位置から破片を飛ばす。それぞれ0.08〜0.18の速さを3軸へランダムに
     * 分けてランダムな符号を付け、上向きにさらに0.2。
     */
    private void fragments() {
        var random = level().getRandom();
        for (int i = 0, n = 18 + random.nextInt(9); i < n; i++) {
            double vel = .08 + random.nextDouble() * .1, vsq = vel * vel;
            double vx = random.nextDouble() * vel, vxsq = vx * vx;
            double vy = random.nextDouble() * Math.sqrt(vsq - vxsq);
            double vz = Math.sqrt(Math.max(0, vsq - vxsq - vy * vy));
            if (random.nextBoolean()) vx = -vx;
            if (random.nextBoolean()) vy = -vy;
            if (random.nextBoolean()) vz = -vz;
            level().addParticle(io.github.pinchan4273.reacademycraft.world.AcademyParticles.SILBARN_FRAG.get(), getX(), getY(), getZ(), vx, vy + .2, vz);
            fragmentsThrown++;
        }
    }
    private static long fragmentsThrown;
    /** テスト用。 */
    public static long fragmentsThrown() { return fragmentsThrown; }
    /** このSilbarnがまだRay Barrageの有効な対象か: 生きていて、飛行中で、砕けていない。 */
    public boolean liveTarget() { return isAlive() && !isHit(); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this); }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    /** GameTest用: サーバーのtickループ無しで論理tickを1つ進める。 */
    public void tickForTest() { tick(); }
}
