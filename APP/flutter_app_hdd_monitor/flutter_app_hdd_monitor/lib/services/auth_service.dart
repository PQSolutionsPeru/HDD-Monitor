// services/auth_service.dart

import 'package:firebase_auth/firebase_auth.dart';

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;

  Future<String?> registerUser(String email, String password) async {
    try {
      await _auth.createUserWithEmailAndPassword(email: email, password: password);
      return null; // Registro exitoso, retorna null sin errores
    } catch (e) {
      return e.toString(); // Retorna el mensaje de error en caso de fallo
    }
  }

  Future<void> signOutUser() async {
    await _auth.signOut();
  }
}
