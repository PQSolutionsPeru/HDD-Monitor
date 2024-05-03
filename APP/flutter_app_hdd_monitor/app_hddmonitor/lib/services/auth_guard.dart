import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_app_hdd_monitor/screens/login_screen.dart';
import 'package:flutter_app_hdd_monitor/screens/admin_screens/admin_homescreen.dart';
import 'package:flutter_app_hdd_monitor/screens/user_screens/user_homescreen.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class AuthGuard {
  final AuthService _authService = AuthService();
  final FirebaseAuth _firebaseAuth = FirebaseAuth.instance;

  // Constructor
  AuthGuard();

  // Verifica si el usuario está logueado y redirige según el tipo de usuario
  Future<void> checkAndRedirect(BuildContext context) async {
    User? user = _firebaseAuth.currentUser;
    if (user != null) {
      String userType = await _authService.getUserType(user.email!);
      if (userType == 'admin') {
        Navigator.pushReplacementNamed(context, '/admin_home');
      } else if (userType == 'user') {
        Navigator.pushReplacementNamed(context, '/user_home');
      }
    }
  }

  // Protege las rutas según el tipo de usuario
  Widget guardRoute(String route) {
    return StreamBuilder<User?>(
      stream: _firebaseAuth.authStateChanges(),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const LoginScreen();
        return FutureBuilder<String>(
          future: _authService.getUserType(snapshot.data!.email!),
          builder: (context, userTypeSnapshot) {
            if (!userTypeSnapshot.hasData) return const CircularProgressIndicator();
            if (userTypeSnapshot.data == 'admin' && route == '/user_home') {
              return const AdminHomeScreen();
            } else if (userTypeSnapshot.data == 'user' && route == '/admin_home') {
              return const UserHomeScreen();
            }
            return route == '/admin_home' ? const AdminHomeScreen() : const UserHomeScreen();
          },
        );
      },
    );
  }
}
