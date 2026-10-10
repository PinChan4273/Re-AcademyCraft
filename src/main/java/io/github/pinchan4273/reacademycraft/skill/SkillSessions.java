package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** プレイヤーのすべての押し続け技能のセッションをまとめて扱う: 原作ContextManager.disposePlayer。 */
@Mod.EventBusSubscriber(modid = "academy")
public final class SkillSessions {
    private SkillSessions() { }

    /**
     * プレイヤーが押し続けているものを終える。能力キーの切り替えがこれを行い、原作のServerManager.__onOverloadと
     * __onCategoryChangeと同じく、オーバーロードや新しいカテゴリもこれを行う。
     */
    public static void stopAll(ServerPlayer player) {
        CurrentCharging.stop(player);
        MagneticMovement.stop(player);
        MagneticManipulation.stop(player);
        BodyIntensify.stop(player);
        ThunderClap.stop(player);
        Railgun.stop(player);
        ScatterBomb.stop(player);
        LightShield.stop(player);
        MineRay.stop(player);
        JetEngine.stop(player);
        ElectronMissile.stop(player);
        Meltdowner.stop(player);
        ThreateningTeleport.stop(player);
        PenetrateTeleport.stop(player);
        MarkTeleport.stop(player);
        FleshRipping.stop(player);
        ShiftTeleport.stop(player);
        Flashing.stop(player);
        DirectedShock.stop(player);
        Groundshock.stop(player);
        VecAccel.stop(player);
        VecDeviation.stop(player);
        DirectedBlastwave.stop(player);
        StormWing.stop(player);
        BloodRetrograde.stop(player);
        VecReflection.stop(player);
        PlasmaCannon.stop(player);
    }
    @SubscribeEvent public static void skillSessionsOnOverload(io.github.pinchan4273.reacademycraft.event.AbilityEvent.Overload event) {
        if (event.player() instanceof ServerPlayer player) stopAll(player);
    }
    @SubscribeEvent public static void skillSessionsOnCategoryChange(io.github.pinchan4273.reacademycraft.event.AbilityEvent.CategoryChange event) {
        if (event.player() instanceof ServerPlayer player) stopAll(player);
    }
}
