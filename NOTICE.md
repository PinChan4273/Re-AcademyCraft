# NOTICE — Re:AcademyCraft（Minecraft 26.2 / NeoForge）

Re:AcademyCraftは、原作AcademyCraftに基づいて作った、**非公式**の独立した移植の実装です。この版は、Minecraft 1.20.1 / Forge向けのRe:AcademyCraft 1.1.0をMinecraft 1.21.1 / NeoForge向けに移植したものを、さらにMinecraft 26.2 / NeoForge向けに移植したものです。
原作者、Lambda Innovation、MohistMC、Mojang Studios、Microsoft、NeoForged、関連作品の権利者から公認・承認・支援を受けたものではなく、提携もしていません。

## 原典と帰属

| 原典 | 固定した版 | この版との関係 |
| --- | --- | --- |
| [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)（Minecraft 1.12.2） | commit `7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9` | 原作。ゲームの仕様・数値・素材の主な出所で、この版の実装の正本。sourceの履歴にはWeAthFolD、KSkun、Paindarほかの名前があります。 |
| [LambdaInnovation/LambdaLib2](https://github.com/LambdaInnovation/LambdaLib2) | commit `abb4b9f1702a731652b80b05c7163dca9dca2237` | 原作が使うライブラリ。シェーダー1つを移植しています（下記）。 |
| Re:AcademyCraft 1.1.0（Minecraft 1.20.1 / Forge） | このリポジトリのcommit `e51f2d89cb63b77c539cd6d63d0ec6a972e1f685` | NeoForge 1.21.1版の移植元。仕様・数値・素材は、その版のものを引き継いでいます。 |
| Re:AcademyCraft（Minecraft 1.21.1 / NeoForge、1.4.1の開発の版） | このリポジトリのcommit `a5a5f90785337b935e5d995f1177c1b208c97d2a` | この26.2版の移植元。その後の機能（1.5.0・1.5.1）は、Forge 1.20.1版とNeoForge 1.21.1版の同じ変更から移しています。 |

著作権と作者の権利は、それぞれの原典の作者に残ります。

### 比較・参考

- [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)（commit `1b83239e6aa1976a73cb35b6142ae00c41c28e9c`）: MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。
- 以前の非公開の開発版には、その一部（クラス・保存キー・風力発電機のモデル）を含むものがありました。Forge版1.1.0では、それらを原作から作り直しています。
- これは、法的に完全に独立していることを認定・保証するものではありません。

## 改変の表示

- この版は、上記の原作（とLambdaLib2のシェーダー1つ）を移植・改変したものです。2026年9月から、Minecraft 1.20.1 / Forge向けに移植・改変しました（1.0.0は2026年10月）。2026年10月から、その1.1.0をMinecraft 1.21.1 / NeoForge向けに移植しました。2026年11月から、そのNeoForge版をMinecraft 26.2 / NeoForge向けに移植しています。
- Forge版での主な改変: Forge 1.20.1のAPIへの置き換え、サーバーを正とする同期、Forgeのcapability・メニュー・ネットワークへの書き直し、描画のcore shaderへの移植、データパック形式への変換、日本語のコメント。
- NeoForge版での主な改変: NeoForge 21.1のAPIへの置き換え（data attachment、Data Components、payloadの通信、capabilityの登録）、Forge版と同じ段階で被弾を扱うためのmixin、1.21のデータパックの形式への変換。
- 26.2版での主な改変: Minecraft 26.2・NeoForge 26.2のAPIへの置き換え（描画のパイプラインと提出、保存の入出力、ゲームルール）、26.2のシェーダーとデータパックの形式への書き直し、保存データの置き場所の移行。
- 個々の変更は、このリポジトリのGitの履歴に記録します。

## ライセンス

- 原作のREADMEは「All versions of AcademyCraft are licensed under GPLv3」と述べ、あわせて販売の禁止とLambda Innovationの権利の留保を述べる追加の文言を置いています。
- GPLv3の全文は`LICENSE`にあります。原作のREADMEの原文は、その追加の文言を含めて、変えずに`licenses/AcademyCraft-original-README.md`へ保持しています。
- この版は、GPLv3と追加の文言の関係を判断していません。どちらの文言も捨てずに保持しています。
- JARには`LICENSE`、この`NOTICE.md`、`licenses/`と`TEMPLATE_LICENSE.txt`（`META-INF/licenses/`）を同梱しています。

## 第三者の部分

- **LambdaLib2のシェーダー**: `src/main/resources/assets/academy/shaders/core/mono.fsh`は、LambdaLib2の`legacy_shader/mono.frag`（上記commit、ShaderMonoが使用）から移植しました。そのcommitのREADMEは「This project uses MIT license.」と述べていますが、LICENSEファイルも著作権の表示もありません。そのためMITの条件はそのREADMEの記述として記録するだけで、著作権者は推定していません。
- **Minecraft由来のシェーダー**: `shaders/core/particle_keep.fsh`は、Minecraft 1.20.1（Mojang）の`particle.fsh`の写しで断片を捨てる条件を`color.a < 0.1`から`color.a == 0.0`へ変えたものを、26.2の形へ書き直したものです。`shaders/core/position_tex_color_cut.fsh`と`shaders/core/eyes_additive.fsh`は、Minecraft 26.2の`position_tex_color.fsh`と`entity.fsh`を元に書いたものです（変えた所は`ASSET_PROVENANCE.tsv`にあります）。Mojangのコンテンツとして、Minecraft EULAと利用ガイドラインに従います。
- **NeoForge MDK**: `build.gradle`・`settings.gradle`・`gradle.properties`と`src/main/templates/META-INF/neoforge.mods.toml`は、NeoForgedのMDK（NeoForgeMDKs/MDK-1.21.1-ModDevGradle、commit `3bca8f1e0756c001e4b71f266c328d1568d29363`）を元にし、版とJavaをNeoForgeMDKs/MDK-26.2-ModDevGradleの値へ合わせています。そのテンプレートはMIT License（`TEMPLATE_LICENSE.txt`、Copyright (c) 2023 NeoForged project）です。
- **Gradle wrapper**: `gradle/wrapper/gradle-wrapper.jar`はGradle 9.2.1のもの、`gradlew`と`gradlew.bat`はGradleの起動スクリプトで、Apache-2.0のヘッダーを保持しています。いずれもApache License 2.0です（`licenses/Gradle-Apache-2.0.txt`）。`gradle-wrapper.jar`のSHA-256は`423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9`で、<https://services.gradle.org/distributions/gradle-9.2.1-wrapper.jar.sha256>の値と一致します（2026-10-14に確認）。配布ZIPのSHA-256は`gradle-wrapper.properties`で`72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f`に固定しています。
- **NeoForge**: 依存物です。この版には同梱していません（LGPL 2.1）。

## 素材

- 画像・モデル・シェーダー・音・言語ファイル・教程の多くは、上流のAcademyCraftに由来します。
- そのうち一部は、合理的な調査でも作者・権利者・再配布の条件を特定できていません。素材ごとの出所と状態はリポジトリの`ASSET_PROVENANCE.tsv`にあり、該当するものは`UNRESOLVED`としています。これは、使用許諾や権利の処理が済んでいるという意味ではありません。26.2版で書き直したシェーダーは、`ASSET_PROVENANCE.tsv`の説明に元のファイルと変えた所を書いています。
- 権利の申立ては、リポジトリの`RIGHTS_CLAIMS.md`を見てください。
- 原作が同梱していた市販の楽曲（「only my railgun」「LEVEL5-judgelight-」「sister's noise」）とそのCDジャケットは、**含みません**。
