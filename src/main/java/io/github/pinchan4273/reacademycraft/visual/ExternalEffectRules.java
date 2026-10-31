package io.github.pinchan4273.reacademycraft.visual;

import io.github.pinchan4273.reacademycraft.api.client.BeamResult;
import io.github.pinchan4273.reacademycraft.api.client.EffectRequest;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 外部の演出の要求の検査（演出API版2）。描画とMinecraftのクライアントに依存しない（GameTestで確かめる）。
 * 結果はnullなら通る。順番: 種類 → 持ち主 → 効果ID → 次元 → 座標 → 種類ごとの欄。
 */
public final class ExternalEffectRules {
    private ExternalEffectRules() { }

    /** 電弧の模様の長さ（LegacyArcGeometryのWEAK・STRONGの範囲）。これより長い電弧は作れない。 */
    public static final double MAX_ARC_LENGTH = 20;
    /** 軌跡などの、位置から向かう先までの最大の長さ（外部の光線と同じ）。 */
    public static final double MAX_LENGTH = ExternalBeamRules.MAX_LENGTH;
    public static final float MAX_WAVE_SCALE = 8, MAX_DEBRIS_RADIUS = 6, MAX_SHIELD_RADIUS = 8, MAX_SURROUND_SIZE = 32, MAX_PLASMA_SCALE = 4;
    /** 細い電弧の模様の長さ（LegacyArcGeometryのTHIN_CONTINUOUSの範囲）。 */
    public static final double MAX_CONTINUOUS_ARC = 20;
    private static final double MAX_HORIZONTAL = 30_000_000, MAX_VERTICAL = 20_000;

    public static @Nullable BeamResult check(EffectRequest request, @Nullable ResourceLocation currentDimension, Predicate<String> modLoaded) {
        if (request == null || request.style() == null) return BeamResult.INVALID;
        var owner = request.ownerModId();
        if (owner == null || owner.isEmpty() || owner.equals("academy") || !modLoaded.test(owner)) return BeamResult.INVALID;
        if (request.effectId() <= 0) return BeamResult.INVALID;
        if (request.dimension() == null || currentDimension == null || !request.dimension().equals(currentDimension)) return BeamResult.INVALID;
        if (!bounded(request.position())) return BeamResult.INVALID;
        return switch (request.style()) {
            case ARC_WEAK, ARC_STRONG, ARC_AREA -> between(request, MAX_ARC_LENGTH);
            case TELEPORT_TRAIL, SHIFT_TRAIL, WAVE_RINGS, BLOOD_SPRAY -> {
                var refused = between(request, MAX_LENGTH);
                if (refused != null) yield refused;
                yield request.style() == io.github.pinchan4273.reacademycraft.api.client.EffectStyle.WAVE_RINGS ? scale(request, MAX_WAVE_SCALE) : null;
            }
            case GROUND_DEBRIS -> scale(request, MAX_DEBRIS_RADIUS);
            case ARC_CONTINUOUS -> between(request, MAX_CONTINUOUS_ARC);
            case ARC_SURROUND, ARC_SURROUND_NORMAL, ARC_SURROUND_BOLD -> scale(request, MAX_SURROUND_SIZE);
            case LIGHT_SHIELD -> {
                if (!entity(request) || !bounded(request.target())) yield BeamResult.INVALID;
                yield scale(request, MAX_SHIELD_RADIUS);
            }
            case RAILGUN_HAND, JET_SHIELD, STORM_WING, MINE_RAY_BASIC, MINE_RAY_EXPERT, MINE_RAY_LUCK -> entity(request) ? null : BeamResult.INVALID;
            case PLASMA_CANNON -> request.target() != null && !bounded(request.target()) ? BeamResult.INVALID : scale(request, MAX_PLASMA_SCALE);
            case ARC_BURST, LIGHTNING, BLOOD_SPLASH, BLOOD_SPLASH_LARGE, CRITICAL_FORMULA, ORB, RIPPLE -> null;
        };
    }

    private static boolean entity(EffectRequest request) { return request.entityId() >= 0 && request.entityUuid() != null; }
    private static @Nullable BeamResult between(EffectRequest request, double max) {
        if (!bounded(request.target())) return BeamResult.INVALID;
        double length = request.position().distanceTo(request.target());
        return length > 0 && length <= max ? null : BeamResult.INVALID;
    }
    private static @Nullable BeamResult scale(EffectRequest request, float max) {
        return request.scale() > 0 && request.scale() <= max ? null : BeamResult.INVALID;
    }
    private static boolean bounded(@Nullable Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= MAX_HORIZONTAL && Math.abs(v.z) <= MAX_HORIZONTAL && Math.abs(v.y) <= MAX_VERTICAL;
    }
}
