package io.github.pinchan4273.reacademycraft.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 原作MineDetectのHandlerRender（術者のクライアントだけで描く）: 各鉱石の上に、各辺0.05内側へ寄せた箱を描き、
 * 各面にeffects/mineview.png（1ピクセルの枠と薄い粒、残りは透明）を貼る。採掘レベルの色で着色し、光の影響なし、
 * カリングなし、壁越しに描く。アルファは距離で下がる（0.3 + 0.7 (1 - 2.2 d / range)）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class OreSurveyRenderer {
    private static final int[][] COLORS={{115,200,227},{161,181,188},{87,231,248},{97,204,94}};
    private static final int[][] FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    private OreSurveyRenderer() { }
    private static final net.minecraft.resources.Identifier MINEVIEW=net.minecraft.resources.Identifier.fromNamespaceAndPath("academy","textures/effects/mineview.png");
    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent extract) { io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.on(extract, io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.Stage.AFTER_PARTICLES, OreSurveyRenderer::renderFrame); }
    private static void renderFrame(io.github.pinchan4273.reacademycraft.client.render.WorldFrame event) {
        if(!ClientOreSurvey.active())return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().position();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.setShader(io.github.pinchan4273.reacademycraft.client.render.Immediate.Shader.TEX_COLOR_CUT);io.github.pinchan4273.reacademycraft.client.render.Immediate.setShaderTexture(0,MINEVIEW);io.github.pinchan4273.reacademycraft.client.render.Immediate.enableBlend();io.github.pinchan4273.reacademycraft.client.render.Immediate.disableCull();io.github.pinchan4273.reacademycraft.client.render.Immediate.disableDepthTest();io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(false);
        var buffer=io.github.pinchan4273.reacademycraft.client.render.Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
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
                buffer.addVertex(matrix,x,y,z).setColor(color[0],color[1],color[2],alpha).setUv(i==1||i==2?1:0,i>=2?1:0);
            }
        }
        buffer.end();
    }
}
