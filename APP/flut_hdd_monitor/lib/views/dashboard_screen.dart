import 'package:flut_hdd_monitor/main.dart';
import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flut_hdd_monitor/views/bluetooth_screen.dart';


class DashboardScreen extends StatelessWidget {
  const DashboardScreen({Key? key}) : super(key: key);

  void _logout(BuildContext context) async {
    await FirebaseAuth.instance.signOut();
    Navigator.of(context).pushReplacementNamed('/login');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dashboard', style: TextStyle(color: Colors.white, fontSize: 20)),
        backgroundColor: Colors.redAccent,
        actions: [
          IconButton(
            icon: const Icon(Icons.bluetooth),
            onPressed: () {
              Navigator.push(context, MaterialPageRoute(builder: (context) => BluetoothScreen()));
            },
            tooltip: 'Configurar Bluetooth',
          ),
          IconButton(
            icon: const Icon(Icons.exit_to_app),
            onPressed: () => _logout(context),
            tooltip: 'Logout',
          ),
        ],
      ),
      body: Container(
        color: Colors.white,
        child: SingleChildScrollView(
          child: Column(
            children: [
              _buildPanel(context),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPanel(BuildContext context) {
    return StreamBuilder<DocumentSnapshot>(
      stream: FirebaseFirestore.instance
          .doc('hdd-monitor/accounts/clients/client_1/panels/panel_1')
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.hasError) return Text('Error: ${snapshot.error}', style: TextStyle(color: Colors.black, fontSize: 16));
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Text('Cargando panel...', style: TextStyle(color: Colors.black, fontSize: 16));
        }
        if (!snapshot.hasData || !snapshot.data!.exists) {
          return const Text('Datos del panel no encontrados', style: TextStyle(color: Colors.black, fontSize: 16));
        }
        var panelData = snapshot.data!.data() as Map<String, dynamic>? ?? {};

        return Card(
          color: Colors.white,
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.dashboard, color: Colors.black),
                title: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(panelData['name'] ?? 'Panel Desconocido', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 18)),
                          Text('Ubicación: ${panelData['location']}', style: TextStyle(color: Colors.black, fontSize: 16)),
                        ],
                      ),
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _relayStatus(context, 'Alarma'),
                        _relayStatus(context, 'Problema'),
                        _relayStatus(context, 'Supervision'),
                      ],
                    ),
                  ],
                ),
              ),
              ExpansionTile(
                title: const Text('Detalles', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 14.5)),
                children: [_buildLastEvent(context)],
                iconColor: Colors.black,
                collapsedIconColor: Colors.black,
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _relayStatus(BuildContext context, String relayName) {
    return StreamBuilder<DocumentSnapshot>(
      stream: FirebaseFirestore.instance.doc('hdd-monitor/accounts/clients/client_1/panels/panel_1/relays/$relayName').snapshots(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const CircularProgressIndicator();
        }
        if (snapshot.hasData) {
          var data = snapshot.data!.data() as Map<String, dynamic>? ?? {};
          bool isOk = data['status'] == 'OK';
          // Trigger notification on status change
          if (!isOk) {
            NotificationService.showNotification(
              1, 
              'Alerta de Relay', 
              'El estado del relay $relayName ha cambiado a no OK!', 
              'relay_$relayName'
            );
          }
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 2.0),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(isOk ? Icons.check_circle : Icons.remove_circle, color: isOk ? Colors.green : Colors.red, size: 24),
                const SizedBox(width: 8),
                Text(relayName, style: TextStyle(color: Colors.black, fontSize: 16)),
              ],
            ),
          );
        } else {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 2.0),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: const [
                Icon(Icons.error, color: Colors.red, size: 24),
                SizedBox(width: 8),
                Text('Error loading', style: TextStyle(color: Colors.black, fontSize: 16)),
              ],
            ),
          );
        }
      },
    );
  }

  Widget _buildLastEvent(BuildContext context) {
    return StreamBuilder<DocumentSnapshot>(
      stream: FirebaseFirestore.instance
          .doc('hdd-monitor/accounts/clients/client_1/panels/panel_1/panel_events/event_1')
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const CircularProgressIndicator();
        }
        if (snapshot.hasData) {
          var eventData = snapshot.data!.data() as Map<String, dynamic>? ?? {};
          return ListTile(
            title: Text('Último Evento: ${eventData['type'] ?? 'Desconocido'}', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 16)),
            subtitle: Text('Descripción: ${eventData['description']} - ${eventData['date_time']}', style: TextStyle(fontStyle: FontStyle.italic, color: Colors.black, fontSize: 15)),
          );
        } else {
          return const ListTile(
            title: Text('No hay eventos recientes', style: TextStyle(color: Colors.black, fontSize: 16)),
          );
        }
      },
    );
  }
}
