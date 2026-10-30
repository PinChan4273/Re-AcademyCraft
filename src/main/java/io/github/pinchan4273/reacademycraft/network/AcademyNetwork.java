package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.client.ClientAbilitySync;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * このmodの通信と、各通知の登録。NeoForge 1.21.1のpayload（Forge 1.20.1版はSimpleChannelと番号）。
 * 各通知のIDは{@code academy:<classの名前のsnake_case>}（{@link AcademyPayload}）。並びと処理はForge版の登録と同じ。
 */
public final class AcademyNetwork {
    /**
     * 通信の版。通知の並びが変わったら上げる。61: プリセットの編集を1操作ずつにし、金属成形・融合のレシピの並びを独自にした。
     * 62: Storm Wingの推進の入力（StormWingInput）と状態の番号を足し、Light Shieldを押し続けからトグルにした（古いクライアントは
     * 心拍とキーアップでLight Shieldを動かすので、新しいサーバーへ繋がせない）。
     * 63: Light Shieldの盾面の状態（LightShieldState。payloadのIDは`academy:light_shield_state`）を足し、Light Shieldの表示を
     * SkillVisualから移した（Forge 1.3.0の工程2。62のクライアントは盾の向きを知らないので繋がせない）。
     * 版が違う相手とは、ログインの時点で接続を断る（NeoForgeのregistrarの版。古い通知を誤って読むところまで進ませない）。
     * NeoForge版の通信はForge版と繋がらないので、版の番号はForge版の番号に合わせている。
     */
    public static final String VERSION = "63";
    private static final Map<Class<?>, CustomPacketPayload.Type<?>> TYPES = new ConcurrentHashMap<>();

    /** 相手の版を受け入れるか。クライアント・サーバーとも同じ版だけ。 */
    public static boolean accepts(String remote) {
        return VERSION.equals(remote);
    }

    private AcademyNetwork() {}

