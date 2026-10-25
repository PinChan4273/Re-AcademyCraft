package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.network.AbilityRegistryCheck;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ログインの時の、能力の登録簿の照合（1.4.0のS4、司令室11の裁定D7）。サーバーは登録簿の要約（カテゴリと、技能の名前・発動の種類・
 * カテゴリ）を送り、クライアントは自分の要約と比べた違いを返す。違いがあれば、違うIDを示して接続を断る。答えが来ないまま
 * {@link #ANSWER_TICKS}が過ぎたときも、確かめられなかったとして断る。サーバーだけのアドオンは、1.4.0では許さない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class RegistryCheck {
    private RegistryCheck() { }

    /** クライアントの答えを待つ長さ（tick）。 */
    public static final int ANSWER_TICKS = 1200;
    /** 切断の文に出す違いの数。 */
    public static final int SHOWN = 5;
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    /** その登録簿の要約（並びは登録の順）。 */
    public static List<String> entries(AbilityRegistry registry) {
        var out = new ArrayList<String>();
        for (var category : registry.categories()) out.add("category " + category.id());
        for (var skill : registry.skills())
            out.add("skill " + skill.id() + " " + skill.kind() + " " + (skill.category() == null ? "-" : skill.category().toString()));
        return out;
    }
    /** サーバーの要約に対する、クライアントの違い（サーバーにだけある物は「missing」、クライアントにだけある物は「extra」）。 */
    public static List<String> problems(List<String> server, List<String> client) {
        var mine = new LinkedHashSet<>(client);
        var theirs = new LinkedHashSet<>(server);
        var out = new ArrayList<String>();
        for (var entry : theirs) if (!mine.contains(entry)) out.add("missing " + entry);
        for (var entry : mine) if (!theirs.contains(entry)) out.add("extra " + entry);
        return out;
    }

    @SubscribeEvent public static void registryCheckOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.connection == null) return;
        PENDING.put(player.getUUID(), player.serverLevel().getGameTime() + ANSWER_TICKS);
        AcademyNetwork.send(player, new AbilityRegistryCheck(entries(AbilityRegistry.INSTANCE)));
    }
    @SubscribeEvent public static void registryCheckOnLogout(PlayerEvent.PlayerLoggedOutEvent event) { PENDING.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void registryCheckOnTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Long deadline = PENDING.get(player.getUUID());
        if (deadline != null && player.serverLevel().getGameTime() > deadline) {
            PENDING.remove(player.getUUID());
            player.connection.disconnect(Component.translatable("academy.registry.unverified"));
        }
    }
    /** 照合を待っているか（試験のため）。 */
    public static boolean pending(ServerPlayer player) { return PENDING.containsKey(player.getUUID()); }
    /** 試験のため、照合を待つ状態にする。 */
    public static void expectForTest(ServerPlayer player, long deadline) { PENDING.put(player.getUUID(), deadline); }

    /** クライアントの答え。待っていない答えは捨てる。違いがあれば断る（断ったらtrue）。 */
    public static boolean answer(ServerPlayer player, io.github.pinchan4273.reacademycraft.network.AbilityRegistryVerdict verdict) {
        if (PENDING.remove(player.getUUID()) == null || verdict.count() == 0) return false;
        var problems = verdict.problems();
        var shown = String.join(", ", problems.subList(0, Math.min(SHOWN, problems.size())));
        player.connection.disconnect(Component.translatable("academy.registry.mismatch", verdict.count(), shown));
        return true;
    }
}
