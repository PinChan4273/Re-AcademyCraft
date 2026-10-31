# Re:AcademyCraft 1.4.1 公開API 開発者ガイド

このガイドでは、Minecraft 1.20.1 / Forge向けのRe:AcademyCraft 1.4.1で、アドオンから能力系統や技能を追加する方法、装備や機械から技能を発動する方法、本体の演出を利用する方法を説明します。

公開APIは実験的な機能です。今後のバージョンでAPIの名称や仕様が変更される場合があります。変更内容は「10. 変更履歴」に記載します。

実装例は、本体リポジトリ内の `samples/academy-api-example` を参照してください。このサンプルは、本体とは独立したGradleプロジェクトです。SDKの入手方法と配布条件は「9. SDKの配布とライセンス」で説明します。

## 1. 公開APIの構成

### パッケージと役割

| パッケージ | 主な内容 | 依存先 |
| --- | --- | --- |
| `io.github.pinchan4273.reacademycraft.api` | ID、能力系統・技能の定義、発動方式、実行結果、停止理由、発動元の種類・関係、スキルツリー上の配置、APIバージョン | Javaのみ。Minecraftの型は使用しません |
| `...api.mc` | 発動元、発動時の情報、技能の実行処理、リソース消費方針、外部発動、技能習得、安全判定 | Minecraft 1.20.1の型 |
| `...api.forge` | 能力・技能の登録イベント（`RegisterAbilitiesEvent`） | Forge |
| `...api.client` | 描画専用の演出API（`ExternalPresentation`、演出APIバージョン2） | Minecraft 1.20.1のクライアント |

表中の `...` は `io.github.pinchan4273.reacademycraft` を表します。

`ability` や `skill` など、上記以外のパッケージは内部実装です。予告なく変更される場合があるため、アドオンの連携には公開APIを使用してください。

`AcademyApi.VERSION` は公開APIのバージョンを表し、1.4.1では `2` です（1.4.0では `1`）。通信プロトコルのバージョンとは別に管理します。演出APIの実行時の対応確認については「8. 演出API」を参照してください。

### このガイドで使う用語

- **能力系統（カテゴリ）**：電撃使いなど、技能をまとめる分類です。APIでは `CategoryDefinition` などの型で扱います。
- **発動元（主体）**：技能を発動する装備・機械などです。APIでは `AbilityActor` で表します。
- **セッション**：押し続け型や切り替え型の技能について、発動開始から終了までの状態を管理する単位です。
- **リソース消費方針**：技能の実行に必要なリソースの支払い方法です。外部発動では `ResourcePolicy` で指定します。

## 2. 能力系統と技能の登録

### 登録できるタイミング

本体は共通初期化処理（`FMLCommonSetupEvent`）中に、各MODのイベントバスへ `RegisterAbilitiesEvent` を1回ずつ発行します。アドオンは、自分のMODのイベントバスにリスナーを登録し、このイベント内で能力系統・技能・実行処理を登録してください。

登録イベントの処理が完了すると、登録内容が確定します。以降の追加・変更はできません。この状態を**登録内容の凍結**と呼びます。

### 登録例

次の例は、`mymod` の名前空間に能力系統 `kinetics` と技能 `pulse` を登録する例です。コメントの `/* 効果 */` の部分に、技能の実際の処理を実装します。

```java
bus.addListener((RegisterAbilitiesEvent event) -> {
    var abilities = event.registrar("mymod");
    abilities.category(new CategoryDefinition(AbilityId.parse("mymod:kinetics"), AbilityId.parse("mymod:kinetics_factor"),
            List.of(1, 1, 1, 1, 1), new Presentation("mymod.ability.kinetics", null, null)));
    abilities.skill(new SkillDefinition(AbilityId.parse("mymod:pulse"), AbilityId.parse("mymod:kinetics"), 1, List.of(),
            ActivationKind.INSTANT, new Presentation("mymod.skill.pulse", "mymod.skill.pulse.desc", null), new TreePosition(40, 50, null)));
    event.behaviors("mymod").behavior(AbilityId.parse("mymod:pulse"), context -> { /* 効果 */ return ActivationOutcome.PERFORMED; });
});
```

### 登録時の制約

次の登録は拒否され、例外が発生します。既存の登録を暗黙に上書きすることはありません。

