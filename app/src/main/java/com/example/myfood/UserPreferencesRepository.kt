package com.example.myfood.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext // Wenn du Hilt verwendest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Diese Zeile erstellt die DataStore-Instanz auf Context-Ebene
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Singleton // Wichtig für Hilt, stellt sicher, dass es nur eine Instanz gibt
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context // Hilt injiziert den ApplicationContext
) {

    private object PreferencesKeys {
        // Ein Schlüssel für die Einstellung der Erinnerungstage
        val REMINDER_DAYS_AHEAD = intPreferencesKey("reminder_days_ahead")
    }

    private val TAG: String = "UserPreferencesRepo"

    // Dieser Flow gibt die gespeicherte Anzahl an Tagen aus
    // oder einen Standardwert (hier 7), falls nichts gespeichert ist.
    val reminderDaysFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            // Fehlerbehandlung beim Lesen der Preferences
            if (exception is IOException) {
                Log.e(TAG, "Error reading preferences.", exception)
                emit(emptyPreferences()) // Bei Fehler leere Preferences ausgeben
            } else {
                throw exception // Andere Fehler weiterwerfen
            }
        }
        .map { preferences ->
            // Den Wert für unseren Schlüssel auslesen, oder den Standardwert 7 verwenden
            preferences[PreferencesKeys.REMINDER_DAYS_AHEAD] ?: 7
        }

    // Funktion zum Aktualisieren der Erinnerungstage
    suspend fun updateReminderDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMINDER_DAYS_AHEAD] = days
            Log.d(TAG, "Reminder days preference updated to: $days")
        }
    }
}
