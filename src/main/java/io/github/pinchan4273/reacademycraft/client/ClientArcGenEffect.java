package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.WorldSubmits;
import io.github.pinchan4273.reacademycraft.network.ArcGenEffect;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGenAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** サーバーが受理した一回きりのArc GenのweakArc。ゲームの処理と、既存の完了音はサーバーが持つ。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientArcGenEffect {
    private static final ChargingLoopLedger LEDGER=new ChargingLoopLedger();
    private static final Map<UUID,Effect> EFFECTS=new HashMap<>();
    private static ClientLevel world;
    private static long ticks,acceptedCount,renderedQuads;
    private static int lastFrameQuads,reloadGeneration;
    private static final class Templates {
        static final List<LegacyArcGeometry.Pattern> WEAK=create();
        static List<LegacyArcGeometry.Pattern> create(){var out=new ArrayList<LegacyArcGeometry.Pattern>(20);var random=new Random();
            for(int i=0;i<20;i++)out.add(LegacyArcGeometry.generate(LegacyArcGeometry.Profile.WEAK,random));return List.copyOf(out);}
    }
    private static final class Effect{
        final Player source;final ArcGenEffect packet;final LegacyArcGenAnimation animation=new LegacyArcGenAnimation();final Random random=new Random();
        LegacyArcMesh.Mesh mesh;int template=-1;boolean fresh=true;
        Effect(Player source,ArcGenEffect packet){this.source=source;this.packet=packet;bake();}
        void bake(){int next=animation.snapshot().template();if(template!=next){mesh=LegacyArcMesh.bake(Templates.WEAK.get(next),packet.length(),random);template=next;}}
        boolean valid(){return source.isAlive()&&!source.isRemoved()&&source.level()==Minecraft.getInstance().level&&source.level().getEntity(source.getId())==source;}
    }
    private ClientArcGenEffect(){}
    private static void currentWorld(){var level=Minecraft.getInstance().level;if(world!=level){reset();world=level;}}
    public static void receive(ArcGenEffect packet){
        currentWorld();var client=Minecraft.getInstance();
        if(client.level==null||client.player==null||!client.level.dimension().identifier().equals(packet.dimension()))return;
        var entity=client.level.getEntity(packet.entityId());
        if(!(entity instanceof Player source)||!source.getUUID().equals(packet.player())||!source.isAlive())return;
        var decision=LEDGER.accept(packet.grant(),ticks);if(decision!=ChargingLoopLedger.Decision.START)return;
        if(client.gui.overlay()!=null){LEDGER.cancel(packet.player());return;}
        EFFECTS.put(packet.player(),new Effect(source,packet));acceptedCount++;
    }
    private static void remove(UUID id){EFFECTS.remove(id);LEDGER.cancel(id);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        currentWorld();var client=Minecraft.getInstance();
        if(client.player==null||client.level==null)return;if(client.gui.overlay()!=null){cancelEffects();return;}if(client.isPaused())return;
        ticks++;for(var id:LEDGER.expire(ticks))remove(id);
        for(var id:List.copyOf(EFFECTS.keySet())){var effect=EFFECTS.get(id);if(!effect.valid())remove(id);else if(effect.fresh)effect.fresh=false;
            else{effect.animation.tick(effect.random);if(effect.animation.snapshot().finished())remove(id);else effect.bake();}}
    }
    /** 26.2: 今の状態を写して提出の予定を作る（1.21.1 の RenderLevelStageEvent の AFTER_PARTICLES の中の描画に当たる）。 */
    @SubscribeEvent public static void extract(ExtractLevelRenderStateEvent event){
        lastFrameQuads=0;var client=Minecraft.getInstance();
        if(client.level!=world||client.player==null||client.gui.overlay()!=null)return;var camera=event.getCamera().position();
        boolean firstPerson=client.options.getCameraType().isFirstPerson();
        for(var effect:EFFECTS.values())if(effect.valid()&&effect.animation.snapshot().visible()){
            var origin=effect.packet.origin().subtract(camera);var offset=LegacyChargingArcPose.handOffset(firstPerson,effect.source==client.player);
            float yaw=effect.packet.yaw(),pitch=effect.packet.pitch();var mesh=effect.mesh;
            WorldSubmits.add(event.getRenderState(),(draw,pose)->{
                pose.pushPose();try{pose.translate(origin.x,origin.y,origin.z);pose.mulPose(Axis.YP.rotationDegrees(-(yaw+90)));
                    pose.mulPose(Axis.ZP.rotationDegrees(-pitch));pose.translate(offset.x(),offset.y(),offset.z());LegacyArcRenderer.submit(draw,pose,mesh,1);}finally{pose.popPose();}
            });
            int count=mesh.quads().size();lastFrameQuads+=count;renderedQuads+=count;
        }
    }
    @net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
    public static final class ArcGenEffectReloadRegistration{
        @SubscribeEvent public static void register(AddClientReloadListenersEvent event){event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("academy","arc_gen_effect"),(ResourceManagerReloadListener)manager->{cancelEffects();reloadGeneration++;});}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    private static void cancelEffects(){for(var id:List.copyOf(EFFECTS.keySet()))remove(id);lastFrameQuads=0;}
    public static void reset(){EFFECTS.clear();LEDGER.clear();world=null;ticks=0;acceptedCount=0;renderedQuads=0;lastFrameQuads=0;}
    public static boolean active(UUID id){return EFFECTS.containsKey(id);}public static int activeEffects(){return EFFECTS.size();}
    public static long acceptedCount(){return acceptedCount;}public static long renderedQuads(){return renderedQuads;}public static int lastFrameQuads(){return lastFrameQuads;}
    public static int reloadGeneration(){return reloadGeneration;}public static int age(UUID id){var e=EFFECTS.get(id);return e==null?-1:e.animation.snapshot().age();}
    public static long session(UUID id){var e=EFFECTS.get(id);return e==null?0:e.packet.session();}
}
