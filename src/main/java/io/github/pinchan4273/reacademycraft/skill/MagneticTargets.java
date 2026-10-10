package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 現行のサーバー側の光線追跡に、原作CatElectromasterの実際の対象の規則を合わせたもの。
 * 古い設定のブロック・エンティティのリストとOreDictionaryは、データパックのタグで置き換える。
 */
public final class MagneticTargets {
    public static final double RANGE = 25;
    public static final TagKey<Block> NORMAL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("academy", "magnetic/normal"));
    public static final TagKey<Block> WEAK = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("academy", "magnetic/weak"));
    public static final TagKey<EntityType<?>> ENTITIES = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "magnetic"));
    public record Target(Vec3 anchor, @Nullable Entity entity) {
        public Vec3 position() { return entity == null ? anchor : entity.getEyePosition(); }
        public boolean alive(ServerPlayer player) {
            return (entity == null || (entity.isAlive() && !entity.isRemoved() && entity.level() == player.level()))
                    && player.level().hasChunkAt(BlockPos.containing(position()));
        }
    }
    private MagneticTargets() {}
    public static boolean isMetal(BlockState state) {
        // 原作isMetalBlockは、技能の経験値60%未満でも既に弱い金属を含む。
        return state.is(NORMAL) || state.is(WEAK);
    }
    public static @Nullable Target find(ServerPlayer player) {
        var level = player.serverLevel(); Vec3 start = player.getEyePosition(), direction = player.getLookAngle();
        Vec3 end = start.add(direction.scale(RANGE));
        for (int i = 0; i <= RANGE; i++) if (!level.hasChunkAt(BlockPos.containing(start.add(direction.scale(i))))) return null;
        // filNormalを使う原作Raytrace.traceLiving: 当たり判定の箱だけが止めるので、草・花・レール（当たり判定の箱が空）は
        // 原作と同じく通り抜ける。
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = block.getType() == HitResult.Type.MISS ? RANGE * RANGE : start.distanceToSqr(block.getLocation());
        Entity closest = null; Vec3 intercept = null;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().expandTowards(direction.scale(RANGE)).inflate(1),
                e -> e.isAlive() && ProjectileHitSeam.hittable(e) && !e.isSpectator())) {
            var box = entity.getBoundingBox().inflate(.3);
            var hit = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < nearest) {
                closest = entity; intercept = hit.get(); nearest = start.distanceToSqr(intercept);
            }
        }
        if (closest != null) return closest.getType().is(ENTITIES) ? new Target(intercept, closest) : null;
        return block.getType() == HitResult.Type.BLOCK && isMetal(level.getBlockState(block.getBlockPos()))
                ? new Target(block.getLocation(), null) : null;
    }
}
