import 'package:flutter/material.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class AuthGuard extends RouteObserver<PageRoute<dynamic>> {
  final AuthService _authService = AuthService();

  Future<bool> canActivate(String routeName) async {
    final currentUser = await _authService.getCurrentUser();
    if (currentUser == null) {
      // Usuario no autenticado, redirige a la pantalla de inicio de sesión
      return false;
    }
    return true;
  }
}
