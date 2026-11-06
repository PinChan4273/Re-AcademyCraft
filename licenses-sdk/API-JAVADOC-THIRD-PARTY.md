# Re:AcademyCraft API Javadoc — 第三者の部品

このJAR（`re-academycraft-neoforge-26.2-<版>-api-javadoc.jar`）は、Re:AcademyCraftの公開API（実験的）のJavadocです。

| 部分 | 出所 | ライセンス・表示 |
| --- | --- | --- |
| APIの説明の文（HTMLの本文） | Re:AcademyCraftのソースの文書コメント | GPLv3（`LICENSE`）。帰属と改変の表示は`NOTICE.md`と`META-INF/licenses/` |
| `resource-files/stylesheet.css`・`script-files/script.js`・`script-files/search.js`・`script-files/search-page.js`・`resource-files/`の画像など、javadocが出力へ写すファイル | OpenJDK 25のjavadoc（Eclipse AdoptiumのTemurin） | GPLv2とClasspath例外（`legal/LICENSE`・`legal/ASSEMBLY_EXCEPTION`・`legal/ADDITIONAL_LICENSE_INFO`。作ったJDKの本文をそのまま入れた） |
| `script-files/jquery-3.7.1.min.js` | jQuery 3.7.1（OpenJS Foundation and other contributors） | MIT（`legal/jquery.md`） |
| `script-files/jquery-ui.min.js`・`resource-files/jquery-ui.min.css` | jQuery UI 1.14.1（OpenJS Foundation and other contributors） | MIT（`legal/jqueryUI.md`） |
| `resource-files/fonts/`のDejaVuLGCのフォント（`.woff`・`.woff2`）と`dejavu.css` | DejaVu fonts 2.37 | Bitstream Vera Fontsの許諾とArev Fontsの許諾。DejaVuの変更はパブリックドメイン（`legal/dejavufonts.md`） |

- 版と表記は、このJARを作ったjavadoc（Eclipse AdoptiumのTemurin、JDK 25）が出力へ写したファイルの見出しと`legal/`の本文に合わせています。ビルドは、この一覧のパスと版が出力と違えば止まります。
- Minecraft・NeoForgeと、原作AcademyCraftの画像・音・モデルは含みません。
- このJARは参照のための物で、完全な対応ソースではありません。対応するソースは、公開のリポジトリの対応するcommitにあります。
