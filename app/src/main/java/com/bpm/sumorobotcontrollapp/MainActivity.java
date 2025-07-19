package com.bpm.sumorobotcontrollapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ListView deviceListView;
    private ArrayAdapter<String> deviceListAdapter;
    private List<BluetoothDevice> foundDevices = new ArrayList<>();

    private static final UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID UART_TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID UART_RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            String name = device.getName();
            String address = device.getAddress();
            String displayName = (name != null ? name : "Nieznane urządzenie") + " (" + address + ")";

            if (deviceListAdapter.getPosition(displayName) == -1 && displayName.contains("micro")) {
                foundDevices.add(device);
                deviceListAdapter.add(displayName);
                deviceListAdapter.notifyDataSetChanged();
            }

            Log.d("BLE", "Znaleziono: " + displayName);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceListView = findViewById(R.id.device_list);
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        deviceListView.setAdapter(deviceListAdapter);

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = foundDevices.get(position);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Toast.makeText(this, "Łączenie z " + device.getName(), Toast.LENGTH_SHORT).show();
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        });

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        setupScanButton();
    }

    private void setupScanButton() {
        Button scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> {
            if (!hasPermissions()) {
                showPermissionsDialog();
                return;
            }

            if (!isLocationEnabled()) {
                Toast.makeText(this, "Lokalizacja jest wymagana do skanowania BLE", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
                return;
            }

            Log.d("BLE", "Rozpoczynanie skanowania...");
            deviceListAdapter.clear();
            foundDevices.clear();

            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            bleScanner.startScan(bleScanCallback);
            Toast.makeText(this, "Skanowanie...", Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(() -> {
                bleScanner.stopScan(bleScanCallback);
                Toast.makeText(this, "Skanowanie zakończone", Toast.LENGTH_SHORT).show();
            }, 5000);
        });
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Połączono z GATT. Rozpoczynanie odkrywania usług...");
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Rozłączono z GATT.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService uartService = gatt.getService(UART_SERVICE_UUID);
                if (uartService != null) {
                    BluetoothGattCharacteristic txChar = uartService.getCharacteristic(UART_TX_UUID);

                    if (txChar != null) {
                        BluetoothGattHolder.set(gatt, txChar);

                        runOnUiThread(() -> {
                            Intent intent = new Intent(MainActivity.this, ControlActivity.class);
                            startActivity(intent);
                        });
                    } else {
                        Log.e("BLE", "Nie znaleziono TX characteristic.");
                    }
                } else {
                    Log.e("BLE", "Nie znaleziono UART service.");
                }
            } else {
                Log.e("BLE", "Błąd podczas discoverServices: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(UART_RX_UUID)) {
                String received = new String(characteristic.getValue());
                Log.d("BLE", "Odebrano: " + received);
            }
        }
    };

    private void sendMessage(String message) {
        if (txCharacteristic != null && bluetoothGatt != null) {
            txCharacteristic.setValue(message.getBytes(StandardCharsets.UTF_8));
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.writeCharacteristic(txCharacteristic);
            Log.d("BLE", "Wysłano: " + message);
        } else {
            Log.e("BLE", "Brak charakterystyki TX lub GATT.");
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean isLocationEnabled() {
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
    }

    private void showPermissionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Wymagane uprawnienia")
                .setMessage("Aby aplikacja działała poprawnie, musisz ręcznie przyznać uprawnienia w ustawieniach.")
                .setPositiveButton("Przejdź do ustawień", (dialog, which) -> redirectToAppSettings())
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void redirectToAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                showPermissionsDialog();
            }
        }
    }
}