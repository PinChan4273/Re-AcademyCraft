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

/** このmodのチャンネル（Forge 1.20.1のSimpleChannel）と、各通知の登録。 */
public final class AcademyNetwork {
    /**
     * 通信の版。通知の並びが変わったら上げる。61: プリセットの編集を1操作ずつにし、金属成形・融合のレシピの並びを独自にした。
     * 62: Storm Wingの推進の入力（StormWingInput）と状態の番号を足し、Light Shieldを押し続けからトグルにした（古いクライアントは
     * ハートビートとキーアップでLight Shieldを動かすので、新しいサーバーへ繋がせない）。
     * 63: Light Shieldの盾面の状態（LightShieldState、50番）を足し、Light Shieldの表示をSkillVisualから移した（62のクライアントは
     * 盾の向きを知らないので繋がせない）。
     * 64: 能力設定の画面（1.4.0）の通知を足した（SkillSettingEdit 51番、SkillSettingsSnapshot 52番）。63のクライアントは
     * 設定の値を受け取れないので繋がせない。1.4.0で、開発機の外部の技能の学習（DevelopAddonSkill 53番、
     * DevelopAddonAvailability 54番）と、ログインの時のレジストリの照合（AbilityRegistryCheck 55番、AbilityRegistryVerdict 56番）も足した（1.4.0の公開の前なので、版は64のまま）。
     * 65: v1.5.0。未元物質の技能の学習で開発機の可用性のビットが48を超え、メニューのデータを4スロットにした。技能由来の直接効果の
     * 同期（DirectEffectSync）を足した。64のクライアントは新しい技能・効果を受け取れないので繋がせない。
     * 66: v1.5.1。甲虫のエンティティに同期データ（EMERGE、出現の残りtick）とエンティティのイベント101（EVENT_RECALL、回収の演出）を足し、
     * 甲虫生成の発動をINSTANTからHELD（短押しで生成、2秒の長押しで全個体の回収）にした。1.5.0と1.5.1が混ざると、エンティティの同期データの並びと
     * キーの送り方が食い違うので繋がせない。
     * 67: 1.5.2。ベクトル操作の解析度の同期（VectorAnalysisSync、74番）を足した。66のクライアントは知らない番号を受けるので繋がせない
     * （1.5.2の公開の前に通知を足すときは、版は67のまま）。
     * 68: 1.5.2。パッシブにした飛行の離陸と停止の依頼（PassiveFlightInput、75番）を足し、能力設定の値にパッシブ／アクティブの
     * 切り替え（academy:passive.*）を足した（版を上げる。67のクライアントは切り替えの値と依頼を知らないので繋がせない）。
     * 69: 1.5.2。開発機の初回の取得・磁気コイルでの変更の計画（DevelopFactorPlan、76番）を足した（因子なしのランダムの取得・変更。
     * 開発中のJARと混ざらないよう版を上げる。68のクライアントは計画の通知を知らないので繋がせない）。
     * 70: 1.5.2。開発機の取得・変更の開始を、画面が見た計画を載せた要求（DevelopCategoryStart、77番）に変え、
     * 計画の通知（76番）に「食い違いで断った」のフラグを足した。69のクライアントはメニューのボタンで始めようとし、新しいサーバーはそれを
     * 断るので繋がせない。
     * 71: 1.5.2。身体強化の状態の通知（BodyIntensifyState）のスロットに、パッシブの自動の起動の-1を足し、能力設定の値を
     * 32に増やした（画面へ送る値の数の上限は64）。70のクライアントは-1のスロットを読めず（断って通信が切れる）、パッシブの欄も知らないので繋がせない。
     * 版が違う相手とは、ログインの時点で接続を断る（古い通知を誤って読むところまで進ませない）。
     */
    public static final String VERSION = "73";
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
                        () -> () -> { io.github.pinchan4273.reacademycraft.client.ClientStormWingEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientPlasmaCannonEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientMineRayEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientJetEngineEffect.receive(packet); io.github.pinchan4273.reacademycraft.client.ClientMagneticEffects.receive(packet); })).add();
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
        CHANNEL.messageBuilder(StormWingInput.class, 49, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StormWingInput::encode).decoder(StormWingInput::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(LightShieldState.class, 50, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightShieldState::encode).decoder(LightShieldState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientLightShieldEffect.receive(packet))).add();
        CHANNEL.messageBuilder(SkillSettingEdit.class, 51, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SkillSettingEdit::encode).decoder(SkillSettingEdit::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(SkillSettingsSnapshot.class, 52, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillSettingsSnapshot::encode).decoder(SkillSettingsSnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientSkillSettings.receive(packet))).add();
        // 1.4.0: 開発機の、外部の技能の学習（旧い48ビットの外）。
        CHANNEL.messageBuilder(DevelopAddonSkill.class, 53, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DevelopAddonSkill::encode).decoder(DevelopAddonSkill::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        CHANNEL.messageBuilder(DevelopAddonAvailability.class, 54, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DevelopAddonAvailability::encode).decoder(DevelopAddonAvailability::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDevelopAddons.receive(packet))).add();
        // 1.4.0: ログインの時の、能力のレジストリの照合。
        CHANNEL.messageBuilder(AbilityRegistryCheck.class, 55, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbilityRegistryCheck::encode).decoder(AbilityRegistryCheck::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientRegistryCheck.receive(packet))).add();
        CHANNEL.messageBuilder(AbilityRegistryVerdict.class, 56, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AbilityRegistryVerdict::encode).decoder(AbilityRegistryVerdict::decode)
                .consumerMainThread((packet, context) -> {
                    var sender = context.get().getSender();
                    if (sender != null) io.github.pinchan4273.reacademycraft.ability.RegistryCheck.answer(sender, packet);
                }).add();
        // v1.5.0（版65）: 技能由来の直接効果のうち、対象のプレイヤー自身のクライアントが使う物（跳躍・盲目・吐き気）。
        CHANNEL.messageBuilder(DirectEffectSync.class, 57, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DirectEffectSync::encode).decoder(DirectEffectSync::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDirectEffects.receive(packet))).add();
        CHANNEL.messageBuilder(DarkWingState.class, 58, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DarkWingState::encode).decoder(DarkWingState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDarkWings.receive(packet))).add();
        CHANNEL.messageBuilder(DarkWingAction.class, 61, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DarkWingAction::encode).decoder(DarkWingAction::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDarkWings.receive(packet))).add();
        // v1.5.0 天界発現: 他の未元物質の通知と番号が重ならないよう70番台を使う。
        CHANNEL.messageBuilder(HeavenSettingEdit.class, 70, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HeavenSettingEdit::encode).decoder(HeavenSettingEdit::decode)
                .consumerMainThread((packet, context) -> { packet.handle(context.get().getSender()); }).add();
        CHANNEL.messageBuilder(HeavenSettingsSnapshot.class, 71, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeavenSettingsSnapshot::encode).decoder(HeavenSettingsSnapshot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientHeavenSettings.receive(packet))).add();
        CHANNEL.messageBuilder(HeavenRegionState.class, 72, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeavenRegionState::encode).decoder(HeavenRegionState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientHeavenEffects.receive(packet))).add();
        CHANNEL.messageBuilder(HeavenMark.class, 73, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeavenMark::encode).decoder(HeavenMark::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientHeavenEffects.receive(packet))).add();
        // 1.5.2: パッシブにした飛行の離陸と停止の依頼（術者のクライアントから）。
        CHANNEL.messageBuilder(PassiveFlightInput.class, 75, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PassiveFlightInput::encode).decoder(PassiveFlightInput::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        // 1.5.2: 開発機の初回の取得・磁気コイルでの変更の計画（ランダムか因子の指定か）。
        CHANNEL.messageBuilder(DevelopFactorPlan.class, 76, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DevelopFactorPlan::encode).decoder(DevelopFactorPlan::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDevelopFactor.receive(packet))).add();
        // 1.5.2: 開発機の取得・変更の開始の要求。画面が見た計画を載せ、サーバーが計算し直した計画と比べる。
        CHANNEL.messageBuilder(DevelopCategoryStart.class, 77, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DevelopCategoryStart::encode).decoder(DevelopCategoryStart::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
        // 1.5.2: ベクトル操作の解析度（本人だけ）。
        CHANNEL.messageBuilder(VectorAnalysisSync.class, 74, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VectorAnalysisSync::encode).decoder(VectorAnalysisSync::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientVectorAnalysis.receive(packet))).add();
        CHANNEL.messageBuilder(DarkBeetleShot.class, 62, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DarkBeetleShot::encode).decoder(DarkBeetleShot::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientDarkBeetles.receive(packet))).add();
        CHANNEL.messageBuilder(WhiteWingFlightState.class, 59, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WhiteWingFlightState::encode).decoder(WhiteWingFlightState::decode)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> io.github.pinchan4273.reacademycraft.client.ClientWhiteWingFlight.receive(packet))).add();
        CHANNEL.messageBuilder(WhiteWingFlightInput.class, 60, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WhiteWingFlightInput::encode).decoder(WhiteWingFlightInput::decode)
                .consumerMainThread((packet, context) -> packet.handle(context.get().getSender())).add();
    }

    public static void lightning(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        var effect = new ThunderClapEffect(level.dimension().location(), position);
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                position.x, position.y, position.z, 128, level.dimension())), effect);
    }

    public static void send(ServerPlayer player, SkillSettingsSnapshot snapshot) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
    public static void send(ServerPlayer player, DevelopAddonAvailability availability) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), availability);
    }
    public static void send(ServerPlayer player, DevelopFactorPlan plan) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), plan);
    }
    /** 開発機の画面から、外部の技能の学習を求める（クライアント）。 */
    public static void sendToServer(DevelopAddonSkill request) { CHANNEL.sendToServer(request); }
    /** 開発機のコンソールから、取得・変更の開始を求める（クライアント。画面が見た計画を載せる）。 */
    public static void sendToServer(DevelopCategoryStart request) { CHANNEL.sendToServer(request); }
    public static void send(ServerPlayer player, AbilityRegistryCheck check) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), check);
    }
    /** レジストリの照合の答え（クライアント）。 */
    public static void sendToServer(AbilityRegistryVerdict verdict) { CHANNEL.sendToServer(verdict); }
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
