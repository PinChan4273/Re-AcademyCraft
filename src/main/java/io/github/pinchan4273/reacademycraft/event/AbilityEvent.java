package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * 原作の能力イベント（cn.academy.event.ability）。プレイヤーの能力が変わったとき、サーバーでPlayerAbilityDataが
 * MinecraftForge.EVENT_BUSへ送る。原作はデータ部分のある各sideで送っていたが、ここではデータを持つサーバーが送る。
 * プレイヤーのデータの読み込みや写し（ログイン、リスポーン）では何も送らない（原作のNBT読み込みも何も送らなかった）。
 */
public abstract class AbilityEvent extends Event {
    private final Player player;
    protected AbilityEvent(Player player) { this.player = player; }
    public Player player() { return player; }

    /** 原作AbilityActivateEvent: 能力がオンになった。 */
    public static final class Activate extends AbilityEvent { public Activate(Player p) { super(p); } }
    /** 原作AbilityDeactivateEvent: 能力がオフになった。 */
    public static final class Deactivate extends AbilityEvent { public Deactivate(Player p) { super(p); } }
    /** 原作CategoryChangeEvent: プレイヤーのカテゴリが別のもの、または無しになった。 */
    public static final class CategoryChange extends AbilityEvent { public CategoryChange(Player p) { super(p); } }
    /**
     * 原作TransformCategoryEvent: 開発機のリセットがプレイヤーを1つ下のレベルの別カテゴリへ変えようとしている。
     * 何かが変わる前に送られ、取り消すと変換を中止する（原作と同じ）。
     */
    @net.minecraftforge.eventbus.api.Cancelable
    public static final class TransformCategory extends AbilityEvent {
        private final ResourceLocation category; private final int level;
        public TransformCategory(Player p, ResourceLocation category, int level) { super(p); this.category = category; this.level = level; }
        public ResourceLocation category() { return category; }
        public int level() { return level; }
    }
    /** 原作LevelChangeEvent: プレイヤーのレベルが変わった。 */
    public static final class LevelChange extends AbilityEvent { public LevelChange(Player p) { super(p); } }
    /** 原作SkillLearnEvent: 技能を新しく習得した。 */
    public static final class SkillLearn extends AbilityEvent {
        private final ResourceLocation skill;
        public SkillLearn(Player p, ResourceLocation skill) { super(p); this.skill = skill; }
        public ResourceLocation skill() { return skill; }
    }
    /** 原作SkillExpAddedEvent: プレイで得た経験値（加工前の量）。 */
    public static final class SkillExpAdded extends AbilityEvent {
        private final ResourceLocation skill; private final float amount;
        public SkillExpAdded(Player p, ResourceLocation skill, float amount) { super(p); this.skill = skill; this.amount = amount; }
        public ResourceLocation skill() { return skill; }
        public float amount() { return amount; }
    }
    /** 原作SkillExpChangedEvent: 理由を問わず、技能の経験値が変わった。 */
    public static final class SkillExpChanged extends AbilityEvent {
        private final ResourceLocation skill;
        public SkillExpChanged(Player p, ResourceLocation skill) { super(p); this.skill = skill; }
        public ResourceLocation skill() { return skill; }
    }
    /** 原作OverloadEvent: プレイヤーがオーバーロードした。 */
    public static final class Overload extends AbilityEvent { public Overload(Player p) { super(p); } }
    /** 原作PresetSwitchEvent: 別のプリセットが現在のものになった。 */
    public static final class PresetSwitch extends AbilityEvent { public PresetSwitch(Player p) { super(p); } }
    /** 原作PresetUpdateEvent: プリセットのスロットが別の技能になった。 */
    public static final class PresetUpdate extends AbilityEvent { public PresetUpdate(Player p) { super(p); } }
    /** 原作CalcEventのプレイヤーの計算: リスナーが変更できる値。 */
    public abstract static class Calc extends AbilityEvent {
        public float value;
        protected Calc(Player p, float value) { super(p); this.value = value; }
    }
    /** 原作CalcEvent.MaxCP: レベルの初期最大CP（コースのボーナスを含む）。 */
    public static final class MaxCP extends Calc { public MaxCP(Player p, float value) { super(p, value); } }
    /** 原作CalcEvent.MaxOverload: レベルの初期最大オーバーロード（コースのボーナスを含む）。 */
    public static final class MaxOverload extends Calc { public MaxOverload(Player p, float value) { super(p, value); } }
    /** 原作CalcEvent.CPRecoverSpeed: CP回復の倍率。最初は1。 */
    public static final class CPRecoverSpeed extends Calc { public CPRecoverSpeed(Player p, float value) { super(p, value); } }
    /** 原作CalcEvent.OverloadRecoverSpeed: オーバーロード回復の倍率。最初は1。 */
    public static final class OverloadRecoverSpeed extends Calc { public OverloadRecoverSpeed(Player p, float value) { super(p, value); } }
    /** 原作CalcEvent.SkillPerform: 技能がCPとオーバーロードを使おうとしている。リスナーはその量を変更できる。 */
    public static final class SkillPerform extends AbilityEvent {
        public float cp, overload;
        public SkillPerform(Player p, float overload, float cp) { super(p); this.cp = cp; this.overload = overload; }
    }
    /** 原作CalcEvent.SkillAttack: 技能が対象へ与えようとしているダメージ（設定のダメージ倍率を掛ける前）。リスナーが変更できる。 */
    public static final class SkillAttack extends AbilityEvent {
        private final ResourceLocation skill; private final Entity target;
        public float value;
        public SkillAttack(Player p, ResourceLocation skill, Entity target, float value) { super(p); this.skill = skill; this.target = target; this.value = value; }
        public ResourceLocation skill() { return skill; }
        public Entity target() { return target; }
    }
}
