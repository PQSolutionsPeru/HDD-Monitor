import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flut_hdd_monitor/views/login_screen.dart';
import 'package:flut_hdd_monitor/views/dashboard_screen.dart';
import 'package:flut_hdd_monitor/services/auth_service.dart';

class AuthGuard extends StatelessWidget {
  final AuthService _authService = AuthService();

  AuthGuard({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<User?>(
      stream: FirebaseAuth.instance.authStateChanges(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            body: Center(
              child: CircularProgressIndicator(),
            ),
          );
        }

        // Si el usuario está autenticado, redirigir al dashboard
        if (snapshot.hasData) {
          return const DashboardScreen();
        } else {
          // Si el usuario no está autenticado, mostrar la pantalla de inicio de sesión
          return const LoginScreen();
        }
      },
    );
  }
}
