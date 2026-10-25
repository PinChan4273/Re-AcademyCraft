# Re:AcademyCraft API Example（サンプルのアドオン）

Re:AcademyCraft 1.4.0の公開API（**実験的**。版の間で変わりうる）だけを使う、小さなサンプルです。
公開の型は`io.github.pinchan4273.reacademycraft.api`（Minecraftの型を使わない定義）、`api.mc`（実行の契約と安全のサービス）、
`api.forge`（Forgeの登録の窓）、`api.client`（クライアント専用の見た目だけの演出。このサンプルは使わない）だけです。ほかのパッケージは内部の物で、予告なく変わります。

## 中身

- 新しいカテゴリ「Kinetics」と因子のアイテム（本体の開発機で能力を得られる）
- Pulse（一回きり）: まわりの生き物への衝撃。攻撃は`AcademySafety.attack`を通し、許された相手だけを押し返す
- Glide（切り替え型）: 落ちる速さを抑える。ONの間、tickごとにCPを払う
- Spark Step（本体のElectromasterへの技能）: 短い瞬間移動。`AcademySafety.checkTeleport`・`teleport`を通す
- Kinetic Charge（ブロック、機械の主体の例）と技能Burst: 置いた人を持ち主として覚え、赤石の信号で、機械の主体（`ActorKind.MACHINE`）として
  `AbilityActivation.activate`でBurstを発動する（資源の方針は`ResourcePolicy.FREE`を明示）。Burstの爆発は`AcademySafety.explode`を通す。
  持ち主がログインしていなければ帰属先は無く、Re:AcademyCraftは地形の変更とプレイヤーへの攻撃を断る。機械なのでゲームルールmobGriefingも要る

## 作り方

本体のリポジトリの直下で、先に本体のJARを作ってから、このサンプルを作ります。

```
bash gradlew jar
bash gradlew -p samples/academy-api-example build
```

できたJAR（`samples/academy-api-example/build/libs/academy-api-example-forge-1.20.1-0.1.0.jar`）を、本体のJARと一緒にmodsへ置きます。

## 注意

- Re:AcademyCraftが判定するのは、APIのサービス（`AcademySafety`）を通した攻撃・地形の変更・空間移動だけです。
  アドオンが`Entity.hurt`や`Level.setBlock`を直接呼べば、Re:AcademyCraftには止められません。
- このサンプルのうち、このプロジェクトが新しく書いた部分のライセンスはGPLv3です（`LICENSE`。本体と同じ）。APIのクラスはJARに入れず、実行の時に
  Re:AcademyCraft本体へ依存します。配布とライセンスの方針は、本体の`API_GUIDE_JA.md`（API開発者向けの手引き）の「9. 配布とライセンス」にあります。
- アイテムの見た目はMinecraftの`amethyst_shard`を参照するだけで、画像のファイルは含みません。
- サンプルの値は動きを見せるためのもので、遊ぶための調整はしていません。
