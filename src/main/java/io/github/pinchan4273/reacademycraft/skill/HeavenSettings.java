package io.github.pinchan4273.reacademycraft.skill;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * v1.5.0 天界発現の個人設定（§10〜§12、§15の「系統を失っても保持」）。能力データの{@code SkillSettings}区分に保存し、サーバーが正本。
 * 領域は毎回ここを読むので、変更は発動中でも即時に効く。既定の値は書かない。
 * <p>
 * 常時効果は対象区分と効果の組ごとのON／OFFで、相反する2効果は{@link Pair}の1つの選択（OFF／強化／弱体化）にしているので、同一区分で両方はONにできない（二者択一）。
 * 既定でONなのは、自分の甲虫の「攻撃強化」と敵対モブの「移動阻害」だけ（§10）。
 * 怪現象は、現象ごとにON／OFF・頻度・対象区分を持つ。既定は全現象ONで対象は敵対モブだけ（§10は「発生費用は別に精算する」と書くが初期の組み合わせは書いていないので実装で決めた。
 * 暗闇はプレイヤー専用なので既定の対象なし）。
 */
public final class HeavenSettings {
    /** §12の対象区分。 */
    public enum Target {
        SELF("self"), BEETLE("beetle"), ALLY("ally"), OTHER("other"), HOSTILE("hostile"), NEUTRAL("neutral");
        private final String id;
        Target(String id) { this.id = id; }
        public String id() { return id; }
        public int bit() { return 1 << ordinal(); }
        public static @Nullable Target parse(String value) { for (var t : values()) if (t.id.equals(value)) return t; return null; }
    }
    /** 相反する効果の組。 */
    public enum Pair {
        ATTACK("attack"), DEFENSE("defense"), MOVE("move"), TEMPO("tempo"), LIFE("life");
        private final String id;
        Pair(String id) { this.id = id; }
        public String id() { return id; }
        public static @Nullable Pair parse(String value) { for (var p : values()) if (p.id.equals(value)) return p; return null; }
    }
    public enum Choice {
        OFF("off"), BOON("boon"), BANE("bane");
        private final String id;
        Choice(String id) { this.id = id; }
        public String id() { return id; }
        public static @Nullable Choice parse(String value) { for (var c : values()) if (c.id.equals(value)) return c; return null; }
    }
    /** §10の常時効果10種。値は熟練度0%と100%の直線補間（表の50%はその中点）。割合か、HP／秒・ダメージ／秒。 */
    public enum Effect {
        ATTACK_UP(Pair.ATTACK, Choice.BOON, .10f, .40f), DEFENSE_UP(Pair.DEFENSE, Choice.BOON, .10f, .30f), MOVE_UP(Pair.MOVE, Choice.BOON, .10f, .30f),
        HASTE(Pair.TEMPO, Choice.BOON, .10f, .30f), REGEN(Pair.LIFE, Choice.BOON, 1, 3),
        ATTACK_DOWN(Pair.ATTACK, Choice.BANE, .10f, .40f), DEFENSE_DOWN(Pair.DEFENSE, Choice.BANE, .10f, .40f), MOVE_DOWN(Pair.MOVE, Choice.BANE, .15f, .45f),
        DELAY(Pair.TEMPO, Choice.BANE, .10f, .40f), DOT(Pair.LIFE, Choice.BANE, 2, 6);
        private final Pair pair; private final Choice side; private final float from, to;
        Effect(Pair pair, Choice side, float from, float to) { this.pair = pair; this.side = side; this.from = from; this.to = to; }
        public Pair pair() { return pair; }
        public Choice side() { return side; }
        public boolean harmful() { return side == Choice.BANE; }
        public float value(float p) { return ArcGen.lerp(from, to, p); }
        public static Effect of(Pair pair, Choice side) {
            for (var e : values()) if (e.pair == pair && e.side == side) return e;
            throw new IllegalArgumentException(pair + "/" + side);
        }
    }
    /** §11の抽選の枠と全種類ON時の重み。 */
    public enum Frame {
        STATUS(35), GLOW(35), LIGHTNING(20), MUTATION(10);
        private final int weight;
        Frame(int weight) { this.weight = weight; }
        public int weight() { return weight; }
    }
    /** §11の現象。状態異常7種は{@link Frame#STATUS}の枠の中から選ぶ。値は表の0%と100%の直線補間。 */
    public enum Phenomenon {
        BURN("burn", Frame.STATUS, 2, 4), POISON("poison", Frame.STATUS, 1, 3), WITHER("wither", Frame.STATUS, 2, 4), SLOW("slow", Frame.STATUS, .2f, .6f),
        WEAK("weak", Frame.STATUS, .2f, .5f), DARK("dark", Frame.STATUS, 0, 0), FLOAT("float", Frame.STATUS, 1, 2),
        GLOW("glow", Frame.GLOW, 0, 0), LIGHTNING("lightning", Frame.LIGHTNING, 8, 16), MUTATION("mutation", Frame.MUTATION, 0, 0);
        private final String id; private final Frame frame; private final float from, to;
        Phenomenon(String id, Frame frame, float from, float to) { this.id = id; this.frame = frame; this.from = from; this.to = to; }
        public String id() { return id; }
        public Frame frame() { return frame; }
        public float value(float p) { return ArcGen.lerp(from, to, p); }
        /** 害のある現象か。発光の輪郭だけが無害で、それ以外はPvP・チームの保護と§15の無効を通す。 */
        public boolean harmful() { return this != GLOW; }
        public static @Nullable Phenomenon parse(String value) { for (var p : values()) if (p.id.equals(value)) return p; return null; }
    }
    /** §11の頻度: 低は間隔2倍、高は半分。 */
    public enum Frequency {
        LOW("low", 2), STANDARD("std", 1), HIGH("high", .5f);
        private final String id; private final float interval;
        Frequency(String id, float interval) { this.id = id; this.interval = interval; }
        public String id() { return id; }
        public float interval() { return interval; }
        public static @Nullable Frequency parse(String value) { for (var f : values()) if (f.id.equals(value)) return f; return null; }
    }
    /** §12の個別プレイヤーの登録。 */
    public enum PlayerMode {
        AUTO("auto"), ALLY("ally"), EXCLUDE("exclude");
        private final String id;
        PlayerMode(String id) { this.id = id; }
        public String id() { return id; }
        public static @Nullable PlayerMode parse(String value) { for (var m : values()) if (m.id.equals(value)) return m; return null; }
    }
    /** 登録したプレイヤー（名前は画面の表示のためだけ）。 */
    public record PlayerEntry(PlayerMode mode, String name) { }
    /** 登録できるプレイヤーの数の上限。 */
    public static final int MAX_PLAYERS = 64;
    private static final String EFFECTS = "effects", PHENOMENA = "phenomena", STATUS_FREQ = "statusFreq", PLAYERS = "players";
    /** 値を変えないキー（画面が送る「全部ちょうだい」と「領域を解除」）は通信の入口が扱う。 */
    public static final String KEY_EFFECT = "effect.", KEY_PHENOMENON = "phen.", KEY_STATUS_FRAME = "frame.status.freq", KEY_PLAYER = "player.";

