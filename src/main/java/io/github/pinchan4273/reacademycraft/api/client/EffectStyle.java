package io.github.pinchan4273.reacademycraft.api.client;

/**
 * 外部の演出の種類（公開API、実験的。演出API版2）。光線は{@link BeamStyle}。値ごとに、{@link EffectRequest}のどの欄を使うかが決まる。
 * どれも見た目だけで、被害・命中・費用に関係しない。音は鳴らさない（{@link #LIGHTNING}だけ、バニラの雷と同じ音が鳴る）。音の名前は{@code api.mc.AcademySoundIds}。
 * 後の版で値を足すことがある。この版が持たない値は{@link ExternalPresentation#supports(EffectStyle)}がfalse。
 */
public enum EffectStyle {
    /** アーク（電撃使いの弱い電弧、寿命10tick）。{@code position}から{@code target}へ。長さは20ブロックまで。{@code entity}があれば、自然能力と同じく手元からずらす。 */
    ARC_WEAK,
    /** サンダーボルトの主電弧（強い電弧3本、寿命20tick）。{@code position}から{@code target}へ。長さは20ブロックまで。{@code entity}があれば、手元からずらす。 */
    ARC_STRONG,
    /** サンダーボルトの範囲の電弧（1本、寿命15〜24tick）。{@code position}から{@code target}へ。長さは20ブロックまで。 */
    ARC_AREA,
    /** 体の周りに弾ける細い電弧（寿命15tick）。{@code entity}があれば追従し（いなくなれば消える）、無ければ{@code position}に出す。 */
    ARC_BURST,
    /** サンダークラップの雷（バニラの見た目だけの雷）。{@code position}に落ちる。バニラの雷と同じ音が鳴る。 */
    LIGHTNING,
    /** テレポートの軌跡（tpの粒子）。{@code position}から{@code target}へ。粒子は最大128個。 */
    TELEPORT_TRAIL,
    /** シフトテレポートの軌跡（細かいtpの粒子）。{@code position}から{@code target}へ。粒子は最大128個。 */
    SHIFT_TRAIL,
    /** ベクトル操作の輪の波（寿命15tick）。{@code position}から{@code target}の方向へ。{@code scale}は大きさ（0より大きく8以下）。 */
    WAVE_RINGS,
    /** 血しぶき（寿命10tick）。{@code entity}があればその体の周り、無ければ{@code position}の周り。 */
    BLOOD_SPLASH,
    /** ブラッド・レトログラードの大きな血しぶき（6〜9個、寿命10tick）。{@code entity}があればその体の周り、無ければ{@code position}の周り。 */
    BLOOD_SPLASH_LARGE,
    /** 会心の数式の絵（寿命約35tick）。{@code entity}があればその体の周り、無ければ{@code position}の周り。 */
    CRITICAL_FORMULA,
    /** 血の跡（ブラッド・レトログラード。1200tick残る）。{@code position}から{@code target}へ線を引き、当たったブロックの面に付ける。当たらなければ何も付けない。 */
    BLOOD_SPRAY,
    /** グラウンドショックの破片と煙。{@code position}の高さのブロックのうち、半径{@code scale}（0より大きく6以下）の円の中で出す。 */
    GROUND_DEBRIS,
    /** レールガンの手元の電弧の絵（寿命1.6秒）。{@code entity}（プレイヤー）に付く。{@code entity}が要る。 */
    RAILGUN_HAND,

    // ---- ここから持続する演出（{@link ExternalPresentation#startEffect}で始める） ----------------------

    /** 原子崩しの球。{@code position}に出す。{@code entity}があれば、受け付けたときの実体からのずれを保って追従する（自然能力の球と同じ）。 */
    ORB,
    /** 引き寄せの細い電弧。{@code position}から{@code target}へ。長さは20ブロックまで。{@code entity}があれば、手元からずらす。 */
    ARC_CONTINUOUS,
    /** 体の周りを這う細い電弧（チャージングのアイテム）。{@code entity}があれば追従し、無ければ{@code position}。{@code scale}は箱の大きさ（0より大きく32以下）。 */
    ARC_SURROUND,
    /** 周りを這う普通の太さの電弧（チャージングの対象）。欄は{@link #ARC_SURROUND}と同じ。 */
    ARC_SURROUND_NORMAL,
    /** 周りを這う太い電弧（サンダークラップ）。欄は{@link #ARC_SURROUND}と同じ。 */
    ARC_SURROUND_BOLD,
    /** 目標の波紋（サンダークラップの目印）。{@code position}に出す。 */
    RIPPLE,
    /** ライト・シールドの盾。{@code entity}が要る。盾の向きは{@code entity}の足元から{@code target}へ。{@code scale}は半径（0より大きく8以下）。 */
    LIGHT_SHIELD,
    /** ジェット・エンジンの菱形と粒子。{@code entity}が要る。向きは{@code entity}の視線に従う。 */
    JET_SHIELD,
    /** ストーム・ウィングの背の竜巻と土の粒子。{@code entity}が要る。止めると、自然能力と同じように15tickで薄れて消える。 */
    STORM_WING,
    /** プラズマ・キャノンの球と竜巻（重い。持ち主ごとに同時2つ）。{@code position}に出す。{@code scale}は球の大きさの倍率（1が自然能力と同じ、0より大きく4以下。竜巻は変わらない）。{@code target}付きで更新すると竜巻が薄れる（飛ぶ段階）。止めると、自然能力と同じように薄れて消える。 */
    PLASMA_CANNON,
    /** 採掘の光線（基本）。{@code entity}が要る。{@code entity}の視線に沿って15ブロック。 */
    MINE_RAY_BASIC,
    /** 採掘の光線（上級）。{@code entity}が要る。 */
    MINE_RAY_EXPERT,
    /** 採掘の光線（幸運）。{@code entity}が要る。 */
    MINE_RAY_LUCK;

    /** 持続する演出か（{@link ExternalPresentation#startEffect}で始める）。それ以外は一発（{@link ExternalPresentation#play}）。 */
    public boolean lasting() { return ordinal() >= ORB.ordinal(); }
}
