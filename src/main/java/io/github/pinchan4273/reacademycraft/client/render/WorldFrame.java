package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;

/**
 * 1.21.1 の {@code RenderLevelStageEvent} の、演出が読んでいた部分（カメラ、補間の値、pose）。
 * 名前を同じにして、演出の本体を変えずに提出の段階で動かす。
 */
public record WorldFrame(Camera camera, DeltaTracker delta, PoseStack pose) {
    public Camera getCamera() { return camera; }
    public DeltaTracker getPartialTick() { return delta; }
    public PoseStack getPoseStack() { return pose; }
}
