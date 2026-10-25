# Re:AcademyCraft 1.4.0 公開API 開発者向けの手引き（実験的）

この文書は、Re:AcademyCraft（Forge 1.20.1）1.4.0の公開APIで、第三者のアドオンが能力のカテゴリ・技能・外部の発動を足す方法を説明する。

- **APIは実験的**です。1.4.0の後の版で、名前や形が変わることがある。変わるときは、この文書の「変更の履歴」に書く。
- **配布とライセンス**は「9. 配布とライセンス」を見てください。
- 動く例は`samples/academy-api-example`（本体のリポジトリの中の、別のGradleのビルド）。

## 1. 公開の範囲

| パッケージ | 中身 | 依存 |
| --- | --- | --- |
| `io.github.pinchan4273.reacademycraft.api` | ID・カテゴリと技能の定義・発動の種類・結果・停止の理由・主体の種類・関係・技能の木の場所・APIの版 | Javaだけ（Minecraftの型を使わない） |
| `...api.mc` | 主体・発動の文脈・技能の振る舞い・資源の方針・外部の発動・習得・安全のサービス | Minecraft 1.20.1の型 |
| `...api.forge` | 登録の窓（`RegisterAbilitiesEvent`） | Forge |
| `...api.client` | 見た目だけの演出（`ExternalPresentation`、演出API版1。クライアント専用） | Minecraft 1.20.1のクライアント |

ほかのパッケージ（`ability`・`skill`など）は内部の物で、予告なく変わる。`AcademyApi.VERSION`（今は1）はAPIの版で、通信の版とは別に数える。

## 2. 登録

本体は、共通の準備の段（`FMLCommonSetupEvent`）の中で`RegisterAbilitiesEvent`をすべてのmodのイベントバスへ1回だけ出し、すぐに凍結する。
アドオンは自分のmodのバスで受ける。

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

- 断る登録（例外。黙って上書きしない）: 同じIDの2回目、名前空間`academy`、知らないカテゴリへの技能、凍結の後、ほかの登録者の技能への振る舞い、受動の技能への振る舞い。
- 凍結の時に、前提の技能がすべて登録されていること、受動でない技能すべてに振る舞いがあることを確かめる。満たさなければ読み込みを止める（原因の技能とmodを示す）。
- 本体のカテゴリ（例`academy:electromaster`）へも技能を足せる。本体の技能の後ろに並ぶ。
- `TreePosition`（任意）を持つ技能は、開発機の技能の木に出て、開発機で学べる。持たない技能は木に出ず、`AbilityLearning.grant`でだけ与えられる。

## 3. 技能の振る舞い（自然能力者）

`SkillBehavior`の`start`はキーの押下で呼ばれる。Re:AcademyCraftが先に資格（能力・習得・カテゴリ・設定・能力のオン・過負荷・妨害・
クールダウン・画面・重複）を確かめ、満たさなければ振る舞いを呼ばない。振る舞いは資源の支払い（`context.pay`）と効果を受け持ち、断るときは
何も変えずに`REFUSED_*`を返す。

- 一回きり（INSTANT）: `start`で効果を終え、`PERFORMED`を返す。
- 押し続け（HELD）: `STARTED`を返すとセッションになる。キーを押している間`tick`が呼ばれ、離すと`release`（その後セッションは終わる）。心拍が途切れれば`stop(HEARTBEAT_LOST)`。
- 切り替え（TOGGLE）: `STARTED`でON。同じキーをもう一度押すと`stop(SWITCHED_OFF)`。
- 能力のオフ・プリセットの切り替え・過負荷・カテゴリの変更は`stop(ALL_STOPPED)`、死亡・次元の移動・妨害は`stop(INELIGIBLE)`、ログアウトは`stop(LOGGED_OUT)`。
- 振る舞いの例外は、そのセッションを終えて記録に残す（サーバーは止めない）。

## 4. 外部の主体（装備・機械・AI）

`AbilityActivation.activate(actor, skill, policy)`で発動する。外部の主体が発動できるのは、外部の受動でない技能だけ（本体の41技能は不可）。

