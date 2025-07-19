package com.bpm.sumorobotcontrollapp;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;


public class ControlActivity extends AppCompatActivity {
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;

    private int lastLeft = 0;
    private int lastRight = 0;
    private long lastSendTime = 0;


    private static final long THROTTLE_DELAY_MS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_control);

        // Przyciski A i B
        Button buttonA = findViewById(R.id.button_a);
        Button buttonB = findViewById(R.id.button_b);

        buttonA.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendCommand(lastLeft, lastRight, 1);
                    break;
                case MotionEvent.ACTION_UP:
                    sendCommand(lastLeft, lastRight, 3);
                    break;
            }
            return true;
        });

        buttonB.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendCommand(lastLeft, lastRight, 2);
                    break;
                case MotionEvent.ACTION_UP:
                    sendCommand(lastLeft, lastRight, 3);
                    break;
            }
            return true;
        });

        JoystickView joystick = findViewById(R.id.JoystickView);
        joystick.setAutoReCenterButton(true); // lub false – zależnie od preferencji

        joystick.setOnMoveListener((angle, strength) -> {
            int[] motorValues = calculateMotorValues(angle, strength);
            int left = motorValues[0];
            int right = motorValues[1];

            long now = System.currentTimeMillis();

            // Natychmiast wyślij 0:0:0 przy puszczeniu (siła == 0)
            if (strength == 0) {
                if (lastLeft != 0 || lastRight != 0) {
                    lastLeft = 0;
                    lastRight = 0;
                    lastSendTime = now;
                    sendCommand(0, 0, 0);
                }
                return;
            }

            if ((left != lastLeft || right != lastRight) && (now - lastSendTime >= THROTTLE_DELAY_MS)) {
                lastLeft = left;
                lastRight = right;
                lastSendTime = now;

                sendCommand(left, right, 0);
            }
        }, 100);

        joystick.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Poczekaj krótko, aż joystick wróci do centrum
                v.postDelayed(() -> {
                    if (joystick.getStrength() == 0) {
                        // Wymuszone wysłanie zer, nawet jeśli były już ostatnio wysłane
                        lastLeft = 0;
                        lastRight = 0;
                        lastSendTime = 0;
                        sendCommand(0, 0, 0);
                        Log.d("JOYSTICK", "Wymuszone wysłanie 0:0:0 po puszczeniu");
                    }
                }, 50); // 50ms opóźnienia – joystick może jeszcze animować powrót
            }
            return false;
        });

        // Odbierz GATT i TX z poprzedniej aktywności
        bluetoothGatt = BluetoothGattHolder.getGatt();
        txCharacteristic = BluetoothGattHolder.getTxCharacteristic();
    }

    private void sendCommand(int left, int right, int button) {
        if (txCharacteristic != null && bluetoothGatt != null) {
            String command = left + ":" + right + ":" + button + "\n";
            byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
            txCharacteristic.setValue(bytes);
            txCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            boolean result = bluetoothGatt.writeCharacteristic(txCharacteristic);

            Log.d("BLE", "Wysłano: " + command + " | Result: " + result);
        } else {
            Log.e("BLE", "BLE TX lub GATT null");
        }
    }

    private int[] calculateMotorValues(int angle, int strength) {
        double rad = Math.toRadians(angle);

        // Odwrócenie kierunków
        double turn = -Math.cos(rad);     // zamieniamy lewo ↔ prawo
        double forward = -Math.sin(rad);  // zamieniamy góra ↔ dół

        int left = (int) (strength * (forward - turn));
        int right = (int) -(strength * (forward + turn));

        left = Math.max(-220, Math.min(220, left));
        right = Math.max(-220, Math.min(220, right));

        return new int[]{left, right};
    }
}