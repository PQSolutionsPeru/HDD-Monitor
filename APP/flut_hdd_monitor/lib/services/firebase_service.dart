import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';


class FirebaseService {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<bool> hasPanelsForCurrentUser() async {
    try {
      final userId = FirebaseAuth.instance.currentUser?.uid;
      if (userId != null) {
        final querySnapshot = await _firestore
            .collection('hdd-monitor/accounts/clients/$userId/panels')
            .limit(1)
            .get();
        return querySnapshot.docs.isNotEmpty;
      }
      return false;
    } catch (e) {
      print('Error al verificar los paneles para el usuario actual: $e');
      rethrow;
    }
  }

  Future<void> addPanel(String panelName) async {
    try {
      final userId = FirebaseAuth.instance.currentUser?.uid;
      if (userId != null) {
        await _firestore.collection('hdd-monitor/accounts/clients/$userId/panels').add({
          'name': panelName,
          'location': 'Ubicación predeterminada',
        });
      }
    } catch (e) {
      print('Error al agregar el panel a Firebase: $e');
      rethrow;
    }
  }
}
