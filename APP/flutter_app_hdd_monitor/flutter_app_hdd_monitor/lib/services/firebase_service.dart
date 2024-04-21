import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';

class FirebaseService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<String?> signInUser(String email, String password) async {
    try {
      await _auth.signInWithEmailAndPassword(email: email, password: password);
      return null; // Inicio de sesión exitoso, retorna null sin errores
    } catch (e) {
      return e.toString(); // Retorna el mensaje de error en caso de fallo
    }
  }

  Future<User?> getCurrentUser() async {
    return _auth.currentUser;
  }

  Future<void> signOutUser() async {
    await _auth.signOut();
  }

  Future<bool> isUserAdmin(String email) async {
    try {
      QuerySnapshot<Map<String, dynamic>> adminQuery = await _firestore
          .collection('hdd-monitor')
          .doc('cuentas')
          .collection('administradores')
          .where('email', isEqualTo: email)
          .limit(1)
          .get();

      return adminQuery.docs.isNotEmpty;
    } catch (e) {
      print('Error verificando si el usuario es administrador: $e');
      return false;
    }
  }

  Future<bool> isRegularUser(String email) async {
    try {
      QuerySnapshot<Map<String, dynamic>> userQuery = await _firestore
          .collection('hdd-monitor')
          .doc('cuentas')
          .collection('clientes')
          .doc('cliente_1')
          .collection('usuarios')
          .where('email', isEqualTo: email)
          .limit(1)
          .get();

      return userQuery.docs.isNotEmpty;
    } catch (e) {
      print('Error verificando si el usuario es regular: $e');
      return false;
    }
  }
}