- 同じIDを重複して登録する。
- 本体用の名前空間 `academy` を使って登録する。
- 未登録の能力系統に技能を登録する。
- 登録内容の確定後に登録する。
- 別の登録者が所有する技能に実行処理を登録する。
- パッシブ技能に実行処理を登録する。

登録内容の確定時には、前提技能がすべて登録されていることと、パッシブ以外のすべての技能に実行処理があることを検証します。条件を満たさない場合は、原因となる技能とMODを示して読み込みを停止します。

### スキルツリーへの表示

本体の能力系統にも技能を追加できます。例えば `academy:electromaster` に追加した技能は、本体の技能の後に並びます。本体の能力系統に技能を追加する場合も、新しい技能のIDにはアドオン自身の名前空間を使用してください。

`TreePosition` を指定した技能は、能力開発機のスキルツリーに表示され、開発機で習得できます。指定しない技能はスキルツリーには表示されず、`AbilityLearning.grant` で付与します。

## 3. プレイヤーが技能キーで発動する場合

### 発動条件と技能の実行処理

技能キーを押すと、`SkillBehavior` の `start` が呼び出されます。ただし、その前に本体が次の発動条件を検証します。

- 能力系統と技能の習得状況
- 技能の設定と、能力が有効になっているか
- 過負荷・能力干渉・クールダウンの状態
- 画面の状態と、同じ技能の重複発動

条件を満たさない場合、アドオンの技能処理は呼び出されません。

技能の実行処理は、`context.pay` によるリソースの支払いと、技能の効果を担当します。処理を拒否する場合は、状態を変更せずに `REFUSED_*` を返してください。

### 発動方式

| 発動方式 | 処理の流れ |
| --- | --- |
| 単発型（`INSTANT`） | `start` 内で効果を実行し、`PERFORMED` を返します |
| 押し続け型（`HELD`） | `STARTED` を返すとセッションが開始します。キーを押している間は `tick` が呼び出され、キーを離すと `release` が呼び出された後にセッションが終了します |
| 切り替え型（`TOGGLE`） | `STARTED` を返すと有効になります。同じキーをもう一度押すと、`stop(SWITCHED_OFF)` で終了します |

押し続け型では、継続を確認する通知（ハートビート）が途切れると、`stop(HEARTBEAT_LOST)` で停止します。

### 状態変化による停止

| 条件 | 呼び出される停止処理 |
| --- | --- |
| 能力の無効化、プリセットの切り替え、過負荷、能力系統の変更 | `stop(ALL_STOPPED)` |
| 死亡、次元移動、能力干渉 | `stop(INELIGIBLE)` |
| ログアウト | `stop(LOGGED_OUT)` |

技能の実行処理で例外が発生した場合は、該当セッションを終了してログに記録します。この例外処理ではサーバー自体を停止しません。

## 4. 装備・機械などから技能を発動する場合

### 外部発動APIの対象

装備・機械・AI制御の発動元などから技能を発動するには、サーバースレッド上で `AbilityActivation.activate(actor, skill, policy)` を呼び出します。

対象は、**アドオンが登録したパッシブ以外の技能**です。本体の既存41技能を、このAPIから発動することはできません。

### 発動元とリソース消費方針

アドオンは `AbilityActor` を実装し、発動元の識別子・種類・ワールド・位置・向き・エンティティ・帰属先プレイヤーと、`valid()` による有効性判定を提供します。

発動時には、リソース消費方針を `ResourcePolicy` で渡してください。未指定の場合は `REFUSED_RESOURCE` となります。消費を不要にする場合は、`ResourcePolicy.FREE` を明示的に指定します。

### 継続と停止

- 押し続け型の技能を継続する間は、`AbilityActivation.hold` を毎tick呼び出します。
- 明示的に終了する場合は、`AbilityActivation.stop` を呼び出します。停止理由は `REQUESTED` です。
- 発動元が能力干渉器の範囲内にある場合は、新しい発動を拒否し、継続中の発動も停止します。

## 5. 攻撃・地形変更・空間移動の安全判定

`AcademySafety` は、攻撃・地形変更・空間移動に対して、本体の安全判定を適用するAPIです。これらの処理を実装する際は、このAPIを経由させてください。

**安全判定が適用されるのは、`AcademySafety` を通した処理だけです。** アドオンが `Entity.hurt` や `Level.setBlock` を直接呼び出す処理は、本体では防止できません。そのような呼び出しを検出できることも保証しません。

