package io.github.pinchan4273.reacademycraft.energy;

/**
 * 原作IWirelessNode: 機械が繋ぐ先。容量は繋げる数、範囲は離れられる距離、帯域は1tickあたりに動かす量、
 * バッファは保持できる量。
 */
public interface WirelessNode {
    int wirelessCapacity();
    double wirelessRange();
    int wirelessBandwidthMilliIF();
    int wirelessBufferMilliIF();
}
