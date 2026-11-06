package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyIntensifyHudAnimation;
import net.minecraft.resources.Identifier;

/** 原作のマスクとspriteの絵を変えずに、状態を保つForgeのGUIのadapterで描く。 */
public final class LegacyIntensifyHudRenderer {
    public static final Identifier MASK = id("em_intensify_mask.png");
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", "textures/effects/" + path); }
    public static Identifier arc(int index) {
        if (index < 0 || index >= 10) throw new IllegalArgumentException("HUD sprite outside pool");
        return id("arcs/" + index + ".png");
    }
    private LegacyIntensifyHudRenderer() { }
    /** GUIの倍率を掛けた大きさ。原作のspriteは、意図して25〜40pxの正方形へ引き伸ばす。 */
    public static int draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int width, int height, LegacyIntensifyHudAnimation.Frame frame) {
        if (width <= 0 || height <= 0 || width > 32768 || height > 32768 || frame.arcs().size() > 14
                || !Double.isFinite(frame.maskAlpha()) || frame.maskAlpha() < 0 || frame.maskAlpha() > 1
                || !Double.isFinite(frame.arcAlpha()) || frame.arcAlpha() < 0 || frame.arcAlpha() > 1)
            throw new IllegalArgumentException("Invalid HUD frame bounds");
        if (frame.disposed()) return 0;
        int quads=0;
        {
            io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShaderColor(1,1,1,1);
            if (frame.maskAlpha()>0) {
                quad(graphics,null,0,0,width,height,0,(float)(.1*frame.maskAlpha()),false);
                quad(graphics,MASK,0,0,width,height,1,(float)frame.maskAlpha(),true); quads+=2;
            }
            if (frame.arcAlpha()>0) for (var arc:frame.arcs()) if (arc.visible()&&!arc.dead()) {
                if (arc.template()<0||arc.template()>=10||!Double.isFinite(arc.x())||!Double.isFinite(arc.y())
                        ||Math.abs(arc.x())>1||Math.abs(arc.y())>1||!Double.isFinite(arc.size())||arc.size()<=0||arc.size()>40)
                    throw new IllegalArgumentException("Invalid HUD sprite bounds");
                quad(graphics,arc(arc.template()),(float)(width*.5*(1+arc.x())-arc.size()*.5),
                        (float)(height*.5*(1+arc.y())-arc.size()*.5),(float)arc.size(),(float)arc.size(),1,(float)frame.arcAlpha(),true); quads++;
            }
            io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShaderColor(1,1,1,1);
        }
        return quads;
    }
    private static void quad(net.minecraft.client.gui.GuiGraphicsExtractor graphics,Identifier texture,float x,float y,float width,float height,float rgb,float alpha,boolean textured) {
        // position_tex 相当（0 だけを捨てる。position_tex_color は 0.1 未満を捨てるので、原作のマスク（最大25/255）が消えてしまう）。
        io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShader(textured?io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.Shader.TEX_COLOR_KEEP:io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.Shader.COLOR);
        if(textured)io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShaderTexture(0,texture);
        io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.setShaderColor(textured?rgb:1,textured?rgb:1,textured?rgb:1,textured?alpha:1);
        var buffer=io.github.pinchan4273.reacademycraft.client.render.GuiImmediate.begin(graphics);var matrix=graphics.pose();
        float[][] vertices={{x,y+height,0,1},{x+width,y+height,1,1},{x+width,y,1,0},{x,y,0,0}};
        for(var v:vertices){var out=buffer.addVertex(matrix,v[0],v[1],0);if(textured)out.setUv(v[2],v[3]);else out.setColor(rgb,rgb,rgb,alpha);}
        buffer.end();
    }
}
