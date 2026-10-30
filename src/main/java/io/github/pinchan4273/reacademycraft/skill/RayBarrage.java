package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.world.entity.SilbarnEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 原作RayBarrage（WeAthFolD/KSkun）: meltdownerのレベル4。
 *
 * 1回押す。20ブロック以内の、生きていて砕けていない投げたSilicon Barnを見ていれば、それを砕いて散る一斉射を撃つ: 術者の前方55度の
 * 円錐で、その中のすべての生きたエンティティにダメージを与える。そうでなければ単純な光線で、直接当たったものにダメージを与える。
 *
 * 原作の円錐の判定には2つの不具合があり、意図した修正を推測するのは挙動の創作になるので、Light ShieldやJet Engineの入れ替わった引数と
 * 同じく、両方とも書かれたとおりに移植している。コスト自体にはその不具合は無い: 能力自身のconsume()呼び出しの原作の
 * overload/CPの引数順は、ここでは正しい。
 */
public final class RayBarrage {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "ray_barrage");
    /** 原作RAY_DIST/DISPLAY_RAY_DIST（どちらも20）と、円錐の全幅。 */
    private static final double RANGE = 20, CONE_DEGREES = 55;

    private RayBarrage() { }

    public static String cast(ServerPlayer player, PlayerAbilityData data) {
        if (!player.isAlive() || data.isReadOnly()
                || !AbilityCategory.MELTDOWNER.id().equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        float exp = data.getProficiency(ID);
        // 原作自身のconsume(overload, cp)呼び出しは、兄弟の技能と違い入れ替わっていない。
        if (!data.consume(ID, ArcGen.lerp(450, 380, exp), ArcGen.lerp(300, 140, exp), player.getAbilities().instabuild))
            return "academy.cast.cp";
        var target = findSilbarn(player);
        // 原作EntityBarrageRayPre.onFirstUpdate: 前段の光線は常に術者の頭の高さから。
        player.level().playSound(null, player.getX(), player.getY() + 1.6, player.getZ(), io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_RAY_SMALL.get(), net.minecraft.sounds.SoundSource.AMBIENT, .8f, 1f);
        // 原作c_spawnPreRay: 前段の光線は常に術者の頭から射撃が届いた所まで。Silbarnに当たった場合は50tick、当たらない場合は30tick生きる。
        var head = player.position().add(0, 1.6, 0);
        var to = target != null ? target.position() : plainRay(player, data, exp);
        beam(player, head, to, target != null ? io.github.pinchan4273.reacademycraft.network.MdRayEffect.BARRAGE_PRE_HIT
                : io.github.pinchan4273.reacademycraft.network.MdRayEffect.BARRAGE_PRE);
        if (target != null) barrage(player, data, target, exp);
        data.setCooldown(ID, (int) ArcGen.lerp(100, 40, exp));
        data.addProficiency(ID, .005f);
        return "";
    }

    /**
     * 術者の視線に沿って20ブロック先までの、（ブロック、生きていて砕けていないSilbarn）のうち最も近いもの。
     * 原作の1回の光線追跡と同じく、途中のブロックはその向こうのSilbarnを対象外にする。
     */
    private static SilbarnEntity findSilbarn(ServerPlayer player) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(RANGE));
        Level level = player.level();
        if (!level.hasChunkAt(BlockPos.containing(start)) || !level.hasChunkAt(BlockPos.containing(end))) return null;
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        SilbarnEntity found = null;
        for (var entity : level.getEntities(player, player.getBoundingBox().inflate(RANGE + 1),
                e -> e instanceof SilbarnEntity s && s.liveTarget())) {
            var intercept = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                nearest = start.distanceToSqr(intercept.get()); found = (SilbarnEntity) entity;
            }
        }
        return found;
    }

    /**
     * 原作はSilbarn自体を砕き、次に55度の円錐の中のすべての生きたエンティティにダメージを与える（yawとpitchのどちらも半分ずつではなく
     * その全幅を使い、pitchの見積もり自体も対象のZのずれしか見ない。どちらも原作の不具合で、書かれたとおりに移植している）。
     */
    private static void barrage(ServerPlayer player, PlayerAbilityData data, SilbarnEntity target, float exp) {
        target.shatter();
        // 原作EntityMdRayBarrage.onFirstUpdate: Silbarnの位置で散る光線。
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), io.github.pinchan4273.reacademycraft.world.AcademySounds.MD_RAY_SMALL.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        // 原作c_spawnBarrage: 散る光線はSilbarnの位置から始まり、術者の視線の周りに広がる。
        beam(player, target.position(), target.position().add(player.getLookAngle().scale(15)),
                io.github.pinchan4273.reacademycraft.network.MdRayEffect.BARRAGE);
        float yaw = player.getYRot(), pitch = player.getXRot();
        float minYaw = (float) (yaw - CONE_DEGREES / 2), maxYaw = (float) (yaw + CONE_DEGREES / 2);
        float minPitch = pitch - (float) CONE_DEGREES, maxPitch = pitch + (float) CONE_DEGREES;
        for (var entity : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(RANGE),
                // 原作EntitySelectors.exclude(silbarn, player): どのエンティティも。ドロップしたアイテムや経験値オーブも含む。
                e -> e != target && e.isAlive() && !e.isSpectator())) {
            double dx = entity.getX() - player.getX();
            // 原作自身のdyの式（目の高さの差）。原作自身のgetEyeHeight()の使い方と一致する。
            double dy = (entity.getY() + entity.getEyeHeight()) - (player.getY() + player.getEyeHeight());
            double dz = entity.getZ() - player.getZ();
            float eyaw = (float) -(Math.atan2(dx, dz) * 180 / Math.PI);
            // 原作: Math.sqrt(dz*dz + dz*dz)。dxとdzではなく、文字どおりdzを2回使っている。
            float epitch = (float) -(Math.atan2(dy, Math.sqrt(dz * dz + dz * dz)) * 180 / Math.PI);
            if (angleYawInRange(minYaw, maxYaw, eyaw) && minPitch <= epitch && epitch <= maxPitch)
                MDDamageHelper.attack(player, data, entity, ID, ArcGen.lerp(10, 18, exp));
        }
    }

    /** meltdownerの他の光線（ElectronBomb）と同じ表示の範囲。 */
    private static final double PRESENTATION_RANGE = 20;
    private static void beam(ServerPlayer player, Vec3 from, Vec3 to, int kind) {
        var effect = new io.github.pinchan4273.reacademycraft.network.MdRayEffect(player.level().dimension().location(), player.getUUID(),
                player.getId(), player.level().getGameTime() + 1, from, to, kind);
        // 術者を先に: テストの治具プレイヤーはlevelのプレイヤー一覧に居ない。
        io.github.pinchan4273.reacademycraft.network.AcademyNetwork.send(player, effect);
        for (var observer : player.serverLevel().players())
            if (observer != player && observer.distanceToSqr(player) <= PRESENTATION_RANGE * PRESENTATION_RANGE)
                io.github.pinchan4273.reacademycraft.network.AcademyNetwork.send(observer, effect);
    }
    /** 視界にSilbarnが無い場合: ダメージを与える1本の光線で、原作の散らない側そのもの。射撃が届いた所を返し、前段の光線はそこまで描く。 */
    private static Vec3 plainRay(ServerPlayer player, PlayerAbilityData data, float exp) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(RANGE));
        Level level = player.level();
        if (!level.hasChunkAt(BlockPos.containing(start)) || !level.hasChunkAt(BlockPos.containing(end))) return end;
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        Entity target = null;
        for (var entity : level.getEntities(player, player.getBoundingBox().inflate(RANGE + 1),
                e -> e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e) && !(e instanceof SilbarnEntity))) {
            var intercept = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                nearest = start.distanceToSqr(intercept.get()); target = entity;
            }
        }
        if (target != null) MDDamageHelper.attack(player, data, target, ID, ArcGen.lerp(25, 60, exp));
        // 原作Raytrace.getLookingPos: ブロック、エンティティ、届く範囲の終点のうち最も近いもの。
        return block.getType() == HitResult.Type.MISS && target == null ? end
                : target != null ? start.add(end.subtract(start).normalize().scale(Math.sqrt(nearest))) : block.getLocation();
    }

    /**
     * 原作cn.lambdalib2.util.MathUtils.angleYawinRange/wrapYawAngleをそのまま移植: すべての角度を[0, 360)に収めて含むかを判定し、
     * start > endなら折り返し点をまたぐ。
     */
    private static boolean angleYawInRange(float start, float end, float angle) {
        if (end < start) return false;
        if (end - start >= 360f) return true;
        float ss = wrapYaw(start), se = wrapYaw(end), sa = wrapYaw(angle);
        if (ss > se) return ss <= sa || sa <= se;
        return ss <= sa && sa <= se;
    }
    private static float wrapYaw(float a) { float r = a % 360f; return r < 0 ? r + 360f : r; }
}
