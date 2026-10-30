# Re:AcademyCraft — Minecraft 1.21.1 / NeoForge

**Re:AcademyCraft**は、Lambda InnovationのMOD「AcademyCraft」（Minecraft 1.12.2）の、**Minecraft 1.21.1 / NeoForge**向けの**非公式**移植版です。
Minecraft 1.20.1 / Forge版の公開版1.3.0と同じ機能を、NeoForge 1.21.1へ移したものです。

> **Minecraftの公式製品ではありません。Mojang・Microsoftの承認を受けておらず、提携もしていません。**
> 原作者、Lambda Innovation、MohistMC、関連作品の権利者から公認・承認・支援を受けたものでも、提携するものでもありません。

| | |
| --- | --- |
| バージョン | 1.3.0 |
| このブランチ | `1.21.1-neoforge`（Minecraft 1.21.1 / NeoForge版のsource） |
| tag | `1.21.1-neoforge-v1.3.0` |
| Forge 1.20.1版 | 別のブランチ`1.20.1`と、別のJAR。このブランチには含みません |

## 動作要件

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.259以上（`[21.1.259,)`） |
| Java | 21 |
| 必要な他のMOD | なし |
| 導入先 | **クライアントとサーバーの両方**（mod ID `academy`） |

## 導入

1. NeoForge 1.21.1（21.1.259以上）を導入します。
2. `re-academycraft-neoforge-1.21.1-1.3.0.jar`を、クライアントの`mods`フォルダーへ入れます。マルチプレイではサーバーにも入れます。
   - 公式の導入用JARは、CurseForge（<https://www.curseforge.com/minecraft/mc-mods/re-academycraft>、Project ID `1735500`）だけで配布します。
   - 導入用JARのSHA-256: `0160b8db2a138539e605ce6c294a4c05970c320343f30c376f4ba0a416413b62`
3. 更新の前に、ワールドと設定をバックアップしてください。
   - クライアントとサーバーは同じバージョンを使用してください。互換性のない通信プロトコルは接続時に拒否されます。
   - **Forge 1.20.1版で遊んだワールドを、このNeoForge版で開いたときの移行は、確認していません。** 試すときは、必ず写しで行ってください。
4. Forge 1.20.1版のJAR（`re-academycraft-forge-1.20.1-…`）や、ほかのAcademyCraftと同時に入れないでください。mod IDが同じ`academy`です。

## 含まれるもの

- 能力の4系統:
  - 電撃使い（Electromaster）
  - 原子崩し（Meltdowner）
  - テレポート（Teleporter）
  - ベクトル操作（Vector Manipulation）
- 能力の学習と昇級: 能力開発機、プリセットと技能の操作、CP・過負荷のHUD
- 能力ごとのプレイヤーの設定（ゲーム内のコマンド。OP権限は不要。下の「能力の設定コマンド」）
- 鉱石と素材、機械、無線エネルギー、データ端末とアプリ、ゲーム内の教程（御坂クラウド）

## サーバーと設定

- 能力の判定・CPと過負荷・技能の結果・機械の動作は、**サーバーが決めます**。クライアントは操作を送り、結果を表示します。

| ファイル | 内容 |
| --- | --- |
| `config/academy-common.toml` | ゲームの規則（ダメージの倍率、CP・過負荷の回復、技能の有効・無効、地形の破壊など）。 |
| `config/academy_client.properties`、`config/academy_hud.properties`、`config/academy_media_player.properties` | クライアントだけの設定、HUDの配置、メディアプレイヤーの音量など。 |

- `academy-common.toml`は**ゲームのインスタンス全体で1つ**です。シングルプレイでは、そのインスタンスのすべてのワールドに効きます。
- 専用サーバーでは、**サーバーの**`academy-common.toml`の値が使われます。サーバーへ参加したクライアントの`academy-common.toml`は、そのサーバーでは何も決めません（表示に必要な規則はサーバーから送られます）。
- ワールドごとの古い`serverconfig/academy-server.toml`は読みません。残っていると、違っている項目と移し方をサーバーの起動時にログへ出します。

## 能力の設定コマンド

Forge 1.20.1版1.3.0と同じです。設定はプレイヤーのデータに保存され、死亡・再接続・次元の移動の後も残ります。専用の設定画面はまだありません。