- 主体（`AbilityActor`）はアドオンが実装する: 識別子・種類・世界・位置・向き・実体・帰属先のプレイヤー・`valid()`。
- 資源の方針（`ResourcePolicy`）が無ければ`REFUSED_RESOURCE`。費用を取らないなら`ResourcePolicy.FREE`を明示して渡す。
- 押し続け型は、続ける間`AbilityActivation.hold`をtickごとに呼ぶ。`AbilityActivation.stop`で終える（`stop(REQUESTED)`）。
- 主体の位置が能力の妨害の範囲の中なら、新しい発動を断り、動作中のセッションを止める。

## 5. 安全のサービス（`AcademySafety`）

攻撃・地形の変更・空間移動は、ここを通す。**ここを通した処理だけが判定される。** アドオンが`Entity.hurt`や`Level.setBlock`を直接呼べば、
Re:AcademyCraftには止められず、そのような呼び出しを見つける手段も保証しない。

- 帰属先は、今ログインしている本物のプレイヤー（FakePlayerでない・観戦者でない）のときだけ確かめられたとする（`verifiedPlayer`）。
- プレイヤーへの攻撃: 確かめられた帰属先が要り、その人のPvP・チーム・サーバーの設定をすべて当てる。無ければ断る。
- 地形（`breakBlock`・`placeBlock`・`replaceBlock`・`explode`）: 確かめられた帰属先が要り、その人として保護の領域・建築の権限・Forgeの破壊と設置の
  イベント・技能の地形の設定を当てる。機械とAIは、ゲームルールmobGriefingも要る。装備は着けた人の操作として扱い、mobGriefingを見ない。
  無ければ断る。爆発は、ほかのMODのDetonateのリスナーが一覧を変えた後の最終の一覧を、1つずつ判定する。
- 空間移動（`checkTeleport`・`teleport`）: 行き先が読み込まれ、世界の境界と高さの中で、Forgeのテレポートのイベントが許すこと。主体の実体でない相手を
  動かすのは攻撃と同じ判定。移した後に実際の位置を確かめる。

## 6. 習得（`AbilityLearning`）

- `canLearn`: 本体の普通の条件（設定・カテゴリ・レベル・前提・数の上限）。
- `grant`: アドオン自身の条件で与える。Re:AcademyCraftは、外部の技能であること・設定・カテゴリ・書き込めること・数の上限だけを確かめる。

## 7. 設定・保存・通信

- 外部の技能の設定は、名前空間ごとの`config/academy-addon-<名前空間>.toml`（有効・ダメージの倍率・CPと過負荷の消費の速さ・熟練度の伸び・ブロックの破壊）。
  ファイルの変更は、ゲームの次の起動で効く。
- 外部の技能は、プレイヤーの保存データ（schema 2）の技能の一覧に、名前空間付きの名前で入る。アドオンを外しても、カテゴリ・技能・プリセットの枠は休眠のまま残り、
  戻せば元どおり使える。
- ログインの時に、サーバーとクライアントの登録（カテゴリと、技能の名前・発動の種類・カテゴリ）を照らし、違えば違うIDを示して切断する。
  サーバーだけのアドオンは、1.4.0では使えない。

## 8. 演出（クライアント専用、演出API版1）

`io.github.pinchan4273.reacademycraft.api.client`は、アドオンがRe:AcademyCraftの描画（今はMeltdownerの光線）を、**見た目だけ**借りるための入口です。
攻撃・費用・通信は持たず、音も鳴らしません。サーバーで確定した結果を、アドオン自身の通信でクライアントへ送り、そのクライアントで呼びます。

- **クライアント専用**: クライアントの主スレッドからだけ呼びます。書き込みの入口（`beam`・`end`・`clear`）は、ほかのスレッドからは`IllegalStateException`です。
  読み取りの入口（`limit`・`active`・`diagnostics`）はスレッドを検査せず、ほかのスレッドで読んだ値は保証しません。サーバーや共通のコード
  （Dedicatedのサーバーで読み込まれうるクラス）から`ExternalPresentation`を参照しないでください。値の型（`BeamRequest`・`BeamResult`・`BeamStyle`）は、
  どこから使っても構いません。
- **別枠**: 光線は、持ち主（MOD ID）ごとの一覧・上限・効果ID・寿命で管理し、自然能力の光線・球とは互いの枠を消費しません。
- **上限**: 持ち主ごとに同時32本（クライアントごと。2026-10-25のユーザーの決定で採用した既定値）。`config/academy_client.properties`の
  `externalBeamsPerOwner`（1〜256）で変えられます。満杯なら`FULL`を返し、古い光線は押し出しません。