### 帰属先プレイヤー

攻撃や地形変更の権限を判定するために、発動元に帰属先プレイヤーを指定します。

`verifiedPlayer` は、現在ログイン中で、FakePlayerでも観戦者でもないプレイヤーだけを、有効な帰属先として扱います。

### プレイヤーへの攻撃

有効な帰属先プレイヤーが必要です。そのプレイヤーに適用されるPvP・チーム・サーバー設定を検証し、許可された場合にのみ攻撃します。帰属先を確認できない場合は拒否します。

### 地形変更と爆発

対象のメソッドは `breakBlock`・`placeBlock`・`replaceBlock`・`explode` です。

有効な帰属先プレイヤーが必要です。そのプレイヤーの操作として、保護領域・建築権限・Forgeのブロック破壊／設置イベント・技能ごとの地形変更設定を適用します。帰属先を確認できない場合は拒否します。

発動元の種類によって、`mobGriefing` の扱いが異なります。

| 発動元 | `mobGriefing` の扱い |
| --- | --- |
| 装備 | 装着者の操作として扱い、追加の条件にはしません |
| 機械・AI制御の発動元 | 通常の権限判定に加え、`mobGriefing` による許可が必要です |

爆発では、他のMODの `Detonate` イベントリスナーによる変更後の最終的な対象一覧に対して、対象ごとに判定します。

### 反射状態の確認とダメージの識別（公開APIバージョン2）

`AcademySafety.reflection(LivingEntity)` は、対象エンティティのベクトル反射の状態（`ReflectionState`）を返します。`REFLECTING` は反射モードが現在有効であることを示し、個々の攻撃を完全に防げることを保証するものではありません。反射処理のダメージ判定では、受けるダメージ量×熟練度に応じた係数（0.6〜1.2）が1以上の場合は攻撃を無効化し、1未満の場合はその係数に応じてダメージを軽減します。

`AcademySafety.skillOf(DamageSource)` は、Re:AcademyCraftの技能によるダメージであれば技能IDを返し、それ以外の場合は `null` を返します。`AcademySafety.isBeam(DamageSource)` は、本体の技能のうち、光線として扱う6技能（`railgun`、`meltdowner`、`ray_barrage`、`electron_bomb`、`scatter_bomb`、`electron_missile`）によるダメージであれば `true` を返します。

いずれも状態を変更しない参照用メソッドです。サーバースレッド上で呼び出してください。`AcademyApi.VERSION` はコンパイル時に値が埋め込まれる定数のため、実行中のバージョンの判定には使用できません。古いバージョンにも対応する場合は、これらのメソッドの呼び出し時に発生する `LinkageError` を捕捉し、対応していない場合の処理を用意してください。

### 空間移動

`checkTeleport`・`teleport` では、移動先が読み込み済みで、ワールド境界と有効な高さの範囲内にあり、Forgeのテレポートイベントで許可されることを確認します。

発動元自身のエンティティ以外を移動させる場合は、攻撃と同じ権限判定を適用します。移動後には、実際の位置も確認します。

## 6. 技能の習得と付与

`AbilityLearning` は、アドオンの技能を習得・付与するためのAPIです。サーバースレッド上で使用します。

| メソッド | 用途と検証範囲 |
| --- | --- |
| `canLearn` | 本体の通常の習得条件を満たすか確認します。技能の有効設定、能力系統、レベル、前提技能と熟練度、登録数などの上限を検証します。開発機の段階による条件は含みません |
| `grant` | アドオン独自の条件で技能を付与します。本体は、アドオンの技能であること、有効設定、能力系統との対応、データへの書き込み可否、登録数などの上限を検証します |

`grant` は、レベルや前提技能の条件を本体側では検証しません。必要な条件は、アドオン側で確認してから呼び出してください。

## 7. 設定・保存データ・通信

### 技能の設定ファイル

アドオンの技能設定は、名前空間ごとに次のファイルへ保存します。

`config/academy-addon-<名前空間>.toml`

設定対象は、技能の有効・無効、ダメージ倍率、CP消費量・過負荷加算量の倍率、熟練度獲得量の倍率、ブロック破壊の可否です。ファイルの変更は、次回のゲームまたはサーバーの起動時に反映されます。

