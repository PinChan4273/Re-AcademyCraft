package io.github.pinchan4273.reacademycraft.skill;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * 天界発現の変異（§11。ユーザーの決定で「恒久化・再変異の回数無制限」へ作り直した版で、旧仕様の79種の独自の組による一時変異を置き換える）。
 * <p>
 * 対象は Java版1.20.1 のバニラで実際に起こり得る変異だけ（{@link Route}の表）。天界発現では落雷・水没・感染・治療・次元・粉雪・成長などの発生条件を省略し、場所や次元を問わず起こす。
 * 1回の当選で1段階だけ進め、毎回その時点の姿から有効な変異先を等確率で選ぶ。同じ個体が同じ領域で何度当選してもよく、個体ごと・再入場ごとの回数制限は無い
 * （例: 陸上のハスク→ゾンビ→ドラウンド、村人↔村人ゾンビ、ムーシュルームの赤↔茶）。有効な変異先が無い個体は抽選の候補にならず、追加CPも払わない。
 * <p>
 * 恒久: 変異の結果はバニラの変換と同じ普通の個体で、印も復元の情報も持たない。領域外への移動、変異の設定のOFF、領域の解除、チャンクの再読み込み、サーバーの再起動のどれでも最後の姿のまま。
 * 変異の瞬間は元の個体を{@link Entity#discard}で消すので、ドロップ・経験値・死亡イベントは出ない。その後に死んだ時は今の姿のバニラの通常の死亡として一度だけ処理される。
 * <p>
 * 他MODとの互換: 種類が変わる変異の前に{@link ForgeEventFactory#canLivingConvert}（{@code LivingConversionEvent.Pre}）を出し、取り消されたら見送る（費用なし）。
 * 変換の後は{@link ForgeEventFactory#onLivingConvert}（{@code LivingConversionEvent.Post}）を出す。同じ種類のままの状態変化（ムーシュルームの色・帯電クリーパー）は
 * 実体が変わらずバニラもこのイベントを出さないので出さない（実装で決めた）。
 * <p>
 * 引き継ぎ（全行に共通）: HP割合（全回復させない）、名前と名前の表示、AIなし、無敵、消えない設定、子ども（年齢を持つ種類どうしは年齢そのもの）、リード（乗り物に乗っていない時）、乗り物。
 * 乗せていた相手は降ろす。装備や村人の情報などの行ごとの引き継ぎはバニラの変換に合わせる（{@link Route}の各行の説明）。
 * <p>
 * 旧方式の印: 旧仕様（一時変異）の個体は永続データ{@value #LEGACY_KEY}に元の個体の全データを持っていた。読み込まれたら印だけを外し、今の姿のまま普通の個体として扱う。
 * 恒久化の決定どおり今の姿を最後の姿とし、保存されていた元の個体は作り直さない（作り直すと旧仕様の復元をすることになり、個体が増える恐れもあるため）。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class HeavenMutation {
    /** 旧方式（一時変異）の変異後の実体に付いていた永続データのキー。読み込まれた個体から外す。 */
    public static final String LEGACY_KEY = "academy_heaven_mutation";

    /**
     * 1.20.1のバニラで起こり得る変異の表（1行が1つの向き）。種類が同じ行は状態変化。
     * <p>
     * 対象外（理由）: エンダードラゴン↔ウィザー・襲撃者同士の任意の変換・普通の牛→ムーシュルーム・ゾンビピグリン→ピグリン・ゾグリン→ホグリン・ウィッチ→村人・帯電クリーパー→普通のクリーパー
     * （どれもバニラに無い）。村人ゾンビ→ドラウンド（{@code ZombieVillager.convertsInWater}がfalseでバニラでは起きない。ゾンビピグリン・ドラウンドも同じ）。
     * 子ども→大人の成長（同じ種類の年齢の進行で、変異の段階にしない。種類が変わるオタマジャクシ→カエルだけは含める）。羊の染色・毛刈り、雪だるまのカボチャ、シュルカーの染色
     * （プレイヤーの道具による外見の変更）。村人の職業（職業ブロックによる状態）。スライム・マグマキューブの分裂（死亡による新しい個体の発生）。
     * 落雷のスケルトントラップ・アレイの複製（既存の個体の変換ではなく新しい個体の発生）。ウミガメへの落雷（損傷で、変換ではない）。フグの膨らみ・ストライダーの寒さ（一時的な状態）。
     * 手懐け（プレイヤーの操作による所有者の変化）。
     */
    public enum Route {
        /** 水没（ゾンビが水中で変わる）。装備を移し、ゾンビの属性の抽選と扉の破壊を引き継ぐ（{@code Zombie.convertToZombieType}）。 */
        ZOMBIE_TO_DROWNED(EntityType.ZOMBIE, EntityType.DROWNED),
        /** 水没（ハスクが水中でゾンビになる）。ゾンビ→ドラウンドと同じ引き継ぎ。 */
        HUSK_TO_ZOMBIE(EntityType.HUSK, EntityType.ZOMBIE),
        /** 粉雪の中で凍える。装備を移す（{@code Skeleton.doFreezeConversion}）。 */
        SKELETON_TO_STRAY(EntityType.SKELETON, EntityType.STRAY),
        /** 落雷。装備は移さない。ウィッチは常に消えない設定（{@code Villager.thunderHit}）。 */
        VILLAGER_TO_WITCH(EntityType.VILLAGER, EntityType.WITCH),
        /** ゾンビによる殺害（感染）。装備は移さず、職業・取引・評判（ゴシップ）・経験を引き継ぐ（{@code Zombie.killedEntity}）。 */
        VILLAGER_TO_ZOMBIE_VILLAGER(EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER),
        /** 治療。職業・取引・評判・経験を引き継ぎ、吐き気（10秒）を付ける。装備は束縛の呪いの物だけ移す（{@code ZombieVillager.finishConversion}）。 */
        ZOMBIE_VILLAGER_TO_VILLAGER(EntityType.ZOMBIE_VILLAGER, EntityType.VILLAGER),
        /** 落雷。装備は移さず金の剣を持たせ、常に消えない設定（{@code Pig.thunderHit}）。 */
        PIG_TO_ZOMBIFIED_PIGLIN(EntityType.PIG, EntityType.ZOMBIFIED_PIGLIN),
        /** ネザー以外の次元。装備を移し、吐き気（10秒）を付ける（{@code AbstractPiglin.finishConversion}）。 */
        PIGLIN_TO_ZOMBIFIED_PIGLIN(EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN),
        /** ネザー以外の次元。ピグリンと同じ。 */
        PIGLIN_BRUTE_TO_ZOMBIFIED_PIGLIN(EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN),
        /** ネザー以外の次元。吐き気（10秒）を付ける（{@code Hoglin.finishConversion}）。 */
        HOGLIN_TO_ZOGLIN(EntityType.HOGLIN, EntityType.ZOGLIN),
        /** 落雷（状態変化）。赤→茶（{@code MushroomCow.thunderHit}）。 */
        MOOSHROOM_RED_TO_BROWN(EntityType.MOOSHROOM, EntityType.MOOSHROOM),
        /** 落雷（状態変化）。茶→赤。 */
        MOOSHROOM_BROWN_TO_RED(EntityType.MOOSHROOM, EntityType.MOOSHROOM),
        /** 毛刈り（バニラの発生条件）。ムーシュルームを牛に変異させ、HP割合を引き継ぐ（{@code MushroomCow.shear}）。天界発現による変異ではキノコをドロップしない。 */
        MOOSHROOM_TO_COW(EntityType.MOOSHROOM, EntityType.COW),
        /** 落雷（状態変化）。帯電する（{@code Creeper.thunderHit}）。逆向きは無い。 */
        CREEPER_CHARGE(EntityType.CREEPER, EntityType.CREEPER),
        /** 成長。カエルの種類はバニラと同じく今の場所のバイオームで決まる。常に消えない設定（{@code Tadpole.ageUp}）。 */
        TADPOLE_TO_FROG(EntityType.TADPOLE, EntityType.FROG);

        private final EntityType<? extends Mob> from, to;
        Route(EntityType<? extends Mob> from, EntityType<? extends Mob> to) { this.from = from; this.to = to; }
        public EntityType<? extends Mob> from() { return from; }
        public EntityType<? extends Mob> to() { return to; }
        /** 同じ種類のままの状態変化か。 */
        public boolean stateChange() { return from == to; }
        /**
         * この個体に今この変異を起こせるか。発生条件（落雷・水没など）は省略する。ピースフルでは変異先が敵対モブ（すぐに消される）の行を外す（実装で決めた。消えるだけの変異で個体を失わせないため）。
         */
        public boolean applies(Mob mob) {
            if (mob.getType() != from || !mob.isAlive() || mob.isRemoved()) return false;
            if (to.getCategory() == MobCategory.MONSTER && mob.level().getDifficulty() == Difficulty.PEACEFUL) return false;
            return switch (this) {
                case MOOSHROOM_RED_TO_BROWN -> mob instanceof MushroomCow cow && cow.getVariant() == MushroomCow.MushroomType.RED;
                case MOOSHROOM_BROWN_TO_RED -> mob instanceof MushroomCow cow && cow.getVariant() == MushroomCow.MushroomType.BROWN;
                case CREEPER_CHARGE -> mob instanceof Creeper creeper && !creeper.isPowered();
                default -> true;
            };
        }
    }

    /** ゾンビの属性の抽選（{@code Zombie.handleAttributes}。protectedなので反射で呼ぶ）。見つからなければnull。 */
    private static final @Nullable Method HANDLE_ATTRIBUTES = findHandleAttributes();

    private HeavenMutation() { }

    private static @Nullable Method findHandleAttributes() {
        try {
            return ObfuscationReflectionHelper.findMethod(Zombie.class, "m_34339_", float.class);
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Could not find Zombie.handleAttributes; mutated zombies keep the default attributes", e);
            return null;
        }
    }

    // ---- 候補 ----

    /** この個体の今の有効な変異先（無ければ空）。 */
    public static List<Route> routes(Mob mob) {
        return java.util.Arrays.stream(Route.values()).filter(route -> route.applies(mob)).toList();
    }
    /** 変異の抽選の候補にしてよいか（今の姿に有効な変異先があるか）。 */
    public static boolean eligible(LivingEntity entity) {
        return entity instanceof Mob mob && !entity.level().isClientSide && !routes(mob).isEmpty();
    }

    // ---- 変異 ----

    /** 領域の当選で1段階変異させる。成立したらtrue（見送りでは費用を払わせない）。 */
    static boolean mutate(LivingEntity entity, HeavenManifest.Session session) {
        if (!(entity instanceof Mob mob)) return false;
        var list = routes(mob);
        if (list.isEmpty()) return false;
        var result = convert(mob, list.get(mob.getRandom().nextInt(list.size())));
        if (result == null) return false;
        if (result != mob) HeavenPhenomena.move(mob, result);
        HeavenManifest.markDirty();
        HeavenVisuals.mutated(session, result);
        return true;
    }

    /**
     * この行の変異を今すぐ起こす（試験からも呼ぶ）。変異後の個体（状態変化なら同じ個体）を返す。起こせない・イベントで取り消された・世界へ入れられなかった時はnullで、元の個体は変わらない。
     */
    public static @Nullable Mob convert(Mob mob, Route route) {
        if (!(mob.level() instanceof ServerLevel level) || !route.applies(mob)) return null;
        if (route.stateChange()) { changeState(mob, route); return mob; }
        if (!ForgeEventFactory.canLivingConvert(mob, route.to, timer -> { })) return null;
        Mob body = route.to.create(level);
        if (body == null) return null;
        float ratio = Math.min(1, mob.getHealth() / mob.getMaxHealth());
        inherit(mob, body);
        before(mob, body, route, level);
        if (!level.addFreshEntity(body)) { body.discard(); return null; }
        // 乗せていた相手は降ろし、自分が乗っていた乗り物には乗り直す。リードは乗り物に乗っていない時だけ（乗るとリードが外れるため）繋ぎ直す。
        mob.ejectPassengers();
        var vehicle = mob.getVehicle();
        var holder = mob.isLeashed() ? mob.getLeashHolder() : null;
        if (vehicle != null) {
            mob.stopRiding();
            body.startRiding(vehicle, true);
        } else if (holder != null) {
            mob.dropLeash(true, false);
            body.setLeashedTo(holder, true);
        }
        after(mob, body, route, level);
        // HP割合は属性の変化（ゾンビの抽選など）の後に合わせる。
        body.setHealth(Math.max(Float.MIN_NORMAL, ratio * body.getMaxHealth()));
        // 死亡ではないのでドロップ・経験値・死亡イベントを出さずに消す。
        mob.discard();
        ForgeEventFactory.onLivingConvert(mob, body);
        return body;
    }

    /** 同じ種類のままの状態変化。 */
    private static void changeState(Mob mob, Route route) {
        switch (route) {
            case MOOSHROOM_RED_TO_BROWN, MOOSHROOM_BROWN_TO_RED -> {
                var cow = (MushroomCow) mob;
                cow.setVariant(route == Route.MOOSHROOM_RED_TO_BROWN ? MushroomCow.MushroomType.BROWN : MushroomCow.MushroomType.RED);
                cow.playSound(SoundEvents.MOOSHROOM_CONVERT, 2, 1);
            }
            case CREEPER_CHARGE -> mob.getEntityData().set(Creeper.DATA_IS_POWERED, true);
            default -> throw new IllegalArgumentException(route.name());
        }
    }

    /** 全行に共通の引き継ぎ（{@code Mob.convertTo}の項目に、無敵と消えない設定を全行で足したもの）。 */
    private static void inherit(Mob from, Mob to) {
        to.copyPosition(from);
        to.yHeadRot = from.yHeadRot;
        to.yBodyRot = from.yBodyRot;
        // 年齢を持つ種類どうしは成長の進み（繁殖の待ちを含む）をそのまま移す。それ以外はバニラと同じく子どもかどうかだけ。
        if (from instanceof AgeableMob a && to instanceof AgeableMob b) b.setAge(a.getAge());
        else to.setBaby(from.isBaby());
        to.setNoAi(from.isNoAi());
        if (from.hasCustomName()) {
            to.setCustomName(from.getCustomName());
            to.setCustomNameVisible(from.isCustomNameVisible());
        }
        if (from.isPersistenceRequired()) to.setPersistenceRequired();
        to.setInvulnerable(from.isInvulnerable());
    }

    /** 世界へ入れる前の行ごとの準備（バニラが加える前に行う物）。 */
    private static void before(Mob from, Mob to, Route route, ServerLevel level) {
        switch (route) {
            case VILLAGER_TO_WITCH, TADPOLE_TO_FROG -> {
                // カエルの種類はここで今の場所のバイオームから決まる。
                to.finalizeSpawn(level, level.getCurrentDifficultyAt(to.blockPosition()), MobSpawnType.CONVERSION, null, null);
                to.setPersistenceRequired();
            }
            case PIG_TO_ZOMBIFIED_PIGLIN -> {
                to.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
                to.setPersistenceRequired();
            }
            default -> { }
        }
    }

    /** 世界へ入れた後の行ごとの引き継ぎと付随（バニラの変換の順に合わせる）。音は鳴らす。 */
    private static void after(Mob from, Mob to, Route route, ServerLevel level) {
        switch (route) {
            case ZOMBIE_TO_DROWNED, HUSK_TO_ZOMBIE -> {
                moveEquipment(from, to);
                var zombie = (Zombie) to;
                handleAttributes(zombie);
                // 扉を壊せない種類（ドラウンドの水中など）ならsetCanBreakDoorsの中でfalseになる（バニラの supportsBreakDoorGoal() && canBreakDoors() と同じ）。
                zombie.setCanBreakDoors(((Zombie) from).canBreakDoors());
                if (!from.isSilent()) level.levelEvent(null, route == Route.ZOMBIE_TO_DROWNED ? 1040 : 1041, from.blockPosition(), 0);
            }
            case SKELETON_TO_STRAY -> {
                moveEquipment(from, to);
                if (!from.isSilent()) level.levelEvent(null, 1048, from.blockPosition(), 0);
            }
            case VILLAGER_TO_WITCH -> releasePois((Villager) from);
            case VILLAGER_TO_ZOMBIE_VILLAGER -> {
                var villager = (Villager) from;
                var zombie = (ZombieVillager) to;
                zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.CONVERSION, new Zombie.ZombieGroupData(false, true), null);
                zombie.setVillagerData(villager.getVillagerData());
                zombie.setGossips(villager.getGossips().store(NbtOps.INSTANCE));
                zombie.setTradeOffers(villager.getOffers().createTag());
                zombie.setVillagerXp(villager.getVillagerXp());
                // バニラでは村人が死亡の処理でPOIを手放す。ここでは死亡させないので同じだけ手放す。
                releasePois(villager);
                if (!from.isSilent()) level.levelEvent(null, 1026, from.blockPosition(), 0);
            }
            case ZOMBIE_VILLAGER_TO_VILLAGER -> cure((ZombieVillager) from, (Villager) to, level);
            case PIGLIN_TO_ZOMBIFIED_PIGLIN, PIGLIN_BRUTE_TO_ZOMBIFIED_PIGLIN, HOGLIN_TO_ZOGLIN -> {
                SoundEvent sound = route == Route.PIGLIN_TO_ZOMBIFIED_PIGLIN ? SoundEvents.PIGLIN_CONVERTED_TO_ZOMBIFIED
                        : route == Route.PIGLIN_BRUTE_TO_ZOMBIFIED_PIGLIN ? SoundEvents.PIGLIN_BRUTE_CONVERTED_TO_ZOMBIFIED : SoundEvents.HOGLIN_CONVERTED_TO_ZOMBIFIED;
                from.playSound(sound, 1, from.getVoicePitch());
                moveEquipment(from, to);
                to.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            }
            case MOOSHROOM_TO_COW -> {
                level.playSound(null, from, SoundEvents.MOOSHROOM_SHEAR, from.getSoundSource(), 1, 1);
                level.sendParticles(ParticleTypes.EXPLOSION, from.getX(), from.getY(.5), from.getZ(), 1, 0, 0, 0, 0);
            }
            case TADPOLE_TO_FROG -> from.playSound(SoundEvents.TADPOLE_GROW_UP, .15f, 1);
            default -> { }
        }
    }

    /**
     * 治療（{@code ZombieVillager.finishConversion}）。装備はバニラどおり束縛の呪いの物を移す。バニラがその場で落とす拾った物（落とす確率が1を超える物）は、
     * 変異の瞬間にアイテムを出さないために村人へ同じ確率のまま移す（実装で決めた。失わせず、後の死亡で落ちる）。それ以外の装備はバニラどおり消える。
     * 治した人がいないので、評判の加点と進捗は出さない。
     */
    private static void cure(ZombieVillager zombie, Villager villager, ServerLevel level) {
        var saved = new CompoundTag();
        zombie.addAdditionalSaveData(saved);
        for (var slot : EquipmentSlot.values()) {
            var stack = zombie.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            float chance = dropChance(saved, slot);
            if (EnchantmentHelper.hasBindingCurse(stack)) villager.setItemSlot(slot, stack.copyAndClear());
            else if (chance > 1) { villager.setItemSlot(slot, stack.copyAndClear()); villager.setDropChance(slot, chance); }
        }
        villager.setVillagerData(zombie.getVillagerData());
        if (saved.contains("Gossips")) villager.setGossips(saved.get("Gossips"));
        if (saved.contains("Offers", Tag.TAG_COMPOUND)) villager.setOffers(new MerchantOffers(saved.getCompound("Offers")));
        villager.setVillagerXp(zombie.getVillagerXp());
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null, null);
        villager.refreshBrain(level);
        villager.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        if (!zombie.isSilent()) level.levelEvent(null, 1027, zombie.blockPosition(), 0);
    }

    /** {@code Mob.convertTo(type, true)}の装備の移し方: 拾うかどうかと全装備と落とす確率を移し、元からは消す（複製しない）。 */
    private static void moveEquipment(Mob from, Mob to) {
        var saved = new CompoundTag();
        from.addAdditionalSaveData(saved);
        to.setCanPickUpLoot(from.canPickUpLoot());
        for (var slot : EquipmentSlot.values()) {
            var stack = from.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            to.setItemSlot(slot, stack.copyAndClear());
            to.setDropChance(slot, dropChance(saved, slot));
        }
    }
    /** 保存データから装備の落とす確率を読む（{@code Mob.getEquipmentDropChance}はprotectedのため）。 */
    private static float dropChance(CompoundTag saved, EquipmentSlot slot) {
        var list = saved.getList(slot.getType() == EquipmentSlot.Type.HAND ? "HandDropChances" : "ArmorDropChances", Tag.TAG_FLOAT);
        return slot.getIndex() < list.size() ? list.getFloat(slot.getIndex()) : .085f;
    }
    /** 村人の全POI（家・職場・職場の候補・集会所）を手放す（{@code Villager.releaseAllPois}と同じ）。 */
    private static void releasePois(Villager villager) {
        villager.releasePoi(MemoryModuleType.HOME);
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.releasePoi(MemoryModuleType.MEETING_POINT);
    }
    private static void handleAttributes(Zombie zombie) {
        if (HANDLE_ATTRIBUTES == null) return;
        try {
            HANDLE_ATTRIBUTES.invoke(zombie, zombie.level().getCurrentDifficultyAt(zombie.blockPosition()).getSpecialMultiplier());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogUtils.getLogger().warn("Could not roll the attributes of a mutated {}", zombie.getType(), e);
        }
    }

    // ---- 旧方式の印 ----

    /** 旧方式の印を外す。外したらtrue（試験からも呼ぶ）。元の個体へは戻さない（クラスの説明）。 */
    public static boolean clearLegacy(Entity entity) {
        if (!entity.getPersistentData().contains(LEGACY_KEY)) return false;
        entity.getPersistentData().remove(LEGACY_KEY);
        return true;
    }
    /** 読み込まれた時（チャンクの読み直し・再起動・次元の移動）に旧方式の印を外す。 */
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) clearLegacy(event.getEntity());
    }
}
