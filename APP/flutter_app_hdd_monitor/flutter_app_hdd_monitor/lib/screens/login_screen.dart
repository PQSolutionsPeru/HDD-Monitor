import 'package:flutter/material.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart'; // Importa AuthService
import 'package:firebase_auth/firebase_auth.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  _LoginScreenState createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final AuthService _authService = AuthService(); // Utiliza AuthService en lugar de FirebaseService

  @override
  void initState() {
    super.initState();
    checkCurrentUser();
  }

  void checkCurrentUser() async {
    User? user = await _authService.getCurrentUser();
    if (user != null) {
      String userType = await _authService.getUserType(user.email!);
      if (userType == 'admin') {
        Navigator.pushReplacementNamed(context, '/admin_home');
      } else if (userType == 'user') {
        Navigator.pushReplacementNamed(context, '/user_home');
      }
    }
  }

  void _signIn(BuildContext context) async {
    String email = _emailController.text.trim();
    String password = _passwordController.text.trim();

    if (email.isNotEmpty && password.isNotEmpty) {
      String? result = await _authService.signInUser(email, password);
      if (result == null) {
        User? user = await _authService.getCurrentUser();
        if (user != null) {
          String userType = await _authService.getUserType(user.email!);
          if (userType == 'admin') {
            Navigator.pushReplacementNamed(context, '/admin_home');
          } else if (userType == 'user') {
            Navigator.pushReplacementNamed(context, '/user_home');
          }
        }
      } else {
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Error de Inicio de Sesión', style: TextStyle(color: Colors.red)),
            content: Text(result),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK', style: TextStyle(color: Colors.red)),
              ),
            ],
          ),
        );
      }
    } else {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Error', style: TextStyle(color: Colors.red)),
          content: const Text('Por favor ingresa tu correo y contraseña.'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('OK', style: TextStyle(color: Colors.red)),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Inicio de Sesión'),
        backgroundColor: Colors.red,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            TextField(
              controller: _emailController,
              decoration: const InputDecoration(
                labelText: 'Correo Electrónico',
                labelStyle: TextStyle(color: Colors.red),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: Colors.red),
                ),
              ),
            ),
            const SizedBox(height: 20.0),
            TextField(
              controller: _passwordController,
              decoration: const InputDecoration(
                labelText: 'Contraseña',
                labelStyle: TextStyle(color: Colors.red),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: Colors.red),
                ),
              ),
              obscureText: true,
            ),
            const SizedBox(height: 20.0),
            ElevatedButton(
              onPressed: () => _signIn(context),
              style: ButtonStyle(
                backgroundColor: MaterialStateProperty.all<Color>(Colors.red),
              ),
              child: const Text('Iniciar Sesión'),
            ),
            const SizedBox(height: 20.0),
            TextButton(
              onPressed: () {
                Navigator.pushNamed(context, '/register');
              },
              child: const Text('¿Nuevo Usuario? Regístrate aquí'),
            ),
          ],
        ),
      ),
    );
  }
}
