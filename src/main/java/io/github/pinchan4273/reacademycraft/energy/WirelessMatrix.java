package io.github.pinchan4273.reacademycraft.energy;

/**
 * 原作IWirelessMatrix: 無線ネットワークの持ち主。容量は参加できるnodeの数、範囲はnodeが離れられる距離、
 * 帯域は1tickあたりに動かす量。
 */
public interface WirelessMatrix {
    int wirelessCapacity();
    double wirelessRange();
    int wirelessBandwidthMilliIF();
}
