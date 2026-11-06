package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.LegacyBlockEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.LegacyEntityRenderer;
import io.github.pinchan4273.reacademycraft.client.render.ObjModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import io.github.pinchan4273.reacademycraft.visual.LegacyCoinPresentation;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.CoinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Quaternionf;

/** 原作の両面のコインのモデル（中身を固定して確かめている）を、観測者から見て安定した回転軸で描く。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CoinRenderer extends LegacyEntityRenderer<CoinEntity> {
    public static final Identifier FRONT = Identifier.fromNamespaceAndPath("academy", "textures/item/coin_front.png");
    public static final Identifier BACK = Identifier.fromNamespaceAndPath("academy", "textures/item/coin_back.png");
    private static long renderedCoins;
    private final net.minecraft.client.renderer.item.ItemModelResolver items;
    public CoinRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemModelResolver();}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(AcademyContent.COIN_ENTITY.get(),CoinRenderer::new);}
    @Override protected void render(CoinEntity entity,float partialTick,PoseStack pose,SubmitNodeCollector buffer,int light){
        pose.pushPose();
        pose.translate(LegacyCoinPresentation.OFFSET_X, LegacyCoinPresentation.OFFSET_Y, LegacyCoinPresentation.OFFSET_Z);
        pose.scale(LegacyCoinPresentation.ENTITY_SCALE, LegacyCoinPresentation.ENTITY_SCALE, LegacyCoinPresentation.ENTITY_SCALE);
        var axis = LegacyCoinPresentation.axis(entity.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(entity.getUUID().getLeastSignificantBits(), 1));
        pose.mulPose(new Quaternionf().rotationAxis((float)Math.toRadians(
                LegacyCoinPresentation.angleDegrees(entity.tickCount + partialTick)), axis.x(), axis.y(), axis.z()));
        pose.scale(1, 1, LegacyCoinPresentation.ENTITY_HALF_THICKNESS / LegacyCoinPresentation.ITEM_HALF_THICKNESS);
        var item=new net.minecraft.client.renderer.item.ItemStackRenderState();items.updateForNonLiving(item,entity.getItem(),ItemDisplayContext.NONE,entity);item.submit(pose,buffer,light,OverlayTexture.NO_OVERLAY,0);
        renderedCoins++;
        pose.popPose();
    }
    public static long renderedCoins(){return renderedCoins;}
}
