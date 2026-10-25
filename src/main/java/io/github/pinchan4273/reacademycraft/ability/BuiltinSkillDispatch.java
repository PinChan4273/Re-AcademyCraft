package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.ability.SkillDispatch.Cast;
import io.github.pinchan4273.reacademycraft.ability.SkillDispatch.Named;
import io.github.pinchan4273.reacademycraft.ability.SkillDispatch.Owner;
import io.github.pinchan4273.reacademycraft.skill.ArcGen;
import io.github.pinchan4273.reacademycraft.skill.BloodRetrograde;
import io.github.pinchan4273.reacademycraft.skill.BodyIntensify;
import io.github.pinchan4273.reacademycraft.skill.CurrentCharging;
import io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave;
import io.github.pinchan4273.reacademycraft.skill.DirectedShock;
import io.github.pinchan4273.reacademycraft.skill.ElectronBomb;
import io.github.pinchan4273.reacademycraft.skill.ElectronMissile;
import io.github.pinchan4273.reacademycraft.skill.Flashing;
import io.github.pinchan4273.reacademycraft.skill.FleshRipping;
import io.github.pinchan4273.reacademycraft.skill.Groundshock;
import io.github.pinchan4273.reacademycraft.skill.JetEngine;
import io.github.pinchan4273.reacademycraft.skill.LightShield;
import io.github.pinchan4273.reacademycraft.skill.MagneticManipulation;
import io.github.pinchan4273.reacademycraft.skill.MagneticMovement;
import io.github.pinchan4273.reacademycraft.skill.MarkTeleport;
import io.github.pinchan4273.reacademycraft.skill.Meltdowner;
import io.github.pinchan4273.reacademycraft.skill.MineDetect;
import io.github.pinchan4273.reacademycraft.skill.MineRay;
import io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport;
import io.github.pinchan4273.reacademycraft.skill.PlasmaCannon;
import io.github.pinchan4273.reacademycraft.skill.Railgun;
import io.github.pinchan4273.reacademycraft.skill.RayBarrage;
import io.github.pinchan4273.reacademycraft.skill.ScatterBomb;
import io.github.pinchan4273.reacademycraft.skill.ShiftTeleport;
import io.github.pinchan4273.reacademycraft.skill.StormWing;
import io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport;
import io.github.pinchan4273.reacademycraft.skill.ThunderBolt;
import io.github.pinchan4273.reacademycraft.skill.ThunderClap;
import io.github.pinchan4273.reacademycraft.skill.VecAccel;
import io.github.pinchan4273.reacademycraft.skill.VecDeviation;
import io.github.pinchan4273.reacademycraft.skill.VecReflection;
import java.util.List;

/**
 * 本体の41技能の振り分けの表（1.4.0のS3）。1.3.0の{@code AbilityAction.handle}と{@code SkillSessions.stopAll}が呼んでいたものを、
 * 同じ名前・同じ順で並べる。この表は、1.3.0（03f1f238）のソースから取り出した呼び出しの表から作った（scratchのgen_builtin_dispatch.py）。
 * 中断（CANCEL）で離し（release）を呼ぶ技能（Thunder Clap、Railgun、Current Charging、Magnetic Movement）や、切り替え型の中断が
 * stopになることも、1.3.0のまま。
 */
final class BuiltinSkillDispatch {
    private BuiltinSkillDispatch() { }

    private static <T> Named<T> named(String name, T action) { return new Named<>(name, action); }

