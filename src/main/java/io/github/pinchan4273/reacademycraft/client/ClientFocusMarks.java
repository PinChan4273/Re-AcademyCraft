package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TOGGLE-GUI-001 F03（P-C′＋M2）: Storm Wing・Flashingが読む方向入力（前・後・左・右の移動キー）の、フォーカスの喪失の印
 * （{@link SteeringGate.FocusMark}）。印のある方向は、そのキーの本当の離しを見るまで推進・狙いに使わない。バニラの移動そのものは変えない。
 *
 * <p>キーのイベントは{@link AbilityControls}が集めて知らせる（画面の経路とInputEvent.Keyの重複を除いた後）。印のtickは、
 * Storm Wing（tickの初め）・Flashing（tickの終わり）が読む前に進める（tickの初め、優先度HIGHEST）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientFocusMarks {
    private ClientFocusMarks() { }
    /** 前・後・左・右（Storm Wingの添字の順）。 */
    private static final SteeringGate.FocusMark[] DIRECTIONS = { new SteeringGate.FocusMark(), new SteeringGate.FocusMark(),
            new SteeringGate.FocusMark(), new SteeringGate.FocusMark() };

    private static KeyMapping[] mappings() {
        var options = Minecraft.getInstance().options;
        return new KeyMapping[] { options.keyUp, options.keyDown, options.keyLeft, options.keyRight };
    }

    /** GLFWのキー・ボタンのイベント（{@link AbilityControls}が届いた順に呼ぶ）。割り当てが同じ方向へ知らせる。 */
    static void event(InputConstants.Key key, int action, boolean windowActive) {
        var mappings = mappings();
        for (int i = 0; i < 4; i++) if (key.equals(mappings[i].getKey())) DIRECTIONS[i].event(action, windowActive);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance();
        var mappings = mappings();
        for (int i = 0; i < 4; i++)
            DIRECTIONS[i].tick(client.isWindowActive(), mappings[i].getKey().getType() != InputConstants.Type.MOUSE);
    }

    /** その移動キーに、フォーカスの喪失の印があるか（前・後・左・右以外のキーはfalse）。 */
    public static boolean marked(KeyMapping mapping) {
        var mappings = mappings();
        for (int i = 0; i < 4; i++) if (mappings[i] == mapping) return DIRECTIONS[i].marked();
        return false;
    }
}
