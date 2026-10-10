package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.ThunderBoltEffect;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 原作ThunderBolt（ThunderBoltContext、WeAthFolD/KSkun）のコスト・クールダウン・ダメージ。原作の範囲の鈍化が誤って
 * 主対象を使っていたのは直している。
 */
public final class ThunderBolt {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("academy","thunder_bolt");
    public static final double RANGE=20,AOE_RANGE=8;
    private static long nextPresentationSession;
    private ThunderBolt() { }
    public static String cast(ServerPlayer player,PlayerAbilityData data) {
        if(!player.isAlive()||data.isReadOnly()||!ArcGen.CATEGORY.equals(data.getAbility())||!data.hasLearned(ID))return "academy.cast.unlearned";
        if(!data.isActive())return "academy.cast.inactive";
        if(data.isOverloadLocked())return "academy.cast.overload";
        if(data.getCooldown(ID)>0)return "academy.cast.cooldown";
        float exp=data.getProficiency(ID);var level=player.serverLevel();var start=player.getEyePosition();var direction=player.getLookAngle();var end=start.add(direction.scale(RANGE));
        for(int i=0;i<=RANGE;i++)if(!level.hasChunkAt(BlockPos.containing(start.add(direction.scale(i)))))return "academy.cast.unloaded";
        if(!data.consume(ID, (int)ArcGen.lerp(280,420,exp),ArcGen.lerp(50,27,exp),player.getAbilities().instabuild))return "academy.cast.cp";
        var block=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        Vec3 impact=block.getType()==HitResult.Type.MISS?end:block.getLocation();double nearest=start.distanceToSqr(impact);Entity target=null;
        for(var entity:level.getEntities(player,player.getBoundingBox().expandTowards(direction.scale(RANGE)).inflate(1),e->e.isAlive()&&ProjectileHitSeam.hittable(e)&&!e.isSpectator())) {
            var box=entity.getBoundingBox().inflate(.3);var hit=box.contains(start)?java.util.Optional.of(start):box.clip(start,end);
            if(hit.isPresent()&&start.distanceToSqr(hit.get())<nearest){nearest=start.distanceToSqr(hit.get());target=entity;}
        }
        if(target!=null)impact=target.getEyePosition(); // 原作のエンティティのRayTraceResultは足元を持ち、そこに目の高さを足す。
        var aoes=new ArrayList<LivingEntity>();
        for(var entity:level.getEntitiesOfClass(LivingEntity.class,new AABB(impact,impact).inflate(AOE_RANGE),e->e!=player&&e.isAlive()&&!e.isSpectator()))
            if(entity!=target&&entity.position().distanceToSqr(impact)<=AOE_RANGE*AOE_RANGE)aoes.add(entity);
        boolean effective=false;
        if(target!=null&&mayAttack(player,target)){attack(player,target,ArcGen.lerp(10,25,exp),exp,40);effective=true;}
        var visualAoes=new ArrayList<LivingEntity>();
        for(var entity:aoes)if(mayAttack(player,entity)){attack(player,entity,ArcGen.lerp(6,15,exp),exp,20);visualAoes.add(entity);effective=true;}
        data.addProficiency(ID,effective?.005f:.003f);
        data.setCooldown(ID,(int)ArcGen.lerp(120,50,exp)); // 原作のコンテキストでは発動前の熟練度を取り込む。
        Vec3 visualImpact=impact;var endpoints=visualAoes.stream()
                .sorted(Comparator.comparingDouble((LivingEntity e)->e.position().distanceToSqr(visualImpact)).thenComparingInt(Entity::getId))
                .limit(ThunderBoltEffect.MAX_AOE_ARCS).map(LivingEntity::getEyePosition).toList();
        long session=Math.incrementExact(nextPresentationSession);nextPresentationSession=session;
        var effect=new ThunderBoltEffect(level.dimension().location(),player.getUUID(),player.getId(),session,start,
                net.minecraft.util.Mth.wrapDegrees(player.getYRot()),net.minecraft.util.Mth.clamp(player.getXRot(),-90,90),visualImpact,endpoints);
        var listeners=new LinkedHashSet<ServerPlayer>();listeners.add(player);
        for(var observer:level.players())if(observer.distanceToSqr(player)<=25*25)listeners.add(observer);
        for(var listener:listeners)if(listener.level()==level)AcademyNetwork.send(listener,effect);
        level.playSound(null,player,AcademySounds.EM_ARC_STRONG.get(),SoundSource.AMBIENT,.6f,1f);
        return "";
    }
    private static boolean mayAttack(ServerPlayer caster,Entity target){
        return SkillCombat.mayAttack(caster, target, ID);
    }
    private static void attack(ServerPlayer caster,Entity target,float damage,float proficiency,int slowTicks){
        EMDamageHelper.attack(caster, target, ID, damage);
        if(target instanceof LivingEntity living&&living.isAlive()&&proficiency>.2f&&caster.getRandom().nextFloat()<.8f)
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,slowTicks,3));
    }
}
