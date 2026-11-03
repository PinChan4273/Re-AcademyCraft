package io.github.pinchan4273.reacademycraft.world.entity;

import io.github.pinchan4273.reacademycraft.skill.DarkMatterBeetles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 未元物質の甲虫（§9）。当たり判定は幅2.0・高さ1.75（ユーザーの判断で見た目に合わせた。判定の水平は正方形なので、幅は体の横幅2.0に合わせ、前後の端は少し外へ出る。
 * 高さは上角の先までで、細い主角の上部は含めない）。
 * <p>
 * 技能（{@link DarkMatterBeetles}）からだけ生成する。チャンクには保存しない（型が{@code noSave}）: 個体の状態は所有者の能力データに置き、
 * ログアウトで収納し、ログインで復帰する。所有者のいない個体（プレビューの撮影など）は立っているだけ。
 * <p>
 * 行動はこのクラスの{@link #customServerAiStep}で毎tick決める: 実行中の突進 → 帰還（所有者の追従開始距離Dを超えてから、D−4以内へ戻るまで）→ 標的との戦闘 → 追従。
 * 地上は経路探索、飛行は直線の速度指定で、進路を塞ぐブロックは{@link #clear}が設定に従って壊す。
 * <p>
 * 描画のために同期する値: {@link #ownerId()}、{@link #flying()}、{@link #attackKind()}、{@link #attackSerial()}。
 */
public final class DarkMatterBeetle extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(DarkMatterBeetle.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DarkMatterBeetle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> ATTACK_KIND = SynchedEntityData.defineId(DarkMatterBeetle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ATTACK_SERIAL = SynchedEntityData.defineId(DarkMatterBeetle.class, EntityDataSerializers.INT);
    /** 同期する攻撃の種類。近接と砲撃は{@link #ATTACK_LOCK}tickの間、突進は終わるまでこの値のまま。 */
    public static final byte ATTACK_NONE = 0, ATTACK_MELEE = 1, ATTACK_DASH = 2, ATTACK_SHOT = 3;
    /**
     * 砲口の位置（実体の足元が原点、+zが前、ブロック単位）。モデルのmuzzle骨を、root→body→head→horn_main（取付-42度）→mid→end→
     * horn_plate（取付-40度）の順にたどった点。{@code generate_beetle_facets.py}のBODY_SCALE・HORN_SCALEは骨の位置
     * （{@code DarkBeetleMesh}）に入っている。頭と角の動きは入れない固定の値。
     */
    public static final Vec3 MUZZLE = new Vec3(0, 1.9635, 2.3764);
    /** 近接と砲撃の後、次の攻撃を選ばないtick（1体が複数の攻撃を同時に出さない）。 */
    public static final int ATTACK_LOCK = 10;
    /** 突進を始める最短の距離（これより近ければ近接）。 */
    public static final double DASH_MIN = 4;
    private static final int STUCK_CHECK = 20, FLIGHT_HOLD = 40;
    private static final double CATCH_UP = 16, CAUGHT_UP = 9, REST_DISTANCE = 1.5, RESUME_DISTANCE = 3;

    /** 進路を確かめた結果。CLEAR: 進める。BLOCKED: 壊せない物が塞いでいる。WAITING: この所有者の今tickの破壊の上限に達した。 */
    public enum Contact { CLEAR, BLOCKED, WAITING }
    private static final class Dash {
        final Vec3 direction; final double speed; final float damage; final boolean grounded; double left; Vec3 last = Vec3.ZERO; boolean moving;
        Dash(Vec3 direction, double speed, double left, float damage, boolean grounded) {
            this.direction = direction; this.speed = speed; this.left = left; this.damage = damage; this.grounded = grounded;
        }
    }

    private @Nullable DarkMatterBeetles.Party party;
    private @Nullable LivingEntity target;
    private final Map<LivingEntity, Long> attackers = new LinkedHashMap<>();
    private @Nullable Dash dash;
    private int meleeCooldown, dashCooldown, shotCooldown, actionTicks;
    private int flightHold, repath, stuckClock, stuckTicks;
    private boolean catchingUp, resting;
    /** 帰還中か（追従開始距離を超えてから、その4ブロック内側へ戻るまで）。保存しない（復帰の後は距離で決め直す）。 */
    private boolean returning;
    private Vec3 stuckFrom = Vec3.ZERO;
    /** 64ブロック超が続いたtick（{@link DarkMatterBeetles}が数える）。 */
    public int farTicks;

    public DarkMatterBeetle(EntityType<? extends DarkMatterBeetle> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1f);
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "academy", bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() { }
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void attributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
            event.put(io.github.pinchan4273.reacademycraft.world.AcademyContent.DARK_MATTER_BEETLE.get(), DarkMatterBeetle.attributes().build());
        }
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, .25).add(Attributes.KNOCKBACK_RESISTANCE, .6);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
        entityData.define(FLYING, false);
        entityData.define(ATTACK_KIND, ATTACK_NONE);
        entityData.define(ATTACK_SERIAL, 0);
    }
    /** 所有者のUUID（クライアントでも読める）。 */
    public @Nullable UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    /** 飛行中か。翅の動きはこの値で決める。 */
    public boolean flying() { return entityData.get(FLYING); }
    /** 今の攻撃の種類（{@link #ATTACK_NONE}など）。 */
    public byte attackKind() { return entityData.get(ATTACK_KIND); }
    /** 攻撃を始めるたびに1増える。同じ種類が続いても動きを最初から始められる。 */
    public int attackSerial() { return entityData.get(ATTACK_SERIAL); }
    public boolean isOwnedBy(Entity entity) { return entity.getUUID().equals(ownerId()); }
    /** 所有者本人か、同じ所有者の個体か（§9の誤射の保護の範囲）。 */
    public boolean isFriendly(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity == this || isOwnedBy(entity)) return true;
        return entity instanceof DarkMatterBeetle other && ownerId() != null && ownerId().equals(other.ownerId());
    }

    // ---- 所有者との結びつきと個体の状態 ----

    /** 技能が生成の直後に呼ぶ。 */
    public void bind(DarkMatterBeetles.Party party, UUID owner) {
        this.party = party;
        entityData.set(OWNER, Optional.of(owner));
    }
    /** 収納・保存する個体の状態（HPとクールダウン）。方針や修復は所有者の設定なのでここに持たない。 */
    public CompoundTag writeState() {
        var tag = new CompoundTag();
        tag.putFloat("health", getHealth());
        tag.putInt("melee", meleeCooldown); tag.putInt("dash", dashCooldown); tag.putInt("shot", shotCooldown);
        return tag;
    }
    /** {@link #writeState}の値へ戻す。HPは無料で全回復しない（0以下なら1）。 */
    public void readState(CompoundTag tag) {
        float health = tag.getFloat("health");
        setHealth(Float.isFinite(health) ? Math.max(1, Math.min(getMaxHealth(), health)) : 1);
        meleeCooldown = Math.max(0, tag.getInt("melee")); dashCooldown = Math.max(0, tag.getInt("dash")); shotCooldown = Math.max(0, tag.getInt("shot"));
    }
    /** 熟練度に合わせて最大HPを置く（今のHPは増やさない。最大より多ければ切り詰める）。 */
    public void applyMaxHealth(float proficiency) {
        var attribute = getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null && attribute.getBaseValue() != DarkMatterBeetles.maxHealth(proficiency)) attribute.setBaseValue(DarkMatterBeetles.maxHealth(proficiency));
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }
    public void noteAttacker(LivingEntity attacker) {
        attackers.remove(attacker);
        attackers.put(attacker, level().getGameTime());
        if (attackers.size() > DarkMatterBeetles.MEMORY_LIMIT) { var first = attackers.keySet().iterator(); first.next(); first.remove(); }
    }
    public Map<LivingEntity, Long> attackers() { return attackers; }
    public @Nullable LivingEntity currentTarget() { return target; }
    /** 帰還中か（追従開始距離を超えて、所有者へ戻っている間）。 */
    public boolean returning() { return returning; }
    public boolean dashing() { return dash != null; }
    public int meleeCooldown() { return meleeCooldown; }
    public int dashCooldown() { return dashCooldown; }
    public int shotCooldown() { return shotCooldown; }
    public void resetCooldownsForTest() { meleeCooldown = dashCooldown = shotCooldown = actionTicks = 0; }

    // ---- 保護と、普通の生き物の振る舞いの打ち消し ----

    /**
     * 環境の損傷か（甲虫はこれを受けない。2026-11-03のユーザーの決定: 特定のMODへの個別の対応ではなく、汎用の環境ダメージ耐性）。
     * 未元物質の構造物で、体温も呼吸も持たない。原因の実体（攻撃した者・飛び道具）の無い損傷のうち、爆発・状態異常（魔法・衰弱）・
     * 無敵を貫く物（奈落・/kill）を除いたもの。火・溶岩・凍結・窒息・サボテン・落雷や、他のMODの気候の損傷などが当たる。
     */
    public static boolean environmental(DamageSource source) {
        if (source.getEntity() != null || source.getDirectEntity() != null) return false;
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return false;
        return !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC) && !source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.WITHER);
    }
    /** §9: 召喚者本人と同じ所有者の個体からの損傷を受けない。他のプレイヤーへの無敵ではない。環境の損傷（{@link #environmental}）も受けない。 */
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return isFriendly(source.getEntity()) || isFriendly(source.getDirectEntity()) || environmental(source) || super.isInvulnerableTo(source);
    }
    /** §9: 召喚者本人と同じ所有者の個体からの状態異常を受けない。 */
    @Override public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return !isFriendly(source) && super.addEffect(effect, source);
    }
    /** §9: 召喚者本人と同じ所有者の個体の間で押し合わない。 */
    @Override public void push(Entity entity) { if (!isFriendly(entity)) super.push(entity); }
    @Override protected void doPush(Entity entity) { if (!isFriendly(entity)) super.doPush(entity); }
    /** §9: 所有者本人のクライアントの照準は自分の甲虫を選ばない（照準の攻撃を遮らない）。他の人やサーバーの判定には今までどおり当たる。 */
    @Override public boolean isPickable() {
        if (level().isClientSide && Boolean.TRUE.equals(net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDarkBeetles.isLocalPlayer(ownerId())))) return false;
        return super.isPickable();
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

    // ---- 行動 ----

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (party == null) return;
        ServerPlayer owner = party.player();
        float p = DarkMatterBeetles.proficiency(owner);
        if (meleeCooldown > 0) meleeCooldown--;
        if (dashCooldown > 0) dashCooldown--;
        if (shotCooldown > 0) shotCooldown--;
        if (actionTicks > 0 && --actionTicks == 0 && dash == null) entityData.set(ATTACK_KIND, ATTACK_NONE);
        // 開始済みの突進は最後まで（設定の変更も帰還も割り込まない。§9）。
        if (dash != null) { tickDash(); return; }
        act(owner, p);
        keepApart(owner);
    }
    private void act(ServerPlayer owner, float p) {
        if (!owner.isAlive() || owner.level() != level()) {
            getNavigation().stop();
            setDeltaMovement(0, flying() ? 0 : getDeltaMovement().y, 0);
            return;
        }
        double toOwner = distanceTo(owner);
        // 追従開始距離は毎tick所有者のデータから読む（設定の変更は召喚済みの全個体へ次のtickから効く）。
        int limit = DarkMatterBeetles.followDistance(owner);
        if (toOwner > limit) returning = true;
        else if (toOwner <= limit - DarkMatterBeetles.FOLLOW_RESUME) returning = false;
        // 帰還中は戦闘・索敵より帰還を優先する: 標的を外し、攻撃しない（64ブロック超の転移はDarkMatterBeetlesが別に行う）。
        if (returning) { target = null; follow(owner, p, toOwner, true); return; }
        target = DarkMatterBeetles.chooseTarget(party, this, target);
        if (target == null) follow(owner, p, toOwner, false);
        else fight(owner, target, p);
    }

    /** 所有者の後ろの、個体ごとの席へ向かう。着いたら、席が3ブロック以上ずれるか所有者が近づくまで休む（小刻みに動かない）。 */
    private void follow(ServerPlayer owner, float p, double toOwner, boolean returning) {
        if (toOwner > CATCH_UP) catchingUp = true;
        else if (toOwner < CAUGHT_UP) catchingUp = false;
        boolean up = ownerAirborne(owner);
        Vec3 dest = DarkMatterBeetles.seat(party, this, up);
        if (dest == null) {
            // 席が無い（狭い所など）。近くなら今の場所に留まり、遠ければ大まかな席へ向かう。
            if (toOwner <= CATCH_UP) { getNavigation().stop(); if (flying() && up) setDeltaMovement(Vec3.ZERO); return; }
            dest = DarkMatterBeetles.roughSeat(party, this);
        }
        double there = position().distanceTo(dest);
        boolean crowding = horizontalDistance(owner) < DarkMatterBeetles.OWNER_CLEARANCE;
        if (there > RESUME_DISTANCE || crowding) resting = false;
        else if (there < REST_DISTANCE) resting = true;
        if (resting) {
            getNavigation().stop();
            if (flying() && up) { setDeltaMovement(Vec3.ZERO); return; }
            if (flying()) { setFlying(false); flightHold = 0; }
            return;
        }
        go(dest, p, returning || up || catchingUp);
    }
    private double horizontalDistance(Entity other) { return Math.sqrt(Mth.square(getX() - other.getX()) + Mth.square(getZ() - other.getZ())); }
    /** 所有者と他の個体に重ならないよう少しずつ離れる。押し合いは無効（§9）なので、自分で動く。突進中はしない。 */
    private void keepApart(ServerPlayer owner) {
        if (owner.level() != level()) return;
        Vec3 push = Vec3.ZERO;
        if (Math.abs(owner.getY() - getY()) < 3) push = push.add(awayFrom(owner, DarkMatterBeetles.OWNER_CLEARANCE, .12));
        for (var other : party.members()) if (other != this && Math.abs(other.getY() - getY()) < 3) push = push.add(awayFrom(other, DarkMatterBeetles.SIBLING_CLEARANCE, .06));
        if (push != Vec3.ZERO) setDeltaMovement(getDeltaMovement().add(push));
    }
    private Vec3 awayFrom(Entity other, double clearance, double strength) {
        double dx = getX() - other.getX(), dz = getZ() - other.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d >= clearance) return Vec3.ZERO;
        // 完全に重なっていたら、個体ごとに違う向きへ。
        if (d < 1e-3) { double a = getId() * 2.4; return new Vec3(Math.cos(a) * strength, 0, Math.sin(a) * strength); }
        return new Vec3(dx / d * strength, 0, dz / d * strength);
    }
    private boolean ownerAirborne(ServerPlayer owner) {
        if (owner.getAbilities().flying) return true;
        if (owner.onGround()) return false;
        var from = owner.position();
        return level().clip(new ClipContext(from, from.add(0, -3, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this)).getType() == HitResult.Type.MISS;
    }

    /** destへ向かう。飛行は直線の速度指定、地上は経路探索。地上で詰まったら飛ぶ。 */
    private void go(Vec3 dest, float p, boolean wantFly) {
        if (wantFly || stuckTicks > 0) flightHold = FLIGHT_HOLD;
        else if (flightHold > 0) flightHold--;
        boolean fly = flightHold > 0 || (flying() && !onGround());
        setFlying(fly);
        if (fly) {
            getNavigation().stop();
            Vec3 to = dest.subtract(position());
            double distance = to.length();
            double step = Math.min(DarkMatterBeetles.flightSpeed(p) / 20, distance);
            Vec3 move = distance < 1e-3 ? Vec3.ZERO : to.scale(step / distance);
            // 足元の床は、本当に下へ抜けたいときだけ塞いでいるとみなす。
            boolean through = dest.y < getY() - 1.5;
            var contact = clear(through ? move : new Vec3(move.x, Math.max(0, move.y), move.z), false, false);
            if (contact == Contact.WAITING) move = Vec3.ZERO;
            else if (horizontalCollision && move.horizontalDistanceSqr() > 1e-6) move = new Vec3(move.x, step, move.z); // 壊せない壁は越える。
            setDeltaMovement(move);
            if (move.horizontalDistanceSqr() > 1e-6) face(position().add(move));
        } else {
            var attribute = getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) attribute.setBaseValue(DarkMatterBeetles.groundAttribute(p));
            if (--repath <= 0 || getNavigation().isDone()) { repath = 10; getNavigation().moveTo(dest.x, dest.y, dest.z, 1); }
            if (getMoveControl().hasWanted()) {
                Vec3 wish = new Vec3(getMoveControl().getWantedX() - getX(), 0, getMoveControl().getWantedZ() - getZ());
                if (wish.lengthSqr() > 1e-4) clear(wish.normalize().scale(.2), true, true);
            }
        }
        trackStuck(dest);
    }
    /** 目的地へ近づかないまま1秒たったら、詰まったとみなす（次のgoから飛ぶ）。 */
    private void trackStuck(Vec3 dest) {
        if (++stuckClock < STUCK_CHECK) return;
        stuckClock = 0;
        boolean stuck = position().distanceTo(dest) > 3 && position().distanceTo(stuckFrom) < .5;
        stuckFrom = position();
        stuckTicks = stuck ? STUCK_CHECK : 0;
    }
    private void setFlying(boolean on) {
        if (flying() != on) entityData.set(FLYING, on);
        setNoGravity(on);
    }
    private static float yawTo(double dx, double dz) { return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90; }
    private void face(Vec3 point) {
        double dx = point.x - getX(), dz = point.z - getZ();
        if (dx * dx + dz * dz < 1e-6) return;
        float yaw = yawTo(dx, dz);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
    }
    /** 砲口の世界の位置（今の向きで）。 */
    public Vec3 muzzle() { return position().add(MUZZLE.yRot(-yBodyRot * Mth.DEG_TO_RAD)); }
    /** tを向いたときの砲口の世界の位置。 */
    private Vec3 muzzleToward(LivingEntity t) {
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        if (dx * dx + dz * dz < 1e-6) return muzzle();
        return position().add(MUZZLE.yRot(-yawTo(dx, dz) * Mth.DEG_TO_RAD));
    }

    private static Vec3 center(LivingEntity entity) { return entity.getBoundingBox().getCenter(); }
    private boolean sees(Vec3 from, LivingEntity other) {
        return level().clip(new ClipContext(from, center(other), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }
    /** 角が届く距離か（体の判定の外1.5ブロック）。 */
    private boolean inMelee(LivingEntity other) { return getBoundingBox().inflate(1.5).intersects(other.getBoundingBox()); }

    /** §9の近／中／遠。禁止リストではなく優先順位で、上から順に今できるものを選ぶ。 */
    private void fight(ServerPlayer owner, LivingEntity t, float p) {
        var data = DarkMatterBeetles.data(owner);
        var stance = data == null ? DarkMatterBeetles.Stance.MID : data.getBeetleStance();
        boolean breaks = data != null && data.isBeetleContactBreak();
        Vec3 here = getBoundingBox().getCenter();
        double d = here.distanceTo(center(t));
        boolean idle = actionTicks == 0, los = sees(here, t), melee = inMelee(t) && los;
        double dashRange = DarkMatterBeetles.dashRange(p), shotRange = DarkMatterBeetles.shotRange(p);
        boolean canMelee = idle && melee && meleeCooldown == 0;
        boolean canDash = idle && dashCooldown == 0 && d >= DASH_MIN && d <= dashRange && (los || breaks);
        boolean canShot = idle && shotCooldown == 0 && d <= shotRange && sees(muzzleToward(t), t);
        switch (stance) {
            case NEAR -> {
                if (canMelee) { strikeMelee(t, p); return; }
                if (canDash) { startDash(t, p); return; }
                // 角を優先する。届かない所（突進の外、または詰まっている）だけ砲撃する。
                if (canShot && (d > dashRange || stuckTicks > 0)) { fire(t, p); return; }
                if (!melee) go(t.position(), p, wantsFlight(t)); else hold(t);
            }
            case MID -> {
                if (canMelee) { strikeMelee(t, p); return; }
                if (!melee && canDash) { startDash(t, p); return; }
                if (canShot) { fire(t, p); return; }
                if (melee || (d <= dashRange * .7 && los)) hold(t); else go(t.position(), p, wantsFlight(t));
            }
            case FAR -> {
                if (canShot) { fire(t, p); return; }
                Vec3 away = new Vec3(getX() - t.getX(), 0, getZ() - t.getZ());
                away = away.lengthSqr() < 1e-4 ? getLookAngle().multiply(-1, 0, -1) : away.normalize();
                boolean trapped = !level().noCollision(this, getBoundingBox().move(away.scale(2)));
                // 下がる先が追従開始距離の内側（帰還をやめる距離D−4以内）でなければ下がらない。下がって帰還に入り、戻ってまた下がる往復を防ぐ。
                Vec3 retreat = position().add(away.scale(6));
                boolean leashed = owner.position().distanceTo(retreat) > DarkMatterBeetles.followDistance(owner) - DarkMatterBeetles.FOLLOW_RESUME;
                if ((trapped || leashed) && canMelee) { strikeMelee(t, p); return; }
                if (d < shotRange * .5 && !trapped && !leashed) go(retreat, p, flying());
                else if (d > shotRange * .8 || !los) go(t.position(), p, wantsFlight(t));
                else hold(t);
            }
        }
    }
    private boolean wantsFlight(LivingEntity t) { return t.getY() - getY() > 2.5 || (flying() && !onGround()); }
    /** その場で標的を向く。飛行中は空中に留まる。 */
    private void hold(LivingEntity t) {
        getNavigation().stop();
        if (flying()) setDeltaMovement(Vec3.ZERO);
        face(t.position());
    }
    private void begin(byte kind) {
        entityData.set(ATTACK_KIND, kind);
        entityData.set(ATTACK_SERIAL, attackSerial() + 1);
    }
    private void strikeMelee(LivingEntity t, float p) {
        face(t.position());
        meleeCooldown = io.github.pinchan4273.reacademycraft.skill.HeavenEffects.scaledInterval(this, DarkMatterBeetles.MELEE_INTERVAL); actionTicks = ATTACK_LOCK;
        begin(ATTACK_MELEE);
        DarkMatterBeetles.strike(party, this, t, DarkMatterBeetles.meleeDamage(p));
    }
    private void fire(LivingEntity t, float p) {
        face(t.position());
        shotCooldown = io.github.pinchan4273.reacademycraft.skill.HeavenEffects.scaledInterval(this, DarkMatterBeetles.shotInterval(p)); actionTicks = ATTACK_LOCK;
        begin(ATTACK_SHOT);
        // 直進で追尾しない弾なので、跳ねて動く相手には着く頃の位置を狙う。
        Vec3 from = muzzle();
        double flight = from.distanceTo(center(t)) / DarkMatterBeetles.SHOT_SPEED;
        DarkMatterBeetles.fire(party, this, from, center(t).add(t.getDeltaMovement().scale(flight)), p);
    }
    private void startDash(LivingEntity t, float p) {
        face(t.position());
        getNavigation().stop();
        Vec3 to = center(t).subtract(getBoundingBox().getCenter());
        boolean grounded = !flying();
        if (grounded) to = new Vec3(to.x, 0, to.z);
        if (to.lengthSqr() < 1e-6) return;
        dashCooldown = io.github.pinchan4273.reacademycraft.skill.HeavenEffects.scaledInterval(this, DarkMatterBeetles.dashCooldown(p));
        dash = new Dash(to.normalize(), DarkMatterBeetles.dashSpeed(p) / 20, DarkMatterBeetles.dashRange(p), DarkMatterBeetles.dashDamage(p), grounded);
        begin(ATTACK_DASH);
    }
    private void tickDash() {
        Dash now = dash;
        double length = Math.min(now.speed, now.left);
        Vec3 step = now.direction.scale(length);
        // 最初の敵への命中で終わる。1回の突進で連続して当てない。
        var enemy = DarkMatterBeetles.firstEnemy(party, this, getBoundingBox().expandTowards(step).inflate(.2));
        if (enemy != null) {
            DarkMatterBeetles.strike(party, this, enemy, now.damage);
            endDash();
            return;
        }
        var contact = clear(step, now.grounded, false);
        if (contact == Contact.BLOCKED) { endDash(); return; }
        if (contact == Contact.WAITING) { now.moving = false; setDeltaMovement(Vec3.ZERO); return; } // 壁の手前で止まり、次のtickに確かめ直す。
        // 前のtickに動けなかった（判定の角などで止められた）なら、そこで終わる。
        if (now.moving && position().distanceToSqr(now.last) < 1e-4) { endDash(); return; }
        now.moving = true;
        now.last = position();
        now.left -= length;
        setDeltaMovement(step);
        if (now.left <= 1e-6) dash = null;
        if (dash == null) { actionTicks = Math.max(actionTicks, 5); }
    }
    private void endDash() {
        dash = null;
        actionTicks = Math.max(actionTicks, 5);
        setDeltaMovement(flying() ? Vec3.ZERO : new Vec3(0, getDeltaMovement().y, 0));
    }

    /**
     * 次の動きstepで体が接触する、進路を塞ぐブロックを確かめ、壊せるものを壊す（§9）。足場として踏んでいるだけの床は、下へ動かない限り対象にしない。
     * walking（または壊せない設定の地上）では登れる高さ（1ブロック）の段差も塞いでいるとみなさない。衝突の無いブロックは数えない。
     * 上限（同じ所有者の全個体で1tick 12ブロック）に達したらWAITINGで、残りは次のtick。壊せないものが1つでもあればBLOCKED（壊せる分は壊す）。
     */
    public Contact clear(Vec3 step, boolean grounded, boolean walking) {
        if (party == null) return Contact.CLEAR;
        boolean allowed = DarkMatterBeetles.contactBreakAllowed(party, this);
        AABB moved = getBoundingBox().move(step);
        double floor = getBoundingBox().minY + (grounded && (walking || !allowed) ? maxUpStep() : 0) + 1e-3;
        double lower = step.y < -1e-3 ? moved.minY : Math.max(moved.minY, floor);
        if (lower >= moved.maxY) return Contact.CLEAR;
        AABB box = new AABB(moved.minX, lower, moved.minZ, moved.maxX, moved.maxY, moved.maxZ);
        boolean blocked = false, waiting = false;
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1e-6); x++)
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY - 1e-6); y++)
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1e-6); z++) {
                    cursor.set(x, y, z);
                    var state = level().getBlockState(cursor);
                    if (state.isAir()) continue;
                    var shape = state.getCollisionShape(level(), cursor);
                    if (shape.isEmpty() || !intersects(shape, cursor, box)) continue;
                    if (!allowed || !DarkMatterBeetles.mayBreak(party, this, cursor, state)) { blocked = true; continue; }
                    if (!DarkMatterBeetles.takeBreak(party)) { waiting = true; continue; }
                    ((ServerLevel) level()).destroyBlock(cursor.immutable(), false, this);
                }
        return blocked ? Contact.BLOCKED : waiting ? Contact.WAITING : Contact.CLEAR;
    }
    private static boolean intersects(net.minecraft.world.phys.shapes.VoxelShape shape, BlockPos pos, AABB box) {
        for (AABB part : shape.toAabbs()) if (part.move(pos).intersects(box)) return true;
        return false;
    }
    /** 試験用の入口: 行動を今すぐ1回決める。 */
    public void aiStepForTest() { customServerAiStep(); }
    /** 試験用の入口: 標的を選び直して持つ（動きと攻撃はしない）。 */
    public void chooseForTest() { if (party != null) target = DarkMatterBeetles.chooseTarget(party, this, target); }
    /** 試験用の入口: 突進を今すぐ始める。 */
    public void startDashForTest(LivingEntity t, float p) { startDash(t, p); }
    /** 試験用の入口: tへ1発撃つ。 */
    public void fireForTest(LivingEntity t, float p) { fire(t, p); }
    /** 試験用の入口: tへ角で1撃する。 */
    public void meleeForTest(LivingEntity t, float p) { strikeMelee(t, p); }
}
