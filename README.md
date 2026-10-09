# Re:AcademyCraft — Minecraft 1.20.1 / Forge

**Re:AcademyCraft**は、Lambda InnovationのMOD「AcademyCraft」（Minecraft 1.12.2）の、**Minecraft 1.20.1 / Forge**向けの**非公式**移植版です。

> **Minecraftの公式製品ではありません。Mojang・Microsoftの承認を受けておらず、提携もしていません。**
> 原作者、Lambda Innovation、MohistMC、関連作品の権利者から公認・承認・支援を受けたものでも、提携するものでもありません。

| | |
| --- | --- |
| バージョン | 1.0.0 |
| このブランチ | `1.20.1`（Minecraft 1.20.1 / Forge版のsource） |
| tag | `1.20.1-v1.0.0` |

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
2. `re-academycraft-forge-1.20.1-1.0.0.jar`を、クライアントの`mods`フォルダーへ入れます。マルチプレイではサーバーにも入れます。
   - 公式の導入用JARは、CurseForge（Project ID `1735500`）だけで配布します。現在は配布の準備中です。GitHubのreleaseは更新情報とsourceの参照用で、JARは添付しません。
   - 導入用JARのSHA-256: `0103dc539dd598e7c2c82077cd6662a0988e3cc306c279b0ae5efd4c87793cad`
3. 更新の前に、ワールドと設定をバックアップしてください。
   - 以前の試験版で遊んだワールドは、開いたときに能力データなどを新しい保存の形へ移します。移した後のワールドは、以前の試験版では正しく読めません。
   - クライアントとサーバーは同じバージョンを使用してください。互換性のない通信プロトコルは接続時に拒否されます。
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
- **ほかのMODとの組み合わせ**: Embeddium・Oculus・シェーダーパックなど、描画を変えるMODとの組み合わせは確認していません。表示がおかしいときは、それらを外して試してください。
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

- 導入用のJARは`build/libs/re-academycraft-forge-1.20.1-1.0.0.jar`です（`reobfJar`で再難読化したもの）。
- 同じ場所の`-sources.jar`は導入用ではありません。
- sourceと文書はUTF-8です。Javaのコンパイルとリソースの処理にUTF-8を指定しているので、WindowsでもLinuxでも、OSの既定の文字コードに依存せずにビルドできます。
- JARはファイルの時刻と順序を固定して作るので、同じsourceからは同じJARができます。
- 一部のデータ（レシピ・戦利品・ワールド生成・素材のモデルなど）は`tools/generate_*.py`（Python 3）で生成し、生成結果をリポジトリに入れています。生成スクリプトはリポジトリのルートで実行します。

## クレジット・ライセンス・権利

- 原作: [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)。sourceの履歴に、WeAthFolD、KSkun、Paindarほかの名前があります。
- 比較・参考: [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)。MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。
- クレジットの全体は`CREDITS.md`、ライセンスと帰属・改変の表示は`NOTICE.md`、素材ごとの出所と状態は`ASSET_PROVENANCE.tsv`、権利の申立ては`RIGHTS_CLAIMS.md`にあります。
- **コード**: 原作のREADMEは「All versions of AcademyCraft are licensed under GPLv3」と述べ、あわせて販売の禁止とLambda Innovationの権利の留保を述べる追加の文言を置いています。GPLv3の全文は`LICENSE`に、原作のREADMEの原文は`licenses/`にあります。Re:AcademyCraftは、GPLv3と追加の文言の関係を判断していません。
- **素材**: 画像・モデル・シェーダー・音の多くは原作由来です。そのうち一部は、合理的な調査でも作者・権利者・再配布の条件を特定できていません。`ASSET_PROVENANCE.tsv`で`UNRESOLVED`としており、使用許諾や権利の処理が済んでいるという意味ではありません。
- Re:AcademyCraftは無料で配布します。有料のダウンロード、有料の能力・アイテムはありません。
