package com.pqsolutions.hdd_monitor.presentation.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.data.Event
import com.pqsolutions.hdd_monitor.data.EventRepository
import com.pqsolutions.hdd_monitor.data.Panel
import com.pqsolutions.hdd_monitor.data.PanelRepository
import com.pqsolutions.hdd_monitor.data.UserRepository
import com.pqsolutions.hdd_monitor.domain.model.UserRole
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationManager @Inject constructor(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val panelRepository: PanelRepository,
    private val context: Context
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Error en corrutina de NotificationManager: ${throwable.message}", throwable)
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default + exceptionHandler)

    private val _notificationState = MutableStateFlow(CombinedNotificationState())
    val notificationState: StateFlow<CombinedNotificationState> = _notificationState.asStateFlow()

    private data class EventKey(
        val documentName: String,
        val status: String,
        val lastUpdate: String
    )

    private data class RelayKey(
        val panelDocName: String,
        val relayName: String,
        val status: String
    )

    private val processedEvents = mutableSetOf<EventKey>()
    private val processedRelays = mutableSetOf<RelayKey>()
    private var isFirstLoad = true

    private fun processInitialState(
        events: List<Event>,
        panels: List<Panel>,
        isAdmin: Boolean,
        clientName: String
    ) {
        try {
            panels.forEach { panel ->
                panel.relays.filter { it.status == "DISC" }.forEach { relay ->
                    val relayKey = RelayKey(panel.documentName, relay.name, relay.status)
                    processedRelays.add(relayKey)

                    val title = if (isAdmin) "Dashboard HDD" else clientName
                    showNotification(
                        "$title - Estado de Panel",
                        "El relay ${relay.name} del panel \"${panel.name}\" se encuentra en estado DISC",
                        CHANNEL_ID_PANEL
                    )
                }
            }

            events.forEach { event ->
                processedEvents.add(EventKey(event.documentName, event.status, event.lastUpdate))
            }

            isFirstLoad = false
            Log.d(TAG, "Initial state processed - Events: ${events.size}, Panels: ${panels.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing initial state", e)
        }
    }

    private fun handleEventUpdate(event: Event, clientName: String, isAdmin: Boolean) {
        val eventKey = EventKey(event.documentName, event.status, event.lastUpdate)

        if (!processedEvents.contains(eventKey)) {
            processedEvents.add(eventKey)

            if (!isFirstLoad) {
                val title = if (isAdmin) "Dashboard HDD" else clientName
                val message = when (event.status) {
                    "ACEPTADO" -> "${event.createdByUserRole} ha aceptado el evento \"${event.title}\""
                    "FINALIZADO" -> "${event.createdByUserRole} ha finalizado el evento \"${event.title}\""
                    else -> "El evento \"${event.title}\" ha cambiado de estado"
                }

                showNotification("$title - Evento", message, CHANNEL_ID_EVENT)
                Log.d(TAG, "Event update processed: ${event.documentName} - ${event.status}")
            }
        }
    }

    private fun handleRelayUpdate(panel: Panel, isAdmin: Boolean, clientName: String) {
        panel.relays.forEach { relay ->
            val relayKey = RelayKey(panel.documentName, relay.name, relay.status)

            if (!processedRelays.contains(relayKey)) {
                processedRelays.add(relayKey)

                if (!isFirstLoad && relay.status == "DISC") {
                    val title = if (isAdmin) "Dashboard HDD" else clientName
                    showNotification(
                        "$title - Estado de Panel",
                        "El relay ${relay.name} del panel \"${panel.name}\" ha cambiado a estado DISC",
                        CHANNEL_ID_PANEL
                    )
                    Log.d(TAG, "Relay update processed: ${panel.name} - ${relay.name} - ${relay.status}")
                }
            }
        }
    }

    private fun showNotification(
        title: String,
        message: String,
        channelId: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                if (channelId == CHANNEL_ID_EVENT) "Eventos" else "Estados de Panel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (channelId == CHANNEL_ID_EVENT)
                    "Notificaciones de eventos"
                else
                    "Notificaciones de estado de paneles"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = "${title}:${message}:${System.currentTimeMillis()}".hashCode()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, """
            Notification shown:
            ID: $notificationId
            Title: $title
            Message: $message
            Channel: $channelId
        """.trimIndent())
    }

    suspend fun updateNotificationState(clientDocName: String) {
        try {
            val currentUser = userRepository.getCurrentUser()
            val isAdmin = currentUser?.role == UserRole.ADMIN
            val clientName = if (isAdmin && clientDocName.isNotEmpty()) {
                "Admin Dashboard"
            } else {
                currentUser?.clientName ?: clientDocName
            }

            Log.d(TAG, """
                Updating notification state:
                User: ${currentUser?.documentName}
                Is admin: $isAdmin
                Client: $clientDocName
                Is first load: $isFirstLoad
            """.trimIndent())

            val eventsFlow = if (isAdmin) {
                eventRepository.getAllEventsFlow()
            } else {
                eventRepository.getEventsFlow(clientDocName)
            }

            val panelsFlow = if (isAdmin) {
                panelRepository.getAllPanelsFlow()
            } else {
                panelRepository.getPanels(clientDocName)
            }

            eventsFlow
                .catch { e ->
                    Log.e(TAG, "Error in events flow", e)
                }
                .combine(
                    panelsFlow.catch { e ->
                        Log.e(TAG, "Error in panels flow", e)
                    }
                ) { events, panels ->
                    try {
                        if (isFirstLoad) {
                            processInitialState(events, panels, isAdmin, clientName)
                        } else {
                            processUpdates(events, panels, isAdmin, clientName)
                        }

                        calculateNotificationState(events, panels, isAdmin)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing updates", e)
                        CombinedNotificationState()
                    }
                }
                .collect { newState ->
                    _notificationState.value = newState
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateNotificationState", e)
        }
    }

    private fun processUpdates(
        events: List<Event>,
        panels: List<Panel>,
        isAdmin: Boolean,
        clientName: String
    ) {
        events.forEach { event ->
            handleEventUpdate(event, clientName, isAdmin)
        }
        panels.forEach { panel ->
            handleRelayUpdate(panel, isAdmin, clientName)
        }
    }

    private fun calculateNotificationState(
        events: List<Event>,
        panels: List<Panel>,
        isAdmin: Boolean
    ): CombinedNotificationState {
        val unreadEvents = if (isAdmin) {
            countUnreadEventsForAdmin(events)
        } else {
            countUnreadEventsForUser(events)
        }

        val panelAlerts = countPanelAlerts(panels)

        return CombinedNotificationState(
            totalCount = unreadEvents + panelAlerts,
            hasUnread = unreadEvents > 0 || panelAlerts > 0,
            eventsCount = unreadEvents,
            panelAlertsCount = panelAlerts,
            lastUpdate = System.currentTimeMillis()
        )
    }

    private fun countUnreadEventsForAdmin(events: List<Event>): Int {
        return events.count { event ->
            val isUnread = !event.isRead
            val isRecentUpdate = try {
                val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                val updateTime = LocalDateTime.parse(event.lastUpdate, formatter)
                val now = LocalDateTime.now()
                updateTime.isAfter(now.minusHours(24))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing date: ${event.lastUpdate}", e)
                false
            }

            when {
                event.isProgramado -> isUnread
                isRecentUpdate -> isUnread
                else -> false
            }
        }
    }

    private fun countUnreadEventsForUser(events: List<Event>): Int {
        return events.count { event ->
            event.isProgramado && !event.isRead
        }
    }

    private fun countPanelAlerts(panels: List<Panel>): Int {
        return panels.count { panel ->
            panel.relays.any { it.status == "DISC" }
        }
    }

    fun refresh() {
        isFirstLoad = true
        processedEvents.clear()
        processedRelays.clear()

        scope.launch {
            try {
                val currentUser = userRepository.getCurrentUser() ?: return@launch
                val clientDocName = if (currentUser.role == UserRole.ADMIN) "" else currentUser.clientDocName
                updateNotificationState(clientDocName)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing notification state", e)
            }
        }
    }

    fun onDestroy() {
        scope.cancel()
    }

    companion object {
        private const val TAG = "NotificationManager"
        private const val CHANNEL_ID_EVENT = "event_notifications"
        private const val CHANNEL_ID_PANEL = "panel_notifications"
    }
}