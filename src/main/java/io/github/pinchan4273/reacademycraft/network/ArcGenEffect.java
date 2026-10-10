package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** 成功したArc Genの不変の見た目のスナップショット。当たり・ダメージ・資源の決定権は持たない。 */
public record ArcGenEffect(ResourceLocation dimension,UUID player,int entityId,long session,
                           Vec3 origin,float yaw,float pitch,float length){
    public ArcGenEffect{
        Objects.requireNonNull(dimension);Objects.requireNonNull(player);Objects.requireNonNull(origin);
        if(dimension.toString().length()>AbilitySnapshot.MAX_ID_LENGTH||entityId<0||session<=0
                ||!bounded(origin.x)||!bounded(origin.y)||!bounded(origin.z)
                ||!Float.isFinite(yaw)||yaw < -180||yaw > 180
                ||!Float.isFinite(pitch)||pitch < -90||pitch > 90
                ||!Float.isFinite(length)||length<=0||length>32)throw new IllegalArgumentException("Invalid Arc Gen visual");
    }
    private static boolean bounded(double value){return Double.isFinite(value)&&Math.abs(value)<=33_554_431;}
    public ChargingLoopEffect grant(){return new ChargingLoopEffect(dimension,player,entityId,session,true,false);}
    public void encode(FriendlyByteBuf b){
        b.writeUtf(dimension.toString(),AbilitySnapshot.MAX_ID_LENGTH);b.writeUUID(player);b.writeVarInt(entityId);b.writeLong(session);
        b.writeDouble(origin.x);b.writeDouble(origin.y);b.writeDouble(origin.z);b.writeFloat(yaw);b.writeFloat(pitch);b.writeFloat(length);
    }
    public static ArcGenEffect decode(FriendlyByteBuf b){return new ArcGenEffect(ResourceLocation.parse(b.readUtf(AbilitySnapshot.MAX_ID_LENGTH)),
            b.readUUID(),b.readVarInt(),b.readLong(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat(),b.readFloat());}
}
