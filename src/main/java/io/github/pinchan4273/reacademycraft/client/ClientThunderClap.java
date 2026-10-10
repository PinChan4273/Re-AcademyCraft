package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.ThunderClapEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;

/** client専用のバニラの雷の描画と音。サーバーのブロックやentityは変更できない。 */
public final class ClientThunderClap {
    private static int nextId = -1_000_000;
    private ClientThunderClap() { }
    public static void receive(ThunderClapEffect packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(packet.dimension())
                || mc.player.position().distanceToSqr(packet.position()) > 160 * 160) return;
        var bolt = EntityType.LIGHTNING_BOLT.create(mc.level);
        if (bolt == null) return;
        // 負のローカルIDは、サーバーが割り当てる通常の正のIDと重ならない。
        // 他のMODのローカルentityも避ける。上限のある探索なので、埋まっていても止まらない。
        for (int attempts = 0; attempts < 256; attempts++) {
            if (nextId == Integer.MIN_VALUE) nextId = -1_000_000;
            int id = nextId--;
            if (mc.level.getEntity(id) != null) continue;
            // ForgeのPlayMessages.EntitySpawnと同じく、追加する前にentity自身のIDを設定する。
            // putNonPlayerEntityのkeyだけでは設定されない。
            bolt.setId(id); bolt.setPos(packet.position()); bolt.setOldPosAndRot(); bolt.setVisualOnly(true);
            mc.level.putNonPlayerEntity(id, bolt); return;
        }
    }
}
