package io.github.pinchan4273.reacademycraft.skill;

import java.util.function.DoubleSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 原作TPSkillHelper（WeAthFolD）: テレポーターのすべての攻撃は、当たる前にここでクリティカルの判定をする。
 *
 * 3つの段を、成功するまで順に試す: 1.3倍、1.6倍、2.6倍。確率はテレポーターの2つの受動技能だけから来る（第1段は両方から、
 * 他の2段はSpace Fluctuationだけから）。習得していない受動技能は何も寄与しない。クリティカルは術者に伝え、両方の受動技能に
 * 経験値を与え、（原作addSkillExpにより）まだ習得していない方を習得させる: 原作では、Dimension Folding Theoremだけを持つ術者が
 * 最初のクリティカルでレベル4のSpace Fluctuationを習得する。
 */
public final class TPSkillHelper {
    /** 原作の倍率。 */
    public static final float[] RATES = { 1.3f, 1.6f, 2.6f };
    private TPSkillHelper() { }

    /** 原作prob(data, level)。 */
    public static float probability(PlayerAbilityData data, int level) {
        float dim = data.hasLearned(DimFoldingTheorem.ID) ? data.getProficiency(DimFoldingTheorem.ID) : -1;
        float fluct = data.hasLearned(SpaceFluctuation.ID) ? data.getProficiency(SpaceFluctuation.ID) : -1;
        return switch (level) {
            case 0 -> tryLerp(.1f, .2f, dim) + tryLerp(.18f, .25f, fluct);
            case 1 -> tryLerp(.1f, .15f, fluct);
            case 2 -> tryLerp(.01f, .03f, fluct);
            default -> throw new IllegalArgumentException("Invalid critical tier");
        };
    }
    private static float tryLerp(float from, float to, float progress) { return progress == -1 ? 0 : ArcGen.lerp(from, to, progress); }

    /** 3つの段を判定し、ダメージ倍率を返す。何も出なければ1。原作は試す段ごとに新しく乱数を引く。 */
    public static float critical(ServerPlayer caster, PlayerAbilityData data, DoubleSupplier roll) {
        return critical(caster, data, null, roll);
    }
    /**
     * 同じ判定で、術者自身のクライアントに当たったものの位置へ原作の式を描かせる。
     * 原作はそれを術者だけへ送るので、他の誰も他人のクリティカルを見ない。
     */
    public static float critical(ServerPlayer caster, PlayerAbilityData data, @javax.annotation.Nullable Entity target, DoubleSupplier roll) {
        for (int tier = 0; tier < RATES.length; tier++) {
            if (roll.getAsDouble() < probability(data, tier)) {
                if (target != null) io.github.pinchan4273.reacademycraft.network.SkillBurst.showTo(caster, target,
                        io.github.pinchan4273.reacademycraft.network.SkillBurst.CRITICAL, tier);
                float rate = RATES[tier];
                // 原作はこれを%fで整形するが、Minecraftの翻訳の整形は対応していないので、倍率を文字列として渡す。
                caster.sendSystemMessage(Component.translatable("academy.teleporter.crithit", Float.toString(rate)));
                data.addSkillExperience(DimFoldingTheorem.ID, (tier + 1) * .005f);
                data.addSkillExperience(SpaceFluctuation.ID, .0001f);
                return rate;
            }
        }
        return 1;
    }

    /** 原作TPC_ID: 技能が行うすべてのテレポートを、プレイヤーの永続データ上で数える。 */
    public static final String TELEPORT_COUNT = "ac_tpcount";
    public static void incrTPCount(ServerPlayer player) {
        var tag = player.getPersistentData();
        tag.putInt(TELEPORT_COUNT, tag.getInt(TELEPORT_COUNT) + 1);
    }
    public static int teleportCount(ServerPlayer player) { return player.getPersistentData().getInt(TELEPORT_COUNT); }

