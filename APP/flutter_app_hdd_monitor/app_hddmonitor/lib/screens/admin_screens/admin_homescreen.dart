import 'package:flutter/material.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class AdminHomeScreen extends StatelessWidget {
  const AdminHomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Pantalla de Administrador'),
        backgroundColor: Colors.red,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              // Cerrar sesión al presionar el botón de logout
              await AuthService().signOutUser();
              Navigator.pushReplacementNamed(context, '/login');
            },
          ),
        ],
      ),
      body: const Center(
        child: Text(
          'Bienvenido Administrador',
          style: TextStyle(fontSize: 24.0),
        ),
      ),
    );
  }
}