    static void register() {
        var thunderClap = new Owner("ThunderClap", named("ThunderClap.release", (p, s) -> ThunderClap.release(p, s)),
                named("ThunderClap.release", (p, s) -> ThunderClap.release(p, s)),
                named("ThunderClap.renew", (p, s) -> ThunderClap.renew(p, s)),
                named("ThunderClap.stop", p -> ThunderClap.stop(p)), null);
        var railgun = new Owner("Railgun", named("Railgun.release", (p, s) -> Railgun.release(p, s)),
                named("Railgun.release", (p, s) -> Railgun.release(p, s)),
                named("Railgun.renew", (p, s) -> Railgun.renew(p, s)),
                named("Railgun.stop", p -> Railgun.stop(p)), null);
        var currentCharging = new Owner("CurrentCharging", named("CurrentCharging.release", (p, s) -> CurrentCharging.release(p, s)),
                named("CurrentCharging.release", (p, s) -> CurrentCharging.release(p, s)),
                named("CurrentCharging.renew", (p, s) -> CurrentCharging.renew(p, s)),
                named("CurrentCharging.stop", p -> CurrentCharging.stop(p)), null);
        var magneticMovement = new Owner("MagneticMovement", named("MagneticMovement.release", (p, s) -> MagneticMovement.release(p, s)),
                named("MagneticMovement.release", (p, s) -> MagneticMovement.release(p, s)),
                named("MagneticMovement.renew", (p, s) -> MagneticMovement.renew(p, s)),
                named("MagneticMovement.stop", p -> MagneticMovement.stop(p)), null);
        var magneticManipulation = new Owner("MagneticManipulation", named("MagneticManipulation.release", (p, s) -> MagneticManipulation.release(p, s)),
                named("MagneticManipulation.cancel", (p, s) -> MagneticManipulation.cancel(p, s)),
                named("MagneticManipulation.renew", (p, s) -> MagneticManipulation.renew(p, s)),
                named("MagneticManipulation.stop", p -> MagneticManipulation.stop(p)), null);
        var bodyIntensify = new Owner("BodyIntensify", null,
                named("BodyIntensify.cancel", (p, s) -> BodyIntensify.cancel(p, s)),
                null,
                named("BodyIntensify.stop", p -> BodyIntensify.stop(p)), named("BodyIntensify.ageOn", (p, s) -> BodyIntensify.ageOn(p, s)));
        var jetEngine = new Owner("JetEngine", null,
                named("JetEngine.stop", (p, s) -> JetEngine.stop(p, s)),
                null,
                named("JetEngine.stop", p -> JetEngine.stop(p)), named("JetEngine.ageOn", (p, s) -> JetEngine.ageOn(p, s)));
        var meltdowner = new Owner("Meltdowner", named("Meltdowner.release", (p, s) -> Meltdowner.release(p, s)),
                named("Meltdowner.cancel", (p, s) -> Meltdowner.cancel(p, s)),
                named("Meltdowner.renew", (p, s) -> Meltdowner.renew(p, s)),
                named("Meltdowner.stop", p -> Meltdowner.stop(p)), null);
        var threateningTeleport = new Owner("ThreateningTeleport", named("ThreateningTeleport.release", (p, s) -> ThreateningTeleport.release(p, s)),
                named("ThreateningTeleport.cancel", (p, s) -> ThreateningTeleport.cancel(p, s)),
                named("ThreateningTeleport.renew", (p, s) -> ThreateningTeleport.renew(p, s)),
                named("ThreateningTeleport.stop", p -> ThreateningTeleport.stop(p)), null);
        var penetrateTeleport = new Owner("PenetrateTeleport", named("PenetrateTeleport.release", (p, s) -> PenetrateTeleport.release(p, s)),
                named("PenetrateTeleport.cancel", (p, s) -> PenetrateTeleport.cancel(p, s)),
                named("PenetrateTeleport.renew", (p, s) -> PenetrateTeleport.renew(p, s)),
                named("PenetrateTeleport.stop", p -> PenetrateTeleport.stop(p)), null);
        var markTeleport = new Owner("MarkTeleport", named("MarkTeleport.release", (p, s) -> MarkTeleport.release(p, s)),
                named("MarkTeleport.cancel", (p, s) -> MarkTeleport.cancel(p, s)),
                named("MarkTeleport.renew", (p, s) -> MarkTeleport.renew(p, s)),
                named("MarkTeleport.stop", p -> MarkTeleport.stop(p)), null);
        var fleshRipping = new Owner("FleshRipping", named("FleshRipping.release", (p, s) -> FleshRipping.release(p, s)),
                named("FleshRipping.cancel", (p, s) -> FleshRipping.cancel(p, s)),
                named("FleshRipping.renew", (p, s) -> FleshRipping.renew(p, s)),
                named("FleshRipping.stop", p -> FleshRipping.stop(p)), null);
        var shiftTeleport = new Owner("ShiftTeleport", named("ShiftTeleport.release", (p, s) -> ShiftTeleport.release(p, s)),
                named("ShiftTeleport.cancel", (p, s) -> ShiftTeleport.cancel(p, s)),
                named("ShiftTeleport.renew", (p, s) -> ShiftTeleport.renew(p, s)),
                named("ShiftTeleport.stop", p -> ShiftTeleport.stop(p)), null);
        var directedShock = new Owner("DirectedShock", named("DirectedShock.release", (p, s) -> DirectedShock.release(p, s)),
                named("DirectedShock.cancel", (p, s) -> DirectedShock.cancel(p, s)),
                named("DirectedShock.renew", (p, s) -> DirectedShock.renew(p, s)),
                named("DirectedShock.stop", p -> DirectedShock.stop(p)), null);
        var groundshock = new Owner("Groundshock", named("Groundshock.release", (p, s) -> Groundshock.release(p, s)),
                named("Groundshock.cancel", (p, s) -> Groundshock.cancel(p, s)),
                named("Groundshock.renew", (p, s) -> Groundshock.renew(p, s)),
                named("Groundshock.stop", p -> Groundshock.stop(p)), null);
        var vecAccel = new Owner("VecAccel", named("VecAccel.release", (p, s) -> VecAccel.release(p, s)),
                named("VecAccel.cancel", (p, s) -> VecAccel.cancel(p, s)),
                named("VecAccel.renew", (p, s) -> VecAccel.renew(p, s)),
                named("VecAccel.stop", p -> VecAccel.stop(p)), null);
        var directedBlastwave = new Owner("DirectedBlastwave", named("DirectedBlastwave.release", (p, s) -> DirectedBlastwave.release(p, s)),
                named("DirectedBlastwave.cancel", (p, s) -> DirectedBlastwave.cancel(p, s)),
                named("DirectedBlastwave.renew", (p, s) -> DirectedBlastwave.renew(p, s)),
                named("DirectedBlastwave.stop", p -> DirectedBlastwave.stop(p)), null);
        var bloodRetrograde = new Owner("BloodRetrograde", named("BloodRetrograde.release", (p, s) -> BloodRetrograde.release(p, s)),
                named("BloodRetrograde.cancel", (p, s) -> BloodRetrograde.cancel(p, s)),
                named("BloodRetrograde.renew", (p, s) -> BloodRetrograde.renew(p, s)),
                named("BloodRetrograde.stop", p -> BloodRetrograde.stop(p)), null);
        var plasmaCannon = new Owner("PlasmaCannon", named("PlasmaCannon.release", (p, s) -> PlasmaCannon.release(p, s)),
                named("PlasmaCannon.cancel", (p, s) -> PlasmaCannon.cancel(p, s)),
                named("PlasmaCannon.renew", (p, s) -> PlasmaCannon.renew(p, s)),
                named("PlasmaCannon.stop", p -> PlasmaCannon.stop(p)), null);
        var scatterBomb = new Owner("ScatterBomb", named("ScatterBomb.stop", (p, s) -> ScatterBomb.stop(p, s)),
                named("ScatterBomb.stop", (p, s) -> ScatterBomb.stop(p, s)),
                named("ScatterBomb.renew", (p, s) -> ScatterBomb.renew(p, s)),
                named("ScatterBomb.stop", p -> ScatterBomb.stop(p)), null);
        var mineRay = new Owner("MineRay", named("MineRay.stop", (p, s) -> MineRay.stop(p, s)),
                named("MineRay.stop", (p, s) -> MineRay.stop(p, s)),
                named("MineRay.renew", (p, s) -> MineRay.renew(p, s)),
                named("MineRay.stop", p -> MineRay.stop(p)), null);
        var lightShield = new Owner("LightShield", null,
                named("LightShield.stop", (p, s) -> LightShield.stop(p, s)),
                null,
                named("LightShield.stop", p -> LightShield.stop(p)), named("LightShield.ageOn", (p, s) -> LightShield.ageOn(p, s)));
        var electronMissile = new Owner("ElectronMissile", null,
                named("ElectronMissile.stop", (p, s) -> ElectronMissile.stop(p, s)),
                null,
                named("ElectronMissile.stop", p -> ElectronMissile.stop(p)), named("ElectronMissile.ageOn", (p, s) -> ElectronMissile.ageOn(p, s)));
        var flashing = new Owner("Flashing", null,
                null,
                null,
                named("Flashing.stop", p -> Flashing.stop(p)), named("Flashing.ageOn", (p, s) -> Flashing.ageOn(p, s)));
        var vecDeviation = new Owner("VecDeviation", null,
                null,
                null,
                named("VecDeviation.stop", p -> VecDeviation.stop(p)), null);
        var stormWing = new Owner("StormWing", null,
                null,
                null,
                named("StormWing.stop", p -> StormWing.stop(p)), null);
        var vecReflection = new Owner("VecReflection", null,
                null,
                null,
                named("VecReflection.stop", p -> VecReflection.stop(p)), null);
        SkillDispatch.builtInOrder(List.of(thunderClap, railgun, currentCharging, magneticMovement, magneticManipulation, bodyIntensify, jetEngine, meltdowner, threateningTeleport, penetrateTeleport, markTeleport, fleshRipping, shiftTeleport, directedShock, groundshock, vecAccel, directedBlastwave, bloodRetrograde, plasmaCannon, scatterBomb, mineRay, lightShield, electronMissile),
                List.of(currentCharging, magneticMovement, magneticManipulation, thunderClap, railgun, scatterBomb, mineRay, meltdowner, threateningTeleport, penetrateTeleport, markTeleport, fleshRipping, shiftTeleport, directedShock, groundshock, vecAccel, directedBlastwave, bloodRetrograde, plasmaCannon),
                List.of(currentCharging, magneticMovement, magneticManipulation, bodyIntensify, thunderClap, railgun, scatterBomb, lightShield, mineRay, jetEngine, electronMissile, meltdowner, threateningTeleport, penetrateTeleport, markTeleport, fleshRipping, shiftTeleport, flashing, directedShock, groundshock, vecAccel, vecDeviation, directedBlastwave, stormWing, bloodRetrograde, vecReflection, plasmaCannon));
        SkillDispatch.builtInCast(ArcGen.ID, new Cast(named("ArcGen.cast", (p, d, s) -> ArcGen.cast(p, d)), null));
        SkillDispatch.builtInCast(CurrentCharging.ID, new Cast(named("CurrentCharging.start", CurrentCharging::start), currentCharging));
        SkillDispatch.builtInCast(MagneticMovement.ID, new Cast(named("MagneticMovement.start", MagneticMovement::start), magneticMovement));
        SkillDispatch.builtInCast(MagneticManipulation.ID, new Cast(named("MagneticManipulation.start", MagneticManipulation::start), magneticManipulation));
        SkillDispatch.builtInCast(BodyIntensify.ID, new Cast(named("BodyIntensify.start", BodyIntensify::start), bodyIntensify));
        SkillDispatch.builtInCast(MineDetect.ID, new Cast(named("MineDetect.cast", (p, d, s) -> MineDetect.cast(p, d)), null));
        SkillDispatch.builtInCast(ThunderBolt.ID, new Cast(named("ThunderBolt.cast", (p, d, s) -> ThunderBolt.cast(p, d)), null));
        SkillDispatch.builtInCast(Railgun.ID, new Cast(named("Railgun.start", Railgun::start), railgun));
        SkillDispatch.builtInCast(ThunderClap.ID, new Cast(named("ThunderClap.start", ThunderClap::start), thunderClap));
        SkillDispatch.builtInCast(ElectronBomb.ID, new Cast(named("ElectronBomb.cast", (p, d, s) -> ElectronBomb.cast(p, d)), null));
        SkillDispatch.builtInCast(ScatterBomb.ID, new Cast(named("ScatterBomb.start", ScatterBomb::start), scatterBomb));
        SkillDispatch.builtInCast(LightShield.ID, new Cast(named("LightShield.start", LightShield::start), lightShield));
        SkillDispatch.builtInCast(MineRay.BASIC.id(), new Cast(named("MineRay.start", (p, d, s) -> MineRay.start(MineRay.BASIC, p, d, s)), mineRay));
        SkillDispatch.builtInCast(MineRay.EXPERT.id(), new Cast(named("MineRay.start", (p, d, s) -> MineRay.start(MineRay.EXPERT, p, d, s)), mineRay));
        SkillDispatch.builtInCast(MineRay.LUCK.id(), new Cast(named("MineRay.start", (p, d, s) -> MineRay.start(MineRay.LUCK, p, d, s)), mineRay));
        SkillDispatch.builtInCast(JetEngine.ID, new Cast(named("JetEngine.start", JetEngine::start), jetEngine));
        SkillDispatch.builtInCast(RayBarrage.ID, new Cast(named("RayBarrage.cast", (p, d, s) -> RayBarrage.cast(p, d)), null));
        SkillDispatch.builtInCast(ElectronMissile.ID, new Cast(named("ElectronMissile.start", ElectronMissile::start), electronMissile));
        SkillDispatch.builtInCast(Meltdowner.ID, new Cast(named("Meltdowner.start", Meltdowner::start), meltdowner));
        SkillDispatch.builtInCast(ThreateningTeleport.ID, new Cast(named("ThreateningTeleport.start", ThreateningTeleport::start), threateningTeleport));
        SkillDispatch.builtInCast(PenetrateTeleport.ID, new Cast(named("PenetrateTeleport.start", PenetrateTeleport::start), penetrateTeleport));
        SkillDispatch.builtInCast(MarkTeleport.ID, new Cast(named("MarkTeleport.start", MarkTeleport::start), markTeleport));
        SkillDispatch.builtInCast(FleshRipping.ID, new Cast(named("FleshRipping.start", FleshRipping::start), fleshRipping));
        SkillDispatch.builtInCast(ShiftTeleport.ID, new Cast(named("ShiftTeleport.start", ShiftTeleport::start), shiftTeleport));
        SkillDispatch.builtInCast(Flashing.ID, new Cast(named("Flashing.toggle", Flashing::toggle), flashing));
        SkillDispatch.builtInCast(DirectedShock.ID, new Cast(named("DirectedShock.start", DirectedShock::start), directedShock));
        SkillDispatch.builtInCast(Groundshock.ID, new Cast(named("Groundshock.start", Groundshock::start), groundshock));
        SkillDispatch.builtInCast(VecAccel.ID, new Cast(named("VecAccel.start", VecAccel::start), vecAccel));
        SkillDispatch.builtInCast(VecDeviation.ID, new Cast(named("VecDeviation.toggle", VecDeviation::toggle), vecDeviation));
        SkillDispatch.builtInCast(DirectedBlastwave.ID, new Cast(named("DirectedBlastwave.start", DirectedBlastwave::start), directedBlastwave));
        SkillDispatch.builtInCast(StormWing.ID, new Cast(named("StormWing.toggle", StormWing::toggle), stormWing));
        SkillDispatch.builtInCast(BloodRetrograde.ID, new Cast(named("BloodRetrograde.start", BloodRetrograde::start), bloodRetrograde));
        SkillDispatch.builtInCast(VecReflection.ID, new Cast(named("VecReflection.toggle", VecReflection::toggle), vecReflection));
        SkillDispatch.builtInCast(PlasmaCannon.ID, new Cast(named("PlasmaCannon.start", PlasmaCannon::start), plasmaCannon));
    }
}