### アドオンを取り外した場合の保存データ

アドオンの技能は、プレイヤーの保存データ（スキーマバージョン2）に、名前空間付きのIDで記録されます。

アドオンを外しても、対応する能力系統・技能・プリセット枠の記録は削除せず、休眠状態で保持します。対応するアドオンを再導入すると、再び使用できます。

### サーバーとクライアントの登録照合

ログイン時に、サーバーとクライアントの能力系統、および技能のID・発動方式・所属系統を照合します。不一致がある場合は、該当するIDを示して接続を拒否します。

1.4.1では、サーバーだけに導入するアドオンはサポートしていません。

## 8. 演出API

### 対応範囲と役割

`io.github.pinchan4273.reacademycraft.api.client` は、アドオンから本体の視覚効果を利用するための公開APIです。1.4.1の演出APIバージョン2では、従来のメルトダウナーの光線（`MELTDOWNER_BEAM`）に加えて、4種類の光線と27種類の演出に対応しています。追加された種類と使い方は、後述の「光線以外の演出（演出APIバージョン2）」を参照してください。

このAPIは描画専用で、ダメージ・命中判定・リソース消費・通信は行いません。効果音も原則として再生しませんが、`LIGHTNING` ではバニラの雷と同じ音が鳴ります。

アドオン側でサーバーの確定結果をクライアントへ送信し、受信したクライアントで演出APIを呼び出してください。通信や、上記の例外以外の効果音の再生は、アドオン側で実装します。

### 呼び出すスレッドとクラスの分離

`ExternalPresentation` はクライアント専用です。クライアントのメインスレッドから呼び出してください。

- 状態を変更する `beam`・`end`・`clear` と、バージョン2で追加された `play`・`startEffect`・`updateEffect`・`endEffect`・`clearEffects` は、別のスレッドから呼び出すと `IllegalStateException` を送出します。
- 状態を参照する `limit`・`active`・`diagnostics` と、バージョン2の `effectLimit`・`activeEffects`・`effectDiagnostics` は、呼び出し元のスレッドを検査しません。ただし、別のスレッドから読み取った値は保証しません。
- 専用サーバーで読み込まれる可能性のある共通コードやサーバー側コードから、`ExternalPresentation` を参照しないでください。
- データ型の `BeamRequest`・`BeamResult`・`BeamStyle` は、クライアント以外のコードからも使用できます。

### 光線の管理と上限

光線は、呼び出し元のMOD IDごとに、表示中の一覧・本数の上限・効果ID・表示期間を管理します。本体の既存能力が使う光線・球とは別枠であり、互いの表示枠を消費しません。

| 項目 | 仕様 |
| --- | --- |
| 同時表示数 | 各クライアントで、MOD IDごとに既定32本 |
| 上限の設定 | `config/academy_client.properties` の `externalBeamsPerOwner`。設定範囲は1〜256 |
| 上限到達時 | `FULL` を返します。表示中の古い光線を削除して、新しい光線に置き換えることはありません |
| 光線の長さ | `MELTDOWNER_BEAM` は最大96ブロック。`maxLength` で取得できます |
| 本体側の制限 | 本体のメルトダウナー光線に設けられた64ブロックの制限は変更しません |
| 表示期間 | 本体のメルトダウナーと同じ50tick |

32本の上限は、プレイヤーごとの上限でも、すべてのアドオンを合計した上限でもありません。

### 表示の終了と術者の扱い

一時停止中は、光線の表示期間を進めません。ログアウト・次元移動・リソース再読み込み時には、光線の表示を終了します。

術者がいなくなっても、受け付け済みの光線は表示期間が終了するまで描画します。

`casterEntityId` と `casterId` の両方が一致する、生存中のプレイヤーが存在する場合は、光線の始点を術者の手元へ補正します。自分の光線では、一人称・三人称に合わせて補正します。該当するプレイヤーがいない場合は、指定された `from` を始点として描画します。

### 効果IDと重複防止

同じMOD IDと効果IDの組み合わせについて、光線の表示中と、表示終了後200tickの間は `DUPLICATE` を返します。これは、通信の再送などによる重複表示を防ぐための仕組みです。

効果IDには1以上の値を使用し、同じMOD内で重複しないように割り当ててください。サーバー側でIDを発行する方法を推奨します。

