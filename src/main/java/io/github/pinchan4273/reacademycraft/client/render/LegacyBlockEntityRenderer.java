package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 の {@code BlockEntityRenderer#render} の形で書いた機械の描画を、26.2 の extract と submit へつなぐ。
 * 状態には BE と補間の値だけを写し、同じフレームの submit の段階で読む（ワールドの tick はその間に進まない）。
 */
public abstract class LegacyBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, LegacyBlockEntityRenderer.State<T>> {
    public static final class State<T extends BlockEntity> extends BlockEntityRenderState {
        T tile;
        float partialTick;
    }

    protected abstract void render(T tile, float partialTick, PoseStack pose, SubmitNodeCollector collector, int light, int overlay);

    @Override public State<T> createRenderState() { return new State<>(); }

    @Override public void extractRenderState(T tile, State<T> state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, camera, breakProgress);
        state.tile = tile;
        state.partialTick = partialTicks;
    }

    @Override public void submit(State<T> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        render(state.tile, state.partialTick, pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
    }
}