- **エレクトロン・ミサイル**
  - オーブは8 tickごとに生成し、8 tickごとに発射します（オーブは最大5個）。サーバーの`academy-common.toml`の`[ability.electron_missile]`の`orb_spawn_interval`（既定8、1〜40）で生成の間隔を変えられます。
  - 照準: `/academy missile mode auto|semi`。AUTO（既定）は許可された対象のうち最も近い相手、SEMI-AUTOは視線から約20°以内の相手を優先します。
  - 攻撃対象: `/academy missile target hostile|neutral|friendly|player on|off`。**既定は敵対Mobだけです。** プレイヤーは`player`がONで、かつサーバーのPvPとチームの設定が許す場合だけ撃ちます。
  - 値を付けずに実行すると、今の設定を表示します。
- **ライト・シールド**
  - `/academy shield mode auto|semi`。AUTO（既定）は背後や頭上を含む周囲の脅威へ盾が自動で向きます。SEMI-AUTOは視線から約30°以内の脅威を捕捉し、約40°を超えるまで追います。従来の`manual`はSEMI-AUTOとして扱われます。
  - 盾に接触した生き物を押し戻し、自分に当たる直前で盾の円を通る飛来物を迎撃します。他のプレイヤーにも同じ盾の向きが見えます。
- **ジェット・エンジン**: 推進中、前方（視線の向きの菱形、最も広い方向で約56°）からの近接攻撃と飛来物のダメージを、熟練度に応じて10〜20%軽減します。ライト・シールドと同時に使うと、先に盾が吸収し、残りを軽減します。

## 既存のワールド

- 鉱石と虚相位液体の湖は、**新しく生成されるチャンクにだけ**置かれます。
- MODを入れる前に生成したチャンクには出ません。新しいチャンクを探索してください。

## Forge 1.20.1版（1.3.0）との違い

- 機能・数値・既定値は、Forge 1.20.1版の公開版1.3.0と同じです。
- ライト・シールドの押し戻しで除外するボスは、NeoForgeの共通のタグ`c:bosses`で判定します（Forge版は`forge:bosses`）。バニラのボス（エンダードラゴン・ウィザー）はどちらにも入っています。
- **NeoForgeで避けられない違い**: 保存の形式（NeoForgeのattachments）、通信（Forge版のサーバーとは接続できません）、アイテムのデータ（Data Components）。Forge 1.20.1版のワールドを開いたときの移行は保証しません（上の「導入」）。
- **エネルギーユニットの作成**: 作業台では、出力1個と、残りを材料の欄へ返す形になります（Minecraft 1.21の制約による）。
  - 受け取れる総数、材料の数の釣り合い、バニラのクラフターでの動作は、Forge版と同じです。
  - ほかのMODの自動作成では、残りが受け取られず1個しか出ない場合があります。**ほかのMODの自動作成は保証しません。**
- Forge 1.20.1版の1.4.0で入った、アドオン用の公開APIとSDKは含みません。

## 言語

英語・日本語・簡体字中国語（ゲーム内の教程を含む）。

## 音楽

- 原作に入っていた市販の3曲（「only my railgun」「LEVEL5-judgelight-」「sister's noise」）とそのCDジャケットは、**含みません**。自動でダウンロードすることもありません。
- メディアプレイヤーは、自分が使う権利を持つ曲で使えます。方法は、JAR内の`assets/academy/media/how_to_add_media.txt`を見てください。

## 既知の問題・注意

- **Iris（シェーダー）とのキーの重なり**: 技能の3つ目の欄（既定R）と、Irisの「シェーダーの読み直し（Reload Shaders）」（既定R）が同じキーです。Rで技能を使うたびにシェーダーが読み直されて画面が一瞬止まり、押し続ける技能（プラズマ砲など）が中断されることがあります。どちらかのキーを変えてください。
  - 手順: 「設定」→「操作設定」→「キー割り当て」で、赤く表示される重なったキー（「技能の3つ目の欄」または「Reload Shaders」）を、使っていない別のキーへ変えます。シェーダーの読み直しをあまり使わない場合は、Irisの方を変えるか「割り当て無し」にするのが簡単です。
  - Re:AcademyCraftもIrisも、相手のキーを自動では変えません。
  - 確かめた組み合わせ: Iris 1.8.12・Sodium 0.6.13・BSL v10.1.8。ほかのシェーダーパックや描画を変えるMODとの組み合わせは確認していません。
