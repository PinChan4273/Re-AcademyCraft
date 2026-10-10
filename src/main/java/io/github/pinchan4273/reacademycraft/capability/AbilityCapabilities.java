package io.github.pinchan4273.reacademycraft.capability;

import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** NeoForge版のAcademyAttachments.PLAYER_ABILITYを、Forgeのcapabilityで置き換えたもの。 */
public final class AbilityCapabilities {
    public static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("academy", "player_ability");
    public static final Capability<PlayerAbilityData> PLAYER_ABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private AbilityCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerAbilityData.class);
    }

    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            AbilityDataProvider provider = new AbilityDataProvider();
            // 原作の能力イベントはプレイヤーを指定する。データは自分のプレイヤーについてだけイベントを発火する。
            provider.bindOwner(player);
            event.addCapability(KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    /**
     * 原作CPData.Events.playerWakeup: 夜を寝て過ごして朝に起きたとき（早く起こされた場合や、
     * 自分でベッドを離れた場合を除く）、CPと過負荷をすべて回復する。
     */
    public static void wakeUp(net.minecraftforge.event.entity.player.PlayerWakeUpEvent event) {
        if (event.wakeImmediately() || event.updateLevel()
                || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        player.getCapability(PLAYER_ABILITY).ifPresent(PlayerAbilityData::recoverAll);
        io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents.sync(player, true);
    }

    public static void clonePlayer(PlayerEvent.Clone event) {
        Player replacement = event.getEntity();
        Player original = event.getOriginal();
        if (replacement.level().isClientSide || original == replacement) return;
        original.reviveCaps();
        try {
            original.getCapability(PLAYER_ABILITY).ifPresent(previous ->
                    replacement.getCapability(PLAYER_ABILITY).ifPresent(next -> {
                        next.copyFrom(previous);
                        next.forgetCooldowns();
                        // エンドからの帰還でも何度実行してよいように、代入だけを行う。状態を加えたり重複させたりしない。
                        if (event.isWasDeath()) next.recoverAfterDeath();
                    }));
        } finally {
            original.invalidateCaps();
        }
        // Forgeがこの置き換えを設定した後に、PlayerRespawnEventで写し・回復した状態を送る。
        // 実際の接続での死亡・リスポーンの順序は、専用サーバーの検査で確かめている。
    }
}
