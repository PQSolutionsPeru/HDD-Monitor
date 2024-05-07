import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dashboard'),
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            _buildPanel(context),
          ],
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
        if (snapshot.hasError) return Text('Error: ${snapshot.error}');
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Text('Cargando panel...');
        }
        if (!snapshot.hasData || !snapshot.data!.exists) {
          return const Text('Datos del panel no encontrados');
        }
        var panelData = snapshot.data!.data() as Map<String, dynamic>? ?? {};
        bool alarma = panelData['alarma'] as bool? ?? false;
        bool supervision = panelData['supervision'] as bool? ?? false;
        bool problema = panelData['problema'] as bool? ?? false;
        String lastEvent = panelData['lastEvent'] as String? ?? 'Desconocido';
        String lastEventDescription = panelData['lastEventDescription'] as String? ?? 'No hay descripción';
        String lastEventTime = panelData['lastEventTime'] as String? ?? 'No especificado';

        return Card(
          child: ExpansionTile(
            leading: Icon(Icons.dashboard, color: Colors.green),
            title: Text(panelData['name'] ?? 'Panel Desconocido'),
            subtitle: Text('Ubicación: ${panelData['location']}'),
            children: <Widget>[
              ListTile(
                title: const Text('Estado de los relays'),
                subtitle: Wrap(
                  spacing: 8, // space between two icons
                  children: <Widget>[
                    Icon(alarma ? Icons.check_circle : Icons.remove_circle, color: alarma ? Colors.green : Colors.red),
                    Icon(supervision ? Icons.check_circle : Icons.remove_circle, color: supervision ? Colors.green : Colors.red),
                    Icon(problema ? Icons.check_circle : Icons.remove_circle, color: problema ? Colors.green : Colors.red),
                  ],
                ),
              ),
              ListTile(
                title: const Text('Último Evento'),
                subtitle: Text('$lastEvent - $lastEventDescription - Fecha: $lastEventTime'),
              ),
            ],
          ),
        );
      },
    );
  }
}
