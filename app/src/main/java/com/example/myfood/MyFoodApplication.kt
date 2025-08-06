package com.example.myfood

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration // Dieser Import bleibt
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyFoodApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        Log.d("MyFoodApplication", "MyFoodApplication onCreate() CALLED")
    }

    // Implementiere die Property, die von deiner spezifischen Version der Schnittstelle erwartet wird
    override val workManagerConfiguration: Configuration // <<< IMPLEMENTIERE ALS PROPERTY
        get() { // mit einem benutzerdefinierten Getter
            Log.d("MyFoodApplication", "MyFoodApplication.workManagerConfiguration GETTER CALLED")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
        }
}
