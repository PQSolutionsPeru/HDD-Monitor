import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_app_hdd_monitor/screens/login_screen.dart';
import 'package:flutter_app_hdd_monitor/screens/admin_screens/admin_homescreen.dart';
import 'package:flutter_app_hdd_monitor/screens/user_screens/user_homescreen.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class AuthGuard {
  final AuthService _authService = AuthService();
  final FirebaseAuth _firebaseAuth = FirebaseAuth.instance;

  Future<void> checkAndRedirect(BuildContext context) async {
    User? user = _firebaseAuth.currentUser;
    if (user != null) {
      String userType = await _authService.getUserType(user.email!);
      if (userType == 'admin') {
        Navigator.pushReplacementNamed(context, '/admin_home');
      } else if (userType == 'user') {
        Navigator.pushReplacementNamed(context, '/user_home');
      } else {
        print("User type is unknown or not handled.");
      }
    }
  }

  Widget guardRoute(String route) {
    return StreamBuilder<User?>(
      stream: _firebaseAuth.authStateChanges(),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const LoginScreen(); // Mostrar LoginScreen si no hay usuario autenticado
        return FutureBuilder<String>(
          future: _authService.getUserType(snapshot.data!.email!),
          builder: (context, userTypeSnapshot) {
            if (!userTypeSnapshot.hasData) return const CircularProgressIndicator(); // Mostrar indicador de carga mientras se carga el tipo de usuario
            if (userTypeSnapshot.data == 'admin' && route == '/user_home') {
              return const AdminHomeScreen(); // Redirigir a AdminHomeScreen si el usuario es administrador y la ruta es /user_home
            } else if (userTypeSnapshot.data == 'user' && route == '/admin_home') {
              return const UserHomeScreen(); // Redirigir a UserHomeScreen si el usuario es usuario regular y la ruta es /admin_home
            }
            return route == '/admin_home' ? const AdminHomeScreen() : const UserHomeScreen(); // Redirigir a la ruta especificada si el tipo de usuario es desconocido
          },
        );
      },
    );
  }
}
