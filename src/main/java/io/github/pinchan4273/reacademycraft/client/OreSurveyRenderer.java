package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/**
 * 原作MineDetectのHandlerRender（術者のクライアントだけで描く）: 各鉱石の上に、各辺0.05内側へ寄せた箱を描き、
 * 各面にeffects/mineview.png（1ピクセルの枠と薄い粒、残りは透明）を貼る。採掘レベルの色で着色し、光の影響なし、
 * カリングなし、壁越しに描く。アルファは距離で下がる（0.3 + 0.7 (1 - 2.2 d / range)）。
 */
@Mod.EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class OreSurveyRenderer {
    private static final int[][] COLORS={{115,200,227},{161,181,188},{87,231,248},{97,204,94}};
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    private OreSurveyRenderer() { }
    private static final class Types extends RenderType {
        private Types(){super("academy_unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
        static final net.minecraft.resources.ResourceLocation MINEVIEW=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","textures/effects/mineview.png");
        private static final RenderType SURVEY=create("academy_ore_survey",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,false,
                CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER).setTextureState(new TextureStateShard(MINEVIEW,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||!ClientOreSurvey.active())return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();var buffer=buffers.getBuffer(Types.SURVEY);
        var matrix=event.getPoseStack().last().pose();double range=ClientOreSurvey.range();
        for(var mark:ClientOreSurvey.marks()) {
            var p=mark.position();double dx=p.getX()-mc.player.getX(),dy=p.getY()-mc.player.getY(),dz=p.getZ()-mc.player.getZ();
            int alpha=(int)(255*Math.max(0,Math.min(1,1-1.54*Math.sqrt(dx*dx+dy*dy+dz*dz)/range)));
            if(alpha==0)continue;
            int[] color=COLORS[mark.color()];
            // LegacyMeshUtils.createBoxWithUV: 各面に絵全体を貼る。
            for(var face:FACES)for(int i=0;i<4;i++) { int corner=face[i];
                float x=(float)(p.getX()+((corner&1)==0?.05:.95)-camera.x);
                float y=(float)(p.getY()+((corner&2)==0?.05:.95)-camera.y);
                float z=(float)(p.getZ()+((corner&4)==0?.05:.95)-camera.z);
                buffer.vertex(matrix,x,y,z).color(color[0],color[1],color[2],alpha).uv(i==1||i==2?1:0,i>=2?1:0).endVertex();
            }
        }
        // NO_DEPTH_TESTだけではワールドの深度テストは無効にならない。
        // そのままでは内側へ寄せた重ね描きが、壁にも鉱石自身の面にも隠れる。
        // 上書きはこのバッチに限り、入ってきたときの実際の深度状態へ戻す。
        boolean depthWasEnabled=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.disableDepthTest();
        try { buffers.endBatch(Types.SURVEY); }
        finally {
            if(depthWasEnabled)RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }
}
