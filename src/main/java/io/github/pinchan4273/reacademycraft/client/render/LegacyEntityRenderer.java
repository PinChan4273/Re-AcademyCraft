package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

/** 1.21.1 の {@code EntityRenderer#render} の形で書いた描画を、26.2 の extract と submit へつなぐ。実体は同じフレームの submit の段階で読む。 */
public abstract class LegacyEntityRenderer<E extends Entity> extends EntityRenderer<E, LegacyEntityRenderer.State<E>> {
    public static final class State<E extends Entity> extends EntityRenderState {
        E entity;
    }

    protected LegacyEntityRenderer(EntityRendererProvider.Context context) { super(context); }

    protected abstract void render(E entity, float partialTick, PoseStack pose, SubmitNodeCollector collector, int light);

    @Override public State<E> createRenderState() { return new State<>(); }

    @Override public void extractRenderState(E entity, State<E> state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.entity = entity;
    }

    @Override public void submit(State<E> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        render(state.entity, state.partialTick, pose, collector, state.lightCoords);
        super.submit(state, pose, collector, camera);
    }
}
