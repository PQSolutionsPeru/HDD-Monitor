import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {
    private val _hasNewNotifications = MutableStateFlow(false)
    val hasNewNotifications: StateFlow<Boolean> = _hasNewNotifications

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    init {
        viewModelScope.launch {
            // Aquí deberías observar los cambios en Firestore
            // y actualizar _hasNewNotifications y _notifications
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            // Marcar todas las notificaciones como leídas en Firestore
            _hasNewNotifications.value = false
        }
    }
}

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)