package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * 技能の表示の現在位置と段階（Plasma Cannon: 0は溜め、1は飛行）。開始したクライアントへ送る。原作はプラズマ体の行き先を
 * 送り、数tickごとに位置を同期していた。サーバー自身の位置を毎tick送るほうが単純で、ずれることもない。
 */
public record SkillVisualMove(long token, Vec3 position, int phase) {
    public SkillVisualMove {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z) || phase < 0 || phase > 8)
            throw new IllegalArgumentException("Invalid skill visual move");
    }
    public void encode(FriendlyByteBuf b) { b.writeLong(token); b.writeDouble(position.x); b.writeDouble(position.y); b.writeDouble(position.z); b.writeVarInt(phase); }
    public static SkillVisualMove decode(FriendlyByteBuf b) {
        return new SkillVisualMove(b.readLong(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readVarInt());
    }
}
