import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_blue/flutter_blue.dart';
import 'package:collection/collection.dart';


class BluetoothProvider with ChangeNotifier {
  FlutterBlue _flutterBlue = FlutterBlue.instance;
  BluetoothDevice? _connectedDevice;
  List<BluetoothDevice> devicesList = [];

  BluetoothProvider() {
    _flutterBlue.state.listen((state) {
      if (state == BluetoothState.off) {
        // Manejar Bluetooth apagado
      } else if (state == BluetoothState.on) {
        startScan();
      }
      notifyListeners();
    });
  }

  void startScan() {
    _flutterBlue.startScan(timeout: Duration(seconds: 4));
    _flutterBlue.scanResults.listen((results) {
      for (ScanResult result in results) {
        if (!devicesList.contains(result.device)) {
          devicesList.add(result.device);
          notifyListeners();
        }
      }
    });
  }

  void connectToDevice(BluetoothDevice device) async {
    await device.connect();
    _connectedDevice = device;
    notifyListeners();
  }

  bool get isConnected => _connectedDevice != null;

  void sendWiFiCredentials(String ssid, String password) async {
    if (_connectedDevice != null) {
      List<BluetoothService> services = await _connectedDevice!.discoverServices();
      BluetoothService? targetService = services.firstWhereOrNull(
        (s) => s.uuid.toString() == "your-service-uuid", // Asegúrate de reemplazar esto por tu UUID
      );

      if (targetService != null) {
        BluetoothCharacteristic? ssidCharacteristic = targetService.characteristics.firstWhereOrNull(
          (c) => c.uuid.toString() == "your-ssid-characteristic-uuid", // Asegúrate de reemplazar esto por tu UUID
        );
        BluetoothCharacteristic? passwordCharacteristic = targetService.characteristics.firstWhereOrNull(
          (c) => c.uuid.toString() == "your-password-characteristic-uuid", // Asegúrate de reemplazar esto por tu UUID
        );

        if (ssidCharacteristic != null && passwordCharacteristic != null) {
          await ssidCharacteristic.write(utf8.encode(ssid), withoutResponse: true);
          await passwordCharacteristic.write(utf8.encode(password), withoutResponse: true);
          print("Credentials sent");
        } else {
          print("SSID or Password Characteristic not found");
        }
      } else {
        print("Target Service not found");
      }
    } else {
      print("No device connected");
    }
  }



  @override
  void dispose() {
    _flutterBlue.stopScan();
    super.dispose();
  }
}
