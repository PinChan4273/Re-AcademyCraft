package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.network.ThunderBoltEffect;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.visual.LegacyThunderBoltArcAnimation;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** サーバーが受理した一回きりのThunder Boltの強い弧と範囲の弧。ゲームの処理とem.arc_strongはサーバーが持つ。 */
@Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClientThunderBoltEffect {
    private static final ChargingLoopLedger LEDGER=new ChargingLoopLedger();
    private static final Map<UUID,Effect> EFFECTS=new HashMap<>();
    private static ClientLevel world;
    private static long ticks,acceptedCount,mainQuads,aoeQuads;
    private static int lastMainQuads,lastAoeQuads,reloadGeneration;
    private static final class Templates{
        static final List<LegacyArcGeometry.Pattern> STRONG=create(LegacyArcGeometry.Profile.STRONG);
        static final List<LegacyArcGeometry.Pattern> AOE=create(LegacyArcGeometry.Profile.AOE);
        static List<LegacyArcGeometry.Pattern> create(LegacyArcGeometry.Profile profile){var out=new ArrayList<LegacyArcGeometry.Pattern>(20);var random=new Random();
            for(int i=0;i<20;i++)out.add(LegacyArcGeometry.generate(profile,random));return List.copyOf(out);}
    }
    private static final class Arc{
        final boolean main;final Vec3 origin;final float yaw,pitch;final double cutoff;final Random random=new Random();final LegacyThunderBoltArcAnimation animation;
        LegacyArcMesh.Mesh mesh;int template=-1;
        Arc(boolean main,Vec3 origin,float yaw,float pitch,double cutoff){this.main=main;this.origin=origin;this.yaw=yaw;this.pitch=pitch;this.cutoff=cutoff;
            animation=main?LegacyThunderBoltArcAnimation.mainArc():LegacyThunderBoltArcAnimation.aoeArc(random);bake();}
        void tick(){animation.tick(random);if(!animation.snapshot().finished())bake();}
        void bake(){int next=animation.snapshot().template();if(template!=next){mesh=LegacyArcMesh.bake((main?Templates.STRONG:Templates.AOE).get(next),cutoff,random);template=next;}}
    }
    private static final class Effect{
        final Player source;final ThunderBoltEffect packet;final List<Arc> arcs;boolean fresh=true;int age;
        Effect(Player source,ThunderBoltEffect packet){this.source=source;this.packet=packet;var built=new ArrayList<Arc>(3+packet.aoeTargets().size());
            for(int i=0;i<3;i++)built.add(new Arc(true,packet.origin(),packet.yaw(),packet.pitch(),20));
            for(var target:packet.aoeTargets()){var delta=target.subtract(packet.impact());var orientation=LegacyChargingArcPose.orientation(delta.x,delta.y,delta.z);
                built.add(new Arc(false,packet.impact(),orientation.yawDegrees(),orientation.pitchDegrees(),orientation.length()));}
            arcs=List.copyOf(built);}
        boolean valid(){return source.isAlive()&&!source.isRemoved()&&source.level()==Minecraft.getInstance().level&&source.level().getEntity(source.getId())==source;}
        boolean finished(){return arcs.stream().allMatch(a->a.animation.snapshot().finished());}
    }
    private ClientThunderBoltEffect(){}
    private static void currentWorld(){var level=Minecraft.getInstance().level;if(world!=level){reset();world=level;}}
    public static void receive(ThunderBoltEffect packet){currentWorld();var client=Minecraft.getInstance();
        if(client.level==null||client.player==null||!client.level.dimension().location().equals(packet.dimension()))return;
        var entity=client.level.getEntity(packet.entityId());if(!(entity instanceof Player source)||!source.getUUID().equals(packet.player())||!source.isAlive())return;
        if(LEDGER.accept(packet.grant(),ticks)!=ChargingLoopLedger.Decision.START)return;if(client.getOverlay()!=null){LEDGER.cancel(packet.player());return;}
        EFFECTS.put(packet.player(),new Effect(source,packet));acceptedCount++;}
    private static void remove(UUID id){EFFECTS.remove(id);LEDGER.cancel(id);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;currentWorld();var client=Minecraft.getInstance();
        if(client.player==null||client.level==null)return;if(client.getOverlay()!=null){cancelEffects();return;}if(client.isPaused())return;
        ticks++;for(var id:LEDGER.expire(ticks))remove(id);for(var id:List.copyOf(EFFECTS.keySet())){var effect=EFFECTS.get(id);
            if(!effect.valid())remove(id);else if(effect.fresh)effect.fresh=false;else{effect.age++;for(var arc:effect.arcs)arc.tick();if(effect.finished())remove(id);}}}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        lastMainQuads=lastAoeQuads=0;var client=Minecraft.getInstance();if(client.level!=world||client.player==null||client.getOverlay()!=null)return;
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();for(var effect:EFFECTS.values())if(effect.valid())for(var arc:effect.arcs){
            if(!arc.animation.snapshot().visible()||arc.animation.snapshot().finished())continue;var origin=arc.origin.subtract(camera);pose.pushPose();try{
                pose.translate(origin.x,origin.y,origin.z);pose.mulPose(Axis.YP.rotationDegrees(-(arc.yaw+90)));pose.mulPose(Axis.ZP.rotationDegrees(-arc.pitch));
                if(arc.main){var offset=LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(),effect.source==client.player);pose.translate(offset.x(),offset.y(),offset.z());}
                LegacyArcRenderer.draw(pose,arc.mesh,1);int count=arc.mesh.quads().size();if(arc.main){lastMainQuads+=count;mainQuads+=count;}else{lastAoeQuads+=count;aoeQuads+=count;}
            }finally{pose.popPose();}}}
    @Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ThunderBoltEffectReloadRegistration{@SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)manager->{cancelEffects();reloadGeneration++;});}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    private static void cancelEffects(){for(var id:List.copyOf(EFFECTS.keySet()))remove(id);lastMainQuads=lastAoeQuads=0;}
    public static void reset(){EFFECTS.clear();LEDGER.clear();world=null;ticks=acceptedCount=mainQuads=aoeQuads=0;lastMainQuads=lastAoeQuads=0;}
    public static boolean active(UUID id){return EFFECTS.containsKey(id);}public static int activeEffects(){return EFFECTS.size();}
    public static long acceptedCount(){return acceptedCount;}public static long mainQuads(){return mainQuads;}public static long aoeQuads(){return aoeQuads;}
    public static int lastMainQuads(){return lastMainQuads;}public static int lastAoeQuads(){return lastAoeQuads;}public static int reloadGeneration(){return reloadGeneration;}
    public static int age(UUID id){var e=EFFECTS.get(id);return e==null?-1:e.age;}public static int arcCount(UUID id){var e=EFFECTS.get(id);return e==null?0:e.arcs.size();}
}
