package io.github.pinchan4273.academyexample;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.ActorKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.SkillRequirement;
import io.github.pinchan4273.reacademycraft.api.TreePosition;
import io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.AcademySafety;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Re:AcademyCraftの公開API（実験的）のサンプル。公開の型（{@code api}・{@code api.mc}・{@code api.forge}）だけを使う。
 * <ul>
 * <li>新しいカテゴリ「Kinetics」（因子のアイテムを持つので、本体の開発機で能力を得られる）。</li>
 * <li>Pulse（レベル1、一回きり）: まわりの生き物へ衝撃。攻撃は安全のサービスを通し、許された相手だけを押し返す。</li>
 * <li>Glide（レベル2、切り替え型、前提はPulse 30%）: 落ちる速さを抑える。ONの間、tickごとにCPを払う。</li>
 * <li>Spark Step（本体のElectromasterのレベル2、前提はArc Gen 50%）: 向いている方へ短く瞬間移動する。空間移動は安全のサービスを通す。</li>
 * <li>Kinetic Charge（ブロック、機械の主体の例）と、その技能Burst（技能の木に出ない）: 赤石の信号で、置いた人に帰属する機械として爆発する。
 * 爆発は安全のサービスを通す（{@link KineticChargeBlock}）。</li>
 * </ul>
 * 値は動きを見せるためのもので、遊ぶための調整はしていない。
 */
@Mod(AcademyExample.MOD_ID)
public final class AcademyExample {
    public static final String MOD_ID = "academyexample";
    static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final RegistryObject<Item> KINETICS_FACTOR = ITEMS.register("kinetics_factor", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Block> KINETIC_CHARGE = BLOCKS.register("kinetic_charge", () -> new KineticChargeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(.5f).sound(SoundType.GRASS)));
    public static final RegistryObject<Item> KINETIC_CHARGE_ITEM = ITEMS.register("kinetic_charge", () -> new BlockItem(KINETIC_CHARGE.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<KineticChargeBlockEntity>> KINETIC_CHARGE_ENTITY = BLOCK_ENTITIES.register("kinetic_charge",
            () -> BlockEntityType.Builder.of(KineticChargeBlockEntity::new, KINETIC_CHARGE.get()).build(null));

    static AbilityId id(String path) { return AbilityId.parse(MOD_ID + ":" + path); }
    static final AbilityId KINETICS = id("kinetics"), PULSE = id("pulse"), GLIDE = id("glide"), SPARK_STEP = id("spark_step"), BURST = id("burst");

    public AcademyExample() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(this::registerAbilities);
        bus.addListener(this::creativeTab);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(KINETICS_FACTOR);
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) event.accept(KINETIC_CHARGE_ITEM);
    }

    private static Presentation shown(AbilityId skill) {
        return new Presentation(MOD_ID + ".skill." + skill.path(), MOD_ID + ".skill." + skill.path() + ".desc", null);
    }

    /** 本体が共通の準備の段で出す登録の窓。ここでだけ登録できる。 */
    private void registerAbilities(RegisterAbilitiesEvent event) {
        var abilities = event.registrar(MOD_ID);
        abilities.category(new CategoryDefinition(KINETICS, id("kinetics_factor"), List.of(1, 1, 1, 1, 1),
                new Presentation(MOD_ID + ".ability.kinetics", null, null)));
        abilities.skill(new SkillDefinition(PULSE, KINETICS, 1, List.of(), ActivationKind.INSTANT, shown(PULSE), new TreePosition(40, 50, null)));
        abilities.skill(new SkillDefinition(GLIDE, KINETICS, 2, List.of(new SkillRequirement(PULSE, .3f)), ActivationKind.TOGGLE, shown(GLIDE),
                new TreePosition(120, 50, PULSE)));
        var arcGen = AbilityId.parse("academy:arc_gen");
        abilities.skill(new SkillDefinition(SPARK_STEP, AbilityId.parse("academy:electromaster"), 2, List.of(new SkillRequirement(arcGen, .5f)),
                ActivationKind.INSTANT, shown(SPARK_STEP), new TreePosition(160, 92, arcGen)));
        // Burstは技能の木に場所を持たない（開発機では学べない）。Kinetic Chargeが機械の主体として発動する。
        abilities.skill(new SkillDefinition(BURST, KINETICS, 1, List.of(), ActivationKind.INSTANT, shown(BURST)));

        var behaviors = event.behaviors(MOD_ID);
        behaviors.behavior(PULSE, AcademyExample::pulse);
        behaviors.behavior(GLIDE, new Glide());
        behaviors.behavior(SPARK_STEP, AcademyExample::sparkStep);
        behaviors.behavior(BURST, AcademyExample::burst);
    }

