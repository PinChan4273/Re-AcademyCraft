package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.OreSurveyStart;
import io.github.pinchan4273.reacademycraft.skill.MineDetect;
import io.github.pinchan4273.reacademycraft.skill.OreSurvey;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ローカルプレイヤーだけの一時的な探査。保存せず、entityを作らず、chunkを読み込まない。 */
@Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClientOreSurvey {
    private static ClientLevel world;
    private static LocalPlayer owner;
    private static OreSurveyStart grant;
    private static int age;
    private static List<OreSurvey.Mark> marks=List.of();
    private ClientOreSurvey() { }
    public static void receive(OreSurveyStart packet) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.player.isAlive()||!packet.belongsTo(mc.player.getUUID(),mc.level.dimension().location()))return;
        world=mc.level;owner=mc.player;grant=packet;age=0;
        marks=OreSurvey.scan(world,owner.position(),grant.range(),grant.advanced());
        // em.minedetectは、サーバーのMineDetectが術者に鳴らす。
    }
    private static boolean valid() {
        var mc=Minecraft.getInstance();
        return grant!=null&&mc.level==world&&mc.player==owner&&owner!=null&&owner.isAlive();
    }
    public static List<OreSurvey.Mark> marks() { return valid()?marks:List.of(); }
    public static float range() { return grant==null?0:Math.min(28,grant.range()); }
    public static boolean active() { return valid(); }
    public static void clear() { world=null;owner=null;grant=null;age=0;marks=List.of(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        if(!valid()){clear();return;}
        if(Minecraft.getInstance().isPaused())return;
        if(++age>=MineDetect.DURATION){clear();return;}
        if(age%5==0)marks=OreSurvey.scan(world,owner.position(),grant.range(),grant.advanced());
    }
}
