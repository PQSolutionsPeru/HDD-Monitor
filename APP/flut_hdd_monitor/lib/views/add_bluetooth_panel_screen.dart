import 'package:flutter/material.dart';

class AddBluetoothPanelScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Agregar Panel por Bluetooth')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
              onPressed: () {
                // Lógica para agregar el panel por Bluetooth
                // Una vez agregado el panel, mostrar el cuadro de mensaje "Panel agregado"
                _showPanelAddedDialog(context);
              },
              child: Text('Agregar Panel'),
            ),
          ],
        ),
      ),
    );
  }

  void _showPanelAddedDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text('Panel Agregado'),
          content: Text('El panel se ha agregado correctamente.'),
          actions: <Widget>[
            TextButton(
              onPressed: () {
                Navigator.pop(context); // Cerrar el diálogo
                Navigator.pushReplacementNamed(context, '/dashboard'); // Ir al Dashboard
              },
              child: Text('OK'),
            ),
          ],
        );
      },
    );
  }
}
