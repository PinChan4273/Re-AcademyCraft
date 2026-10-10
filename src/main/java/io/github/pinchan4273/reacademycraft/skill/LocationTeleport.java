package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

/**
 * 原作LocationTeleport（WeAthFolD）: テレポーターのレベル3技能。
 *
 * キーは発動せず、名前付きの地点の一覧を開く: 術者は立っている場所を登録し、地点を忘れ、またはそこへテレポートできる。
 * テレポートには、5ブロック以内の大きすぎない（幅 * 幅 * 高さが80未満の）生き物をすべて連れて行き、それぞれ術者からのずれを保つ。
 * ディメンションをまたぐには熟練度80%超が要る。テレポートは原作の強制consumeで払う: オーバーロード240と、
 * max(8, sqrt(min(800, 距離)))の1単位ごとにlerp(200, 150, exp)のCP（ディメンションをまたぐと2倍）。経験値は0.015（200ブロック以上なら
 * 0.03）で、その経験値の後にクールダウンlerp(30, 20, exp)を取る。
 *
 * 原作がクライアントを信じていた所は、ここではサーバーが決める。原作のperformメッセージはクライアントから地点全体（任意の名前・
 * ディメンション・座標）を運び、サーバーは一覧・CP・ディメンション越えの熟練度、技能を習得しているかさえ確かめずにそこへテレポートした。
 * ここではクライアントは登録済みの地点を番号で指し、サーバーがすべてを自身で確かめる。地点はプレイヤーの永続Forgeデータにあり、
 * 原作のデータ部分と同じく死亡を越えて付いて行く。
 */
public final class LocationTeleport {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("academy", "location_teleport");
    /** 名前に対する原作のtake(16)。 */
    public static final int NAME_LENGTH = 16;
    /** 原作には無い上限（原作は無制限）: 一覧はプレイヤーのデータへ書き、全体を送るため。 */
    public static final int MAX_LOCATIONS = 64;
    /** 原作WorldUtils.getEntities(player, 5, teleportSelector)。 */
    public static final double CARRY_RANGE = 5;
    public static final float CARRY_VOLUME = 80;
    public static final float OVERLOAD = 240;
    private static final String TAG = "academy_locations";

    public record Location(String name, ResourceKey<Level> dimension, float x, float y, float z) {
        public Vec3 position() { return new Vec3(x, y, z); }
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("name", name); tag.putString("dim", dimension.location().toString());
            tag.putFloat("x", x); tag.putFloat("y", y); tag.putFloat("z", z);
            return tag;
        }
        @Nullable static Location load(CompoundTag tag) {
            var dim = ResourceLocation.tryParse(tag.getString("dim"));
            if (dim == null) return null;
            return new Location(clean(tag.getString("name")), ResourceKey.create(Registries.DIMENSION, dim),
                    tag.getFloat("x"), tag.getFloat("y"), tag.getFloat("z"));
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(name, NAME_LENGTH * 4); buffer.writeResourceLocation(dimension.location());
            buffer.writeFloat(x); buffer.writeFloat(y); buffer.writeFloat(z);
        }
        public static Location decode(FriendlyByteBuf buffer) {
            return new Location(clean(buffer.readUtf(NAME_LENGTH * 4)), ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
    }

    private LocationTeleport() { }
    private static PlayerAbilityData data(Player p) { return p.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null); }

    /** 原作take(16)。原作と同じく文字数で数え、制御文字は除く。 */
    public static String clean(String name) {
        var builder = new StringBuilder();
        name.codePoints().filter(c -> !Character.isISOControl(c)).limit(NAME_LENGTH).forEach(builder::appendCodePoint);
        return builder.toString();
    }

