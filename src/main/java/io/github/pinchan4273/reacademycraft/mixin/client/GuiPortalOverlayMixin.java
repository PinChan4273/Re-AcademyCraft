package io.github.pinchan4273.reacademycraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.pinchan4273.reacademycraft.client.ClientDirectEffects;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * v1.5.0の技能由来の吐き気（{@link ClientDirectEffects}）で、ネザーポータルの紫の重ね絵を出さない（クライアントだけ）。
 * <p>
 * バニラ1.21.1の{@code Gui.renderCameraOverlays}は、歪みの強さが正でバニラの吐き気の効果が無いときにポータルの絵を描く。技能の吐き気は
 * バニラの効果を付けないので、このままでは紫の絵が出る。Forge 1.20.1版は独立したGUIの重ね（{@code VanillaGuiOverlay.PORTAL}）を
 * 取り消したが、NeoForge 1.21.1ではかぼちゃ・粉雪と同じ層の中なので、ポータルの絵の呼び出しだけを止める。ポータルの中なら通常どおり描く。
 * <p>
 * 他のMODが同じ呼び出しを変えていてもぶつからないよう、置き換え（{@code @Redirect}）ではなくMixinExtrasの{@code @WrapOperation}
 * （NeoForge 21.1に同梱）で包み、止めないときは元の呼び出し（他のMODの包みを含む）をそのまま呼ぶ。
 */
@Mixin(Gui.class)
abstract class GuiPortalOverlayMixin {
    @WrapOperation(method = "renderCameraOverlays", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;renderPortalOverlay(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void academy$portalOverlay(Gui gui, GuiGraphics guiGraphics, float alpha, Operation<Void> original) {
        if (!ClientDirectEffects.hidesPortalOverlay()) original.call(gui, guiGraphics, alpha);
    }
}
