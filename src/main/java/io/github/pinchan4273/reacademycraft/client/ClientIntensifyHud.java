package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.network.IntensifyLoopEffect;
import io.github.pinchan4273.reacademycraft.skill.BodyIntensify;
import io.github.pinchan4273.reacademycraft.visual.LegacyIntensifyHudAnimation;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 術者だけに見える予備動作のHUD。サーバーがBody Intensifyの予備動作中と伝えている間続き、成功か中断で薄れて消える。 */
@Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClientIntensifyHud {
    private static final ChargingLoopLedger LEDGER=new ChargingLoopLedger();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;
    private static LegacyIntensifyHudAnimation animation;
    private static UUID owner;
    private static long session,ticks,startedNanos,completedCount;
    private static int entityId,preset,slot,reloadGeneration,frames,lastQuads,completedFrames;
    private static boolean finished,performed;
    private ClientIntensifyHud() { }
    private static void currentWorld(){var w=Minecraft.getInstance().level;if(world!=w){reset();world=w;}}
    private static double age(){return Math.max(0,(System.nanoTime()-startedNanos)/1_000_000_000.0);}
    private static boolean usable(){
        var c=Minecraft.getInstance();if(c.player==null||c.level==null||c.screen!=null||c.getOverlay()!=null||c.isPaused()||!c.isWindowActive()
                ||!c.player.isAlive()||c.player.isRemoved())return false;
        var d=c.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        return d!=null&&!d.isReadOnly()&&d.isActive()&&!d.isOverloadLocked();
    }
    private static int heldSlot(){
        var c=Minecraft.getInstance();if(!usable()||!ClientBodyIntensify.warmingUp())return -1;var d=c.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElseThrow(IllegalStateException::new);
        // Body Intensifyは切り替え型: キーを押しているかに関係なく、サーバーが予備動作中と伝えている枠を表示する。
        int i=ClientBodyIntensify.slot();
        return i>=0&&BodyIntensify.ID.equals(d.getSlot(d.getCurrentPreset(),i))?i:-1;
    }
    private static boolean sameOwner(){
        var c=Minecraft.getInstance();if(!usable()||!c.player.getUUID().equals(owner)||c.player.getId()!=entityId)return false;
        var d=c.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElseThrow(IllegalStateException::new);
        return d.getCurrentPreset()==preset&&BodyIntensify.ID.equals(d.getSlot(preset,slot));
    }
    public static void receive(IntensifyLoopEffect packet){
        currentWorld();var c=Minecraft.getInstance();
        if(c.player==null||c.level==null||!c.level.dimension().location().equals(packet.dimension())
                ||!c.player.getUUID().equals(packet.player())||c.player.getId()!=packet.entityId())return;
        var decision=LEDGER.accept(packet.grant(),ticks);
        if(decision==ChargingLoopLedger.Decision.IGNORE)return;
        if(decision==ChargingLoopLedger.Decision.STOP){
            // 対応する受理済みの開始が無い停止は、記録だけを残し、効果は作らない。
            if(animation!=null&&session==packet.session()&&sameOwner()&&!finished){
                performed=packet.performed();finished=true;if(performed)completedCount++;
                animation.startBlend(age(),performed,c.options.getCameraType().isFirstPerson(),RANDOM);
            }else if(animation!=null&&session<=packet.session())abort();
            return;
        }
        int held=heldSlot();
        if(held<0){
            if(decision==ChargingLoopLedger.Decision.REFRESH&&animation!=null&&!finished&&sameOwner())return;
            LEDGER.cancel(packet.player());if(animation!=null&&!finished)abort();return;
        }
        if(decision==ChargingLoopLedger.Decision.START){
            clearVisual();owner=packet.player();entityId=packet.entityId();session=packet.session();slot=held;
            preset=c.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElseThrow(IllegalStateException::new).getCurrentPreset();
            startedNanos=System.nanoTime();animation=new LegacyIntensifyHudAnimation(RANDOM);
        }
    }
    private static void clearVisual(){if(animation!=null)animation.clear();animation=null;finished=false;performed=false;frames=0;lastQuads=0;completedFrames=0;}
    private static void abort(){if(owner!=null)LEDGER.cancel(owner);clearVisual();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;currentWorld();ticks++;
        if(!LEDGER.expire(ticks).isEmpty())abort();
        if(animation==null)return;
        if(!sameOwner()){abort();return;}
        // 切り替え型なので、キーを離しても何も起こらない。予備動作を終えるのは、完了の成功か中断だけ。
        // 上のGUI・フォーカス・プリセット・ワールド・resourceによる中断は、どれも遅れて届いた完了を受け付けない。
        double now=age();animation.tick(now,RANDOM);if(animation.snapshot(now).disposed())clearVisual();
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var c=Minecraft.getInstance();if(animation==null||!sameOwner()||c.options.hideGui)return;
        var frame=animation.snapshot(age());if(frame.disposed())return;
        event.getGuiGraphics().flush();
        lastQuads=LegacyIntensifyHudRenderer.draw(event.getGuiGraphics().pose(),c.getWindow().getGuiScaledWidth(),c.getWindow().getGuiScaledHeight(),frame);frames++;if(finished&&performed)completedFrames++;
    }
    @Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class IntensifyHudReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->{reset();reloadGeneration++;});}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    public static void reset(){clearVisual();LEDGER.clear();world=null;owner=null;session=0;ticks=0;completedCount=0;}
    public static long session(){return session;}
    public static long completedCount(){return completedCount;}
    public static boolean active(){return animation!=null;}
    public static boolean completed(){return animation!=null&&finished&&performed;}
    public static int completedFrames(){return completedFrames;}
    public static int frames(){return frames;}
    public static int lastQuads(){return lastQuads;}
    public static int reloadGeneration(){return reloadGeneration;}
    public static LegacyIntensifyHudAnimation.Frame snapshot(){return animation==null?null:animation.snapshot(age());}
}
