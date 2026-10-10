package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyIntensifyHudAnimation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** 原作のマスクとspriteの絵を変えずに、状態を保つForgeのGUIのadapterで描く。 */
public final class LegacyIntensifyHudRenderer {
    public static final ResourceLocation MASK = id("em_intensify_mask.png");
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + path); }
    public static ResourceLocation arc(int index) {
        if (index < 0 || index >= 10) throw new IllegalArgumentException("HUD sprite outside pool");
        return id("arcs/" + index + ".png");
    }
    private static final class Types extends RenderType {
        private Types() { super("academy_unused_hud", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
        private static final RenderType BLACK = create("academy_intensify_black", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,256,false,false,CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        private static RenderType texture(ResourceLocation texture) {
            // POSITION_COLOR_TEXはalpha 0.1未満を捨てるので、原作のマスク（最大25/255）が消えてしまう。
            return create("academy_intensify_texture",DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS,256,false,false,
                    CompositeState.builder().setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexShader)).setTextureState(new TextureStateShard(texture,false,false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
        private static final RenderType MASK_TYPE = texture(MASK);
        private static final RenderType[] ARCS = java.util.stream.IntStream.range(0,10).mapToObj(i->texture(arc(i))).toArray(RenderType[]::new);
    }
    private LegacyIntensifyHudRenderer() { }
    /** GUIの倍率を掛けた大きさ。原作のspriteは、意図して25〜40pxの正方形へ引き伸ばす。 */
    public static int draw(PoseStack pose, int width, int height, LegacyIntensifyHudAnimation.Frame frame) {
        RenderSystem.assertOnRenderThread();
        if (width <= 0 || height <= 0 || width > 32768 || height > 32768 || frame.arcs().size() > 14
                || !Double.isFinite(frame.maskAlpha()) || frame.maskAlpha() < 0 || frame.maskAlpha() > 1
                || !Double.isFinite(frame.arcAlpha()) || frame.arcAlpha() < 0 || frame.arcAlpha() > 1)
            throw new IllegalArgumentException("Invalid HUD frame bounds");
        if (frame.disposed()) return 0;
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int function=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader=RenderSystem.getShader(); int texture=RenderSystem.getShaderTexture(0); float[] color=RenderSystem.getShaderColor().clone();
        int quads=0;
        try {
            RenderSystem.setShaderColor(1,1,1,1); RenderSystem.disableDepthTest();
            if (frame.maskAlpha()>0) {
                quad(pose,Types.BLACK,0,0,width,height,0,(float)(.1*frame.maskAlpha()),false);
                quad(pose,Types.MASK_TYPE,0,0,width,height,1,(float)frame.maskAlpha(),true); quads+=2;
            }
            if (frame.arcAlpha()>0) for (var arc:frame.arcs()) if (arc.visible()&&!arc.dead()) {
                if (arc.template()<0||arc.template()>=10||!Double.isFinite(arc.x())||!Double.isFinite(arc.y())
                        ||Math.abs(arc.x())>1||Math.abs(arc.y())>1||!Double.isFinite(arc.size())||arc.size()<=0||arc.size()>40)
                    throw new IllegalArgumentException("Invalid HUD sprite bounds");
                quad(pose,Types.ARCS[arc.template()],(float)(width*.5*(1+arc.x())-arc.size()*.5),
                        (float)(height*.5*(1+arc.y())-arc.size()*.5),(float)arc.size(),(float)arc.size(),1,(float)frame.arcAlpha(),true); quads++;
            }
        } finally {
            RenderSystem.depthFunc(function);RenderSystem.depthMask(mask);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,texture);
        }
        return quads;
    }
    private static void quad(PoseStack pose,RenderType type,float x,float y,float width,float height,float rgb,float alpha,boolean textured) {
        RenderSystem.setShaderColor(textured?rgb:1,textured?rgb:1,textured?rgb:1,textured?alpha:1);
        var buffer=BUFFERS.getBuffer(type);var matrix=pose.last().pose();
        float[][] vertices={{x,y+height,0,1},{x+width,y+height,1,1},{x+width,y,1,0},{x,y,0,0}};
        for(var v:vertices){var out=buffer.vertex(matrix,v[0],v[1],0);if(textured)out.uv(v[2],v[3]);else out.color(rgb,rgb,rgb,alpha);out.endVertex();}
        BUFFERS.endBatch(type);
    }
}
