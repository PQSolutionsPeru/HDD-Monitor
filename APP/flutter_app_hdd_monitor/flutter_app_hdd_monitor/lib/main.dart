import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_app_hdd_monitor/screens/home_screen.dart';
import 'package:flutter_app_hdd_monitor/screens/login_screen.dart';
import 'package:firebase_auth/firebase_auth.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Inicializa Firebase
  await Firebase.initializeApp();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HDD-Monitor',
      theme: ThemeData(
        primarySwatch: Colors.red,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: StreamBuilder(
        stream: FirebaseAuth.instance.authStateChanges(), // Escucha los cambios de autenticación
        builder: (context, AsyncSnapshot<User?> snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            // Muestra un indicador de carga mientras se verifica la autenticación
            return const Scaffold(
              body: Center(
                child: CircularProgressIndicator(),
              ),
            );
          } else {
            // Verifica si el usuario está autenticado y redirige a la pantalla correspondiente
            if (snapshot.hasData && snapshot.data != null) {
              return const HomeScreen(); // Usuario autenticado, muestra HomeScreen
            } else {
              return const LoginScreen(); // Usuario no autenticado, muestra LoginScreen
            }
          }
        },
      ),
      routes: {
        '/home': (context) => const HomeScreen(),
      },
    );
  }
}
