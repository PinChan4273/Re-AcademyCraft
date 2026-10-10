package io.github.pinchan4273.reacademycraft.develop;

import io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * サーバーが持つ、具体的な1台の開発機への結び付き。クライアントが送った段階・エネルギー・進捗や、任意のブロック位置からは
 * 決して作らない。
 */
public interface DevelopmentDevice {
    DeveloperTier tier();
    boolean valid(ServerPlayer player);
    int energy();
    boolean consume(int amount);
    default void close() { }

    static DevelopmentDevice handheld(ServerPlayer owner, InteractionHand hand) {
        var stack = owner.getItemInHand(hand);
        return new DevelopmentDevice() {
            @Override public DeveloperTier tier() { return DeveloperTier.PORTABLE; }
            @Override public boolean valid(ServerPlayer player) {
                return player == owner && owner.getItemInHand(hand) == stack && !stack.isEmpty()
                        && stack.getItem() instanceof DeveloperPortable;
            }
            @Override public int energy() { return DeveloperPortable.energy(stack); }
            @Override public boolean consume(int amount) {
                boolean consumed = DeveloperPortable.consume(stack, amount);
                if (consumed) owner.getInventory().setChanged();
                return consumed;
            }
        };
    }
}
