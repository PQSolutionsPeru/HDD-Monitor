import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class UserHomeScreen extends StatelessWidget {
  const UserHomeScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Panel de Control'),
        backgroundColor: Colors.red,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              await AuthService().signOutUser();
              Navigator.pushReplacementNamed(context, '/login');
            },
          ),
        ],
      ),
      body: StreamBuilder(
        stream: FirebaseFirestore.instance
            .collection('hdd-monitor')
            .doc('cuentas')
            .collection('clientes')
            .doc('cliente_1')
            .collection('paneles')
            .snapshots(),
        builder: (context, AsyncSnapshot<QuerySnapshot> snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return Center(child: CircularProgressIndicator());
          }

          if (snapshot.hasError) {
            return Center(child: Text('Error: ${snapshot.error}'));
          }

          final panels = snapshot.data!.docs;

          return ListView.builder(
            itemCount: panels.length,
            itemBuilder: (context, index) {
              final panelData = panels[index].data() as Map<String, dynamic>;

              return Card(
                margin: const EdgeInsets.all(8.0),
                child: ListTile(
                  title: Text(panelData['nombre'] ?? '-sin data-'),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Ubicación: ${panelData['ubicación'] ?? '-sin data-'}'),
                      Text('Estado: ${panelData['estado'] ?? '-sin data-'}'),
                      SizedBox(height: 8),
                      Text(
                        'Relés:',
                        style: TextStyle(fontWeight: FontWeight.bold),
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          for (int i = 1; i <= 3; i++)
                            if (panelData['relevador_$i'] != null)
                              Text(
                                '${panelData['relevador_$i']['nombre'] ?? '-sin data-'}: ${panelData['relevador_$i']['estado'] ?? '-sin data-'}',
                              ),
                        ],
                      ),
                    ],
                  ),
                  onTap: () {
                    // Acción al hacer clic en un panel (si es necesario)
                  },
                ),
              );
            },
          );
        },
      ),
    );
  }
}
