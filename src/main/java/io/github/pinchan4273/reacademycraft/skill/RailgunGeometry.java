package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 原作RangedRayDamage/Plotterの幾何。位置は不変で、横方向の基底は正しく求めている。ワールドの変更や
 * chunkの読み込みはしない。50は古いエンティティの距離。地形は51メートルではなく51ボクセルの刻みを使う。
 *
 * Meltdownerと共有する。Meltdownerは同じ原作のクラスを独自の半径で作る。下のRailgunの名を持つ定数はRailgunの半径で、既定値のまま。
 * RangedRayを参照。
 */
public final class RailgunGeometry {
    public static final int MAX_INCREMENTS=51;
    public static final double ENTITY_LENGTH=50,RADIUS=2,ENTITY_RADIUS=2.4;
    private RailgunGeometry(){}
    public static Vec3 direction(Vec3 vector){
        double length=vector.lengthSqr();
        if(!Double.isFinite(length)||length<1e-12)throw new IllegalArgumentException("Invalid beam direction");
        return vector.scale(1/Math.sqrt(length));
    }
    public static List<BlockPos> rayOrigins(Vec3 start,Vec3 look,RandomSource random){return rayOrigins(start,look,RADIUS,random);}
    public static List<BlockPos> rayOrigins(Vec3 start,Vec3 look,double radius,RandomSource random){
        if(!Double.isFinite(start.lengthSqr())||Math.abs(start.x)>30_000_001||Math.abs(start.z)>30_000_001||Math.abs(start.y)>30_000_001)
            throw new IllegalArgumentException("Invalid beam origin");
        if(!Double.isFinite(radius)||radius<=0||radius>4)throw new IllegalArgumentException("Invalid beam radius");
        var forward=direction(look);var horizontal=new Vec3(forward.z,0,-forward.x);
        var right=horizontal.lengthSqr()<1e-12?new Vec3(1,0,0):horizontal.normalize();
        var up=forward.cross(right);var origins=new LinkedHashSet<BlockPos>();
        for(double s=-radius;s<=radius;s+=.9)for(double t=-radius;t<=radius;t+=.9){
            double varied=radius*(.9+random.nextDouble()*.2);if(s*s+t*t>varied*varied)continue;
            origins.add(BlockPos.containing(start.add(right.scale(s)).add(up.scale(t))));
        }
        return List.copyOf(origins);
    }
    public static boolean containsTarget(Vec3 start,Vec3 look,Vec3 point){return containsTarget(start,look,point,ENTITY_RADIUS);}
    public static boolean containsTarget(Vec3 start,Vec3 look,Vec3 point,double entityRadius){
        var forward=direction(look);var delta=point.subtract(start);double along=delta.dot(forward);
        return Double.isFinite(along)&&along>=0&&along<=ENTITY_LENGTH&&delta.cross(forward).lengthSqr()<entityRadius*entityRadius;
    }
    /** 原作の式を保つ: 縦ではなく横方向のダメージの減衰。 */
    public static float damageFactor(Vec3 start,Vec3 look,Vec3 point){
        double lateral=point.subtract(start).cross(direction(look)).length();
        if(!Double.isFinite(lateral))throw new IllegalArgumentException("Invalid target position");
        return (float)(1-.8*Math.min(ENTITY_LENGTH,lateral)/ENTITY_LENGTH);
    }
    public static List<BlockPos> blockRay(BlockPos origin,Vec3 look,int increments){
        if(increments<0||increments>MAX_INCREMENTS)throw new IllegalArgumentException("Unbounded beam traversal");
        var ray=direction(look);var plotter=new Plotter(origin,ray);var result=new ArrayList<BlockPos>(increments);
        for(int i=0;i<increments;i++)result.add(plotter.next());return List.copyOf(result);
    }
    /**
     * cn.academy.util.Plotterの主軸に沿った走査のJava 17/BlockPos移植。
     * 同点の順序（Y、Z、次に主軸）を保つ。これが地形のコストを決める。
     */
    private static final class Plotter {
        private final int axis,x0,y0,z0,sign;
        private final double dyx,dzx;
        private int x,y,z;
        Plotter(BlockPos start,Vec3 direction){
            double dx=direction.x,dy=direction.y,dz=direction.z;int sx=start.getX(),sy=start.getY(),sz=start.getZ();
            double ax=Math.abs(dx),ay=Math.abs(dy),az=Math.abs(dz);
            if(az>ay&&az>ax){axis=2;double d=dz;dz=dx;dx=d;int i=sz;sz=sx;sx=i;}
            else if(ay>ax){axis=1;double d=dy;dy=dx;dx=d;int i=sy;sy=sx;sx=i;}
            else axis=0;
            x=x0=sx;y=y0=sy;z=z0=sz;dyx=dy/dx;dzx=dz/dx;sign=dx>0?1:-1;
        }
        BlockPos next(){int next=x+sign;double valY=y0+(next-x0)*dyx,valZ=z0+(next-x0)*dzx;
            if(Math.abs(valY-y)>.5)y+=(int)Math.signum(dyx)*sign;
            else if(Math.abs(valZ-z)>.5)z+=(int)Math.signum(dzx)*sign;
            else x=next;
            return axis==2?new BlockPos(z,y,x):axis==1?new BlockPos(y,x,z):new BlockPos(x,y,z);
        }
    }
}
