package com.example.myfood

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration // Dieser Import bleibt
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder // Dieser Import bleibt
import androidx.work.WorkManager
import com.example.myfood.workers.ExpiryCheckWorker // Passe den Pfad ggf. an
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MyFoodApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        Log.d("MyFoodApplication", "MyFoodApplication onCreate() CALLED")

        scheduleDailyExpiryCheck()
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("MyFoodApplication", "MyFoodApplication.workManagerConfiguration GETTER CALLED")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
        }

    private fun scheduleDailyExpiryCheck() {
        Log.d("MyFoodApplication", "scheduleDailyExpiryCheck() CALLED")

        // Explizit den Typ für den Builder angeben
        val dailyExpiryCheckWorkBuilder = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(
            1, // repeatInterval
            TimeUnit.DAYS // repeatIntervalTimeUnit
        )

        // setInitialDelay sollte verfügbar sein, wenn WorkManager-Version >= 2.7.0
        dailyExpiryCheckWorkBuilder.setInitialDelay(calculateInitialDelayToSpecificTime(), TimeUnit.MILLISECONDS)
        dailyExpiryCheckWorkBuilder.addTag("DailyExpiryCheckWorkerTag") // Optional

        val dailyExpiryCheckWork = dailyExpiryCheckWorkBuilder.build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyExpiryCheckWorkerUniqueName",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyExpiryCheckWork
        )
        Log.d("MyFoodApplication", "Periodic ExpiryCheckWorker enqueued with ExistingPeriodicWorkPolicy.KEEP.")
    }

    private fun calculateInitialDelayToSpecificTime(): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        val delay = calendar.timeInMillis - System.currentTimeMillis()
        Log.d("MyFoodApplication", "Calculated initial delay: $delay ms (Target time: ${calendar.time})")
        return delay
    }
}

