package io.github.pinchan4273.reacademycraft.api.neoforge;

import io.github.pinchan4273.reacademycraft.ability.AbilityRegistry;
import io.github.pinchan4273.reacademycraft.api.AbilityRegistrar;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * 能力のカテゴリと技能を登録するためのイベント（公開API、実験的。NeoForge 1.21.1の接続の部分）。Re:AcademyCraftは、共通の準備の段（FMLCommonSetupEvent）の
 * 中で、このイベントをすべてのmodのイベントバス（modのバス）へ1回だけ出し、その直後にレジストリを凍結する。アドオンは自分のmodのバスで受け、
 * {@link #registrar(String)}に自分のmodのIDを渡して登録する。凍結の後の登録、同じIDの2回目、名前空間{@code academy}の登録は例外になり、
 * ゲームの読み込みはそこで止まる（原因のmodのIDが例外に出る）。
 */
public final class RegisterAbilitiesEvent extends Event implements IModBusEvent {
    /** そのmodの登録用のレジストラ。 */
    public AbilityRegistrar registrar(String modId) { return AbilityRegistry.INSTANCE.registrar(modId); }
    /** そのmodの技能の振る舞いを登録するレジストラ（1.4.0）。パッシブでない技能には、1つずつ振る舞いが要る。 */
    public io.github.pinchan4273.reacademycraft.api.mc.SkillBehaviorRegistrar behaviors(String modId) {
        return io.github.pinchan4273.reacademycraft.ability.SkillBehaviorRegistry.INSTANCE.registrar(modId);
    }
}
