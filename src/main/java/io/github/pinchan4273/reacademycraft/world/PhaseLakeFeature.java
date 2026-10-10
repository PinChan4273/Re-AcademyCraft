package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * WeAthFolDのWorldGenPhaseLiqの楕円体の湖を、現行のconfigured/placed featureの登録で行う。
 * 古い0.3の確率、Y5〜34の候補、0.6の楕円の閾値を保つ。既存のchunkに後から生成することはない。
 */
public final class PhaseLakeFeature extends Feature<NoneFeatureConfiguration> {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "academy");
    public static final RegistryObject<PhaseLakeFeature> TYPE = FEATURES.register("phase_pool", () -> new PhaseLakeFeature(NoneFeatureConfiguration.CODEC));
    public static void register(IEventBus bus) { FEATURES.register(bus); }
    private PhaseLakeFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        if (!AcademyConfig.GENERATE_PHASE_LIQUID.get() || !level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        var candidate = candidate(context.origin(), context.random());
        return candidate != null && generate(level, context.random(), candidate);
    }
    /** nullは飛ばした試行。乱数を引く順は原作のPhaseLiquidGeneratorと一致する。 */
    public static @Nullable BlockPos candidate(BlockPos chunkOrigin, RandomSource random) {
        if (random.nextDouble() >= .3) return null;
        return new BlockPos(chunkOrigin.getX() + random.nextInt(16) + 8, 5 + random.nextInt(30), chunkOrigin.getZ() + random.nextInt(16) + 8);
    }
    public static boolean[] mask(RandomSource random) {
        var buffer = new boolean[2048];
        for (int i = 0, loops = random.nextInt(4) + 4; i < loops; i++) {
            double dx = random.nextDouble() * 6 + 3, dy = random.nextDouble() * 4 + 2, dz = random.nextDouble() * 6 + 3;
            double cx = random.nextDouble() * (14 - dx) + 1 + dx / 2;
            double cy = random.nextDouble() * (4 - dy) + 2 + dy / 2;
            double cz = random.nextDouble() * (14 - dz) + 1 + dz / 2;
            for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) for (int y = 1; y < 7; y++) {
                double a = (x - cx) / (dx / 2), b = (y - cy) / (dy / 2), c = (z - cz) / (dz / 2);
                if (a * a + b * b + c * c < .6) buffer[index(x, y, z)] = true;
            }
        } return buffer;
    }
    private static int index(int x, int y, int z) { return (x * 16 + z) * 8 + y; }
    private static boolean boundary(boolean[] mask, int x, int y, int z) {
        return !mask[index(x, y, z)] && (x < 15 && mask[index(x + 1, y, z)] || x > 0 && mask[index(x - 1, y, z)]
                || z < 15 && mask[index(x, y, z + 1)] || z > 0 && mask[index(x, y, z - 1)]
                || y < 7 && mask[index(x, y + 1, z)] || y > 0 && mask[index(x, y - 1, z)]);
    }
    public static boolean generate(WorldGenLevel level, RandomSource random, BlockPos candidate) {
        int x = candidate.getX() - 8, z = candidate.getZ() - 8, y = candidate.getY();
        if (level.isOutsideBuildHeight(candidate) || !level.hasChunkAt(new BlockPos(x, y, z))) return false;
        while (y > 5 && level.isEmptyBlock(new BlockPos(x, y, z))) y--;
        if (y <= 4) return false;
        y -= 4; var base = new BlockPos(x, y, z); var cells = mask(random);
        // 書く前に領域全体を検証する。ワールド生成でブロックエンティティのインベントリや岩盤を壊してはならない。
        for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) for (int j = 0; j < 8; j++) {
            var p = base.offset(i, j, k);
            if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p) || !level.ensureCanWrite(p)) return false;
            var state = level.getBlockState(p);
            if (cells[index(i, j, k)] && (state.hasBlockEntity() || state.getDestroySpeed(level, p) < 0)) return false;
            if (boundary(cells, i, j, k)) {
                if (j >= 4 && !state.getFluidState().isEmpty()) return false;
                if (j < 4 && !state.isSolid() && !state.is(PhaseContent.BLOCK.get())) return false;
            }
        }
        for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) for (int j = 0; j < 8; j++)
            if (cells[index(i, j, k)]) level.setBlock(base.offset(i, j, k), j >= 4 ? Blocks.AIR.defaultBlockState() : PhaseContent.BLOCK.get().defaultBlockState(), 2);
        // 原作の土→草ブロックの分岐は土の発する光（バニラでは常に0）を問い合わせていたので、表面は変わらなかった。
        // 凍結はBiome.shouldFreezeを通してバニラの水だけのまま。位相液体を氷に置き換えない。
        for (int i = 0; i < 16; i++) for (int k = 0; k < 16; k++) {
            var p = base.offset(i, 4, k);
            if (level.getBiome(p).value().shouldFreeze(level, p, false)) level.setBlock(p, Blocks.ICE.defaultBlockState(), 2);
        }
        return true;
    }
}
