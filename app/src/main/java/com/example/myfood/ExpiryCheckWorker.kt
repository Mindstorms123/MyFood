package com.example.myfood.workers

import android.Manifest
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
import androidx.work.WorkerParameters
import com.example.myfood.FoodItem      // Stelle sicher, dass FoodItem hier korrekt definiert ist
import com.example.myfood.FoodStore      // Dein FoodStore object
import com.example.myfood.MainActivity
import com.example.myfood.R
// Importiere UserPreferencesRepository, wenn du es für reminderDays verwendest
import com.example.myfood.datastore.UserPreferencesRepository // ANNAHME: Du hast diese Datei für reminderDays

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first // Wichtig für .first() auf dem Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    // Da FoodStore ein object ist, wird es normalerweise nicht so injiziert,
    // es sei denn, du hast ein spezielles Hilt-Modul dafür.
    // Für die Einfachheit rufen wir es direkt auf, oder du stellst es über ein Hilt-Modul bereit.
    // private val foodRepository: FoodStore, // Entfernen, wenn direkt aufgerufen
    private val userPreferencesRepository: UserPreferencesRepository // Beibehalten, wenn du reminderDays einstellbar machst
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "ExpiryCheckWorker"
        private const val NOTIFICATION_CHANNEL_ID = "expiry_notifications_channel"
        private const val NOTIFICATION_GROUP_KEY = "com.example.myfood.EXPIRY_NOTIFICATION_GROUP"
        private const val SUMMARY_NOTIFICATION_ID = 0
        private const val TAG = "ExpiryCheckWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Expiry check worker started.")
        try {
            if (ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Worker cannot send notifications.")
                return Result.success()
            }

            // Hole die reminderDays aus UserPreferencesRepository
            // Wenn du UserPreferencesRepository nicht verwendest, setze hier einen festen Wert:
            // val reminderDays = 3 // Beispiel für einen festen Wert
            val reminderDays = userPreferencesRepository.reminderDaysFlow.first()
            Log.d(TAG, "Using reminder days preference: $reminderDays")

            // Hole die Liste der FoodItems direkt aus deinem FoodStore-Objekt
            val allItems = FoodStore.getFoodList(appContext).first() // .first() um den aktuellen Wert des Flows zu bekommen

            if (allItems.isEmpty()) {
                Log.d(TAG, "No food items found to check.")
                return Result.success()
            }
            Log.d(TAG, "Total items fetched: ${allItems.size}")

            val today = LocalDate.now()
            val expiringItems = allItems.filter { item ->
                item.expiryDate != null &&
                        item.expiryDate >= today &&
                        ChronoUnit.DAYS.between(today, item.expiryDate) <= reminderDays
            }

            if (expiringItems.isNotEmpty()) {
                Log.d(TAG, "${expiringItems.size} item(s) are expiring soon.")
                sendExpiryNotifications(expiringItems) // Übergib appContext an sendExpiryNotifications
            } else {
                Log.d(TAG, "No items expiring within the next $reminderDays days.")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during expiry check execution", e)
            return Result.failure()
        }
    }

    // Die sendExpiryNotifications und createNotificationChannel Methoden bleiben im Wesentlichen gleich,
    // stelle nur sicher, dass sie appContext verwenden, wenn sie es direkt benötigen (hier tun sie es).

    private fun sendExpiryNotifications(items: List<FoodItem>) {
        val notificationManager = NotificationManagerCompat.from(appContext)
        createNotificationChannel(notificationManager)

        // ... (Rest der Methode bleibt gleich wie in meiner vorherigen Antwort)
        // Stelle sicher, dass du R.drawable.ic_notification_icon ersetzt
        // und dass FoodItem die Properties id, name, brand, expiryDate hat.

        items.forEach { item ->
            val notificationId = item.id.toString().hashCode()

            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val daysLeft = item.expiryDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
            val expiryText = when {
                daysLeft == null -> "hat kein Ablaufdatum."
                daysLeft < 0 -> "ist bereits abgelaufen!"
                daysLeft == 0L -> "läuft HEUTE ab!"
                daysLeft == 1L -> "läuft MORGEN ab!"
                else -> "läuft in $daysLeft Tagen ab."
            }
            val contentTitle = "${item.name ?: "Unbenanntes Produkt"} ${item.brand?.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""}".trim()

            val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon) // ERSETZE DIES
                .setContentTitle(contentTitle)
                .setContentText(expiryText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .build()
            try {
                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "Sent notification for ${item.name}, ID: $notificationId")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException for ${item.name}", e)
            }
        }

        if (items.size > 1) {
            val summaryIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val summaryPendingIntent = PendingIntent.getActivity(
                appContext,
                SUMMARY_NOTIFICATION_ID,
                summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${items.size} Produkte laufen bald ab")
                .setSummaryText("${items.size} Produkte")
            items.take(5).forEach { item ->
                val daysLeftSummary = item.expiryDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
                val itemText = when {
                    daysLeftSummary == null -> "${item.name ?: "Produkt"} (kein MHD)"
                    daysLeftSummary == 0L -> "${item.name ?: "Produkt"} (heute)"
                    daysLeftSummary == 1L -> "${item.name ?: "Produkt"} (morgen)"
                    else -> "${item.name ?: "Produkt"} ($daysLeftSummary Tage)"
                }
                inboxStyle.addLine(itemText)
            }
            if (items.size > 5) {
                inboxStyle.addLine("... und ${items.size - 5} weitere.")
            }
            val summaryNotification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon) // ERSETZE DIES
                .setContentTitle("${items.size} Produkte laufen bald ab")
                .setContentText("Überprüfe deine Vorräte.")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(summaryPendingIntent)
                .setAutoCancel(true)
                .build()
            try {
                notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
                Log.d(TAG, "Sent summary notification, ID: $SUMMARY_NOTIFICATION_ID")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException for summary notification", e)
            }
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val name = appContext.getString(R.string.notification_channel_name)
                val descriptionText = appContext.getString(R.string.notification_channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' created.")
            } else {
                Log.d(TAG, "Notification channel '$NOTIFICATION_CHANNEL_ID' already exists.")
            }
        }
    }
}
