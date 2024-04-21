import 'package:flutter/material.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart'; // Importa AuthService

class AdminHomeScreen extends StatelessWidget {
  const AdminHomeScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Pantalla de Administrador'),
        backgroundColor: Colors.red,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () {
              // Lógica para cerrar sesión al presionar el botón de logout
              AuthService().signOutUser();
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
