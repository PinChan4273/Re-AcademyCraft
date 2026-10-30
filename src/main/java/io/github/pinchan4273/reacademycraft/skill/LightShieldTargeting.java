package io.github.pinchan4273.reacademycraft.skill;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Light Shieldの標的と目標の向きの選び方（工程2）。AUTOとMANUALの違いはここだけで、盾の防御・移動・描画・費用は{@link LightShield}が
 * 共通で行う。数値はPhase 0の比較の第一候補で、正式な値ではない。
 * <p>
 * <b>AUTO（P-B）</b>: 視線に依らず、基点から半径{@link LightShieldGeometry#SEARCH_RADIUS}の全方向（上下も）を探し、
 * ① 術者へ向かう飛来物（最接近までの時間が短い順）→ ② 今接触している敵 → ③ 術者を狙っているMob（{@code getTarget()}）→
 * ④ 直前（5秒以内）に攻撃してきた元、の順に選ぶ（②〜④は近い順）。脅威が無ければ、最後の向きのまま（Phase 0の待機案(a)）。
 * <p>
 * <b>MANUAL</b>: 視線の中心軸から{@link #MANUAL_CONE}度以内の有効な対象のうち、軸に最も近いものを選ぶ。角度の差が
 * {@link #MANUAL_TIE}度以内なら近いほうを選ぶ。対象が無ければ視線の方向を目指す。
 * <p>
 * <b>切替の抑え（ヒステリシス）</b>: 今の標的が有効な間は、AUTOでは、より優先の高い分類の脅威が現れたときか、同じ分類で
 * {@link #HOLD_TICKS}tick以上保った後に明らかに危ない（飛来物は5tick以上早く届く、他は距離が6割未満）ときだけ替える。
 * MANUALでは、今の標的が軸から{@link #MANUAL_KEEP}度以内にある間は、新しい対象の角度が{@link #MANUAL_SWITCH}度以上小さいときだけ替える。
 * <p>
 * 対象にしないもの: 術者自身、死んだもの、傍観者、落ちたアイテム、経験値オーブ、防具立て、術者自身の弾、術者が飼っている動物、
 * 術者の味方（{@code isAlliedTo}）。他のプレイヤーは、直前に術者を攻撃したときだけ。
 */
public final class LightShieldTargeting {
    private LightShieldTargeting() { }
    /** 分類（小さいほど優先）。MANUALの有効な対象には、狙っていない敵（{@link #ENEMY}）も含める。 */
    public static final int PROJECTILE = 0, CONTACT = 1, AIMING = 2, ATTACKER = 3, ENEMY = 4, NONE = -1;
    public static final int HOLD_TICKS = 10, RECENT_ATTACK_TICKS = 100;
    public static final double MANUAL_CONE = 30, MANUAL_TIE = 3, MANUAL_KEEP = 40, MANUAL_SWITCH = 8;
    /** 接触とみなす、術者と相手の当たり判定の箱の隙間。 */
    public static final double CONTACT_GAP = 1.0;
    /** 飛来物が術者へ向かっているとみなす、最接近の距離（基点から）。 */
    public static final double PROJECTILE_MISS = 1.5;

    /** セッションごとの追従の状態。モードを替えたときは{@link #clear}で捨てる。 */
    public static final class Memory {
        @Nullable UUID target; int kind = NONE; int since;
        public void clear() { target = null; kind = NONE; since = 0; }
        public @Nullable UUID target() { return target; }
        public int kind() { return kind; }
    }

    private record Candidate(Entity entity, int kind, double score, double angle) { }

    /**
     * このtickの目標の向き（単位ベクトル）。nullは「向きを変えない」（AUTOで脅威が無いとき）。memoryを更新する。
     */
    public static @Nullable Vec3 aim(ServerPlayer player, LightShieldMode mode, Memory memory, int tick) {
        Vec3 base = LightShieldGeometry.base(player.position());
        // getLookAngleは三角関数の表から作るので、長さがわずかに1でない（約2e-8短い）。盾の法線は単位ベクトルに保つ。
        Vec3 look = LightShieldGeometry.unit(player.getLookAngle(), new Vec3(0, 0, 1));
        Candidate best = null, current = null;
        var box = player.getBoundingBox().inflate(LightShieldGeometry.SEARCH_RADIUS + 2);
        for (Entity e : player.serverLevel().getEntities(player, box, e -> valid(player, e))) {
            double distance = center(e).distanceTo(base);
            boolean isCurrent = e.getUUID().equals(memory.target);
            if (distance > LightShieldGeometry.SEARCH_RADIUS + (isCurrent ? 2 : 0)) continue;
            Candidate c = classify(player, base, look, e, distance, mode);
            if (c == null) continue;
            if (isCurrent) current = c;
            if (mode == LightShieldMode.MANUAL && c.angle > MANUAL_CONE) continue;
            if (best == null || better(mode, c, best)) best = c;
        }
        Candidate chosen = choose(mode, memory, tick, best, current);
        if (chosen == null) {
            memory.clear();
            return mode == LightShieldMode.MANUAL ? look : null;
        }
        if (!chosen.entity.getUUID().equals(memory.target)) { memory.target = chosen.entity.getUUID(); memory.since = tick; }
        memory.kind = chosen.kind;
        return LightShieldGeometry.unit(center(chosen.entity).subtract(base), look);
    }

    private static @Nullable Candidate choose(LightShieldMode mode, Memory memory, int tick, @Nullable Candidate best, @Nullable Candidate current) {
        if (current == null) return best;
        // CR8-LS-F01（WORK-121）: MANUALの今の対象が視線の軸から40度より外へ出たら、他の候補が無くても先に解除する
        // （bestは30度の円錐の中だけなので、ここでcurrentになることはない。nullなら視線へ戻る）。
        if (mode == LightShieldMode.MANUAL && current.angle > MANUAL_KEEP) return best;
        if (best == null || best.entity == current.entity) return current;
        if (mode == LightShieldMode.MANUAL) return best.angle < current.angle - MANUAL_SWITCH ? best : current;
        if (best.kind < current.kind) return best;
        if (best.kind > current.kind || tick - memory.since < HOLD_TICKS) return current;
        boolean clearly = best.kind == PROJECTILE ? best.score < current.score - 5 : best.score < current.score * .6;
        return clearly ? best : current;
    }
    private static boolean better(LightShieldMode mode, Candidate a, Candidate b) {
        if (mode == LightShieldMode.MANUAL) {
            if (Math.abs(a.angle - b.angle) <= MANUAL_TIE) return a.score < b.score;
            return a.angle < b.angle;
        }
        return a.kind != b.kind ? a.kind < b.kind : a.score < b.score;
    }

    /** 分類と得点（飛来物は最接近までのtick、他は距離）。どれにも当たらなければnull。 */
    private static @Nullable Candidate classify(ServerPlayer player, Vec3 base, Vec3 look, Entity e, double distance, LightShieldMode mode) {
        double angle = LightShieldGeometry.degrees(look, LightShieldGeometry.unit(center(e).subtract(base), look));
        if (e instanceof Projectile) {
            Vec3 v = e.getDeltaMovement();
            double speed2 = v.lengthSqr();
            if (speed2 < .0025) return null;
            Vec3 toward = base.subtract(e.position());
            double along = v.dot(toward);
            if (along <= 0) return null;
            double time = along / speed2;
            if (toward.subtract(v.scale(time)).length() > PROJECTILE_MISS) return null;
            return new Candidate(e, PROJECTILE, time, angle);
        }
        if (!(e instanceof LivingEntity living)) return null;
        boolean attacker = recentAttacker(player, living);
        boolean aiming = e instanceof Mob mob && mob.getTarget() == player;
        boolean enemy = attacker || aiming || (e instanceof Enemy && !(e instanceof Player));
        if (!enemy) return null;
        if (player.getBoundingBox().inflate(CONTACT_GAP).intersects(e.getBoundingBox())) return new Candidate(e, CONTACT, distance, angle);
        if (aiming) return new Candidate(e, AIMING, distance, angle);
        if (attacker) return new Candidate(e, ATTACKER, distance, angle);
        // 狙っていない敵: MANUALでは視線で選べる有効な対象。AUTOでは脅威として扱わない。
        return mode == LightShieldMode.MANUAL ? new Candidate(e, ENEMY, distance, angle) : null;
    }
    private static boolean recentAttacker(ServerPlayer player, LivingEntity e) {
        return player.getLastHurtByMob() == e && player.tickCount - player.getLastHurtByMobTimestamp() < RECENT_ATTACK_TICKS;
    }
    /** 盾が向く相手かどうかの、分類の前の除外。 */
    public static boolean valid(ServerPlayer player, Entity e) {
        if (e == player || !e.isAlive() || e.isSpectator() || e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof ArmorStand) return false;
        if (e instanceof Projectile projectile && projectile.getOwner() == player) return false;
        if (e instanceof OwnableEntity owned && player.getUUID().equals(owned.getOwnerUUID())) return false;
        return !e.isAlliedTo(player);
    }
    /** 盾が向く点: 当たり判定の箱の中心（飛来物も同じ）。 */
    public static Vec3 center(Entity e) { return e.getBoundingBox().getCenter(); }
}