### 実行時の対応確認

`AcademyApi.VERSION` は定数のため、アドオンのコンパイル時に値が埋め込まれます。実行中のRe:AcademyCraftが演出APIに対応しているかどうかは、`ExternalPresentation.apiVersion()` を呼び出して確認してください。

`LinkageError` が発生した場合は、演出APIに対応していないバージョンとして扱い、アドオン独自の演出へ切り替えます。

次の例は、演出APIの有無を確認してから、サーバーで確定した光線を表示するコードです。

```java
// クライアント専用のクラス（例: アドオンの通信の受け手から、クライアントの主スレッドで呼ぶ）。
final class BeamPresenter {
    private static Boolean available;
    static boolean available() {
        if (available == null) {
            try { available = ExternalPresentation.apiVersion() >= 1 && ExternalPresentation.supports(BeamStyle.MELTDOWNER_BEAM); }
            catch (LinkageError e) { available = false; }   // 演出APIの無い版
        }
        return available;
    }
    /** サーバーで確定した光線を描く。描けなければfalse（呼び出し側は自分の演出に戻る）。 */
    static boolean show(long effectId, ResourceLocation dimension, int casterEntityId, UUID casterId, Vec3 from, Vec3 to) {
        if (!available()) return false;
        var result = ExternalPresentation.beam(new BeamRequest("mymod", effectId, dimension, casterEntityId, casterId, from, to,
                BeamStyle.MELTDOWNER_BEAM));
        return result == BeamResult.ACCEPTED || result == BeamResult.DUPLICATE;   // DUPLICATEは再送として扱い、代替演出を重ねない
    }
}
```

この例では、`DUPLICATE` の場合も独自演出へ切り替えません。同じ効果を二重に表示しないためです。`DUPLICATE` には表示終了後の再送も含まれるので、現在も画面に光線が表示されていることを保証する結果ではありません。

### 光線以外の演出（演出APIバージョン2）

1.4.1から、4系統の能力に属する、以下の27種類の演出を利用できます。本体のすべての演出に対応しているわけではありません。演出の種類に応じて、次の2つのメソッドを使い分けます。

- **単発の演出**：`ExternalPresentation.play(EffectRequest)` で表示します。表示期間が終了すると自動的に消えます。
- **持続する演出**：`startEffect(EffectRequest)` で開始し、`updateEffect(ownerModId, effectId, position, target)` で更新します。`EFFECT_LEASE_TICKS`（100tick）の間に更新されなければ、自動的に終了します。`endEffect` を呼び出して終了することもできます。ただし、風の翼（`STORM_WING`）とプラズマ砲（`PLASMA_CANNON`）は、本体の能力と同じように徐々に薄れて消え、完全に消えるまでは表示枠と効果IDを使用します。更新期限が切れた場合も同様です。

`updateEffect` では、変更しない `position`・`target` に `null` を指定できます。更新後の値が新規受付時と同じ検証条件を満たさない場合は、状態も更新期限も変更せずに `false` を返します。

`play` と `startEffect` の戻り値は、どちらも `BeamResult` です。メソッドと演出の種類が一致しない場合（単発用の `play` に持続する演出を指定した場合など）は、`INVALID` を返します。

同時表示数の上限は、各クライアントで、呼び出し元のMOD IDごとに管理します。光線の表示枠とは別枠です。設定は `config/academy_client.properties` の `externalEffectsPerOwner` で、設定範囲は1〜256、既定値は32です。上限に達すると `FULL` を返し、表示中の古い演出を新しい演出で置き換えることはありません。描画負荷の高いプラズマ砲（`PLASMA_CANNON`）には、さらにMOD IDごとに同時2件までの上限があります。

HUD・照準・画面の色・手の構え・視線・歩行など、プレイヤーの画面表示や操作を直接変更する機能は、このAPIの対象外です。コインと飛行中の磁力ブロックも、サーバー側のエンティティを必要とするため対象外です。

表の「エンティティ」は、`entityId` と `entityUuid` で指定する、演出の追従先です。`position` と `target` はワールド座標で指定します。新規受付時には、エンティティを指定する場合も、有効な `position` が必要です。表で上限を示した `scale` には、いずれも0より大きい値を指定してください。

