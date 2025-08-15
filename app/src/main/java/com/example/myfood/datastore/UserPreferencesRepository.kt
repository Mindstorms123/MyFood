package com.example.myfood.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Datenklasse für die kombinierten Einstellungen
data class NotificationSettings(
    val leadTimeDays: Int, // Wie viele Tage im Voraus benachrichtigen
    val notificationHour: Int, // Stunde der Benachrichtigung (0-23)
    val notificationMinute: Int // Minute der Benachrichtigung (0-59)
)

// Diese Zeile erstellt die DataStore-Instanz auf Context-Ebene.
// Der Name "user_settings" wird beibehalten, da er nun mehrere Benutzereinstellungen enthält.
val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")
// Hinweis: Ich habe den Delegatennamen von `dataStore` zu `userSettingsDataStore` geändert,
// um Klarheit zu schaffen, dass dies der spezifische DataStore für Benutzereinstellungen ist.
// Du kannst ihn aber auch `dataStore` lassen, wenn du das bevorzugst und die Logik anpasst.

@Singleton // Wichtig für Hilt, stellt sicher, dass es nur eine Instanz gibt
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context // Hilt injiziert den ApplicationContext
) {

    // Die Schlüssel für die Preferences an einem Ort zusammenfassen
    private object PreferencesKeys {
        // Bestehender Schlüssel für die Einstellung der Erinnerungstage
        val REMINDER_DAYS_AHEAD = intPreferencesKey("reminder_days_ahead")

        // Neue Schlüssel für die Benachrichtigungszeit
        val NOTIFICATION_TIME_HOUR = intPreferencesKey("notification_time_hour")
        val NOTIFICATION_TIME_MINUTE = intPreferencesKey("notification_time_minute")
    }

    private val tag: String = "UserPreferencesRepo"

    // Standardwerte für die Einstellungen
    companion object {
        const val DEFAULT_REMINDER_DAYS_AHEAD = 3 // Standard: 3 Tage vorher
        const val DEFAULT_NOTIFICATION_HOUR = 2   // Standard: 2 Uhr
        const val DEFAULT_NOTIFICATION_MINUTE = 0 // Standard: 0 Minuten
    }

    // Dieser Flow gibt die kombinierten NotificationSettings aus
    val notificationSettingsFlow: Flow<NotificationSettings> = context.userSettingsDataStore.data
        .catch { exception ->
            // Fehlerbehandlung beim Lesen der Preferences
            if (exception is IOException) {
                Log.e(tag, "Error reading user preferences.", exception)
                // Bei Fehler ein NotificationSettings-Objekt mit Standardwerten ausgeben
                emit(
                    // Hier direkt ein leeres Preferences-Objekt übergeben,
                    // der Mapper kümmert sich um die Standardwerte
                    emptyPreferences()
                )
            } else {
                throw exception // Andere Fehler weiterwerfen
            }
        }
        .map { preferences ->
            // Die gelesenen Preferences in ein NotificationSettings-Objekt umwandeln
            NotificationSettings(
                leadTimeDays = preferences[PreferencesKeys.REMINDER_DAYS_AHEAD] ?: DEFAULT_REMINDER_DAYS_AHEAD,
                notificationHour = preferences[PreferencesKeys.NOTIFICATION_TIME_HOUR] ?: DEFAULT_NOTIFICATION_HOUR,
                notificationMinute = preferences[PreferencesKeys.NOTIFICATION_TIME_MINUTE] ?: DEFAULT_NOTIFICATION_MINUTE
            )
        }

    // Funktion zum Aktualisieren der Erinnerungstage (Vorlaufzeit)
    suspend fun updateReminderDays(days: Int) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.REMINDER_DAYS_AHEAD] = days
            Log.d(tag, "Reminder days preference updated to: $days")
        }
    }

    // Funktion zum Aktualisieren der Benachrichtigungszeit (Stunde und Minute)
    suspend fun updateNotificationTime(hour: Int, minute: Int) {
        context.userSettingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_TIME_HOUR] = hour
            preferences[PreferencesKeys.NOTIFICATION_TIME_MINUTE] = minute
            Log.d(tag, "Notification time preference updated to: $hour:$minute")
        }
    }

}

