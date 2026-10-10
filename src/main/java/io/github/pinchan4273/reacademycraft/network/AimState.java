package io.github.pinchan4273.reacademycraft.network;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 照準を持つ技能の原作MSG_MADEALIVEとMSG_TERMINATED（術者のクライアントから見たもの）: サーバーは押し続け技能が本当に
 * 始まったことと、終わったことを術者へ伝える。照準の印は、技能が生きている間だけ正確に表示され、サーバーが拒否したキー
 * （クールダウン、CP不足、手に何も無い）が押されている間には出ない。SkillSoundsと同じく、各照準は自身の「まだ続いている」
 * 条件と共に保持し、サーバーtickごとに確かめるので、どの技能も終わり方ごとに知らせる必要がない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public record AimState(long token, ResourceLocation skill, boolean alive) {
    public void encode(FriendlyByteBuf b) { b.writeLong(token); b.writeResourceLocation(skill); b.writeBoolean(alive); }
    public static AimState decode(FriendlyByteBuf b) { return new AimState(b.readLong(), b.readResourceLocation(), b.readBoolean()); }

    private record Aiming(ServerPlayer caster, AimState end, BooleanSupplier lasts) { }
    private static final Map<Long, Aiming> AIMING = new LinkedHashMap<>();
    private static long nextToken;

    /** lastsがfalseになるまで、技能が照準中であることを術者へ伝える。 */
    public static long announce(ServerPlayer caster, ResourceLocation skill, BooleanSupplier lasts) {
        long token = ++nextToken;
        send(caster, new AimState(token, skill, true));
        AIMING.put(token, new Aiming(caster, new AimState(token, skill, false), lasts));
        return token;
    }
    public static boolean aiming(long token) { return AIMING.containsKey(token); }
    /**
     * 次のtickの確認を待たず、照準を1つすぐ終える。原作は順序が大事な箇所でMSG_MARK_ENDを自分で送る: Jet Engineは飛び立つのと
     * 同時に印を消し、1tickの遅れがあると飛行の始めに印が残ってしまう。
     */
    public static void end(long token) {
        var aiming = AIMING.remove(token);
        if (aiming != null) send(aiming.caster, aiming.end);
    }

    private static void send(ServerPlayer player, AimState packet) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    private static void check() {
        for (Iterator<Aiming> it = AIMING.values().iterator(); it.hasNext(); ) {
            var aiming = it.next();
            if (aiming.caster.isRemoved() || !aiming.lasts.getAsBoolean()) { it.remove(); send(aiming.caster, aiming.end); }
        }
    }
    /** テスト用の入口: サーバー1tick分の確認。 */
    public static void tickForTest() { check(); }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) { if (event.phase == TickEvent.Phase.END) check(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { AIMING.clear(); }
}
