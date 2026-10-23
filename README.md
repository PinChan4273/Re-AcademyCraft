# Re:AcademyCraft — Minecraft 1.20.1 / Forge

**Re:AcademyCraft**は、Lambda InnovationのMOD「AcademyCraft」（Minecraft 1.12.2）の、**Minecraft 1.20.1 / Forge**向けの**非公式**移植版です。

> **Minecraftの公式製品ではありません。Mojang・Microsoftの承認を受けておらず、提携もしていません。**
> 原作者、Lambda Innovation、MohistMC、関連作品の権利者から公認・承認・支援を受けたものでも、提携するものでもありません。

| | |
| --- | --- |
| バージョン | 1.3.0 |
| このブランチ | `1.20.1`（Minecraft 1.20.1 / Forge版のsource） |
| tag | `1.20.1-v1.3.0` |

## 動作要件

| | |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | `[47.4.10,48)`（47.4.10以上、48未満） |
| Java | 17 |
| 必要な他のMOD | なし |
| 導入先 | **クライアントとサーバーの両方**（mod ID `academy`） |

## 導入

1. Forge 1.20.1（47.4.10以上）を導入します。
2. `re-academycraft-forge-1.20.1-1.3.0.jar`を、クライアントの`mods`フォルダーへ入れます。マルチプレイではサーバーにも入れます。
   - 公式の導入用JARは、CurseForge（<https://www.curseforge.com/minecraft/mc-mods/re-academycraft>、Project ID `1735500`）だけで配布します。GitHubのreleaseは更新情報とsourceの参照用で、JARは添付しません。
   - 導入用JARのSHA-256: `55f274698e9662d2cbbb6df2be0c15f7087200caa06fcf67d8dbb300bfca1f0c`
3. 更新の前に、ワールドと設定をバックアップしてください。
   - 1.3.0では、プレイヤーごとの技能の設定（エレクトロン・ミサイルの照準と攻撃対象、ライト・シールドのモード）をプレイヤーのデータに保存します。設定ファイルには、`[ability.electron_missile]`の`orb_spawn_interval`（既定8）を足しました。
   - 1.0.0のセーブを初めて読み込んだときは、1.1.0と同じく、最大CPを新しい方式へ1回だけ移します（1.0.0標準式と一致しないカスタム最大CPは保持します）。設定ファイルは、新しい項目を足す前に`academy-common.toml.before-capacity-v2.bak`へ写します。
   - 1.3.0で開いたワールドを、1.2.0以前へ戻すことは保証しません。戻すときは、更新の前に取ったバックアップを使ってください。
   - マルチプレイでは、サーバーと参加する全員のクライアントを1.3.0に更新してください。
   - 以前の試験版で遊んだワールドは、開いたときに能力データなどを新しい保存の形へ移します。移した後のワールドは、以前の試験版では正しく読めません。
   - クライアントとサーバーは同じバージョンを使用してください。互換性のない通信プロトコルは接続時に拒否されます。
   - 1.3.0では通信プロトコルを変えたので、1.2.0以前のサーバー・クライアントとは接続できません。
4. 他のAcademyCraftや、以前の試験版のJAR（`academycraft-forge-1.20.1-…`）と同時に入れないでください。mod IDが同じ`academy`です。

## 含まれるもの

- 能力の4系統:
  - 電撃使い（Electromaster）
  - 原子崩し（Meltdowner）
  - テレポート（Teleporter）
  - ベクトル操作（Vector Manipulation）
- 能力の学習と昇級: 能力開発機、プリセットと技能の操作、CP・過負荷のHUD
- 鉱石と素材、機械、無線エネルギー、データ端末とアプリ、ゲーム内の教程（ミサカクラウド）

## サーバーと設定

- 能力の判定・CPと過負荷・技能の結果・機械の動作は、**サーバーが決めます**。クライアントは操作を送り、結果を表示します。

| ファイル | 内容 |
| --- | --- |
| `config/academy-common.toml` | ゲームの規則（ダメージの倍率、CP・過負荷の回復、技能の有効・無効、地形の破壊など）。 |
| `config/academy_client.properties`、`config/academy_hud.properties`、`config/academy_media_player.properties` | クライアントだけの設定、HUDの配置、メディアプレイヤーの音量など。 |

- `academy-common.toml`は**ゲームのインスタンス全体で1つ**です。シングルプレイでは、そのインスタンスのすべてのワールドに効きます。
- 専用サーバーでは、**サーバーの**`academy-common.toml`の値が使われます。サーバーへ参加したクライアントの`academy-common.toml`は、そのサーバーでは何も決めません（表示に必要な規則はサーバーから送られます）。
- 以前の試験版で作ったワールドには、ワールドごとの`serverconfig/academy-server.toml`が残っている場合があります。
  - このファイルはもう読まれず、値は**自動では移りません**。必要な値は`config/academy-common.toml`へ手で写してください。
  - サーバーの起動時に、古いファイルと違っている項目と移し方をログへ出します。古いファイルは消しません。

## 既存のワールド

- 鉱石と虚相位液体の湖は、**新しく生成されるチャンクにだけ**置かれます。
- MODを入れる前や、以前のバージョンで生成したチャンクには出ません。新しいチャンクを探索してください。

## 言語

英語・日本語・簡体字中国語（ゲーム内の教程を含む）。

## 音楽

- 原作に入っていた市販の3曲（「only my railgun」「LEVEL5-judgelight-」「sister's noise」）とそのCDジャケットは、**含みません**。自動でダウンロードすることもありません。
- メディアプレイヤーは、自分が使う権利を持つ曲で使えます。方法は、JAR内の`assets/academy/media/how_to_add_media.txt`を見てください。

## 既知の問題・注意