    /**
     * 術者をここへテレポートしてよいかを、Forge自身の取り消し可能なテレポートイベントで問う。保護modがエンダーパールと同じように
     * 拒否でき、リスナーは行き先を動かすこともできる。原作にはこのような仕組みが無く、地形の技能も同様にBreakEventを送る。
     * 使う行き先を返す。テレポートが拒否されればnull。
     */
    public static net.minecraft.world.phys.Vec3 permit(ServerPlayer player, net.minecraft.world.phys.Vec3 target) {
        var event = new net.minecraftforge.event.entity.EntityTeleportEvent(player, target.x, target.y, target.z);
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event)) return null;
        return event.getTarget();
    }
    /** 原作のテレポート: 降りて、setPositionAndUpdate、落下をリセット。 */
    public static void teleport(ServerPlayer player, net.minecraft.world.phys.Vec3 target) {
        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(target.x, target.y, target.z);
        player.fallDistance = 0;
    }

    /** 原作Raytrace.traceLivingが見つけたもの: 勝ったエンティティまたはブロックと、原作がそれについて返す位置。 */
    public record Trace(net.minecraft.world.phys.Vec3 position, Entity entity, net.minecraft.world.phys.BlockHitResult block) { }
    /**
     * 原作Raytrace.traceLiving（LambdaLib2）: 目から視線に沿って与えた距離まで、選別が受け入れるエンティティ（術者は除く）と
     * 衝突できるブロックに対して調べる。0.3広げた箱に光線が最初に当たったエンティティが、エンティティの中で勝つ。その結果は原作の
     * RayTraceResult(entity)で、光線が当たった点ではなくエンティティ自身の位置を持ち、その位置をブロックへの当たりと比べる
     * （同じならエンティティが勝つ）。外れは届く範囲の終点を返す。
     */
    public static Trace traceLiving(net.minecraft.world.entity.player.Player player, double range, java.util.function.Predicate<Entity> filter) {
        var start = player.getEyePosition();
        return trace(player, start, start.add(player.getLookAngle().scale(range)), filter);
    }
    /** 2点間の原作Raytrace.perform。術者は見つけない。traceLivingを参照。 */
    public static Trace trace(net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.Vec3 start, net.minecraft.world.phys.Vec3 end,
                              java.util.function.Predicate<Entity> filter) {
        var level = player.level();
        net.minecraft.world.phys.BlockHitResult block = level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))
                && level.hasChunkAt(net.minecraft.core.BlockPos.containing(start))
                ? level.clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player)) : null;
        if (block != null && block.getType() == net.minecraft.world.phys.HitResult.Type.MISS) block = null;
        Entity entity = null; double nearest = Double.MAX_VALUE;
        for (var candidate : level.getEntities(player, new net.minecraft.world.phys.AABB(start, end).inflate(1),
                e -> e.isAlive() && !e.isSpectator() && ProjectileHitSeam.hittable(e) && filter.test(e))) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                nearest = start.distanceToSqr(intercept.get()); entity = candidate;
            }
        }
        if (entity != null && (block == null || start.distanceTo(entity.position()) <= start.distanceTo(block.getLocation())))
            return new Trace(entity.position(), entity, null);
        if (block != null) return new Trace(block.getLocation(), null, block);
        return new Trace(end, null, null);
    }
    /** 原作EntitySelectors.living()。 */
    public static boolean living(Entity e) {
        return e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }

    public static boolean attack(ServerPlayer caster, PlayerAbilityData data, Entity target, ResourceLocation skill, float damage) {
        return SkillCombat.attack(caster, target, skill, damage * critical(caster, data, target, () -> caster.getRandom().nextFloat()));
    }
    public static boolean attackIgnoreArmor(ServerPlayer caster, PlayerAbilityData data, Entity target, ResourceLocation skill, float damage) {
        return SkillCombat.attackIgnoreArmor(caster, target, skill, damage * critical(caster, data, target, () -> caster.getRandom().nextFloat()));
    }
}
