package io.github.pinchan4273.reacademycraft.api.client;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 外部の演出の光線を1本足す要求（公開API、実験的。演出API版1）。サーバーで確定した結果（始点・終点）を、そのまま描くための値。
 *
 * @param ownerModId     持ち主のMOD ID（読み込まれていて、{@code academy}でないこと）。上限・一覧・効果IDは持ち主ごと。
 * @param effectId       持ち主の中で光線を区別する番号（1以上）。同じ番号の再送は{@link BeamResult#DUPLICATE}になる。
 * @param dimension      光線の次元。今のクライアントの次元と違えば{@link BeamResult#INVALID}。
 * @param casterEntityId 術者の実体の番号（分からなければ-1）。
 * @param casterId       術者のUUID（分からなければnull）。番号とUUIDの両方が合う生きた実体がいれば、始点を術者の手元へずらす
 *                       （自然能力のMeltdownerと同じ。いなければ{@code from}から描く）。
 * @param from           始点（ワールドの座標）。
 * @param to             終点（ワールドの座標）。始点からの長さが{@link ExternalPresentation#maxLength}以下。
 * @param style          見た目。
 */
public record BeamRequest(String ownerModId, long effectId, ResourceLocation dimension, int casterEntityId, @Nullable UUID casterId,
                          Vec3 from, Vec3 to, BeamStyle style) { }
