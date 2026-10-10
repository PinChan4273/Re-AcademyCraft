# クレジット — Re:AcademyCraft

Re:AcademyCraftは、Lambda Innovationの原作AcademyCraftに基づいて作った、**非公式**の独立したMinecraft 1.20.1 / Forge移植の実装です。MohistMC/AcademyCraftは現代版の比較・参考実装として利用しました。公開版Re:AcademyCraftの直接のコード・データ形式・モデルの出所にはしていません。

## 上流

| 部分 | クレジット | 上流が述べている条件 |
| --- | --- | --- |
| 原作 | [LambdaInnovation/AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft)。sourceの履歴に、WeAthFolD、KSkun、Paindarほかの名前があります。 | READMEは全版をGPLv3とし、追加の文言を置いています（`licenses/AcademyCraft-original-README.md`）。 |
| LambdaLib2 | [LambdaInnovation/LambdaLib2](https://github.com/LambdaInnovation/LambdaLib2)（commit `abb4b9f1702a731652b80b05c7163dca9dca2237`） | そのcommitのREADMEは「This project uses MIT license.」と述べています。LICENSEファイルと著作権の表示は無いので、著作権者は推定していません。 |
| Minecraft | Mojang Studios | Minecraft EULAと利用ガイドライン。 |
| Gradle wrapper | Gradle | Apache License 2.0（`licenses/Gradle-Apache-2.0.txt`）。 |
| Minecraft Forge | 依存物で、同梱していません。 | LGPL 2.1。 |

比較・参考: [MohistMC/AcademyCraft](https://github.com/MohistMC/AcademyCraft)とその貢献者。現代版の比較・参考実装として利用しました（原典ではなく、この版のコード・データ形式・モデルの出所ではありません）。

## Minecraft 1.20.1 / Forge版（ブランチ`1.20.1`）

| 対象 | クレジット |
| --- | --- |
| `assets/academy/shaders/core/mono.fsh` | LambdaLib2の`legacy_shader/mono.frag`から移植。 |
| `assets/academy/shaders/core/position_tex_color_keep.fsh`、`particle_keep.fsh` | Minecraft 1.20.1（Mojang）の`position_tex_color.fsh`・`particle.fsh`の写しで、断片を捨てる条件を変えたもの。 |
| 英語・日本語・簡体字中国語の言語ファイルと教程 | 上流の翻訳を元に、この版のために編集し直した派生の翻訳です。協力者の名前は、本人の希望に従って追記します。 |
| 移植 | PinChan4273とRe:AcademyCraftの貢献者。 |

## 素材と権利

- 素材ごとの出所と状態は`ASSET_PROVENANCE.tsv`にあります。`UNRESOLVED`の素材は、作者・権利者・再配布の条件を確認できていません。使用許諾や権利の処理が済んでいるとは表示しません。
- 原作が同梱していた市販の楽曲（「only my railgun」「LEVEL5-judgelight-」「sister's noise」）とそのCDジャケットは含みません。
- 原作者、Lambda Innovation、MohistMC、Mojang Studios、Microsoft、関連作品の権利者から公認・承認・支援を受けたものではなく、提携もしていません。
- 権利の申立て: **pinmodcontact@gmail.com**（手順は`RIGHTS_CLAIMS.md`）
