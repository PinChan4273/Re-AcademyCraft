package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * プリセットの編集（クライアント→サーバー）。1つの通知は1つの操作:
 * <ul>
 * <li>{@link #select}: 使うプリセットを替える（原作PresetData.switchFromClient）。</li>
 * <li>{@link #assign}: 1つのプリセットの1つのキーへ技能を置くか、空にする。</li>
 * </ul>
 * サーバーは受け取った時点の自分の状態へその操作だけを適用し、他のキーの値をクライアントから受け取らない。
 * そのため、応答の前に続けて別のキーを編集しても、先の編集を巻き戻さない。
 * 置ける技能は、プレイヤーのカテゴリの、実装済み・習得済み・レベルの足りた能動技能で、同じプリセットの他のキーに無いもの
 * （原作PresetEditUIの選択肢と同じ）。空にするのはいつでもよい。受け入れても断っても、サーバーの状態を送り返す。
 */
public record PresetEdit(boolean selection, int preset, int slot, @Nullable ResourceLocation skill) {
    private static final int PRESETS = PlayerAbilityData.PRESET_COUNT, KEYS = PlayerAbilityData.SLOT_COUNT, MAX_ID = 256;
    /** 1tickに受ける操作の数の上限。正当な編集（16キーと切り替え）を十分に上回り、大量の通知だけを捨てる。 */
    public static final int MAX_PER_TICK = 64;
    private static final Map<ServerPlayer, long[]> BUDGET = new WeakHashMap<>();

    public PresetEdit {
        if (preset < 0 || preset >= PRESETS) throw new IllegalArgumentException("Invalid preset " + preset);
        if (selection ? slot != 0 || skill != null : slot < 0 || slot >= KEYS) throw new IllegalArgumentException("Invalid preset slot " + slot);
        if (skill != null && skill.toString().length() > MAX_ID) throw new IllegalArgumentException("Invalid skill");
    }

    public static PresetEdit select(int preset) { return new PresetEdit(true, preset, 0, null); }

    /** skillがnullならそのキーを空にする。 */
    public static PresetEdit assign(int preset, int slot, @Nullable ResourceLocation skill) { return new PresetEdit(false, preset, slot, skill); }

    /** 通信の並び: 操作（0 切り替え、1 キーの設定）、プリセット。設定ならキーと技能のID（空にするなら""）。 */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(selection ? 0 : 1);
        buffer.writeByte(preset);
        if (!selection) {
            buffer.writeByte(slot);
            buffer.writeUtf(skill == null ? "" : skill.toString(), MAX_ID);
        }
    }

    public static PresetEdit decode(FriendlyByteBuf buffer) {
        int operation = buffer.readUnsignedByte(), preset = buffer.readUnsignedByte();
        if (operation == 0) return select(preset);
        if (operation != 1) throw new IllegalArgumentException("Unknown preset operation " + operation);
        int slot = buffer.readUnsignedByte();
        String id = buffer.readUtf(MAX_ID);
        ResourceLocation skill = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (!id.isEmpty() && skill == null) throw new IllegalArgumentException("Invalid skill identifier");
        return assign(preset, slot, skill);
    }

    /** そのスロットへその技能を置けるか。今のサーバーの状態で判断する。 */
    public static boolean canEquip(PlayerAbilityData data, int preset, int slot, ResourceLocation skill) {
        if (data.isReadOnly() || !data.hasAbility()) return false;
        var definition = SkillCatalog.find(skill);
        if (definition == null || !definition.offeredTo(data.getAbility()) || definition.passive()
                || !data.hasLearned(skill) || data.getLevel() < definition.level()) return false;
        for (int other = 0; other < KEYS; other++)
            if (other != slot && skill.equals(data.getSlot(preset, other))) return false;
        return true;
    }

    /** 同じtickのうちに受けた操作の数を数え、上限を超えたら捨てる。tickが変われば数え直す。 */
    private static boolean withinBudget(ServerPlayer player) {
        long[] budget = BUDGET.computeIfAbsent(player, ignored -> new long[]{Long.MIN_VALUE, 0});
        long now = player.serverLevel().getGameTime();
        if (budget[0] != now) { budget[0] = now; budget[1] = 0; }
        return ++budget[1] <= MAX_PER_TICK;
    }

    public void handle(@Nullable ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(data -> {
            if (data.isReadOnly() || !data.hasAbility() || !withinBudget(player)) return;
            if (selection) data.setCurrentPreset(preset);
            else if (skill == null) data.setSlot(preset, slot, null);
            else if (canEquip(data, preset, slot, skill)) data.setSlot(preset, slot, skill);
            // 受け入れても断っても、サーバーの状態を送り返す。クライアントのNBTは受け取らない。
            AbilitySyncEvents.sync(player, true);
        });
    }
}
