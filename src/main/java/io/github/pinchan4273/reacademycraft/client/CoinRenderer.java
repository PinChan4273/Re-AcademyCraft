package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyCoinPresentation;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.entity.CoinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/** 原作の両面のコインのモデル（中身を固定して確かめている）を、観測者から見て安定した回転軸で描く。 */
@Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CoinRenderer extends EntityRenderer<CoinEntity> {
    public static final ResourceLocation FRONT = ResourceLocation.fromNamespaceAndPath("academy", "textures/item/coin_front.png");
    public static final ResourceLocation BACK = ResourceLocation.fromNamespaceAndPath("academy", "textures/item/coin_back.png");
    private static long renderedCoins;
    private final ItemRenderer items;
    public CoinRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemRenderer();}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(AcademyContent.COIN_ENTITY.get(),CoinRenderer::new);}
    @Override public void render(CoinEntity entity,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffer,int light){
        pose.pushPose();
        pose.translate(LegacyCoinPresentation.OFFSET_X, LegacyCoinPresentation.OFFSET_Y, LegacyCoinPresentation.OFFSET_Z);
        pose.scale(LegacyCoinPresentation.ENTITY_SCALE, LegacyCoinPresentation.ENTITY_SCALE, LegacyCoinPresentation.ENTITY_SCALE);
        var axis = LegacyCoinPresentation.axis(entity.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(entity.getUUID().getLeastSignificantBits(), 1));
        pose.mulPose(new Quaternionf().rotationAxis((float)Math.toRadians(
                LegacyCoinPresentation.angleDegrees(entity.tickCount + partialTick)), axis.x(), axis.y(), axis.z()));
        pose.scale(1, 1, LegacyCoinPresentation.ENTITY_HALF_THICKNESS / LegacyCoinPresentation.ITEM_HALF_THICKNESS);
        items.renderStatic(entity.getItem(),ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,pose,buffer,entity.level(),entity.getId());
        renderedCoins++;
        pose.popPose();super.render(entity,yaw,partialTick,pose,buffer,light);
    }
    public static long renderedCoins(){return renderedCoins;}
    @Override public ResourceLocation getTextureLocation(CoinEntity entity){return FRONT;}
}