| 系統 | 種類（`EffectStyle`） | 表示方式 | 使用する項目と描画内容 |
| --- | --- | --- | --- |
| 電撃使い | `ARC_WEAK`、`ARC_STRONG` | 単発 | `position`→`target`（20ブロックまで）。エンティティを指定すると、本体の能力と同じ手元の描画位置補正を適用します |
| 電撃使い | `ARC_AREA` | 単発 | `position`→`target`（20ブロックまで） |
| 電撃使い | `ARC_BURST` | 単発 | エンティティ、または `position` |
| 電撃使い | `LIGHTNING` | 単発 | `position`。バニラの雷と同じ音が鳴ります |
| 電撃使い | `RAILGUN_HAND` | 単発 | エンティティ（プレイヤー）。本人の一人称視点で手元に表示します |
| 電撃使い | `ARC_CONTINUOUS` | 持続 | `position`→`target`（20ブロックまで）。エンティティを指定すると、手元の描画位置補正を適用します |
| 電撃使い | `ARC_SURROUND`（細い）、`ARC_SURROUND_NORMAL`、`ARC_SURROUND_BOLD`（太い） | 持続 | エンティティ、または `position`。エンティティを指定しない場合、`scale` は演出範囲を囲む箱の大きさを指定します（最大32） |
| 電撃使い | `RIPPLE` | 持続 | `position`。水平の輪です |
| 原子崩し | `ORB` | 持続 | `position`。エンティティを指定すると、受付時の相対位置を保って追従します。更新時に `position` を渡すと、その座標を基準に相対位置を設定し直します。`null` の場合は相対位置を維持します |
| 原子崩し | `LIGHT_SHIELD` | 持続 | エンティティ、`target`（エンティティの足元を基準に盾の向きを決める座標）、`scale`（半径、最大8） |
| 原子崩し | `JET_SHIELD` | 持続 | エンティティ。その視線の向きに合わせます |
| 原子崩し | `MINE_RAY_BASIC`、`MINE_RAY_EXPERT`、`MINE_RAY_LUCK` | 持続 | エンティティ。マイン・レイの光線を、その視線に沿って15ブロック描画します |
| 空間移動 | `TELEPORT_TRAIL`、`SHIFT_TRAIL` | 単発 | `position`→`target`（96ブロックまで） |
| 空間移動 | `CRITICAL_FORMULA` | 単発 | エンティティ、または `position` |
| ベクトル操作 | `WAVE_RINGS` | 単発 | `position`→`target`（演出の向き）、`scale`（最大8） |
| ベクトル操作 | `BLOOD_SPLASH`、`BLOOD_SPLASH_LARGE`（大きい） | 単発 | エンティティ、または `position` |
| ベクトル操作 | `BLOOD_SPRAY` | 単発 | `position`→`target`。その直線が当たったブロックの面に血痕を表示し、1200tick保持します。その間は表示枠を使用します |
| ベクトル操作 | `GROUND_DEBRIS` | 単発 | `position`、`scale`（半径、最大6） |
| ベクトル操作 | `STORM_WING` | 持続 | エンティティ。風の翼の演出です |
| ベクトル操作 | `PLASMA_CANNON` | 持続 | `position`、`scale`（球の大きさの倍率。1で本体の能力と同じ大きさ、最大4。竜巻の大きさは変わりません）。`target` を指定して更新すると竜巻が徐々に薄れます |

光線の種類（`BeamStyle`）には、`MELTDOWNER_RAY_SMALL`、`RAILGUN`、`BARRAGE_AIM`、`BARRAGE_SCATTER` を追加しました。従来と同じ `beam` メソッドで表示します。

追従先には、エンティティID（`entityId`）とUUID（`entityUuid`）の両方が一致するエンティティだけを使用します。エンティティが必須の種類で追従先が見つからなければ、`INVALID` を返します。受付後に追従先がいなくなった場合や、同じエンティティIDが別のエンティティに再利用された場合は、演出を終了します。

追加された演出を使用する前に、`ExternalPresentation.apiVersion() >= 2` を確認してください。1.4.0には `EffectStyle` がないため、対応していない環境で参照すると `LinkageError` が発生します。

1.4.0向けアドオンの互換性と、1.4.0と1.4.1の混在接続は未検証です。上記のAPI対応確認だけで動作互換性が保証されるわけではありません。利用するアドオンの対応状況を確認し、マルチプレイではサーバーと全クライアントを1.4.1にそろえることを推奨します。

