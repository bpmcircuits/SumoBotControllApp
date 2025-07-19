package com.bpm.sumorobotcontrollapp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class BluetoothGattHolder {
    private static BluetoothGatt gatt;
    private static BluetoothGattCharacteristic txChar;

    public static void set(BluetoothGatt g, BluetoothGattCharacteristic tx) {
        gatt = g;
        txChar = tx;
    }

    public static BluetoothGatt getGatt() {
        return gatt;
    }

    public static BluetoothGattCharacteristic getTxCharacteristic() {
        return txChar;
    }

    public static void clear() {
        gatt = null;
        txChar = null;
    }
}