- **既存のチャンク**: 鉱石と虚相位液体の湖は、新しく生成されるチャンクにだけ出ます（上の「既存のワールド」）。
- **ほかのMODとの組み合わせ**: Oculus・Embeddium・BSLの組み合わせで主な表示を確かめました。ほかのシェーダーパックや描画を変えるMODとの組み合わせは確認していません。BSLでは、一部の縁取りなどがシェーダーなしのときと少し違って見えます。表示がおかしいときは、それらを外して試してください。
- **エレクトロン・ミサイルの攻撃対象の既定**: 1.3.0から、既定では敵対Mob（と自分を狙っているMob）だけを撃ちます。1.2.0は、種類で選ばずに、射程内で最も近い生き物を撃っていました（プレイヤーは、PvPなどの設定が許す場合だけ）。`/academy missile target neutral on`・`/academy missile target friendly on`で、1.2.0に近い撃ち方に戻せます。防具立ては、どの設定でも撃ちません。
- **技能の設定の画面**: エレクトロン・ミサイルの照準（`/academy missile mode auto|semi`）と攻撃対象（`/academy missile target`）、ライト・シールドのモード（`/academy shield mode auto|semi`）は、チャットのコマンドで変えます。設定の画面はまだありません。
- **ウィンドウ・画面から戻った直後のキー**: ウィンドウから離れて戻った直後や、画面・データ端末を閉じた直後は、押したままだった技能のキー・移動キー（風の翼の推進、フラッシングの方向）を、一度離して押し直すまで新しい操作として扱いません。戻った後の最初の1回の押下が効かないことがあります。そのときは、もう一度押してください。
- **水中の光線の表示**: 一部の能力エフェクト（メルトダウナー、エレクトロン・ボム、スキャッター・ボム、エレクトロン・ミサイル、超電磁砲の光線と弧、レイ・バラージュなど）の描画順序を変えました。水面の下の光線が消える問題の改善を目的とした変更で、期待どおりの見え方になるかは実画面で確認していません。光線の奥にある半透明のブロック（水面・色付きガラス・氷など）や、粒子・雲・天気が、光線の上に重なって見えることがあります。シェーダーや描画を変えるMODとの組み合わせでの見え方は、確認していません。
- **原作との見た目の違い**: Electron Missileの光線の始点、Thunder Boltの範囲の弧など、一部に原作と違う見た目が残っています。
- **素材**: 原作由来の素材の一部は、作者・再配布の条件が確認できていません（下の「クレジット・ライセンス・権利」）。
- **翻訳**: 英語・簡体字中国語の文章は、母語話者による校正をしていません。気づいた点はIssueで教えてください。

## 不具合の報告・要望

- GitHub Issues: <https://github.com/PinChan4273/Re-AcademyCraft/issues>
- 次を添えてください:
  - Re:AcademyCraftのバージョン、Forgeのバージョン、一緒に入れているMOD
  - 再現の手順
  - 関係するログ（`logs/latest.log`、あればクラッシュレポート）
- **公開のIssueに、個人情報・秘密情報・権利を証明する資料を書かないでください。**

## 権利の申立て

- 窓口: **pinmodcontact@gmail.com**
- 手順と必要な情報は`RIGHTS_CLAIMS.md`を見てください。

## ソースからのビルド

- 必要なもの: JDK 17。初回はGradle・Minecraft Forge・Mojangのライブラリを取得するためにインターネット接続が必要です。
- Gradle wrapper（Gradle 8.8）を同梱しています。

```sh
./gradlew clean build
```

- 導入用のJARは`build/libs/re-academycraft-forge-1.20.1-1.3.0.jar`です（`reobfJar`で再難読化したもの）。
- 同じ場所の`-sources.jar`は導入用ではありません。
- sourceと文書はUTF-8です。Javaのコンパイルとリソースの処理にUTF-8を指定しているので、WindowsでもLinuxでも、OSの既定の文字コードに依存せずにビルドできます。
- JARはファイルの時刻と順序を固定して作るので、同じsourceからは同じJARができます。
- CurseForgeで配布する1.3.0のJARは、テキストのファイルの改行がCRLFの作業ツリーから作ったものです。改行がLFの環境で同じsourceからビルドしたJARとは、SHA-256が一致しないことがあります。
- 一部のデータ（レシピ・戦利品・ワールド生成・素材のモデルなど）は`tools/generate_*.py`（Python 3）で生成し、生成結果をリポジトリに入れています。生成スクリプトはリポジトリのルートで実行します。

## クレジット・ライセンス・権利

- 原作: [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)。sourceの履歴に、WeAthFolD、KSkun、Paindarほかの名前があります。
- 比較・参考: [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)。MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。
- クレジットの全体は`CREDITS.md`、ライセンスと帰属・改変の表示は`NOTICE.md`、素材ごとの出所と状態は`ASSET_PROVENANCE.tsv`、権利の申立ては`RIGHTS_CLAIMS.md`にあります。
- **コード**: 原作のREADMEは「All versions of AcademyCraft are licensed under GPLv3」と述べ、あわせて販売の禁止とLambda Innovationの権利の留保を述べる追加の文言を置いています。GPLv3の全文は`LICENSE`に、原作のREADMEの原文は`licenses/`にあります。Re:AcademyCraftは、GPLv3と追加の文言の関係を判断していません。
- **素材**: 画像・モデル・シェーダー・音の多くは原作由来です。そのうち一部は、合理的な調査でも作者・権利者・再配布の条件を特定できていません。`ASSET_PROVENANCE.tsv`で`UNRESOLVED`としており、使用許諾や権利の処理が済んでいるという意味ではありません。
- Re:AcademyCraftは無料で配布します。有料のダウンロード、有料の能力・アイテムはありません。
