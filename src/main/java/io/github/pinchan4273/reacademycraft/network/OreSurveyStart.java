package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** 所有者だけへのサーバーの許可。鉱石の位置・ワールドの変更・クライアントが選んだコストは含まない。 */
public record OreSurveyStart(UUID owner, Identifier dimension, float range, boolean advanced) implements AcademyPayload {
    public OreSurveyStart {
        Objects.requireNonNull(owner); Objects.requireNonNull(dimension);
        if (!Float.isFinite(range) || range < 15 || range > 30) throw new IllegalArgumentException("Invalid survey range");
    }
    public boolean belongsTo(UUID player, Identifier world) { return owner.equals(player) && dimension.equals(world); }
    public void encode(FriendlyByteBuf b) { b.writeUUID(owner); b.writeIdentifier(dimension); b.writeFloat(range); b.writeBoolean(advanced); }
    public static OreSurveyStart decode(FriendlyByteBuf b) { return new OreSurveyStart(b.readUUID(),b.readIdentifier(),b.readFloat(),b.readBoolean()); }
}