    private final Choice[][] choices = new Choice[Target.values().length][Pair.values().length];
    private final boolean[] phenomenonOn = new boolean[Phenomenon.values().length];
    private final Frequency[] frequency = new Frequency[Phenomenon.values().length];
    private final int[] targets = new int[Phenomenon.values().length];
    private Frequency statusFrame = Frequency.STANDARD;
    private final Map<UUID, PlayerEntry> players = new LinkedHashMap<>();

    public HeavenSettings() {
        for (var t : Target.values()) for (var p : Pair.values()) choices[t.ordinal()][p.ordinal()] = defaultChoice(t, p);
        for (var ph : Phenomenon.values()) { phenomenonOn[ph.ordinal()] = true; frequency[ph.ordinal()] = Frequency.STANDARD; targets[ph.ordinal()] = defaultTargets(ph); }
    }
    private static Choice defaultChoice(Target target, Pair pair) {
        if (target == Target.BEETLE && pair == Pair.ATTACK) return Choice.BOON;
        if (target == Target.HOSTILE && pair == Pair.MOVE) return Choice.BANE;
        return Choice.OFF;
    }
    private static int defaultTargets(Phenomenon phenomenon) { return phenomenon == Phenomenon.DARK ? 0 : Target.HOSTILE.bit(); }

