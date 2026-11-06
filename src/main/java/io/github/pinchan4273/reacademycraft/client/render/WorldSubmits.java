package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * ワールドの中の演出の、extract と submit の間の受け渡し。
 * 演出は {@code ExtractLevelRenderStateEvent} の中で {@link Submit} を {@link #add} し、ここで同じフレームの submit の段階に順に実行する。
 * pose はカメラ基準（1.21.1 の {@code RenderLevelStageEvent} の pose と同じく、原点がカメラの位置）。
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WorldSubmits {
    /** 1.21.1 の描く段階に当たる物。 */
    public enum Stage {
        /** 1.21.1 の AFTER_ENTITIES と AFTER_BLOCK_ENTITIES: 半透明の地形（水）と粒子の前。 */
        BEFORE_TRANSLUCENT_TERRAIN,
        /** 1.21.1 の AFTER_TRANSLUCENT_BLOCKS: 半透明の地形の後、粒子の前。 */
        AFTER_TRANSLUCENT_TERRAIN,
        /** 1.21.1 の AFTER_PARTICLES: 半透明の地形と粒子の後。 */
        AFTER_PARTICLES
    }

    /** 1 つの演出の提出。extract の時に写した値と、同じフレームの間は変わらない状態だけを使う。 */
    @FunctionalInterface
    public interface Submit {
        void run(WorldDraw draw, PoseStack pose);
    }

    private record Planned(Stage stage, Submit submit) { }

    private static final ContextKey<List<Planned>> KEY = new ContextKey<>(Identifier.fromNamespaceAndPath("academy", "world_submits"));
    /** vanilla の order 0 の粒子などより後に実行する。 */
    private static final int AFTER_VANILLA = 1000;
    /** order 0 の粒子より前に実行する。 */
    private static final int BEFORE_VANILLA = -1000;
    private static @Nullable WorldDraw current;

    private WorldSubmits() { }

    /** 今の提出先。提出の段階の中でだけ使える。 */
    public static WorldDraw current() {
        if (current == null) throw new IllegalStateException("Not submitting");
        return current;
    }

    /** 1.21.1 の AFTER_PARTICLES の段階で描く。 */
    public static void add(LevelRenderState state, Submit submit) { add(state, Stage.AFTER_PARTICLES, submit); }

    public static void add(LevelRenderState state, Stage stage, Submit submit) {
        List<Planned> list = state.getRenderData(KEY);
        if (list == null) {
            list = new ArrayList<>();
            state.setRenderData(KEY, list);
        }
        list.add(new Planned(stage, submit));
    }

    /** 1.21.1 の {@code RenderLevelStageEvent} の処理を、同じ形（{@link WorldFrame}）で提出の段階に動かす。 */
    public static void on(ExtractLevelRenderStateEvent event, Stage stage, Consumer<WorldFrame> render) {
        var camera = event.getCamera();
        var delta = event.getDeltaTracker();
        add(event.getRenderState(), stage, (draw, pose) -> render.accept(new WorldFrame(camera, delta, pose)));
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        List<Planned> list = event.getLevelRenderState().getRenderData(KEY);
        if (list == null) return;
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        SubmitNodeCollection late = (SubmitNodeCollection) collector.order(AFTER_VANILLA);
        SubmitNodeCollection early = (SubmitNodeCollection) collector.order(BEFORE_VANILLA);
        FeatureRenderPhase<SubmitNode> beforeTerrain = late.translucentCustomGeometry, afterTerrain = early.afterTerrain, afterParticles = late.afterTerrain;
        var drawBeforeTerrain = new WorldDraw(beforeTerrain);
        var drawAfterTerrain = new WorldDraw(afterTerrain);
        var drawAfterParticles = new WorldDraw(afterParticles);
        try {
            for (Planned planned : list) {
                current = switch (planned.stage()) {
                    case BEFORE_TRANSLUCENT_TERRAIN -> drawBeforeTerrain;
                    case AFTER_TRANSLUCENT_TERRAIN -> drawAfterTerrain;
                    case AFTER_PARTICLES -> drawAfterParticles;
                };
                Immediate.reset();
                planned.submit().run(current, event.getPoseStack());
            }
        } finally {
            current = null;
        }
    }
}
