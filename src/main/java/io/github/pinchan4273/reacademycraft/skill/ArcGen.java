package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.ArcGenEffect;
import java.util.LinkedHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 原作ArcGen（LambdaInnovation、WeAthFolD・KSkun）。判定はサーバーで行い、コスト・射程・ダメージ・経験値・クールダウンは原作のもの。
 */
public final class ArcGen {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "arc_gen");
    public static final ResourceLocation CATEGORY = ResourceLocation.fromNamespaceAndPath("academy", "electromaster");
    private static long nextPresentationSession;
    private ArcGen() {}
    public static float lerp(float from, float to, float progress) { return from + (to - from) * progress; }

    /** 翻訳キーを返す。発動に成功したとき（外れを含む）は空。 */
    public static String cast(ServerPlayer player, PlayerAbilityData data) {
        if (!player.isAlive() || data.isReadOnly()
                || !CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        float exp = data.getProficiency(ID), range = lerp(6, 15, exp);
        var level = player.serverLevel();
        Vec3 start = player.getEyePosition(), direction = player.getLookAngle(), end = start.add(direction.scale(range));
        // 入力パケットは任意の位置を要求したり、遠くのchunkを生成させたりできない。
        for (int i = 0; i <= Math.ceil(range); i++)
            if (!level.hasChunkAt(BlockPos.containing(start.add(direction.scale(Math.min(i, range)))))) return "academy.cast.unloaded";
        if (!data.consume(ID, lerp(30, 70, exp), lerp(18, 11, exp), player.getAbilities().instabuild)) return "academy.cast.cp";

        // 原作blockFilter: 水（静止・流水）またはfilNormal（当たり判定の箱）。溶岩は電弧を止めない。
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, player));
        double nearest = block.getType() == HitResult.Type.MISS ? range * range : start.distanceToSqr(block.getLocation());
        Entity target = null; Vec3 hit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        for (Entity entity : level.getEntities(player, player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1),
                e -> e.isAlive() && ProjectileHitSeam.hittable(e) && !e.isSpectator())) {
            var box = entity.getBoundingBox().inflate(0.3);
            var intercept = box.contains(start) ? java.util.Optional.of(start) : box.clip(start, end);
            if (intercept.isPresent() && start.distanceToSqr(intercept.get()) < nearest) {
                target = entity; hit = intercept.get(); nearest = start.distanceToSqr(hit);
            }
        }
        boolean entityHit = target != null;
        if (target != null) {
            EMDamageHelper.attack(player, target, ID, lerp(5, 9, exp));
            // 原作ArcGenはcanStunEnemyを宣言するが使っていないので、当てた相手を動かさない。
        } else if (block.getType() != HitResult.Type.MISS) {
            BlockPos pos = block.getBlockPos();
            if (level.getBlockState(pos).is(Blocks.WATER)) {
                if (exp > 0.5f && level.random.nextFloat() < 0.1f)
                    level.addFreshEntity(new ItemEntity(level, hit.x, hit.y, hit.z, new ItemStack(Items.COOKED_COD)));
            } else if (io.github.pinchan4273.reacademycraft.config.AcademyConfig.canDestroy(level, ID) && level.random.nextFloat() < lerp(0, 0.6f, exp) && level.isEmptyBlock(pos.above())
                    && level.mayInteract(player, pos.above())) {
                level.setBlockAndUpdate(pos.above(), Blocks.FIRE.defaultBlockState());
            }
        }
        if (entityHit || block.getType() != HitResult.Type.MISS)
            data.addProficiency(ID, entityHit ? lerp(0.0048f, 0.0072f, exp) : lerp(0.0018f, 0.0027f, exp));
        data.setCooldown(ID, (int) lerp(15, 5, data.getProficiency(ID)));
        // 見た目の長さは、ゲームプレイ上のより近い当たりの終点ではなく、原作の設定された射程に従う。
        long session=Math.incrementExact(nextPresentationSession);nextPresentationSession=session;
        var effect=new ArcGenEffect(level.dimension().location(),player.getUUID(),player.getId(),session,start,
                net.minecraft.util.Mth.wrapDegrees(player.getYRot()),net.minecraft.util.Mth.clamp(player.getXRot(),-90,90),range);
        var listeners=new LinkedHashSet<ServerPlayer>();listeners.add(player);
        for(var observer:level.players())if(observer.distanceToSqr(player)<=25*25)listeners.add(observer);
        for(var listener:listeners)if(listener.level()==level)AcademyNetwork.send(listener,effect);
        // 原作FollowEntitySound: ambientカテゴリ、音量0.5、ピッチ1.0。
        // バニラのエンティティに結び付いたパケットが、各クライアントで術者に付いて行く。
        level.playSound(null, player, AcademySounds.EM_ARC_WEAK.get(), SoundSource.AMBIENT, 0.5f, 1.0f);
        return "";
    }
}
