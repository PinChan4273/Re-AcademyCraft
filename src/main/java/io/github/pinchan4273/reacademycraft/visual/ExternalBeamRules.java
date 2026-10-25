package io.github.pinchan4273.reacademycraft.visual;

import io.github.pinchan4273.reacademycraft.api.client.BeamRequest;
import io.github.pinchan4273.reacademycraft.api.client.BeamResult;
import io.github.pinchan4273.reacademycraft.api.client.BeamStyle;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 外部の演出の光線の要求の検査（演出API版1）。描画とMinecraftのクライアントに依存しない（GameTestで確かめる）。
 * 結果はnullなら通る。順番: 見た目（無い・持たない）→ 持ち主 → 効果ID → 次元 → 座標 → 長さ。
 */
public final class ExternalBeamRules {
    private ExternalBeamRules() { }

    /** 外部の光線の最大の長さ（ブロック）。人工能力の最大の射程（96）まで。自然能力の光線は64のまま（通信・アニメーション・描画）。 */
    public static final double MAX_LENGTH = 96;
    /** 座標の範囲（水平は世界の境界の最大、高さは大きく取った範囲）。この外は誤りとして断る。 */
    private static final double MAX_HORIZONTAL = 30_000_000, MAX_VERTICAL = 20_000;

    /**
     * @param request      要求
     * @param currentDimension 今のクライアントの世界の次元（無ければnull。その場合は呼び出し側がNOT_READYを先に返す）
     * @param modLoaded    MOD IDが読み込まれているか
     */
    public static @Nullable BeamResult check(BeamRequest request, @Nullable ResourceLocation currentDimension, Predicate<String> modLoaded) {
        if (request == null || request.style() == null) return BeamResult.INVALID;
        if (request.style() != BeamStyle.MELTDOWNER_BEAM) return BeamResult.UNSUPPORTED;
        var owner = request.ownerModId();
        if (owner == null || owner.isEmpty() || owner.equals("academy") || !modLoaded.test(owner)) return BeamResult.INVALID;
        if (request.effectId() <= 0) return BeamResult.INVALID;
        if (request.dimension() == null || currentDimension == null || !request.dimension().equals(currentDimension)) return BeamResult.INVALID;
        if (!bounded(request.from()) || !bounded(request.to())) return BeamResult.INVALID;
        double length = request.from().distanceTo(request.to());
        if (!(length > 0) || length > MAX_LENGTH) return BeamResult.INVALID;
        return null;
    }

    private static boolean bounded(@Nullable Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= MAX_HORIZONTAL && Math.abs(v.z) <= MAX_HORIZONTAL && Math.abs(v.y) <= MAX_VERTICAL;
    }
}
