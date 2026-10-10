package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.VecAccel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作ParabolaEffect（Vector Accelerationの狙い）: 充電の間、一人称のときだけ、跳躍の軌跡を目の横から
 * glow_lineの帯で描く。飛行の約3分の1秒で消えていく。跳躍できないとき（2ブロック以内に地面が無い、
 * 練度50%未満）は赤。軌跡は、その時点までの充電による跳躍自身の速度で計算する。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientVecAccelParabola {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png");
    private static long drawnFrames;
    private ClientVecAccelParabola() { }

    /** 原作ParabolaRendererの頂点: 目の横から、0.02ずつ100歩。各歩で速度に0.98を掛け、重力1.9 * 0.02を引く。術者の足元からの相対位置。 */
    static List<Vec3> path(float yaw, float pitch, int ticks) {
        var speed = VecAccel.velocity(yaw, pitch, ticks);
        var look = Vec3.directionFromRotation(pitch, yaw);
        // 原作rotateYaw(90)はラジアンで受け取る: 直角ではなく90ラジアン。原作の回し方のまま残している。
        var side = new Vec3(look.x, 0, look.z).yRot(90).normalize().scale(-.08);
        var pos = new Vec3(side.x, 1.56, side.z).subtract(look.scale(.12));
        var vertices = new ArrayList<Vec3>(100);
        double dt = .02;
        for (int i = 0; i < 100; i++) {
            vertices.add(pos);
            speed = speed.scale(.98);
            pos = pos.add(speed.scale(dt));
            speed = new Vec3(speed.x, speed.y - dt * 1.9, speed.z);
        }
        return vertices;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null || !client.options.getCameraType().isFirstPerson()) return;
        int ticks = ClientTeleporterAim.heldTicks(VecAccel.ID);
        if (ticks < 0) return;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null) return;
        float partial = event.getPartialTick();
        boolean canPerform = data.getProficiency(VecAccel.ID) > .5f || VecAccel.onGround(player);
        var path = path(Mth.lerp(partial, player.yRotO, player.getYRot()), Mth.lerp(partial, player.xRotO, player.getXRot()), ticks);
        var origin = player.getPosition(partial).subtract(event.getCamera().getPosition());
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.x, origin.y, origin.z);
        // 原作は放物線をalphaテストなしで描いた（LegacyAlphaShaders）。
        RenderSystem.setShader(LegacyAlphaShaders::positionTexColor);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float g = canPerform ? 1 : .2f;
        double h = .02;
        for (int i = 1; i < path.size(); i++) {
            // 原作のalphaは0.7 * (1 - i * 0.03): 34番目の区間から0未満になり、GLが0へ切り詰める。
            float alpha = .7f * (1 - i * .03f);
            if (alpha <= 0) break;
            var prev = path.get(i - 1); var cur = path.get(i);
            buffer.vertex(m, (float) prev.x, (float) (prev.y + h), (float) prev.z).uv(0, 0).color(1, g, g, alpha).endVertex();
            buffer.vertex(m, (float) prev.x, (float) (prev.y - h), (float) prev.z).uv(0, 1).color(1, g, g, alpha).endVertex();
            buffer.vertex(m, (float) cur.x, (float) (cur.y - h), (float) cur.z).uv(1, 1).color(1, g, g, alpha).endVertex();
            buffer.vertex(m, (float) cur.x, (float) (cur.y + h), (float) cur.z).uv(1, 0).color(1, g, g, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        pose.popPose();
        drawnFrames++;
    }
    /** テスト用の入口: 放物線を描いたフレーム数。 */
    public static long drawnFrames() { return drawnFrames; }
}
