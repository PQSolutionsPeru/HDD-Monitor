import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_app_hdd_monitor/services/auth_guard.dart';
import 'package:flutter_app_hdd_monitor/screens/login_screen.dart';
import 'package:flutter_app_hdd_monitor/screens/register_screen.dart';
import 'package:flutter_app_hdd_monitor/services/firebase_config.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: FirebaseConfig.options);
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  final AuthGuard _authGuard = AuthGuard();

  MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HDD-Monitor',
      theme: ThemeData(
        primarySwatch: Colors.red,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      initialRoute: '/login', // Ruta inicial al iniciar la aplicación
      routes: {
        '/login': (context) => const LoginScreen(),
        '/register': (context) => const RegisterScreen(),
        '/admin_home': (context) => _authGuard.guardRoute('/admin_home'),
        '/user_home': (context) => _authGuard.guardRoute('/user_home'),
      },
    );
  }
}
