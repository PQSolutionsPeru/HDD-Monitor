import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class AuthService {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<String?> signInUser(String email, String password) async {
    try {
      await _auth.signInWithEmailAndPassword(email: email, password: password);
      return null; // Inicio de sesión exitoso, retorna null sin errores
    } on FirebaseAuthException catch (e) {
      return 'Error de autenticación: ${e.message}'; // Retorna el mensaje de error en caso de fallo
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
    // Verificar si el usuario es un administrador
    QuerySnapshot<Map<String, dynamic>> adminSnapshot = await _firestore
        .collection('hdd-monitor')
        .doc('accounts')
        .collection('admins')
        .where('email', isEqualTo: email)
        .limit(1)
        .get();

    if (adminSnapshot.docs.isNotEmpty) {
      return 'admin';
    }

    // Verificar si el usuario es un cliente (más complejo debido a la estructura)
    var clientRef = _firestore.collection('hdd-monitor').doc('accounts').collection('clients');
    var clientsDocs = await clientRef.get();
    for (var client in clientsDocs.docs) {
      var usersRef = client.reference.collection('users');
      var userDoc = await usersRef.where('email', isEqualTo: email).limit(1).get();
      if (userDoc.docs.isNotEmpty) {
        return 'user';
      }
    }

    return 'unknown';
  }
}