package io.github.pinchan4273.reacademycraft.ability;

import com.mojang.logging.LogUtils;
import io.github.pinchan4273.reacademycraft.ability.SkillDispatch.Cast;
import io.github.pinchan4273.reacademycraft.ability.SkillDispatch.Named;
import io.github.pinchan4273.reacademycraft.ability.SkillDispatch.Owner;
import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.StopReason;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 外部の技能の自然能力者による発動とセッション（1.4.0のS4、内部の実装）。振り分けの表から呼ばれ、資格を判定してから振る舞い
 * （{@link SkillBehavior}）を呼ぶ。セッションはプレイヤーと技能ごとに1つで、サーバーのスレッドでだけ扱う。
 * <p>
 * 資格（発動の前、この順）: 生きていて観戦者でない → 能力があり、データが書き込めて、習得していて、カテゴリが提供する → 設定で有効 →
 * 能力がオン → 過負荷の錠が無い → 妨害の外 → クールダウンが無い → インベントリ以外の画面を開いていない → 同じ技能が動作中でない。
 * 新しい公開APIなので、妨害は、始めた後のセッションも止める（司令室11の裁定D4。本体の技能の今の挙動とは別）。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class AddonSessions {
    private AddonSessions() { }

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 押し続け型の心拍が、これより長く途切れたら止める（tick。本体の押し続け型の多くと同じ）。 */
    public static final int HEARTBEAT_TIMEOUT = 10;
    private static final Map<UUID, Map<ResourceLocation, Session>> SESSIONS = new HashMap<>();

    /** 振り分けの表へ、その技能を足す（振る舞いの登録簿の凍結の時）。 */
    static void addToDispatch(ResourceLocation skill, ActivationKind kind) {
        String name = skill.toString();
        Owner owner = null, toggleOwner = null;
        if (kind == ActivationKind.HELD || kind == ActivationKind.TOGGLE) {
            Named<SkillDispatch.SlotAction> release = kind == ActivationKind.HELD ? new Named<>(name + ".release", (p, s) -> release(p, s, skill)) : null;
            Named<SkillDispatch.SlotAction> cancel = new Named<>(name + ".cancel", (p, s) -> stopOnSlot(p, s, skill, StopReason.CANCELLED));
            Named<SkillDispatch.SlotAction> renew = kind == ActivationKind.HELD ? new Named<>(name + ".renew", (p, s) -> renew(p, s, skill)) : null;
            Named<SkillDispatch.Age> age = kind == ActivationKind.TOGGLE ? new Named<>(name + ".ageOn", (p, s) -> ageOn(p, s, skill)) : null;
            owner = new Owner(name, release, cancel, renew, new Named<>(name + ".stop", p -> stop(p, skill, StopReason.ALL_STOPPED)), age);
            // 切り替え型の2回目の押下の停止は、理由だけが違う（振り分けの表は、発動の持ち主のstopを呼ぶ）。
            toggleOwner = new Owner(name, release, cancel, renew, new Named<>(name + ".stop", p -> stop(p, skill, StopReason.SWITCHED_OFF)), age);
        }
        SkillDispatch.addAddon(skill, owner, new Cast(new Named<>(name + ".start", (p, d, s) -> {
            var outcome = start(p, d, skill, s);
            return outcome.accepted() ? null : outcome.name();
        }), toggleOwner));
    }

    /** 発動する。資格が無ければ振る舞いを呼ばずに断る。 */
    public static ActivationOutcome start(ServerPlayer player, @Nullable PlayerAbilityData data, ResourceLocation skill, int slot) {
        AbilityId id = RegistryViews.id(skill);
        var definition = AbilityRegistry.INSTANCE.skill(id);
        SkillBehavior behavior = SkillBehaviorRegistry.INSTANCE.behavior(id);
        if (definition == null || behavior == null || !player.isAlive() || player.isSpectator()) return ActivationOutcome.REFUSED_INVALID;
        var catalog = SkillCatalog.find(skill);
        if (data == null || data.isReadOnly() || !data.hasAbility() || !data.hasLearned(skill) || catalog == null || !catalog.offeredTo(data.getAbility()))
            return ActivationOutcome.REFUSED_UNLEARNED;
        if (!io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillEnabled(skill)) return ActivationOutcome.REFUSED_DISABLED;
        if (!data.isActive()) return ActivationOutcome.REFUSED_INACTIVE;
        if (data.isOverloadLocked()) return ActivationOutcome.REFUSED_OVERLOAD;
        if (data.isInterfering()) return ActivationOutcome.REFUSED_INTERFERED;
        if (data.getCooldown(skill) > 0) return ActivationOutcome.REFUSED_COOLDOWN;
        if (player.containerMenu != player.inventoryMenu || session(player, skill) != null) return ActivationOutcome.REFUSED_INVALID;
        var session = new Session(player, id, skill, slot, definition.kind(), behavior, player.serverLevel().getGameTime());
        ActivationOutcome outcome;
        try {
            outcome = behavior.start(session);
        } catch (RuntimeException e) {
            failed(session, "start", e);
            return ActivationOutcome.REFUSED_INVALID;
        }
        if (outcome == null) {
            LOGGER.error("The behavior of {} ({}) returned no outcome", skill, AbilityRegistry.INSTANCE.owner(id));
            return ActivationOutcome.REFUSED_INVALID;
        }
        if (outcome == ActivationOutcome.STARTED) {
            if (definition.kind() == ActivationKind.INSTANT) return ActivationOutcome.PERFORMED;
            SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>()).put(skill, session);
        }
        return outcome;
    }

    /** そのプレイヤーの、その技能のセッション（無ければnull）。 */
    public static @Nullable ActivationContext session(ServerPlayer player, ResourceLocation skill) {
        var sessions = SESSIONS.get(player.getUUID());
        return sessions == null ? null : sessions.get(skill);
    }
    /** 動作中のセッションの数（試験と診断のため）。 */
    public static int count(ServerPlayer player) {
        var sessions = SESSIONS.get(player.getUUID());
        return sessions == null ? 0 : sessions.size();
    }

    private static @Nullable Session onSlot(ServerPlayer player, int slot, ResourceLocation skill) {
        var session = (Session) session(player, skill);
        return session != null && session.slot == slot ? session : null;
    }
    private static void renew(ServerPlayer player, int slot, ResourceLocation skill) {
        var session = onSlot(player, slot, skill);
        if (session != null) session.heartbeat = player.serverLevel().getGameTime();
    }
    private static void release(ServerPlayer player, int slot, ResourceLocation skill) {
        var session = onSlot(player, slot, skill);
        if (session == null) return;
        remove(session);
        try {
            session.behavior.release(session);
        } catch (RuntimeException e) {
            failed(session, "release", e);
        }
    }
    private static int ageOn(ServerPlayer player, int slot, ResourceLocation skill) {
        var session = onSlot(player, slot, skill);
        return session == null ? -1 : session.age();
    }
    private static void stopOnSlot(ServerPlayer player, int slot, ResourceLocation skill, StopReason reason) {
        var session = onSlot(player, slot, skill);
        if (session != null) end(session, reason);
    }
    /** その技能のセッションを終える。 */
    public static void stop(ServerPlayer player, ResourceLocation skill, StopReason reason) {
        var session = (Session) session(player, skill);
        if (session != null) end(session, reason);
    }
    private static void stopAll(ServerPlayer player, StopReason reason) {
        var sessions = SESSIONS.get(player.getUUID());
        if (sessions != null) for (var session : List.copyOf(sessions.values())) end(session, reason);
    }
    private static void end(Session session, StopReason reason) {
        remove(session);
        try {
            session.behavior.stop(session, reason);
        } catch (RuntimeException e) {
            failed(session, "stop", e);
        }
    }
    private static void remove(Session session) {
        var sessions = SESSIONS.get(session.player.getUUID());
        if (sessions != null && sessions.get(session.location) == session) {
            sessions.remove(session.location);
            if (sessions.isEmpty()) SESSIONS.remove(session.player.getUUID());
        }
    }
    private static void failed(Session session, String call, RuntimeException e) {
        remove(session);
        LOGGER.error("The behavior of {} ({}) threw in {}; its session ended", session.location, AbilityRegistry.INSTANCE.owner(session.id), call, e);
    }

    @SubscribeEvent public static void addonSessionsOnPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) tick(player);
    }
    /** 試験の治具のプレイヤー（サーバーがtickしない）のため、プレイヤーのtickの終わりと同じ処理を呼ぶ。 */
    public static void tickForTest(ServerPlayer player) { tick(player); }
    private static void tick(ServerPlayer player) {
        var sessions = SESSIONS.get(player.getUUID());
        if (sessions == null) return;
        long now = player.serverLevel().getGameTime();
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).resolve().orElse(null);
        for (var session : List.copyOf(sessions.values())) {
            if (session.player != player || !player.isAlive() || player.isSpectator() || data == null || data.isReadOnly() || !data.hasAbility()
                    || !data.hasLearned(session.location) || !data.isActive() || data.isInterfering()) { end(session, StopReason.INELIGIBLE); continue; }
            if (session.kind == ActivationKind.HELD && now - session.heartbeat > HEARTBEAT_TIMEOUT) { end(session, StopReason.HEARTBEAT_LOST); continue; }
            boolean going;
            try {
                going = session.behavior.tick(session);
            } catch (RuntimeException e) {
                failed(session, "tick", e);
                continue;
            }
            if (!going && session(player, session.location) == session) end(session, StopReason.FINISHED);
        }
    }
    @SubscribeEvent public static void addonSessionsOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopAll(player, StopReason.LOGGED_OUT);
    }
    @SubscribeEvent public static void addonSessionsOnDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopAll(player, StopReason.INELIGIBLE);
    }
    @SubscribeEvent public static void addonSessionsOnDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stopAll(player, StopReason.INELIGIBLE);
    }

    /** 1回の発動とそのセッションの文脈。 */
    private static final class Session implements ActivationContext {
        final ServerPlayer player;
        final AbilityId id;
        final ResourceLocation location;
        final int slot;
        final ActivationKind kind;
        final SkillBehavior behavior;
        final long started;
        long heartbeat;
        private final NaturalActor actor;

        Session(ServerPlayer player, AbilityId id, ResourceLocation location, int slot, ActivationKind kind, SkillBehavior behavior, long started) {
            this.player = player; this.id = id; this.location = location; this.slot = slot; this.kind = kind; this.behavior = behavior;
            this.started = started; this.heartbeat = started; this.actor = new NaturalActor(player);
        }
        private @Nullable PlayerAbilityData data() { return player.getCapability(AbilityCapabilities.PLAYER_ABILITY).resolve().orElse(null); }
        @Override public AbilityId skill() { return id; }
        @Override public AbilityActor actor() { return actor; }
        @Override public ServerPlayer naturalUser() { return player; }
        @Override public int slot() { return slot; }
        @Override public int age() { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, player.serverLevel().getGameTime() - started)); }
        @Override public float proficiency() {
            var data = data();
            return data == null ? 0 : data.getProficiency(location);
        }
        @Override public boolean pay(float cp, float overload) {
            if (!Float.isFinite(cp) || !Float.isFinite(overload) || cp < 0 || overload < 0) throw new IllegalArgumentException("Invalid cost " + cp + ", " + overload);
            var data = data();
            return data != null && data.consume(location, cp, overload, player.getAbilities().instabuild);
        }
        @Override public void addProficiency(float amount) {
            if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid proficiency " + amount);
            var data = data();
            if (data != null && !data.isReadOnly() && data.hasLearned(location)) data.addProficiency(location, amount);
        }
        @Override public void setCooldown(int ticks) {
            if (ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Invalid cooldown " + ticks);
            var data = data();
            if (data != null && !data.isReadOnly() && data.hasLearned(location)) data.setCooldown(location, ticks);
        }
    }
}
