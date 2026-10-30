package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.OreSurveyStart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 原作MineDetect（MDContext、WeAthFolD・KSkun）のサーバー側。コストと成長を払い、探査そのものは原作と同じく
 * 使った者のクライアントだけで行う。
 */
public final class MineDetect {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy","mine_detect");
    public static final int DURATION = 100;
    private MineDetect() { }
    public static String cast(ServerPlayer player,PlayerAbilityData data) {
        if (!player.isAlive() || data.isReadOnly()
                || !ArcGen.CATEGORY.equals(data.getAbility()) || !data.hasLearned(ID)) return "academy.cast.unlearned";
        if (!data.isActive()) return "academy.cast.inactive";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID)>0) return "academy.cast.cooldown";
        float exp=data.getProficiency(ID);
        var grant=new OreSurveyStart(player.getUUID(),player.level().dimension().location(),ArcGen.lerp(15,30,exp),exp>.5f&&data.getLevel()>=4);
        if (!data.consume(ID, ArcGen.lerp(1500,1000,exp),ArcGen.lerp(200,180,exp),player.getAbilities().instabuild)) return "academy.cast.cp";
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,DURATION));
        data.addProficiency(ID,.008f);
        data.setCooldown(ID,(int)ArcGen.lerp(900,400,data.getProficiency(ID)));
        AcademyNetwork.send(player,grant);
        // 原作MDContextC: 術者自身のクライアントだけで、音量0.5のem.minedetect。
        player.playNotifySound(io.github.pinchan4273.reacademycraft.world.AcademySounds.EM_MINEDETECT.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        return "";
    }
}