### 効果音の識別子

演出APIは、`LIGHTNING` を除き、効果音を再生しません。本体の効果音を利用する場合は、`api.mc.AcademySoundIds` の識別子を使い、アドオン側で再生処理を実装してください。音を聞かせるプレイヤーの範囲も、アドオン側で決めます。

```java
var sound = ForgeRegistries.SOUND_EVENTS.getValue(AcademySoundIds.EM_RAILGUN);
level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 1.0f, 1.0f);
```

### 描画負荷の計測

`diagnostics` では、MODごとの表示本数と受付結果別の件数、および外部光線全体の描画情報を取得できます。

- 直前のフレームで描画した四角形（quad）の数
- 描画バッチを確定した回数（`endBatch` の呼び出し回数）
- 外部光線の描画処理にかかったCPU時間（ナノ秒）

**GPUの処理時間は含みません。**

外部光線は、`RenderLevelStageEvent` の `AFTER_BLOCK_ENTITIES` 段階で、`EventPriority.LOWEST` のイベントリスナーから描画します。同じ優先度のリスナーは登録順に呼び出されるため、アドオン側も同じ段階・同じ優先度で計測すると、本体の描画より先に計測を終えてしまう場合があります。

外部光線の描画にかかったCPU時間は、`diagnostics` の `lastFrameNanos` を使用してください。フレーム全体を計測する場合は、後続の描画段階やフレーム境界など、外部光線の描画処理を含む位置で測定します。

## 9. SDKの配布とライセンス

以下は、本プロジェクトの配布方針です。法的助言や、第三者の権利関係がすべて解決済みであることの表明ではありません。

### SDKの内容と配布先

SDKはGitHub Releasesの添付ファイルとして無料で提供します。内容は次のとおりです。

- API JAR
- APIソース
- Javadoc
- サンプルアドオン
- この開発者ガイドと変更履歴
- 対応ソースとビルド手順
- ハッシュ一覧

CurseForgeで配布するのは本体のみです。1.4.1では、Maven経由の配布は行いません。

### 本体とSDKのライセンス

本体と公開APIにはGPLv3を適用し、`LICENSE`・`NOTICE.md`・原作READMEの追加文言（`licenses/AcademyCraft-original-README.md`）を保持します。

APIだけをMITやApacheなどの別ライセンスには変更しておらず、リンク例外も設けていません。サンプルのうち、本プロジェクトが新たに作成した部分にはGPLv3を適用します。

### アドオンへの組み込み

APIのクラスをアドオンのJARへ同梱しないでください。実行時にはRe:AcademyCraft本体を必要とする構成にし、`mods.toml` の `dependencies` で `academy` を必須依存として指定します。

コンパイルには、API JARまたは本体のJARを使用してください。

### アドオンのライセンス

APIの利用だけで、アドオン全体に適用されるライセンスが自動的に決まるとは限りません。取り込むコードや素材、本体との関係を踏まえ、配布者が適用される義務を確認してください。

### 対応ソースとSDKに含まれないもの

配布バイナリに対応するソースは、ビルドスクリプトを含め、公開リポジトリの対応コミットから無料で取得できます。APIソースのJARは参照用であり、それだけで完全な対応ソースを構成するものではありません。

Minecraft・Forge本体、および原作の画像・音声・モデルは、SDKに含めません。

## 10. 変更履歴

- **1.4.1／公開APIバージョン2（実験的）**：`AcademySafety.reflection`・`skillOf`・`isBeam`、`ReflectionState`、`AcademySoundIds` を追加。
- **1.4.1／演出APIバージョン2（実験的）**：`EffectStyle`・`EffectRequest`、`play`・`startEffect`・`updateEffect`・`endEffect`・`clearEffects` を追加。光線に `MELTDOWNER_RAY_SMALL`・`RAILGUN`・`BARRAGE_AIM`・`BARRAGE_SCATTER` を追加。通信プロトコルは変えていません。
- **1.4.0／公開APIバージョン1（実験的）**：最初の公開APIを提供。
- **1.4.0／演出APIバージョン1（実験的）**：`api.client` にアドオン向け光線演出を追加。`MELTDOWNER_BEAM` は最大96ブロックに対応し、同時表示数は各クライアントでMOD IDごとに既定32本。
