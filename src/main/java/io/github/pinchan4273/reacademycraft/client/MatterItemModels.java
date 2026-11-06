package io.github.pinchan4273.reacademycraft.client;

import com.mojang.serialization.MapCodec;
import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * WeAthFolDによる原作ItemMatterUnitの4枚の絵のアニメーション。PNGは変更していない。
 * 値は 0 以上 1 未満へ正規化している。ワールドやNBTは変更しない。
 * 26.2: 1.21.1 の ItemProperties は assets/academy/items の range_dispatch と、このプロパティになった。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MatterItemModels {
    private static long previousMillis = -1, elapsedMillis;
    private MatterItemModels() { }

    /** 試験用: 今の絵の番号（0 から 3）。1.21.1 の frame の値の 4 倍に当たる。 */
    public static int currentFrame() { return (int) Math.floorMod(animationMillis() / 250L, 4L); }

    private record Frame() implements RangeSelectItemModelProperty {
        static final MapCodec<Frame> CODEC = MapCodec.unit(new Frame());
        @Override public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            return stack.is(PhaseContent.FILLED.get()) ? Math.floorMod(animationMillis() / 250L, 4L) / 4f : 0f;
        }
        @Override public MapCodec<Frame> type() { return CODEC; }
    }

    @SubscribeEvent public static void register(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath("academy", "frame"), Frame.CODEC);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        animationMillis();
    }
    // 原作LambdaLibのGameTimerは一時停止中の時間を含まず、アイテムが見えていなくても進む。
    private static long animationMillis() {
        long now = Util.getMillis();
        if (previousMillis >= 0 && !Minecraft.getInstance().isPaused()) elapsedMillis += Math.max(0, now - previousMillis);
        previousMillis = now;
        return elapsedMillis;
    }
}
