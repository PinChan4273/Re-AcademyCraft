package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.TierSortingRegistry;

/**
 * 原作MineDetectのクライアントだけで動く、移動する球状の探査。鉱石はタグと採掘の段階で見分ける。
 * エンティティの生成、chunkの読み込み、ブロックの変更はしない。
 */
public final class OreSurvey {
    public static final int MAX_RESULTS = 8400;
    public static final TagKey<Block> DETECTABLE = TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("academy","detectable_ores"));
    public record Mark(BlockPos position,int color) {
        public Mark { position = position.immutable(); }
    }
    public interface Source {
        int minY(); int maxY(); // 建築上限: 下限は含み、上限は含まない。
        boolean isChunkLoaded(int chunkX,int chunkZ);
        BlockState state(BlockPos position);
    }
    private OreSurvey() { }
    public static boolean detectable(BlockState state) { return state.is(DETECTABLE); }
    public static int color(BlockState state,boolean advanced) {
        if (!advanced || !state.requiresCorrectToolForDrops()) return 0;
        if (TierSortingRegistry.isCorrectTierForDrops(Tiers.WOOD,state)) return 1;
        if (TierSortingRegistry.isCorrectTierForDrops(Tiers.STONE,state)) return 2;
        return 3; // 原作min(3, harvestLevel+1)。ダイヤや独自の段階を含む。
    }
    public static List<Mark> scan(Level level,Vec3 center,float requestedRange,boolean advanced) {
        return scan(new Source() {
            public int minY() { return level.getMinBuildHeight(); }
            public int maxY() { return level.getMaxBuildHeight(); }
            public boolean isChunkLoaded(int x,int z) { return level.hasChunk(x,z); }
            public BlockState state(BlockPos pos) { return level.getBlockState(pos); }
        },center,requestedRange,advanced);
    }
    public static List<Mark> scan(Source source,Vec3 center,float requestedRange,boolean advanced) {
        if (!Float.isFinite(requestedRange) || requestedRange < 1 || requestedRange > 30
                || !Double.isFinite(center.lengthSqr()) || Math.abs(center.x)>30000000 || Math.abs(center.z)>30000000)
            throw new IllegalArgumentException("Invalid ore survey bounds");
        double range = Math.min(28,requestedRange), squared = range*range;
        int minX=(int)Math.floor(center.x-range),maxX=(int)Math.ceil(center.x+range);
        int minZ=(int)Math.floor(center.z-range),maxZ=(int)Math.ceil(center.z+range);
        int minY=Math.max(source.minY(),(int)Math.floor(center.y-range)),maxY=Math.min(source.maxY()-1,(int)Math.ceil(center.y+range));
        if (minY>maxY) return List.of();
        // 状態を引く前に、chunkの読み込み状況をキャッシュする。無いchunkを要求・読み込みしない。
        var loaded=new boolean[maxX-minX+1][maxZ-minZ+1];
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)loaded[x-minX][z-minZ]=source.isChunkLoaded(x>>4,z>>4);
        var result=new ArrayList<Mark>();var pos=new BlockPos.MutableBlockPos();
        // 原作の走査順と球は、ブロックの中心ではなく整数座標を使う。
        for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++) {
            if(!loaded[x-minX][z-minZ])continue;
            double dx=x-center.x,dy=y-center.y,dz=z-center.z;
            if(dx*dx+dy*dy+dz*dz>squared)continue;
            pos.set(x,y,z);var state=source.state(pos);
            if(detectable(state)) {
                result.add(new Mark(pos,color(state,advanced)));
                if(result.size()==MAX_RESULTS)return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }
}
