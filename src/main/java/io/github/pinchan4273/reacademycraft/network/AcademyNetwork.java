package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.client.ClientAbilitySync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** このmodの通信路（Forge 1.20.1のSimpleChannel）と、各通知の登録。 */
public final class AcademyNetwork {
    /**
     * 通信の版。通知の並びが変わったら上げる。61: プリセットの編集を1操作ずつにし、金属成形・融合のレシピの並びを独自にした。
     * 版が違う相手とは、ログインの時点で接続を断る（古い通知を誤って読むところまで進ませない）。
     */
    public static final String VERSION = "61";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("academy", "main"), () -> VERSION, AcademyNetwork::accepts, AcademyNetwork::accepts);

    /** 相手の版を受け入れるか。クライアント・サーバーとも同じ版だけ。 */
    public static boolean accepts(String remote) {
        return VERSION.equals(remote);
    }

    private AcademyNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(AbilitySnapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbilitySnapshot::encode).decoder(AbilitySnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientAbilitySync.receive(packet))).add();
        CHANNEL.messageBuilder(AbilityAction.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AbilityAction::encode).decoder(AbilityAction::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(PresetEdit.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PresetEdit::encode).decoder(PresetEdit::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(OreSurveyStart.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OreSurveyStart::encode).decoder(OreSurveyStart::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientOreSurvey.receive(packet))).add();
        CHANNEL.messageBuilder(ThunderClapEffect.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ThunderClapEffect::encode).decoder(ThunderClapEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientThunderClap.receive(packet))).add();
        CHANNEL.messageBuilder(ChargingLoopEffect.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChargingLoopEffect::encode).decoder(ChargingLoopEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> {
                            io.github.pinchan4273.reacademycraft.client.ClientChargingLoop.receive(packet);
                            io.github.pinchan4273.reacademycraft.client.ClientChargingSurround.receive(packet);
                            io.github.pinchan4273.reacademycraft.client.ClientChargingArc.receive(packet);
                        })).add();
        CHANNEL.messageBuilder(ChargingArcTarget.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChargingArcTarget::encode).decoder(ChargingArcTarget::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientChargingArc.receive(packet))).add();
        CHANNEL.messageBuilder(IntensifyLoopEffect.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IntensifyLoopEffect::encode).decoder(IntensifyLoopEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> {
                            io.github.pinchan4273.reacademycraft.client.ClientIntensifyLoop.receive(packet);
                            io.github.pinchan4273.reacademycraft.client.ClientIntensifyHud.receive(packet);
                        })).add();
        CHANNEL.messageBuilder(IntensifyBurstEffect.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IntensifyBurstEffect::encode).decoder(IntensifyBurstEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientIntensifyBurst.receive(packet))).add();
        CHANNEL.messageBuilder(ArcGenEffect.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ArcGenEffect::encode).decoder(ArcGenEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientArcGenEffect.receive(packet))).add();
        CHANNEL.messageBuilder(ThunderBoltEffect.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ThunderBoltEffect::encode).decoder(ThunderBoltEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientThunderBoltEffect.receive(packet))).add();
        CHANNEL.messageBuilder(ThunderClapPresentationEffect.class, 11, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ThunderClapPresentationEffect::encode).decoder(ThunderClapPresentationEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientThunderClapPresentation.receive(packet))).add();
        CHANNEL.messageBuilder(MdBallEffect.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MdBallEffect::encode).decoder(MdBallEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientMdEffects.receive(packet))).add();
        CHANNEL.messageBuilder(MdRayEffect.class, 13, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MdRayEffect::encode).decoder(MdRayEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientMdEffects.receive(packet))).add();
        CHANNEL.messageBuilder(LocationRequest.class, 14, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LocationRequest::encode).decoder(LocationRequest::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(LocationList.class, 15, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LocationList::encode).decoder(LocationList::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.LocationTeleportScreen.receive(packet))).add();
        CHANNEL.messageBuilder(FlashingPerform.class, 16, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlashingPerform::encode).decoder(FlashingPerform::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(FlashingState.class, 17, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashingState::encode).decoder(FlashingState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientFlashing.receive(packet))).add();
        CHANNEL.messageBuilder(SkillModeState.class, 18, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillModeState::encode).decoder(SkillModeState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientSkillModes.receive(packet))).add();
        CHANNEL.messageBuilder(StormWingState.class, 19, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StormWingState::encode).decoder(StormWingState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientStormWing.receive(packet))).add();
        CHANNEL.messageBuilder(FollowSound.class, 20, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FollowSound::encode).decoder(FollowSound::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientFollowSounds.receive(packet))).add();
        CHANNEL.messageBuilder(AimState.class, 21, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AimState::encode).decoder(AimState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> { io.github.pinchan4273.reacademycraft.client.ClientTeleporterAim.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientJetEngineEffect.receive(packet); })).add();
        CHANNEL.messageBuilder(VmWave.class, 22, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VmWave::encode).decoder(VmWave::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientVmWaves.receive(packet))).add();
        CHANNEL.messageBuilder(SkillVisual.class, 23, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillVisual::encode).decoder(SkillVisual::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> { io.github.pinchan4273.reacademycraft.client.ClientStormWingEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientPlasmaCannonEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientMineRayEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientLightShieldEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientJetEngineEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientMagneticEffects.receive(packet); })).add();
        CHANNEL.messageBuilder(TerminalInstalled.class, 32, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TerminalInstalled::encode).decoder(TerminalInstalled::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> io.github.pinchan4273.reacademycraft.client.TerminalInstallEffect::start)).add();
        CHANNEL.messageBuilder(TerminalSnapshot.class, 28, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TerminalSnapshot::encode).decoder(TerminalSnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientTerminal.receive(packet))).add();
        CHANNEL.messageBuilder(TerminalSetting.class, 30, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TerminalSetting::encode).decoder(TerminalSetting::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(TerminalOpenApp.class, 29, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TerminalOpenApp::encode).decoder(TerminalOpenApp::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        // 1つのrecordで双方向: パネルが望むリストをサーバーへ、機械のリストをパネルへ。
        CHANNEL.messageBuilder(InterfererWhitelist.class, 34, NetworkDirection.PLAY_TO_SERVER)
                .encoder(InterfererWhitelist::encode).decoder(InterfererWhitelist::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(InterfererWhitelistSync.class, 35, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InterfererWhitelistSync::encode).decoder(InterfererWhitelistSync::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.AbilityInterfererScreen.receive(packet))).add();
        CHANNEL.messageBuilder(NodePassword.class, 37, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NodePassword::encode).decoder(NodePassword::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(FreqRequest.class, 38, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FreqRequest::encode).decoder(FreqRequest::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(FreqReply.class, 39, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FreqReply::encode).decoder(FreqReply::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.FreqTransmitterHud.receive(packet))).add();
        CHANNEL.messageBuilder(TutorialSnapshot.class, 36, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TutorialSnapshot::encode).decoder(TutorialSnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.TutorialNotifyHud.receive(packet))).add();
        CHANNEL.messageBuilder(MediaSnapshot.class, 33, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MediaSnapshot::encode).decoder(MediaSnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientTerminal.receive(packet))).add();
        CHANNEL.messageBuilder(NodeRename.class, 31, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NodeRename::encode).decoder(NodeRename::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(WirelessConfig.class, 27, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WirelessConfig::encode).decoder(WirelessConfig::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(SkillTrail.class, 26, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillTrail::encode).decoder(SkillTrail::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientTeleporterBursts.receive(packet))).add();
        CHANNEL.messageBuilder(SkillBurst.class, 25, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillBurst::encode).decoder(SkillBurst::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientTeleporterBursts.receive(packet))).add();
        CHANNEL.messageBuilder(SkillVisualMove.class, 24, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillVisualMove::encode).decoder(SkillVisualMove::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> { io.github.pinchan4273.reacademycraft.client.ClientPlasmaCannonEffect.move(packet); io.github.pinchan4273.reacademycraft.client.ClientMineRayEffect.move(packet); io.github.pinchan4273.reacademycraft.client.ClientMagneticEffects.move(packet); })).add();
        // 新しいパケットは末尾に追加する。（リストの途中に登録したパケットが専用サーバーのログインを止めるという見立ては、
        // 後に成り立たないと分かった: ログインのタイムアウトは非決定的に起きる。）
        CHANNEL.messageBuilder(PenetrateDistance.class, 40, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PenetrateDistance::encode).decoder(PenetrateDistance::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(WirelessPageRequest.class, 41, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WirelessPageRequest::encode).decoder(WirelessPageRequest::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(WirelessPageReply.class, 42, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WirelessPageReply::encode).decoder(WirelessPageReply::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.TechScreen.receive(packet))).add();
        CHANNEL.messageBuilder(GroundshockEffect.class, 43, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GroundshockEffect::encode).decoder(GroundshockEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientGroundshock.receive(packet))).add();
        CHANNEL.messageBuilder(BloodRetroEffect.class, 44, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BloodRetroEffect::encode).decoder(BloodRetroEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientBloodRetro.receive(packet))).add();
        CHANNEL.messageBuilder(DeveloperNode.class, 45, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DeveloperNode::encode).decoder(DeveloperNode::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.DeveloperScreen.receive(packet))).add();
        CHANNEL.messageBuilder(RailgunHandEffect.class, 46, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RailgunHandEffect::encode).decoder(RailgunHandEffect::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientRailgunHand.receive(packet))).add();
        CHANNEL.messageBuilder(BodyIntensifyState.class, 47, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BodyIntensifyState::encode).decoder(BodyIntensifyState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientBodyIntensify.receive(packet))).add();
        CHANNEL.messageBuilder(AcademyRulesSnapshot.class, 48, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AcademyRulesSnapshot::encode).decoder(AcademyRulesSnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientAcademyRules.receive(packet))).add();
    }

    public static void lightning(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        var effect = new ThunderClapEffect(level.dimension().location(), position);
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                position.x, position.y, position.z, 128, level.dimension())), effect);
    }

    public static void send(ServerPlayer player, ChargingLoopEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, ChargingArcTarget target) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), target);
    }
    public static void send(ServerPlayer player, ArcGenEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, MdBallEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, MdRayEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, ThunderBoltEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, ThunderClapPresentationEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, IntensifyBurstEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }
    public static void send(ServerPlayer player, IntensifyLoopEffect effect) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), effect);
    }

    public static void send(ServerPlayer player, OreSurveyStart grant) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), grant);
    }

    public static void send(ServerPlayer player, AbilitySnapshot snapshot) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
}