    public Choice choice(Target target, Pair pair) { return choices[target.ordinal()][pair.ordinal()]; }
    public boolean effectOn(Target target, Effect effect) { return choice(target, effect.pair()) == effect.side(); }
    /** どこかの対象区分でONの効果か（費用は種類ごとに1回で、対象区分の数で増えない）。 */
    public boolean effectSelected(Effect effect) {
        for (var t : Target.values()) if (effectOn(t, effect)) return true;
        return false;
    }
    public boolean phenomenonOn(Phenomenon phenomenon) { return phenomenonOn[phenomenon.ordinal()]; }
    public Frequency frequency(Phenomenon phenomenon) { return frequency[phenomenon.ordinal()]; }
    public Frequency statusFrameFrequency() { return statusFrame; }
    public boolean targets(Phenomenon phenomenon, Target target) { return (targets[phenomenon.ordinal()] & target.bit()) != 0; }
    /** この現象がONで、この対象区分を選んでいるか。 */
    public boolean enabled(Phenomenon phenomenon, Target target) { return phenomenonOn(phenomenon) && targets(phenomenon, target); }
    /** 抽選の枠の頻度。状態異常の枠は枠全体の設定、他は現象の設定。 */
    public Frequency frameFrequency(Frame frame) {
        return switch (frame) {
            case STATUS -> statusFrame;
            case GLOW -> frequency(Phenomenon.GLOW);
            case LIGHTNING -> frequency(Phenomenon.LIGHTNING);
            case MUTATION -> frequency(Phenomenon.MUTATION);
        };
    }
    public PlayerMode playerMode(UUID player) { var entry = players.get(player); return entry == null ? PlayerMode.AUTO : entry.mode(); }
    public Map<UUID, PlayerEntry> players() { return Collections.unmodifiableMap(players); }

    /** 組み合わせが許されるか: 自分の甲虫へ害のある選択はできない（§12: 誤射保護を領域で迂回しない）。 */
    public static boolean allowed(Target target, Pair pair, Choice choice) { return target != Target.BEETLE || choice != Choice.BANE; }
    /** 組み合わせが許されるか: 自分と自分の甲虫へは害のある現象を選べない（同じ保護）。変異は§11のとおりモブだけで、自分の区分には使えない。 */
    public static boolean allowed(Phenomenon phenomenon, Target target) {
        if (phenomenon == Phenomenon.DARK) return target == Target.SELF || target == Target.ALLY || target == Target.OTHER;
        if (phenomenon == Phenomenon.MUTATION) return target != Target.SELF && target != Target.BEETLE;
        return target != Target.BEETLE || !phenomenon.harmful();
    }
    public boolean setChoice(Target target, Pair pair, Choice choice) {
        if (!allowed(target, pair, choice) || choice(target, pair) == choice) return false;
        choices[target.ordinal()][pair.ordinal()] = choice;
        return true;
    }
    public boolean setPhenomenon(Phenomenon phenomenon, boolean on) {
        if (phenomenonOn(phenomenon) == on) return false;
        phenomenonOn[phenomenon.ordinal()] = on;
        return true;
    }
    public boolean setFrequency(Phenomenon phenomenon, Frequency value) {
        if (frequency(phenomenon) == value) return false;
        frequency[phenomenon.ordinal()] = value;
        return true;
    }
    public boolean setStatusFrameFrequency(Frequency value) {
        if (statusFrame == value) return false;
        statusFrame = value;
        return true;
    }
    public boolean setTarget(Phenomenon phenomenon, Target target, boolean on) {
        if (targets(phenomenon, target) == on || (on && !allowed(phenomenon, target))) return false;
        targets[phenomenon.ordinal()] ^= target.bit();
        return true;
    }
    /** 個別プレイヤーを登録する。自動判定に戻すと一覧から外れる。上限を超える新規の登録は断る。 */
    public boolean setPlayer(UUID player, PlayerMode mode, String name) {
        if (mode == PlayerMode.AUTO) return players.remove(player) != null;
        var old = players.get(player);
        if (old != null && old.mode() == mode) return false;
        if (old == null && players.size() >= MAX_PLAYERS) return false;
        players.put(player, new PlayerEntry(mode, name.length() > 16 ? name.substring(0, 16) : name));
        return true;
    }
    /**
     * 画面からの変更を文字列で適用する（個別プレイヤーは{@link #setPlayer}を通信の入口が呼ぶ）。キーは{@code effect.対象.組}、{@code phen.現象.on}、{@code phen.現象.freq}、
     * {@code phen.現象.target.対象}、{@code frame.status.freq}。変わったらtrue。知らないキーと値は何も変えない。
     */
    public boolean apply(String key, String value) {
        if (KEY_STATUS_FRAME.equals(key)) { var f = Frequency.parse(value); return f != null && setStatusFrameFrequency(f); }
        String[] parts = key.split("\\.");
        if (key.startsWith(KEY_EFFECT) && parts.length == 3) {
            var target = Target.parse(parts[1]); var pair = Pair.parse(parts[2]); var choice = Choice.parse(value);
            return target != null && pair != null && choice != null && setChoice(target, pair, choice);
        }
        if (!key.startsWith(KEY_PHENOMENON) || parts.length < 3) return false;
        var phenomenon = Phenomenon.parse(parts[1]);
        if (phenomenon == null) return false;
        Boolean on = AbilitySettings.ON.equals(value) ? Boolean.TRUE : AbilitySettings.OFF.equals(value) ? Boolean.FALSE : null;
        if (parts.length == 3 && "on".equals(parts[2])) return on != null && setPhenomenon(phenomenon, on);
        if (parts.length == 3 && "freq".equals(parts[2])) { var f = Frequency.parse(value); return f != null && setFrequency(phenomenon, f); }
        if (parts.length == 4 && "target".equals(parts[2])) { var target = Target.parse(parts[3]); return target != null && on != null && setTarget(phenomenon, target, on); }
        return false;
    }

