// services/firebase_service.dart

import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

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

  Future<String?> registerUser(String email, String password, String username) async {
    try {
      await _auth.createUserWithEmailAndPassword(email: email, password: password);
      String userId = _auth.currentUser!.uid;
      await _firestore.collection('users').doc(userId).set({
        'username': username,
        'email': email,
        'isAdmin': false,
      });
      return null; // Registro exitoso, retorna null sin errores
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

  Future<bool> isUserAdmin() async {
    Map<String, dynamic>? userData = await getCurrentUserData();
    if (userData != null) {
      return userData['isAdmin'] ?? false;
    }
    return false;
  }

  Future<Map<String, dynamic>?> getCurrentUserData() async {
    String? userId = _auth.currentUser?.uid;
    if (userId != null) {
      DocumentSnapshot userSnapshot = await _firestore.collection('users').doc(userId).get();
      if (userSnapshot.exists) {
        return userSnapshot.data() as Map<String, dynamic>?;
      }
    }
    return null;
  }
}
