package io.github.pinchan4273.reacademycraft.mixin.client;

import io.github.pinchan4273.reacademycraft.client.ClientDarkBody;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * v1.5.0 の未元物質人間への変化の瞬間の白いシルエット（{@link ClientDarkBody#whiten}）。プレイヤーの提出の入口で、提出先を
 * 明るさと重ね色を白へ寄せる物へ差し替える（クライアントだけ）。
 * <p>
 * 1.21.1 版は {@code RenderPlayerEvent.Pre} を取り消し、包んだ {@code MultiBufferSource} で描き直した。26.2 の合図は提出先を
 * 差し替えられず、描き直すためのカメラの状態も渡さないので、入口の引数だけを替える（描くのは1回だけ）。白くするのは変化の合図を
 * 受けた実体だけで、それ以外は同じ提出先をそのまま返す。
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarWhitenMixin {
    @ModifyVariable(method = "submit(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector academy$whiten(SubmitNodeCollector collector, AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
                                               SubmitNodeCollector original, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        return ClientDarkBody.whiten(state.id, state.partialTick, collector);
    }
}