- **長さ**: `MELTDOWNER_BEAM`は96ブロックまで（`maxLength`）。自然能力のMeltdownerの光線は64のままです。
- **寿命**: 自然能力のMeltdownerと同じ50tick。一時停止の間は進まず、ログアウト・次元の移動・資源の読み直しで終わります。術者がいなくなっても、
  受け付けた光線は寿命まで描きます。
- **術者**: `casterEntityId`と`casterId`の両方が合う生きたプレイヤーがいれば、始点を手元へずらします（自分なら一人称・三人称に合わせる）。
  いなければ`from`から描きます。
- **重複**: 同じ持ち主・同じ効果IDの光線を描いている間と、終わってから200tickの間は`DUPLICATE`です（再送への対策）。効果IDは1以上で、持ち主の中で
  重ならないように振ってください（サーバーが振るのがおすすめ）。
- **機能の確かめ**: `AcademyApi.VERSION`は定数でアドオンに埋め込まれます。実行時は`ExternalPresentation.apiVersion()`を呼び、`LinkageError`なら
  この演出APIの無い版として、自分の演出に戻ってください。

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
        return result == BeamResult.ACCEPTED || result == BeamResult.DUPLICATE;   // DUPLICATEは既に描いている
    }
}
```

- 計数（`diagnostics`）: 持ち主ごとの本数・結果ごとの数と、すべての外部の光線の直前のフレームの四角形・描画の確定（endBatch）の回数・CPU時間（ナノ秒）。
  GPUの時間は含みません。
- 計時の注意: 外部の光線は、`RenderLevelStageEvent`の`AFTER_BLOCK_ENTITIES`の段階で、最も低い優先度（`EventPriority.LOWEST`）の購読者が描きます。
  同じ優先度の購読者は登録の順に呼ばれるので、呼び出し側が同じ段階の`LOWEST`で「段階の終わり」を計ると、Re:AcademyCraftより先に動くことがあります
  （アドオンの計測で実際に起きた）。外部の光線の描画のCPU時間は`diagnostics`の`lastFrameNanos`を使い、フレーム全体の時間は
  段階の外（`RenderLevelStageEvent`の後の段階や、フレームの境）で計ってください。

## 9. 配布とライセンス

このプロジェクトの配布の方針です。法的な助言ではなく、第三者の権利が解決済みだと認めるものでもありません。

- **SDKの中身と配布先**: API JAR・API sources・Javadoc・サンプル・この手引き・変更の履歴・対応するソースとビルドの手順・ハッシュの一覧を、
  GitHub Releasesのファイルとして無料で提供します。CurseForgeは本体だけです。Mavenの配布は1.4.0にはありません。
- **ライセンス**: 本体と公開APIは、本体と同じGPLv3（`LICENSE`）で、`NOTICE.md`と、原作のREADMEの追加の文言（`licenses/AcademyCraft-original-README.md`）を
  保持します。APIだけを別のライセンス（MIT・Apacheなど）にはせず、リンクの例外もありません。サンプルのうちこのプロジェクトが新しく書いた部分はGPLv3です。
- **アドオンの作り方**: APIのクラスをアドオンのJARに入れないでください。実行の時にRe:AcademyCraft本体（modsのJAR）へ依存させます（`mods.toml`の
  `dependencies`で`academy`を必須にする）。コンパイルにはAPI JARか本体のJARを使います。
- **アドオンのライセンス**: APIを使うだけで、アドオン全体のライセンスが自動で決まるとは言いません。取り込むコードや素材、本体との関係を踏まえて、
  アドオンを配布する人が自分で義務を確かめてください。
- **対応するソース**: 配布するバイナリに対応するソース（ビルドのスクリプトを含む）は、公開のリポジトリの対応するcommitから無料で取得できます。
  API sourcesのJARは参照用で、それだけが完全な対応ソースではありません。
- **含まないもの**: Minecraft・Forgeの本体と、原作の画像・音・モデルは、SDKに入れません。

## 10. 変更の履歴

- 1.4.0（実験的、API版1）: 最初の公開API。
- 1.4.0（実験的、演出API版1）: `api.client`の外部の演出の光線（`MELTDOWNER_BEAM`、96ブロック、持ち主ごとに同時32本）。
