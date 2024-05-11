import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  static final FlutterLocalNotificationsPlugin _notificationsPlugin = FlutterLocalNotificationsPlugin();

  static Future<void> initialize() async {
    const AndroidInitializationSettings initializationSettingsAndroid = AndroidInitializationSettings('@mipmap/ic_launcher');
    final InitializationSettings initializationSettings = InitializationSettings(
      android: initializationSettingsAndroid,
    );

    await _notificationsPlugin.initialize(initializationSettings, onDidReceiveNotificationResponse: onDidReceiveNotificationResponse);

    // Iniciar el servicio en segundo plano
    await FlutterBackgroundService.initialize(onStart);
  }

  static void onStart() {
    FlutterBackgroundService.getService().onDataReceived.listen((event) {
      if (event!['action'] == 'notify') {
        showNotification(
          event['id'] as int,
          event['title'] as String,
          event['body'] as String,
          event['payload'] as String,
        );
      }
    });

    // Envía cada 1 minuto un "heartbeat"
    FlutterBackgroundService.getService().sendData(
      {"action": "setAsForeground"},
    );

    FlutterBackgroundService.getService().setAsForegroundService();
  }

  static Future onDidReceiveNotificationResponse(NotificationResponse response) async {
    if (response.payload != null) {
      debugPrint('Notification payload: ${response.payload}');
      // Implementar navegación o lógica específica aquí
    }
  }

  static Future<void> showNotification(int id, String title, String body, String payload) async {
    const NotificationDetails notificationDetails = NotificationDetails(
      android: AndroidNotificationDetails(
        'high_importance_channel',
        'High Importance Notifications',
        channelDescription: 'This channel is used for important notifications.',
        importance: Importance.max,
        priority: Priority.high,
        playSound: true,
        visibility: NotificationVisibility.public,
        fullScreenIntent: true,
        enableVibration: true,
      )
    );

    await _notificationsPlugin.show(id, title, body, notificationDetails, payload: payload);
  }
}