    /** Pulse: 半径4の生き物へ、許された相手だけにダメージと押し返し。 */
    private static ActivationOutcome pulse(ActivationContext c) {
        if (!c.pay(60, 15)) return ActivationOutcome.REFUSED_RESOURCE;
        var actor = c.actor();
        Vec3 centre = actor.position();
        float damage = 3 + 3 * c.proficiency();
        int hits = 0;
        for (var target : actor.level().getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(4), e -> e != actor.entity())) {
            // 攻撃が許されなかった相手は、押し返しもしない（安全のサービスが断った処理の副作用を残さない）。
            if (!AcademySafety.attack(actor, target, PULSE, damage)) continue;
            var away = target.position().subtract(centre).normalize().scale(.8);
            target.push(away.x, .3, away.z);
            target.hurtMarked = true;
            hits++;
        }
        actor.level().sendParticles(ParticleTypes.SONIC_BOOM, centre.x, centre.y, centre.z, 1, 0, 0, 0, 0);
        if (hits > 0) c.addProficiency(.01f * hits);
        c.setCooldown(30);
        return ActivationOutcome.PERFORMED;
    }

    /**
     * Burst: 主体の位置で威力2.5の爆発。機械などの外部の主体からだけ発動する（自然能力者のキーでは断る）。地形・相手の判定は安全のサービスが行う
     * （帰属先の確かめ・保護・PvP・mobGriefingなど）。爆発が起きなかったときも、費用は無い（Kinetic ChargeはFREEの方針）。
     */
    private static ActivationOutcome burst(ActivationContext c) {
        var actor = c.actor();
        if (actor.kind() == ActorKind.NATURAL) return ActivationOutcome.REFUSED_INVALID;
        if (!AcademySafety.explode(actor, actor.position(), 2.5f, BURST)) return ActivationOutcome.REFUSED_PERMISSION;
        c.setCooldown(20);
        return ActivationOutcome.PERFORMED;
    }

    /** Glide: ONの間、落ちる速さを0.1までに抑え、落下の距離を消す。払えなくなったら終える。 */
    private static final class Glide implements SkillBehavior {
        @Override public ActivationOutcome start(ActivationContext c) {
            return c.pay(20, 5) ? ActivationOutcome.STARTED : ActivationOutcome.REFUSED_RESOURCE;
        }
        @Override public boolean tick(ActivationContext c) {
            var body = c.actor().entity();
            if (body == null || !c.pay(2, 0)) return false;
            var motion = body.getDeltaMovement();
            if (motion.y < -.1) {
                body.setDeltaMovement(motion.x, -.1, motion.z);
                body.hurtMarked = true;
            }
            body.fallDistance = 0;
            if (c.age() % 10 == 0) c.addProficiency(.002f);
            return true;
        }
    }

    /** Spark Step: 向いている水平の方向へ最大4ブロック、ぶつからない所まで移る。行けないときは払わずに断る。 */
    private static ActivationOutcome sparkStep(ActivationContext c) {
        var actor = c.actor();
        var body = actor.entity();
        if (body == null) return ActivationOutcome.REFUSED_INVALID;
        var look = actor.look();
        var flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) return ActivationOutcome.REFUSED_INVALID;
        flat = flat.normalize();
        Vec3 to = null;
        for (double d = 4; d >= 1; d -= .5) {
            var candidate = body.position().add(flat.scale(d));
            if (actor.level().noCollision(body, body.getBoundingBox().move(candidate.subtract(body.position())))) { to = candidate; break; }
        }
        if (to == null || AcademySafety.checkTeleport(actor, body, to, SPARK_STEP) == null) return ActivationOutcome.REFUSED_PERMISSION;
        if (!c.pay(40, 10)) return ActivationOutcome.REFUSED_RESOURCE;
        if (!AcademySafety.teleport(actor, body, to, SPARK_STEP)) return ActivationOutcome.PERFORMED;
        actor.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 1, to.z, 12, .3, .5, .3, 0);
        c.addProficiency(.01f);
        c.setCooldown(20);
        return ActivationOutcome.PERFORMED;
    }
}
