package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingAttackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 1.5.2: 電撃使いと原子崩しの干渉。
 *
 * <p>原作では、電撃使い（御坂美琴）と原子崩し（麦野沈利）は、どちらも電子にかかわる能力で、戦いの中で互いの能力が干渉して
 * 打ち消し合っていた。電気を操る能力者として、落雷も受けない。これを次のように再現する。
 * 働くのは、守る側のプレイヤーが能力を使える間（能力使用モードがONで、過負荷のロックや能力妨害機の干渉が無い。{@link PlayerAbilityData#canUseAbility()}）だけ。</p>
 * <ul>
 * <li>電撃使いと原子崩しの能力者は、バニラの落雷のダメージ（{@link DamageTypes#LIGHTNING_BOLT}）を受けない。</li>
 * <li>電撃使いは原子崩しの能力のダメージを、原子崩しは電撃使いの能力のダメージを無効にする。ただし、次の技能は通る
 * （{@link #ELECTROMASTER_PASSES}・{@link #MELTDOWNER_PASSES}）。</li>
 * <li>同じ系統どうし（電撃使いどうし、原子崩しどうし）は、ふつうに通る。</li>
 * <li>天界発現の落雷は未元物質の能力のダメージ（{@code #academy:ability/dark_matter}）なので、対象外。</li>
 * </ul>
 * <p>能力の見分けは、ダメージの種類の能力のタグ（{@link SkillDamageSource#abilityOf}）で行う。通る技能は、本体の技能のIDで見分ける
 * （他のMODの名前やIDは見ない）。防御の技能が CP を払う前に打ち消すよう、未元物質人間の無効と同じく攻撃の入口（最優先）で取り消す。</p>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class AbilityInterference {
    /*
     * IDは定数を参照せず直接書く: このクラスはイベントの購読の登録（MODの構築中）で初期化されるので、技能のクラスの初期化を
     * 巻き込まない。各技能のIDと同じ値であることはテスト（V152DamageGameTests）で確かめる。
     */
    /** 電撃使いのカテゴリ（{@link ArcGen#CATEGORY}と同じ）。 */
    public static final Identifier ELECTROMASTER = id("electromaster");
    /** 原子崩しのカテゴリ。 */
    public static final Identifier MELTDOWNER = id("meltdowner");
    /**
     * 原子崩しの能力者にも通る電撃使いの技能: 超電磁砲と、磁力操作（{@code academy:mag_manip}）。磁力操作のダメージは、飛ばした
     * ブロック（{@code MagneticBlockEntity}）が術者と磁力操作のIDで当てるので、IDで見分けられる（持っている間の接触も同じIDで通る）。
     */
    public static final Set<Identifier> ELECTROMASTER_PASSES = Set.of(id("railgun"), id("mag_manip"));
    /**
     * 電撃使いの能力者にも通る原子崩しの技能: 原子崩し（メルトダウナー）、レイバラージ、ライト・シールドの接触、
     * ジェット・エンジンの接触（推進中にぶつかった相手へのダメージ）。ライト・シールドとジェット・エンジンのダメージは接触だけなので、IDで見分ける。
     */
    public static final Set<Identifier> MELTDOWNER_PASSES = Set.of(id("meltdowner"), id("ray_barrage"), id("light_shield"), id("jet_engine"));

    private AbilityInterference() { }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", path); }

    /** 干渉は防御より先（未元物質人間の無効と同じ最優先）。取り消された攻撃は、防御の技能が CP を払うこともない。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true) public static void attacked(AcademyLivingAttackEvent event) {
        PierceHits.attack(event, AbilityInterference::attackedBody);
    }
    /** 干渉の本体（1.5.2: 貫通の打撃では、他の Mod の取り消しの後も {@link PierceHits} を通して動く）。 */
    private static void attackedBody(AcademyLivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (nullifies(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    /**
     * 落雷そのものを受けない: 打たれたことを取り消し、落雷の火や変化も起こさない（ダメージの無効だけでは、その後の燃焼が残るため。
     * 1.5.2から）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void struck(net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent event) {
        if (!event.getEntity().level().isClientSide() && interferingCategory(event.getEntity() instanceof LivingEntity living ? living : null) != null) event.setCanceled(true);
    }

    /** このダメージを、守る側の能力の干渉で無効にするか。 */
    public static boolean nullifies(LivingEntity victim, @Nullable DamageSource source) {
        if (source == null) return false;
        var defender = interferingCategory(victim);
        if (defender == null) return false;
        if (source.is(DamageTypes.LIGHTNING_BOLT)) return true;
        var attacker = SkillDamageSource.abilityOf(source);
        if (attacker == null || attacker.equals(defender)) return false;
        if (defender.equals(ELECTROMASTER) && attacker.equals(MELTDOWNER)) return !passes(source, MELTDOWNER_PASSES);
        if (defender.equals(MELTDOWNER) && attacker.equals(ELECTROMASTER)) return !passes(source, ELECTROMASTER_PASSES);
        return false;
    }

    /** 干渉を押し切る技能のダメージか（本体の技能のダメージ源だけを見る）。 */
    private static boolean passes(DamageSource source, Set<Identifier> skills) {
        return source instanceof SkillDamageSource skill && skills.contains(skill.skill());
    }

    /** 干渉が働いている守る側の能力: 能力使用モードがONの電撃使いか原子崩しのプレイヤーなら、そのカテゴリ。それ以外はnull。 */
    public static @Nullable Identifier interferingCategory(@Nullable LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return null;
        var data = AbilityCapabilities.of(player).orElse(null);
        // 過負荷のロックや能力妨害機で能力が使えない間は、干渉も働かない。
        if (data == null || data.isReadOnly() || !data.canUseAbility()) return null;
        var ability = data.getAbility();
        return ELECTROMASTER.equals(ability) || MELTDOWNER.equals(ability) ? ability : null;
    }
}
