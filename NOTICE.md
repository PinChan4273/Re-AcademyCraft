# NOTICE — Re:AcademyCraft（Minecraft 1.20.1 / Forge）

Re:AcademyCraftは、原作AcademyCraftに基づいてMinecraft 1.20.1 / Forge向けに作った、**非公式**の独立した移植の実装です。
原作者、Lambda Innovation、MohistMC、Mojang Studios、Microsoft、関連作品の権利者から公認・承認・支援を受けたものではなく、提携もしていません。

## 原典と帰属

| 原典 | 固定した版 | この版との関係 |
| --- | --- | --- |
| [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)（Minecraft 1.12.2） | commit `7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9` | 原作。ゲームの仕様・数値・素材の主な出所で、この版の実装の正本。sourceの履歴にはWeAthFolD、KSkun、Paindarほかの名前があります。 |
| [LambdaInnovation/LambdaLib2](https://github.com/LambdaInnovation/LambdaLib2) | commit `abb4b9f1702a731652b80b05c7163dca9dca2237` | 原作が使うライブラリ。シェーダー1つを移植しています（下記）。 |

著作権と作者の権利は、それぞれの原典の作者に残ります。

### 比較・参考

- [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)（commit `1b83239e6aa1976a73cb35b6142ae00c41c28e9c`）: MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。
- 以前の非公開の開発版には、その一部（クラス・保存キー・風力発電機のモデル）を含むものがありました。この版では、それらを原作から作り直しています。
- これは、法的に完全に独立していることを認定・保証するものではありません。

## 改変の表示

- この版は、上記の原作（とLambdaLib2のシェーダー1つ）を移植・改変したものです。2026年9月から、Minecraft 1.20.1 / Forge向けに移植・改変しました（1.0.0は2026年10月）。
- 主な改変: Forge 1.20.1のAPIへの置き換え、サーバーを正とする同期、Forgeのcapability・メニュー・ネットワークへの書き直し、描画のcore shaderへの移植、データパック形式への変換、日本語のコメント。
- 1.0.0より後の個々の変更は、このリポジトリのGitの履歴に記録します。

## ライセンス

- 原作のREADMEは「All versions of AcademyCraft are licensed under GPLv3」と述べ、あわせて販売の禁止とLambda Innovationの権利の留保を述べる追加の文言を置いています。
- GPLv3の全文は`LICENSE`にあります。原作のREADMEの原文は、その追加の文言を含めて、変えずに`licenses/AcademyCraft-original-README.md`へ保持しています。
- この版は、GPLv3と追加の文言の関係を判断していません。どちらの文言も捨てずに保持しています。
- JARには`LICENSE`、この`NOTICE.md`、`licenses/`（`META-INF/licenses/`）を同梱しています。

## 第三者の部分

- **LambdaLib2のシェーダー**: `src/main/resources/assets/academy/shaders/core/mono.fsh`は、LambdaLib2の`legacy_shader/mono.frag`（上記commit、ShaderMonoが使用）から移植しました。そのcommitのREADMEは「This project uses MIT license.」と述べていますが、LICENSEファイルも著作権の表示もありません。そのためMITの条件はそのREADMEの記述として記録するだけで、著作権者は推定していません。
- **Minecraft由来のシェーダー**: `shaders/core/position_tex_color_keep.fsh`と`shaders/core/particle_keep.fsh`は、Minecraft 1.20.1（Mojang）の`position_tex_color.fsh`と`particle.fsh`の写しで、断片を捨てる条件を`color.a < 0.1`から`color.a == 0.0`へ変えたものです。Mojangのコンテンツとして、Minecraft EULAと利用ガイドラインに従います。
- **Gradle wrapper**: `gradle/wrapper/gradle-wrapper.jar`はGradle 8.8（gradle/gradleのtag `v8.8.0`）のもの、`gradlew`と`gradlew.bat`はForge 1.20.1のリポジトリから取ったGradleの起動スクリプトで、Apache-2.0のヘッダーを保持しています。いずれもApache License 2.0です（`licenses/Gradle-Apache-2.0.txt`）。`gradle-wrapper.jar`のSHA-256は`cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`で、<https://gradle.org/release-checksums/>の値と一致します。
- **Minecraft Forge**: 依存物です。この版には同梱していません（LGPL 2.1）。

## 素材

- 画像・モデル・シェーダー・音・言語ファイル・教程の多くは、上流のAcademyCraftに由来します。
- そのうち一部は、合理的な調査でも作者・権利者・再配布の条件を特定できていません。素材ごとの出所と状態は`ASSET_PROVENANCE.tsv`にあり、該当するものは`UNRESOLVED`としています。これは、使用許諾や権利の処理が済んでいるという意味ではありません。
- 権利の申立ては`RIGHTS_CLAIMS.md`を見てください。
- 原作が同梱していた市販の楽曲（「only my railgun」「LEVEL5-judgelight-」「sister's noise」）とそのCDジャケットは、**含みません**。
