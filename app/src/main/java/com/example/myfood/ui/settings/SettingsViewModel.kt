package com.example.myfood.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Importiere UserPreferencesRepository und NotificationSettings aus dem datastore-Paket
import com.example.myfood.datastore.UserPreferencesRepository
import com.example.myfood.datastore.NotificationSettings
// Importiere deinen ExpiryCheckWorker, um auf die Planungsfunktion zugreifen zu können
import com.example.myfood.workers.ExpiryCheckWorker // <<< ANGEPASST
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val applicationContext: Context // Wird für den Worker-Reschedule benötigt
) : ViewModel() {

    // Standardeinstellungen, die verwendet werden, bis der Flow aus DataStore geladen ist
    // oder falls keine Werte im DataStore gespeichert sind.
    // Greift auf die Konstanten im Companion Object von UserPreferencesRepository zu.
    private val defaultSettings = NotificationSettings(
        leadTimeDays = UserPreferencesRepository.DEFAULT_REMINDER_DAYS_AHEAD,
        notificationHour = UserPreferencesRepository.DEFAULT_NOTIFICATION_HOUR,
        notificationMinute = UserPreferencesRepository.DEFAULT_NOTIFICATION_MINUTE
    )

    // StateFlow, der die aktuellen Benachrichtigungseinstellungen aus dem DataStore hält.
    // Die UI beobachtet diesen Flow, um sich bei Änderungen zu aktualisieren.
    val currentSettings: StateFlow<NotificationSettings> =
        userPreferencesRepository.notificationSettingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L), // Flow aktiv halten, solange es Abonnenten gibt (plus 5s Puffer)
                initialValue = defaultSettings // Initialwert, bis der erste Wert vom Flow kommt
            )

    /**
     * Aktualisiert die Vorlaufzeit in Tagen für Benachrichtigungen im DataStore.
     * Ein Neustart des Workers ist hier nicht erforderlich, da der Worker diese Einstellung
     * bei jeder Ausführung dynamisch aus dem UserPreferencesRepository liest.
     * @param days Die neue Anzahl an Tagen für die Vorlaufzeit.
     */
    fun updateLeadTime(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateReminderDays(days)
        }
    }

    /**
     * Aktualisiert die Uhrzeit (Stunde und Minute) für die täglichen Benachrichtigungen
     * im DataStore und plant den ExpiryCheckWorker neu mit der aktualisierten Zeit.
     * @param hour Die neue Stunde für die Benachrichtigung (0-23).
     * @param minute Die neue Minute für die Benachrichtigung (0-59).
     */
    fun updateNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateNotificationTime(hour, minute)
            // Hole die neuesten (gerade gespeicherten) Einstellungen, um sicherzustellen,
            // dass der Worker mit den korrekten Daten neu geplant wird.
            val latestSettings = userPreferencesRepository.notificationSettingsFlow.first()
            // Rufe die statische Planungsfunktion direkt auf dem ExpiryCheckWorker auf.
            ExpiryCheckWorker.scheduleOrRescheduleWorker(applicationContext, latestSettings) // <<< ANGEPASST
        }
    }
}

