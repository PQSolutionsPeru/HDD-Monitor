import 'package:flutter/material.dart';
import 'package:flutter_app_hdd_monitor/services/firebase_service.dart';
import 'package:flutter_app_hdd_monitor/services/auth_service.dart';

class RegisterScreen extends StatelessWidget {
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _usernameController = TextEditingController();
  final FirebaseService _firebaseService = FirebaseService();
  final AuthService _authService = AuthService();

  RegisterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Registro de Usuario'),
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
            TextField(
              controller: _usernameController,
              decoration: const InputDecoration(
                labelText: 'Nombre de Usuario',
                labelStyle: TextStyle(color: Colors.red),
                focusedBorder: OutlineInputBorder(
                  borderSide: BorderSide(color: Colors.red),
                ),
              ),
            ),
            const SizedBox(height: 20.0),
            ElevatedButton(
              onPressed: () async {
                String? registerError = await _authService.registerUser(
                  _emailController.text.trim(),
                  _passwordController.text.trim(),
                );
                
                // Mostrar mensaje de error de registro
                showDialog(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Error de Registro', style: TextStyle(color: Colors.red)),
                    content: Text(registerError),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: const Text('OK', style: TextStyle(color: Colors.red)),
                      ),
                    ],
                  ),
                );
                return; // Detener la ejecución si hubo un error en el registro
              
                // Registro exitoso, ahora guarda datos adicionales
                String? firebaseError = await _firebaseService.registerUser(
                  _emailController.text.trim(),
                  _passwordController.text.trim(),
                  _usernameController.text.trim(),
                );
                
                // Mostrar mensaje de error de Firebase
                showDialog(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Error de Registro', style: TextStyle(color: Colors.red)),
                    content: Text(firebaseError),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: const Text('OK', style: TextStyle(color: Colors.red)),
                      ),
                    ],
                  ),
                );
                            },
              style: ButtonStyle(
                backgroundColor: MaterialStateProperty.all<Color>(Colors.red),
              ),
              child: const Text('Registrarse'),
            ),
          ],
        ),
      ),
    );
  }
}