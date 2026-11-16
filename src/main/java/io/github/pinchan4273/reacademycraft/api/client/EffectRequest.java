package io.github.pinchan4273.reacademycraft.api.client;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 外部の演出を1つ出す要求（公開API、実験的。演出API版2）。どの欄が要るかは{@link EffectStyle}の値ごとに決まる。使わない欄は無視する。
 *
 * @param ownerModId 持ち主のMOD ID（読み込まれていて、{@code academy}でないこと）。上限・一覧・効果IDは持ち主ごと。光線とは別に数える。
 * @param effectId   持ち主の中で演出を区別するID（1以上）。同じIDの再送は{@link BeamResult#DUPLICATE}になる。
 * @param dimension  演出のディメンション。今のクライアントのディメンションと違えば{@link BeamResult#INVALID}。
 * @param style      種類。
 * @param position   位置（ワールドの座標）。
 * @param target     向かう先（ワールドの座標）。使わない種類ではnull。
 * @param entityId   追従するエンティティのID（無ければ-1）。
 * @param entityUuid 追従するエンティティのUUID（無ければnull）。IDとUUIDの両方が合うエンティティだけを追従する。受け付けた後に合わなくなれば、演出は終わる。
 * @param scale      大きさ。使わない種類では無視する。
 */
public record EffectRequest(String ownerModId, long effectId, ResourceLocation dimension, EffectStyle style, Vec3 position,
                            @Nullable Vec3 target, int entityId, @Nullable UUID entityUuid, float scale) {
    /** 位置だけの要求。 */
    public static EffectRequest at(String ownerModId, long effectId, ResourceLocation dimension, EffectStyle style, Vec3 position, float scale) {
        return new EffectRequest(ownerModId, effectId, dimension, style, position, null, -1, null, scale);
    }
    /** 位置から向かう先への要求。 */
    public static EffectRequest between(String ownerModId, long effectId, ResourceLocation dimension, EffectStyle style, Vec3 position, Vec3 target, float scale) {
        return new EffectRequest(ownerModId, effectId, dimension, style, position, target, -1, null, scale);
    }
    /** エンティティに付ける要求。{@code position}は、エンティティがいなければ使う位置。 */
    public static EffectRequest on(String ownerModId, long effectId, ResourceLocation dimension, EffectStyle style, int entityId, UUID entityUuid, Vec3 position, float scale) {
        return new EffectRequest(ownerModId, effectId, dimension, style, position, null, entityId, entityUuid, scale);
    }
}
