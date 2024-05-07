import 'package:flutter/material.dart';
import 'views/login_screen.dart';
import 'views/dashboard_screen.dart';
import 'firebase_options.dart';
import 'package:firebase_core/firebase_core.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(
    options: firebaseOptions,
  );
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HDD Monitor App',
      theme: ThemeData(
        primaryColor: const Color(0xFF212121), // Deep black for primary color
        colorScheme: ColorScheme.fromSwatch(
          primarySwatch: Colors.red, // Red used for primary variant interactions
          brightness: Brightness.dark, // Overall theme is dark
        ).copyWith(
          secondary: Colors.redAccent, // Red for interactive elements and buttons
          background: const Color(0xFF303030), // Dark grey for background surfaces
          surface: const Color(0xFF424242), // Slightly lighter grey for cards and UI surfaces
          onPrimary: Colors.white, // Text on primary dark background
          onSecondary: Colors.black, // Text on secondary red background
          onError: Colors.yellowAccent, // Just in case of error highlights
        ),
        textButtonTheme: TextButtonThemeData(
          style: TextButton.styleFrom(
            foregroundColor: Colors.redAccent, // Red text for flat buttons
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.red, // Red background for raised buttons
            foregroundColor: Colors.white, // White text on raised buttons
          ),
        ),
        inputDecorationTheme: const InputDecorationTheme(
          border: OutlineInputBorder(
            borderSide: BorderSide(color: Colors.redAccent),
          ),
          enabledBorder: OutlineInputBorder(
            borderSide: BorderSide(color: Colors.redAccent),
          ),
          focusedBorder: OutlineInputBorder(
            borderSide: BorderSide(color: Colors.red),
          ),
          labelStyle: TextStyle(
            color: Colors.redAccent, // Labels in red accent
          ),
        ),
      ),
      initialRoute: '/',
      routes: {
        '/': (context) => LoginScreen(),
        '/dashboard': (context) => DashboardScreen(),
      },
    );
  }
}
