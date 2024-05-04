import 'package:flutter/material.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';
import 'package:firebase_auth/firebase_auth.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  _LoginScreenState createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final AuthService _authService = AuthService();

  @override
  void initState() {
    super.initState();
    checkCurrentUser();
  }

  void checkCurrentUser() async {
    var user = await _authService.getCurrentUser();
    if (user != null) {
      redirectToHome(user);
    }
  }

  void redirectToHome(user) async {
    if (!mounted) return;
    var userType = await _authService.getUserType(user.email!);
    if (!mounted) return;

    if (userType == 'admin') {
      Navigator.pushReplacementNamed(context, '/admin_home');
    } else if (userType == 'user') {
      Navigator.pushReplacementNamed(context, '/user_home');
    } else {
      print("User type is unknown or not handled.");
    }
  }

  void _signIn(BuildContext context) async {
  String email = _emailController.text.trim();
  String password = _passwordController.text.trim();

  if (email.isNotEmpty && password.isNotEmpty) {
    String? result = await _authService.signInUser(email, password);
    if (result == null) {
      print("Sign-in successful");
      User? user = FirebaseAuth.instance.currentUser;
      if (user != null) {
        redirectToHome(user);
      } else {
        print("User is null after sign-in");
      }
    } else {
      print("Sign-in failed: $result");
      showErrorDialog(result);
    }
  } else {
    print("Email or password is empty.");
    showErrorDialog('Por favor ingresa tu correo y contraseña.');
  }
}


  void showErrorDialog(String message) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Error', style: TextStyle(color: Colors.red)), // Corrección: Elimina `const`.
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK', style: TextStyle(color: Colors.red)), // Corrección: Elimina `const`.
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Inicio de Sesión'),
        backgroundColor: Colors.red,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0), // Corrección: Elimina `const`.
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
            const SizedBox(height: 20.0), // Corrección: Elimina `const`.
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
            const SizedBox(height: 20.0), // Corrección: Elimina `const`.
            ElevatedButton(
              onPressed: () => _signIn(context),
              style: ButtonStyle(
                backgroundColor: MaterialStateProperty.all<Color>(Colors.red),
              ),
              child: const Text('Iniciar Sesión'), // Corrección: Elimina `const`.
            ),
            const SizedBox(height: 20.0), // Corrección: Elimina `const`.
            TextButton(
              onPressed: () {
                Navigator.pushNamed(context, '/register');
              },
              child: const Text('¿Nuevo Usuario? Regístrate aquí'), // Corrección: Elimina `const`.
            ),
          ],
        ),
      ),
    );
  }
}
