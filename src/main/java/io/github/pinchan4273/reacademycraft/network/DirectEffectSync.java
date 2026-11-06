package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 技能由来の直接効果の今の一覧（全種類。サーバー→そのプレイヤー、
 * v1.5.0）。変わったとき・ログイン・復活・次元の移動で送る。値は表示・自分の跳躍・視界のためだけで、効果の有無・時間はサーバーが決める。
 * kindは{@code skill.DirectEffects.Kind}の序数、remainingは残tickか-1（トグル中）。
 * （NeoForge版: payloadのIDは{@code academy:direct_effect_sync}、向きはサーバー→クライアントだけ（{@link AcademyNetwork}の登録）。
 * 件数と各値の範囲は、作るときと読むときの両方で確かめる（Forge版と同じ）。）
 */
public record DirectEffectSync(List<Effect> effects) implements AcademyPayload {
    public static final int MAX_EFFECTS = 64;
    public record Effect(int kind, int amplifier, int remaining) {
        public Effect {
            if (kind < 0 || kind > 63 || amplifier < 0 || amplifier > 255 || remaining < -1) throw new IllegalArgumentException("Invalid direct effect");
        }
    }
    public DirectEffectSync {
        if (effects.size() > MAX_EFFECTS) throw new IllegalArgumentException("Too many direct effects");
        effects = List.copyOf(effects);
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(effects.size());
        for (var effect : effects) { buffer.writeByte(effect.kind()); buffer.writeByte(effect.amplifier()); buffer.writeVarInt(effect.remaining() + 1); }
    }
    public static DirectEffectSync decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_EFFECTS) throw new IllegalArgumentException("Too many direct effects");
        var effects = new ArrayList<Effect>(size);
        for (int i = 0; i < size; i++) effects.add(new Effect(buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readVarInt() - 1));
        return new DirectEffectSync(effects);
    }
    public static void send(net.minecraft.server.level.ServerPlayer player, List<Effect> effects) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new DirectEffectSync(effects.size() > MAX_EFFECTS ? effects.subList(0, MAX_EFFECTS) : effects));
    }
}
