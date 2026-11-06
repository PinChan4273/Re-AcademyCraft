package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * このmodの通知。NeoForge 1.21.1のpayloadで、型（ID）は{@link AcademyNetwork#type}がclassの名前から作る。
 * 中身の読み書きは、各通知のencode・decode（Forge版と同じ並び）。
 */
public interface AcademyPayload extends CustomPacketPayload {
    @Override
    @SuppressWarnings("unchecked")
    default Type<? extends CustomPacketPayload> type() {
        return AcademyNetwork.type((Class<AcademyPayload>) getClass());
    }
}
