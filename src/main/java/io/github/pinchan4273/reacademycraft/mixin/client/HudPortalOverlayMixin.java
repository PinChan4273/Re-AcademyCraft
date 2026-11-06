package io.github.pinchan4273.reacademycraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.pinchan4273.reacademycraft.client.ClientDirectEffects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * v1.5.0 の技能由来の吐き気（{@link ClientDirectEffects}）で、ネザーポータルの紫の重ね絵を出さない（クライアントだけ）。
 * <p>
 * 26.2 の {@code Hud.extractCameraOverlays} は、ポータルの強さ（{@code LocalPlayer.portalEffectIntensity}。1.21.1 の
 * {@code spinningEffectIntensity} に当たる）が正ならポータルの絵を描く。技能の吐き気はバニラの効果を付けずにこの強さを下から支えて
 * 画面を歪ませる（Forge 版・1.21.1 版と同じ歪みの速さ）ので、このままでは紫の絵が出る。NeoForge 1.21.1 版と同じく、層ごとではなく
 * ポータルの絵の呼び出しだけを止める。ポータルの中なら通常どおり描く。
 * <p>
 * 他の MOD が同じ呼び出しを包んでも重ねられるよう、{@code @Redirect} ではなく MixinExtras の {@code @WrapOperation} を使う。
 */
@Mixin(Hud.class)
abstract class HudPortalOverlayMixin {
    @WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;extractPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
    private void academy$portalOverlay(Hud hud, GuiGraphicsExtractor graphics, float alpha, Operation<Void> original) {
        if (!ClientDirectEffects.hidesPortalOverlay()) original.call(hud, graphics, alpha);
    }
}
