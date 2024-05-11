import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flut_hdd_monitor/services/firebase_options.dart';
import 'package:flut_hdd_monitor/services/auth_guard.dart';
import 'package:provider/provider.dart';
import 'package:flut_hdd_monitor/services/bluetooth_provider.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class NotificationService {
  static final FlutterLocalNotificationsPlugin _notificationsPlugin = FlutterLocalNotificationsPlugin();

  static Future<void> initialize() async {
    const AndroidInitializationSettings initializationSettingsAndroid = AndroidInitializationSettings('@mipmap/ic_launcher');
    final InitializationSettings initializationSettings = InitializationSettings(
      android: initializationSettingsAndroid,
    );

    await _notificationsPlugin.initialize(
      initializationSettings,
      onDidReceiveNotificationResponse: onDidReceiveNotificationResponse
    );
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
        'high_importance_channel', // id
        'High Importance Notifications', // title
        channelDescription: 'This channel is used for important notifications.', // description
        importance: Importance.high,
        priority: Priority.high,
        playSound: true,
        enableLights: true,
        visibility: NotificationVisibility.public,
        fullScreenIntent: true
      )
    );

    await _notificationsPlugin.show(id, title, body, notificationDetails, payload: payload);
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: firebaseOptions);
  await NotificationService.initialize();
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => BluetoothProvider(),
      child: MaterialApp(
        title: 'HDD Monitor App',
        theme: ThemeData(
          primarySwatch: Colors.red,
          appBarTheme: AppBarTheme(
            color: Colors.red[800]!,
            foregroundColor: Colors.white,
          ),
          scaffoldBackgroundColor: Colors.white,
          textButtonTheme: TextButtonThemeData(
            style: TextButton.styleFrom(
              backgroundColor: Colors.redAccent,
              foregroundColor: Colors.white,
            ),
          ),
          elevatedButtonTheme: ElevatedButtonThemeData(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
            ),
          ),
          inputDecorationTheme: InputDecorationTheme(
            labelStyle: TextStyle(color: Colors.redAccent),
            hintStyle: TextStyle(color: Colors.redAccent),
            fillColor: Colors.white,
            filled: true,
            enabledBorder: UnderlineInputBorder(
              borderSide: BorderSide(color: Colors.redAccent),
            ),
            focusedBorder: UnderlineInputBorder(
              borderSide: BorderSide(color: Colors.red),
            ),
          ),
        ),
        home: AuthGuard(),
      ),
    );
  }
}