    public static List<Location> locations(Player player) {
        var list = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList(TAG, Tag.TAG_COMPOUND);
        var result = new ArrayList<Location>(list.size());
        for (int i = 0; i < list.size() && result.size() < MAX_LOCATIONS; i++) {
            var location = Location.load(list.getCompound(i));
            if (location != null) result.add(location);
        }
        return result;
    }
    private static void store(ServerPlayer player, List<Location> locations) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var list = new ListTag();
        for (var location : locations) list.add(location.save());
        persisted.put(TAG, list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** キーは技能を装備し能力がオンの間だけ存在する。サーバーはすべての要求に同じ条件を課す。 */
    public static boolean usable(ServerPlayer player, @Nullable PlayerAbilityData data) {
        if (data == null || !player.isAlive() || data.isReadOnly() || !data.isActive()
                || !AbilityCategory.TELEPORTER.id().equals(data.getAbility()) || !data.hasLearned(ID)) return false;
        for (int slot = 0; slot < 4; slot++) if (ID.equals(data.getSlot(data.getCurrentPreset(), slot))) return true;
        return false;
    }

    /** 原作hAdd: 術者の立っている場所をfloatで、名前を付けて登録する。 */
    public static boolean add(ServerPlayer player, String name) {
        if (!usable(player, data(player))) return false;
        var locations = locations(player);
        if (locations.size() >= MAX_LOCATIONS) return false;
        locations.add(new Location(clean(name), player.level().dimension(),
                (float) player.getX(), (float) player.getY(), (float) player.getZ()));
        store(player, locations);
        return true;
    }
    /** 原作hRemove: 後ろのものは、原作が番号を振り直すのと同じく繰り上がる。 */
    public static boolean remove(ServerPlayer player, int index) {
        if (!usable(player, data(player))) return false;
        var locations = locations(player);
        if (index < 0 || index >= locations.size()) return false;
        locations.remove(index);
        store(player, locations);
        return true;
    }

    public static boolean crossesDimension(Player player, Location location) { return !player.level().dimension().equals(location.dimension()); }
    /** 原作canCrossDimension: 熟練度80%超。 */
    public static boolean canCrossDimension(float exp) { return exp > .8f; }
    /** 原作getConsumptionのCP。 */
    public static float cpCost(float exp, double distance, boolean crossDimension) {
        return ArcGen.lerp(200, 150, exp) * (crossDimension ? 2 : 1) * Math.max(8f, (float) Math.sqrt(Math.min(800, distance)));
    }
    public static float cpCost(Player player, PlayerAbilityData data, Location location) {
        return cpCost(data.getProficiency(ID), player.position().distanceTo(location.position()), crossesDimension(player, location));
    }
    /** 原作getPerformStat: テレポートできればnull、できなければその理由（翻訳キー）。 */
    @Nullable public static String refusal(Player player, PlayerAbilityData data, Location location) {
        if (crossesDimension(player, location) && !canCrossDimension(data.getProficiency(ID))) return "academy.loctele.err_exp";
        if (!player.getAbilities().instabuild && !data.canConsumeCp(ID, cpCost(player, data, location))) return "academy.loctele.err_cp";
        return null;
    }

    /** この番号の登録済み地点に対する原作perform。失敗の翻訳キー、または術者が移動した後は""を返す。 */
    public static String perform(ServerPlayer player, int index) {
        var data = data(player);
        if (!usable(player, data)) return "academy.cast.unlearned";
        // 原作のキーは妨害されている間は一覧を開けない。既に開いている一覧からもどこへも行けず、他の拒否と同じく何も言わない。
        if (data.isInterfering()) return "";
        if (data.isOverloadLocked()) return "academy.cast.overload";
        if (data.getCooldown(ID) > 0) return "academy.cast.cooldown";
        var locations = locations(player);
        if (index < 0 || index >= locations.size()) return "academy.loctele.err_missing";
        var location = locations.get(index);
        var refusal = refusal(player, data, location);
        if (refusal != null) return refusal;
        ServerLevel destination = player.server.getLevel(location.dimension());
        if (destination == null) return "academy.loctele.err_missing";
        boolean cross = destination != player.level();
        var origin = player.position();
        var target = location.position();
        if (!cross) {
            target = TPSkillHelper.permit(player, target);
            if (target == null) return "";
        }
        // 原作teleportSelector: 生き物で、幅 * 幅 * 高さが80未満、5ブロック以内。
        var carried = player.level().getEntities(player, new AABB(origin, origin).inflate(CARRY_RANGE),
                e -> e.isAlive() && !e.isSpectator() && TPSkillHelper.living(e)
                        && e.getBbWidth() * e.getBbWidth() * e.getBbHeight() < CARRY_VOLUME
                        && e.position().distanceToSqr(origin) <= CARRY_RANGE * CARRY_RANGE);
        move(player, destination, target);
        // Forgeのディメンション移動のイベントが術者を拒否することがある。その場合は何も払わず、何も連れて行かない。
        if (player.level() != destination) return "";
        float exp = data.getProficiency(ID);
        double distance = origin.distanceTo(location.position());
        data.consumeWithForce(ID, cpCost(exp, distance, cross), OVERLOAD, player.getAbilities().instabuild);
        for (var entity : carried) move(entity, destination, target.add(entity.position().subtract(origin)));
        data.addProficiency(ID, distance >= 200 ? .03f : .015f);
        data.setCooldown(ID, (int) ArcGen.lerp(30, 20, data.getProficiency(ID)));
        TPSkillHelper.incrTPCount(player);
        // 原作は術者自身の画面からACSounds.playClientでtp.tpを鳴らす: 術者だけが聞く。
        player.playNotifySound(io.github.pinchan4273.reacademycraft.world.AcademySounds.TP_TP.get(), net.minecraft.sounds.SoundSource.AMBIENT, .5f, 1f);
        AbilitySyncEvents.sync(player, true);
        return "";
    }

    private static void move(Entity entity, ServerLevel destination, Vec3 target) {
        if (entity.isPassenger()) entity.stopRiding();
        if (entity.level() == destination) {
            if (entity instanceof ServerPlayer p) TPSkillHelper.teleport(p, target);
            else { entity.teleportTo(target.x, target.y, target.z); entity.fallDistance = 0; }
        } else if (entity instanceof ServerPlayer p) {
            p.teleportTo(destination, target.x, target.y, target.z, p.getYRot(), p.getXRot());
            p.fallDistance = 0;
        } else {
            // 原作は既定のteleporterでchangeDimensionを呼ぶ。1.12ではそれがポータルの探索を通り（ポータルを作ることもある）、その後で既に取り除かれた
            // 古いエンティティの位置を設定する。ここではエンティティを原作が意図した場所へ直接運ぶ。
            entity.changeDimension(destination, new ITeleporter() {
                @Override public PortalInfo getPortalInfo(Entity e, ServerLevel level, java.util.function.Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                    return new PortalInfo(target, Vec3.ZERO, e.getYRot(), e.getXRot());
                }
                @Override public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) { return false; }
            });
        }
    }
}
