import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_app_hdd_monitor/screens/user_screens/user_homescreen.dart';
import 'package:flutter_app_hdd_monitor/screens/admin_screens/admin_homescreen.dart';
import 'package:flutter_app_hdd_monitor/screens/login_screen.dart';
import 'package:flutter_app_hdd_monitor/services/firebase_config.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: FirebaseConfig.options);

  runApp(const MyApp());
}


class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HDD-Monitor',
      theme: ThemeData(
        primarySwatch: Colors.red,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      initialRoute: '/login',
      routes: {
        '/login': (context) => const LoginScreen(),
        '/user_home': (context) => const UserHomeScreen(), // Pantalla de usuario normal
        '/admin_home': (context) => const AdminHomeScreen(), // Pantalla de administrador
      },
    );
  }
}

