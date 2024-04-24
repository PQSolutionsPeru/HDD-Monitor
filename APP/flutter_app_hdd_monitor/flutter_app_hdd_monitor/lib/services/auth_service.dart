import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<String?> signInUser(String email, String password) async {
    try {
      await _auth.signInWithEmailAndPassword(email: email, password: password);
      return null;  // Inicio de sesión exitoso
    } on FirebaseAuthException catch (e) {
      return 'Error de autenticación: ${e.message}';  // Manejo de errores específicos de autenticación
    } catch (e) {
      return 'Error general: ${e.toString()}';  // Otros errores
    }
  }



  Future<String?> registerUser(String email, String password) async {
    try {
      await _auth.createUserWithEmailAndPassword(email: email, password: password);
      // Aquí puedes realizar operaciones adicionales, como guardar información del usuario en Firestore
      return null; // Registro exitoso, retorna null sin errores
    } on FirebaseAuthException catch (e) {
      return 'Error de registro: ${e.message}'; // Retorna el mensaje de error en caso de fallo
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
      // Verificación de administrador
      QuerySnapshot adminQuery = await _firestore
          .collection('hdd-monitor/accounts/admins')
          .where('email', isEqualTo: email)
          .get();
      if (adminQuery.docs.isNotEmpty) {
        return 'admin';
      }

      // Verificación de usuario regular
      QuerySnapshot userQuery = await _firestore
          .collection('hdd-monitor/accounts/clients/client_1/users')
          .where('email', isEqualTo: email)
          .get();
      if (userQuery.docs.isNotEmpty) {
        return 'user';
      }

      return 'unknown';  // No se encontró el tipo de usuario
    } catch (e) {
      print('Error fetching user type: $e');
      return 'error';  // Manejo de errores
    }
  }
}