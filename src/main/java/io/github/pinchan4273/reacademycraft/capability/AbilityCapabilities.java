package io.github.pinchan4273.reacademycraft.capability;

import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * プレイヤーの能力データ。NeoForgeのData Attachment（Forge 1.20.1版はcapabilityとAbilityDataProvider）。
 *
 * <p>保存: プレイヤーのNBTの{@code neoforge:attachments}の下、{@code academy:player_ability}に{@link PlayerAbilityData#save()}の形で置く
 * （Forge版は{@code ForgeCaps}の下。Forge版のセーブからの移行はしない: ユーザーの判断で、セーブの互換はNeoForgeの版どうしだけ）。
 * 読むときは、Forge版のdeserializeNBTと同じくクールダウンを捨てる。
 *
 * <p>死亡と帰還の写し（{@link #clonePlayer}）は、attachmentのcopyOnDeathに任せず明示的に行う: 所有者は新しいプレイヤー、
 * クールダウンは捨て、死亡なら回復する。NeoForge自身も死亡でない複製ではattachmentを写すので、こちらは最も低い優先度で最後に
 * 代入し、結果がForge版と同じになるようにする。
 */
public final class AbilityCapabilities {
    public static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("academy", "player_ability");
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, KEY.getNamespace());
    public static final Supplier<AttachmentType<PlayerAbilityData>> PLAYER_ABILITY = ATTACHMENTS.register(KEY.getPath(),
            () -> AttachmentType.builder(AbilityCapabilities::create).serialize(new IAttachmentSerializer<CompoundTag, PlayerAbilityData>() {
                @Override public PlayerAbilityData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    var data = create(holder);
                    data.load(tag);
                    data.forgetCooldowns();
                    return data;
                }
                @Override public CompoundTag write(PlayerAbilityData data, HolderLookup.Provider provider) { return data.save(); }
            }).build());

    private AbilityCapabilities() {}

    /** 原作の能力イベントはプレイヤーを指定する。データは自分のプレイヤーについてだけイベントを発火する。 */
    private static PlayerAbilityData create(IAttachmentHolder holder) {
        var data = new PlayerAbilityData();
        if (holder instanceof Player player) data.bindOwner(player);
        return data;
    }

    public static void register(IEventBus modBus) { ATTACHMENTS.register(modBus); }

    /**
     * Forge版の{@code entity.getCapability(PLAYER_ABILITY)}に当たる: プレイヤーなら能力データ、それ以外は空。
     * プレイヤー以外にattachmentを作らないよう、getDataはプレイヤーにだけ呼ぶ。
     */
    public static Optional<PlayerAbilityData> of(Entity entity) {
        return entity instanceof Player player ? Optional.of(player.getData(PLAYER_ABILITY)) : Optional.empty();
    }

    /**
     * 原作CPData.Events.playerWakeup: 夜を寝て過ごして朝に起きたとき（早く起こされた場合や、
     * 自分でベッドを離れた場合を除く）、CPと過負荷をすべて回復する。
     */
    public static void wakeUp(net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) {
        if (event.wakeImmediately() || event.updateLevel()
                || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        of(player).ifPresent(PlayerAbilityData::recoverAll);
        io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents.sync(player, true);
    }

    public static void clonePlayer(PlayerEvent.Clone event) {
        Player replacement = event.getEntity();
        Player original = event.getOriginal();
        if (replacement.level().isClientSide || original == replacement) return;
        PlayerAbilityData previous = original.getData(PLAYER_ABILITY), next = replacement.getData(PLAYER_ABILITY);
        next.copyFrom(previous);
        next.forgetCooldowns();
        // エンドからの帰還でも何度実行してよいように、代入だけを行う。状態を加えたり重複させたりしない。
        if (event.isWasDeath()) next.recoverAfterDeath();
        // この置き換えの後に、PlayerRespawnEventで写し・回復した状態を送る。
    }
}
