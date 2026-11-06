# Re:AcademyCraft

**Re:AcademyCraft**は、Lambda InnovationのMOD「AcademyCraft」（Minecraft 1.12.2）を、新しいMinecraftへ移植する**非公式**のプロジェクトです。

> **Minecraftの公式製品ではありません。Mojang・Microsoftの承認を受けておらず、提携もしていません。**
> 原作者、Lambda Innovation、MohistMC、関連作品の権利者から公認・承認・支援を受けたものでも、提携するものでもありません。

このブランチ（`main`）は案内だけを置いています。製品のsourceは、Minecraftのバージョンごとのブランチにあります。

## 対応バージョン

| Minecraft | ローダー | Re:AcademyCraft | sourceのブランチ | release |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge `[47.4.10,48)` | 1.5.1 | [`1.20.1`](https://github.com/PinChan4273/Re-AcademyCraft/tree/1.20.1) | [`1.20.1-v1.5.1`](https://github.com/PinChan4273/Re-AcademyCraft/releases/tag/1.20.1-v1.5.1) |
| 1.21.1 | NeoForge `[21.1.259,)` | 1.5.1 | [`1.21.1-neoforge`](https://github.com/PinChan4273/Re-AcademyCraft/tree/1.21.1-neoforge) | [`1.21.1-neoforge-v1.5.1`](https://github.com/PinChan4273/Re-AcademyCraft/releases/tag/1.21.1-neoforge-v1.5.1) |
| 26.2 | NeoForge `[26.2.0.93,)` | 1.5.1 | [`26.2-neoforge`](https://github.com/PinChan4273/Re-AcademyCraft/tree/26.2-neoforge) | [`26.2-neoforge-v1.5.1`](https://github.com/PinChan4273/Re-AcademyCraft/releases/tag/26.2-neoforge-v1.5.1) |

- releaseの一覧: <https://github.com/PinChan4273/Re-AcademyCraft/releases>
- 公式の導入用JARは、CurseForge（<https://www.curseforge.com/minecraft/mc-mods/re-academycraft>、Project ID `1735500`）だけで配布します。GitHub Releasesでは更新情報と対応ソースを案内し、Forge 1.20.1版は1.4.0から、NeoForge 1.21.1版は1.4.1から、NeoForge 26.2版は1.5.1から、開発者向けSDKを添付します。SDKのJARはゲーム導入用の本体ではありません。
- Forge 1.20.1版の1.5.1は、1.5.0からの更新です。未元物質の調整（白翼自動防御の羽の球、甲虫生成のキーを離したときの生成と長押しでの回収、カブトムシの出現の演出、白翼刺突のしなり、シェーダーパックの使用中の翼の発光）と、演出APIの版3を含みます。通信プロトコルを変えたので、1.5.0とは接続できません。
- NeoForge 1.21.1版の1.5.1は、NeoForge版1.4.1からの更新です。NeoForge版では1.5.0を出していないため、Forge 1.20.1版の1.5.0（5番目の能力系統「未元物質」など）と1.5.1（未元物質の調整と演出APIの版3）の内容を、この1回の更新にまとめて入れています。通信プロトコルは66で、NeoForge版1.4.1とは接続できません。
- Minecraft 26.2 / NeoForge版は、1.5.1が最初のリリースです。主な機能と能力の構成はNeoForge 1.21.1版の1.5.1と共通で、Minecraft 26.2・NeoForgeに合わせた違いがあります（ブランチ`26.2-neoforge`の`README.md`）。Java 25とNeoForge 26.2.0.93以上が必要です。Vulkanの描画は、開発の環境で動作を確かめています（NeoForgeの`earlyWindowControl`を`false`にする必要があります。詳しくはブランチ`26.2-neoforge`の`README.md`）。
- Minecraftのバージョンが違う版どうし（1.20.1・1.21.1・26.2）は接続できません。ほかの版で遊んだワールドを開いたときの移行は確認していません。
- アドオンのJARは、ローダーとMinecraftのバージョンごとに別で、そのまま使い回せません。
- Forge 1.20.1版・NeoForge 1.21.1版・NeoForge 26.2版のJARは別のものです。同時に入れないでください。
- 導入方法・動作要件・既知の問題は、各ブランチの`README.md`を見てください。
- tagは`<Minecraftのバージョン>[-<ローダー>]-v<Re:AcademyCraftのバージョン>`の形です（例: `1.20.1-v1.0.0`、`1.21.1-neoforge-v1.4.1`、`26.2-neoforge-v1.5.1`）。Forge 1.20.1版のtagにはローダーの名前を付けていません。

## 不具合の報告・要望

- GitHub Issues: <https://github.com/PinChan4273/Re-AcademyCraft/issues>
- **公開のIssueに、個人情報・秘密情報・権利を証明する資料を書かないでください。**

## 権利の申立て

- 窓口: **pinmodcontact@gmail.com**
- 手順は`RIGHTS_CLAIMS.md`を見てください。

## ライセンスとクレジット

- 原作: [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)
- 比較・参考: [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)。MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。
- 原作のREADMEは「All versions of AcademyCraft are licensed under GPLv3」と述べ、あわせて販売の禁止と権利の留保を述べる追加の文言を置いています。GPLv3の全文は`LICENSE`にあります。原作のREADMEの原文と、素材ごとの出所は、各製品ブランチの`licenses/`・`NOTICE.md`・`ASSET_PROVENANCE.tsv`にあります。
- クレジットの全体は`CREDITS.md`にあります。
- 原作由来の素材の一部は、作者・権利者・再配布の条件を特定できていません。使用許諾や権利の処理が済んでいるとは表示しません。
- 原作が同梱していた市販の楽曲とそのCDジャケットは含みません。
