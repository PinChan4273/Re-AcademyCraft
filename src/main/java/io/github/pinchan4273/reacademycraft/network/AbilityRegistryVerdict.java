package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * クライアントの能力の登録簿と、サーバーの要約との違い（クライアント→サーバー、1.4.0のS4、司令室11の裁定D7）。空なら一致。
 * 違いが多ければ、先頭の{@link #MAX_PROBLEMS}件だけを送る（数は別に送る）。
 */
public record AbilityRegistryVerdict(int count, List<String> problems) {
    public static final int MAX_PROBLEMS = 64;

    public AbilityRegistryVerdict {
        if (count < problems.size() || problems.size() > MAX_PROBLEMS || (count > 0 && problems.isEmpty())
                || problems.stream().anyMatch(p -> p.isEmpty() || p.length() > AbilityRegistryCheck.MAX_ENTRY + 8))
            throw new IllegalArgumentException("Invalid ability registry verdict");
        problems = List.copyOf(problems);
    }
    /** 違いの一覧から作る（多ければ先頭だけ）。 */
    public static AbilityRegistryVerdict of(List<String> problems) {
        return new AbilityRegistryVerdict(problems.size(), problems.subList(0, Math.min(MAX_PROBLEMS, problems.size())));
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(count);
        buffer.writeVarInt(problems.size());
        for (var problem : problems) buffer.writeUtf(problem, AbilityRegistryCheck.MAX_ENTRY + 8);
    }
    public static AbilityRegistryVerdict decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt(), size = buffer.readVarInt();
        if (size < 0 || size > MAX_PROBLEMS) throw new IllegalArgumentException("Invalid ability registry verdict");
        var problems = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) problems.add(buffer.readUtf(AbilityRegistryCheck.MAX_ENTRY + 8));
        return new AbilityRegistryVerdict(count, problems);
    }
}