    /** 既定と違う値だけを書く。全部既定なら空の区分。 */
    public CompoundTag save() {
        var out = new CompoundTag();
        var effects = new CompoundTag();
        for (var t : Target.values()) {
            var row = new CompoundTag();
            for (var p : Pair.values()) if (choice(t, p) != defaultChoice(t, p)) row.putString(p.id(), choice(t, p).id());
            if (!row.isEmpty()) effects.put(t.id(), row);
        }
        if (!effects.isEmpty()) out.put(EFFECTS, effects);
        var phenomena = new CompoundTag();
        for (var ph : Phenomenon.values()) {
            var row = new CompoundTag();
            if (!phenomenonOn(ph)) row.putBoolean("on", false);
            if (frequency(ph) != Frequency.STANDARD) row.putString("freq", frequency(ph).id());
            if (targets[ph.ordinal()] != defaultTargets(ph)) row.putInt("targets", targets[ph.ordinal()]);
            if (!row.isEmpty()) phenomena.put(ph.id(), row);
        }
        if (!phenomena.isEmpty()) out.put(PHENOMENA, phenomena);
        if (statusFrame != Frequency.STANDARD) out.putString(STATUS_FREQ, statusFrame.id());
        if (!players.isEmpty()) {
            var list = new ListTag();
            players.forEach((id, entry) -> {
                var row = new CompoundTag();
                row.store("id", net.minecraft.core.UUIDUtil.CODEC, id); row.putString("mode", entry.mode().id()); row.putString("name", entry.name());
                list.add(row);
            });
            out.put(PLAYERS, list);
        }
        return out;
    }
    /** {@link #save}の形を読む。読めない値は既定のまま。 */
    public static HeavenSettings load(CompoundTag tag) {
        var out = new HeavenSettings();
        var effects = tag.getCompoundOrEmpty(EFFECTS);
        for (var t : Target.values()) {
            var row = effects.getCompoundOrEmpty(t.id());
            for (var p : Pair.values()) {
                var choice = Choice.parse(row.getStringOr(p.id(), ""));
                if (choice != null && allowed(t, p, choice)) out.choices[t.ordinal()][p.ordinal()] = choice;
            }
        }
        var phenomena = tag.getCompoundOrEmpty(PHENOMENA);
        for (var ph : Phenomenon.values()) {
            var row = phenomena.getCompoundOrEmpty(ph.id());
            if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(row, "on", Tag.TAG_BYTE)) out.phenomenonOn[ph.ordinal()] = row.getBooleanOr("on", false);
            var frequency = Frequency.parse(row.getStringOr("freq", ""));
            if (frequency != null) out.frequency[ph.ordinal()] = frequency;
            if (io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(row, "targets", Tag.TAG_INT)) {
                int mask = 0;
                for (var t : Target.values()) if ((row.getIntOr("targets", 0) & t.bit()) != 0 && allowed(ph, t)) mask |= t.bit();
                out.targets[ph.ordinal()] = mask;
            }
        }
        var frame = Frequency.parse(tag.getStringOr(STATUS_FREQ, ""));
        if (frame != null) out.statusFrame = frame;
        var list = tag.getListOrEmpty(PLAYERS);
        for (int i = 0; i < Math.min(MAX_PLAYERS, list.size()); i++) {
            var row = list.getCompoundOrEmpty(i);
            var mode = PlayerMode.parse(row.getStringOr("mode", ""));
            // 1.21.1 の hasUUID・getUUID と同じ形（整数4つの配列）を読む。
            var id = row.read("id", net.minecraft.core.UUIDUtil.CODEC);
            if (id.isPresent() && mode != null && mode != PlayerMode.AUTO) out.players.put(id.get(), new PlayerEntry(mode, row.getStringOr("name", "")));
        }
        return out;
    }
    /** 枠ごとの重みの表（試験と抽選で使う）。 */
    public static Map<Frame, Integer> weights() {
        var out = new EnumMap<Frame, Integer>(Frame.class);
        for (var frame : Frame.values()) out.put(frame, frame.weight());
        return out;
    }
}
