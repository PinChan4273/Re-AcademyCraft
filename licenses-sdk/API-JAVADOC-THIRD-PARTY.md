# Re:AcademyCraft API Javadoc — 第三者の部品

このJAR（`re-academycraft-forge-1.20.1-<版>-api-javadoc.jar`）は、Re:AcademyCraftの公開API（実験的）のJavadocです。

| 部分 | 出所 | ライセンス・表示 |
| --- | --- | --- |
| APIの説明の文（HTMLの本文） | Re:AcademyCraftのソースの文書コメント | GPLv3（`LICENSE`）。帰属と改変の表示は`NOTICE.md`と`META-INF/licenses/` |
| `stylesheet.css`・`script.js`・`search.js`・`jquery-ui.overrides.css`・`resources/`の画像など、javadocが出力へ写すファイル | OpenJDK 17のjavadoc（Eclipse AdoptiumのTemurin） | GPLv2とClasspath例外（`legal/LICENSE`・`legal/ASSEMBLY_EXCEPTION`・`legal/ADDITIONAL_LICENSE_INFO`。作ったJDKの本文をそのまま入れた） |
| `script-dir/jquery-3.7.1.min.js` | jQuery 3.7.1（OpenJS Foundation and other contributors） | MIT（`legal/jquery.md`） |
| `script-dir/jquery-ui.min.js`・`script-dir/jquery-ui.min.css` | jQuery UI 1.14.1（OpenJS Foundation and other contributors） | MIT（`legal/jqueryUI.md`） |

- 版と表記は、このJARを作ったjavadoc（Eclipse AdoptiumのTemurin、JDK 17）が出力へ写したファイルの見出しと`legal/`の本文に合わせています。ビルドは、この一覧のパスと版が出力と違えば止まります。
- Minecraft・Minecraft Forgeと、原作AcademyCraftの画像・音・モデルは含みません。
- このJARは参照のための物で、完全な対応ソースではありません。対応するソースは、公開のリポジトリの対応するcommitにあります。