- **既存のチャンク**: 鉱石と虚相位液体の湖は、新しく生成されるチャンクにだけ出ます（上の「既存のワールド」）。
- **通信の版**: このバージョンの通信の版は63です。版62（NeoForge版1.2.0の開発版）や、Forge 1.20.1版とは接続できません。サーバーと全員のクライアントを同じ1.3.0にそろえてください。
- **ほかのMODのボス**: ライト・シールドの押し戻しで除外するボスは、共通のタグ`c:bosses`で判定します。ほかのMODのボスが除外されるかは、そのMODのタグ付けによって変わります。すべてのMODのボスとの互換は保証しません。
- **調整の可能性**: ミサイルの20°、シールドの30°・40°、ジェット・エンジンの10〜20%、敵対Mobだけを撃つ既定は、Forge版と同じく、今後の試遊で調整する可能性があります。
- **原作との見た目の違い**: Electron Missileの光線の始点、Thunder Boltの範囲の弧など、一部に原作と違う見た目が残っています。
- **素材**: 原作由来の素材の一部は、作者・再配布の条件が確認できていません（下の「クレジット・ライセンス・権利」）。
- **翻訳**: 英語・簡体字中国語の文章は、母語話者による校正をしていません。気づいた点はIssueで教えてください。

## 不具合の報告・要望

- GitHub Issues: <https://github.com/PinChan4273/Re-AcademyCraft/issues>
- 次を添えてください:
  - Re:AcademyCraftのバージョン、NeoForgeのバージョン、一緒に入れているMOD
  - 再現の手順
  - 関係するログ（`logs/latest.log`、あればクラッシュレポート）
- **公開のIssueに、個人情報・秘密情報・権利を証明する資料を書かないでください。**

## 権利の申立て

- 窓口: **pinmodcontact@gmail.com**
- 手順と必要な情報は`RIGHTS_CLAIMS.md`を見てください。

## ソースからのビルド

- 必要なもの: JDK 21。初回はGradle・NeoForge・Mojangのライブラリを取得するためにインターネット接続が必要です。
- Gradle wrapper（Gradle 9.2.1）を同梱しています。

```sh
./gradlew clean build
```

- 導入用のJARは`build/libs/re-academycraft-neoforge-1.21.1-1.3.0.jar`です。
- 同じ場所の`-sources.jar`は導入用ではありません。
- sourceと文書はUTF-8です。Javaのコンパイルとリソースの処理にUTF-8を指定しているので、WindowsでもLinuxでも、OSの既定の文字コードに依存せずにビルドできます。
- JARはファイルの時刻と順序を固定して作ります。1.3.0では、Windows・JDK 21.0.4でこのブランチの内容から作ったJARが、配布するJARと同じバイトになることを確かめました。ほかのOSやJDKの版では確かめていません。
- CurseForgeで配布する1.3.0のJARは、このブランチの1.3.0のtagと同じファイル（改行はLF）からビルドしたものです。
- レシピ・戦利品・モデルなどのデータは、リポジトリのJSONを直接編集して管理しています（Forge 1.20.1版の生成スクリプトは、1.21.1のデータの形に合わないため含みません）。

## クレジット・ライセンス・権利

- 原作: [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)。sourceの履歴に、WeAthFolD、KSkun、Paindarほかの名前があります。
- 比較・参考: [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)。MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。
- クレジットの全体は`CREDITS.md`、ライセンスと帰属・改変の表示は`NOTICE.md`、素材ごとの出所と状態は`ASSET_PROVENANCE.tsv`、権利の申立ては`RIGHTS_CLAIMS.md`にあります。
- **コード**: 原作のREADMEは「All versions of AcademyCraft are licensed under GPLv3」と述べ、あわせて販売の禁止とLambda Innovationの権利の留保を述べる追加の文言を置いています。GPLv3の全文は`LICENSE`に、原作のREADMEの原文は`licenses/`にあります。Re:AcademyCraftは、GPLv3と追加の文言の関係を判断していません。
- NeoForgeのMDKのテンプレートに由来する部分は、MIT License（`TEMPLATE_LICENSE.txt`）です。
- **素材**: 画像・モデル・シェーダー・音の多くは原作由来です。そのうち一部は、合理的な調査でも作者・権利者・再配布の条件を特定できていません。`ASSET_PROVENANCE.tsv`で`UNRESOLVED`としており、使用許諾や権利の処理が済んでいるという意味ではありません。
- Re:AcademyCraftは無料で配布します。有料のダウンロード、有料の能力・アイテムはありません。
