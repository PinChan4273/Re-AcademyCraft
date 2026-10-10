package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 成功したThunder Boltの不変の見た目のスナップショット。ダメージ・資源・対象の決定権は持たない。 */
public record ThunderBoltEffect(ResourceLocation dimension,UUID player,int entityId,long session,
                                Vec3 origin,float yaw,float pitch,Vec3 impact,List<Vec3> aoeTargets){
    public static final int MAX_AOE_ARCS=64;
    private static final double MAX_SPAN_SQR=32*32;
    public ThunderBoltEffect{
        Objects.requireNonNull(dimension);Objects.requireNonNull(player);Objects.requireNonNull(origin);Objects.requireNonNull(impact);Objects.requireNonNull(aoeTargets);
        aoeTargets=List.copyOf(aoeTargets);
        if(dimension.toString().length()>AbilitySnapshot.MAX_ID_LENGTH||entityId<0||session<=0
                ||!point(origin)||!point(impact)||origin.distanceToSqr(impact)>MAX_SPAN_SQR
                ||!Float.isFinite(yaw)||yaw < -180||yaw > 180||!Float.isFinite(pitch)||pitch < -90||pitch > 90
                ||aoeTargets.size()>MAX_AOE_ARCS)throw new IllegalArgumentException("Invalid Thunder Bolt visual");
        for(var target:aoeTargets)if(!point(target)||impact.distanceToSqr(target)>MAX_SPAN_SQR)
            throw new IllegalArgumentException("Invalid Thunder Bolt AOE visual");
    }
    private static boolean point(Vec3 value){return value!=null&&bounded(value.x)&&bounded(value.y)&&bounded(value.z);}
    private static boolean bounded(double value){return Double.isFinite(value)&&Math.abs(value)<=33_554_431;}
    public ChargingLoopEffect grant(){return new ChargingLoopEffect(dimension,player,entityId,session,true,false);}
    public void encode(FriendlyByteBuf b){
        b.writeUtf(dimension.toString(),AbilitySnapshot.MAX_ID_LENGTH);b.writeUUID(player);b.writeVarInt(entityId);b.writeLong(session);
        vector(b,origin);b.writeFloat(yaw);b.writeFloat(pitch);vector(b,impact);b.writeVarInt(aoeTargets.size());for(var target:aoeTargets)vector(b,target);
    }
    public static ThunderBoltEffect decode(FriendlyByteBuf b){
        var dimension=ResourceLocation.parse(b.readUtf(AbilitySnapshot.MAX_ID_LENGTH));var player=b.readUUID();int entity=b.readVarInt();long session=b.readLong();
        var origin=vector(b);float yaw=b.readFloat(),pitch=b.readFloat();var impact=vector(b);int size=b.readVarInt();
        if(size<0||size>MAX_AOE_ARCS)throw new IllegalArgumentException("Invalid Thunder Bolt AOE count");
        var targets=new ArrayList<Vec3>(size);for(int i=0;i<size;i++)targets.add(vector(b));
        return new ThunderBoltEffect(dimension,player,entity,session,origin,yaw,pitch,impact,targets);
    }
    private static void vector(FriendlyByteBuf b,Vec3 value){b.writeDouble(value.x);b.writeDouble(value.y);b.writeDouble(value.z);}
    private static Vec3 vector(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
}