    /** 通知のclassのpayloadの型。IDはclassの単純名のsnake_case。 */
    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(Class<T> payload) {
        return (CustomPacketPayload.Type<T>) TYPES.computeIfAbsent(payload, c -> new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath("academy", c.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT))));
    }
    private static <T extends CustomPacketPayload> void toClient(PayloadRegistrar registrar, Class<T> payload, StreamMemberEncoder<FriendlyByteBuf, T> encoder,
                                                                 StreamDecoder<FriendlyByteBuf, T> decoder, IPayloadHandler<T> handler) {
        registrar.playToClient(type(payload), StreamCodec.ofMember(encoder, decoder), handler);
    }
    private static <T extends CustomPacketPayload> void toServer(PayloadRegistrar registrar, Class<T> payload, StreamMemberEncoder<FriendlyByteBuf, T> encoder,
                                                                 StreamDecoder<FriendlyByteBuf, T> decoder, IPayloadHandler<T> handler) {
        registrar.playToServer(type(payload), StreamCodec.ofMember(encoder, decoder), handler);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AcademyNetwork::registerPayloads);
    }

    /** 処理はNeoForgeの既定どおりメインスレッドで動く（Forge版のconsumerMainThreadと同じ）。 */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        toClient(registrar, AbilitySnapshot.class, AbilitySnapshot::encode, AbilitySnapshot::decode, (packet, context) -> ClientAbilitySync.receive(packet));
        toServer(registrar, AbilityAction.class, AbilityAction::encode, AbilityAction::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toServer(registrar, PresetEdit.class, PresetEdit::encode, PresetEdit::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, OreSurveyStart.class, OreSurveyStart::encode, OreSurveyStart::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientOreSurvey.receive(packet));
        toClient(registrar, ThunderClapEffect.class, ThunderClapEffect::encode, ThunderClapEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientThunderClap.receive(packet));
        toClient(registrar, ChargingLoopEffect.class, ChargingLoopEffect::encode, ChargingLoopEffect::decode, (packet, context) -> {
                            io.github.pinchan4273.reacademycraft.client.ClientChargingLoop.receive(packet);
                            io.github.pinchan4273.reacademycraft.client.ClientChargingSurround.receive(packet);
                            io.github.pinchan4273.reacademycraft.client.ClientChargingArc.receive(packet);
                        });
        toClient(registrar, ChargingArcTarget.class, ChargingArcTarget::encode, ChargingArcTarget::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientChargingArc.receive(packet));
        toClient(registrar, IntensifyLoopEffect.class, IntensifyLoopEffect::encode, IntensifyLoopEffect::decode, (packet, context) -> {
                            io.github.pinchan4273.reacademycraft.client.ClientIntensifyLoop.receive(packet);
                            io.github.pinchan4273.reacademycraft.client.ClientIntensifyHud.receive(packet);
                        });
        toClient(registrar, IntensifyBurstEffect.class, IntensifyBurstEffect::encode, IntensifyBurstEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientIntensifyBurst.receive(packet));
        toClient(registrar, ArcGenEffect.class, ArcGenEffect::encode, ArcGenEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientArcGenEffect.receive(packet));
        toClient(registrar, ThunderBoltEffect.class, ThunderBoltEffect::encode, ThunderBoltEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientThunderBoltEffect.receive(packet));
        toClient(registrar, ThunderClapPresentationEffect.class, ThunderClapPresentationEffect::encode, ThunderClapPresentationEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientThunderClapPresentation.receive(packet));
        toClient(registrar, MdBallEffect.class, MdBallEffect::encode, MdBallEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientMdEffects.receive(packet));
        toClient(registrar, MdRayEffect.class, MdRayEffect::encode, MdRayEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientMdEffects.receive(packet));
        toServer(registrar, LocationRequest.class, LocationRequest::encode, LocationRequest::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, LocationList.class, LocationList::encode, LocationList::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.LocationTeleportScreen.receive(packet));
        toServer(registrar, FlashingPerform.class, FlashingPerform::encode, FlashingPerform::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, FlashingState.class, FlashingState::encode, FlashingState::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientFlashing.receive(packet));
        toClient(registrar, SkillModeState.class, SkillModeState::encode, SkillModeState::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientSkillModes.receive(packet));
        toClient(registrar, StormWingState.class, StormWingState::encode, StormWingState::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientStormWing.receive(packet));
        toClient(registrar, FollowSound.class, FollowSound::encode, FollowSound::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientFollowSounds.receive(packet));
        toClient(registrar, AimState.class, AimState::encode, AimState::decode, (packet, context) -> { io.github.pinchan4273.reacademycraft.client.ClientTeleporterAim.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientJetEngineEffect.receive(packet); });
        toClient(registrar, VmWave.class, VmWave::encode, VmWave::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientVmWaves.receive(packet));
        toClient(registrar, SkillVisual.class, SkillVisual::encode, SkillVisual::decode, (packet, context) -> { io.github.pinchan4273.reacademycraft.client.ClientStormWingEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientPlasmaCannonEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientMineRayEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientJetEngineEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientMagneticEffects.receive(packet); });
        toClient(registrar, TerminalInstalled.class, TerminalInstalled::encode, TerminalInstalled::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.TerminalInstallEffect.start());
        toClient(registrar, TerminalSnapshot.class, TerminalSnapshot::encode, TerminalSnapshot::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientTerminal.receive(packet));
        toServer(registrar, TerminalSetting.class, TerminalSetting::encode, TerminalSetting::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toServer(registrar, TerminalOpenApp.class, TerminalOpenApp::encode, TerminalOpenApp::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        // 1つのrecordで双方向: パネルが望むリストをサーバーへ、機械のリストをパネルへ。
        toServer(registrar, InterfererWhitelist.class, InterfererWhitelist::encode, InterfererWhitelist::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, InterfererWhitelistSync.class, InterfererWhitelistSync::encode, InterfererWhitelistSync::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.AbilityInterfererScreen.receive(packet));
        toServer(registrar, NodePassword.class, NodePassword::encode, NodePassword::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toServer(registrar, FreqRequest.class, FreqRequest::encode, FreqRequest::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, FreqReply.class, FreqReply::encode, FreqReply::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.FreqTransmitterHud.receive(packet));
        toClient(registrar, TutorialSnapshot.class, TutorialSnapshot::encode, TutorialSnapshot::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.TutorialNotifyHud.receive(packet));
        toClient(registrar, MediaSnapshot.class, MediaSnapshot::encode, MediaSnapshot::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientTerminal.receive(packet));
        toServer(registrar, NodeRename.class, NodeRename::encode, NodeRename::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toServer(registrar, WirelessConfig.class, WirelessConfig::encode, WirelessConfig::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, SkillTrail.class, SkillTrail::encode, SkillTrail::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientTeleporterBursts.receive(packet));
        toClient(registrar, SkillBurst.class, SkillBurst::encode, SkillBurst::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientTeleporterBursts.receive(packet));
        toClient(registrar, SkillVisualMove.class, SkillVisualMove::encode, SkillVisualMove::decode, (packet, context) -> { io.github.pinchan4273.reacademycraft.client.ClientPlasmaCannonEffect.move(packet); io.github.pinchan4273.reacademycraft.client.ClientMineRayEffect.move(packet); io.github.pinchan4273.reacademycraft.client.ClientMagneticEffects.move(packet); });
        // 新しいパケットは末尾に追加する。（リストの途中に登録したパケットが専用サーバーのログインを止めるという見立ては、
        // 後に成り立たないと分かった: ログインのタイムアウトは非決定的に起きる。）
        toServer(registrar, PenetrateDistance.class, PenetrateDistance::encode, PenetrateDistance::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toServer(registrar, WirelessPageRequest.class, WirelessPageRequest::encode, WirelessPageRequest::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, WirelessPageReply.class, WirelessPageReply::encode, WirelessPageReply::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.TechScreen.receive(packet));
        toClient(registrar, GroundshockEffect.class, GroundshockEffect::encode, GroundshockEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientGroundshock.receive(packet));
        toClient(registrar, BloodRetroEffect.class, BloodRetroEffect::encode, BloodRetroEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientBloodRetro.receive(packet));
        toClient(registrar, DeveloperNode.class, DeveloperNode::encode, DeveloperNode::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.DeveloperScreen.receive(packet));
        toClient(registrar, RailgunHandEffect.class, RailgunHandEffect::encode, RailgunHandEffect::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientRailgunHand.receive(packet));
        toClient(registrar, BodyIntensifyState.class, BodyIntensifyState::encode, BodyIntensifyState::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientBodyIntensify.receive(packet));
        toClient(registrar, AcademyRulesSnapshot.class, AcademyRulesSnapshot::encode, AcademyRulesSnapshot::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientAcademyRules.receive(packet));
        toServer(registrar, StormWingInput.class, StormWingInput::encode, StormWingInput::decode, (packet, context) -> packet.handle((ServerPlayer) context.player()));
        toClient(registrar, LightShieldState.class, LightShieldState::encode, LightShieldState::decode, (packet, context) -> io.github.pinchan4273.reacademycraft.client.ClientLightShieldEffect.receive(packet));
    }

    public static void lightning(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        var effect = new ThunderClapEffect(level.dimension().location(), position);
        PacketDistributor.sendToPlayersNear(level, null, position.x, position.y, position.z, 128, effect);
    }

    public static void send(ServerPlayer player, ChargingLoopEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, ChargingArcTarget target) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, target);
    }
    public static void send(ServerPlayer player, ArcGenEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, MdBallEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, MdRayEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, ThunderBoltEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, ThunderClapPresentationEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, IntensifyBurstEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }
    public static void send(ServerPlayer player, IntensifyLoopEffect effect) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, effect);
    }

    public static void send(ServerPlayer player, OreSurveyStart grant) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, grant);
    }

    public static void send(ServerPlayer player, AbilitySnapshot snapshot) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, snapshot);
    }
}
