import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  static final FlutterLocalNotificationsPlugin _notificationsPlugin = FlutterLocalNotificationsPlugin();

  // Inicializa el servicio de notificaciones
  static Future<void> initialize() async {
    const AndroidInitializationSettings initializationSettingsAndroid = AndroidInitializationSettings('@mipmap/ic_launcher');
    const InitializationSettings initializationSettings = InitializationSettings(
      android: initializationSettingsAndroid,
    );

    await _notificationsPlugin.initialize(initializationSettings);
  }

  // Muestra una notificación
  static Future<void> showNotification(int id, String title, String body, String payload) async {
    const NotificationDetails notificationDetails = NotificationDetails(
      android: AndroidNotificationDetails(
        'high_importance_channel', // ID del canal
        'High Importance Notifications', // Título del canal
        channelDescription: 'This channel is used for important notifications.', // Descripción del canal
        importance: Importance.max, // Importancia de la notificación
        priority: Priority.high, // Prioridad de la notificación
        playSound: true, // Activa el sonido
        visibility: NotificationVisibility.public, // Visibilidad de la notificación
        fullScreenIntent: true, // Notificación en pantalla completa
        enableVibration: true, // Activa la vibración
      )
    );

    await _notificationsPlugin.show(
      id, 
      title, 
      body, 
      notificationDetails, 
      payload: payload
    );
  }
}
