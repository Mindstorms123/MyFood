package com.example.myfood.workers

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myfood.FoodItem      // Stelle sicher, dass FoodItem hier korrekt definiert ist
import com.example.myfood.FoodStore      // Dein FoodStore object
import com.example.myfood.MainActivity
import com.example.myfood.R
// Importiere UserPreferencesRepository und NotificationSettings
import com.example.myfood.datastore.UserPreferencesRepository
import com.example.myfood.datastore.NotificationSettings // <<< HINZUGEFÜGT
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first // Wichtig für .first() auf dem Flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository // Wird injiziert
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "ExpiryCheckWorker"
        private const val NOTIFICATION_CHANNEL_ID = "expiry_notifications_channel"
        private const val NOTIFICATION_GROUP_KEY = "com.example.myfood.EXPIRY_NOTIFICATION_GROUP"
        private const val SUMMARY_NOTIFICATION_ID = 0 // Feste ID für die Zusammenfassungs-Benachrichtigung
        private const val TAG = "ExpiryCheckWorker"

        // Methode zum Planen oder Neuplanen des Workers
        fun scheduleOrRescheduleWorker(context: Context, settings: NotificationSettings) {
            val workManager = WorkManager.getInstance(context.applicationContext) // Verwende ApplicationContext

            val now = LocalDateTime.now()
            // Bestimme die nächste Ausführungszeit basierend auf den Einstellungen
            var nextRunTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(settings.notificationHour, settings.notificationMinute))

            if (now.isAfter(nextRunTime)) {
                nextRunTime = nextRunTime.plusDays(1) // Wenn die Zeit heute schon vorbei ist, für morgen planen
            }

            val initialDelay = Duration.between(now, nextRunTime)

            Log.d(TAG, "Scheduling ExpiryCheckWorker. Current time: $now, Next run time: $nextRunTime, Initial delay: ${initialDelay.toMinutes()} minutes.")

            val periodicWorkRequest = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(
                24, TimeUnit.HOURS // Täglich wiederholen
            )
                .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
                // Optional: Constraints hinzufügen
                // .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE, // Ersetze bestehenden Worker mit demselben Namen
                periodicWorkRequest
            )
            Log.i(TAG, "ExpiryCheckWorker enqueued with name '$WORK_NAME'. Will run daily, first run after approx. ${initialDelay.toMinutes()} minutes.")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ExpiryCheckWorker: doWork() started.")
        try {
            // Überprüfe die Benachrichtigungsberechtigung (relevant für Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Worker cannot send notifications.")
                // Der Worker hat seine Arbeit (die Überprüfung) trotzdem versucht.
                // Ob dies ein 'failure' oder 'success' ist, hängt von den Anforderungen ab.
                // Wenn Benachrichtigungen optional sind, ist success() okay.
                return Result.success()
            }

            // Hole die aktuellen Benutzereinstellungen (Vorlaufzeit, Benachrichtigungszeit)
            val currentSettings = userPreferencesRepository.notificationSettingsFlow.first()
            val reminderDays = currentSettings.leadTimeDays
            Log.d(TAG, "Using reminder days preference: $reminderDays. Configured notification time (not directly used in doWork logic): ${currentSettings.notificationHour}:${currentSettings.notificationMinute}")

            // Hole die Liste der Lebensmittel
            // Annahme: FoodStore.getFoodList gibt einen Flow zurück
            val allItems = FoodStore.getFoodList(appContext).first()

            if (allItems.isEmpty()) {
                Log.d(TAG, "No food items found to check.")
                return Result.success()
            }
            Log.d(TAG, "Total food items fetched for check: ${allItems.size}")

            val today = LocalDate.now()
            val expiringItems = allItems.filter { item ->
                item.expiryDate != null &&
                        item.expiryDate >= today && // Nur Artikel prüfen, die heute oder in Zukunft ablaufen
                        ChronoUnit.DAYS.between(today, item.expiryDate) <= reminderDays
            }

            if (expiringItems.isNotEmpty()) {
                Log.d(TAG, "${expiringItems.size} item(s) are expiring within the next $reminderDays days.")
                sendExpiryNotifications(expiringItems)
            } else {
                Log.d(TAG, "No items found expiring within the next $reminderDays days.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during ExpiryCheckWorker execution", e)
            return Result.failure()
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun sendExpiryNotifications(items: List<FoodItem>) {
        val notificationManager = NotificationManagerCompat.from(appContext)
        createNotificationChannel(notificationManager) // Stelle sicher, dass der Channel existiert

        items.forEachIndexed { index, item ->
            // Generiere eine eindeutige ID für jede Benachrichtigung,
            // um zu verhindern, dass sie sich gegenseitig überschreiben, falls item.id nicht eindeutig genug wäre
            // oder um Kollisionen mit der Summary-ID zu vermeiden.
            val notificationId = item.id.toString().hashCode() + index // Einfache Methode zur Erhöhung der Eindeutigkeit

            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // Optional: Füge Extras hinzu, um in der MainActivity spezifisch zu navigieren
                // putExtra("food_item_id", item.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId, // Eindeutiger RequestCode für jeden PendingIntent
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val daysLeft = item.expiryDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
            val expiryText = when {
                daysLeft == null -> appContext.getString(R.string.notification_no_expiry_date)
                daysLeft < 0 -> appContext.getString(R.string.notification_expired_already) // Sollte durch Filter in doWork kaum auftreten
                daysLeft == 0L -> appContext.getString(R.string.notification_expires_today)
                daysLeft == 1L -> appContext.getString(R.string.notification_expires_tomorrow)
                else -> appContext.getString(R.string.notification_expires_in_days, daysLeft)
            }
            val contentTitle = "${item.name ?: appContext.getString(R.string.notification_unnamed_product)} ${item.brand?.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""}".trim()

            val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon) // ERSETZE DIES mit deinem App-Icon
                .setContentTitle(contentTitle)
                .setContentText(expiryText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY) // Alle individuellen Benachrichtigungen dieser Gruppe zuordnen
                .build()
            try {
                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "Sent notification for ${item.name ?: "Unknown Item"}, ID: $notificationId")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while sending notification for ${item.name ?: "Unknown Item"}. This might be due to background restrictions or missing POST_NOTIFICATIONS permission.", e)
            }
        }

        // Erstelle eine Zusammenfassungsbenachrichtigung, wenn mehr als ein Artikel abläuft
        if (items.size > 1) {
            val summaryIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val summaryPendingIntent = PendingIntent.getActivity(
                appContext,
                SUMMARY_NOTIFICATION_ID, // Fester RequestCode für die Zusammenfassung
                summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(appContext.getString(R.string.notification_summary_title, items.size))
                .setSummaryText(appContext.getString(R.string.notification_summary_text, items.size)) // Wird auf älteren Android-Versionen angezeigt

            items.take(5).forEach { item -> // Zeige Details für bis zu 5 Artikel
                val daysLeftSummary = item.expiryDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
                val itemText = when {
                    daysLeftSummary == null -> "${item.name ?: appContext.getString(R.string.notification_unnamed_product)} (${appContext.getString(R.string.notification_no_expiry_date_short)})"
                    daysLeftSummary == 0L -> "${item.name ?: appContext.getString(R.string.notification_unnamed_product)} (${appContext.getString(R.string.notification_today_short)})"
                    daysLeftSummary == 1L -> "${item.name ?: appContext.getString(R.string.notification_unnamed_product)} (${appContext.getString(R.string.notification_tomorrow_short)})"
                    else -> "${item.name ?: appContext.getString(R.string.notification_unnamed_product)} (${appContext.getString(R.string.notification_days_short, daysLeftSummary)})"
                }
                inboxStyle.addLine(itemText)
            }
            if (items.size > 5) {
                inboxStyle.addLine(appContext.getString(R.string.notification_summary_more_items, items.size - 5))
            }

            val summaryNotification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon) // ERSETZE DIES
                .setContentTitle(appContext.getString(R.string.notification_summary_title, items.size)) // z.B. "X Produkte laufen bald ab"
                .setContentText(appContext.getString(R.string.notification_summary_content_text))       // z.B. "Überprüfe deine Vorräte."
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true) // Dies kennzeichnet die Benachrichtigung als Zusammenfassung für die Gruppe
                .setContentIntent(summaryPendingIntent)
                .setAutoCancel(true)
                .build()
            try {
                notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification) // Verwende die feste ID für die Zusammenfassung
                Log.d(TAG, "Sent summary notification, ID: $SUMMARY_NOTIFICATION_ID")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while sending summary notification.", e)
            }
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Überprüfe, ob der Channel bereits existiert, um ihn nicht mehrmals zu erstellen
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val name = appContext.getString(R.string.notification_channel_name) // z.B. aus strings.xml: "Ablaufwarnungen"
                val descriptionText = appContext.getString(R.string.notification_channel_description) // z.B. "Benachrichtigungen für bald ablaufende Produkte"
                val importance = NotificationManager.IMPORTANCE_DEFAULT // Wähle die passende Wichtigkeit
                val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    // Weitere Einstellungen für den Channel sind hier möglich (z.B. Lights, Vibration)
                    // enableLights(true)
                    // lightColor = Color.RED
                    // enableVibration(true)
                    // vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                }
                // Registriere den Channel beim System
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created.")
            } else {
                Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' already exists.")
            }
        }
    }
}

