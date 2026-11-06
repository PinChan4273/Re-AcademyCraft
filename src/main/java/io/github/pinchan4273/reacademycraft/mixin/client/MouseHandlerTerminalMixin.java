package io.github.pinchan4273.reacademycraft.mixin.client;

import io.github.pinchan4273.reacademycraft.client.TerminalHud;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * データ端末を開いている間、マウスが回したプレイヤーの向きを、カメラの向きを取り出す前に取り消す（クライアントだけ）。
 * <p>
 * 26.2 の {@code Minecraft.runTick} は、{@code MouseHandler.handleAccumulatedMovement}（ここでマウスがプレイヤーを回す）の後に
 * {@code renderFrame} を呼び、その中で {@code gameRenderer.update}・{@code extract}（カメラの向きを取り出す）→
 * {@code RenderFrameEvent.Pre} → {@code gameRenderer.render} の順に進む。{@code RenderFrameEvent.Pre} で取り消すと、回った向きで
 * 1フレーム描かれてから戻るので、画面が揺れた。マウスの処理の終わりで取り消せば、カメラは開く前の向きのまま描かれる。
 * 取り消しとカーソルへの換算は {@link TerminalHud#afterMouseMovement} が行う。
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerTerminalMixin {
    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void academy$terminalTakesTheTurn(CallbackInfo ci) {
        TerminalHud.afterMouseMovement();
    }
}
