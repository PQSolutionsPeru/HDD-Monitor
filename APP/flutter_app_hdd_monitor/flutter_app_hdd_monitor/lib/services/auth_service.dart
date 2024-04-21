import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<String?> signInUser(String email, String password) async {
    try {
      UserCredential userCredential = await _auth.signInWithEmailAndPassword(
        email: email,
        password: password,
      );

      return null; // Inicio de sesión exitoso, retorna null sin errores
    } catch (e) {
      return 'Error de autenticación: $e'; // Retorna el mensaje de error en caso de fallo
    }
  }

  Future<void> signOutUser() async {
    await _auth.signOut();
  }

  Future<User?> getCurrentUser() async {
    return _auth.currentUser;
  }

  Future<String> getUserType(String email) async {
    try {
      // Verificar si el usuario es un administrador
      QuerySnapshot<Map<String, dynamic>> adminSnapshot = await _firestore
          .collection('hdd-monitor')
          .doc('cuentas')
          .collection('administradores')
          .where('email', isEqualTo: email)
          .limit(1)
          .get();

      if (adminSnapshot.docs.isNotEmpty) {
        return 'admin';
      }

      // Verificar si el usuario es un usuario regular
      QuerySnapshot<Map<String, dynamic>> userSnapshot = await _firestore
          .collection('hdd-monitor')
          .doc('cuentas')
          .collection('clientes')
          .doc('cliente_1')
          .collection('usuarios')
          .where('email', isEqualTo: email)
          .limit(1)
          .get();

      if (userSnapshot.docs.isNotEmpty) {
        return 'user';
      }

      // Si no se encontró el usuario en ninguna colección
      return 'Usuario no registrado';
    } catch (e) {
      print('Error obteniendo tipo de usuario: $e');
      return 'Error obteniendo tipo de usuario';
    }
  }
}
