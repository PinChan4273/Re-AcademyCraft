package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 原作SkillDamageSourceに対する、Forge 1.20.1の動的なダメージ型のアダプタ。
 * 術者の帰属、防具とForgeのダメージイベント、被害者・術者・技能の死亡文を保つ。
 *
 * <p>1.5.2から、ダメージの種類は「能力（カテゴリ）× 当たり方」で選ぶ。本体の5つのカテゴリの技能は
 * {@code academy:<カテゴリ>}と{@code academy:<カテゴリ>_<当たり方>}（例: {@code academy:electromaster}・
 * {@code academy:dark_matter_pierce}）で当て、どの種類にも能力のタグ{@code #academy:ability/<カテゴリ>}が付く。
 * 当たり方は Re のタグ{@code #academy:hit/contact}・{@code stab}・{@code pierce}・{@code ignore_armor}で表し、
 * バニラの防御を飛ばすタグ（bypasses_armorなど）には、当たり方のタグを通して1.5.1と同じ効き目になるように入れている。
 * 本体のカテゴリに属さない技能（アドオンのカテゴリの技能など）は、以前からの共通の5つの種類（{@link #TYPE}など）で当てる。
 * 共通の5つの種類は、アドオンやデータパックが参照していることがあるので、登録したまま残す。</p>
 */
public final class SkillDamageSource extends DamageSource {
    /**
     * 当たり方（1.5.2）。種類の名前の後ろに付ける文字列と、Re の当たり方のタグを持つ。{@link #PLAIN}はタグを持たない
     * （防具・盾・被ダメージ後の待ちなどはふつうに効く）。
     */
    public enum Style {
        /** ふつうの当たり方。 */
        PLAIN("", null),
        /** 接触（ライト・シールド、ジェット・エンジン、未元空気砲、甲虫、天界発現の接触）。被ダメージ後の待ちだけを飛ばす。 */
        CONTACT("_contact", "contact"),
        /** 刺突（1.5.1 の白翼刺突の熟練度50%未満の打撃）。被ダメージ後の待ちだけを飛ばす。1.5.2 から白翼刺突はいつも{@link #PIERCE}で、この種類は登録だけ残す。 */
        STAB("_stab", "stab"),
        /**
         * 貫通（1.5.2）。いつも貫通で当てる技能（{@link #PIERCING_SKILLS}と甲虫の近接・突進）の打撃。盾・防具・防護エンチャント・耐性・効果・
         * 被ダメージ後の待ちをタグで飛ばし、吸収と他の Mod の守りは{@link PierceHits}が外す。Re 自身の防御は効く（白翼刺突だけは通る）。
         * 他の Mod の守りをどこまで外すかは、技能ごとの段（{@link PierceTier}）で決まる。
         */
        PIERCE("_pierce", "pierce"),
        /** 原作の防具を無視する打撃（盾と防具を飛ばし、空腹のコストも無い）。 */
        IGNORE_ARMOR("_ignore_armor", "ignore_armor");
        private final String suffix;
        private final @Nullable TagKey<DamageType> tag;
        Style(String suffix, @Nullable String hit) {
            this.suffix = suffix;
            this.tag = hit == null ? null : TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "hit/" + hit));
        }
        /** 種類の名前の後ろに付ける文字列（ふつうは空）。 */
        public String suffix() { return suffix; }
        /** Re の当たり方のタグ（ふつうはnull）。 */
        public @Nullable TagKey<DamageType> tag() { return tag; }
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("academy", path));
    }
    private static TagKey<DamageType> tag(String path) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("academy", path));
    }

    /** 共通のふつうの種類（1.5.1まではすべての技能の既定。1.5.2からは本体のカテゴリに属さない技能の既定）。 */
    public static final ResourceKey<DamageType> TYPE = key("skill");
    /**
     * 原作SkillDamageSource.setDamageBypassesArmor(): isUnblockableで、1.12では防具と盾を飛ばし、空腹のコストも無い。
     * 1.20.1ではこれがbypasses_armorとbypasses_shieldのタグに分かれ、この型はその両方に属し、exhaustionは0。
     * エンチャントの防護とResistanceは、1.12の防げないダメージ源と同じく引き続き効く。
     * 1.5.2からは共通の種類（本体のカテゴリに属さない技能用）で、バニラのタグは{@code #academy:hit/ignore_armor}を通して付く。
     */
    public static final ResourceKey<DamageType> IGNORE_ARMOR = key("skill_ignore_armor");
    /**
     * Light ShieldとJet Engineの接触攻撃だけの型。中身は{@link #TYPE}と同じ（防具は効く）で、
     * minecraft:bypasses_cooldownのタグにだけ属する: 同じ対象への8tickごとの命中が、標準の被ダメージ後の待ち（invulnerableTime &gt; 10）で
     * 削られないようにする。bypasses_invulnerabilityには入れないので、クリエイティブなどの本当の無敵、Forgeの攻撃イベントの取り消し、
     * 防具・Resistance・エンチャントはそのまま効く。共通の{@link #TYPE}には何も足さない（他の技能と通常の武器は従来どおり）。
     * 1.5.2からは共通の種類（本体のカテゴリに属さない技能用）で、バニラのタグは{@code #academy:hit/contact}を通して付く。
     */
    public static final ResourceKey<DamageType> CONTACT = key("skill_contact");
    /**
     * v1.5.0の白翼刺突の、熟練度50%未満の打撃。中身は{@link #TYPE}と同じ（防具は効く）で、
     * minecraft:bypasses_cooldownにだけ属する: 0.4〜0.6秒（8〜12tick）の連撃が標準の被ダメージ後の待ちで削られないようにする。
     * 1.5.2からは共通の種類で、バニラのタグは{@code #academy:hit/stab}を通して付く。
     */
    public static final ResourceKey<DamageType> STAB = key("skill_stab");
    /**
     * 貫通の打撃の共通の種類（本体のカテゴリに属さない技能用）。盾・防具・防具強度・防護エンチャント・耐性（バニラと技能の直接効果）・被ダメージ後の待ちを
     * 通らない（minecraft:bypasses_armor・bypasses_shield・bypasses_effects・bypasses_enchantments・bypasses_resistance・bypasses_cooldown。
     * {@code #academy:hit/pierce}を通して付く）。吸収と他の Mod の守りは{@link PierceHits}が外す。
     * bypasses_invulnerabilityには入れない: クリエイティブなどの本当の無敵が効き、バニラの不死のトーテムも効く（1.5.2）。
     * Re 自身の防御（ベクトル反射・偏向、ライト・シールド、ジェットの前面、翼の防御など）は効く。白翼刺突だけは{@link #bypassesReDefenses}で通る。
     */
    public static final ResourceKey<DamageType> PIERCE = key("skill_pierce");

    /** 接触の当たり方のタグ（1.5.2）。共通の{@link #CONTACT}と、各カテゴリの{@code _contact}の種類が入る。 */
    public static final TagKey<DamageType> HIT_CONTACT = Style.CONTACT.tag();
    /** 刺突の当たり方のタグ（1.5.2）。 */
    public static final TagKey<DamageType> HIT_STAB = Style.STAB.tag();
    /** 貫通の当たり方のタグ（1.5.2）。{@link #isPierce}はこれを見る。 */
    public static final TagKey<DamageType> HIT_PIERCE = Style.PIERCE.tag();
    /** 防具を無視する当たり方のタグ（1.5.2）。 */
    public static final TagKey<DamageType> HIT_IGNORE_ARMOR = Style.IGNORE_ARMOR.tag();
    /** Re の技能のダメージの種類すべて（共通の5つと、能力ごとの種類）のタグ（1.5.2）。 */
    public static final TagKey<DamageType> SKILL_DAMAGE = tag("skill_damage");

    /**
     * 能力ごとの種類を持つ、本体のカテゴリ（1.5.2）。IDの道が、そのまま種類の名前の頭と能力のタグの名前になる。
     * クラスの初期化を他の技能に結び付けないよう、定数ではなくIDを直接書く（{@link ArcGen#CATEGORY}・{@link DarkMatter#CATEGORY}と同じ値）。
     */
    public static final List<ResourceLocation> TYPED_CATEGORIES = List.of(
            ResourceLocation.fromNamespaceAndPath("academy", "electromaster"), ResourceLocation.fromNamespaceAndPath("academy", "meltdowner"),
            ResourceLocation.fromNamespaceAndPath("academy", "teleporter"), ResourceLocation.fromNamespaceAndPath("academy", "vecmanip"),
            ResourceLocation.fromNamespaceAndPath("academy", "dark_matter"));

    /** 本体のカテゴリの能力のタグ（{@code #academy:ability/<カテゴリ>}）。本体のカテゴリでなければnull。 */
    public static @Nullable TagKey<DamageType> abilityTag(@Nullable ResourceLocation category) {
        return category != null && TYPED_CATEGORIES.contains(category) ? tag("ability/" + category.getPath()) : null;
    }
    /** 技能が属する本体のカテゴリ。登録に無い技能、カテゴリの無い汎用コース、アドオンのカテゴリの技能はnull。 */
    public static @Nullable ResourceLocation typedCategory(ResourceLocation skill) {
        var definition = SkillCatalog.find(skill);
        return definition != null && definition.category() != null && TYPED_CATEGORIES.contains(definition.category()) ? definition.category() : null;
    }
    /** 共通の種類（本体のカテゴリに属さない技能用。1.5.1までの5つ）。 */
    public static ResourceKey<DamageType> genericType(Style style) { return key("skill" + style.suffix()); }
    /** この技能をこの当たり方で当てるときの種類: 本体のカテゴリなら能力ごとの種類、それ以外は共通の種類。 */
    public static ResourceKey<DamageType> typeKey(ResourceLocation skill, Style style) {
        var category = typedCategory(skill);
        return category == null ? genericType(style) : key(category.getPath() + style.suffix());
    }
    /**
     * {@link #typeKey}の種類の登録の実体。データパックで能力ごとの種類が消されていたら、共通の種類へ戻す
     * （共通の種類も無ければ、以前と同じく失敗する）。
     */
    public static Holder<DamageType> type(RegistryAccess access, ResourceLocation skill, Style style) {
        var registry = access.registryOrThrow(Registries.DAMAGE_TYPE);
        return registry.getHolder(typeKey(skill, style)).<Holder<DamageType>>map(h -> h).orElseGet(() -> registry.getHolderOrThrow(genericType(style)));
    }
    /** ダメージの能力（本体のカテゴリ）: 能力のタグから読む。本体の能力のダメージでなければnull。 */
    public static @Nullable ResourceLocation abilityOf(@Nullable DamageSource source) {
        if (source == null) return null;
        for (var category : TYPED_CATEGORIES) if (source.is(abilityTag(category))) return category;
        return null;
    }

    /**
     * いつも貫通で当てる技能（1.5.2）: レールガン、メルトダウナー（原子崩し）、血源逆行、プラズマ砲、白翼刺突、
     * スレットニング・テレポート、シフト・テレポート、フレッシュ・リッピング、天界発現。熟練度に関係なく、これらの技能のダメージは{@link Style#PIERCE}になる
     * （{@link #styleFor}）。ダークマターのカブトムシは近接と突進だけが貫通なので、ここには入れず{@link SkillCombat#attackVia}が決める。
     * クラスの初期化を他の技能に結び付けないよう、IDを直接書く（各技能の{@code ID}と同じ値であることはテストで確かめる）。
     */
    public static final java.util.Set<ResourceLocation> PIERCING_SKILLS = java.util.Set.of(
            ResourceLocation.fromNamespaceAndPath("academy", "railgun"), ResourceLocation.fromNamespaceAndPath("academy", "meltdowner"),
            ResourceLocation.fromNamespaceAndPath("academy", "blood_retro"), ResourceLocation.fromNamespaceAndPath("academy", "plasma_cannon"),
            ResourceLocation.fromNamespaceAndPath("academy", "white_wing_stab"), ResourceLocation.fromNamespaceAndPath("academy", "threatening_teleport"),
            ResourceLocation.fromNamespaceAndPath("academy", "shift_tp"), ResourceLocation.fromNamespaceAndPath("academy", "flesh_ripping"),
            ResourceLocation.fromNamespaceAndPath("academy", "heaven_manifest"));
    /** Re 自身の防御も通る、ただ1つの技能（白翼刺突）。 */
    private static final ResourceLocation WHITE_WING_STAB = ResourceLocation.fromNamespaceAndPath("academy", "white_wing_stab");
    /** この技能をこの当たり方で当てるときの、実際の当たり方: いつも貫通の技能なら{@link Style#PIERCE}、それ以外は頼まれた当たり方。 */
    public static Style styleFor(ResourceLocation skill, Style requested) { return PIERCING_SKILLS.contains(skill) ? Style.PIERCE : requested; }
    /**
     * 貫通の段（1.5.2）。番号は 2・3・4 を使い、段 1 は使わない。どの段の打撃も、バニラの守り（盾、防具と防具強度、
     * 防護のエンチャント、耐性、衝撃吸収）と被ダメージ後の無敵時間を通る。段は技能ごとに決まり、熟練度では変わらない（{@link #PIERCE_TIERS}）。
     * Re 自身の防御は、どの段にも今までどおり働く。
     */
    public enum PierceTier {
        /** 段 2 の通常貫通。ほかの MOD が Forge のイベントで行った取り消し・軽減と、死亡の取り消しを外さない。 */
        NORMAL(2),
        /** 段 3 の上位貫通。ほかの MOD の取り消し・軽減は外し、死亡の取り消しは外さない。 */
        HIGH(3),
        /** 段 4 の完全貫通。ほかの MOD の取り消し・軽減と、死亡の取り消しを外す。 */
        FULL(4);
        private final int level;
        PierceTier(int level) { this.level = level; }
        /** 段の番号（2・3・4）。 */
        public int level() { return level; }
        /** ほかの MOD が Forge のイベントで行った攻撃とダメージの計算の取り消し、ダメージの軽減、被ダメージ後の待ちの引き下げを外すか（段 3 と段 4）。 */
        public boolean overridesModGuards() { return this != NORMAL; }
        /** ほかの MOD の死亡の取り消しを外すか（段 4 だけ）。 */
        public boolean overridesModDeathCancel() { return this == FULL; }
    }
    /**
     * 技能ごとの貫通の段（1.5.2）。いつも貫通の9技能（{@link #PIERCING_SKILLS}）と、カブトムシ（近接と突進だけが貫通）の段を持つ。
     * ここに無い技能の貫通の打撃は段 4 とする（{@link #tierOf}）。クラスの初期化を他の技能に結び付けないよう、IDを直接書く。
     */
    public static final java.util.Map<ResourceLocation, PierceTier> PIERCE_TIERS = java.util.Map.of(
            ResourceLocation.fromNamespaceAndPath("academy", "dark_matter_beetle"), PierceTier.FULL,
            ResourceLocation.fromNamespaceAndPath("academy", "shift_tp"), PierceTier.FULL,
            ResourceLocation.fromNamespaceAndPath("academy", "white_wing_stab"), PierceTier.FULL,
            ResourceLocation.fromNamespaceAndPath("academy", "heaven_manifest"), PierceTier.FULL,
            ResourceLocation.fromNamespaceAndPath("academy", "blood_retro"), PierceTier.FULL,
            ResourceLocation.fromNamespaceAndPath("academy", "railgun"), PierceTier.HIGH,
            ResourceLocation.fromNamespaceAndPath("academy", "flesh_ripping"), PierceTier.HIGH,
            ResourceLocation.fromNamespaceAndPath("academy", "meltdowner"), PierceTier.HIGH,
            ResourceLocation.fromNamespaceAndPath("academy", "plasma_cannon"), PierceTier.HIGH,
            ResourceLocation.fromNamespaceAndPath("academy", "threatening_teleport"), PierceTier.NORMAL);
    /** この技能の貫通の段。{@link #PIERCE_TIERS}に無い技能は段 4。 */
    public static PierceTier tierOf(ResourceLocation skill) { return PIERCE_TIERS.getOrDefault(skill, PierceTier.FULL); }
    /**
     * このダメージの元の貫通の段（貫通の打撃のときだけ意味を持つ）。派生の打撃（反射で折り返した光線と、貫通の近接への打ち返し）は、
     * 元の打撃の段を{@link #inheritPierceTier}で持つ。持たない Re の技能の打撃は技能の段、天界発現の状態異常は天界発現の段、それ以外は段 4。
     */
    public static PierceTier pierceTier(@Nullable DamageSource source) {
        if (source instanceof SkillDamageSource skill) return skill.pierceTier != null ? skill.pierceTier : tierOf(skill.skill());
        if (source instanceof HeavenPhenomena.StatusSource status) return tierOf(status.skill());
        return PierceTier.FULL;
    }
    /** 貫通の打撃か（{@code #academy:hit/pierce}）。他の Mod の守りは{@link PierceHits}が外す。 */
    public static boolean isPierce(@Nullable DamageSource source) { return source != null && source.is(HIT_PIERCE); }
    /**
     * Re 自身の防御（ライト・シールド、ジェットの前面、翼の防御、天界発現の防御の強化）も通る打撃か: 白翼刺突の貫通の打撃だけ
     * （1.5.2。1.5.1 までの{@code ignoresDefenses}は、すべての貫通の打撃がこれだった）。ベクトル反射・偏向は、未元物質に直接触れる攻撃として
     * 今までどおり軽減を50%までにする（{@link VectorAnalysis#directContact}）。
     */
    public static boolean bypassesReDefenses(@Nullable DamageSource source) {
        return isPierce(source) && source instanceof SkillDamageSource skill && WHITE_WING_STAB.equals(skill.skill());
    }
    /** 接触の打撃か（{@code #academy:hit/contact}）。 */
    public static boolean isContact(@Nullable DamageSource source) { return source != null && source.is(HIT_CONTACT); }
    /** 刺突の打撃か（{@code #academy:hit/stab}）。 */
    public static boolean isStab(@Nullable DamageSource source) { return source != null && source.is(HIT_STAB); }
    /** 防具を無視する打撃か（{@code #academy:hit/ignore_armor}）。 */
    public static boolean isIgnoreArmor(@Nullable DamageSource source) { return source != null && source.is(HIT_IGNORE_ARMOR); }
    private final ResourceLocation skill;
    /**
     * 未元物質に直接触れる攻撃のフラグ（1.5.2。甲虫の近接と突進）。白翼刺突は技能のIDで分かるので立てない
     * （{@link VectorAnalysis#directContact}）。1.5.1までの「50%以上の白翼刺突と未元光線・未元空気砲はベクトルの膜を通る」作り
     * （passesVectorMembrane）は、これと解析度に置き換えた。
     */
    private boolean directContact;
    /** 元の打撃から引き継いだ貫通の段（1.5.2）。null なら技能の段（{@link #pierceTier}）。 */
    private @Nullable PierceTier pierceTier;
    /** ふつうの当たり方で、技能のカテゴリの種類で当てる。 */
    public SkillDamageSource(ServerPlayer caster, ResourceLocation skill) { this(caster, skill, Style.PLAIN); }
    /** この当たり方で、技能のカテゴリの種類で当てる（1.5.2）。直接の加害も帰属も術者。いつも貫通の技能は、頼まれた当たり方に関係なく貫通。 */
    public SkillDamageSource(ServerPlayer caster, ResourceLocation skill, Style style) {
        this(type(caster.level().registryAccess(), skill, styleFor(skill, style)), caster, caster, skill);
    }
    /** 種類をそのまま指定する（テストなど、種類を自分で選ぶ呼び出し用）。 */
    public SkillDamageSource(ServerPlayer caster,ResourceLocation skill,ResourceKey<DamageType> type) {
        super(caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),caster);
        this.skill=java.util.Objects.requireNonNull(skill);
    }
    /**
     * 外部の主体の技能のダメージ（1.4.0）。directは直接の加害（主体のエンティティ。無ければnull）、causingは帰属（確かめられた帰属先の
     * プレイヤー、無ければ主体のエンティティ）。
     */
    public SkillDamageSource(Holder<DamageType> type, @Nullable net.minecraft.world.entity.Entity direct,
                             @Nullable net.minecraft.world.entity.Entity causing, ResourceLocation skill) {
        super(type, direct, causing);
        this.skill = java.util.Objects.requireNonNull(skill);
    }
    public ResourceLocation skill(){return skill;}
    /** 未元物質に直接触れる攻撃のフラグが立っているか。 */
    public boolean isDirectContact() { return directContact; }
    /** 未元物質に直接触れる攻撃のフラグを立てる（甲虫の近接と突進）。自身を返す。 */
    public SkillDamageSource markDirectContact() { directContact = true; return this; }
    /** 派生の打撃に、元の打撃の貫通の段を持たせる（1.5.2。null なら技能の段のまま）。自身を返す。 */
    public SkillDamageSource inheritPierceTier(@Nullable PierceTier tier) { pierceTier = tier; return this; }
    /**
     * 「被害者は術者に技能で殺された」の死亡文。キーは種類の{@code message_id}から作る（1.5.2から能力ごとに
     * {@code death.attack.ac_skill.<カテゴリ>}。共通の種類は1.5.1と同じ{@code death.attack.ac_skill}）。引数は被害者・術者・技能の名前のまま。
     */
    @Override public Component getLocalizedDeathMessage(LivingEntity victim){
        var definition=SkillCatalog.find(skill);var caster=getEntity()!=null?getEntity():getDirectEntity();
        return Component.translatable("death.attack."+type().msgId(),victim.getDisplayName(),caster==null?Component.literal("?"):caster.getDisplayName(),
                definition==null?Component.literal(skill.toString()):Component.translatable(definition.translation()));
    }
}
