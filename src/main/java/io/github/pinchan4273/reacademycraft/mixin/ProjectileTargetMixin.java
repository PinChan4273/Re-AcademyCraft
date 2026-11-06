package io.github.pinchan4273.reacademycraft.mixin;

import io.github.pinchan4273.reacademycraft.skill.DarkMatterBeetles;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 飛び道具が当たりの候補を選ぶ所（{@code Projectile.canHitEntity}）で、通り抜ける相手を外す（{@link DarkMatterBeetles#passesThrough}）。
 * バニラの飛び道具（矢・三叉槍・投げる物・火の玉など）は、どれもこの判定を通って当たる相手を探す（子classは super を呼ぶ）。
 */
@Mixin(Projectile.class)
abstract class ProjectileTargetMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void academy$passThrough(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (DarkMatterBeetles.passesThrough((Projectile) (Object) this, target)) cir.setReturnValue(false);
    }
}
