package io.github.pinchan4273.reacademycraft.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** 1.21.1 にあって 26.2 で無くなった vanilla の小さなメソッドを、同じ動きで保つ。 */
public final class VanillaCompat {
    private VanillaCompat() { }

    /** 1.21.1 の ServerPlayer#playNotifySound（本人にだけ、本人の位置で鳴らす）。 */
    public static void playNotifySound(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source,
                player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }
}
