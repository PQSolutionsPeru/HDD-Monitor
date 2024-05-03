import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class UserHomeScreen extends StatelessWidget {
  const UserHomeScreen({super.key});

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
      body: StreamBuilder<QuerySnapshot>(
        stream: FirebaseFirestore.instance
            .collection('hdd-monitor/accounts/clients/client_1/panels')
            .snapshots(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
            return const Center(child: Text('No data available'));
          }

          return ListView(
            children: snapshot.data!.docs.map((DocumentSnapshot document) {
              Map<String, dynamic> panelData = document.data() as Map<String, dynamic>;
              String panelName = panelData['name'] ?? 'Unknown Panel';
              String panelLocation = panelData['location'] ?? 'Unknown Location';

              return Card(
                margin: const EdgeInsets.all(10),
                child: ExpansionTile(
                  title: Text(panelName),
                  subtitle: Text(panelLocation),
                  children: [
                    StreamBuilder<QuerySnapshot>(
                      stream: FirebaseFirestore.instance
                          .collection('hdd-monitor/accounts/clients/client_1/panels/${document.id}/relays')
                          .snapshots(),
                      builder: (context, relaySnapshot) {
                        if (relaySnapshot.connectionState == ConnectionState.waiting) {
                          return const Center(child: CircularProgressIndicator());
                        }
                        if (!relaySnapshot.hasData || relaySnapshot.data!.docs.isEmpty) {
                          return const ListTile(title: Text('No relays data'));
                        }

                        return Column(
                          children: relaySnapshot.data!.docs.map((relayDoc) {
                            Map<String, dynamic> relayData = relayDoc.data() as Map<String, dynamic>;
                            return ListTile(
                              title: Text(relayDoc.id),
                              subtitle: Text('Status: ${relayData['status']} at ${relayData['date_time']}'),
                            );
                          }).toList(),
                        );
                      },
                    ),
                  ],
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}
