import 'package:flutter/material.dart';

class PanelListWidget extends StatelessWidget {
  final List<Map<String, dynamic>?> panels;

  const PanelListWidget({super.key, required this.panels});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: panels.length,
      itemBuilder: (context, index) {
        final panelData = panels[index];

        // Verificar si panelData es null
        if (panelData == null) {
          return const Card(
            child: ListTile(
              title: Text('-sin data-'),
              subtitle: Text('-sin data-'),
            ),
          );
        }

        // Obtener valores de panelData de manera segura con operadores ?.
        final panelName = panelData['nombre'] ?? '-sin data-';
        final panelLocation = panelData['ubicación'] ?? '-sin data-';
        final panelState = panelData['estado'] ?? '-sin data-';

        // Construir la lista con los datos seguros
        return Card(
          margin: const EdgeInsets.all(8.0),
          child: ListTile(
            title: Text(panelName),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Ubicación: $panelLocation'),
                Text('Estado: $panelState'),
                const SizedBox(height: 8),
                const Text(
                  'Relés:',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (int i = 1; i <= 3; i++)
                      Text(
                        '${panelData['relevador_$i']?['nombre'] ?? '-sin data-'}: ${panelData['relevador_$i']?['estado'] ?? '-sin data-'}',
                      ),
                  ],
                ),
              ],
            ),
            onTap: () {
              // Acción al hacer clic en un panel (si es necesario)
            },
          ),
        );
      },
    );
  }
}
