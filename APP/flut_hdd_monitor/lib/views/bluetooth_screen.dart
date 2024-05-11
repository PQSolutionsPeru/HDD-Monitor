import 'package:flutter/material.dart';
import 'package:flutter_blue/flutter_blue.dart';
import 'package:provider/provider.dart';
import 'package:flut_hdd_monitor/services/bluetooth_provider.dart';

class BluetoothScreen extends StatefulWidget {
  @override
  _BluetoothScreenState createState() => _BluetoothScreenState();
}

class _BluetoothScreenState extends State<BluetoothScreen> {
  final TextEditingController ssidController = TextEditingController();
  final TextEditingController passwordController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final bluetoothProvider = Provider.of<BluetoothProvider>(context);
    return Scaffold(
      appBar: AppBar(
        title: Text('Connect to ESP32 via BLE'),
      ),
      body: Column(
        children: <Widget>[
          Expanded(
            child: StreamBuilder<List<BluetoothDevice>>(
              stream: Stream.periodic(Duration(seconds: 2))
                  .asyncMap((_) => bluetoothProvider.devicesList),
              builder: (c, snapshot) {
                if (snapshot.hasData) {
                  return ListView(
                    children: snapshot.data!
                        .map((d) => ListTile(
                              title: Text(d.name),
                              subtitle: Text(d.id.toString()),
                              onTap: () => bluetoothProvider.connectToDevice(d),
                            ))
                        .toList(),
                  );
                } else {
                  return Center(child: CircularProgressIndicator());
                }
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: ssidController,
                  decoration: InputDecoration(
                    labelText: "SSID",
                  ),
                ),
                TextField(
                  controller: passwordController,
                  decoration: InputDecoration(
                    labelText: "Password",
                  ),
                ),
                SizedBox(height: 20),
                ElevatedButton(
                  onPressed: () {
                    if (bluetoothProvider.isConnected) {
                      bluetoothProvider.sendWiFiCredentials(ssidController.text, passwordController.text);
                    } else {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text("No hay conexión BLE establecida."),
                          duration: Duration(seconds: 3),
                        ),
                      );
                    }
                  },
                  child: Text('Save WiFi Settings'),
                )
              ],
            ),
          ),
        ],
      ),
    );
  }
}
