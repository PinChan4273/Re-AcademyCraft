package io.github.pinchan4273.reacademycraft.ability;

import com.mojang.logging.LogUtils;
import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.ActorKind;
import io.github.pinchan4273.reacademycraft.api.StopReason;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.ResourcePolicy;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 外部の主体（装備・機械・AI）の発動とセッション（1.4.0のS5、内部の実装。公開の説明は{@code api.mc.AbilityActivation}）。
 * セッションは主体の識別子と技能ごとに1つで、サーバーのtickの終わりに進める。サーバーのスレッドでだけ扱う。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class ExternalSessions {
    private ExternalSessions() { }

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 押し続け型の心拍が、これより長く途切れたら止める（tick。自然能力者の外部の技能と同じ）。 */
    public static final int HEARTBEAT_TIMEOUT = AddonSessions.HEARTBEAT_TIMEOUT;
    private static final Map<UUID, Map<AbilityId, Session>> SESSIONS = new HashMap<>();
    /** 主体と技能ごとの、クールダウンが終わるゲームの時刻。 */
    private static final Map<UUID, Map<AbilityId, Long>> COOLDOWNS = new HashMap<>();

    private static void requireServerThread(AbilityActor actor) {
        var server = actor.level().getServer();
        if (server != null && !server.isSameThread()) throw new IllegalStateException("External activation must run on the server thread");
    }
    private static boolean usable(AbilityActor actor) {
        if (!actor.valid()) return false;
        var pos = actor.position();
        return pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z) && actor.level().isLoaded(BlockPos.containing(pos));
    }

    public static ActivationOutcome activate(AbilityActor actor, AbilityId skill, @Nullable ResourcePolicy policy) {
        if (actor == null || skill == null) return ActivationOutcome.REFUSED_INVALID;
        requireServerThread(actor);
        // 1. 主体: 自然能力者はキーで発動する。使えない主体・読み込まれていない場所は断る。
        if (actor.kind() == null || actor.kind() == ActorKind.NATURAL || !usable(actor)) return ActivationOutcome.REFUSED_INVALID;
        // 2. 資格（3の権限より先に、登録・種類を確かめる）。
        var definition = AbilityRegistry.INSTANCE.skill(skill);
        if (definition == null || definition.kind() == ActivationKind.PASSIVE) return ActivationOutcome.REFUSED_INVALID;
        // 3. 権限: 本体の41技能は、外部の主体からは発動できない（司令室11の裁定D3）。
        if (skill.isAcademy()) return ActivationOutcome.REFUSED_PERMISSION;
        SkillBehavior behavior = SkillBehaviorRegistry.INSTANCE.behavior(skill);
        if (behavior == null) return ActivationOutcome.REFUSED_INVALID;
        var location = RegistryViews.location(skill);
        if (!AcademyConfig.skillEnabled(location)) return ActivationOutcome.REFUSED_DISABLED;
        if (SafetyService.interfered(actor)) return ActivationOutcome.REFUSED_INTERFERED;
        long now = actor.level().getGameTime();
        if (cooldownEnd(actor.id(), skill) > now) return ActivationOutcome.REFUSED_COOLDOWN;
        if (session(actor, skill) != null) return ActivationOutcome.REFUSED_INVALID;
        // 4. 資源: 方針が無ければ断る（無料は方針FREEを明示して選ぶ）。
        if (policy == null) return ActivationOutcome.REFUSED_RESOURCE;
        // 5. 効果。
        var session = new Session(actor, skill, definition.kind(), behavior, policy, now);
        ActivationOutcome outcome;
        try {
            outcome = behavior.start(session);
        } catch (RuntimeException e) {
            LOGGER.error("The behavior of {} ({}) threw in an external start by {}", skill, AbilityRegistry.INSTANCE.owner(skill), actor.kind(), e);
            return ActivationOutcome.REFUSED_INVALID;
        }
        if (outcome == null) return ActivationOutcome.REFUSED_INVALID;
        // 6. 結果。
        if (outcome == ActivationOutcome.STARTED) {
            if (definition.kind() == ActivationKind.INSTANT) return ActivationOutcome.PERFORMED;
            SESSIONS.computeIfAbsent(actor.id(), ignored -> new LinkedHashMap<>()).put(skill, session);
        }
        return outcome;
    }

    private static long cooldownEnd(UUID actor, AbilityId skill) {
        var map = COOLDOWNS.get(actor);
        var end = map == null ? null : map.get(skill);
        return end == null ? Long.MIN_VALUE : end;
    }
    private static @Nullable Session session(AbilityActor actor, AbilityId skill) {
        var map = SESSIONS.get(actor.id());
        return map == null ? null : map.get(skill);
    }
    public static boolean active(AbilityActor actor, AbilityId skill) { return actor != null && session(actor, skill) != null; }
    public static boolean hold(AbilityActor actor, AbilityId skill) {
        var session = actor == null ? null : session(actor, skill);
        if (session == null) return false;
        session.heartbeat = actor.level().getGameTime();
        return true;
    }
    public static boolean stop(AbilityActor actor, AbilityId skill) {
        var session = actor == null ? null : session(actor, skill);
        if (session == null) return false;
        end(session, StopReason.REQUESTED);
        return true;
    }
    /** 動作中のセッションの数（試験と診断のため）。 */
    public static int count(AbilityActor actor) {
        var map = SESSIONS.get(actor.id());
        return map == null ? 0 : map.size();
    }

    private static void end(Session session, StopReason reason) {
        remove(session);
        try {
            session.behavior.stop(session, reason);
        } catch (RuntimeException e) {
            LOGGER.error("The behavior of {} threw in an external stop", session.skill, e);
        }
    }
    private static void remove(Session session) {
        var map = SESSIONS.get(session.actor.id());
        if (map != null && map.get(session.skill) == session) {
            map.remove(session.skill);
            if (map.isEmpty()) SESSIONS.remove(session.actor.id());
        }
    }

    @SubscribeEvent public static void externalSessionsOnServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) tick();
    }
    /** 試験のため、サーバーのtickの終わりと同じ処理を呼ぶ。 */
    public static void tickForTest() { tick(); }
    private static void tick() {
        for (var map : List.copyOf(SESSIONS.values())) {
            for (var session : List.copyOf(map.values())) {
                var actor = session.actor;
                if (!usable(actor) || SafetyService.interfered(actor)) { end(session, StopReason.INELIGIBLE); continue; }
                if (session.kind == ActivationKind.HELD && actor.level().getGameTime() - session.heartbeat > HEARTBEAT_TIMEOUT) {
                    end(session, StopReason.HEARTBEAT_LOST); continue;
                }
                boolean going;
                try {
                    going = session.behavior.tick(session);
                } catch (RuntimeException e) {
                    remove(session);
                    LOGGER.error("The behavior of {} threw in an external tick; its session ended", session.skill, e);
                    continue;
                }
                if (!going && session(actor, session.skill) == session) end(session, StopReason.FINISHED);
            }
        }
    }
    @SubscribeEvent public static void externalSessionsOnServerStopping(ServerStoppingEvent event) {
        for (var map : List.copyOf(SESSIONS.values())) for (var session : List.copyOf(map.values())) end(session, StopReason.INELIGIBLE);
        COOLDOWNS.clear();
    }

    /** 外部の主体の1回の発動とそのセッションの文脈。 */
    private static final class Session implements ActivationContext {
        final AbilityActor actor;
        final AbilityId skill;
        final ActivationKind kind;
        final SkillBehavior behavior;
        final ResourcePolicy policy;
        final long started;
        long heartbeat;

        Session(AbilityActor actor, AbilityId skill, ActivationKind kind, SkillBehavior behavior, ResourcePolicy policy, long started) {
            this.actor = actor; this.skill = skill; this.kind = kind; this.behavior = behavior; this.policy = policy;
            this.started = started; this.heartbeat = started;
        }
        @Override public AbilityId skill() { return skill; }
        @Override public AbilityActor actor() { return actor; }
        @Override public ServerPlayer naturalUser() { return null; }
        @Override public int slot() { return -1; }
        @Override public int age() { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, actor.level().getGameTime() - started)); }
        @Override public float proficiency() { return 0; }
        @Override public boolean pay(float cp, float overload) {
            if (!Float.isFinite(cp) || !Float.isFinite(overload) || cp < 0 || overload < 0) throw new IllegalArgumentException("Invalid cost " + cp + ", " + overload);
            var location = RegistryViews.location(skill);
            float scaledCp = (float) (cp * AcademyConfig.skillCpSpeed(location)), scaledOverload = (float) (overload * AcademyConfig.skillOverloadSpeed(location));
            return policy.pay(actor, skill, scaledCp, scaledOverload);
        }
        @Override public void addProficiency(float amount) {
            if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid proficiency " + amount);
        }
        @Override public void setCooldown(int ticks) {
            if (ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Invalid cooldown " + ticks);
            COOLDOWNS.computeIfAbsent(actor.id(), ignored -> new HashMap<>()).put(skill, actor.level().getGameTime() + ticks);
        }
    }
}
