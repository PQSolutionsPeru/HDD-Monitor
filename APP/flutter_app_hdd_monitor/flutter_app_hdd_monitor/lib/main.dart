import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_app_hdd_monitor/screens/admin_screens/admin_homescreen.dart';
import 'package:flutter_app_hdd_monitor/screens/user_screens/user_homescreen.dart';
import 'package:flutter_app_hdd_monitor/screens/login_screen.dart';
import 'package:flutter_app_hdd_monitor/screens/register_screen.dart';
import 'package:flutter_app_hdd_monitor/services/firebase_service.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_app_hdd_monitor/services/firebase_config.dart';
import 'package:flutter_app_hdd_monitor/widgets/panel_list.dart'; // Importa el archivo donde está definido PanelListWidget

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: FirebaseConfig.options);

  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  final FirebaseService _firebaseService = FirebaseService();

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
        '/login': (context) => LoginScreen(),
        '/register': (context) => RegisterScreen(),
        '/admin_home': (context) => FutureBuilder(
          future: _firebaseService.getCurrentUser(),
          builder: (context, AsyncSnapshot<User?> snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return CircularProgressIndicator();
            } else {
              final currentUser = snapshot.data;
              if (currentUser != null) {
                return FutureBuilder<bool>(
                  future: _firebaseService.isUserAdmin(currentUser.email!),
                  builder: (context, AsyncSnapshot<bool> isAdminSnapshot) {
                    if (isAdminSnapshot.connectionState == ConnectionState.waiting) {
                      return CircularProgressIndicator();
                    } else {
                      final isAdmin = isAdminSnapshot.data ?? false;
                      if (isAdmin) {
                        return Scaffold(
                          appBar: AppBar(title: Text('Admin Home')),
                          body: Column(
                            children: [
                              Text('Welcome, Admin!'),
                              Expanded(
                                child: PanelListWidget(
                                  panels: [
                                    // Aquí puedes pasar datos de paneles si es necesario
                                  ],
                                ),
                              ),
                            ],
                          ),
                        );
                      } else {
                        return UserHomeScreen();
                      }
                    }
                  },
                );
              } else {
                return LoginScreen();
              }
            }
          },
        ),
        '/user_home': (context) => FutureBuilder(
          future: _firebaseService.getCurrentUser(),
          builder: (context, AsyncSnapshot<User?> snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return CircularProgressIndicator();
            } else {
              final currentUser = snapshot.data;
              if (currentUser != null) {
                return FutureBuilder<bool>(
                  future: _firebaseService.isRegularUser(currentUser.email!),
                  builder: (context, AsyncSnapshot<bool> isRegularUserSnapshot) {
                    if (isRegularUserSnapshot.connectionState == ConnectionState.waiting) {
                      return CircularProgressIndicator();
                    } else {
                      final isRegularUser = isRegularUserSnapshot.data ?? false;
                      if (isRegularUser) {
                        return UserHomeScreen();
                      } else {
                        return AdminHomeScreen();
                      }
                    }
                  },
                );
              } else {
                return LoginScreen();
              }
            }
          },
        ),
      },
    );
  }
}
