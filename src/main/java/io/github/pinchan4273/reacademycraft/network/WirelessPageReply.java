package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 原作UserResultとNodeResult: 無線ページが表示するもの。ブロックが参加しているnodeまたはネットワークと、参加できる候補。
 * それぞれ名前とパスワードの有無を持つ。
 */
public record WirelessPageReply(BlockPos self, boolean node, @Nullable Entry linked, List<Entry> avail) {
    public static final int MAX_ENTRIES = 64, MAX_NAME = 32;
    public record Entry(BlockPos pos, String name, boolean encrypted) {
        public Entry {
            Objects.requireNonNull(pos); Objects.requireNonNull(name);
            if (name.length() > MAX_NAME) throw new IllegalArgumentException("Wireless name too long");
        }
        void encode(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeUtf(name, MAX_NAME); b.writeBoolean(encrypted); }
        static Entry decode(FriendlyByteBuf b) { return new Entry(b.readBlockPos(), b.readUtf(MAX_NAME), b.readBoolean()); }
    }
    public WirelessPageReply {
        Objects.requireNonNull(self);
        avail = List.copyOf(avail);
        if (avail.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many wireless entries");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(self); b.writeBoolean(node);
        b.writeBoolean(linked != null); if (linked != null) linked.encode(b);
        b.writeVarInt(avail.size()); for (var entry : avail) entry.encode(b);
    }
    public static WirelessPageReply decode(FriendlyByteBuf b) {
        var self = b.readBlockPos(); boolean node = b.readBoolean();
        var linked = b.readBoolean() ? Entry.decode(b) : null;
        int count = b.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many wireless entries");
        var avail = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) avail.add(Entry.decode(b));
        return new WirelessPageReply(self, node, linked, avail);
    }
}
